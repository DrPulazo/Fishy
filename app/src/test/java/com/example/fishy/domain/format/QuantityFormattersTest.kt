package com.example.fishy.domain.format

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class QuantityFormattersTest {

    @Test
    fun weightOmitsTrailingZeros() {
        val us = Locale.US
        assertEquals("54000", QuantityFormatters.formatWeight(54000.0, false, us))
        assertEquals("54000.456", QuantityFormatters.formatWeight(54000.456, false, us))
        assertEquals("10.5", QuantityFormatters.formatWeight(10.5, false, us))
        assertEquals("0", QuantityFormatters.formatWeight(0.0, false, us))
    }

    @Test
    fun weightWithThousandsUsesNbsp() {
        val us = Locale.US
        assertEquals("54\u00A0000", QuantityFormatters.formatWeight(54000.0, true, us))
        assertEquals("54\u00A0000.456", QuantityFormatters.formatWeight(54000.456, true, us))
        assertEquals("1\u00A0000", QuantityFormatters.formatInteger(1000, true, us))
    }

    @Test
    fun sanitizeDecimalInputReplacesDotWithComma() {
        assertEquals("12,5", QuantityFormatters.sanitizeDecimalInput("12.5"))
        assertEquals("12,", QuantityFormatters.sanitizeDecimalInput("12."))
        assertEquals("12,34", QuantityFormatters.sanitizeDecimalInput("12.3.4"))
    }

    @Test
    fun parseDecimalInputKeepsTrailingSeparatorAsWhole() {
        assertEquals(0.0, QuantityFormatters.parseDecimalInput("")!!, 0.0)
        assertEquals(null, QuantityFormatters.parseDecimalInput(","))
        assertEquals(12.0, QuantityFormatters.parseDecimalInput("12,")!!, 0.0)
        assertEquals(12.5, QuantityFormatters.parseDecimalInput("12,5")!!, 0.0)
        assertEquals(12.5, QuantityFormatters.parseDecimalInput("12.5")!!, 0.0)
    }
}
