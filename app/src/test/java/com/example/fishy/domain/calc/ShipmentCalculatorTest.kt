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
    fun formatKindsRuDeclension() {
        assertEquals("1 вид", ShipmentCalculator.formatKindsRu(1))
        assertEquals("2 вида", ShipmentCalculator.formatKindsRu(2))
        assertEquals("5 видов", ShipmentCalculator.formatKindsRu(5))
        assertEquals("21 вид", ShipmentCalculator.formatKindsRu(21))
        assertEquals("12 видов", ShipmentCalculator.formatKindsRu(12))
    }

    @Test
    fun formatPlacesRuDeclension() {
        assertEquals("1 место", ShipmentCalculator.formatPlacesRu(1))
        assertEquals("2 места", ShipmentCalculator.formatPlacesRu(2))
        assertEquals("5 мест", ShipmentCalculator.formatPlacesRu(5))
    }

    @Test
    fun productTypesDistinctByNameBatchManufacturerTare() {
        val same = Product(
            name = "Минтай",
            batch = "П-2",
            manufacturer = "Колхоз Ленина",
            packageWeight = 24.0,
            quantity = 10,
            pallets = listOf(Pallet(places = 5.0))
        )
        val sameAgain = same.copy(id = 2, quantity = 20, pallets = listOf(Pallet(places = 10.0)))
        val differentTare = same.copy(id = 3, packageWeight = 25.0)
        assertEquals(1, ShipmentCalculator.totalsForProducts(listOf(same, sameAgain), false).productTypes)
        assertEquals(2, ShipmentCalculator.totalsForProducts(listOf(same, differentTare), false).productTypes)
    }

    @Test
    fun productTypesCountPalletsWithoutMetadata() {
        val unnamed = Product(
            quantity = 100,
            pallets = listOf(Pallet(places = 40.0))
        )
        assertEquals(1, ShipmentCalculator.totalsForProducts(listOf(unnamed), false).productTypes)
    }

    @Test
    fun remainderAndTotals() {
        val product = Product(
            name = "Fish",
            quantity = 100,
            packageWeight = 10.0,
            pallets = listOf(
                Pallet(places = 40.0),
                Pallet(places = 40.0)
            )
        )
        assertEquals(20.0, ShipmentCalculator.remainder(product, false, false), 0.001)
        val totals = ShipmentCalculator.totalsForProducts(listOf(product), false)
        assertEquals(80.0, totals.places, 0.001)
        assertEquals(800.0, totals.actualWeight, 0.001)
    }

    @Test
    fun forecastCreatesPlaceholders() {
        val product = Product(
            quantity = 100,
            pallets = listOf(Pallet(places = 40.0, palletNumber = 1))
        )
        val forecasted = ShipmentCalculator.applyForecastPlaceholders(product)
        assertEquals(3, forecasted.pallets.size)
        assertTrue(forecasted.pallets[1].isPlaceholder)
        assertEquals(20.0, forecasted.pallets.last().places, 0.001)
    }

    @Test
    fun forecastRejectsTooManyPlaceholders() {
        val product = Product(
            quantity = 1000,
            pallets = listOf(Pallet(places = 1.0, palletNumber = 1))
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
            ShipmentCalculator.formatForecastExpectationRu(100, 40.0)
        )
        assertEquals(
            "Ожидается 2 поддона по 50 мест",
            ShipmentCalculator.formatForecastExpectationRu(100, 50.0)
        )
        assertEquals(
            "Ожидается 1 поддон по 1 место",
            ShipmentCalculator.formatForecastExpectationRu(1, 1.0)
        )
        assertEquals(
            "Ожидается 5 поддонов по 10 мест",
            ShipmentCalculator.formatForecastExpectationRu(50, 10.0)
        )
    }

    @Test
    fun canAutoForecastOnlyWithSingleRealPallet() {
        val ready = Product(quantity = 100, pallets = listOf(Pallet(places = 40.0)))
        assertTrue(ShipmentCalculator.canAutoForecast(ready))
        val locked = Product(
            quantity = 100,
            pallets = listOf(Pallet(places = 40.0), Pallet(places = 40.0))
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
            pallets = listOf(Pallet(places = 50.0))
        )
        val payload = ShipmentPayload(
            batchControlEnabled = true,
            batchLimits = listOf(
                BatchLimit(
                    productName = "Fish",
                    batchName = "A",
                    manufacturer = "Plant",
                    packageWeight = 10.0,
                    plannedPlaces = 50.0
                )
            ),
            products = listOf(product)
        )
        assertFalse(ShipmentCalculator.canAddPlaces(payload, product, 1.0))
        assertTrue(ShipmentCalculator.canAddPlaces(payload, product, 0.0))
    }

    @Test
    fun unknownBatchDetectsMismatchOnFullKey() {
        val payload = ShipmentPayload(
            batchControlEnabled = true,
            batchLimits = listOf(
                BatchLimit(
                    productName = "Минтай",
                    batchName = "А",
                    manufacturer = "Колхоз",
                    packageWeight = 24.0,
                    plannedPlaces = 100.0
                )
            )
        )
        val known = Product(
            name = "Минтай",
            batch = "А",
            manufacturer = "Колхоз",
            packageWeight = 24.0
        )
        val unknown = known.copy(batch = "Г")
        val nameOnly = Product(name = "Минтай")
        val missingTare = known.copy(packageWeight = 0.0)
        val missingManufacturer = known.copy(manufacturer = "")
        assertFalse(ShipmentCalculator.isUnknownBatch(known, payload))
        assertTrue(ShipmentCalculator.isUnknownBatch(unknown, payload))
        assertFalse(ShipmentCalculator.isUnknownBatch(nameOnly, payload))
        assertFalse(ShipmentCalculator.isUnknownBatch(missingTare, payload))
        assertFalse(ShipmentCalculator.isUnknownBatch(missingManufacturer, payload))
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
                Product(quantity = 100, pallets = listOf(Pallet(places = 25.0)))
            )
        )
        assertEquals(0.25f, ShipmentCalculator.progressPercent(payload), 0.001f)
    }

    @Test
    fun progressPercentOverloadExceedsOne() {
        val payload = ShipmentPayload(
            products = listOf(
                Product(quantity = 100, pallets = listOf(Pallet(places = 120.0)))
            )
        )
        assertEquals(1.2f, ShipmentCalculator.progressPercent(payload), 0.001f)
    }
}
