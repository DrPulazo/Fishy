package com.example.fishy.domain.model

import com.example.fishy.data.local.entity.ShipmentEntity
import com.example.fishy.domain.calc.ShipmentCalculator
import kotlin.math.abs

/**
 * Card / list summaries from a shipment payload.
 * Transport uses the same XOR rule as the form: wagon XOR road set.
 */
object ShipmentSummaries {

    fun ports(payload: ShipmentPayload): List<String> = when (payload.mode) {
        ShipmentMode.MONO, ShipmentMode.MULTI_VEHICLE ->
            listOf(payload.port).filter { it.isNotBlank() }
        ShipmentMode.MULTI_PORT ->
            payload.multiPorts.map { it.port.trim() }.filter { it.isNotBlank() }.distinct()
        ShipmentMode.UNLOAD ->
            payload.unloadReceptions
                .flatMap { reception -> reception.inbounds.map { it.port.trim() } }
                .filter { it.isNotBlank() }
                .distinct()
    }

    fun transportLabels(payload: ShipmentPayload): List<String> = when (payload.mode) {
        ShipmentMode.MONO, ShipmentMode.MULTI_PORT ->
            listOfNotNull(transportLabel(payload.transport))
        ShipmentMode.MULTI_VEHICLE ->
            payload.multiVehicles.mapNotNull { transportLabel(it.transport) }
        ShipmentMode.UNLOAD ->
            payload.unloadReceptions.flatMap { reception ->
                listOfNotNull(transportLabel(reception.transport)) +
                    reception.inbounds.mapNotNull { transportLabel(it.transport) }
            }.distinct()
    }

    fun receptionPoints(payload: ShipmentPayload): List<String> =
        if (payload.mode != ShipmentMode.UNLOAD) emptyList()
        else payload.unloadReceptions.map { it.name.trim() }.filter { it.isNotBlank() }.distinct()

    /** Wagon wins; otherwise container / truck / trailer (trailer only without container). */
    fun transportLabel(transport: Transport): String? {
        val wagon = transport.wagonNumber.trim()
        if (wagon.isNotEmpty()) return wagon
        val parts = mutableListOf<String>()
        val container = transport.containerNumber.trim()
        val truck = transport.truckNumber.trim()
        val trailer = transport.trailerNumber.trim()
        if (container.isNotEmpty()) parts += container
        if (truck.isNotEmpty()) parts += truck
        if (trailer.isNotEmpty() && container.isEmpty()) parts += trailer
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" • ")
    }

    enum class TransportKind { WAGON, CONTAINER, TRUCK }

    fun transportKind(transport: Transport): TransportKind? {
        if (transport.wagonNumber.isNotBlank()) return TransportKind.WAGON
        if (transport.containerNumber.isNotBlank()) return TransportKind.CONTAINER
        if (transport.truckNumber.isNotBlank() || transport.trailerNumber.isNotBlank()) {
            return TransportKind.TRUCK
        }
        return null
    }

    fun allTransports(payload: ShipmentPayload): List<Transport> = when (payload.mode) {
        ShipmentMode.MONO, ShipmentMode.MULTI_PORT -> listOf(payload.transport)
        ShipmentMode.MULTI_VEHICLE -> payload.multiVehicles.map { it.transport }
        ShipmentMode.UNLOAD -> payload.unloadReceptions.flatMap { reception ->
            listOf(reception.transport) + reception.inbounds.map { it.transport }
        }
    }

    /** Counts by type: «2 контейнера, 1 вагон». */
    fun transportCountsRu(payload: ShipmentPayload): String {
        var wagons = 0
        var containers = 0
        var trucks = 0
        allTransports(payload).forEach { t ->
            when (transportKind(t)) {
                TransportKind.WAGON -> wagons++
                TransportKind.CONTAINER -> containers++
                TransportKind.TRUCK -> trucks++
                null -> Unit
            }
        }
        val parts = mutableListOf<String>()
        if (containers > 0) parts += ruCount(containers, "контейнер", "контейнера", "контейнеров")
        if (wagons > 0) parts += ruCount(wagons, "вагон", "вагона", "вагонов")
        if (trucks > 0) parts += ruCount(trucks, "авто", "авто", "авто")
        return parts.joinToString(", ")
    }

    fun productNames(payload: ShipmentPayload): List<String> =
        ShipmentCalculator.allProducts(payload)
            .map { it.name.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    fun manufacturers(payload: ShipmentPayload): List<String> =
        ShipmentCalculator.allProducts(payload)
            .map { it.manufacturer.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    /** Vessels for loading modes only (not UNLOAD). */
    fun loadingVessels(payload: ShipmentPayload): List<String> {
        if (payload.mode == ShipmentMode.UNLOAD) return emptyList()
        val vessels = mutableListOf<String>()
        if (payload.vessel.isNotBlank()) vessels += payload.vessel.trim()
        if (payload.mode == ShipmentMode.MULTI_PORT) {
            payload.multiPorts.forEach { port ->
                if (port.vessel.isNotBlank()) vessels += port.vessel.trim()
            }
        }
        return vessels.distinct()
    }

    fun productTypesCount(payload: ShipmentPayload): Int =
        ShipmentCalculator.totals(payload).productTypes

    /** Lowercase haystack for archive free-text search. */
    fun searchHaystack(entity: ShipmentEntity, payload: ShipmentPayload): String {
        val products = ShipmentCalculator.allProducts(payload)
        return buildList {
            add(entity.customer)
            add(entity.port)
            add(entity.transportSummary)
            add(entity.mode)
            add(entity.id.toString())
            add(entity.totalPlaces.toString())
            add(entity.totalWeight.toString())
            addAll(ports(payload))
            addAll(loadingVessels(payload))
            addAll(receptionPoints(payload))
            addAll(productNames(payload))
            addAll(manufacturers(payload))
            products.forEach { p ->
                add(p.name)
                add(p.batch)
                add(p.manufacturer)
            }
            allTransports(payload).forEach { t ->
                add(t.wagonNumber)
                add(t.containerNumber)
                add(t.truckNumber)
                add(t.trailerNumber)
                add(t.sealNumber)
            }
            payload.unloadReceptions.forEach { r ->
                add(r.name)
                r.inbounds.forEach { ib ->
                    add(ib.port)
                    add(ib.vessel)
                }
            }
        }.joinToString(" ") { it.trim() }.lowercase()
    }

    private fun ruCount(n: Int, one: String, few: String, many: String): String {
        val mod100 = abs(n) % 100
        val mod10 = abs(n) % 10
        val word = when {
            mod100 in 11..14 -> many
            mod10 == 1 -> one
            mod10 in 2..4 -> few
            else -> many
        }
        return "$n $word"
    }

    /**
     * 1-based archive number by completion order (oldest = #1).
     * [items] is id to completedAtMillis; renumbers automatically when items are removed.
     */
    fun archiveNumber(itemId: Long, items: List<Pair<Long, Long>>): Int {
        val sorted = items.sortedWith(compareBy({ it.second }, { it.first }))
        val index = sorted.indexOfFirst { it.first == itemId }
        return if (index >= 0) index + 1 else 0
    }
}
