package com.example.fishy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.fishy.ui.theme.FishyAccent
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Draggable vertical scrollbar for a [LazyListState].
 * Hidden when content fits in the viewport.
 */
@Composable
fun LazyListScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    width: Dp = 16.dp,
    minThumbHeight: Dp = 40.dp,
    thumbColor: Color = FishyAccent,
    trackColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val minThumbPx = with(density) { minThumbHeight.toPx() }

    val metrics by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val visible = info.visibleItemsInfo
            val total = info.totalItemsCount
            val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
            if (total == 0 || visible.isEmpty() || viewport <= 0f) {
                null
            } else {
                val avgSize = visible.map { it.size }.average().toFloat()
                val spacing = info.mainAxisItemSpacing.toFloat()
                val step = avgSize + spacing
                val contentHeight = avgSize * total + spacing * (total - 1).coerceAtLeast(0)
                if (contentHeight <= viewport) {
                    null
                } else {
                    val scrolled =
                        listState.firstVisibleItemIndex * step +
                            listState.firstVisibleItemScrollOffset
                    val maxScroll = (contentHeight - viewport).coerceAtLeast(1f)
                    val fraction = (scrolled / maxScroll).coerceIn(0f, 1f)
                    val thumbH = (viewport * (viewport / contentHeight)).coerceIn(minThumbPx, viewport)
                    val travel = (viewport - thumbH).coerceAtLeast(0f)
                    ScrollbarMetrics(
                        fraction = fraction,
                        thumbHeightPx = thumbH,
                        travelPx = travel,
                        maxScrollPx = maxScroll,
                        totalItems = total,
                        stepPx = step
                    )
                }
            }
        }
    }

    val m = metrics ?: return

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(width)
    ) {
        val trackHeightPx = with(density) { maxHeight.toPx() }
        val travel = if (m.travelPx > 0f) {
            m.travelPx
        } else {
            (trackHeightPx - m.thumbHeightPx).coerceAtLeast(0f)
        }
        val thumbOffsetY = m.fraction * travel
        val thumbHeightDp = with(density) { m.thumbHeightPx.toDp() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(trackColor)
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, thumbOffsetY.roundToInt()) }
                .width(width)
                .height(thumbHeightDp)
                .background(thumbColor)
                .pointerInput(m.maxScrollPx, travel, m.totalItems, m.stepPx) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (travel <= 0f) return@detectVerticalDragGestures
                        val deltaFraction = dragAmount / travel
                        val scrollDelta = deltaFraction * m.maxScrollPx
                        scope.launch {
                            listState.scroll {
                                scrollBy(scrollDelta)
                            }
                        }
                    }
                }
        )
    }
}

private data class ScrollbarMetrics(
    val fraction: Float,
    val thumbHeightPx: Float,
    val travelPx: Float,
    val maxScrollPx: Float,
    val totalItems: Int,
    val stepPx: Float
)
