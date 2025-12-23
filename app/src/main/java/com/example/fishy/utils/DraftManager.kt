package com.example.fishy.utils

import android.content.Context
import android.content.SharedPreferences
import com.example.fishy.database.entities.MultiPort
import com.example.fishy.database.entities.MultiVehicle
import com.example.fishy.database.entities.Pallet
import com.example.fishy.database.entities.ProductItem
import com.example.fishy.database.entities.Shipment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Класс для хранения данных черновика со всеми режимами
data class DraftData(
    val id: Long = System.currentTimeMillis(),
    val name: String = "",
    val shipment: Shipment,
    val products: List<ProductItem> = emptyList(),
    val pallets: Map<Long, List<Pallet>> = emptyMap(),
    val shipmentType: String = "mono",
    val multiPorts: List<MultiPort> = emptyList(),
    val multiVehicles: List<MultiVehicle> = emptyList(),
    val activeProductId: Long? = null,
    val createdAt: Date = Date(),
    val lastModified: Date = Date()
)

// Ключи для SharedPreferences
private const val PREF_NAME = "fishy_drafts"
private const val KEY_DRAFTS_LIST = "drafts_list"

class DraftManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        // Максимальное количество черновиков
        private const val MAX_DRAFTS_COUNT = 50
    }

    /**
     * Сохранение нового черновика или обновление существующего
     */
    fun saveDraft(
        id: Long? = null,
        name: String = generateDraftName(),
        shipment: Shipment,
        products: List<ProductItem> = emptyList(),
        pallets: Map<Long, List<Pallet>> = emptyMap(),
        shipmentType: String = "mono",
        multiPorts: List<MultiPort> = emptyList(),
        multiVehicles: List<MultiVehicle> = emptyList(),
        activeProductId: Long? = null
    ): Long {
        return try {
            val drafts = getDrafts().toMutableList()

            // Генерируем ID для нового черновика
            val draftId = id ?: System.currentTimeMillis()

            // Находим существующий черновик, если обновляем
            val existingDraft = drafts.find { it.id == draftId }

            // Создаем/обновляем черновик
            val draft = DraftData(
                id = draftId,
                name = name,
                shipment = shipment,
                products = products,
                pallets = pallets,
                shipmentType = shipmentType,
                multiPorts = multiPorts,
                multiVehicles = multiVehicles,
                activeProductId = activeProductId,
                createdAt = existingDraft?.createdAt ?: Date(),
                lastModified = Date()
            )

            // Удаляем старую версию, если существует
            drafts.removeAll { it.id == draftId }

            // Добавляем новую версику в начало списка
            drafts.add(0, draft)

            // Ограничиваем количество черновиков (удаляем самые старые)
            val trimmedDrafts = if (drafts.size > MAX_DRAFTS_COUNT)
                drafts.take(MAX_DRAFTS_COUNT)
            else drafts

            // Сохраняем список черновиков
            val json = gson.toJson(trimmedDrafts)
            prefs.edit()
                .putString(KEY_DRAFTS_LIST, json)
                .apply()

            draftId

        } catch (e: Exception) {
            e.printStackTrace()
            0L
        }
    }

    /**
     * Получение списка всех черновиков
     */
    fun getDrafts(): List<DraftData> {
        return try {
            val json = prefs.getString(KEY_DRAFTS_LIST, null)
            if (json.isNullOrEmpty()) {
                emptyList()
            } else {
                val type = object : TypeToken<List<DraftData>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Получение черновика по ID
     */
    fun getDraft(id: Long): DraftData? {
        return getDrafts().firstOrNull { it.id == id }
    }

    /**
     * Получение последнего черновика (для обратной совместимости)
     */
    fun getLastDraft(): DraftData? {
        return getDrafts().firstOrNull()
    }

    /**
     * Удаление черновика по ID
     */
    fun deleteDraft(id: Long) {
        try {
            val drafts = getDrafts().filter { it.id != id }
            val json = gson.toJson(drafts)
            prefs.edit()
                .putString(KEY_DRAFTS_LIST, json)
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Удаление всех черновиков
     */
    fun clearAllDrafts() {
        prefs.edit()
            .remove(KEY_DRAFTS_LIST)
            .apply()
    }

    /**
     * Обновление имени черновика
     */
    fun updateDraftName(id: Long, newName: String): Boolean {
        val draft = getDraft(id) ?: return false

        return saveDraft(
            id = id,
            name = newName,
            shipment = draft.shipment,
            products = draft.products,
            pallets = draft.pallets,
            shipmentType = draft.shipmentType,
            multiPorts = draft.multiPorts,
            multiVehicles = draft.multiVehicles,
            activeProductId = draft.activeProductId
        ) != 0L
    }

    /**
     * Генерация имени для черновика
     */
    private fun generateDraftName(): String {
        val date = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date())
        return "Черновик ($date)"
    }

    /**
     * Проверка наличия черновиков
     */
    fun hasDrafts(): Boolean {
        return getDrafts().isNotEmpty()
    }

    /**
     * Получение количества черновиков
     */
    fun getDraftCount(): Int {
        return getDrafts().size
    }

    /**
     * Сохранение только общих данных отгрузки (для обратной совместимости)
     */
    fun saveShipmentOnly(shipment: Shipment, shipmentType: String = "mono"): Long {
        return saveDraft(
            shipment = shipment,
            shipmentType = shipmentType,
            products = emptyList(),
            pallets = emptyMap(),
            multiPorts = emptyList(),
            multiVehicles = emptyList()
        )
    }
}