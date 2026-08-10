package com.example.fishy.domain.stats

import com.example.fishy.data.local.entity.ShipmentEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class StatisticsAggregatorTest {

    @Test
    fun tonnageByMonthGroupsAndSorts() {
        val entities = listOf(
            entity(id = 1, completedAt = monthMillis(2025, 0), weight = 1000.0),
            entity(id = 2, completedAt = monthMillis(2025, 0), weight = 2000.0),
            entity(id = 3, completedAt = monthMillis(2025, 1), weight = 500.0)
        )

        val result = StatisticsAggregator.tonnageByMonth(entities)

        assertEquals(2, result.size)
        assertEquals(3.0, result[0].valueTonnes, 0.001)
        assertEquals(0.5, result[1].valueTonnes, 0.001)
        assertEquals(3000.0, result[0].valueKg, 0.001)
    }

    @Test
    fun tonnageLastMonthsReturns13BarsIncludingSameMonthLastYear() {
        // "Now" = 18 July 2026 → window Jul 2025 … Jul 2026 (13 months).
        val now = calendarMillis(2026, Calendar.JULY, 18)
        val entities = listOf(
            entity(id = 1, completedAt = calendarMillis(2025, Calendar.JULY, 1), weight = 1000.0),
            entity(id = 2, completedAt = calendarMillis(2025, Calendar.AUGUST, 1), weight = 2000.0),
            entity(id = 3, completedAt = calendarMillis(2026, Calendar.JULY, 10), weight = 500.0)
        )

        val result = StatisticsAggregator.tonnageLastMonths(entities, monthCount = 13, nowMillis = now)

        assertEquals(13, result.size)
        // First bar = Jul 2025 (same month last year)
        assertEquals(1.0, result[0].valueTonnes, 0.001)
        assertEquals(1000.0, result[0].valueKg, 0.001)
        // Second bar = Aug 2025
        assertEquals(2.0, result[1].valueTonnes, 0.001)
        // Last bar = Jul 2026
        assertEquals(0.5, result[12].valueTonnes, 0.001)
        // Empty months are zero
        assertEquals(0.0, result[2].valueTonnes, 0.001)
    }

    @Test
    fun lastMonthsBoundsStartsOnSameMonthLastYear() {
        val now = calendarMillis(2026, Calendar.JULY, 18)
        val (from, to) = StatisticsAggregator.lastMonthsBounds(monthCount = 13, nowMillis = now)
        val fromCal = Calendar.getInstance().apply { timeInMillis = from }
        val toCal = Calendar.getInstance().apply { timeInMillis = to }
        assertEquals(1, fromCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.JULY, fromCal.get(Calendar.MONTH))
        assertEquals(2025, fromCal.get(Calendar.YEAR))
        assertEquals(Calendar.JULY, toCal.get(Calendar.MONTH))
        assertEquals(2026, toCal.get(Calendar.YEAR))
    }

    @Test
    fun tonnageByMonthRangeRespectsSelectedBounds() {
        val from = calendarMillis(2026, Calendar.MAY, 1)
        val to = calendarMillis(2026, Calendar.JULY, 1)
        val entities = listOf(
            entity(id = 1, completedAt = calendarMillis(2026, Calendar.APRIL, 10), weight = 9000.0),
            entity(id = 2, completedAt = calendarMillis(2026, Calendar.MAY, 10), weight = 1000.0),
            entity(id = 3, completedAt = calendarMillis(2026, Calendar.JULY, 10), weight = 2000.0)
        )

        val result = StatisticsAggregator.tonnageByMonthRange(entities, from, to)

        assertEquals(3, result.size)
        assertEquals(1.0, result[0].valueTonnes, 0.001)
        assertEquals(0.0, result[1].valueTonnes, 0.001)
        assertEquals(2.0, result[2].valueTonnes, 0.001)
    }

    @Test
    fun previousPeriodRangeMatchesDuration() {
        val from = 10_000L
        val to = 19_000L
        val (prevFrom, prevTo) = StatisticsAggregator.previousPeriodRange(from, to)
        assertEquals(from - 1, prevTo)
        assertEquals(prevTo - (to - from), prevFrom)
    }

    @Test
    fun monthChoicesFromFirstShipmentUsesAnchorMinus12Months() {
        // First ship Oct 2027 → earliest Oct 2026; now = Oct 2027 → 13 months.
        val first = calendarMillis(2027, Calendar.OCTOBER, 15)
        val now = calendarMillis(2027, Calendar.OCTOBER, 20)
        val choices = StatisticsAggregator.monthChoicesFromFirstShipment(
            firstShipmentMillis = first,
            nowMillis = now
        )
        assertEquals(13, choices.size)
        val earliest = Calendar.getInstance().apply { timeInMillis = choices.last().startMillis }
        val latest = Calendar.getInstance().apply { timeInMillis = choices.first().startMillis }
        assertEquals(Calendar.OCTOBER, earliest.get(Calendar.MONTH))
        assertEquals(2026, earliest.get(Calendar.YEAR))
        assertEquals(Calendar.OCTOBER, latest.get(Calendar.MONTH))
        assertEquals(2027, latest.get(Calendar.YEAR))
    }

    @Test
    fun monthChoicesFromEmptyArchiveUsesTodayMinus12Months() {
        val now = calendarMillis(2026, Calendar.JULY, 18)
        val choices = StatisticsAggregator.monthChoicesFromFirstShipment(
            firstShipmentMillis = null,
            nowMillis = now
        )
        assertEquals(13, choices.size)
        val earliest = Calendar.getInstance().apply { timeInMillis = choices.last().startMillis }
        assertEquals(Calendar.JULY, earliest.get(Calendar.MONTH))
        assertEquals(2025, earliest.get(Calendar.YEAR))
    }

    @Test
    fun monthChoicesBetweenIsNewestFirstInclusive() {
        val from = calendarMillis(2026, Calendar.MAY, 1)
        val to = calendarMillis(2026, Calendar.JULY, 1)
        val choices = StatisticsAggregator.monthChoicesBetween(from, to)
        assertEquals(3, choices.size)
        val first = Calendar.getInstance().apply { timeInMillis = choices.first().startMillis }
        val last = Calendar.getInstance().apply { timeInMillis = choices.last().startMillis }
        assertEquals(Calendar.JULY, first.get(Calendar.MONTH))
        assertEquals(2026, first.get(Calendar.YEAR))
        assertEquals(Calendar.MAY, last.get(Calendar.MONTH))
        assertEquals(2026, last.get(Calendar.YEAR))
    }

    private fun entity(id: Long, completedAt: Long, weight: Double) = ShipmentEntity(
        id = id,
        payloadJson = "{}",
        customer = "A",
        port = "P",
        mode = "MONO",
        totalPlaces = 0,
        totalWeight = weight,
        transportSummary = "",
        createdAtMillis = completedAt,
        completedAtMillis = completedAt,
        isDraft = false,
        draftName = ""
    )

    private fun monthMillis(year: Int, month: Int): Long =
        calendarMillis(year, month, 15)

    private fun calendarMillis(year: Int, month: Int, day: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(year, month, day, 12, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
