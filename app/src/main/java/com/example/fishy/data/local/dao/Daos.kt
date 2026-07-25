package com.example.fishy.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.fishy.data.local.entity.ChecklistItemEntity
import com.example.fishy.data.local.entity.DictionaryEntity
import com.example.fishy.data.local.entity.ReportTemplateEntity
import com.example.fishy.data.local.entity.ScheduledReminderEntity
import com.example.fishy.data.local.entity.ScheduledShipmentEntity
import com.example.fishy.data.local.entity.ShipmentEntity
import com.example.fishy.data.local.entity.ShipmentEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShipmentDao {
    @Query("SELECT * FROM shipments WHERE isDraft = 0 ORDER BY completedAtMillis DESC")
    fun observeArchive(): Flow<List<ShipmentEntity>>

    @Query("SELECT * FROM shipments WHERE isDraft = 1 ORDER BY completedAtMillis DESC")
    fun observeDrafts(): Flow<List<ShipmentEntity>>

    @Query("SELECT * FROM shipments WHERE isDraft = 1 ORDER BY completedAtMillis DESC")
    suspend fun getDrafts(): List<ShipmentEntity>

    @Query("SELECT * FROM shipments WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ShipmentEntity?

    @Query(
        """
        SELECT * FROM shipments WHERE isDraft = 0
        AND completedAtMillis BETWEEN :from AND :to
        AND (:customer = '' OR customer = :customer)
        AND (:port = '' OR port = :port)
        ORDER BY completedAtMillis DESC
        """
    )
    suspend fun filterStats(from: Long, to: Long, customer: String, port: String): List<ShipmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ShipmentEntity): Long

    @Delete
    suspend fun delete(entity: ShipmentEntity)

    @Query("DELETE FROM shipments WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ScheduledShipmentDao {
    @Query("SELECT * FROM scheduled_shipments WHERE isCompleted = 0 ORDER BY scheduledDateMillis ASC")
    fun observeAll(): Flow<List<ScheduledShipmentEntity>>

    @Query("SELECT * FROM scheduled_shipments WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ScheduledShipmentEntity?

    @Query(
        """
        SELECT * FROM scheduled_shipments
        WHERE isCompleted = 0
          AND (
            startNotificationSent = 0
            OR (
              notificationEnabled = 1
              AND EXISTS (
                SELECT 1 FROM scheduled_reminders r
                WHERE r.scheduledShipmentId = scheduled_shipments.id AND r.sent = 0
              )
            )
          )
        """
    )
    suspend fun getPendingNotifications(): List<ScheduledShipmentEntity>

    @Query("SELECT id FROM scheduled_shipments")
    suspend fun getAllIds(): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ScheduledShipmentEntity): Long

    @Update
    suspend fun update(entity: ScheduledShipmentEntity)

    @Query("DELETE FROM scheduled_shipments WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM checklist_items WHERE scheduledShipmentId = :shipmentId ORDER BY sortOrder ASC")
    fun observeChecklist(shipmentId: Long): Flow<List<ChecklistItemEntity>>

    @Query("SELECT * FROM checklist_items WHERE scheduledShipmentId = :shipmentId ORDER BY sortOrder ASC")
    suspend fun getChecklist(shipmentId: Long): List<ChecklistItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChecklistItem(item: ChecklistItemEntity): Long

    @Query("DELETE FROM checklist_items WHERE id = :id")
    suspend fun deleteChecklistItem(id: Long)

    @Query("DELETE FROM checklist_items WHERE scheduledShipmentId = :shipmentId")
    suspend fun deleteChecklistForShipment(shipmentId: Long)

    @Query("SELECT * FROM scheduled_reminders WHERE scheduledShipmentId = :shipmentId ORDER BY sortOrder ASC, atMillis ASC")
    fun observeReminders(shipmentId: Long): Flow<List<ScheduledReminderEntity>>

    @Query("SELECT * FROM scheduled_reminders WHERE scheduledShipmentId = :shipmentId ORDER BY sortOrder ASC, atMillis ASC")
    suspend fun getReminders(shipmentId: Long): List<ScheduledReminderEntity>

    @Query("SELECT * FROM scheduled_reminders WHERE id = :id LIMIT 1")
    suspend fun getReminderById(id: Long): ScheduledReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReminder(item: ScheduledReminderEntity): Long

    @Query("DELETE FROM scheduled_reminders WHERE id = :id")
    suspend fun deleteReminder(id: Long)

    @Query("DELETE FROM scheduled_reminders WHERE scheduledShipmentId = :shipmentId")
    suspend fun deleteRemindersForShipment(shipmentId: Long)

    @Query(
        """
        DELETE FROM scheduled_reminders
        WHERE scheduledShipmentId = :shipmentId
          AND sent = 1
        """
    )
    suspend fun pruneDueReminders(shipmentId: Long): Int

    @Query(
        """
        DELETE FROM scheduled_reminders
        WHERE scheduledShipmentId = :shipmentId
          AND atMillis <= :nowMillis
        """
    )
    suspend fun deletePastReminders(shipmentId: Long, nowMillis: Long): Int

    @Query("SELECT * FROM scheduled_shipments WHERE isCompleted = 0")
    suspend fun getActiveScheduled(): List<ScheduledShipmentEntity>
}

@Dao
interface DictionaryDao {
    @Query(
        """
        SELECT * FROM dictionary_items WHERE type = :type
        ORDER BY lastUsedAtMillis DESC, value COLLATE NOCASE ASC
        """
    )
    fun observeByType(type: String): Flow<List<DictionaryEntity>>

    @Query(
        """
        SELECT * FROM dictionary_items WHERE type = :type
        ORDER BY lastUsedAtMillis DESC, value COLLATE NOCASE ASC
        """
    )
    suspend fun getByType(type: String): List<DictionaryEntity>

    @Query("SELECT * FROM dictionary_items WHERE type = :type AND value = :value LIMIT 1")
    suspend fun findByTypeAndValue(type: String, value: String): DictionaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: DictionaryEntity): Long

    @Query("DELETE FROM dictionary_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM dictionary_items WHERE type = :type AND value = :value")
    suspend fun count(type: String, value: String): Int
}

@Dao
interface EventDao {
    @Query("SELECT * FROM shipment_events WHERE shipmentKey = :key ORDER BY timestampMillis ASC")
    fun observeForKey(key: String): Flow<List<ShipmentEventEntity>>

    @Query("SELECT DISTINCT shipmentKey FROM shipment_events WHERE type = :type")
    fun observeKeysByType(type: String): Flow<List<String>>

    @Query("UPDATE shipment_events SET shipmentKey = :newKey WHERE shipmentKey = :oldKey")
    suspend fun rekey(oldKey: String, newKey: String)

    @Query("DELETE FROM shipment_events WHERE shipmentKey = :key")
    suspend fun deleteByKey(key: String)

    @Insert
    suspend fun insert(event: ShipmentEventEntity): Long
}

@Dao
interface ReportTemplateDao {
    @Query("SELECT * FROM report_templates ORDER BY name ASC")
    fun observeAll(): Flow<List<ReportTemplateEntity>>

    @Query("SELECT * FROM report_templates WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ReportTemplateEntity?

    @Query("SELECT * FROM report_templates WHERE customerBinding = :customer LIMIT 1")
    suspend fun getForCustomer(customer: String): ReportTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReportTemplateEntity): Long

    @Query("DELETE FROM report_templates WHERE id = :id")
    suspend fun deleteById(id: Long)
}
