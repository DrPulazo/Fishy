package com.example.fishy.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.fishy.FishyApp
import com.example.fishy.MainActivity
import com.example.fishy.R
import com.example.fishy.data.local.entity.ChecklistItemEntity
import com.example.fishy.data.local.entity.ScheduledShipmentEntity
import com.example.fishy.data.serialization.FishyJson
import com.example.fishy.domain.model.ShipmentMode
import com.example.fishy.domain.model.ShipmentPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class ScheduledNotifKind {
    /** User-chosen prep time (checklist / get ready). */
    PREP,
    /** Scheduled shipment start — tap opens the shipment. */
    START
}

class NotificationScheduler(
    private val context: Context,
    private val repository: com.example.fishy.data.repo.FishyRepository
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(shipment: ScheduledShipmentEntity) {
        cancel(shipment.id)
        if (!shipment.notificationEnabled || shipment.isCompleted) return

        val now = System.currentTimeMillis()

        if (!shipment.notificationSent) {
            val prepAt = shipment.notificationAtMillis
            if (prepAt != null && prepAt > now) {
                setAlarm(shipment.id, ScheduledNotifKind.PREP, prepAt)
            }
        }

        if (!shipment.startNotificationSent) {
            val startAt = scheduledStartMillis(shipment)
            if (startAt > now) {
                setAlarm(shipment.id, ScheduledNotifKind.START, startAt)
            }
        }
    }

    fun cancel(id: Long) {
        cancelAlarm(id, ScheduledNotifKind.PREP)
        cancelAlarm(id, ScheduledNotifKind.START)
        // Legacy single-alarm request code from pre-dual-notif builds.
        cancelLegacy(id)
    }

    suspend fun cancelAll() {
        repository.allScheduledIds().forEach { cancel(it) }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancelAll()
    }

    fun rescheduleAll() {
        CoroutineScope(Dispatchers.IO).launch {
            repository.pendingNotifications().forEach { schedule(it) }
        }
    }

    private fun setAlarm(id: Long, kind: ScheduledNotifKind, atMillis: Long) {
        val intent = alarmIntent(id, kind)
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode(id, kind),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        } else {
            @Suppress("DEPRECATION")
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    private fun cancelAlarm(id: Long, kind: ScheduledNotifKind) {
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode(id, kind),
            alarmIntent(id, kind),
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

    private fun alarmIntent(id: Long, kind: ScheduledNotifKind): Intent =
        Intent(context, NotificationAlarmReceiver::class.java).apply {
            action = ACTION_SHOW
            putExtra(EXTRA_ID, id)
            putExtra(EXTRA_KIND, kind.name)
        }

    companion object {
        const val ACTION_SHOW = "com.example.fishy.SHOW_NOTIFICATION"
        const val EXTRA_ID = "shipment_id"
        const val EXTRA_KIND = "notif_kind"
        const val CHANNEL_ID = "fishy_channel"

        const val EXTRA_FROM_NOTIFICATION = "from_notification"
        const val EXTRA_OPEN_SCHEDULER = "open_scheduler"
        const val EXTRA_START_SCHEDULED_ID = "start_scheduled_id"

        fun requestCode(id: Long, kind: ScheduledNotifKind): Int = when (kind) {
            ScheduledNotifKind.PREP -> (id * 2).toInt()
            ScheduledNotifKind.START -> (id * 2 + 1).toInt()
        }

        fun notifyId(id: Long, kind: ScheduledNotifKind): Int = when (kind) {
            ScheduledNotifKind.PREP -> id.toInt()
            ScheduledNotifKind.START -> (id + 1_000_000L).toInt()
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
        val kind = runCatching {
            ScheduledNotifKind.valueOf(
                intent.getStringExtra(NotificationScheduler.EXTRA_KIND) ?: ScheduledNotifKind.PREP.name
            )
        }.getOrDefault(ScheduledNotifKind.PREP)

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as FishyApp
                val shipment = app.repository.getScheduled(id) ?: return@launch
                if (!shipment.notificationEnabled || shipment.isCompleted) return@launch

                when (kind) {
                    ScheduledNotifKind.PREP -> {
                        if (shipment.notificationSent) return@launch
                        val checklist = app.repository.getChecklist(id)
                        showPrepNotification(context, shipment, checklist)
                        app.repository.updateScheduled(shipment.copy(notificationSent = true))
                    }
                    ScheduledNotifKind.START -> {
                        if (shipment.startNotificationSent) return@launch
                        val checklist = app.repository.getChecklist(id)
                        showStartNotification(context, shipment, checklist)
                        app.repository.updateScheduled(shipment.copy(startNotificationSent = true))
                    }
                }
            } finally {
                pending.finish()
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
        checklist: List<ChecklistItemEntity>
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(context, nm)

        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(NotificationScheduler.EXTRA_FROM_NOTIFICATION, true)
            putExtra(NotificationScheduler.EXTRA_OPEN_SCHEDULER, true)
            putExtra(NotificationScheduler.EXTRA_ID, shipment.id)
        }
        val pi = PendingIntent.getActivity(
            context,
            NotificationScheduler.requestCode(shipment.id, ScheduledNotifKind.PREP),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val body = buildShipmentNotifBody(context, shipment, checklist)
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
        nm.notify(NotificationScheduler.notifyId(shipment.id, ScheduledNotifKind.PREP), notification)
    }

    private fun showStartNotification(
        context: Context,
        shipment: ScheduledShipmentEntity,
        checklist: List<ChecklistItemEntity>
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

        val body = buildShipmentNotifBody(context, shipment, checklist)
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
        checklist: List<ChecklistItemEntity>
    ): ShipmentNotifBody {
        val payload = decodePayload(shipment)
        val dateStr = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            .format(Date(shipment.scheduledDateMillis))
        val timeText = formatTimeUntil(context, shipment)
        val portText = resolvePortText(context, shipment, payload)
        val customer = shipment.customer.ifBlank { payload?.customer.orEmpty() }
        val checklistStatus = checklistStatus(checklist)
        val checklistEmoji = when (checklistStatus) {
            ChecklistStatus.COMPLETED -> "🟢"
            ChecklistStatus.PARTIAL -> "🟡"
            ChecklistStatus.NONE -> "🔴"
            ChecklistStatus.EMPTY -> "⚪"
        }
        val checklistText = when (checklistStatus) {
            ChecklistStatus.COMPLETED -> context.getString(R.string.notif_checklist_done)
            ChecklistStatus.PARTIAL -> context.getString(R.string.notif_checklist_partial)
            ChecklistStatus.NONE -> context.getString(R.string.notif_checklist_none)
            ChecklistStatus.EMPTY -> context.getString(R.string.notif_checklist_empty)
        }
        val customerLabel = customer.ifBlank { context.getString(R.string.notif_customer_unknown) }
        val bigText = """
            ${context.getString(R.string.notif_big_time, timeText)}
            
            ⚓ ${context.getString(R.string.port)}: $portText
            📅 ${context.getString(R.string.schedule_date_field)}: $dateStr
            🕐 ${context.getString(R.string.schedule_time_field)}: ${shipment.scheduledTime}
            💼 ${context.getString(R.string.customer)}: $customerLabel
            $checklistEmoji $checklistText
        """.trimIndent()
        return ShipmentNotifBody(
            contentLine = context.getString(R.string.notif_content_line, timeText, portText),
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
        if (mode == ShipmentMode.MULTI_PORT && payload != null) {
            val ports = payload.multiPorts.map { it.port }.filter { it.isNotBlank() }
            if (ports.isNotEmpty()) return ports.joinToString(", ")
        }
        return shipment.port.ifBlank { payload?.port.orEmpty() }.ifBlank { unknown }
    }

    private fun formatTimeUntil(context: Context, shipment: ScheduledShipmentEntity): String {
        val startAt = NotificationScheduler.scheduledStartMillis(shipment)
        val timeUntil = startAt - System.currentTimeMillis()
        if (timeUntil <= 0) return context.getString(R.string.notif_time_now)
        val days = TimeUnit.MILLISECONDS.toDays(timeUntil)
        val hours = TimeUnit.MILLISECONDS.toHours(timeUntil) % 24
        val minutes = TimeUnit.MILLISECONDS.toMinutes(timeUntil) % 60
        return when {
            days > 0 -> context.getString(R.string.notif_time_days, days.toInt(), hours.toInt())
            hours > 0 -> context.getString(R.string.notif_time_hours, hours.toInt(), minutes.toInt())
            minutes > 0 -> context.getString(R.string.notif_time_minutes, minutes.toInt())
            else -> context.getString(R.string.notif_time_now)
        }
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
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        (context.applicationContext as? FishyApp)?.notificationScheduler?.rescheduleAll()
    }
}
