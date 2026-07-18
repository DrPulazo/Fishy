package com.example.fishy.domain.calc

import com.example.fishy.domain.model.BatchLimit
import com.example.fishy.domain.model.Pallet
import com.example.fishy.domain.model.Product
import com.example.fishy.domain.model.ShipmentPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShipmentCalculatorTest {

    @Test
    fun remainderAndTotals() {
        val product = Product(
            name = "Fish",
            quantity = 100,
            packageWeight = 10.0,
            pallets = listOf(
                Pallet(places = 40),
                Pallet(places = 40)
            )
        )
        assertEquals(20, ShipmentCalculator.remainder(product, false, false))
        val totals = ShipmentCalculator.totalsForProducts(listOf(product), false)
        assertEquals(80, totals.places)
        assertEquals(800.0, totals.actualWeight, 0.001)
    }

    @Test
    fun forecastCreatesPlaceholders() {
        val product = Product(
            quantity = 100,
            pallets = listOf(Pallet(places = 40, palletNumber = 1))
        )
        val forecasted = ShipmentCalculator.applyForecastPlaceholders(product)
        assertEquals(3, forecasted.pallets.size)
        assertTrue(forecasted.pallets[1].isPlaceholder)
        assertEquals(20, forecasted.pallets.last().places)
    }

    @Test
    fun forecastRejectsTooManyPlaceholders() {
        val product = Product(
            quantity = 1000,
            pallets = listOf(Pallet(places = 1, palletNumber = 1))
        )
        assertEquals(1000, ShipmentCalculator.expectedForecastPallets(product))
        val forecasted = ShipmentCalculator.applyForecastPlaceholders(product)
        assertEquals(1, forecasted.pallets.size)
        assertFalse(forecasted.pallets.first().isPlaceholder)
    }

    @Test
    fun forecastExpectationMessagePlurals() {
        assertEquals(
            "Ожидается 2 поддона по 40 мест и 1 поддон по 20 мест",
            ShipmentCalculator.formatForecastExpectationRu(100, 40)
        )
        assertEquals(
            "Ожидается 2 поддона по 50 мест",
            ShipmentCalculator.formatForecastExpectationRu(100, 50)
        )
        assertEquals(
            "Ожидается 1 поддон по 1 место",
            ShipmentCalculator.formatForecastExpectationRu(1, 1)
        )
        assertEquals(
            "Ожидается 5 поддонов по 10 мест",
            ShipmentCalculator.formatForecastExpectationRu(50, 10)
        )
    }

    @Test
    fun canAutoForecastOnlyWithSingleRealPallet() {
        val ready = Product(quantity = 100, pallets = listOf(Pallet(places = 40)))
        assertTrue(ShipmentCalculator.canAutoForecast(ready))
        val locked = Product(
            quantity = 100,
            pallets = listOf(Pallet(places = 40), Pallet(places = 40, isPlaceholder = false))
        )
        assertFalse(ShipmentCalculator.canAutoForecast(locked))
    }

    @Test
    fun batchLimitBlocksExtraPlaces() {
        val product = Product(
            name = "Fish",
            batch = "A",
            manufacturer = "Plant",
            packageWeight = 10.0,
            quantity = 100,
            pallets = listOf(Pallet(places = 50))
        )
        val payload = ShipmentPayload(
            batchControlEnabled = true,
            batchLimits = listOf(
                BatchLimit(
                    productName = "Fish",
                    batchName = "A",
                    manufacturer = "Plant",
                    packageWeight = 10.0,
                    plannedPlaces = 50
                )
            ),
            products = listOf(product)
        )
        assertFalse(ShipmentCalculator.canAddPlaces(payload, product, 1))
        assertTrue(ShipmentCalculator.canAddPlaces(payload, product, 0))
    }

    @Test
    fun batchKeySeparatesSameBatchDifferentProduct() {
        val a = Product(name = "Salmon", batch = "B1", manufacturer = "X", packageWeight = 10.0)
        val b = Product(name = "Cod", batch = "B1", manufacturer = "X", packageWeight = 10.0)
        assertTrue(ShipmentCalculator.batchKey(a) != ShipmentCalculator.batchKey(b))
    }

    @Test
    fun progressPercent() {
        val payload = ShipmentPayload(
            products = listOf(
                Product(quantity = 100, pallets = listOf(Pallet(places = 25)))
            )
        )
        assertEquals(0.25f, ShipmentCalculator.progressPercent(payload), 0.001f)
    }

    @Test
    fun progressPercentOverloadExceedsOne() {
        val payload = ShipmentPayload(
            products = listOf(
                Product(quantity = 100, pallets = listOf(Pallet(places = 120)))
            )
        )
        assertEquals(1.2f, ShipmentCalculator.progressPercent(payload), 0.001f)
    }
}
