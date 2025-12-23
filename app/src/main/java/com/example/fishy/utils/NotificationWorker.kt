package com.example.fishy.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.fishy.MainActivity
import com.example.fishy.R
import com.example.fishy.database.AppDatabase
import com.example.fishy.screens.ChecklistStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class NotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val shipmentId = inputData.getLong("shipmentId", 0L)
            if (shipmentId == 0L) {
                return@withContext Result.failure()
            }

            val database = AppDatabase.getDatabase(applicationContext)
            val scheduledShipmentDao = database.scheduledShipmentDao()

            val shipment = scheduledShipmentDao.getScheduledShipmentById(shipmentId)
            if (shipment == null) {
                println("NotificationWorker: Отгрузка не найдена, id=$shipmentId")
                return@withContext Result.failure()
            }

            // ВАЖНО: Проверяем, не отправили ли уже уведомление
            if (shipment.notificationSent) {
                println("NotificationWorker: Уведомление уже отправлено для отгрузки $shipmentId")
                return@withContext Result.success()
            }

            if (shipment.notificationEnabled && !shipment.isCompleted) {
                // Получаем чеклист перед отправкой уведомления
                val checklistItems = try {
                    scheduledShipmentDao.getChecklistItems(shipmentId).first()
                } catch (e: Exception) {
                    emptyList()
                }

                println("NotificationWorker: Чеклист отгрузки $shipmentId содержит ${checklistItems.size} пунктов")

                val checklistStatus = getChecklistStatus(shipmentId, scheduledShipmentDao)
                sendNotification(shipment, checklistStatus)

                // ВАЖНО: Обновляем только notificationSent, не трогаем isCompleted!
                scheduledShipmentDao.updateScheduledShipment(
                    shipment.copy(
                        notificationSent = true,
                        updatedAt = Date() // Обновляем время обновления
                    )
                )

                println("Уведомление успешно отправлено для отгрузки ${shipment.id}")
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            // ВАЖНО: При ошибке возвращаем retry
            Result.retry()
        }
    }

    private suspend fun getChecklistStatus(
        shipmentId: Long,
        dao: com.example.fishy.database.ScheduledShipmentDao
    ): ChecklistStatus {
        val items = try {
            dao.getChecklistItems(shipmentId).first()
        } catch (e: Exception) {
            emptyList()
        }

        // Если чеклистов нет - серый
        if (items.isEmpty()) return ChecklistStatus.EMPTY

        val completedCount = items.count { it.isCompleted }
        val totalCount = items.size

        return when {
            completedCount == totalCount && totalCount > 0 -> ChecklistStatus.COMPLETED  // Все выполнены
            completedCount > 0 -> ChecklistStatus.PARTIAL                                 // Частично выполнены
            else -> ChecklistStatus.NONE                                                  // Ни один не выполнен
        }
    }

    // NotificationWorker.kt - обновленный метод sendNotification
    private fun sendNotification(
        shipment: com.example.fishy.database.entities.ScheduledShipment,
        checklistStatus: ChecklistStatus
    ) {
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Создаем Intent для открытия планировщика
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            action = "OPEN_SCHEDULER_FROM_NOTIFICATION"
            putExtra("shipment_id", shipment.id)
            putExtra("from_notification", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            shipment.id.toInt(), // Используем ID отгрузки как requestCode для уникальности
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Создаем канал уведомлений (для Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "fishy_channel",
                "Fishy Уведомления",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о запланированных отгрузках"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Форматируем дату и время отгрузки
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val dateStr = dateFormat.format(shipment.scheduledDate)

        // Вычисляем время отгрузки в миллисекундах
        val shipmentCalendar = Calendar.getInstance().apply {
            time = shipment.scheduledDate
            val timeParts = shipment.scheduledTime.split(":")
            if (timeParts.size == 2) {
                set(Calendar.HOUR_OF_DAY, timeParts[0].toIntOrNull() ?: 9)
                set(Calendar.MINUTE, timeParts[1].toIntOrNull() ?: 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }
        val shipmentTime = shipmentCalendar.timeInMillis

        // Текущее время
        val currentTime = System.currentTimeMillis()

        // Рассчитываем разницу во времени
        val timeUntilShipment = shipmentTime - currentTime
        val daysUntil = TimeUnit.MILLISECONDS.toDays(timeUntilShipment)
        val hoursUntil = TimeUnit.MILLISECONDS.toHours(timeUntilShipment) % 24
        val minutesUntil = TimeUnit.MILLISECONDS.toMinutes(timeUntilShipment) % 60

        // Формируем текст о времени
        val timeText = when {
            daysUntil > 0 -> "Через $daysUntil дн. $hoursUntil ч."
            hoursUntil > 0 -> "Через $hoursUntil ч. $minutesUntil мин."
            minutesUntil > 0 -> "Через $minutesUntil мин."
            else -> "Сейчас"
        }

        // Формируем текст для порта
        val portText = when {
            shipment.shipmentType == "multi_port" && shipment.ports.isNotEmpty() -> {
                shipment.ports.joinToString(", ")
            }

            shipment.port.isNotEmpty() -> shipment.port
            else -> "Не указан"
        }

        // Формируем эмодзи для чек-листа
        val checklistEmoji = when (checklistStatus) {
            ChecklistStatus.COMPLETED -> "🟢"
            ChecklistStatus.PARTIAL -> "🟡"
            ChecklistStatus.NONE -> "🔴"
            ChecklistStatus.EMPTY -> "⚪"
        }

        // Формируем текст для чек-листа
        val checklistText = when (checklistStatus) {
            ChecklistStatus.COMPLETED -> "Чек-лист: выполнен"
            ChecklistStatus.PARTIAL -> "Чек-лист: выполнен частично"
            ChecklistStatus.NONE -> "Чек-лист: не выполнен"
            ChecklistStatus.EMPTY -> "Нет чек-листа"
        }

        // Создаем уведомление
        val notification = NotificationCompat.Builder(applicationContext, "fishy_channel")
            .setSmallIcon(R.drawable.fishylogo)
            .setContentTitle("⏰ Напоминание об отгрузке")
            .setContentText("$timeText: $portText")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        """
                    $timeText начнется отгрузка
                    
                    ⚓ Порт: $portText
                    📅 Дата: $dateStr
                    🕐 Время: ${shipment.scheduledTime}
                    💼 Заказчик: ${shipment.customer.takeIf { it.isNotBlank() } ?: "Не указан"}
                    ${checklistEmoji} ${checklistText}
                """.trimIndent())
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent) // Добавляем обработчик клика
            .build()

        notificationManager.notify(shipment.id.toInt(), notification)
    }
}