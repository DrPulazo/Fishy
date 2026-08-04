package com.example.fishy.domain.report

import com.example.fishy.domain.calc.ShipmentCalculator
import com.example.fishy.domain.format.NumberFormatters
import com.example.fishy.domain.format.QuantityFormatters
import com.example.fishy.domain.model.Pallet
import com.example.fishy.domain.model.Product
import com.example.fishy.domain.model.ShipmentMode
import com.example.fishy.domain.model.ShipmentPayload
import com.example.fishy.domain.model.Transport
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportVariables {
    const val DATE = "{Дата}"
    const val TRANSPORT = "{Транспорт}"
    const val NAME = "{Наименование}"
    const val BATCH = "{Партия}"
    const val PLACE_WEIGHT = "{ВесМеста}"
    const val MANUFACTURER = "{Изготовитель}"
    const val PLACES = "{КоличествоМест}"
    const val MASS = "{МассаМест}"
    const val TONNAGE = "{ОбщийТоннаж}"

    val all = listOf(
        DATE, TRANSPORT, NAME, BATCH, PLACE_WEIGHT,
        MANUFACTURER, PLACES, MASS, TONNAGE
    )
}

data class ReportTemplate(
    val id: Long = 0,
    val name: String = "По умолчанию",
    val body: String = defaultBody(),
    val customerBinding: String = ""
) {
    companion object {
        /** Kept for DB/API compatibility; live reports use fixed [ReportGenerator] layout. */
        fun defaultBody(): String = ""
    }
}

object ReportGenerator {

    private val dateOnlyFmt = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    private val dateTimeFmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    fun generate(
        payload: ShipmentPayload,
        templateBody: String = ReportTemplate.defaultBody(),
        formatContainerSpaces: Boolean = false,
        formatVehicleSpaces: Boolean = false,
        thousandsSeparator: Boolean = false,
        generatedAtMillis: Long = System.currentTimeMillis()
    ): String {
        if (payload.editedReportText != null) {
            return convertQuotes(payload.editedReportText)
        }
        @Suppress("UNUSED_VARIABLE")
        val ignoredTemplate = templateBody

        val shipmentDate = dateOnlyFmt.format(
            Date(payload.completedAtMillis ?: payload.createdAtMillis)
        )
        val blocks = transportBlocks(payload, formatContainerSpaces, formatVehicleSpaces, thousandsSeparator)
            .filter { it.isNotBlank() }
        val totals = ShipmentCalculator.totals(payload)
        val notes = payload.notes.trim()

        val body = buildString {
            append(shipmentDate)
            blocks.forEachIndexed { index, block ->
                append(if (index == 0) "\n" else "\n\n")
                append(block)
            }
            append("\n\n")
            append("Общий тоннаж: ${QuantityFormatters.formatWeight(totals.actualWeight, thousandsSeparator)} кг")
            if (notes.isNotEmpty()) {
                append("\n\n")
                append(notes)
            }
            append("\n\n")
            append("Сгенерировано приложением «Фишка».")
            append("\n")
            append(dateTimeFmt.format(Date(generatedAtMillis)))
        }

        return convertQuotes(body)
    }

    /**
     * Primary transport unit only (mutually exclusive):
     * - container → container number only (truck/trailer/seal ignored in report)
     * - else wagon → wagon number only
     * - else fura → truck – trailer, driver placeholder, optional "Пломба: …"
     */
    fun formatTransportLine(
        transport: Transport,
        formatContainerSpaces: Boolean,
        formatVehicleSpaces: Boolean
    ): String {
        val container = transport.containerNumber.trim()
        if (container.isNotEmpty()) {
            return NumberFormatters.formatContainerForDisplay(container, formatContainerSpaces)
        }
        val wagon = transport.wagonNumber.trim()
        if (wagon.isNotEmpty()) {
            return wagon
        }
        val truck = transport.truckNumber.trim()
        val trailer = transport.trailerNumber.trim()
        val seal = transport.sealNumber.trim()
        val vehicleLine = buildList {
            if (truck.isNotEmpty()) {
                add(NumberFormatters.formatVehicleForDisplay(truck, formatVehicleSpaces))
            }
            if (trailer.isNotEmpty()) {
                add(NumberFormatters.formatTrailerForDisplay(trailer, formatVehicleSpaces))
            }
        }.joinToString(" – ")
        return buildList {
            if (vehicleLine.isNotEmpty()) add(vehicleLine)
            if (vehicleLine.isNotEmpty() || seal.isNotEmpty()) {
                add("Водитель: *Добавьте ФИО и контактный телефон водителя*")
            }
            if (seal.isNotEmpty()) add("Пломба: $seal")
        }.joinToString("\n")
    }

    fun formatProductLine(
        product: Product,
        doubleControl: Boolean,
        thousandsSeparator: Boolean = false
    ): String {
        val places = ShipmentCalculator.placesForProduct(product, doubleControl)
        val mass = product.packageWeight * places
        val nameBatch = listOf(product.name.trim(), product.batch.trim())
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        val tare = QuantityFormatters.formatWeight(product.packageWeight, thousandsSeparator)
        val manufacturer = product.manufacturer.trim().ifBlank { "—" }
        val head = nameBatch.ifBlank { "—" }
        return "$head (1/$tare) – $manufacturer - ${ShipmentCalculator.formatPlacesRu(places, thousandsSeparator)} – ${QuantityFormatters.formatWeight(mass, thousandsSeparator)} кг"
    }

    /** Convert ASCII/curly quotes to Russian guillemets «». */
    fun convertQuotes(text: String): String {
        val sb = StringBuilder(text.length)
        var opening = true
        for (c in text) {
            when (c) {
                '"', '\u201C', '\u201D' -> {
                    sb.append(if (opening) '«' else '»')
                    opening = !opening
                }
                else -> sb.append(c)
            }
        }
        return sb.toString()
    }

    /** Transport line + product lines per vehicle/reception — same blocks as in the TXT report. */
    fun transportProductBlocks(
        payload: ShipmentPayload,
        formatContainerSpaces: Boolean = false,
        formatVehicleSpaces: Boolean = false,
        thousandsSeparator: Boolean = false
    ): List<String> = transportBlocks(
        payload,
        formatContainerSpaces,
        formatVehicleSpaces,
        thousandsSeparator
    )

    private fun transportBlocks(
        payload: ShipmentPayload,
        cSpaces: Boolean,
        vSpaces: Boolean,
        thousandsSeparator: Boolean
    ): List<String> {
        val dc = payload.doubleControlEnabled
        return when (payload.mode) {
            ShipmentMode.MONO -> listOf(
                blockFor(payload.transport, payload.products, dc, cSpaces, vSpaces, thousandsSeparator)
            )
            ShipmentMode.MULTI_PORT -> listOf(
                blockFor(
                    transport = payload.transport,
                    products = payload.multiPorts.flatMap { it.products },
                    doubleControl = dc,
                    cSpaces = cSpaces,
                    vSpaces = vSpaces,
                    thousandsSeparator = thousandsSeparator
                )
            )
            ShipmentMode.MULTI_VEHICLE -> payload.multiVehicles.map { vehicle ->
                blockFor(
                    vehicle.transport,
                    vehicle.products,
                    vehicle.doubleControlEnabled || dc,
                    cSpaces,
                    vSpaces,
                    thousandsSeparator
                )
            }
            ShipmentMode.UNLOAD -> payload.unloadReceptions.map { reception ->
                val products = mergeIdenticalProductsForReport(
                    reception.inbounds.flatMap { it.products },
                    dc
                )
                val transportLine = formatTransportLine(reception.transport, cSpaces, vSpaces)
                    .ifBlank { reception.name.trim() }
                blockForLines(transportLine, products, dc, thousandsSeparator)
            }
        }
    }

    /**
     * Merge products that share name + batch + manufacturer + package tare.
     * Places (and thus mass in the report line) are summed. Order = first occurrence.
     */
    fun mergeIdenticalProductsForReport(
        products: List<Product>,
        doubleControl: Boolean
    ): List<Product> {
        data class Key(
            val name: String,
            val batch: String,
            val manufacturer: String,
            val packageWeight: Double
        )
        val groups = linkedMapOf<Key, MutableList<Product>>()
        products
            .filter { it.name.isNotBlank() || it.batch.isNotBlank() || it.pallets.isNotEmpty() }
            .forEach { product ->
                val key = Key(
                    name = product.name.trim(),
                    batch = product.batch.trim(),
                    manufacturer = product.manufacturer.trim(),
                    packageWeight = product.packageWeight
                )
                groups.getOrPut(key) { mutableListOf() }.add(product)
            }
        return groups.map { (key, group) ->
            val totalPlaces = group.sumOf { ShipmentCalculator.placesForProduct(it, doubleControl) }
            val prototype = group.first()
            prototype.copy(
                name = key.name,
                batch = key.batch,
                manufacturer = key.manufacturer,
                packageWeight = key.packageWeight,
                pallets = listOf(
                    Pallet(
                        places = totalPlaces,
                        isImported = doubleControl
                    )
                )
            )
        }
    }

    private fun blockFor(
        transport: Transport,
        products: List<Product>,
        doubleControl: Boolean,
        cSpaces: Boolean,
        vSpaces: Boolean,
        thousandsSeparator: Boolean
    ): String {
        val transportLine = formatTransportLine(transport, cSpaces, vSpaces)
        return blockForLines(transportLine, products, doubleControl, thousandsSeparator)
    }

    private fun blockForLines(
        transportLine: String,
        products: List<Product>,
        doubleControl: Boolean,
        thousandsSeparator: Boolean
    ): String {
        val lines = mutableListOf<String>()
        if (transportLine.isNotBlank()) {
            lines += transportLine
        }
        mergeIdenticalProductsForReport(products, doubleControl)
            .forEach { lines += formatProductLine(it, doubleControl, thousandsSeparator) }
        return lines.joinToString("\n")
    }
}
