package com.example.fishy.domain.stats

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

object StatsChartColors {

    /** Base accent #45BBBB → hue ≈ 180° */
    private const val BASE_HUE = 180f
    private const val ACCENT_EXCLUSION = 18f
    /** Minimum angular gap between series hues (reduces blending). */
    private const val MIN_HUE_GAP = 30f

    val otherColor = Color(0xFF7AA8A8)
    val selectedTint = Color(0xFF3AA3A3)

    /**
     * Stable, well-spaced hues for [keys] (excludes Other / total).
     * Same key set → same map; order by key hash so names don't cluster.
     */
    fun resolveHueMap(keys: Collection<String>): Map<String, Float> {
        val ordered = keys
            .asSequence()
            .filter { it != StatisticsBreakdown.OTHER_KEY && it != "total" }
            .distinct()
            .sortedBy { it.hashCode() }
            .toList()
        if (ordered.isEmpty()) return emptyMap()
        val n = ordered.size
        val step = max(MIN_HUE_GAP, 360f / n)
        val startSeed = (ordered.first().hashCode().toLong() and 0xffffffffL) % 360L
        var cursor = startSeed.toFloat()
        val result = LinkedHashMap<String, Float>(n)
        ordered.forEach { key ->
            cursor = nudgeAwayFromAccent(cursor)
            // If still too close to an already placed hue, keep stepping.
            var guard = 0
            while (guard < 24 && result.values.any { angularDistance(it, cursor) < MIN_HUE_GAP * 0.85f }) {
                cursor = nudgeAwayFromAccent((cursor + step * 0.5f) % 360f)
                guard++
            }
            result[key] = cursor
            cursor = (cursor + step) % 360f
        }
        return result
    }

    /**
     * [hueMap] from [resolveHueMap] keeps neighbours visually apart.
     * Saturation/lightness still follow legend rank (tonnage order).
     */
    fun colorFor(key: String, legendIndex: Int, hueMap: Map<String, Float> = emptyMap()): Color {
        val (s, l) = saturationLightnessForRank(legendIndex)
        val hue = hueMap[key] ?: stableHue(key)
        return hslToColor(hue, s, l)
    }

    /** Fallback when only legend rank is known (no series key). */
    fun colorForIndex(index: Int): Color {
        val (s, l) = saturationLightnessForRank(index)
        val hue = nudgeAwayFromAccent((BASE_HUE + 30f + index * max(MIN_HUE_GAP, 137.508f / 2f)) % 360f)
        return hslToColor(hue, s, l)
    }

    fun assignColorIndices(seriesTotals: Map<String, Double>): Map<String, Int> {
        val ranked = seriesTotals.entries
            .sortedByDescending { it.value }
            .map { it.key }
        return ranked.withIndex().associate { (index, key) -> key to index }
    }

    private fun saturationLightnessForRank(index: Int): Pair<Float, Float> {
        val tier = when {
            index <= 2 -> 0.58f to 0.44f
            index <= 8 -> 0.50f to 0.50f
            else -> 0.42f to 0.58f
        }
        // Slight L jitter within tier so same-S neighbours still differ.
        val lJitter = ((index % 3) - 1) * 0.035f
        return tier.first to (tier.second + lJitter).coerceIn(0.32f, 0.68f)
    }

    private fun stableHue(key: String): Float {
        val raw = ((key.hashCode().toLong() and 0xffffffffL) % 360L).toFloat()
        return nudgeAwayFromAccent(raw)
    }

    private fun nudgeAwayFromAccent(hue: Float): Float {
        var h = ((hue % 360f) + 360f) % 360f
        val delta = angularDistance(h, BASE_HUE)
        if (delta >= ACCENT_EXCLUSION) return h
        h = (BASE_HUE + ACCENT_EXCLUSION + 8f) % 360f
        return h
    }

    private fun angularDistance(a: Float, b: Float): Float {
        val d = abs(a - b) % 360f
        return minOf(d, 360f - d)
    }

    private fun hslToColor(h: Float, s: Float, l: Float): Color {
        val c = (1f - abs(2 * l - 1)) * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f
        val (r1, g1, b1) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Color(
            red = (r1 + m).coerceIn(0f, 1f),
            green = (g1 + m).coerceIn(0f, 1f),
            blue = (b1 + m).coerceIn(0f, 1f)
        )
    }

    fun composeColor(index: Int): Color = colorForIndex(index)

    fun argbHex(index: Int): Int {
        val c = colorForIndex(index)
        val a = (c.alpha * 255).roundToInt()
        val r = (c.red * 255).roundToInt()
        val g = (c.green * 255).roundToInt()
        val b = (c.blue * 255).roundToInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
