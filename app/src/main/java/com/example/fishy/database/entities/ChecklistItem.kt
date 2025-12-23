package com.example.fishy.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "checklist_items",
    foreignKeys = [ForeignKey(
        entity = ScheduledShipment::class,
        parentColumns = ["id"],
        childColumns = ["scheduledShipmentId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["scheduledShipmentId"])]
)
data class ChecklistItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val scheduledShipmentId: Long = 0,
    val title: String = "",
    val isCustom: Boolean = false, // true если добавлено пользователем
    val isCompleted: Boolean = false,
    val orderIndex: Int = 0 // Для сортировки
)
