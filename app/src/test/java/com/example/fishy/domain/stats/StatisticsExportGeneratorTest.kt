package com.example.fishy.domain.stats

import com.example.fishy.data.local.entity.ShipmentEntity
import com.example.fishy.data.serialization.FishyJson
import com.example.fishy.domain.model.Pallet
import com.example.fishy.domain.model.Product
import com.example.fishy.domain.model.ShipmentMode
import com.example.fishy.domain.model.ShipmentPayload
import com.example.fishy.domain.report.ReportDocxBuilder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class StatisticsExportGeneratorTest {

    @Test
    fun exportListsProductsPerMonthWithoutOtherBucket() {
        val aug = calendarMillis(2026, Calendar.AUGUST, 10)
        val sep = calendarMillis(2026, Calendar.SEPTEMBER, 5)
        val entities = listOf(
            archived(
                id = 1,
                completedAt = aug,
                products = listOf(
                    product("Горбуша", weight = 20.0, places = 500.0),
                    product("Минтай", weight = 20.0, places = 1400.0)
                )
            ),
            archived(
                id = 2,
                completedAt = sep,
                products = listOf(
                    product("Навага", weight = 20.0, places = 250.0),
                    product("Сайра", weight = 20.0, places = 450.0)
                )
            )
        )
        val from = StatisticsAggregator.monthStart(aug, monthsAgo = 0)
        val to = StatisticsAggregator.monthStart(sep, monthsAgo = 0)
        val text = StatisticsExportGenerator.generate(
            entities = entities,
            fromMonthStart = from,
            toMonthStart = to,
            thousandsSeparator = true,
            generatedAtMillis = calendarMillis(2026, Calendar.SEPTEMBER, 20, hour = 15, minute = 30),
            locale = Locale("ru", "RU")
        )

        assertTrue(text.contains("Август 2026:"))
        assertTrue(text.contains("Горбуша – 10"))
        assertTrue(text.contains("Минтай – 28"))
        assertTrue(text.contains("Общий тоннаж за август 2026:"))
        assertTrue(text.contains("Сентябрь 2026:"))
        assertTrue(text.contains("Навага – 5"))
        assertTrue(text.contains("Сайра – 9"))
        assertTrue(text.contains("Общий тоннаж за сентябрь 2026:"))
        assertTrue(text.contains("Общий тоннаж за период: 52"))
        assertTrue(text.contains("Сгенерировано приложением «Фишка»."))
        assertTrue(text.contains("20.09.2026"))
        assertFalse(text.contains("Прочее"))
        assertFalse(text.contains(StatisticsBreakdown.OTHER_KEY))
    }

    @Test
    fun singleMonthExportOmitsPeriodTotalLine() {
        val aug = calendarMillis(2026, Calendar.AUGUST, 10)
        val entities = listOf(
            archived(
                id = 1,
                completedAt = aug,
                products = listOf(product("Горбуша", weight = 20.0, places = 500.0))
            )
        )
        val month = StatisticsAggregator.monthStart(aug, monthsAgo = 0)
        val text = StatisticsExportGenerator.generate(
            entities = entities,
            fromMonthStart = month,
            toMonthStart = month,
            thousandsSeparator = true,
            generatedAtMillis = calendarMillis(2026, Calendar.AUGUST, 10, hour = 15, minute = 9),
            locale = Locale("ru", "RU")
        )

        assertTrue(text.contains("Август 2026:"))
        assertTrue(text.contains("Общий тоннаж за август 2026:"))
        assertFalse(text.contains("Общий тоннаж за период"))
        assertTrue(text.contains("Сгенерировано приложением «Фишка»."))
    }

    @Test
    fun periodTotalLineIsBoldInDocx() {
        assertTrue(ReportDocxBuilder.shouldBoldLine("Общий тоннаж за период: 52 000 кг"))
        assertFalse(ReportDocxBuilder.shouldBoldLine("Общий тоннаж за август 2026: 38 000 кг"))
    }

    private fun product(name: String, weight: Double, places: Double) = Product(
        name = name,
        packageWeight = weight,
        quantity = places.toInt(),
        pallets = listOf(Pallet(places = places))
    )

    private fun archived(id: Long, completedAt: Long, products: List<Product>): ShipmentEntity {
        val payload = ShipmentPayload(
            mode = ShipmentMode.MONO,
            customer = "Test",
            products = products,
            createdAtMillis = completedAt,
            completedAtMillis = completedAt
        )
        val weight = products.sumOf { it.packageWeight * it.pallets.sumOf { p -> p.places } }
        return ShipmentEntity(
            id = id,
            payloadJson = FishyJson.encodePayload(payload),
            customer = "Test",
            port = "P",
            mode = "MONO",
            totalPlaces = products.sumOf { it.pallets.sumOf { p -> p.places } }.toInt(),
            totalWeight = weight,
            transportSummary = "",
            createdAtMillis = completedAt,
            completedAtMillis = completedAt,
            isDraft = false,
            draftName = ""
        )
    }

    private fun calendarMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 12,
        minute: Int = 0
    ): Long {
        val cal = Calendar.getInstance()
        cal.set(year, month, day, hour, minute, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
