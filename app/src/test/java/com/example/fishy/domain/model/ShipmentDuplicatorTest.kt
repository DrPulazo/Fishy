package com.example.fishy.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShipmentDuplicatorTest {

    @Test
    fun clearsTransportPalletsAndNotesButKeepsPlannedQuantity() {
        val source = ShipmentPayload(
            mode = ShipmentMode.MONO,
            customer = "Acme",
            port = "Vladivostok",
            vessel = "Ship-1",
            transport = Transport(wagonNumber = "12345678", sealNumber = "SEAL"),
            products = listOf(
                Product(
                    name = "Salmon",
                    batch = "B1",
                    packageWeight = 12.5,
                    quantity = 100,
                    pallets = listOf(Pallet(places = 40.0))
                )
            ),
            notes = "Important note",
            accordionExpanded = mapOf("product:1" to true),
            quickPlacesByKey = mapOf("id:1" to "40"),
            checklist = listOf(
                ChecklistTask(title = "Check seal", isCompleted = true, completedAtMillis = 1L)
            )
        )

        val copy = ShipmentDuplicator.forNewDraft(source)

        assertEquals("Acme", copy.customer)
        assertEquals("Vladivostok", copy.port)
        assertEquals("Ship-1", copy.vessel)
        assertEquals("", copy.transport.wagonNumber)
        assertEquals("", copy.notes)
        assertEquals(100, copy.products.single().quantity)
        assertEquals(12.5, copy.products.single().packageWeight, 0.0)
        assertTrue(copy.products.single().pallets.isEmpty())
        assertEquals("Salmon", copy.products.single().name)
        assertEquals("Check seal", copy.checklist.single().title)
        assertFalse(copy.checklist.single().isCompleted)
        assertEquals(null, copy.checklist.single().completedAtMillis)
        assertEquals(null, copy.completedAtMillis)
        assertEquals(emptyMap<String, Boolean>(), copy.accordionExpanded)
        assertEquals(emptyMap<String, String>(), copy.quickPlacesByKey)
    }

    @Test
    fun clearsMultiVehicleTransportKeepsQuantity() {
        val source = ShipmentPayload(
            mode = ShipmentMode.MULTI_VEHICLE,
            multiVehicles = listOf(
                VehicleGroup(
                    transport = Transport(containerNumber = "ABCD1234567"),
                    products = listOf(Product(name = "Cod", quantity = 50, pallets = listOf(Pallet(places = 10.0))))
                )
            )
        )

        val copy = ShipmentDuplicator.forNewDraft(source)

        assertEquals("", copy.multiVehicles.single().transport.containerNumber)
        assertEquals(50, copy.multiVehicles.single().products.single().quantity)
        assertTrue(copy.multiVehicles.single().products.single().pallets.isEmpty())
    }
}
