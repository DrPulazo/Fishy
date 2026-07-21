package com.example.fishy.domain.model

import com.example.fishy.domain.calc.ShipmentCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GrossWeightMathTest {

    @Test
    fun coefficientAndGrossTareRoundTrip() {
        val net = 22.0
        val k = 1.05
        val grossTare = GrossWeightMath.grossTare(net, k)
        assertEquals(23.1, grossTare, 0.0001)
        val back = GrossWeightMath.coefficientFromGrossTare(net, 23.1)
        assertEquals(1.05, back!!, 0.0001)
    }

    @Test
    fun totalGrossFromUserExample() {
        // 2510 places × 22 kg net, k=1.05 → 57_981 kg gross
        val product = Product(
            packageWeight = 22.0,
            quantity = 2510,
            grossCoefficient = 1.05
        )
        assertEquals(23.1, product.grossPackageWeight, 0.0001)
        assertEquals(57_981.0, product.totalGrossWeight, 0.001)
        assertEquals(57_981.0, GrossWeightMath.totalGross(22.0, 2510, 1.05), 0.001)
    }

    @Test
    fun coefficientFromGrossTareWhenNetZero() {
        assertNull(GrossWeightMath.coefficientFromGrossTare(0.0, 23.1))
    }

    @Test
    fun totalsIncludeGrossWeights() {
        val product = Product(
            name = "Fish",
            packageWeight = 22.0,
            quantity = 100,
            grossCoefficient = 1.05,
            pallets = listOf(Pallet(places = 80.0))
        )
        val totals = ShipmentCalculator.totalsForProducts(listOf(product), false)
        assertEquals(22.0 * 100, totals.targetWeight, 0.001)
        assertEquals(22.0 * 100 * 1.05, totals.targetGrossWeight, 0.001)
        assertEquals(22.0 * 80, totals.actualWeight, 0.001)
        assertEquals(22.0 * 80 * 1.05, totals.actualGrossWeight, 0.001)
    }
}
