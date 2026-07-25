package com.example.fishy.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Thin non-interactive vertical scrollbar for a [ScrollState].
 * Hidden when content fits in the viewport.
 */
@Composable
fun ColumnScrollIndicator(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    width: Dp = 3.dp,
    minThumbHeight: Dp = 16.dp,
    thumbColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
    trackColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
) {
    val maxScroll = scrollState.maxValue
    if (maxScroll <= 0) return

    val density = LocalDensity.current
    val minThumbPx = with(density) { minThumbHeight.toPx() }

    BoxWithConstraints(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
    ) {
        val trackPx = constraints.maxHeight.toFloat()
        if (trackPx <= 0f) return@BoxWithConstraints

        val thumbPx = (trackPx * trackPx / (trackPx + maxScroll))
            .coerceIn(minThumbPx, trackPx)
        val travel = (trackPx - thumbPx).coerceAtLeast(0f)
        val fraction = (scrollState.value.toFloat() / maxScroll).coerceIn(0f, 1f)
        val thumbOffsetPx = fraction * travel

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(trackColor, RoundedCornerShape(width / 2))
        )
        Box(
            modifier = Modifier
                .offset(y = with(density) { thumbOffsetPx.roundToInt().toDp() })
                .fillMaxWidth()
                .height(with(density) { thumbPx.toDp() })
                .background(thumbColor, RoundedCornerShape(width / 2))
        )
    }
}
