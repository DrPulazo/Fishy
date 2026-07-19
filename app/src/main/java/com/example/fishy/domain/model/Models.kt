package com.example.fishy.domain.model

import java.util.concurrent.atomic.AtomicLong
import kotlinx.serialization.Serializable

/** Monotonic ids so products created in the same millisecond stay unique (forecast, find). */
private val nextEntityIdSeq = AtomicLong(System.currentTimeMillis())

fun nextEntityId(): Long = nextEntityIdSeq.incrementAndGet()

@Serializable
enum class ShipmentMode {
    MONO,
    MULTI_VEHICLE,
    MULTI_PORT,
    UNLOAD
}

@Serializable
data class Transport(
    val containerNumber: String = "",
    val truckNumber: String = "",
    val trailerNumber: String = "",
    val wagonNumber: String = "",
    val sealNumber: String = ""
) {
    fun hasUserContent(): Boolean =
        containerNumber.isNotBlank() ||
            truckNumber.isNotBlank() ||
            trailerNumber.isNotBlank() ||
            wagonNumber.isNotBlank() ||
            sealNumber.isNotBlank()
}

@Serializable
data class Pallet(
    val id: Long = nextEntityId(),
    val palletNumber: Int = 0,
    val places: Int = 0,
    val isImported: Boolean = false,
    /** Grey forecast placeholder row (4.2) */
    val isPlaceholder: Boolean = false
)

@Serializable
data class Product(
    val id: Long = nextEntityId(),
    val name: String = "",
    val manufacturer: String = "",
    val batch: String = "",
    val packageWeight: Double = 0.0,
    val quantity: Int = 0,
    val pallets: List<Pallet> = emptyList()
) {
    val palletCount: Int get() = pallets.count { !it.isPlaceholder }
    val placesCount: Int
        get() = pallets.filter { !it.isPlaceholder }.sumOf { it.places }
    val totalWeight: Double get() = packageWeight * quantity

    fun hasUserContent(): Boolean =
        name.isNotBlank() ||
            manufacturer.isNotBlank() ||
            batch.isNotBlank() ||
            packageWeight > 0.0 ||
            quantity > 0 ||
            pallets.any { !it.isPlaceholder }
}

@Serializable
data class BatchLimit(
    val id: Long = nextEntityId(),
    val productName: String = "",
    val batchName: String = "",
    val manufacturer: String = "",
    val packageWeight: Double = 0.0,
    val plannedPlaces: Int = 0
)

@Serializable
data class ChecklistTask(
    val id: Long = nextEntityId(),
    val title: String = "",
    val isCompleted: Boolean = false,
    val completedAtMillis: Long? = null
)

@Serializable
data class PortGroup(
    val id: Long = nextEntityId(),
    val port: String = "",
    val vessel: String = "",
    val doubleControlEnabled: Boolean = false,
    val products: List<Product> = emptyList()
)

@Serializable
data class VehicleGroup(
    val id: Long = nextEntityId(),
    val transport: Transport = Transport(),
    val doubleControlEnabled: Boolean = false,
    val products: List<Product> = emptyList()
)

@Serializable
data class UnloadInbound(
    val id: Long = nextEntityId(),
    /** Where cargo was loaded (e.g. port of loading for this truck). */
    val port: String = "",
    /** Transport we unload FROM (truck / container / etc.). */
    val transport: Transport = Transport(),
    val vessel: String = "",
    val products: List<Product> = emptyList()
)

@Serializable
data class UnloadReception(
    val id: Long = nextEntityId(),
    /** Optional warehouse / destination label when not only transport numbers. */
    val name: String = "",
    /** Primary destination transport (wagon / container receiving cargo). */
    val transport: Transport = Transport(),
    val inbounds: List<UnloadInbound> = emptyList()
)

@Serializable
data class ShipmentPayload(
    val mode: ShipmentMode = ShipmentMode.MONO,
    val customer: String = "",
    val port: String = "",
    val vessel: String = "",
    val transport: Transport = Transport(),
    val products: List<Product> = emptyList(),
    val multiPorts: List<PortGroup> = emptyList(),
    val multiVehicles: List<VehicleGroup> = emptyList(),
    val unloadReceptions: List<UnloadReception> = emptyList(),
    val doubleControlEnabled: Boolean = false,
    val palletForecastEnabled: Boolean = false,
    val checklistEnabled: Boolean = true,
    val batchControlEnabled: Boolean = false,
    val batchWarnThreshold: Int = 5,
    val batchLimits: List<BatchLimit> = emptyList(),
    val checklist: List<ChecklistTask> = emptyList(),
    val lastUsedVehicleId: Long? = null,
    val lastUsedProductId: Long? = null,
    val lastUsedPortId: Long? = null,
    val lastUsedUnloadReceptionId: Long? = null,
    val notes: String = "",
    val editedReportText: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val completedAtMillis: Long? = null
) {
    /** True if the user filled any meaningful field (not just empty mode scaffold). */
    fun hasUserContent(): Boolean {
        if (customer.isNotBlank() || port.isNotBlank() || vessel.isNotBlank() || notes.isNotBlank()) {
            return true
        }
        if (transport.hasUserContent()) return true
        if (products.any { it.hasUserContent() }) return true
        if (multiVehicles.any { vg ->
                vg.transport.hasUserContent() || vg.products.any { it.hasUserContent() }
            }
        ) {
            return true
        }
        if (multiPorts.any { pg ->
                pg.port.isNotBlank() ||
                    pg.vessel.isNotBlank() ||
                    pg.products.any { it.hasUserContent() }
            }
        ) {
            return true
        }
        if (unloadReceptions.any { reception ->
                reception.name.isNotBlank() ||
                    reception.transport.hasUserContent() ||
                    reception.inbounds.any { inbound ->
                        inbound.port.isNotBlank() ||
                            inbound.vessel.isNotBlank() ||
                            inbound.transport.hasUserContent() ||
                            inbound.products.any { it.hasUserContent() }
                    }
            }
        ) {
            return true
        }
        if (batchLimits.any {
                it.productName.isNotBlank() ||
                    it.batchName.isNotBlank() ||
                    it.manufacturer.isNotBlank() ||
                    it.packageWeight > 0.0 ||
                    it.plannedPlaces > 0
            }
        ) {
            return true
        }
        if (checklist.any { it.title.isNotBlank() }) return true
        return false
    }
}

enum class DictionaryType(val key: String) {
    CUSTOMER("customer"),
    PORT("port"),
    VESSEL("vessel"),
    PRODUCT("product"),
    MANUFACTURER("manufacturer")
}

enum class ShipmentEventType {
    STARTED,
    DRAFT_SAVED,
    COMPLETED,
    DUPLICATED,
    REPORT_EDITED,
    PALLET_ADDED,
    PALLET_PLACES,
    PALLET_DELETED,
    PALLET_IMPORTED,
    PRODUCT_ADDED,
    PRODUCT_DELETED,
    TRANSPORT_ADDED,
    TRANSPORT_DELETED,
    PORT_ADDED,
    PORT_DELETED,
    CHECKLIST_CHANGED,
    INPUT_GUARD_CONFIRMED,
    BATCH_LIMIT_HIT;

    val isMilestone: Boolean
        get() = when (this) {
            STARTED, DRAFT_SAVED, COMPLETED, DUPLICATED, REPORT_EDITED -> true
            else -> false
        }

    val isPallet: Boolean
        get() = when (this) {
            PALLET_ADDED, PALLET_PLACES, PALLET_DELETED, PALLET_IMPORTED -> true
            else -> false
        }
}
