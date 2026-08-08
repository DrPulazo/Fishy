package com.example.fishy.domain.archive

import com.example.fishy.domain.format.NumberFormatters
import com.example.fishy.domain.model.ShipmentMode
import com.example.fishy.domain.model.ShipmentPayload
import com.example.fishy.domain.model.Transport
import com.example.fishy.domain.report.ReportGenerator

/** Localized prefix templates (`Порт: %1$s`, …) for archive detail. */
data class ArchiveDetailLabels(
    val portFmt: String = "Порт: %1\$s",
    val vesselFmt: String = "Судно: %1\$s",
    val wagonFmt: String = "Вагон: %1\$s",
    val containerFmt: String = "Контейнер: %1\$s",
    val truckFmt: String = "Авто: %1\$s",
    val trailerFmt: String = "Прицеп: %1\$s",
    val sealFmt: String = "Пломба: %1\$s"
) {
    companion object {
        val Russian = ArchiveDetailLabels()
    }
}

/**
 * Human-readable archive detail (what was loaded where).
 * Unlike [ReportGenerator], does not merge identical products across ports,
 * and shows full transport fields (report shows only the primary unit).
 */
object ArchiveDetailFormatter {

    fun contentBlocks(
        payload: ShipmentPayload,
        formatContainerSpaces: Boolean = false,
        formatVehicleSpaces: Boolean = false,
        thousandsSeparator: Boolean = false,
        labels: ArchiveDetailLabels = ArchiveDetailLabels.Russian
    ): List<String> {
        val dc = payload.doubleControlEnabled
        return when (payload.mode) {
            ShipmentMode.MONO -> listOfNotNull(
                monoBlock(payload, dc, formatContainerSpaces, formatVehicleSpaces, thousandsSeparator, labels)
            )
            ShipmentMode.MULTI_PORT -> multiPortBlocks(
                payload, dc, formatContainerSpaces, formatVehicleSpaces, thousandsSeparator, labels
            )
            ShipmentMode.MULTI_VEHICLE -> payload.multiVehicles.mapNotNull { vehicle ->
                val lines = mutableListOf<String>()
                lines += formatTransportFull(
                    vehicle.transport, formatContainerSpaces, formatVehicleSpaces, labels
                )
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
                val receptionTransport = formatTransportFull(
                    reception.transport, formatContainerSpaces, formatVehicleSpaces, labels
                )
                if (receptionTransport.isNotEmpty()) {
                    lines += receptionTransport
                } else {
                    val name = reception.name.trim()
                    if (name.isNotBlank()) lines += name
                }
                reception.inbounds.forEach { inbound ->
                    val port = inbound.port.trim()
                    if (port.isNotBlank()) lines += String.format(labels.portFmt, port)
                    lines += formatTransportFull(
                        inbound.transport, formatContainerSpaces, formatVehicleSpaces, labels
                    )
                    val vessel = inbound.vessel.trim()
                    if (vessel.isNotBlank()) lines += String.format(labels.vesselFmt, vessel)
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

    /**
     * All entered transport fields for archive (no driver placeholder).
     * Wagon XOR road: if container/truck/trailer present, wagon is ignored;
     * otherwise wagon (+ seal). Matches UI mutual exclusion.
     */
    fun formatTransportFull(
        transport: Transport,
        formatContainerSpaces: Boolean = false,
        formatVehicleSpaces: Boolean = false,
        labels: ArchiveDetailLabels = ArchiveDetailLabels.Russian
    ): List<String> {
        val wagon = transport.wagonNumber.trim()
        val container = transport.containerNumber.trim()
        val truck = transport.truckNumber.trim()
        val trailer = transport.trailerNumber.trim()
        val seal = transport.sealNumber.trim()
        val hasRoad = container.isNotEmpty() || truck.isNotEmpty() || trailer.isNotEmpty()
        val lines = mutableListOf<String>()
        if (hasRoad) {
            if (container.isNotEmpty()) {
                lines += String.format(
                    labels.containerFmt,
                    NumberFormatters.formatContainerForDisplay(container, formatContainerSpaces)
                )
            }
            if (truck.isNotEmpty()) {
                lines += String.format(
                    labels.truckFmt,
                    NumberFormatters.formatVehicleForDisplay(truck, formatVehicleSpaces)
                )
            }
            if (trailer.isNotEmpty()) {
                lines += String.format(
                    labels.trailerFmt,
                    NumberFormatters.formatTrailerForDisplay(trailer, formatVehicleSpaces)
                )
            }
        } else if (wagon.isNotEmpty()) {
            lines += String.format(labels.wagonFmt, wagon)
        }
        if (seal.isNotEmpty()) {
            lines += String.format(labels.sealFmt, seal)
        }
        return lines
    }

    private fun monoBlock(
        payload: ShipmentPayload,
        dc: Boolean,
        cSpaces: Boolean,
        vSpaces: Boolean,
        thousandsSeparator: Boolean,
        labels: ArchiveDetailLabels
    ): String? {
        val lines = mutableListOf<String>()
        lines += formatTransportFull(payload.transport, cSpaces, vSpaces, labels)
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
        thousandsSeparator: Boolean,
        labels: ArchiveDetailLabels
    ): List<String> {
        val blocks = mutableListOf<String>()
        val transportLines = formatTransportFull(payload.transport, cSpaces, vSpaces, labels)
        if (transportLines.isNotEmpty()) {
            blocks += transportLines.joinToString("\n")
        }
        payload.multiPorts.forEach { group ->
            val lines = mutableListOf<String>()
            val port = group.port.trim()
            if (port.isNotBlank()) lines += String.format(labels.portFmt, port)
            val vessel = group.vessel.trim()
            if (vessel.isNotBlank()) lines += String.format(labels.vesselFmt, vessel)
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
