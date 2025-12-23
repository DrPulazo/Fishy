package com.example.fishy.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fishy.database.AppDatabase
import com.example.fishy.database.entities.ChecklistItem
import com.example.fishy.database.entities.DictionaryItem
import com.example.fishy.database.entities.ScheduledShipment
import com.example.fishy.screens.ChecklistStatus
import com.example.fishy.utils.NotificationHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

class SchedulerViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val scheduledShipmentDao = AppDatabase.getDatabase(application).scheduledShipmentDao()
    private val dictionaryDao = database.dictionaryDao()
    private val notificationHelper = NotificationHelper(application)

    init {
        viewModelScope.launch {
            try {
                initializeDatabase()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun initializeDatabase() {
        try {
            rescheduleAllNotifications()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Все запланированные отгрузки, отсортированные по дате и времени
    val allScheduledShipments: Flow<List<ScheduledShipment>> =
        scheduledShipmentDao.getAllScheduledShipments()

    // Справочники
    fun getDictionaryItems(type: String): Flow<List<DictionaryItem>> {
        return dictionaryDao.getItemsByType(type)
    }

    fun insertShipment(shipment: ScheduledShipment) = viewModelScope.launch {
        val newId = scheduledShipmentDao.insertScheduledShipment(shipment)

        // Обновляем shipment с новым ID
        val savedShipment = shipment.copy(id = newId)

        // Планируем уведомление, если оно включено
        if (savedShipment.notificationEnabled && !savedShipment.isCompleted) {
            notificationHelper.scheduleNotificationForShipment(savedShipment)
        }
    }

    fun updateShipment(shipment: ScheduledShipment) = viewModelScope.launch {
        scheduledShipmentDao.updateScheduledShipment(shipment)

        // Перепланируем уведомление
        if (shipment.notificationEnabled && !shipment.isCompleted) {
            notificationHelper.cancelNotificationForShipment(shipment.id)
            notificationHelper.scheduleNotificationForShipment(shipment)
        } else if (!shipment.notificationEnabled) {
            notificationHelper.cancelNotificationForShipment(shipment.id)
        }
    }

    // Функции для работы с запланированными отгрузками
    suspend fun insertScheduledShipment(shipment: ScheduledShipment): Long {
        val id = scheduledShipmentDao.insertScheduledShipment(shipment)
        // Планируем уведомление
        if (shipment.notificationEnabled) {
            notificationHelper.scheduleNotificationForShipment(shipment.copy(id = id))
        }
        return id
    }

    fun updateScheduledShipment(shipment: ScheduledShipment) {
        viewModelScope.launch {
            scheduledShipmentDao.updateScheduledShipment(shipment)

            // ВАЖНО: При любом изменении времени/даты сбрасываем статус отправки
            if (!shipment.notificationSent) {
                // Обновляем уведомление
                if (shipment.notificationEnabled) {
                    notificationHelper.cancelNotificationForShipment(shipment.id)
                    notificationHelper.scheduleNotificationForShipment(shipment)
                } else {
                    notificationHelper.cancelNotificationForShipment(shipment.id)
                }
            } else if (shipment.notificationEnabled) {
                // Если уведомление уже было отправлено, но мы меняем время,
                // нужно сбросить статус и перепланировать
                val updatedShipment = shipment.copy(notificationSent = false)
                scheduledShipmentDao.updateScheduledShipment(updatedShipment)
                notificationHelper.cancelNotificationForShipment(shipment.id)
                notificationHelper.scheduleNotificationForShipment(updatedShipment)
            }
        }
    }

    fun deleteScheduledShipment(shipment: ScheduledShipment) {
        viewModelScope.launch {
            scheduledShipmentDao.deleteScheduledShipment(shipment)
            notificationHelper.cancelNotificationForShipment(shipment.id)
        }
    }

    // Дублировать отгрузку на другой день
    fun duplicateScheduledShipment(shipment: ScheduledShipment) {
        viewModelScope.launch {
            val newDate = Calendar.getInstance().apply {
                time = Date()
                add(Calendar.DAY_OF_MONTH, 1) // Завтра
            }.time

            val duplicated = shipment.copy(
                id = 0,
                title = "${shipment.title} (копия)",
                scheduledDate = newDate,
                isCompleted = false,
                notificationSent = false,
                createdAt = Date(),
                updatedAt = Date()
            )

            val newId = scheduledShipmentDao.insertScheduledShipment(duplicated)

            // Копируем чеклисты только если они есть
            val checklistItems = scheduledShipmentDao.getChecklistItems(shipment.id).firstOrNull()
            checklistItems?.forEach { item ->
                val newItem = item.copy(
                    id = 0,
                    scheduledShipmentId = newId
                )
                scheduledShipmentDao.insertChecklistItem(newItem)
            }

            // Планируем уведомление для дубликата
            if (duplicated.notificationEnabled) {
                notificationHelper.scheduleNotificationForShipment(duplicated.copy(id = newId))
            }
        }
    }

    // Функции для работы с чеклистами
    fun getChecklistItems(shipmentId: Long): Flow<List<ChecklistItem>> {
        return scheduledShipmentDao.getChecklistItems(shipmentId)
    }

    suspend fun getChecklistStatus(shipmentId: Long): ChecklistStatus {
        val items = try {
            // ВАЖНО: Используем suspend функцию для получения чеклистов
            scheduledShipmentDao.getChecklistItems(shipmentId).first()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }

        if (items.isEmpty()) return ChecklistStatus.EMPTY

        val completedCount = items.count { it.isCompleted }
        val totalCount = items.size

        return when {
            completedCount == totalCount && totalCount > 0 -> ChecklistStatus.COMPLETED
            completedCount > 0 -> ChecklistStatus.PARTIAL
            else -> ChecklistStatus.NONE
        }
    }

    fun addChecklistItem(shipmentId: Long, title: String, isCustom: Boolean = false) {
        viewModelScope.launch {
            val checklistItemsFlow = scheduledShipmentDao.getChecklistItems(shipmentId)
            val currentItems = try {
                checklistItemsFlow.first()
            } catch (e: Exception) {
                emptyList()
            }
            val newItem = ChecklistItem(
                scheduledShipmentId = shipmentId,
                title = title,
                isCustom = isCustom,
                orderIndex = currentItems.size
            )
            scheduledShipmentDao.insertChecklistItem(newItem)
        }
    }

    fun updateChecklistItem(item: ChecklistItem) {
        viewModelScope.launch {
            scheduledShipmentDao.updateChecklistItem(item)
        }
    }

    fun deleteChecklistItem(item: ChecklistItem) {
        viewModelScope.launch {
            scheduledShipmentDao.deleteChecklistItem(item)
        }
    }

    // Функции для работы со справочниками
    fun addDictionaryItem(type: String, value: String) {
        viewModelScope.launch {
            val existing = dictionaryDao.getItemByTypeAndValue(type, value)
            if (existing == null) {
                // ИСПРАВЛЕНО: используйте правильный конструктор
                val newItem = DictionaryItem(
                    type = type,
                    value = value,
                    lastUsed = System.currentTimeMillis(),
                    usageCount = 1 // Начинаем с 1 при первом добавлении
                )
                dictionaryDao.insertDictionaryItem(newItem)
            } else {
                // Увеличиваем счетчик использования и обновляем время
                dictionaryDao.updateLastUsed(
                    existing.id,
                    System.currentTimeMillis(),
                    existing.usageCount + 1
                )
            }
        }
    }

    // Метод для обновления только lastUsed (при выборе из списка)
    fun updateDictionaryLastUsed(type: String, value: String) {
        viewModelScope.launch {
            val existing = dictionaryDao.getItemByTypeAndValue(type, value)
            if (existing != null) {
                // Обновляем только время, не меняем счетчик
                dictionaryDao.updateLastUsed(
                    existing.id,
                    System.currentTimeMillis(),
                    existing.usageCount
                )
            }
        }
    }

    // Функции для уведомлений
    fun rescheduleAllNotifications() {
        notificationHelper.rescheduleAllNotifications()
    }

    // Начать отгрузку из планировщика
    fun startShipmentFromScheduler(
        scheduledShipment: ScheduledShipment,
        shipmentViewModel: ShipmentViewModel
    ) {
        viewModelScope.launch {
            // Просто передаем ID запланированной отгрузки
            // ShipmentViewModel сам загрузит данные через loadFromScheduledShipment
            shipmentViewModel.loadFromScheduledShipment(scheduledShipment.id)

            // Помечаем как завершенную в планировщике
            scheduledShipmentDao.updateScheduledShipment(
                scheduledShipment.copy(
                    isCompleted = true,
                    updatedAt = Date()
                )
            )
        }
    }
    fun fixExistingShipmentsNotifications() = viewModelScope.launch {
        val shipments = scheduledShipmentDao.getAllScheduledShipments().first()
        shipments.forEach { shipment ->
            if (shipment.notificationEnabled && !shipment.isCompleted) {
                // Устанавливаем стандартные значения для старых отгрузок
                val fixedShipment = shipment.copy(
                    notificationDaysBefore = 0,
                    notificationHoursBefore = 1,
                    notificationTime = "09:00"
                )
                scheduledShipmentDao.updateScheduledShipment(fixedShipment)
                notificationHelper.scheduleNotificationForShipment(fixedShipment)
            }
        }
    }

    // Методы для работы со справочниками (для TemplatesScreen)
    fun deleteDictionaryItem(item: DictionaryItem) {
        viewModelScope.launch {
            dictionaryDao.deleteDictionaryItem(item)
        }
    }

    fun updateDictionaryItem(item: DictionaryItem) {
        viewModelScope.launch {
            dictionaryDao.updateDictionaryItem(item)
        }
    }

    // Получить все элементы справочника определенного типа (синхронно)
    suspend fun getDictionaryItemsByType(type: String): List<DictionaryItem> {
        return dictionaryDao.getItemsByType(type).first()
    }

    suspend fun getAllDictionaryItemsForDebug(): List<DictionaryItem> {
        return dictionaryDao.getAllItems()
    }

    // Явный метод для добавления элемента с возвратом результата
    suspend fun addDictionaryItemAndRefresh(type: String, value: String): Boolean {
        return try {
            val existing = dictionaryDao.getItemByTypeAndValue(type, value)
            if (existing == null) {
                val newItem = DictionaryItem(
                    type = type,
                    value = value,
                    lastUsed = System.currentTimeMillis(),
                    usageCount = 1
                )
                val id = dictionaryDao.insertDictionaryItem(newItem)
                println("DEBUG: Добавлен элемент с ID: $id, type: $type, value: $value")
                true
            } else {
                dictionaryDao.updateLastUsed(existing.id, System.currentTimeMillis())
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Метод для принудительного обновления Flow
    private val _refreshTrigger = MutableStateFlow(0)
    val refreshTrigger: StateFlow<Int> = _refreshTrigger

    fun triggerRefresh() {
        _refreshTrigger.value++
    }

    // Получить элемент справочника по ID
    suspend fun getDictionaryItemById(id: Long): DictionaryItem? {
        return dictionaryDao.getItemById(id)
    }
}