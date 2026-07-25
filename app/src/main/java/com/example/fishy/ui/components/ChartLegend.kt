package com.example.fishy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.fishy.domain.stats.StatLegendItem
import com.example.fishy.domain.stats.StatisticsBreakdown
import com.example.fishy.domain.stats.StatsChartColors
import java.util.Locale

private const val LegendColumns = 3

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChartLegend(
    items: List<StatLegendItem>,
    modifier: Modifier = Modifier,
    showPercent: Boolean = true,
    totalKg: Double = items.sumOf { it.totalKg }
) {
    if (items.isEmpty()) return
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val gap = 12.dp
        val cellWidth = (maxWidth - gap * (LegendColumns - 1)) / LegendColumns
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            maxItemsInEachRow = LegendColumns
        ) {
            items.forEach { item ->
                val color = if (item.key == StatisticsBreakdown.OTHER_KEY) {
                    StatsChartColors.otherColor
                } else {
                    StatsChartColors.colorForIndex(item.colorIndex)
                }
                Row(
                    modifier = Modifier.width(cellWidth),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(color)
                    )
                    val pct = if (showPercent && totalKg > 0.0) {
                        val p = item.totalKg / totalKg * 100.0
                        " (${String.format(Locale.getDefault(), "%.0f", p)}%)"
                    } else ""
                    Text(
                        text = item.label + pct,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
