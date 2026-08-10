package com.example.fishy.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.fishy.domain.stats.StackedStatBarEntry
import com.example.fishy.domain.stats.StatisticsBreakdown
import com.example.fishy.domain.stats.StatsChartColors
import com.example.fishy.ui.theme.FishyAccent
import com.example.fishy.ui.theme.isLightTheme

@Composable
fun StackedVerticalBarChart(
    title: String,
    entries: List<StackedStatBarEntry>,
    modifier: Modifier = Modifier,
    barWidthDp: Int = 48,
    chartHeightDp: Int = 160,
    fillHeight: Boolean = false,
    selectedIndex: Int? = null,
    onBarClick: ((StackedStatBarEntry, Int) -> Unit)? = null
) {
    if (entries.isEmpty()) return

    val maxValue = remember(entries) {
        entries.maxOfOrNull { it.totalTonnes }?.takeIf { it > 0.0 } ?: 1.0
    }
    val hueMap = remember(entries) {
        StatsChartColors.resolveHueMap(entries.flatMap { bar -> bar.segments.map { it.key } })
    }
    val scrollState = rememberScrollState()
    val selectionStroke = if (isLightTheme()) Color.Black else Color.White
    val segmentDivider = if (isLightTheme()) {
        Color.White.copy(alpha = 0.85f)
    } else {
        Color.Black.copy(alpha = 0.55f)
    }

    LaunchedEffect(entries.size, scrollState.maxValue) {
        if (scrollState.maxValue > 0) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    Column(
        modifier = modifier.then(
            if (fillHeight) Modifier.fillMaxHeight() else Modifier
        )
    ) {
        if (title.isNotBlank()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (fillHeight) Modifier.weight(1f) else Modifier)
                .horizontalScroll(scrollState)
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            entries.forEachIndexed { index, entry ->
                val selected = selectedIndex == index
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(barWidthDp.dp)
                        .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier)
                        .then(
                            if (onBarClick != null) {
                                Modifier.clickable { onBarClick(entry, index) }
                            } else {
                                Modifier
                            }
                        )
                ) {
                    Text(
                        text = "%.1f".format(entry.totalTonnes),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                    Box(
                        modifier = Modifier
                            .width(barWidthDp.dp)
                            .then(
                                if (fillHeight) {
                                    Modifier.weight(1f)
                                } else {
                                    Modifier.height(chartHeightDp.dp)
                                }
                            )
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Canvas(modifier = Modifier.matchParentSize()) {
                            val barHeightRatio = (entry.totalTonnes / maxValue).toFloat().coerceIn(0f, 1f)
                            val availH = size.height
                            val totalBarHeight = availH * barHeightRatio
                            if (totalBarHeight <= 0.5f) return@Canvas

                            val segments = entry.segments.filter { it.valueKg > 0.0 }
                            if (segments.isEmpty()) return@Canvas

                            // Room for an outside selection stroke (centered on fill edge → fully outside).
                            val strokeWidth = 1.5.dp.toPx()
                            val pad = strokeWidth
                            val barLeft = pad
                            val barRight = (size.width - pad).coerceAtLeast(barLeft + 1f)
                            val barBottom = (size.height - pad).coerceAtLeast(pad + 1f)
                            val barWidth = barRight - barLeft
                            val maxFillH = (barBottom - pad).coerceAtLeast(1f)
                            val fillH = (totalBarHeight * (maxFillH / availH)).coerceIn(1f, maxFillH)
                            val barTop = barBottom - fillH
                            val corner = CornerRadius(6f, 6f)
                            val fillPath = Path().apply {
                                addRoundRect(
                                    RoundRect(
                                        left = barLeft,
                                        top = barTop,
                                        right = barRight,
                                        bottom = barBottom,
                                        cornerRadius = corner
                                    )
                                )
                            }
                            val dividerColor = if (selected) selectionStroke else segmentDivider
                            val dividerWidth = if (selected) 2f else 1f
                            clipPath(fillPath) {
                                var bottomY = barBottom
                                segments.forEachIndexed { index, segment ->
                                    val segRatio = if (entry.totalKg > 0.0) {
                                        (segment.valueKg / entry.totalKg).toFloat()
                                    } else {
                                        0f
                                    }
                                    val segHeight = fillH * segRatio
                                    if (segHeight <= 0f) return@forEachIndexed
                                    val topY = bottomY - segHeight
                                    drawRect(
                                        color = segmentColor(segment, hueMap),
                                        topLeft = Offset(barLeft, topY),
                                        size = Size(barWidth, segHeight)
                                    )
                                    if (index < segments.lastIndex) {
                                        drawLine(
                                            color = dividerColor,
                                            start = Offset(barLeft, topY),
                                            end = Offset(barRight, topY),
                                            strokeWidth = dividerWidth
                                        )
                                    }
                                    bottomY = topY
                                }
                            }
                            if (selected) {
                                val half = strokeWidth / 2f
                                val strokePath = Path().apply {
                                    addRoundRect(
                                        RoundRect(
                                            left = barLeft - half,
                                            top = barTop - half,
                                            right = barRight + half,
                                            bottom = barBottom + half,
                                            cornerRadius = CornerRadius(6f + half, 6f + half)
                                        )
                                    )
                                }
                                drawPath(
                                    path = strokePath,
                                    color = selectionStroke,
                                    style = Stroke(width = strokeWidth)
                                )
                            }
                        }
                    }
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .height(32.dp)
                            .padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

private fun segmentColor(
    segment: com.example.fishy.domain.stats.StatSegment,
    hueMap: Map<String, Float>
): Color =
    when {
        segment.key == StatisticsBreakdown.OTHER_KEY -> StatsChartColors.otherColor
        segment.key == "total" -> FishyAccent
        else -> StatsChartColors.colorFor(segment.key, segment.colorIndex, hueMap)
    }
