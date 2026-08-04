package com.example.fishy.data.serialization

import com.example.fishy.domain.model.ShipmentMode
import com.example.fishy.domain.model.ShipmentPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShipmentPayloadAccordionSerdeTest {

    @Test
    fun accordionExpanded_roundTripsThroughFishyJson() {
        val payload = ShipmentPayload(
            mode = ShipmentMode.MULTI_PORT,
            accordionExpanded = mapOf(
                "port:1" to true,
                "port:2" to false,
                "port:1/product:10" to true
            )
        )

        val encoded = FishyJson.encodePayload(payload)
        val decoded = FishyJson.decodePayload(encoded)

        assertEquals(payload.accordionExpanded, decoded.accordionExpanded)
        assertTrue(encoded.contains("accordionExpanded"))
    }

    @Test
    fun accordionExpanded_defaultsToEmptyWhenMissingInLegacyJson() {
        val legacy = """{"mode":"MONO","customer":"Acme"}"""
        val decoded = FishyJson.decodePayload(legacy)
        assertEquals(emptyMap<String, Boolean>(), decoded.accordionExpanded)
    }
}
