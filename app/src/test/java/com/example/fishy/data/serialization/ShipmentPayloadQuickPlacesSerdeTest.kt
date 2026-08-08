package com.example.fishy.data.serialization

import com.example.fishy.domain.model.ShipmentMode
import com.example.fishy.domain.model.ShipmentPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShipmentPayloadQuickPlacesSerdeTest {

    @Test
    fun quickPlacesByKey_roundTripsThroughFishyJson() {
        val payload = ShipmentPayload(
            mode = ShipmentMode.MONO,
            quickPlacesByKey = mapOf(
                "id:1" to "40",
                "Salmon|B1|Plant|12.5" to "25,5"
            )
        )

        val encoded = FishyJson.encodePayload(payload)
        val decoded = FishyJson.decodePayload(encoded)

        assertEquals(payload.quickPlacesByKey, decoded.quickPlacesByKey)
        assertTrue(encoded.contains("quickPlacesByKey"))
    }

    @Test
    fun quickPlacesByKey_defaultsToEmptyWhenMissingInLegacyJson() {
        val legacy = """{"mode":"MONO","customer":"Acme"}"""
        val decoded = FishyJson.decodePayload(legacy)
        assertEquals(emptyMap<String, String>(), decoded.quickPlacesByKey)
    }
}
