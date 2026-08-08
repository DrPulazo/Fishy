package com.example.fishy.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShipmentSummariesFirstPlusRestTest {

    @Test
    fun fourPorts_declension() {
        val names = listOf("Находка", "Владивосток", "Корсаков", "Ванино")
        assertEquals("Находка + 3 порта", ShipmentSummaries.firstPlusRestRu(names))
    }

    @Test
    fun twoPorts_oneRest() {
        assertEquals("A + 1 порт", ShipmentSummaries.firstPlusRestRu(listOf("A", "B")))
    }

    @Test
    fun onePort_noPlus() {
        assertEquals("A", ShipmentSummaries.firstPlusRestRu(listOf("A")))
    }

    @Test
    fun empty_null() {
        assertNull(ShipmentSummaries.firstPlusRestRu(emptyList()))
        assertNull(ShipmentSummaries.firstPlusRestRu(listOf("  ", "")))
    }

    @Test
    fun skipsBlanks_preservesOrder() {
        assertEquals(
            "Находка + 2 порта",
            ShipmentSummaries.firstPlusRestRu(listOf("", "Находка", "  ", "B", "C"))
        )
    }

    @Test
    fun fiveRest_manyForm() {
        val names = listOf("P1", "P2", "P3", "P4", "P5", "P6")
        assertEquals("P1 + 5 портов", ShipmentSummaries.firstPlusRestRu(names))
    }

    @Test
    fun twoPorts_oneRest_countOnly() {
        assertEquals("A + 1", ShipmentSummaries.firstPlusRestCountOnlyRu(listOf("A", "B")))
    }

    @Test
    fun threePorts_twoRest_countOnly_skipsBlanks() {
        assertEquals(
            "Находка + 2",
            ShipmentSummaries.firstPlusRestCountOnlyRu(listOf("", "Находка", " ", "B", "C"))
        )
    }

    @Test
    fun empty_null_countOnly() {
        assertNull(ShipmentSummaries.firstPlusRestCountOnlyRu(emptyList()))
        assertNull(ShipmentSummaries.firstPlusRestCountOnlyRu(listOf(" ", "")))
    }
}
