package com.example.fishy.database

import androidx.room.*
import com.example.fishy.database.entities.DictionaryItem
import kotlinx.coroutines.flow.Flow

@Dao
interface DictionaryDao {

    @Query("SELECT * FROM dictionary_items WHERE type = :type ORDER BY lastUsed DESC, usageCount DESC")
    fun getItemsByType(type: String): Flow<List<DictionaryItem>>

    @Query("SELECT * FROM dictionary_items WHERE type = :type AND value = :value")
    suspend fun getItemByTypeAndValue(type: String, value: String): DictionaryItem?

    @Query("SELECT * FROM dictionary_items WHERE id = :id")
    suspend fun getItemById(id: Long): DictionaryItem?

    @Insert
    suspend fun insertDictionaryItem(item: DictionaryItem): Long

    @Update
    suspend fun updateDictionaryItem(item: DictionaryItem)

    @Delete
    suspend fun deleteDictionaryItem(item: DictionaryItem)

    @Query("DELETE FROM dictionary_items WHERE type = :type AND value = :value")
    suspend fun deleteByTypeAndValue(type: String, value: String)

    @Query("UPDATE dictionary_items SET lastUsed = :timestamp, usageCount = usageCount + 1 WHERE id = :id")
    suspend fun updateLastUsed(id: Long, timestamp: Long)

    @Query("UPDATE dictionary_items SET lastUsed = :lastUsed, usageCount = :usageCount WHERE id = :id")
    suspend fun updateLastUsed(id: Long, lastUsed: Long, usageCount: Int)

    // ДОБАВЬТЕ этот метод для отладки
    @Query("SELECT * FROM dictionary_items")
    suspend fun getAllItems(): List<DictionaryItem>
}