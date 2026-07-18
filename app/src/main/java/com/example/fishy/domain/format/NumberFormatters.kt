package com.example.fishy.domain.format

/**
 * Display formatting for transport numbers (4.18).
 * DB / internal storage always keeps values without spaces.
 */
object NumberFormatters {

    fun stripSpaces(value: String): String = value.replace(" ", "")

    fun formatContainerForDisplay(raw: String, enabled: Boolean): String {
        val clean = stripSpaces(raw).uppercase()
        if (!enabled || clean.length <= 4) return clean
        return clean.substring(0, 4) + " " + clean.substring(4)
    }

    /**
     * Russian truck plate A000AA00 -> "A 000 AA / 00".
     * If pattern does not match, returns unchanged (no spaces forced).
     */
    fun formatVehicleForDisplay(raw: String, enabled: Boolean): String {
        val clean = stripSpaces(raw).uppercase()
        if (!enabled) return clean
        val plate = Regex("^([А-ЯA-Z])(\\d{3})([А-ЯA-Z]{2})(\\d{2,3})$")
        val match = plate.matchEntire(clean) ?: return clean
        val (letter, digits, letters, region) = match.destructured
        return "$letter $digits $letters / $region"
    }

    /**
     * Russian trailer plate AA000000 -> "AA 0000 / 00" (region 2–3 digits).
     * If pattern does not match, returns unchanged.
     */
    fun formatTrailerForDisplay(raw: String, enabled: Boolean): String {
        val clean = stripSpaces(raw).uppercase()
        if (!enabled) return clean
        val plate = Regex("^([А-ЯA-Z]{2})(\\d{4})(\\d{2,3})$")
        val match = plate.matchEntire(clean) ?: return clean
        val (letters, digits, region) = match.destructured
        return "$letters $digits / $region"
    }
}
