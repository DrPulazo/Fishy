package com.example.fishy.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// Модель для черновика (упрощенная - только основные поля)
data class DraftShipment(
    val containerNumber: String = "",
    val truckNumber: String = "",
    val trailerNumber: String = "",
    val wagonNumber: String = "",
    val sealNumber: String = "",
    val port: String = "",
    val vessel: String = "",
    val customer: String = "",
    val lastModified: Long = System.currentTimeMillis()
)

class DraftManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("fishy_drafts", Context.MODE_PRIVATE)
    private val gson = Gson()

    // Сохранить черновик
    fun saveDraft(draft: DraftShipment) {
        val json = gson.toJson(draft)
        prefs.edit().putString("current_draft", json).apply()
    }

    // Загрузить черновик
    fun loadDraft(): DraftShipment? {
        val json = prefs.getString("current_draft", null)
        return if (!json.isNullOrEmpty()) {
            gson.fromJson(json, DraftShipment::class.java)
        } else {
            null
        }
    }

    // Удалить черновик
    fun clearDraft() {
        prefs.edit().remove("current_draft").apply()
    }

    // Проверить есть ли черновик
    fun hasDraft(): Boolean {
        return prefs.contains("current_draft")
    }
}