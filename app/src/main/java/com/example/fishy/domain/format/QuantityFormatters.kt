package com.example.fishy.domain.format

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Display formatting for places counts and weights/tare/mass.
 * Trailing fractional zeros are omitted; optional thin grouping spaces for thousands.
 */
object QuantityFormatters {

    private const val GROUP_SEP = '\u00A0' // non-breaking space between thousands
    private const val EPS = 1e-9

    fun formatInteger(value: Int, thousandsSeparator: Boolean = false, locale: Locale = Locale.getDefault()): String {
        if (!thousandsSeparator) return value.toString()
        val symbols = DecimalFormatSymbols(locale).apply {
            groupingSeparator = GROUP_SEP
        }
        return DecimalFormat("#,##0", symbols).apply { isGroupingUsed = true }.format(value)
    }

    /** Whole numbers via [formatInteger]; fractions via [formatWeight] (comma decimal). */
    fun formatCount(value: Double, thousandsSeparator: Boolean = false, locale: Locale = Locale.getDefault()): String {
        val asLong = value.roundToLong()
        return if (abs(value - asLong) < EPS) {
            formatInteger(asLong.toInt(), thousandsSeparator, locale)
        } else {
            formatWeight(value, thousandsSeparator, locale)
        }
    }

    /**
     * Compact weight: 54000, 54000.456 / 54000,456 (locale decimal).
     * With thousands: 54 000, 54 000,456.
     */
    fun formatWeight(value: Double, thousandsSeparator: Boolean = false, locale: Locale = Locale.getDefault()): String {
        if (value == 0.0) return "0"
        val symbols = DecimalFormatSymbols(locale).apply {
            if (thousandsSeparator) groupingSeparator = GROUP_SEP
        }
        val pattern = if (thousandsSeparator) "#,##0.###" else "0.###"
        return DecimalFormat(pattern, symbols).apply {
            isGroupingUsed = thousandsSeparator
        }.format(value)
    }

    /** Text for weight/tare fields: empty when zero. */
    fun formatWeightInput(
        value: Double,
        thousandsSeparator: Boolean = false,
        locale: Locale = Locale.getDefault()
    ): String =
        if (value == 0.0) "" else formatWeight(value, thousandsSeparator, locale)

    /**
     * Keep digits and a single decimal separator; always use comma (`.` → `,`).
     * Strips ordinary and non-breaking spaces (thousands grouping).
     */
    fun sanitizeDecimalInput(raw: String): String {
        val withComma = raw
            .replace(" ", "")
            .replace("\u00A0", "")
            .replace('.', ',')
        return buildString {
            var seenSep = false
            for (c in withComma) {
                when {
                    c.isDigit() -> append(c)
                    c == ',' && !seenSep -> {
                        append(',')
                        seenSep = true
                    }
                }
            }
        }
    }

    /**
     * Parse sanitized decimal text. Empty → 0.0.
     * Incomplete values like `,` alone → null (do not wipe the field).
     * `"12,"` → 12.0 so the model can update while the UI keeps the trailing comma.
     */
    fun parseDecimalInput(raw: String): Double? {
        val t = raw.trim()
        if (t.isEmpty()) return 0.0
        if (t == ",") return null
        val normalized = t.replace(',', '.')
        val toParse = if (normalized.endsWith('.')) normalized.dropLast(1) else normalized
        if (toParse.isEmpty()) return null
        return toParse.toDoubleOrNull()
    }
}
