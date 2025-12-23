package com.example.fishy.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.fishy.database.entities.ChecklistItem
import com.example.fishy.database.entities.ScheduledShipment
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface ScheduledShipmentDao {

    // ScheduledShipment операции

    @Query("SELECT * FROM scheduled_shipments ORDER BY scheduledDate ASC, scheduledTime ASC")
    fun getAllScheduledShipments(): Flow<List<ScheduledShipment>>

    @Query("SELECT * FROM scheduled_shipments WHERE id = :id")
    suspend fun getScheduledShipmentById(id: Long): ScheduledShipment?

    @Query("SELECT * FROM scheduled_shipments WHERE scheduledDate >= :startDate AND scheduledDate <= :endDate")
    fun getScheduledShipmentsInRange(startDate: Date, endDate: Date): Flow<List<ScheduledShipment>>

    @Query("SELECT * FROM scheduled_shipments WHERE scheduledDate = :date")
    fun getScheduledShipmentsByDate(date: Date): Flow<List<ScheduledShipment>>

    @Query("SELECT * FROM scheduled_shipments WHERE notificationEnabled = 1 AND notificationSent = 0 AND isCompleted = 0")
    suspend fun getPendingNotifications(): List<ScheduledShipment>

    // ИЗМЕНЕНО: Убрали REPLACE стратегию для вставки
    @Insert(onConflict = OnConflictStrategy.IGNORE) // или OnConflictStrategy.ABORT
    suspend fun insertScheduledShipment(scheduledShipment: ScheduledShipment): Long

    // ДОБАВЛЕНО: Метод для вставки с REPLACE только для новых отгрузок
    suspend fun insertOrReplaceScheduledShipment(scheduledShipment: ScheduledShipment): Long {
        if (scheduledShipment.id == 0L) {
            return insertScheduledShipment(scheduledShipment)
        } else {
            updateScheduledShipment(scheduledShipment)
            return scheduledShipment.id
        }
    }

    @Update
    suspend fun updateScheduledShipment(scheduledShipment: ScheduledShipment)

    @Delete
    suspend fun deleteScheduledShipment(scheduledShipment: ScheduledShipment)

    @Query("DELETE FROM scheduled_shipments WHERE id = :id")
    suspend fun deleteScheduledShipmentById(id: Long)

    // ChecklistItem операции
    @Query("SELECT * FROM checklist_items WHERE scheduledShipmentId = :shipmentId ORDER BY orderIndex ASC")
    fun getChecklistItems(shipmentId: Long): Flow<List<ChecklistItem>>

    @Insert
    suspend fun insertChecklistItem(checklistItem: ChecklistItem): Long

    @Update
    suspend fun updateChecklistItem(checklistItem: ChecklistItem)

    @Delete
    suspend fun deleteChecklistItem(checklistItem: ChecklistItem)

    @Query("DELETE FROM checklist_items WHERE scheduledShipmentId = :shipmentId")
    suspend fun deleteAllChecklistItems(shipmentId: Long)
}