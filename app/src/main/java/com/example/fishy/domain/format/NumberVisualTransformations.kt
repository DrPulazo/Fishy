package com.example.fishy.domain.format

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

private const val THOUSANDS_GROUP_SEP = '\u00A0'

/**
 * Groups the integer part of a decimal (comma) with NBSP every 3 digits.
 * Raw value stays space-free (e.g. "12345,67" → "12 345,67").
 */
class ThousandsGroupingVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        thousandsGroupingTransform(text.text)
}

internal fun thousandsGroupingTransform(raw: String): TransformedText {
    if (raw.isEmpty()) {
        return TransformedText(AnnotatedString(""), OffsetMapping.Identity)
    }

    val commaIndex = raw.indexOf(',')
    val intPart = if (commaIndex >= 0) raw.substring(0, commaIndex) else raw
    val fracPart = if (commaIndex >= 0) raw.substring(commaIndex) else ""
    val intLen = intPart.length

    val formattedInt = buildString(intLen + intLen / 3) {
        intPart.forEachIndexed { index, c ->
            if (index > 0 && (intLen - index) % 3 == 0) {
                append(THOUSANDS_GROUP_SEP)
            }
            append(c)
        }
    }

    val formatted = formattedInt + fracPart
    if (formatted == raw) {
        return TransformedText(AnnotatedString(raw), OffsetMapping.Identity)
    }

    val origToTrans = IntArray(raw.length + 1)
    var tPos = 0
    for (o in 0..raw.length) {
        origToTrans[o] = tPos
        if (o < raw.length) {
            if (o < intLen && o > 0 && (intLen - o) % 3 == 0) {
                tPos += 1
            }
            tPos += 1
        }
    }

    val transToOrig = IntArray(formatted.length + 1)
    var oPos = 0
    for (t in 0..formatted.length) {
        transToOrig[t] = oPos.coerceAtMost(raw.length)
        if (t < formatted.length && formatted[t] != THOUSANDS_GROUP_SEP) {
            oPos++
        }
    }

    return TransformedText(
        AnnotatedString(formatted),
        object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                origToTrans[offset.coerceIn(0, raw.length)]

            override fun transformedToOriginal(offset: Int): Int =
                transToOrig[offset.coerceIn(0, formatted.length)]
        }
    )
}

/** Inserts a space after the 4th container character; raw value stays space-free. */
class ContainerSpaceVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.length <= 4) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val formatted = raw.substring(0, 4) + " " + raw.substring(4)
        return TransformedText(
            AnnotatedString(formatted),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int {
                    val o = offset.coerceIn(0, raw.length)
                    return if (o <= 4) o else o + 1
                }

                override fun transformedToOriginal(offset: Int): Int {
                    val o = offset.coerceIn(0, formatted.length)
                    return if (o <= 4) o else (o - 1).coerceAtMost(raw.length)
                }
            }
        )
    }
}

/**
 * Russian truck plate: A000AA00 → "A 000 AA / 00" (region 2–3 digits).
 * Progressive formatting while typing; raw value stays space-free.
 */
class VehicleSpaceVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        progressivePlateTransform(
            raw = text.text,
            spaceBeforeIndices = setOf(1, 4),
            slashBeforeIndex = 6
        )
}

/**
 * Russian trailer plate: AA000000 → "AA 0000 / 00" (region 2–3 digits).
 * Uses the same settings toggle as vehicle auto-spacing.
 */
class TrailerSpaceVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        progressivePlateTransform(
            raw = text.text,
            spaceBeforeIndices = setOf(2),
            slashBeforeIndex = 6
        )
}

private fun progressivePlateTransform(
    raw: String,
    spaceBeforeIndices: Set<Int>,
    slashBeforeIndex: Int
): TransformedText {
    if (raw.isEmpty()) {
        return TransformedText(AnnotatedString(""), OffsetMapping.Identity)
    }

    val formatted = buildString {
        raw.forEachIndexed { index, c ->
            when {
                index in spaceBeforeIndices -> append(' ')
                index == slashBeforeIndex -> append(" / ")
            }
            append(c)
        }
    }

    val origToTrans = IntArray(raw.length + 1)
    var tPos = 0
    for (o in 0..raw.length) {
        origToTrans[o] = tPos
        if (o < raw.length) {
            when {
                o in spaceBeforeIndices -> tPos += 1
                o == slashBeforeIndex -> tPos += 3
            }
            tPos += 1
        }
    }

    val transToOrig = IntArray(formatted.length + 1)
    var oPos = 0
    for (t in 0..formatted.length) {
        transToOrig[t] = oPos.coerceAtMost(raw.length)
        if (t < formatted.length && formatted[t].isLetterOrDigit()) {
            oPos++
        }
    }

    return TransformedText(
        AnnotatedString(formatted),
        object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                origToTrans[offset.coerceIn(0, raw.length)]

            override fun transformedToOriginal(offset: Int): Int =
                transToOrig[offset.coerceIn(0, formatted.length)]
        }
    )
}
