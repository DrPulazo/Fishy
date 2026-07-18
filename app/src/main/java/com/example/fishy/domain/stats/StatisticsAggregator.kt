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

    /**
     * Last 12 calendar months including the current month.
     * Window starts on day 1 of (current month − 11), e.g. 18.07.2026 → Aug 2025 … Jul 2026.
     * Each month is a full calendar month from the 1st (not from "today minus 1 year").
     */
    fun tonnageLast12Months(
        entities: List<ShipmentEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<StatBarEntry> {
        val labelFmt = SimpleDateFormat("MM.yyyy", Locale.getDefault())
        val byMonth = entities.groupBy { monthKey(it.completedAtMillis) }
        return (0 until 12).map { index ->
            val start = monthStart(nowMillis, monthsAgo = 11 - index)
            val key = monthKey(start)
            val list = byMonth[key].orEmpty()
            val kg = list.sumOf { it.totalWeight }
            StatBarEntry(
                label = labelFmt.format(start),
                valueTonnes = kg / 1000.0,
                valueKg = kg,
                monthStartMillis = start
            )
        }
    }

    /** Inclusive range covering the 12 calendar months of [tonnageLast12Months]. */
    fun last12MonthsRange(nowMillis: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val from = monthStart(nowMillis, monthsAgo = 11)
        return from to nowMillis
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

    private fun monthKey(millis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}"
    }

    /** First moment of the calendar month that is [monthsAgo] months before [nowMillis]'s month. */
    private fun monthStart(nowMillis: Long, monthsAgo: Int): Long {
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
}
