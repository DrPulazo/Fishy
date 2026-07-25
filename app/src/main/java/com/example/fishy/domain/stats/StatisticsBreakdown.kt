package com.example.fishy.domain.stats

import com.example.fishy.data.local.entity.ShipmentEntity
import com.example.fishy.data.serialization.FishyJson
import com.example.fishy.domain.calc.ShipmentCalculator
import com.example.fishy.domain.model.Product
import com.example.fishy.domain.model.ShipmentMode
import com.example.fishy.domain.model.ShipmentPayload
import com.example.fishy.domain.model.ShipmentSummaries
import java.text.SimpleDateFormat
import java.util.Locale

enum class StatDimension {
    MONTH,
    CUSTOMER,
    PORT,
    PRODUCT,
    MANUFACTURER,
    VESSEL,
    MODE
}

enum class StatSplit {
    NONE,
    MONTH,
    PRODUCT,
    CUSTOMER,
    PORT,
    MANUFACTURER
}

data class StatSegment(
    val key: String,
    val label: String,
    val valueKg: Double,
    val colorIndex: Int
)

data class StackedStatBarEntry(
    val label: String,
    val segments: List<StatSegment>,
    val meta: Long = 0L
) {
    val totalKg: Double get() = segments.sumOf { it.valueKg }
    val totalTonnes: Double get() = totalKg / 1000.0
}

data class StatLegendItem(
    val key: String,
    val label: String,
    val colorIndex: Int,
    val totalKg: Double
)

data class StackedChartResult(
    val bars: List<StackedStatBarEntry>,
    val legend: List<StatLegendItem>
)

/** One weighted row with all dimension labels for flexible grouping. */
data class StatContribution(
    val weightKg: Double,
    val monthKey: String,
    val monthLabel: String,
    val monthStartMillis: Long,
    val customer: String,
    val port: String,
    val productKey: String,
    val productLabel: String,
    val manufacturer: String,
    val vessel: String,
    val mode: String
)

object StatisticsBreakdown {

    const val UNKNOWN_LABEL = "—"
    const val OTHER_KEY = "__other__"
    const val UNSPECIFIED_KEY = "__unspecified__"
    const val DEFAULT_TOP_GROUPS = 10
    const val DEFAULT_TOP_SERIES = 9

    private val monthLabelFmt = SimpleDateFormat("MM.yyyy", Locale.getDefault())

    fun extractContributions(entity: ShipmentEntity, payload: ShipmentPayload): List<StatContribution> {
        val monthStart = StatisticsAggregator.monthStart(entity.completedAtMillis, monthsAgo = 0)
        val monthKey = monthKey(entity.completedAtMillis)
        val monthLabel = monthLabelFmt.format(monthStart)
        val customer = entity.customer.ifBlank { UNKNOWN_LABEL }
        val modeLabel = payload.mode.name

        val rows = mutableListOf<StatContribution>()
        when (payload.mode) {
            ShipmentMode.MONO -> {
                val port = payload.port.ifBlank { entity.port }.ifBlank { UNKNOWN_LABEL }
                val vessel = payload.vessel.ifBlank { UNKNOWN_LABEL }
                val dc = payload.doubleControlEnabled
                payload.products.forEach { product ->
                    addProductRow(rows, product, dc, monthKey, monthLabel, monthStart, customer, port, vessel, modeLabel)
                }
            }
            ShipmentMode.MULTI_PORT -> {
                payload.multiPorts.forEach { group ->
                    val port = group.port.ifBlank { UNKNOWN_LABEL }
                    val vessel = group.vessel.ifBlank { UNKNOWN_LABEL }
                    val dc = payload.doubleControlEnabled || group.doubleControlEnabled
                    group.products.forEach { product ->
                        addProductRow(rows, product, dc, monthKey, monthLabel, monthStart, customer, port, vessel, modeLabel)
                    }
                }
            }
            ShipmentMode.MULTI_VEHICLE -> {
                val port = payload.port.ifBlank { entity.port }.ifBlank { UNKNOWN_LABEL }
                val vessel = payload.vessel.ifBlank { UNKNOWN_LABEL }
                payload.multiVehicles.forEach { vehicle ->
                    val dc = payload.doubleControlEnabled || vehicle.doubleControlEnabled
                    vehicle.products.forEach { product ->
                        addProductRow(rows, product, dc, monthKey, monthLabel, monthStart, customer, port, vessel, modeLabel)
                    }
                }
            }
            ShipmentMode.UNLOAD -> {
                payload.unloadReceptions.forEach { reception ->
                    reception.inbounds.forEach { inbound ->
                        val port = inbound.port.ifBlank { UNKNOWN_LABEL }
                        val vessel = inbound.vessel.ifBlank { UNKNOWN_LABEL }
                        inbound.products.forEach { product ->
                            addProductRow(rows, product, false, monthKey, monthLabel, monthStart, customer, port, vessel, modeLabel)
                        }
                    }
                }
            }
        }

        if (rows.isEmpty() && entity.totalWeight > 0.0) {
            val ports = ShipmentSummaries.ports(payload)
            val port = ports.firstOrNull()?.ifBlank { null } ?: entity.port.ifBlank { UNKNOWN_LABEL }
            val vessels = ShipmentSummaries.loadingVessels(payload)
            val vessel = vessels.firstOrNull()?.ifBlank { null } ?: payload.vessel.ifBlank { UNKNOWN_LABEL }
            rows += StatContribution(
                weightKg = entity.totalWeight,
                monthKey = monthKey,
                monthLabel = monthLabel,
                monthStartMillis = monthStart,
                customer = customer,
                port = port,
                productKey = UNSPECIFIED_KEY,
                productLabel = UNKNOWN_LABEL,
                manufacturer = UNKNOWN_LABEL,
                vessel = vessel,
                mode = modeLabel
            )
        }
        return rows
    }

    fun extractContributions(entity: ShipmentEntity): List<StatContribution> {
        val payload = FishyJson.decodePayloadOrNull(entity.payloadJson) ?: return emptyList()
        return extractContributions(entity, payload)
    }

    fun totalWeightKg(
        entities: List<ShipmentEntity>,
        portFilter: String = "",
        productFilter: String = ""
    ): Double =
        entities.flatMap { extractContributions(it) }
            .let { filterContributionsByPort(it, portFilter) }
            .let { filterContributionsByProduct(it, productFilter) }
            .sumOf { it.weightKg }

    fun filterContributionsByPort(
        contributions: List<StatContribution>,
        portFilter: String
    ): List<StatContribution> {
        if (portFilter.isBlank()) return contributions
        return contributions.filter { it.port.equals(portFilter, ignoreCase = true) }
    }

    fun filterContributionsByProduct(
        contributions: List<StatContribution>,
        productFilter: String
    ): List<StatContribution> {
        if (productFilter.isBlank()) return contributions
        return contributions.filter {
            it.productLabel.equals(productFilter, ignoreCase = true) ||
                it.productKey.equals(productFilter, ignoreCase = true)
        }
    }

    private fun addProductRow(
        rows: MutableList<StatContribution>,
        product: Product,
        doubleControl: Boolean,
        monthKey: String,
        monthLabel: String,
        monthStartMillis: Long,
        customer: String,
        port: String,
        vessel: String,
        mode: String
    ) {
        val places = ShipmentCalculator.placesForProduct(product, doubleControl)
        val weightKg = product.packageWeight * places
        if (weightKg <= 0.0 && places <= 0.0 && product.quantity <= 0) return
        val productLabel = productLabel(product)
        rows += StatContribution(
            weightKg = weightKg,
            monthKey = monthKey,
            monthLabel = monthLabel,
            monthStartMillis = monthStartMillis,
            customer = customer,
            port = port,
            productKey = productKey(product),
            productLabel = productLabel,
            manufacturer = product.manufacturer.ifBlank { UNKNOWN_LABEL },
            vessel = vessel,
            mode = mode
        )
    }

    fun productLabel(product: Product): String {
        if (product.name.isNotBlank()) return product.name.trim()
        if (product.batch.isNotBlank()) return product.batch.trim()
        if (product.manufacturer.isNotBlank()) return product.manufacturer.trim()
        return UNKNOWN_LABEL
    }

    /** Stats grouping key: product name only (batch/tare/manufacturer do not split the series). */
    fun productKey(product: Product): String {
        if (product.name.isNotBlank()) {
            return product.name.trim().lowercase(Locale.getDefault())
        }
        if (product.batch.isNotBlank()) {
            return "batch:${product.batch.trim().lowercase(Locale.getDefault())}"
        }
        if (product.manufacturer.isNotBlank()) {
            return "mfr:${product.manufacturer.trim().lowercase(Locale.getDefault())}"
        }
        return UNSPECIFIED_KEY
    }

    fun dimensionValue(contribution: StatContribution, dimension: StatDimension): String = when (dimension) {
        StatDimension.MONTH -> contribution.monthKey
        StatDimension.CUSTOMER -> contribution.customer
        StatDimension.PORT -> contribution.port
        StatDimension.PRODUCT -> contribution.productKey
        StatDimension.MANUFACTURER -> contribution.manufacturer
        StatDimension.VESSEL -> contribution.vessel
        StatDimension.MODE -> contribution.mode
    }

    fun dimensionLabel(contribution: StatContribution, dimension: StatDimension): String = when (dimension) {
        StatDimension.MONTH -> contribution.monthLabel
        StatDimension.CUSTOMER -> contribution.customer
        StatDimension.PORT -> contribution.port
        StatDimension.PRODUCT -> contribution.productLabel
        StatDimension.MANUFACTURER -> contribution.manufacturer
        StatDimension.VESSEL -> contribution.vessel
        StatDimension.MODE -> contribution.mode
    }

    fun splitToDimension(split: StatSplit): StatDimension? = when (split) {
        StatSplit.NONE -> null
        StatSplit.MONTH -> StatDimension.MONTH
        StatSplit.PRODUCT -> StatDimension.PRODUCT
        StatSplit.CUSTOMER -> StatDimension.CUSTOMER
        StatSplit.PORT -> StatDimension.PORT
        StatSplit.MANUFACTURER -> StatDimension.MANUFACTURER
    }

    fun isValidCombination(groupBy: StatDimension, splitBy: StatSplit): Boolean {
        val splitDim = splitToDimension(splitBy) ?: return true
        return splitDim != groupBy
    }

    private fun monthKey(millis: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = millis }
        return "${cal.get(java.util.Calendar.YEAR)}-${cal.get(java.util.Calendar.MONTH)}"
    }

    fun buildStackedChart(
        entities: List<ShipmentEntity>,
        groupBy: StatDimension,
        splitBy: StatSplit,
        fromMonthStart: Long,
        toMonthStart: Long,
        topGroups: Int = DEFAULT_TOP_GROUPS,
        topSeries: Int = DEFAULT_TOP_SERIES,
        otherSeriesLabel: String = "…",
        otherGroupLabel: String = "…",
        portFilter: String = "",
        productFilter: String = ""
    ): StackedChartResult {
        if (!isValidCombination(groupBy, splitBy)) {
            return StackedChartResult(emptyList(), emptyList())
        }
        val contributions = entities.flatMap { entity ->
            val payload = FishyJson.decodePayloadOrNull(entity.payloadJson) ?: return@flatMap emptyList()
            extractContributions(entity, payload)
        }.let { list -> filterContributionsByPort(list, portFilter) }
            .let { list -> filterContributionsByProduct(list, productFilter) }
        if (contributions.isEmpty()) return StackedChartResult(emptyList(), emptyList())

        val splitDim = splitToDimension(splitBy)
        val groupTotals = contributions.groupBy { dimensionValue(it, groupBy) }
            .mapValues { (_, list) -> list.sumOf { it.weightKg } }
        val topGroupKeys = if (groupBy == StatDimension.MONTH) {
            emptySet()
        } else {
            topKeys(groupTotals, topGroups).toSet()
        }
        fun mappedGroupKey(raw: String): String =
            if (groupBy == StatDimension.MONTH || raw in topGroupKeys) raw else OTHER_KEY

        val groupKeysOrdered = resolveGroupKeysOrdered(
            contributions = contributions,
            groupBy = groupBy,
            groupTotals = groupTotals,
            topGroupKeys = topGroupKeys,
            topGroups = topGroups,
            fromMonthStart = fromMonthStart,
            toMonthStart = toMonthStart,
            otherGroupLabel = otherGroupLabel
        )

        val seriesTotals = if (splitDim == null) {
            emptyMap()
        } else {
            contributions.groupBy { dimensionValue(it, splitDim) }
                .mapValues { (_, list) -> list.sumOf { it.weightKg } }
        }
        val topSeriesKeys = if (splitDim == null) emptySet() else topKeys(seriesTotals, topSeries).toSet()
        fun mappedSeriesKey(raw: String): String =
            if (splitDim == null || raw in topSeriesKeys) raw else OTHER_KEY

        val legendSeriesKeys = if (splitDim == null) {
            emptyList()
        } else {
            val keys = topKeys(seriesTotals, topSeries).toMutableList()
            if (seriesTotals.keys.any { it !in topSeriesKeys }) keys += OTHER_KEY
            keys
        }
        val colorBySeries = StatsChartColors.assignColorIndices(
            legendSeriesKeys.associateWith { key ->
                when (key) {
                    OTHER_KEY -> seriesTotals.filterKeys { it !in topSeriesKeys }.values.sum()
                    else -> seriesTotals[key] ?: 0.0
                }
            }
        )

        val bars = groupKeysOrdered.map { groupInfo ->
            val groupContribs = contributions.filter {
                mappedGroupKey(dimensionValue(it, groupBy)) == groupInfo.key
            }
            val segments = if (splitDim == null) {
                val total = groupContribs.sumOf { it.weightKg }
                listOf(
                    StatSegment(key = "total", label = "total", valueKg = total, colorIndex = 0)
                )
            } else {
                groupContribs.groupBy { mappedSeriesKey(dimensionValue(it, splitDim)) }
                    .mapValues { (_, list) -> list.sumOf { it.weightKg } }
                    .entries
                    .filter { it.value > 0.0 }
                    .sortedByDescending { it.value }
                    .map { (seriesKey, kg) ->
                        val label = seriesLabel(
                            contributions, splitDim, seriesKey, topSeriesKeys, otherSeriesLabel
                        )
                        StatSegment(
                            key = seriesKey,
                            label = label,
                            valueKg = kg,
                            colorIndex = colorBySeries[seriesKey] ?: 0
                        )
                    }
            }
            StackedStatBarEntry(label = groupInfo.label, segments = segments, meta = groupInfo.meta)
        }

        val legend = legendSeriesKeys.map { key ->
            val kg = when (key) {
                OTHER_KEY -> contributions
                    .filter { mappedSeriesKey(dimensionValue(it, splitDim!!)) == OTHER_KEY }
                    .sumOf { it.weightKg }
                else -> seriesTotals[key] ?: 0.0
            }
            StatLegendItem(
                key = key,
                label = seriesLabel(contributions, splitDim!!, key, topSeriesKeys, otherSeriesLabel),
                colorIndex = colorBySeries[key] ?: 0,
                totalKg = kg
            )
        }.sortedByDescending { it.totalKg }

        return StackedChartResult(bars, legend)
    }

    private fun seriesLabel(
        contributions: List<StatContribution>,
        splitDim: StatDimension,
        seriesKey: String,
        topSeriesKeys: Set<String>,
        otherLabel: String
    ): String {
        if (seriesKey == OTHER_KEY) return otherLabel
        return contributions.firstOrNull { dimensionValue(it, splitDim) == seriesKey }
            ?.let { dimensionLabel(it, splitDim) } ?: seriesKey
    }

    private data class GroupInfo(val key: String, val label: String, val meta: Long = 0L)

    private fun resolveGroupKeysOrdered(
        contributions: List<StatContribution>,
        groupBy: StatDimension,
        groupTotals: Map<String, Double>,
        topGroupKeys: Set<String>,
        topGroups: Int,
        fromMonthStart: Long,
        toMonthStart: Long,
        otherGroupLabel: String
    ): List<GroupInfo> {
        if (groupBy == StatDimension.MONTH) {
            val from = minOf(fromMonthStart, toMonthStart)
            val to = maxOf(fromMonthStart, toMonthStart)
            return StatisticsAggregator.monthsInclusivePublic(from, to).map { start ->
                GroupInfo(key = monthKey(start), label = monthLabelFmt.format(start), meta = start)
            }
        }
        val orderedKeys = topKeys(groupTotals, topGroups).toMutableList()
        if (groupTotals.keys.any { it !in topGroupKeys }) orderedKeys += OTHER_KEY
        return orderedKeys.map { key ->
            if (key == OTHER_KEY) {
                GroupInfo(key = OTHER_KEY, label = otherGroupLabel)
            } else {
                val sample = contributions.first { dimensionValue(it, groupBy) == key }
                GroupInfo(key = key, label = dimensionLabel(sample, groupBy))
            }
        }.sortedByDescending { info ->
            when (info.key) {
                OTHER_KEY -> groupTotals.filterKeys { it !in topGroupKeys }.values.sum()
                else -> groupTotals[info.key] ?: 0.0
            }
        }
    }

    private fun topKeys(totals: Map<String, Double>, n: Int): List<String> =
        totals.entries
            .sortedByDescending { it.value }
            .take(n.coerceAtLeast(1))
            .map { it.key }
}
