package com.example.fishy.domain.stats

import com.example.fishy.data.local.entity.ShipmentEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class StatBarEntry(
    val label: String,
    val valueTonnes: Double,
    val valueKg: Double = valueTonnes * 1000.0,
    /** Month start millis (for selection / range). */
    val monthStartMillis: Long = 0L
)

data class MonthChoice(
    val label: String,
    val startMillis: Long
)

data class PeriodComparison(
    val currentTonnes: Double,
    val previousTonnes: Double,
    val currentLabel: String,
    val previousLabel: String
) {
    val deltaTonnes: Double get() = currentTonnes - previousTonnes
    val deltaPercent: Double?
        get() = if (previousTonnes <= 0.0) null else (deltaTonnes / previousTonnes) * 100.0
}

object StatisticsAggregator {

    /** Default chart window: 13 calendar months (current + same month a year ago). */
    const val DEFAULT_MONTH_COUNT = 13

    /**
     * Inclusive calendar months from [fromMonthStart] through [toMonthStart].
     * Empty months are included as zero bars.
     */
    fun tonnageByMonthRange(
        entities: List<ShipmentEntity>,
        fromMonthStart: Long,
        toMonthStart: Long
    ): List<StatBarEntry> {
        val from = minOf(fromMonthStart, toMonthStart)
        val to = maxOf(fromMonthStart, toMonthStart)
        val labelFmt = SimpleDateFormat("MM.yyyy", Locale.getDefault())
        val byMonth = entities.groupBy { monthKey(it.completedAtMillis) }
        return monthsInclusive(from, to).map { start ->
            val list = byMonth[monthKey(start)].orEmpty()
            val kg = list.sumOf { it.totalWeight }
            StatBarEntry(
                label = labelFmt.format(start),
                valueTonnes = kg / 1000.0,
                valueKg = kg,
                monthStartMillis = start
            )
        }
    }

    /**
     * Last [monthCount] calendar months including the current month.
     * E.g. monthCount=13 on 18.07.2026 → Jul 2025 … Jul 2026.
     */
    fun tonnageLastMonths(
        entities: List<ShipmentEntity>,
        monthCount: Int = DEFAULT_MONTH_COUNT,
        nowMillis: Long = System.currentTimeMillis()
    ): List<StatBarEntry> {
        val (from, to) = lastMonthsBounds(monthCount, nowMillis)
        return tonnageByMonthRange(entities, from, to)
    }

    /** @deprecated Prefer [tonnageLastMonths] with 13 months. */
    fun tonnageLast12Months(
        entities: List<ShipmentEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<StatBarEntry> = tonnageLastMonths(entities, monthCount = 12, nowMillis = nowMillis)

    /** Inclusive millis range covering [fromMonthStart] … end of [toMonthStart]. */
    fun monthRangeMillis(fromMonthStart: Long, toMonthStart: Long): Pair<Long, Long> {
        val from = minOf(fromMonthStart, toMonthStart)
        val to = maxOf(fromMonthStart, toMonthStart)
        return from to endOfMonth(to)
    }

    fun lastMonthsBounds(
        monthCount: Int = DEFAULT_MONTH_COUNT,
        nowMillis: Long = System.currentTimeMillis()
    ): Pair<Long, Long> {
        val count = monthCount.coerceAtLeast(1)
        val to = monthStart(nowMillis, monthsAgo = 0)
        val from = monthStart(nowMillis, monthsAgo = count - 1)
        return from to to
    }

    /** Inclusive range covering the last [monthCount] calendar months (through now). */
    fun lastMonthsRange(
        monthCount: Int = DEFAULT_MONTH_COUNT,
        nowMillis: Long = System.currentTimeMillis()
    ): Pair<Long, Long> {
        val (from, toMonth) = lastMonthsBounds(monthCount, nowMillis)
        return from to maxOf(endOfMonth(toMonth), nowMillis)
    }

    fun last12MonthsRange(nowMillis: Long = System.currentTimeMillis()): Pair<Long, Long> =
        lastMonthsRange(monthCount = 12, nowMillis = nowMillis)

    /**
     * Month choices for period pickers, newest first.
     * [count] months ending at the current month.
     */
    fun monthChoices(
        count: Int = 36,
        nowMillis: Long = System.currentTimeMillis()
    ): List<MonthChoice> {
        val labelFmt = SimpleDateFormat("MM.yyyy", Locale.getDefault())
        return (0 until count.coerceAtLeast(1)).map { monthsAgo ->
            val start = monthStart(nowMillis, monthsAgo = monthsAgo)
            MonthChoice(label = labelFmt.format(start), startMillis = start)
        }
    }

    fun tonnageByMonth(entities: List<ShipmentEntity>): List<StatBarEntry> {
        if (entities.isEmpty()) return emptyList()
        val fmt = SimpleDateFormat("MM.yyyy", Locale.getDefault())
        return entities
            .groupBy { monthKey(it.completedAtMillis) }
            .toSortedMap()
            .map { (_, list) ->
                val sample = list.first()
                val kg = list.sumOf { it.totalWeight }
                StatBarEntry(
                    label = fmt.format(sample.completedAtMillis),
                    valueTonnes = kg / 1000.0,
                    valueKg = kg,
                    monthStartMillis = monthStart(sample.completedAtMillis, monthsAgo = 0)
                )
            }
    }

    fun tonnageByCustomer(entities: List<ShipmentEntity>, limit: Int = 10): List<StatBarEntry> {
        return entities
            .groupBy { it.customer.ifBlank { "—" } }
            .map { (customer, list) ->
                val kg = list.sumOf { it.totalWeight }
                StatBarEntry(
                    label = customer,
                    valueTonnes = kg / 1000.0,
                    valueKg = kg
                )
            }
            .sortedByDescending { it.valueTonnes }
            .take(limit)
    }

    fun comparePeriods(
        current: List<ShipmentEntity>,
        previous: List<ShipmentEntity>,
        currentFrom: Long,
        currentTo: Long,
        previousFrom: Long,
        previousTo: Long
    ): PeriodComparison {
        val fmt = SimpleDateFormat("dd.MM.yy", Locale.getDefault())
        return PeriodComparison(
            currentTonnes = current.sumOf { it.totalWeight } / 1000.0,
            previousTonnes = previous.sumOf { it.totalWeight } / 1000.0,
            currentLabel = "${fmt.format(currentFrom)}–${fmt.format(currentTo)}",
            previousLabel = "${fmt.format(previousFrom)}–${fmt.format(previousTo)}"
        )
    }

    fun previousPeriodRange(fromMillis: Long, toMillis: Long): Pair<Long, Long> {
        val duration = (toMillis - fromMillis).coerceAtLeast(0L)
        val prevTo = (fromMillis - 1).coerceAtLeast(0L)
        val prevFrom = (prevTo - duration).coerceAtLeast(0L)
        return prevFrom to prevTo
    }

    fun stackedChart(
        entities: List<ShipmentEntity>,
        groupBy: StatDimension,
        splitBy: StatSplit,
        fromMonthStart: Long,
        toMonthStart: Long,
        topGroups: Int = StatisticsBreakdown.DEFAULT_TOP_GROUPS,
        topSeries: Int = StatisticsBreakdown.DEFAULT_TOP_SERIES,
        otherSeriesLabel: String = "…",
        otherGroupLabel: String = "…",
        portFilter: String = "",
        productFilter: String = ""
    ): StackedChartResult = StatisticsBreakdown.buildStackedChart(
        entities = entities,
        groupBy = groupBy,
        splitBy = splitBy,
        fromMonthStart = fromMonthStart,
        toMonthStart = toMonthStart,
        topGroups = topGroups,
        topSeries = topSeries,
        otherSeriesLabel = otherSeriesLabel,
        otherGroupLabel = otherGroupLabel,
        portFilter = portFilter,
        productFilter = productFilter
    )

    internal fun monthsInclusivePublic(fromMonthStart: Long, toMonthStart: Long): List<Long> =
        monthsInclusive(fromMonthStart, toMonthStart)

    private fun monthsInclusive(fromMonthStart: Long, toMonthStart: Long): List<Long> {
        val result = mutableListOf<Long>()
        var cursor = fromMonthStart
        while (cursor <= toMonthStart) {
            result += cursor
            cursor = addMonths(cursor, 1)
        }
        return result
    }

    private fun monthKey(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}"
    }

    /** First moment of the calendar month that is [monthsAgo] months before [nowMillis]'s month. */
    fun monthStart(nowMillis: Long, monthsAgo: Int): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, -monthsAgo)
        }
        return cal.timeInMillis
    }

    private fun addMonths(monthStartMillis: Long, delta: Int): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = monthStartMillis
            add(Calendar.MONTH, delta)
        }
        return cal.timeInMillis
    }

    private fun endOfMonth(monthStartMillis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = monthStartMillis
            add(Calendar.MONTH, 1)
            add(Calendar.MILLISECOND, -1)
        }
        return cal.timeInMillis
    }
}
