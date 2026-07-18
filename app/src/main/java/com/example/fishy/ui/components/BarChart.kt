package com.example.fishy.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.fishy.domain.stats.StatBarEntry

@Composable
fun VerticalBarChart(
    title: String,
    entries: List<StatBarEntry>,
    modifier: Modifier = Modifier,
    barWidthDp: Int = 48,
    chartHeightDp: Int = 160,
    selectedIndex: Int? = null,
    onBarClick: ((StatBarEntry, Int) -> Unit)? = null
) {
    if (entries.isEmpty()) return

    val maxValue = remember(entries) {
        entries.maxOfOrNull { it.valueTonnes }?.takeIf { it > 0.0 } ?: 1.0
    }
    val barColor = MaterialTheme.colorScheme.primary
    val selectedBarColor = MaterialTheme.colorScheme.tertiary

    Column(modifier = modifier.fillMaxWidth()) {
        if (title.isNotBlank()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            entries.forEachIndexed { index, entry ->
                val selected = selectedIndex == index
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(barWidthDp.dp)
                        .then(
                            if (onBarClick != null) {
                                Modifier.clickable { onBarClick(entry, index) }
                            } else {
                                Modifier
                            }
                        )
                ) {
                    Text(
                        text = "%.1f".format(entry.valueTonnes),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                    Box(
                        modifier = Modifier
                            .width(barWidthDp.dp)
                            .height(chartHeightDp.dp)
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Canvas(modifier = Modifier.matchParentSize()) {
                            val barHeightRatio = (entry.valueTonnes / maxValue).toFloat().coerceIn(0f, 1f)
                            val barHeight = size.height * barHeightRatio
                            // Zero tonnage sits on the baseline (y = 0); no filled track column.
                            if (barHeight > 0.5f) {
                                drawRoundRect(
                                    color = if (selected) selectedBarColor else barColor,
                                    topLeft = Offset(0f, size.height - barHeight),
                                    size = Size(size.width, barHeight),
                                    cornerRadius = CornerRadius(8f, 8f)
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
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PeriodComparisonChart(
    currentLabel: String,
    previousLabel: String,
    currentTonnes: Double,
    previousTonnes: Double,
    modifier: Modifier = Modifier
) {
    val entries = listOf(
        StatBarEntry(previousLabel, previousTonnes),
        StatBarEntry(currentLabel, currentTonnes)
    )
    VerticalBarChart(
        title = "",
        entries = entries,
        modifier = modifier,
        barWidthDp = 72,
        chartHeightDp = 140
    )
}
