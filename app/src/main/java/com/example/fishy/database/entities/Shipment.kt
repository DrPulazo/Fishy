package com.example.fishy.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import java.util.Date
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json

@Entity(tableName = "shipments")
data class Shipment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val containerNumber: String = "",
    val truckNumber: String = "",
    val trailerNumber: String = "",
    val wagonNumber: String = "",
    val sealNumber: String = "",
    val port: String = "",
    val vessel: String = "",
    val customer: String = "",
    val createdAt: Date = Date(),
    val totalProductTypes: Int = 0,
    val totalPallets: Int = 0,
    val totalPlaces: Int = 0,
    val totalWeight: Double = 0.0,
    val doubleControlEnabled: Boolean = false, // Включен ли двойной контроль
    val exportedPallets: Int = 0, // Вывезено поддонов
    val importedPallets: Int = 0,  // Завезено поддонов

    // Новые поля для мультирежимов
    @ColumnInfo(defaultValue = "mono")
    val shipmentType: String = "mono", // "mono", "multi_port", "multi_vehicle"

    @ColumnInfo(defaultValue = "")
    val multiPortData: String = "", // JSON строка с данными мультипорта

    @ColumnInfo(defaultValue = "")
    val multiVehicleData: String = "" // JSON строка с данными мультиавто
)

// Модели для мультипорта

@Serializable
data class MultiPortProduct(
    val id: Long = System.currentTimeMillis(),
    val name: String = "",
    val manufacturer: String = "",
    val batch: String = "",
    val packageWeight: Double = 0.0,
    val quantity: Int = 0,
    val palletCount: Int = 0,
    val placesCount: Int = 0,
    val totalWeight: Double = 0.0,
    val pallets: List<MultiPortPallet> = emptyList()
)

@Serializable
data class MultiPortPallet(
    val id: Long = System.currentTimeMillis(),
    val palletNumber: Int = 0,
    val places: Int = 0,
    val isImported: Boolean = false
)

// Модели для мультиавто

@Serializable
data class MultiPort(
    val id: Long = System.currentTimeMillis(),
    val port: String = "",
    val vessel: String = "",
    val doubleControlEnabled: Boolean = false, // ← ДОЛЖНО БЫТЬ ЭТО ПОЛЕ
    val products: List<MultiPortProduct> = emptyList()
)

@Serializable
data class MultiVehicle(
    val id: Long = System.currentTimeMillis(),
    val wagonNumber: String = "",
    val containerNumber: String = "",
    val truckNumber: String = "",
    val trailerNumber: String = "",
    val sealNumber: String = "",
    val doubleControlEnabled: Boolean = false, // ← Уже есть
    val products: List<MultiVehicleProduct> = emptyList()
)

@Serializable
data class MultiVehicleProduct(
    val id: Long = System.currentTimeMillis(),
    val name: String = "",
    val manufacturer: String = "",
    val batch: String = "",
    val packageWeight: Double = 0.0,
    val quantity: Int = 0,
    val palletCount: Int = 0,
    val placesCount: Int = 0,
    val totalWeight: Double = 0.0,
    val pallets: List<MultiVehiclePallet> = emptyList()
)

@Serializable
data class MultiVehiclePallet(
    val id: Long = System.currentTimeMillis(),
    val palletNumber: Int = 0,
    val places: Int = 0,
    val isImported: Boolean = false
)

// Функции для работы с JSON (ИСПРАВЛЕНО: уникальные имена)
@OptIn(InternalSerializationApi::class)
fun List<MultiPort>.toMultiPortJsonString(): String = Json.encodeToString(this)

@OptIn(InternalSerializationApi::class)
fun String.toMultiPortList(): List<MultiPort> =
    if (this.isEmpty()) emptyList() else Json.decodeFromString(this)

@OptIn(InternalSerializationApi::class)
fun List<MultiVehicle>.toMultiVehicleJsonString(): String = Json.encodeToString(this)

@OptIn(InternalSerializationApi::class)
fun String.toMultiVehicleList(): List<MultiVehicle> =
    if (this.isEmpty()) emptyList() else Json.decodeFromString(this)