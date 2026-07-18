package com.example.fishy.domain.model

/**
 * Prepares a copy of a completed shipment as a new draft:
 * keeps structure, products metadata (including planned quantity), and checklist titles;
 * clears pallets, transport numbers, notes and checklist completion.
 */
object ShipmentDuplicator {

    fun forNewDraft(source: ShipmentPayload): ShipmentPayload {
        val now = System.currentTimeMillis()
        return source.copy(
            transport = Transport(),
            products = source.products.map { it.withoutCounts() },
            multiPorts = source.multiPorts.map { port ->
                port.copy(products = port.products.map { it.withoutCounts() })
            },
            multiVehicles = source.multiVehicles.map { vehicle ->
                vehicle.copy(
                    transport = Transport(),
                    products = vehicle.products.map { it.withoutCounts() }
                )
            },
            unloadReceptions = source.unloadReceptions.map { reception ->
                reception.copy(
                    transport = Transport(),
                    inbounds = reception.inbounds.map { inbound ->
                        inbound.copy(
                            transport = Transport(),
                            products = inbound.products.map { it.withoutCounts() }
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
            lastUsedUnloadReceptionId = null
        )
    }

    private fun Product.withoutCounts(): Product = copy(
        pallets = emptyList()
    )
}
