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
import com.example.fishy.data.repo.FishyRepository
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

class NotificationScheduler(
    private val context: Context,
    private val repository: FishyRepository
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(shipment: ScheduledShipmentEntity) {
        cancel(shipment.id)
        if (!shipment.notificationEnabled || shipment.isCompleted) return
        val at = shipment.notificationAtMillis ?: return
        if (at <= System.currentTimeMillis()) return

        val intent = Intent(context, NotificationAlarmReceiver::class.java).apply {
            action = ACTION_SHOW
            putExtra(EXTRA_ID, shipment.id)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            shipment.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(id: Long) {
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

    companion object {
        const val ACTION_SHOW = "com.example.fishy.SHOW_NOTIFICATION"
        const val EXTRA_ID = "shipment_id"
        const val CHANNEL_ID = "fishy_channel"
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
                val shipment = app.repository.getScheduled(id) ?: return@launch
                if (!shipment.notificationEnabled || shipment.notificationSent || shipment.isCompleted) {
                    return@launch
                }
                val checklist = app.repository.getChecklist(id)
                showNotification(context, shipment, checklist)
                app.repository.updateScheduled(shipment.copy(notificationSent = true))
            } finally {
                pending.finish()
            }
        }
    }

    private fun showNotification(
        context: Context,
        shipment: ScheduledShipmentEntity,
        checklist: List<ChecklistItemEntity>
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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

        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            action = "OPEN_SCHEDULER_FROM_NOTIFICATION"
            putExtra("from_notification", true)
            putExtra("shipment_id", shipment.id)
        }
        val pi = PendingIntent.getActivity(
            context,
            shipment.id.toInt(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

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

        val notification = NotificationCompat.Builder(context, NotificationScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_fishy)
            .setContentTitle(context.getString(R.string.notif_title_emoji))
            .setContentText(context.getString(R.string.notif_content_line, timeText, portText))
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi)
            .build()
        nm.notify(shipment.id.toInt(), notification)
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
        val shipmentCalendar = Calendar.getInstance().apply {
            time = Date(shipment.scheduledDateMillis)
            val timeParts = shipment.scheduledTime.split(":")
            if (timeParts.size == 2) {
                set(Calendar.HOUR_OF_DAY, timeParts[0].toIntOrNull() ?: 9)
                set(Calendar.MINUTE, timeParts[1].toIntOrNull() ?: 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }
        val timeUntil = shipmentCalendar.timeInMillis - System.currentTimeMillis()
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
