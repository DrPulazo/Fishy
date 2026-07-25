package com.example.fishy.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShipmentSummariesScheduleCardTest {

    @Test
    fun mixedTransports_usesTransportWord() {
        val payload = ShipmentPayload(
            mode = ShipmentMode.MULTI_VEHICLE,
            port = "Владивосток",
            multiVehicles = listOf(
                VehicleGroup(transport = Transport(containerNumber = "MSKU1234567")),
                VehicleGroup(transport = Transport(wagonNumber = "12345678")),
                VehicleGroup(transport = Transport(truckNumber = "A123BC45"))
            )
        )
        assertEquals("3 транспорта", ShipmentSummaries.scheduleTransportLine(payload))
    }

    @Test
    fun sameContainers_declension() {
        val payload = ShipmentPayload(
            mode = ShipmentMode.MULTI_VEHICLE,
            multiVehicles = listOf(
                VehicleGroup(transport = Transport(containerNumber = "MSKU1234567")),
                VehicleGroup(transport = Transport(containerNumber = "MSKU7654321")),
                VehicleGroup(transport = Transport(containerNumber = "TEMU1111111"))
            )
        )
        assertEquals("3 контейнера", ShipmentSummaries.scheduleTransportLine(payload))
    }

    @Test
    fun sixPorts_showsFivePlusMore() {
        val ports = (1..6).map { PortGroup(port = "Port$it") }
        val payload = ShipmentPayload(mode = ShipmentMode.MULTI_PORT, multiPorts = ports)
        val lines = ShipmentSummaries.scheduleLocationLines(payload)
        assertEquals(6, lines.size)
        assertEquals("Порт 1: Port1", lines[0])
        assertEquals("Порт 5: Port5", lines[4])
        assertEquals("Ещё 1 порт", lines[5])
    }

    @Test
    fun twoProductKinds_line() {
        val payload = ShipmentPayload(
            mode = ShipmentMode.MONO,
            products = listOf(
                Product(name = "Минтай", batch = "A", manufacturer = "X", packageWeight = 10.0, quantity = 1),
                Product(name = "Сельдь", batch = "B", manufacturer = "Y", packageWeight = 20.0, quantity = 1)
            )
        )
        assertEquals("2 вида продукции", ShipmentSummaries.scheduleProductLine(payload))
    }

    @Test
    fun oneProduct_withPrefix() {
        val payload = ShipmentPayload(
            mode = ShipmentMode.MONO,
            products = listOf(
                Product(name = "Минтай", packageWeight = 10.0, quantity = 100)
            )
        )
        assertEquals("Продукция: Минтай", ShipmentSummaries.scheduleProductLine(payload))
    }

    @Test
    fun plannedTonnage_sumOfMasses() {
        val payload = ShipmentPayload(
            mode = ShipmentMode.MONO,
            products = listOf(
                Product(name = "A", packageWeight = 10.0, quantity = 100),
                Product(name = "B", packageWeight = 20.0, quantity = 50)
            )
        )
        assertEquals(2000.0, ShipmentSummaries.schedulePlannedTonnageKg(payload), 0.001)
        val body = ShipmentSummaries.scheduleCardBodyLines(payload, thousandsSeparator = false)
        assertTrue(body.any { it.startsWith("Тоннаж:") && it.contains("2000") })
    }

    @Test
    fun emptyTransportSlots_ignored() {
        val payload = ShipmentPayload(
            mode = ShipmentMode.MULTI_VEHICLE,
            multiVehicles = listOf(
                VehicleGroup(transport = Transport()),
                VehicleGroup(transport = Transport(containerNumber = "MSKU1234567"))
            )
        )
        assertEquals("1 контейнер", ShipmentSummaries.scheduleTransportLine(payload))
    }

    @Test
    fun monoWithoutPortOrProduct_emptyLocationsAndProduct() {
        val payload = ShipmentPayload(mode = ShipmentMode.MONO)
        assertTrue(ShipmentSummaries.scheduleLocationLines(payload).isEmpty())
        assertNull(ShipmentSummaries.scheduleProductLine(payload))
    }
}
