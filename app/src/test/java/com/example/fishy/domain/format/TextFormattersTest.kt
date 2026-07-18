package com.example.fishy.domain.format

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class TextFormattersTest {

    @Test
    fun capitalizesEachLine() {
        val input = "первая строка\nвторая строка\n\nтретья"
        val out = TextFormatters.capitalizeLines(input, Locale("ru"))
        assertEquals("Первая строка\nВторая строка\n\nТретья", out)
    }

    @Test
    fun preservesTrailingNewline() {
        val out = TextFormatters.capitalizeLines("раз\n", Locale("ru"))
        assertEquals("Раз\n", out)
    }

    @Test
    fun leavesAlreadyCapitalized() {
        assertEquals("Уже так", TextFormatters.capitalizeLines("Уже так", Locale("ru")))
    }
}
