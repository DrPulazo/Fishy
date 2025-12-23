package com.example.fishy.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.fishy.database.AppDatabase
import com.example.fishy.utils.NotificationWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("NotificationAlarm", "Alarm получен")

        val shipmentId = intent.getLongExtra("shipment_id", 0L)
        if (shipmentId == 0L) return

        // Запускаем NotificationWorker для отправки уведомления
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getDatabase(context)
                val shipment = database.scheduledShipmentDao().getScheduledShipmentById(shipmentId)

                if (shipment != null && shipment.notificationEnabled && !shipment.notificationSent && !shipment.isCompleted) {
                    // Создаем уведомление через Worker
                    val inputData = workDataOf("shipmentId" to shipmentId)

                    // Запускаем немедленно
                    val workRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
                        .setInputData(inputData)
                        .addTag("shipment_$shipmentId")
                        .build()

                    WorkManager.getInstance(context).enqueue(workRequest)
                }
            } catch (e: Exception) {
                Log.e("NotificationAlarm", "Ошибка при обработке alarm", e)
            }
        }
    }
}