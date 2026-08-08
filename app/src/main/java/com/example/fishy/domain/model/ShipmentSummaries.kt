package com.example.fishy.domain.model

import com.example.fishy.data.local.entity.ShipmentEntity
import com.example.fishy.domain.calc.ShipmentCalculator
import com.example.fishy.domain.format.QuantityFormatters
import kotlin.math.abs

/**
 * Card / list summaries from a shipment payload.
 * Transport uses the same XOR rule as the form: wagon XOR road set.
 */
object ShipmentSummaries {

    private const val SCHEDULE_LIST_CAP = 5

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
    fun transportCountsRu(
        payload: ShipmentPayload,
        copy: SummaryStrings = SummaryStrings.Russian
    ): String {
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
        if (containers > 0) {
            parts += ruCount(containers, copy.container.first, copy.container.second, copy.container.third)
        }
        if (wagons > 0) {
            parts += ruCount(wagons, copy.wagon.first, copy.wagon.second, copy.wagon.third)
        }
        if (trucks > 0) {
            parts += ruCount(trucks, copy.truck.first, copy.truck.second, copy.truck.third)
        }
        return parts.joinToString(", ")
    }

    /**
     * MULTI_VEHICLE schedule card: one kind → «N контейнеров»; mixed → «N транспортов».
     * Empty / untyped slots are ignored.
     */
    fun scheduleTransportLine(
        payload: ShipmentPayload,
        copy: SummaryStrings = SummaryStrings.Russian
    ): String? {
        if (payload.mode != ShipmentMode.MULTI_VEHICLE) return null
        val kinds = payload.multiVehicles.mapNotNull { transportKind(it.transport) }
        if (kinds.isEmpty()) return null
        val distinct = kinds.distinct()
        return if (distinct.size == 1) {
            when (distinct.first()) {
                TransportKind.CONTAINER ->
                    ruCount(kinds.size, copy.container.first, copy.container.second, copy.container.third)
                TransportKind.WAGON ->
                    ruCount(kinds.size, copy.wagon.first, copy.wagon.second, copy.wagon.third)
                TransportKind.TRUCK ->
                    ruCount(kinds.size, copy.truck.first, copy.truck.second, copy.truck.third)
            }
        } else {
            ruCount(kinds.size, copy.transport.first, copy.transport.second, copy.transport.third)
        }
    }

    fun schedulePlannedTonnageKg(payload: ShipmentPayload): Double =
        ShipmentCalculator.plannedTonnageKg(payload)

    /**
     * Body lines for a scheduled-shipment list card.
     * Order depends on [ShipmentPayload.mode]; blank sections omitted.
     */
    fun scheduleCardBodyLines(
        payload: ShipmentPayload,
        thousandsSeparator: Boolean = false,
        copy: SummaryStrings = SummaryStrings.Russian
    ): List<String> = buildList {
        addAll(scheduleLocationLines(payload, copy))
        scheduleTransportLine(payload, copy)?.let { add(it) }
        scheduleProductLine(payload, copy)?.let { add(it) }
        val tonnage = schedulePlannedTonnageKg(payload)
        if (tonnage > 0.0) {
            val formatted = QuantityFormatters.formatWeight(tonnage, thousandsSeparator)
            add(copy.tonnageFmt.format(formatted))
        }
    }

    fun scheduleLocationLines(
        payload: ShipmentPayload,
        copy: SummaryStrings = SummaryStrings.Russian
    ): List<String> = when (payload.mode) {
        ShipmentMode.MONO, ShipmentMode.MULTI_VEHICLE -> {
            val port = payload.port.trim()
            if (port.isBlank()) emptyList() else listOf(copy.portFmt.format(port))
        }
        ShipmentMode.MULTI_PORT -> cappedNumberedList(
            items = payload.multiPorts.map { it.port.trim() }.filter { it.isNotBlank() },
            singularLabel = { i, name -> copy.portNumberedFmt.format(i, name) },
            moreWord = copy.portWord,
            moreFmt = copy.moreFmt
        )
        ShipmentMode.UNLOAD -> {
            val names = payload.unloadReceptions.map { it.name.trim() }.filter { it.isNotBlank() }
            when {
                names.isEmpty() -> emptyList()
                names.size == 1 -> listOf(copy.receptionFmt.format(names.first()))
                else -> cappedNumberedList(
                    items = names,
                    singularLabel = { i, name -> copy.receptionNumberedFmt.format(i, name) },
                    moreWord = copy.pointWord,
                    moreFmt = copy.moreFmt
                )
            }
        }
    }

    fun scheduleProductLine(
        payload: ShipmentPayload,
        copy: SummaryStrings = SummaryStrings.Russian
    ): String? {
        val kinds = scheduleProductKinds(payload)
        return when {
            kinds.isEmpty() -> null
            kinds.size == 1 -> {
                val name = kinds.first().name.trim()
                if (name.isBlank()) null else copy.productFmt.format(name)
            }
            else -> {
                val counted = ruCount(
                    kinds.size,
                    copy.kindWord.first,
                    copy.kindWord.second,
                    copy.kindWord.third
                )
                copy.productKindsFmt.format(counted)
            }
        }
    }

    fun scheduleProductKinds(payload: ShipmentPayload): List<Product> =
        ShipmentCalculator.allProducts(payload)
            .filter {
                it.name.isNotBlank() || it.batch.isNotBlank() ||
                    it.manufacturer.isNotBlank() || it.packageWeight > 0.0 ||
                    it.quantity > 0 ||
                    ShipmentCalculator.realPallets(it).any { p -> p.places > 0.0 }
            }
            .distinctBy { ShipmentCalculator.batchKey(it) }

    private fun cappedNumberedList(
        items: List<String>,
        singularLabel: (index: Int, name: String) -> String,
        moreWord: Triple<String, String, String>,
        moreFmt: String
    ): List<String> {
        if (items.isEmpty()) return emptyList()
        val shown = items.take(SCHEDULE_LIST_CAP)
        val lines = shown.mapIndexed { index, name -> singularLabel(index + 1, name) }.toMutableList()
        val rest = items.size - shown.size
        if (rest > 0) {
            lines += moreFmt.format(ruCount(rest, moreWord.first, moreWord.second, moreWord.third))
        }
        return lines
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

    fun ruCount(n: Int, one: String, few: String, many: String): String {
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
     * First non-blank name, or «first + N порт(а/ов)» when more remain.
     * Blank entries are skipped; order preserved.
     */
    fun firstPlusRestRu(
        names: List<String>,
        one: String = SummaryStrings.Russian.portWord.first,
        few: String = SummaryStrings.Russian.portWord.second,
        many: String = SummaryStrings.Russian.portWord.third
    ): String? {
        val cleaned = names.map { it.trim() }.filter { it.isNotBlank() }
        if (cleaned.isEmpty()) return null
        if (cleaned.size == 1) return cleaned.first()
        val rest = cleaned.size - 1
        return "${cleaned.first()} + ${ruCount(rest, one, few, many)}"
    }

    /**
     * Short variant: «first + N» without the word declension (порт/порта/портов).
     * Useful for tighter notification strings.
     */
    fun firstPlusRestCountOnlyRu(names: List<String>): String? {
        val cleaned = names.map { it.trim() }.filter { it.isNotBlank() }
        if (cleaned.isEmpty()) return null
        if (cleaned.size == 1) return cleaned.first()
        val rest = cleaned.size - 1
        return "${cleaned.first()} + $rest"
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
