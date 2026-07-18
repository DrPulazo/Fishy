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
    fun tonnageLast12MonthsAlwaysReturns12BarsFromFullMonths() {
        // "Now" = 18 July 2026 → window Aug 2025 … Jul 2026 (12 months), Jul 2025 not included.
        val now = calendarMillis(2026, Calendar.JULY, 18)
        val entities = listOf(
            entity(id = 1, completedAt = calendarMillis(2025, Calendar.JULY, 1), weight = 1000.0),
            entity(id = 2, completedAt = calendarMillis(2025, Calendar.AUGUST, 1), weight = 2000.0),
            entity(id = 3, completedAt = calendarMillis(2026, Calendar.JULY, 10), weight = 500.0)
        )

        val result = StatisticsAggregator.tonnageLast12Months(entities, now)

        assertEquals(12, result.size)
        // First bar = Aug 2025
        assertEquals(2.0, result[0].valueTonnes, 0.001)
        assertEquals(2000.0, result[0].valueKg, 0.001)
        // Last bar = Jul 2026
        assertEquals(0.5, result[11].valueTonnes, 0.001)
        // Empty months are zero
        assertEquals(0.0, result[1].valueTonnes, 0.001)
    }

    @Test
    fun last12MonthsRangeStartsOnFirstOfOldestMonth() {
        val now = calendarMillis(2026, Calendar.JULY, 18)
        val (from, to) = StatisticsAggregator.last12MonthsRange(now)
        assertEquals(now, to)
        val cal = Calendar.getInstance().apply { timeInMillis = from }
        assertEquals(1, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.AUGUST, cal.get(Calendar.MONTH))
        assertEquals(2025, cal.get(Calendar.YEAR))
    }

    @Test
    fun previousPeriodRangeMatchesDuration() {
        val from = 10_000L
        val to = 19_000L
        val (prevFrom, prevTo) = StatisticsAggregator.previousPeriodRange(from, to)
        assertEquals(from - 1, prevTo)
        assertEquals(prevTo - (to - from), prevFrom)
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
