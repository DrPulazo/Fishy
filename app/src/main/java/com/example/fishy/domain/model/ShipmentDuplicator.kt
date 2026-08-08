package com.example.fishy.domain.model

/**
 * Prepares a copy as a new draft:
 * keeps structure, product metadata and planned quantity/tare;
 * clears transport numbers, pallets, notes and checklist completion.
 */
object ShipmentDuplicator {

    fun forNewDraft(source: ShipmentPayload): ShipmentPayload {
        val now = System.currentTimeMillis()
        return source.copy(
            transport = Transport(),
            products = source.products.map { it.withoutPallets() },
            multiPorts = source.multiPorts.map { port ->
                port.copy(products = port.products.map { it.withoutPallets() })
            },
            multiVehicles = source.multiVehicles.map { vehicle ->
                vehicle.copy(
                    transport = Transport(),
                    products = vehicle.products.map { it.withoutPallets() }
                )
            },
            unloadReceptions = source.unloadReceptions.map { reception ->
                reception.copy(
                    transport = Transport(),
                    inbounds = reception.inbounds.map { inbound ->
                        inbound.copy(
                            transport = Transport(),
                            products = inbound.products.map { it.withoutPallets() }
                        )
                    }
                )
            },
            checklist = source.checklist.map { task ->
                task.copy(isCompleted = false, completedAtMillis = null)
            },
            notes = "",
            editedReportText = null,
            createdAtMillis = now,
            completedAtMillis = null,
            lastUsedVehicleId = null,
            lastUsedProductId = null,
            lastUsedPortId = null,
            lastUsedUnloadReceptionId = null,
            accordionExpanded = emptyMap(),
            quickPlacesByKey = emptyMap()
        )
    }

    private fun Product.withoutPallets(): Product = copy(pallets = emptyList())
}
