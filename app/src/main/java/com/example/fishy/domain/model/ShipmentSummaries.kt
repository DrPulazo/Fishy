package com.example.fishy.domain.model

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
