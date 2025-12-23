package com.example.fishy.utils

import android.content.Context
import android.content.SharedPreferences
import com.example.fishy.database.entities.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// Класс для хранения данных черновика со всеми режимами
data class DraftData(
    val shipment: Shipment,
    val products: List<ProductItem> = emptyList(),
    val pallets: Map<Long, List<Pallet>> = emptyMap(),
    val shipmentType: String = "mono",
    val multiPorts: List<MultiPort> = emptyList(),
    val multiVehicles: List<MultiVehicle> = emptyList(),
    val activeProductId: Long? = null
)

// Ключи для SharedPreferences
private const val PREF_NAME = "fishy_drafts"
private const val KEY_CURRENT_DRAFT = "current_draft"
private const val KEY_LAST_DRAFT_TIME = "last_draft_time"
private const val KEY_DRAFT_COUNT = "draft_count"

class DraftManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        // Максимальное время жизни черновика (24 часа)
        private const val MAX_DRAFT_AGE_MS = 24 * 60 * 60 * 1000L
    }

    /**
     * Сохранение черновика со всеми режимами
     */
    fun saveDraft(
        shipment: Shipment,
        products: List<ProductItem> = emptyList(),
        pallets: Map<Long, List<Pallet>> = emptyMap(),
        shipmentType: String = "mono",
        multiPorts: List<MultiPort> = emptyList(),
        multiVehicles: List<MultiVehicle> = emptyList(),
        activeProductId: Long? = null
    ) {
        try {
            // Создаем черновик
            val draft = DraftData(
                shipment = shipment,
                products = products,
                pallets = pallets,
                shipmentType = shipmentType,
                multiPorts = multiPorts,
                multiVehicles = multiVehicles,
                activeProductId = activeProductId
            )

            // Сериализуем в JSON
            val json = gson.toJson(draft)

            // Сохраняем в SharedPreferences
            prefs.edit().apply {
                putString(KEY_CURRENT_DRAFT, json)
                putLong(KEY_LAST_DRAFT_TIME, System.currentTimeMillis())
                val count = prefs.getInt(KEY_DRAFT_COUNT, 0)
                putInt(KEY_DRAFT_COUNT, count + 1)
            }.apply()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Загрузка черновика
     */
    fun loadDraft(): DraftData? {
        try {
            // Проверяем время последнего сохранения
            val lastSaved = prefs.getLong(KEY_LAST_DRAFT_TIME, 0)
            val currentTime = System.currentTimeMillis()

            // Если черновик слишком старый, удаляем его
            if (lastSaved > 0 && (currentTime - lastSaved) > MAX_DRAFT_AGE_MS) {
                clearDraft()
                return null
            }

            val json = prefs.getString(KEY_CURRENT_DRAFT, null)
            if (json.isNullOrEmpty()) {
                return null
            }

            // Десериализуем из JSON
            val type = object : TypeToken<DraftData>() {}.type
            return gson.fromJson(json, type)

        } catch (e: Exception) {
            e.printStackTrace()
            // При ошибке десериализации очищаем черновик
            clearDraft()
            return null
        }
    }

    /**
     * Проверка наличия черновика
     */
    fun hasDraft(): Boolean {
        val json = prefs.getString(KEY_CURRENT_DRAFT, null)
        if (json.isNullOrEmpty()) return false

        // Проверяем время создания
        val lastSaved = prefs.getLong(KEY_LAST_DRAFT_TIME, 0)
        if (lastSaved == 0L) return false

        val currentTime = System.currentTimeMillis()
        return (currentTime - lastSaved) <= MAX_DRAFT_AGE_MS
    }

    /**
     * Очистка черновика
     */
    fun clearDraft() {
        prefs.edit().apply {
            remove(KEY_CURRENT_DRAFT)
            remove(KEY_LAST_DRAFT_TIME)
            // Не очищаем счетчик, чтобы вести статистику
        }.apply()
    }

    /**
     * Получение статистики черновиков
     */
    fun getDraftStats(): DraftStats {
        val count = prefs.getInt(KEY_DRAFT_COUNT, 0)
        val lastSavedTime = prefs.getLong(KEY_LAST_DRAFT_TIME, 0)
        val hasCurrent = hasDraft()

        return DraftStats(
            totalDraftsCreated = count,
            lastSavedTime = lastSavedTime,
            hasCurrentDraft = hasCurrent,
            draftAgeMinutes = if (lastSavedTime > 0) {
                ((System.currentTimeMillis() - lastSavedTime) / (60 * 1000)).toInt()
            } else 0
        )
    }

    /**
     * Экспорт черновика в строку (для отладки)
     */
    fun exportDraftToString(): String? {
        return prefs.getString(KEY_CURRENT_DRAFT, null)
    }

    /**
     * Импорт черновика из строки (для отладки/восстановления)
     */
    fun importDraftFromString(json: String): Boolean {
        return try {
            // Пробуем десериализовать, чтобы проверить валидность
            val type = object : TypeToken<DraftData>() {}.type
            gson.fromJson<DraftData>(json, type)

            prefs.edit().apply {
                putString(KEY_CURRENT_DRAFT, json)
                putLong(KEY_LAST_DRAFT_TIME, System.currentTimeMillis())
            }.apply()

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Сохранение только общих данных отгрузки (без продукции)
     * Используется для быстрого сохранения при заполнении формы
     */
    fun saveShipmentOnly(shipment: Shipment, shipmentType: String = "mono") {
        try {
            // Загружаем текущий черновик, если есть
            val currentDraft = loadDraft() ?: DraftData(shipment = Shipment())

            // Обновляем только данные отгрузки и тип
            val updatedDraft = currentDraft.copy(
                shipment = shipment,
                shipmentType = shipmentType
            )

            // Сохраняем обратно
            val json = gson.toJson(updatedDraft)
            prefs.edit()
                .putString(KEY_CURRENT_DRAFT, json)
                .putLong(KEY_LAST_DRAFT_TIME, System.currentTimeMillis())
                .apply()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Полная очистка всех данных черновиков
     */
    fun clearAllDrafts() {
        prefs.edit().clear().apply()
    }
}

// Статистика черновиков
data class DraftStats(
    val totalDraftsCreated: Int = 0,
    val lastSavedTime: Long = 0,
    val hasCurrentDraft: Boolean = false,
    val draftAgeMinutes: Int = 0
)