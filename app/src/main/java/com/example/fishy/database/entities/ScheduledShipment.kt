package com.example.fishy.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.example.fishy.database.Converters
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*

@Entity(tableName = "scheduled_shipments")
@TypeConverters(Converters::class)
data class ScheduledShipment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Основные данные
    val title: String = "",
    val scheduledDate: Date = Date(),
    val scheduledTime: String = "",
    val shipmentType: String = "mono",

    // Для мультипорта: список портов и времени
    val ports: List<String> = emptyList(),
    val portTimes: List<String> = emptyList(),

    // Для мультиавто: количество транспорта
    val vehicleCount: Int = 1,

    // Общие данные отгрузки
    val customer: String = "",
    val port: String = "", // Для mono и общего порта
    val vessel: String = "",

    // Транспортные данные (для mono и общего в мультирежимах)
    val containerNumber: String = "",
    val truckNumber: String = "",
    val trailerNumber: String = "",
    val wagonNumber: String = "",
    val sealNumber: String = "",

    // Продукция (старые поля для обратной совместимости)
    val productName: String = "",
    val manufacturer: String = "",
    val batch: String = "",
    val quantity: Int = 0,
    val packageWeight: Double = 0.0,
    val totalWeight: Double = 0.0,

    // Новое: структурированные данные в JSON
    val productsJson: String = "",

    // Уведомления
    val notificationEnabled: Boolean = true,
    val notificationDate: Date? = null,
    val notificationTime: String = "09:00",
    val notificationDaysBefore: Int = 0,
    val notificationHoursBefore: Int = 1,
    val notificationSent: Boolean = false,

    // Статус
    val isCompleted: Boolean = false,

    // Даты
    val createdAt: Date = Date(),
    val updatedAt: Date = Date()
)

// Модель для продукта в планировщике
@Serializable
data class ScheduledProduct(
    val id: Long = System.currentTimeMillis(),
    val name: String = "",
    val manufacturer: String = "",
    val batch: String = "",
    val packageWeight: Double = 0.0,
    val quantity: Int = 0,
    val totalWeight: Double = 0.0
) {
    companion object {
        fun fromJson(json: String): List<ScheduledProduct> {
            return if (json.isEmpty()) emptyList()
            else try {
                Json.decodeFromString(json)
            } catch (e: Exception) {
                emptyList()
            }
        }

        fun toJson(products: List<ScheduledProduct>): String {
            return Json.encodeToString(products)
        }
    }
}

// Структурированные данные для мультипорта
@Serializable
data class ScheduledPortData(
    val portName: String,
    val portTime: String = "",
    val products: List<ScheduledProduct> = emptyList(),
    val vessel: String = ""
)

// Структурированные данные для мультиавто
@Serializable
data class ScheduledVehicleData(
    val vehicleId: Long = System.currentTimeMillis(),
    val containerNumber: String = "",
    val truckNumber: String = "",
    val trailerNumber: String = "",
    val wagonNumber: String = "",
    val sealNumber: String = "",
    val products: List<ScheduledProduct> = emptyList()
)

// Функции расширения для удобства
fun ScheduledShipment.getProducts(): List<ScheduledProduct> {
    return when {
        this.productsJson.isEmpty() -> {
            // Конвертируем старые данные, если они есть
            if (this.productName.isNotEmpty()) {
                listOf(ScheduledProduct(
                    name = this.productName,
                    manufacturer = this.manufacturer,
                    batch = this.batch,
                    packageWeight = this.packageWeight,
                    quantity = this.quantity,
                    totalWeight = this.totalWeight
                ))
            } else {
                emptyList()
            }
        }
        else -> {
            try {
                // Пытаемся распарсить как список продуктов (для mono)
                Json.decodeFromString<List<ScheduledProduct>>(this.productsJson)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}

fun ScheduledShipment.getPortData(): List<ScheduledPortData> {
    if (this.shipmentType != "multi_port") return emptyList()

    return if (this.productsJson.isNotEmpty()) {
        try {
            Json.decodeFromString<List<ScheduledPortData>>(this.productsJson)
        } catch (e: Exception) {
            // Если не получается распарсить как структурированные данные,
            // создаем из старых полей
            val allProducts = getProducts()
            if (ports.isNotEmpty()) {
                ports.mapIndexed { index, portName ->
                    val portTime = if (index < portTimes.size) portTimes[index] else ""
                    ScheduledPortData(
                        portName = portName,
                        portTime = portTime,
                        products = allProducts, // Все продукты для всех портов (старая логика)
                        vessel = this.vessel
                    )
                }
            } else {
                emptyList()
            }
        }
    } else {
        emptyList()
    }
}

fun ScheduledShipment.getVehicleData(): List<ScheduledVehicleData> {
    if (this.shipmentType != "multi_vehicle") return emptyList()

    return if (this.productsJson.isNotEmpty()) {
        try {
            Json.decodeFromString<List<ScheduledVehicleData>>(this.productsJson)
        } catch (e: Exception) {
            // Если не получается распарсить как структурированные данные,
            // создаем из старых полей
            val allProducts = getProducts()
            val vehicles = mutableListOf<ScheduledVehicleData>()

            for (i in 0 until this.vehicleCount) {
                vehicles.add(ScheduledVehicleData(
                    containerNumber = if (this.containerNumber.isNotEmpty()) "${this.containerNumber}-${i+1}" else "",
                    truckNumber = if (this.truckNumber.isNotEmpty()) "${this.truckNumber}-${i+1}" else "",
                    trailerNumber = if (this.trailerNumber.isNotEmpty()) "${this.trailerNumber}-${i+1}" else "",
                    wagonNumber = if (this.wagonNumber.isNotEmpty()) "${this.wagonNumber}-${i+1}" else "",
                    sealNumber = if (this.sealNumber.isNotEmpty()) "${this.sealNumber}-${i+1}" else "",
                    products = allProducts // Все продукты для всех транспортов (старая логика)
                ))
            }

            vehicles
        }
    } else {
        emptyList()
    }
}

fun ScheduledShipment.calculateTotalWeight(): Double {
    return when (this.shipmentType) {
        "mono" -> getProducts().sumOf { it.totalWeight }
        "multi_port" -> getPortData().flatMap { it.products }.sumOf { it.totalWeight }
        "multi_vehicle" -> getVehicleData().flatMap { it.products }.sumOf { it.totalWeight }
        else -> this.totalWeight
    }
}