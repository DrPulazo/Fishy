package com.example.fishy.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContainerWagonValidatorTest {

    @Test
    fun validContainerPassesIso6346() {
        // Known pattern: compute check digit for CSQU305438
        val base = "CSQU305438"
        val digit = ContainerWagonValidator.getContainerCheckDigit(base)!!
        val full = base + digit
        assertTrue(ContainerWagonValidator.isValidContainerNumber(full))
        assertTrue(ContainerWagonValidator.isValidContainerNumber("CSQU 305438$digit"))
    }

    @Test
    fun invalidContainerFails() {
        assertFalse(ContainerWagonValidator.isValidContainerNumber("CSQU3054380"))
        assertFalse(ContainerWagonValidator.isValidContainerNumber("ABC"))
    }

    @Test
    fun wagonCheckDigitRoundTrip() {
        val seven = "1234567"
        val digit = ContainerWagonValidator.getWagonCheckDigit(seven)!!
        assertTrue(ContainerWagonValidator.isValidWagonNumber(seven + digit))
        assertEquals(
            ValidationState.Valid,
            ContainerWagonValidator.validateWagonNumberLive(seven + digit)
        )
    }
}
