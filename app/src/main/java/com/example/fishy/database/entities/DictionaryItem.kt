package com.example.fishy.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dictionary_items")
data class DictionaryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String, // "port", "customer", "product", "manufacturer"
    val value: String,
    val lastUsed: Long = System.currentTimeMillis(), // Для сортировки (последние используемые сверху)
    val usageCount: Int = 0 // Счетчик использования
)