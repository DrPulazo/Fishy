package com.example.fishy.utils

import android.content.Context
import androidx.work.*
import com.example.fishy.database.AppDatabase
import com.example.fishy.database.entities.ScheduledShipment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.TimeUnit

class NotificationHelper(private val context: Context) {

    private val workManager = WorkManager.getInstance(context)
    private val database = AppDatabase.getDatabase(context)
    private val scheduledShipmentDao = database.scheduledShipmentDao()

    fun scheduleNotificationForShipment(shipment: ScheduledShipment) {
        if (!shipment.notificationEnabled) {
            cancelNotificationForShipment(shipment.id)
            return
        }

        val notificationTime = calculateNotificationTime(shipment)
        val now = System.currentTimeMillis()

        // Если время уже прошло
        if (notificationTime <= now) {
            CoroutineScope(Dispatchers.IO).launch {
                scheduledShipmentDao.updateScheduledShipment(
                    shipment.copy(notificationSent = true)
                )
            }
            return
        }

        val delay = notificationTime - now
        val inputData = workDataOf("shipmentId" to shipment.id)

        // Простой WorkManager без setExpedited
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresCharging(false)
            .setRequiresBatteryNotLow(false)
            .build()

        val notificationWork = OneTimeWorkRequestBuilder<NotificationWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(inputData)
            .setConstraints(constraints)
            .addTag("shipment_${shipment.id}")
            .build()

        workManager.enqueueUniqueWork(
            "shipment_${shipment.id}",
            ExistingWorkPolicy.REPLACE,
            notificationWork
        )

        println("Уведомление запланировано для отгрузки ${shipment.id}")
    }

    fun cancelNotificationForShipment(shipmentId: Long) {
        workManager.cancelUniqueWork("shipment_$shipmentId")
        workManager.cancelAllWorkByTag("shipment_$shipmentId")

        CoroutineScope(Dispatchers.IO).launch {
            scheduledShipmentDao.getScheduledShipmentById(shipmentId)?.let { shipment ->
                scheduledShipmentDao.updateScheduledShipment(
                    shipment.copy(notificationSent = false)
                )
            }
        }
    }

    fun rescheduleAllNotifications() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val allShipments = scheduledShipmentDao.getAllScheduledShipments().first()

                // Отменяем все старые уведомления
                workManager.cancelAllWorkByTag("shipment_")

                // Планируем уведомления для всех активных отгрузок
                allShipments.forEach { shipment ->
                    if (shipment.notificationEnabled && !shipment.isCompleted && !shipment.notificationSent) {
                        scheduleNotificationForShipment(shipment)
                    }
                }
                println("Все уведомления перепланированы, найдено ${allShipments.size} отгрузок")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun calculateNotificationTime(shipment: ScheduledShipment): Long {
        val calendar = Calendar.getInstance()

        // Если указана конкретная дата уведомления
        if (shipment.notificationDate != null) {
            calendar.time = shipment.notificationDate!!
        } else {
            // Рассчитываем от даты отгрузки
            calendar.time = shipment.scheduledDate
            val timeParts = shipment.scheduledTime.split(":")
            if (timeParts.size == 2) {
                calendar.set(Calendar.HOUR_OF_DAY, timeParts[0].toIntOrNull() ?: 9)
                calendar.set(Calendar.MINUTE, timeParts[1].toIntOrNull() ?: 0)
            }
            // Вычитаем дни и часы
            calendar.add(Calendar.DAY_OF_YEAR, -shipment.notificationDaysBefore)
            calendar.add(Calendar.HOUR_OF_DAY, -shipment.notificationHoursBefore)
        }

        // Устанавливаем время уведомления
        val notificationTimeParts = shipment.notificationTime.split(":")
        if (notificationTimeParts.size == 2) {
            calendar.set(Calendar.HOUR_OF_DAY, notificationTimeParts[0].toIntOrNull() ?: 9)
            calendar.set(Calendar.MINUTE, notificationTimeParts[1].toIntOrNull() ?: 0)
        }

        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}