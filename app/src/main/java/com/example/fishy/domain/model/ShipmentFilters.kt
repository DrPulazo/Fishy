package com.example.fishy.domain.model

import com.example.fishy.data.local.entity.ShipmentEntity

object ShipmentFilters {

    /** Matches archive/statistics port filter including MULTI_PORT and UNLOAD inbounds. */
    fun matchesPortFilter(entity: ShipmentEntity, payload: ShipmentPayload, portFilter: String): Boolean {
        if (portFilter.isBlank()) return true
        return entity.port == portFilter ||
            payload.port.equals(portFilter, ignoreCase = true) ||
            payload.multiPorts.any { it.port.equals(portFilter, ignoreCase = true) } ||
            payload.unloadReceptions.any { reception ->
                reception.inbounds.any { it.port.equals(portFilter, ignoreCase = true) }
            }
    }
}
