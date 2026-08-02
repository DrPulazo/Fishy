package com.example.fishy.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shipments")
data class ShipmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val payloadJson: String,
    val customer: String = "",
    val port: String = "",
    val mode: String = "MONO",
    val totalPlaces: Int = 0,
    val totalWeight: Double = 0.0,
    val transportSummary: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val completedAtMillis: Long = System.currentTimeMillis(),
    val isDraft: Boolean = false,
    val draftName: String = ""
)

@Entity(tableName = "scheduled_shipments")
data class ScheduledShipmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val scheduledDateMillis: Long = System.currentTimeMillis(),
    val scheduledTime: String = "13:00",
    val mode: String = "MONO",
    val customer: String = "",
    val port: String = "",
    val vessel: String = "",
    val payloadJson: String = "",
    val notificationEnabled: Boolean = true,
    val notificationAtMillis: Long? = null,
    val notificationSent: Boolean = false,
    /** True after the start-time reminder has been delivered. */
    val startNotificationSent: Boolean = false,
    val isCompleted: Boolean = false,
    /** Draft shipment id while this plan is in progress; null = not started. */
    val linkedDraftId: Long? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "checklist_items")
data class ChecklistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduledShipmentId: Long,
    val title: String,
    val isCompleted: Boolean = false,
    val sortOrder: Int = 0
)

/** One-shot prep reminders for a scheduled shipment (not the start-time alarm). */
@Entity(tableName = "scheduled_reminders")
data class ScheduledReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduledShipmentId: Long,
    val atMillis: Long,
    val sent: Boolean = false,
    val sortOrder: Int = 0
)

@Entity(tableName = "dictionary_items")
data class DictionaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val value: String,
    /** Last time this value was used in a shipment / added; newest first in dropdowns. */
    val lastUsedAtMillis: Long = 0L
)

@Entity(tableName = "shipment_events")
data class ShipmentEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shipmentKey: String,
    val type: String,
    val message: String,
    val timestampMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "report_templates")
data class ReportTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val body: String,
    val customerBinding: String = ""
)
