package com.example.fishy.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.fishy.FishyApp
import com.example.fishy.MainActivity
import com.example.fishy.R
import com.example.fishy.data.local.entity.ChecklistItemEntity
import com.example.fishy.data.local.entity.ScheduledShipmentEntity
import com.example.fishy.data.serialization.FishyJson
import com.example.fishy.domain.calc.ShipmentCalculator
import com.example.fishy.domain.format.QuantityFormatters
import com.example.fishy.domain.model.ShipmentMode
import com.example.fishy.domain.model.ShipmentPayload
import com.example.fishy.domain.model.ShipmentSummaries
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class ScheduledNotifKind {
    /** User-chosen prep reminder (checklist / get ready). */
    PREP,
    /** Scheduled shipment start — tap opens the shipment. */
    START
}

class NotificationScheduler(
    private val context: Context,
    private val repository: com.example.fishy.data.repo.FishyRepository
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val mutex = kotlinx.coroutines.sync.Mutex()

    fun schedule(shipment: ScheduledShipmentEntity) {
        CoroutineScope(Dispatchers.IO).launch {
            deliverDueAndSchedule(shipment.id)
        }
    }

    suspend fun scheduleSuspend(shipment: ScheduledShipmentEntity) {
        deliverDueAndSchedule(shipment.id)
    }

    /**
     * Catch-up due notifications, prune past/sent prep rows, then (re)arm future alarms.
     */
    suspend fun deliverDueAndSchedule(shipmentId: Long) = mutex.withLock {
        val initial = repository.getScheduled(shipmentId) ?: return@withLock
        if (initial.isCompleted) {
            cancelSuspendUnlocked(shipmentId)
            return@withLock
        }
        // In-progress plan (draft linked): keep card, but do not fire or re-arm reminders.
        if (initial.linkedDraftId != null) {
            cancelSuspendUnlocked(shipmentId)
            return@withLock
        }

        val now = System.currentTimeMillis()
        var shipment = initial
        val thousandsSeparator = runCatching {
            FishyApp.instance.settingsRepository.settings.first().effectiveThousandsSeparator
        }.getOrDefault(false)

        // Catch-up START
        if (!shipment.startNotificationSent) {
            val startAt = scheduledStartMillis(shipment)
            if (startAt <= now) {
                val checklist = repository.getChecklist(shipmentId)
                showStartNotification(context, shipment, checklist, thousandsSeparator)
                shipment = shipment.copy(startNotificationSent = true)
                repository.updateScheduled(shipment)
            }
        }

        // Catch-up PREP (enabled only)
        if (shipment.notificationEnabled) {
            val due = repository.getReminders(shipmentId)
                .filter { !it.sent && it.atMillis <= now }
                .sortedBy { it.atMillis }
            if (due.isNotEmpty()) {
                val checklist = repository.getChecklist(shipmentId)
                due.forEach { reminder ->
                    showPrepNotification(context, shipment, checklist, reminder.id, thousandsSeparator)
                    repository.upsertReminder(reminder.copy(sent = true))
                }
            }
        }

        // After catch-up, drop all past rows (sent or skipped while notify was off).
        shipment = repository.pruneRemindersAfterCatchUp(shipmentId, now) ?: return@withLock
        if (shipment.isCompleted) {
            cancelSuspendUnlocked(shipmentId)
            return@withLock
        }

        cancelSuspendUnlocked(shipmentId)

        if (!shipment.startNotificationSent) {
            val startAt = scheduledStartMillis(shipment)
            if (startAt > now) {
                setAlarm(shipment.id, ScheduledNotifKind.START, startAt, reminderId = 0L)
            }
        }

        if (shipment.notificationEnabled) {
            val reminders = repository.getReminders(shipment.id)
                .filter { !it.sent && it.atMillis > now }
                .sortedBy { it.atMillis }
            reminders.forEachIndexed { slot, reminder ->
                if (slot >= PREP_ALARM_SLOTS) return@forEachIndexed
                setAlarm(
                    shipment.id,
                    ScheduledNotifKind.PREP,
                    reminder.atMillis,
                    reminderId = reminder.id,
                    prepSlot = slot
                )
            }
        }
    }

    fun cancel(id: Long) {
        CoroutineScope(Dispatchers.IO).launch {
            cancelSuspend(id)
        }
    }

    suspend fun cancelSuspend(id: Long) = mutex.withLock {
        cancelSuspendUnlocked(id)
    }

    private fun cancelSuspendUnlocked(id: Long) {
        for (slot in 0 until PREP_ALARM_SLOTS) {
            cancelAlarm(id, ScheduledNotifKind.PREP, prepSlot = slot)
        }
        cancelAlarm(id, ScheduledNotifKind.START, prepSlot = 0)
        cancelLegacy(id)
        cancelLegacyDual(id)
    }

    suspend fun cancelAll() {
        repository.allScheduledIds().forEach { cancelSuspend(it) }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancelAll()
    }

    fun rescheduleAll() {
        CoroutineScope(Dispatchers.IO).launch {
            rescheduleAllSuspend()
        }
    }

    suspend fun rescheduleAllSuspend() {
        repository.getActiveScheduled().forEach { deliverDueAndSchedule(it.id) }
    }

    private fun setAlarm(
        shipmentId: Long,
        kind: ScheduledNotifKind,
        atMillis: Long,
        reminderId: Long,
        prepSlot: Int = 0
    ) {
        val intent = alarmIntent(shipmentId, kind, reminderId)
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode(shipmentId, kind, prepSlot),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && canExact -> {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            }
            else -> {
                @Suppress("DEPRECATION")
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, atMillis, pi)
            }
        }
    }

    private fun cancelAlarm(shipmentId: Long, kind: ScheduledNotifKind, prepSlot: Int) {
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode(shipmentId, kind, prepSlot),
            alarmIntent(shipmentId, kind, reminderId = 0L),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pi)
    }

    private fun cancelLegacy(id: Long) {
        val intent = Intent(context, NotificationAlarmReceiver::class.java).apply {
            action = ACTION_SHOW
            putExtra(EXTRA_ID, id)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pi)
    }

    private fun cancelLegacyDual(id: Long) {
        listOf(ScheduledNotifKind.PREP, ScheduledNotifKind.START).forEach { kind ->
            val intent = Intent(context, NotificationAlarmReceiver::class.java).apply {
                action = ACTION_SHOW
                putExtra(EXTRA_ID, id)
                putExtra(EXTRA_KIND, kind.name)
            }
            val code = when (kind) {
                ScheduledNotifKind.PREP -> (id * 2).toInt()
                ScheduledNotifKind.START -> (id * 2 + 1).toInt()
            }
            val pi = PendingIntent.getBroadcast(
                context,
                code,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pi)
        }
    }

    private fun alarmIntent(shipmentId: Long, kind: ScheduledNotifKind, reminderId: Long): Intent =
        Intent(context, NotificationAlarmReceiver::class.java).apply {
            action = ACTION_SHOW
            putExtra(EXTRA_ID, shipmentId)
            putExtra(EXTRA_KIND, kind.name)
            putExtra(EXTRA_REMINDER_ID, reminderId)
        }

    companion object {
        const val ACTION_SHOW = "com.example.fishy.SHOW_NOTIFICATION"
        const val EXTRA_ID = "shipment_id"
        const val EXTRA_KIND = "notif_kind"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val CHANNEL_ID = "fishy_channel"

        const val EXTRA_FROM_NOTIFICATION = "from_notification"
        const val EXTRA_OPEN_SCHEDULER = "open_scheduler"
        const val EXTRA_OPEN_PREP_CHECKLIST = "open_prep_checklist"
        const val EXTRA_START_SCHEDULED_ID = "start_scheduled_id"

        /** Fixed prep alarm slots per shipment so cancel works after reminder row ids change. */
        const val PREP_ALARM_SLOTS = 16

        fun requestCode(shipmentId: Long, kind: ScheduledNotifKind, prepSlot: Int = 0): Int {
            val base = (shipmentId % 50_000L) * PREP_ALARM_SLOTS
            return when (kind) {
                ScheduledNotifKind.PREP -> (1_100_000_000L + base + prepSlot.coerceIn(0, PREP_ALARM_SLOTS - 1)).toInt()
                ScheduledNotifKind.START -> (1_000_000_000L + (shipmentId % 100_000_000L)).toInt()
            }
        }

        /** Activity PendingIntent for prep taps — unique per reminder row, not slot. */
        fun prepActivityRequestCode(reminderId: Long): Int =
            (1_200_000_000L + (reminderId % 100_000_000L)).toInt()

        fun notifyId(shipmentId: Long, kind: ScheduledNotifKind, reminderId: Long = 0L): Int = when (kind) {
            ScheduledNotifKind.PREP -> (100_000_000L + (reminderId % 100_000_000L)).toInt()
            ScheduledNotifKind.START -> (shipmentId + 1_000_000L).toInt()
        }

        fun scheduledStartMillis(shipment: ScheduledShipmentEntity): Long {
            return Calendar.getInstance().apply {
                timeInMillis = shipment.scheduledDateMillis
                val parts = shipment.scheduledTime.split(":")
                set(Calendar.HOUR_OF_DAY, parts.getOrNull(0)?.toIntOrNull() ?: 9)
                set(Calendar.MINUTE, parts.getOrNull(1)?.toIntOrNull() ?: 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
    }
}

private enum class ChecklistStatus {
    COMPLETED, PARTIAL, NONE, EMPTY
}

class NotificationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val id = intent?.getLongExtra(NotificationScheduler.EXTRA_ID, 0L) ?: return
        if (id == 0L) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as FishyApp
                app.notificationScheduler.deliverDueAndSchedule(id)
            } finally {
                pending.finish()
            }
        }
    }
}

private fun ensureChannel(context: Context, nm: NotificationManager) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        nm.createNotificationChannel(
            NotificationChannel(
                NotificationScheduler.CHANNEL_ID,
                context.getString(R.string.notifications),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notif_channel_desc)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }
        )
    }
}

private fun showPrepNotification(
    context: Context,
    shipment: ScheduledShipmentEntity,
    checklist: List<ChecklistItemEntity>,
    reminderId: Long,
    thousandsSeparator: Boolean = false
) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    ensureChannel(context, nm)

    val open = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(NotificationScheduler.EXTRA_FROM_NOTIFICATION, true)
        putExtra(NotificationScheduler.EXTRA_OPEN_SCHEDULER, true)
        putExtra(NotificationScheduler.EXTRA_OPEN_PREP_CHECKLIST, true)
        putExtra(NotificationScheduler.EXTRA_ID, shipment.id)
    }
    val pi = PendingIntent.getActivity(
        context,
        NotificationScheduler.prepActivityRequestCode(reminderId),
        open,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val body = buildShipmentNotifBody(context, shipment, checklist, thousandsSeparator)
    val notification = NotificationCompat.Builder(context, NotificationScheduler.CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_fishy)
        .setContentTitle(context.getString(R.string.notif_title_emoji))
        .setContentText(body.contentLine)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body.bigText))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setAutoCancel(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(pi)
        .build()
    nm.notify(
        NotificationScheduler.notifyId(shipment.id, ScheduledNotifKind.PREP, reminderId),
        notification
    )
}

private fun showStartNotification(
    context: Context,
    shipment: ScheduledShipmentEntity,
    checklist: List<ChecklistItemEntity>,
    thousandsSeparator: Boolean = false
) {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    ensureChannel(context, nm)

    val open = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(NotificationScheduler.EXTRA_FROM_NOTIFICATION, true)
        putExtra(NotificationScheduler.EXTRA_START_SCHEDULED_ID, shipment.id)
    }
    val request = NotificationScheduler.requestCode(shipment.id, ScheduledNotifKind.START)
    val pi = PendingIntent.getActivity(
        context,
        request,
        open,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val body = buildShipmentNotifBody(context, shipment, checklist, thousandsSeparator)
    val notification = NotificationCompat.Builder(context, NotificationScheduler.CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_fishy)
        .setContentTitle(context.getString(R.string.notif_start_title_emoji))
        .setContentText(body.contentLine)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body.bigText))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setAutoCancel(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(pi)
        .addAction(
            0,
            context.getString(R.string.notif_start_action),
            pi
        )
        .build()
    nm.notify(NotificationScheduler.notifyId(shipment.id, ScheduledNotifKind.START), notification)
}

private data class ShipmentNotifBody(
    val contentLine: String,
    val bigText: String
)

private fun buildShipmentNotifBody(
    context: Context,
    shipment: ScheduledShipmentEntity,
    checklist: List<ChecklistItemEntity>,
    thousandsSeparator: Boolean = false
): ShipmentNotifBody {
    val payload = decodePayload(shipment)
    val dateStr = formatShipmentDate(context, shipment.scheduledDateMillis)
    val timeText = formatTimeUntil(context, shipment)
    val portText = resolvePortText(context, shipment, payload)
    val plannedPortsCount = when (payload?.mode) {
        ShipmentMode.MULTI_PORT -> payload.multiPorts
            .map { it.port.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .size

        else -> 1
    }
    val customer = shipment.customer.ifBlank { payload?.customer.orEmpty() }
    val checklistStatus = checklistStatus(checklist)
    val checklistDone = checklist.count { it.isCompleted }
    val checklistTotal = checklist.size
    val systemNight =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    val checklistEmoji = when (checklistStatus) {
        ChecklistStatus.COMPLETED -> "🟢"
        ChecklistStatus.PARTIAL -> "🟡"
        ChecklistStatus.NONE -> "🔴"
        ChecklistStatus.EMPTY -> if (systemNight) "⚪" else "⚫"
    }
    val checklistText = when (checklistStatus) {
        ChecklistStatus.COMPLETED -> context.getString(
            R.string.checklist_completed,
            checklistDone,
            checklistTotal
        )
        ChecklistStatus.PARTIAL -> context.getString(
            R.string.checklist_partial,
            checklistDone,
            checklistTotal
        )
        ChecklistStatus.NONE -> context.getString(
            R.string.checklist_none,
            checklistDone,
            checklistTotal
        )
        ChecklistStatus.EMPTY -> context.getString(R.string.notif_checklist_empty)
    }
    val customerLabel = customer.ifBlank { context.getString(R.string.notif_customer_unknown) }
    val tonnageKg = payload?.let { ShipmentCalculator.plannedTonnageKg(it) } ?: 0.0
    val bigText = buildString {
        if (!timeText.isNullOrBlank()) {
            appendLine(timeText)
            appendLine()
        }
        appendLine("📅 ${context.getString(R.string.schedule_date_field)}: $dateStr")
        appendLine("🕐 ${context.getString(R.string.schedule_time_field)}: ${shipment.scheduledTime}")
        appendLine("💼 ${context.getString(R.string.customer)}: $customerLabel")
        if (tonnageKg > 0.0) {
            val formatted = QuantityFormatters.formatWeight(tonnageKg, thousandsSeparator)
            appendLine("🪝 ${context.getString(R.string.schedule_tonnage, formatted)}")
        }
        val portLabel = if (plannedPortsCount > 1) {
            context.getString(R.string.ports_prefix, portText)
        } else {
            context.getString(R.string.port_prefix, portText)
        }
        appendLine("⚓ $portLabel")
        append("$checklistEmoji $checklistText")
    }
    return ShipmentNotifBody(
        contentLine = if (timeText.isNullOrBlank()) {
            portText
        } else {
            context.getString(R.string.notif_content_line, timeText, portText)
        },
        bigText = bigText
    )
}

private fun decodePayload(shipment: ScheduledShipmentEntity): ShipmentPayload? =
    runCatching { FishyJson.decodePayload(shipment.payloadJson) }.getOrNull()

private fun resolvePortText(
    context: Context,
    shipment: ScheduledShipmentEntity,
    payload: ShipmentPayload?
): String {
    val unknown = context.getString(R.string.notif_port_unknown)
    val mode = payload?.mode ?: runCatching {
        ShipmentMode.valueOf(shipment.mode)
    }.getOrNull()
    when (mode) {
        ShipmentMode.MULTI_PORT -> {
            if (payload != null) {
                ShipmentSummaries.firstPlusRestCountOnlyRu(payload.multiPorts.map { it.port })
                    ?.let { return it }
            }
        }
        ShipmentMode.UNLOAD -> {
            if (payload != null) {
                ShipmentSummaries.firstPlusRestCountOnlyRu(payload.unloadReceptions.map { it.name })
                    ?.let { return it }
            }
        }
        else -> Unit
    }
    return shipment.port.ifBlank { payload?.port.orEmpty() }.ifBlank { unknown }
}

private fun formatShipmentDate(context: Context, millis: Long): String {
    val datePart = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(millis))
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    val weekdayRes = when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> R.string.weekday_short_mon
        Calendar.TUESDAY -> R.string.weekday_short_tue
        Calendar.WEDNESDAY -> R.string.weekday_short_wed
        Calendar.THURSDAY -> R.string.weekday_short_thu
        Calendar.FRIDAY -> R.string.weekday_short_fri
        Calendar.SATURDAY -> R.string.weekday_short_sat
        else -> R.string.weekday_short_sun
    }
    return "$datePart ${context.getString(weekdayRes)}"
}

private fun formatTimeUntil(context: Context, shipment: ScheduledShipmentEntity): String? {
    val startAt = NotificationScheduler.scheduledStartMillis(shipment)
    val timeUntil = startAt - System.currentTimeMillis()
    if (timeUntil <= 0) return null
    val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(timeUntil)
    val roundedMinutes = ((totalMinutes + 2) / 5) * 5
    if (roundedMinutes <= 0) return null
    val days = roundedMinutes / (60 * 24)
    val hours = (roundedMinutes / 60) % 24
    val minutes = roundedMinutes % 60
    val locale = context.resources.configuration.locales[0]
    return if (locale.language == "ru") {
        when {
            days > 0 -> {
                val dayPart = ruCountWord(days.toInt(), "день", "дня", "дней")
                if (hours > 0) {
                    val hourPart = ruCountWord(hours.toInt(), "час", "часа", "часов")
                    "До начала: $dayPart $hourPart"
                } else {
                    "До начала: $dayPart"
                }
            }
            hours > 0 -> "До начала: ${ruCountWord(hours.toInt(), "час", "часа", "часов")}"
            minutes > 0 -> "До начала: ${ruMinutesAccusative(minutes.toInt())}"
            else -> null
        }
    } else {
        when {
            days > 0 -> {
                if (hours > 0) {
                    context.getString(R.string.notif_until_days_hours, days.toInt(), hours.toInt())
                } else {
                    context.getString(R.string.notif_until_days, days.toInt())
                }
            }
            hours > 0 -> context.getString(R.string.notif_until_hours, hours.toInt())
            minutes > 0 -> context.getString(R.string.notif_until_minutes, minutes.toInt())
            else -> null
        }
    }
}

private fun ruCountWord(n: Int, one: String, few: String, many: String): String {
    val mod100 = kotlin.math.abs(n) % 100
    val mod10 = kotlin.math.abs(n) % 10
    val word = when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
    return "$n $word"
}

private fun ruMinutesAccusative(n: Int): String {
    val mod100 = kotlin.math.abs(n) % 100
    val mod10 = kotlin.math.abs(n) % 10
    val word = when {
        mod100 in 11..14 -> "минут"
        mod10 == 1 -> "минуту"
        mod10 in 2..4 -> "минуты"
        else -> "минут"
    }
    return "$n $word"
}

private fun checklistStatus(items: List<ChecklistItemEntity>): ChecklistStatus {
    if (items.isEmpty()) return ChecklistStatus.EMPTY
    val completed = items.count { it.isCompleted }
    return when {
        completed == items.size -> ChecklistStatus.COMPLETED
        completed > 0 -> ChecklistStatus.PARTIAL
        else -> ChecklistStatus.NONE
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as? FishyApp ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.notificationScheduler.rescheduleAllSuspend()
            } finally {
                pending.finish()
            }
        }
    }
}
