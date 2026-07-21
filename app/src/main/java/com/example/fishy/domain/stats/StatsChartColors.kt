package com.example.fishy.domain.stats

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

object StatsChartColors {

    /** Base accent #45BBBB → hue ≈ 180° */
    private const val BASE_HUE = 180f
    private const val GOLDEN_ANGLE = 137.508f
    private const val SATURATION = 0.55f
    private const val LIGHTNESS = 0.52f

    val otherColor = Color(0xFF7AA8A8)
    val selectedTint = Color(0xFF3AA3A3)

    fun colorForIndex(index: Int): Color {
        val hue = (BASE_HUE + index * GOLDEN_ANGLE) % 360f
        return hslToColor(hue, SATURATION, LIGHTNESS)
    }

    fun assignColorIndices(seriesTotals: Map<String, Double>): Map<String, Int> {
        val ranked = seriesTotals.entries
            .sortedByDescending { it.value }
            .map { it.key }
        return ranked.withIndex().associate { (index, key) -> key to index }
    }

    private fun hslToColor(h: Float, s: Float, l: Float): Color {
        val c = (1f - kotlin.math.abs(2 * l - 1)) * s
        val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
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
