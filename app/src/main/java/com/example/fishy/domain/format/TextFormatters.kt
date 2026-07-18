package com.example.fishy.domain.format

import java.util.Locale

object TextFormatters {

    /**
     * Capitalizes the first letter of each line (including after blank lines).
     * Preserves the rest of the line and existing newlines.
     */
    fun capitalizeLines(text: String, locale: Locale = Locale.getDefault()): String {
        if (text.isEmpty()) return text
        val endsWithNewline = text.endsWith("\n")
        val lines = text.split('\n')
        val converted = lines.joinToString("\n") { line ->
            if (line.isEmpty()) line
            else line.replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase(locale) else ch.toString()
            }
        }
        return if (endsWithNewline && !converted.endsWith("\n")) converted + "\n" else converted
    }
}
