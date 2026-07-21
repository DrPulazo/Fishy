package com.example.fishy.domain.stats

import com.example.fishy.data.local.entity.ShipmentEntity
import com.example.fishy.data.serialization.FishyJson
import com.example.fishy.domain.model.Pallet
import com.example.fishy.domain.model.PortGroup
import com.example.fishy.domain.model.Product
import com.example.fishy.domain.model.ShipmentMode
import com.example.fishy.domain.model.ShipmentPayload
import com.example.fishy.domain.model.ShipmentFilters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class StatisticsBreakdownTest {

    @Test
    fun monthByProduct_twoProductsInSameMonth() {
        val month = calendarMillis(2026, Calendar.JANUARY, 15)
        val entities = listOf(
            entity(
                id = 1,
                completedAt = month,
                payload = monoPayload(
                    Product(name = "Минтай", packageWeight = 10.0, pallets = listOf(Pallet(places = 100.0))),
                    Product(name = "Сельдь", packageWeight = 5.0, pallets = listOf(Pallet(places = 40.0)))
                ),
                totalWeight = 1200.0
            )
        )
        val from = StatisticsAggregator.monthStart(month, monthsAgo = 0)
        val result = StatisticsBreakdown.buildStackedChart(
            entities = entities,
            groupBy = StatDimension.MONTH,
            splitBy = StatSplit.PRODUCT,
            fromMonthStart = from,
            toMonthStart = from
        )
        assertEquals(1, result.bars.size)
        assertEquals(2, result.bars[0].segments.size)
        assertEquals(1200.0, result.bars[0].totalKg, 0.001)
        assertEquals(2, result.legend.size)
    }

    @Test
    fun emptyProductNameUsesDashLabel() {
        val month = calendarMillis(2026, Calendar.FEBRUARY, 10)
        val entities = listOf(
            entity(
                id = 1,
                completedAt = month,
                payload = monoPayload(
                    Product(packageWeight = 10.0, pallets = listOf(Pallet(places = 5.0)))
                ),
                totalWeight = 50.0
            )
        )
        val from = StatisticsAggregator.monthStart(month, monthsAgo = 0)
        val result = StatisticsBreakdown.buildStackedChart(
            entities = entities,
            groupBy = StatDimension.MONTH,
            splitBy = StatSplit.PRODUCT,
            fromMonthStart = from,
            toMonthStart = from
        )
        assertEquals(StatisticsBreakdown.UNKNOWN_LABEL, result.legend.first().label)
    }

    @Test
    fun topCustomers_groupsBeyondLimitIntoOther() {
        val month = calendarMillis(2026, Calendar.MARCH, 5)
        val entities = (1..12).map { i ->
            entity(
                id = i.toLong(),
                completedAt = month,
                customer = "Customer $i",
                payload = monoPayload(
                    Product(name = "Fish", packageWeight = 1.0, pallets = listOf(Pallet(places = i.toDouble())))
                ),
                totalWeight = i.toDouble()
            )
        }
        val from = StatisticsAggregator.monthStart(month, monthsAgo = 0)
        val result = StatisticsBreakdown.buildStackedChart(
            entities = entities,
            groupBy = StatDimension.CUSTOMER,
            splitBy = StatSplit.PRODUCT,
            fromMonthStart = from,
            toMonthStart = from,
            topGroups = 10,
            otherGroupLabel = "Other"
        )
        assertEquals(11, result.bars.size)
        assertTrue(result.bars.any { it.label == "Other" })
    }

    @Test
    fun stableColorIndexForSameProductKey() {
        val month = calendarMillis(2026, Calendar.APRIL, 1)
        val product = Product(name = "Треска", packageWeight = 2.0, pallets = listOf(Pallet(places = 10.0)))
        val entities = listOf(
            entity(id = 1, completedAt = month, payload = monoPayload(product), totalWeight = 20.0),
            entity(id = 2, completedAt = month, payload = monoPayload(product.copy(id = 2)), totalWeight = 20.0)
        )
        val from = StatisticsAggregator.monthStart(month, monthsAgo = 0)
        val result = StatisticsBreakdown.buildStackedChart(
            entities = entities,
            groupBy = StatDimension.MONTH,
            splitBy = StatSplit.PRODUCT,
            fromMonthStart = from,
            toMonthStart = from
        )
        val legendIndex = result.legend.first().colorIndex
        val segmentIndex = result.bars.first().segments.first().colorIndex
        assertEquals(legendIndex, segmentIndex)
    }

    @Test
    fun sameProductNameDifferentBatch_mergesIntoOneBar() {
        val month = calendarMillis(2026, Calendar.JUNE, 1)
        val mintaiA = Product(
            name = "Минтай",
            batch = "П-1",
            manufacturer = "A",
            packageWeight = 10.0,
            pallets = listOf(Pallet(places = 10.0))
        )
        val mintaiB = Product(
            name = "Минтай",
            batch = "П-2",
            manufacturer = "B",
            packageWeight = 24.0,
            pallets = listOf(Pallet(places = 5.0))
        )
        val entities = listOf(
            entity(id = 1, completedAt = month, payload = monoPayload(mintaiA), totalWeight = 100.0),
            entity(id = 2, completedAt = month, payload = monoPayload(mintaiB), totalWeight = 120.0)
        )
        val from = StatisticsAggregator.monthStart(month, monthsAgo = 0)
        val result = StatisticsBreakdown.buildStackedChart(
            entities = entities,
            groupBy = StatDimension.PRODUCT,
            splitBy = StatSplit.MONTH,
            fromMonthStart = from,
            toMonthStart = from
        )
        assertEquals(1, result.bars.size)
        assertEquals("Минтай", result.bars[0].label)
        assertEquals(220.0, result.bars[0].totalKg, 0.001)
    }

    @Test
    fun multiPort_portsCountedSeparately_notCombined() {
        val month = calendarMillis(2026, Calendar.JULY, 10)
        val murmanskProduct = Product(
            name = "Минтай",
            packageWeight = 1000.0,
            pallets = listOf(Pallet(places = 5.0))
        )
        val arkhProduct = Product(
            name = "Сельдь",
            packageWeight = 1000.0,
            pallets = listOf(Pallet(places = 10.0))
        )
        val payload = ShipmentPayload(
            mode = ShipmentMode.MULTI_PORT,
            multiPorts = listOf(
                PortGroup(port = "Мурманск", products = listOf(murmanskProduct)),
                PortGroup(port = "Архангельск", products = listOf(arkhProduct))
            )
        )
        val entities = listOf(
            entity(
                id = 1,
                completedAt = month,
                payload = payload,
                totalWeight = 15_000.0,
                customer = "Рыбпром",
                port = "Мурманск, Архангельск"
            )
        )
        val from = StatisticsAggregator.monthStart(month, monthsAgo = 0)

        val portChart = StatisticsBreakdown.buildStackedChart(
            entities = entities,
            groupBy = StatDimension.PORT,
            splitBy = StatSplit.PRODUCT,
            fromMonthStart = from,
            toMonthStart = from
        )
        assertEquals(2, portChart.bars.size)
        val murmanskBar = portChart.bars.first { it.label == "Мурманск" }
        val arkhBar = portChart.bars.first { it.label == "Архангельск" }
        assertEquals(5000.0, murmanskBar.totalKg, 0.001)
        assertEquals(10_000.0, arkhBar.totalKg, 0.001)

        val filtered = entities.filter { entity ->
            ShipmentFilters.matchesPortFilter(entity, payload, "Мурманск")
        }
        assertEquals(5000.0, StatisticsBreakdown.totalWeightKg(filtered, portFilter = "Мурманск"), 0.001)
        assertEquals(10_000.0, StatisticsBreakdown.totalWeightKg(filtered, portFilter = "Архангельск"), 0.001)
    }

    @Test
    fun invalidCombinationReturnsEmpty() {
        val month = calendarMillis(2026, Calendar.MAY, 1)
        val entities = listOf(
            entity(id = 1, completedAt = month, payload = monoPayload(), totalWeight = 0.0)
        )
        val from = StatisticsAggregator.monthStart(month, monthsAgo = 0)
        val result = StatisticsBreakdown.buildStackedChart(
            entities = entities,
            groupBy = StatDimension.MONTH,
            splitBy = StatSplit.MONTH,
            fromMonthStart = from,
            toMonthStart = from
        )
        assertTrue(result.bars.isEmpty())
    }

    private fun monoPayload(vararg products: Product): ShipmentPayload =
        ShipmentPayload(mode = ShipmentMode.MONO, products = products.toList())

    private fun entity(
        id: Long,
        completedAt: Long,
        payload: ShipmentPayload,
        totalWeight: Double,
        customer: String = "A",
        port: String = payload.port
    ) = ShipmentEntity(
        id = id,
        payloadJson = FishyJson.encodePayload(payload),
        customer = customer,
        port = port,
        mode = payload.mode.name,
        totalPlaces = 0,
        totalWeight = totalWeight,
        transportSummary = "",
        createdAtMillis = completedAt,
        completedAtMillis = completedAt,
        isDraft = false,
        draftName = ""
    )

    private fun calendarMillis(year: Int, month: Int, day: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(year, month, day, 12, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
