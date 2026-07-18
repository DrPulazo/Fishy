package com.example.fishy.domain.format

import org.junit.Assert.assertEquals
import org.junit.Test

class NumberFormattersTest {

    @Test
    fun containerGetsSpaceAfterOwnerCode() {
        assertEquals("CSQU 3054389", NumberFormatters.formatContainerForDisplay("CSQU3054389", true))
        assertEquals("CSQU3054389", NumberFormatters.formatContainerForDisplay("CSQU3054389", false))
    }

    @Test
    fun trailerPlateFormatting() {
        assertEquals("AB 1234 / 77", NumberFormatters.formatTrailerForDisplay("AB123477", true))
        assertEquals("NOTAPLATE", NumberFormatters.formatTrailerForDisplay("NOTAPLATE", true))
    }

    @Test
    fun stripSpaces() {
        assertEquals("CSQU3054389", NumberFormatters.stripSpaces("CSQU 3054389"))
    }
}
