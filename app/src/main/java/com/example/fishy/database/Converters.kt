package com.example.fishy.database

import androidx.room.TypeConverter
import com.example.fishy.database.entities.ScheduledProduct
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*

class Converters {

    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun fromStringList(value: String?): List<String> {
        return value?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    }

    @TypeConverter
    fun fromListString(list: List<String>?): String {
        return list?.joinToString(",") ?: ""
    }

    // Добавляем конвертеры для нового поля
    @TypeConverter
    fun fromScheduledProductsJson(value: String?): List<ScheduledProduct> {
        return if (value.isNullOrEmpty()) emptyList()
        else try {
            Json.decodeFromString(value)
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun toScheduledProductsJson(products: List<ScheduledProduct>?): String {
        return if (products.isNullOrEmpty()) ""
        else Json.encodeToString(products)
    }
}