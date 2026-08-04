package com.example.fishy.domain.archive

import com.example.fishy.domain.model.ShipmentMode
import com.example.fishy.domain.model.ShipmentPayload
import com.example.fishy.domain.report.ReportGenerator

/**
 * Human-readable archive detail (what was loaded where).
 * Unlike [ReportGenerator], does not merge identical products across ports.
 */
object ArchiveDetailFormatter {

    fun contentBlocks(
        payload: ShipmentPayload,
        formatContainerSpaces: Boolean = false,
        formatVehicleSpaces: Boolean = false,
        thousandsSeparator: Boolean = false
    ): List<String> {
        val dc = payload.doubleControlEnabled
        return when (payload.mode) {
            ShipmentMode.MONO -> listOfNotNull(
                monoBlock(payload, dc, formatContainerSpaces, formatVehicleSpaces, thousandsSeparator)
            )
            ShipmentMode.MULTI_PORT -> multiPortBlocks(
                payload, dc, formatContainerSpaces, formatVehicleSpaces, thousandsSeparator
            )
            ShipmentMode.MULTI_VEHICLE -> payload.multiVehicles.mapNotNull { vehicle ->
                val lines = mutableListOf<String>()
                val transport = ReportGenerator.formatTransportLine(
                    vehicle.transport, formatContainerSpaces, formatVehicleSpaces
                )
                if (transport.isNotBlank()) lines += transport.lines()
                val merged = ReportGenerator.mergeIdenticalProductsForReport(
                    vehicle.products,
                    vehicle.doubleControlEnabled || dc
                )
                merged.forEach {
                    lines += ReportGenerator.formatProductLine(
                        it, vehicle.doubleControlEnabled || dc, thousandsSeparator
                    )
                }
                lines.joinToString("\n").takeIf { it.isNotBlank() }
            }
            ShipmentMode.UNLOAD -> payload.unloadReceptions.mapNotNull { reception ->
                val lines = mutableListOf<String>()
                val receptionTransport = ReportGenerator.formatTransportLine(
                    reception.transport, formatContainerSpaces, formatVehicleSpaces
                ).ifBlank { reception.name.trim() }
                if (receptionTransport.isNotBlank()) lines += receptionTransport.lines()
                reception.inbounds.forEach { inbound ->
                    val port = inbound.port.trim()
                    if (port.isNotBlank()) lines += port
                    val inboundTransport = ReportGenerator.formatTransportLine(
                        inbound.transport, formatContainerSpaces, formatVehicleSpaces
                    )
                    if (inboundTransport.isNotBlank()) lines += inboundTransport.lines()
                    val vessel = inbound.vessel.trim()
                    if (vessel.isNotBlank()) lines += vessel
                    ReportGenerator.mergeIdenticalProductsForReport(inbound.products, dc)
                        .forEach {
                            lines += ReportGenerator.formatProductLine(it, dc, thousandsSeparator)
                        }
                }
                lines.joinToString("\n").takeIf { it.isNotBlank() }
            }
        }
    }

    fun notes(payload: ShipmentPayload): String? =
        payload.notes.trim().takeIf { it.isNotEmpty() }

    private fun monoBlock(
        payload: ShipmentPayload,
        dc: Boolean,
        cSpaces: Boolean,
        vSpaces: Boolean,
        thousandsSeparator: Boolean
    ): String? {
        val lines = mutableListOf<String>()
        val transport = ReportGenerator.formatTransportLine(payload.transport, cSpaces, vSpaces)
        if (transport.isNotBlank()) lines += transport.lines()
        ReportGenerator.mergeIdenticalProductsForReport(payload.products, dc).forEach {
            lines += ReportGenerator.formatProductLine(it, dc, thousandsSeparator)
        }
        return lines.joinToString("\n").takeIf { it.isNotBlank() }
    }

    private fun multiPortBlocks(
        payload: ShipmentPayload,
        dc: Boolean,
        cSpaces: Boolean,
        vSpaces: Boolean,
        thousandsSeparator: Boolean
    ): List<String> {
        val blocks = mutableListOf<String>()
        val transport = ReportGenerator.formatTransportLine(payload.transport, cSpaces, vSpaces)
        if (transport.isNotBlank()) {
            blocks += transport
        }
        payload.multiPorts.forEach { group ->
            val lines = mutableListOf<String>()
            val port = group.port.trim()
            if (port.isNotBlank()) lines += port
            val vessel = group.vessel.trim()
            if (vessel.isNotBlank()) lines += vessel
            val localDc = dc || group.doubleControlEnabled
            ReportGenerator.mergeIdenticalProductsForReport(group.products, localDc).forEach {
                lines += ReportGenerator.formatProductLine(it, localDc, thousandsSeparator)
            }
            val block = lines.joinToString("\n")
            if (block.isNotBlank()) blocks += block
        }
        return blocks
    }
}
