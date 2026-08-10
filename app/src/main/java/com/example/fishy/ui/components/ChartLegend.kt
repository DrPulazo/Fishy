package com.example.fishy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.fishy.domain.stats.StatLegendItem
import com.example.fishy.domain.stats.StatisticsBreakdown
import com.example.fishy.domain.stats.StatsChartColors
import java.util.Locale

private const val LegendColumns = 2

@Composable
fun ChartLegend(
    items: List<StatLegendItem>,
    modifier: Modifier = Modifier,
    showPercent: Boolean = true,
    totalKg: Double = items.sumOf { it.totalKg }
) {
    if (items.isEmpty()) return
    val hueMap = remember(items) {
        StatsChartColors.resolveHueMap(items.map { it.key })
    }
    val gap = 12.dp
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.chunked(LegendColumns).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                rowItems.forEach { item ->
                    LegendCell(
                        item = item,
                        hueMap = hueMap,
                        showPercent = showPercent,
                        totalKg = totalKg,
                        modifier = Modifier.weight(1f)
                    )
                }
                // Keep an odd last item in the left column (empty right cell).
                if (rowItems.size < LegendColumns) {
                    repeat(LegendColumns - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendCell(
    item: StatLegendItem,
    hueMap: Map<String, Float>,
    showPercent: Boolean,
    totalKg: Double,
    modifier: Modifier = Modifier
) {
    val color = if (item.key == StatisticsBreakdown.OTHER_KEY) {
        StatsChartColors.otherColor
    } else {
        StatsChartColors.colorFor(item.key, item.colorIndex, hueMap)
    }
    val pct = if (showPercent && totalKg > 0.0) {
        val p = item.totalKg / totalKg * 100.0
        " (${String.format(Locale.getDefault(), "%.0f", p)}%)"
    } else {
        ""
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Text(
            text = item.label + pct,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
