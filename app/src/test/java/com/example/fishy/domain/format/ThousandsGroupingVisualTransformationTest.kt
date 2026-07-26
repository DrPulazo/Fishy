package com.example.fishy.domain.format

import org.junit.Assert.assertEquals
import org.junit.Test

class ThousandsGroupingVisualTransformationTest {

    @Test
    fun formatsIntegerGroupsWithNbsp() {
        assertEquals("1\u00A0000", transformText("1000"))
        assertEquals("12\u00A0345", transformText("12345"))
        assertEquals("123", transformText("123"))
        assertEquals("1\u00A0000\u00A0000", transformText("1000000"))
    }

    @Test
    fun formatsWithDecimalComma() {
        assertEquals("1\u00A0234,5", transformText("1234,5"))
        assertEquals("12\u00A0345,67", transformText("12345,67"))
        assertEquals("12,", transformText("12,"))
    }

    @Test
    fun offsetMappingEndOfInteger() {
        val t = thousandsGroupingTransform("1000")
        val map = t.offsetMapping
        assertEquals("1\u00A0000", t.text.text)
        assertEquals(0, map.originalToTransformed(0))
        // After '1' (offset 1): space is inserted before next digit → transformed index 1 is the NBSP
        assertEquals(1, map.originalToTransformed(1))
        assertEquals(5, map.originalToTransformed(4))
        assertEquals(4, map.transformedToOriginal(5))
        // Cursor on NBSP maps back to after '1'
        assertEquals(1, map.transformedToOriginal(1))
        assertEquals(1, map.transformedToOriginal(2))
    }

    @Test
    fun offsetMappingAroundComma() {
        val t = thousandsGroupingTransform("1234,5")
        val map = t.offsetMapping
        assertEquals("1\u00A0234,5", t.text.text)
        assertEquals(5, map.originalToTransformed(4)) // before comma
        assertEquals(6, map.originalToTransformed(5)) // after comma
        assertEquals(7, map.originalToTransformed(6))
        assertEquals(4, map.transformedToOriginal(5))
        assertEquals(5, map.transformedToOriginal(6))
    }

    private fun transformText(raw: String): String = thousandsGroupingTransform(raw).text.text
}
