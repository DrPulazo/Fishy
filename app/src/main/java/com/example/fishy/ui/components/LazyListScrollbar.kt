package com.example.fishy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateMap
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
    val knownSizes = rememberLazyItemSizeCache(listState)

    val metrics by remember(listState, minThumbPx, knownSizes) {
        derivedStateOf { lazyListScrollbarMetrics(listState, knownSizes, minThumbPx) }
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

/**
 * Thin non-interactive vertical scrollbar for a [LazyListState].
 * Hidden when content fits in the viewport.
 */
@Composable
fun LazyListScrollIndicator(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    width: Dp = 3.dp,
    minThumbHeight: Dp = 16.dp,
    thumbColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
    trackColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
) {
    val density = LocalDensity.current
    val minThumbPx = with(density) { minThumbHeight.toPx() }
    val knownSizes = rememberLazyItemSizeCache(listState)

    val metrics by remember(listState, minThumbPx, knownSizes) {
        derivedStateOf { lazyListScrollbarMetrics(listState, knownSizes, minThumbPx) }
    }

    val m = metrics ?: return

    BoxWithConstraints(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
    ) {
        val trackPx = constraints.maxHeight.toFloat()
        if (trackPx <= 0f) return@BoxWithConstraints

        val effectiveMin = minOf(minThumbPx, trackPx)
        val thumbPx = m.thumbHeightPx.coerceIn(effectiveMin, trackPx)
        val travel = (trackPx - thumbPx).coerceAtLeast(0f)
        val thumbOffsetPx = m.fraction * travel

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

/** Clearance reserved under list content so a bottom-end FAB does not cover actions. */
val FabContentClearance = 88.dp

/** Extra end inset so Scaffold FAB clears the thin list scroll indicator. */
val FabEndInsetForScrollbar = 12.dp

@Composable
private fun rememberLazyItemSizeCache(listState: LazyListState): SnapshotStateMap<Int, Int> {
    val knownSizes = remember { mutableStateMapOf<Int, Int>() }
    val info = listState.layoutInfo
    SideEffect {
        val total = info.totalItemsCount
        info.visibleItemsInfo.forEach { knownSizes[it.index] = it.size }
        knownSizes.keys.filter { it >= total }.forEach { knownSizes.remove(it) }
    }
    return knownSizes
}

private fun lazyListScrollbarMetrics(
    listState: LazyListState,
    knownSizes: Map<Int, Int>,
    minThumbPx: Float
): ScrollbarMetrics? {
    val info = listState.layoutInfo
    val visible = info.visibleItemsInfo
    val total = info.totalItemsCount
    val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
    if (total == 0 || visible.isEmpty() || viewport <= 0f) return null

    val spacing = info.mainAxisItemSpacing.toFloat()
    val avgKnown = when {
        knownSizes.isNotEmpty() -> knownSizes.values.average().toFloat()
        else -> visible.map { it.size }.average().toFloat()
    }
    if (avgKnown <= 0f) return null

    fun sizeAt(index: Int): Float = (knownSizes[index] ?: avgKnown.toInt()).toFloat()

    var contentHeight = 0f
    for (i in 0 until total) {
        contentHeight += sizeAt(i)
        if (i < total - 1) contentHeight += spacing
    }
    if (contentHeight <= viewport) return null

    var scrolled = 0f
    val firstIndex = listState.firstVisibleItemIndex
    for (i in 0 until firstIndex) {
        scrolled += sizeAt(i) + spacing
    }
    scrolled += listState.firstVisibleItemScrollOffset.toFloat()

    val maxScroll = (contentHeight - viewport).coerceAtLeast(1f)
    val fraction = (scrolled / maxScroll).coerceIn(0f, 1f)
    val effectiveMin = minOf(minThumbPx, viewport)
    val thumbH = (viewport * (viewport / contentHeight)).coerceIn(effectiveMin, viewport)
    val travel = (viewport - thumbH).coerceAtLeast(0f)
    return ScrollbarMetrics(
        fraction = fraction,
        thumbHeightPx = thumbH,
        travelPx = travel,
        maxScrollPx = maxScroll,
        totalItems = total,
        stepPx = avgKnown + spacing
    )
}

private data class ScrollbarMetrics(
    val fraction: Float,
    val thumbHeightPx: Float,
    val travelPx: Float,
    val maxScrollPx: Float,
    val totalItems: Int,
    val stepPx: Float
)
