package com.example.fishy.domain.report

import com.example.fishy.domain.calc.ShipmentCalculator
import com.example.fishy.domain.format.NumberFormatters
import com.example.fishy.domain.model.Product
import com.example.fishy.domain.model.ShipmentMode
import com.example.fishy.domain.model.ShipmentPayload
import com.example.fishy.domain.model.Transport
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
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

    private val weightFmt = DecimalFormat("0.###", DecimalFormatSymbols(Locale.US))
    private val dateOnlyFmt = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    private val dateTimeFmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    fun generate(
        payload: ShipmentPayload,
        templateBody: String = ReportTemplate.defaultBody(),
        formatContainerSpaces: Boolean = false,
        formatVehicleSpaces: Boolean = false,
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
        val blocks = transportBlocks(payload, formatContainerSpaces, formatVehicleSpaces)
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
            append("Общий тоннаж: ${weightFmt.format(totals.actualWeight)} кг")
            if (notes.isNotEmpty()) {
                append("\n\n")
                append(notes)
            }
            append("\n\n")
            append("Сгенерировано приложением «Фишка».")
            append("\n")
            append(dateTimeFmt.format(Date(generatedAtMillis)))
            append("\n")
        }

        return convertQuotes(body)
    }

    /**
     * Mutual exclusion:
     * - container → only container number
     * - else wagon → only wagon number
     * - else road set → truck, trailer (optional), seal (optional)
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
        val parts = mutableListOf<String>()
        val truck = transport.truckNumber.trim()
        if (truck.isNotEmpty()) {
            parts += NumberFormatters.formatVehicleForDisplay(truck, formatVehicleSpaces)
        }
        val trailer = transport.trailerNumber.trim()
        if (trailer.isNotEmpty()) {
            parts += NumberFormatters.formatTrailerForDisplay(trailer, formatVehicleSpaces)
        }
        val seal = transport.sealNumber.trim()
        if (seal.isNotEmpty()) {
            parts += seal
        }
        return parts.joinToString(", ")
    }

    fun formatProductLine(product: Product, doubleControl: Boolean): String {
        val places = ShipmentCalculator.placesForProduct(product, doubleControl)
        val mass = product.packageWeight * places
        val nameBatch = listOf(product.name.trim(), product.batch.trim())
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        val tare = weightFmt.format(product.packageWeight)
        val manufacturer = product.manufacturer.trim().ifBlank { "—" }
        val head = nameBatch.ifBlank { "—" }
        return "$head (1/$tare) – $manufacturer - ${ShipmentCalculator.formatPlacesRu(places)} – ${weightFmt.format(mass)} кг"
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

    private fun transportBlocks(
        payload: ShipmentPayload,
        cSpaces: Boolean,
        vSpaces: Boolean
    ): List<String> {
        val dc = payload.doubleControlEnabled
        return when (payload.mode) {
            ShipmentMode.MONO -> listOf(
                blockFor(payload.transport, payload.products, dc, cSpaces, vSpaces)
            )
            ShipmentMode.MULTI_PORT -> listOf(
                blockFor(
                    transport = payload.transport,
                    products = payload.multiPorts.flatMap { it.products },
                    doubleControl = dc,
                    cSpaces = cSpaces,
                    vSpaces = vSpaces
                )
            )
            ShipmentMode.MULTI_VEHICLE -> payload.multiVehicles.map { vehicle ->
                blockFor(vehicle.transport, vehicle.products, vehicle.doubleControlEnabled || dc, cSpaces, vSpaces)
            }
            ShipmentMode.UNLOAD -> payload.unloadReceptions.map { reception ->
                val products = reception.inbounds.flatMap { it.products }
                val transportLine = formatTransportLine(reception.transport, cSpaces, vSpaces)
                    .ifBlank { reception.name.trim() }
                blockForLines(transportLine, products, dc)
            }
        }
    }

    private fun blockFor(
        transport: Transport,
        products: List<Product>,
        doubleControl: Boolean,
        cSpaces: Boolean,
        vSpaces: Boolean
    ): String {
        val transportLine = formatTransportLine(transport, cSpaces, vSpaces)
        return blockForLines(transportLine, products, doubleControl)
    }

    private fun blockForLines(
        transportLine: String,
        products: List<Product>,
        doubleControl: Boolean
    ): String {
        val lines = mutableListOf<String>()
        if (transportLine.isNotBlank()) {
            lines += transportLine
        }
        products
            .filter { it.name.isNotBlank() || it.batch.isNotBlank() || it.pallets.isNotEmpty() }
            .forEach { lines += formatProductLine(it, doubleControl) }
        return lines.joinToString("\n")
    }
}
