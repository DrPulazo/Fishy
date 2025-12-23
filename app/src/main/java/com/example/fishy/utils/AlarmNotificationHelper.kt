package com.example.fishy.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.fishy.MainActivity

class AlarmNotificationHelper(private val context: Context) {

    fun scheduleExactNotification(shipmentId: Long, timeInMillis: Long) {
        try {
            // Создаем Intent для открытия MainActivity
            val intent = Intent(context, MainActivity::class.java).apply {
                action = "OPEN_SCHEDULER_FROM_NOTIFICATION"
                putExtra("shipment_id", shipmentId)
                putExtra("from_notification", true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                shipmentId.toInt(),
                intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
            )

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            // Используем AlarmManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    timeInMillis,
                    pendingIntent
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
            }

            println("Точное уведомление запланировано через AlarmManager")
        } catch (e: SecurityException) {
            println("Нет разрешения на точные уведомления: ${e.message}")
            // Fallback к WorkManager
            scheduleWithWorkManager(shipmentId, timeInMillis)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun scheduleWithWorkManager(shipmentId: Long, timeInMillis: Long) {
        // ... fallback к WorkManager
    }

    fun cancelNotification(shipmentId: Long) {
        try {
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                shipmentId.toInt(),
                intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
            )
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}