package com.example.fishy.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun HintedScrollableTabs(
    selectedIndex: Int,
    titles: List<String>,
    onSelect: (Int) -> Unit
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val surface = MaterialTheme.colorScheme.surface
    val density = LocalDensity.current
    val tabCoords = remember { mutableMapOf<Int, LayoutCoordinates>() }

    val showLeft by remember { derivedStateOf { scrollState.value > 0 } }
    val showRight by remember {
        derivedStateOf { scrollState.maxValue > 0 && scrollState.value < scrollState.maxValue }
    }
    val leftAlpha by animateFloatAsState(if (showLeft) 1f else 0f, label = "leftHint")
    val rightAlpha by animateFloatAsState(if (showRight) 1f else 0f, label = "rightHint")

    // Keep the selected tab visible; small horizontal padding = next tab peeks/clips.
    LaunchedEffect(selectedIndex, tabCoords[selectedIndex]?.size) {
        val coords = tabCoords[selectedIndex] ?: return@LaunchedEffect
        val left = coords.positionInParent().x.roundToInt()
        val right = left + coords.size.width
        val viewport = scrollState.viewportSize
        if (viewport <= 0) return@LaunchedEffect
        val target = when {
            left < scrollState.value + 8 -> (left - 8).coerceAtLeast(0)
            right > scrollState.value + viewport - 8 -> (right - viewport + 8).coerceAtLeast(0)
            else -> return@LaunchedEffect
        }
        scrollState.animateScrollTo(target.coerceIn(0, scrollState.maxValue))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(scrollState)
                // Small padding: next/last tab gets clipped at the edge.
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            titles.forEachIndexed { index, title ->
                val selected = index == selectedIndex
                Column(
                    modifier = Modifier
                        .onGloballyPositioned { tabCoords[index] = it }
                        .clickable { onSelect(index) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = title,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .height(3.dp)
                            .fillMaxWidth()
                            .background(
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.Transparent
                                }
                            )
                    )
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.align(Alignment.BottomCenter),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        // Left fade + chevron
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(36.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                surface.copy(alpha = 0.95f * leftAlpha),
                                surface.copy(alpha = 0f)
                            )
                        )
                    )
            )
            if (leftAlpha > 0.05f) {
                IconButton(
                    onClick = {
                        scope.launch {
                            val step = with(density) { 120.dp.roundToPx() }
                            scrollState.animateScrollTo(
                                (scrollState.value - step).coerceAtLeast(0)
                            )
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = leftAlpha)
                    )
                }
            }
        }

        // Right fade + chevron
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(36.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                surface.copy(alpha = 0f),
                                surface.copy(alpha = 0.95f * rightAlpha)
                            )
                        )
                    )
            )
            if (rightAlpha > 0.05f) {
                IconButton(
                    onClick = {
                        scope.launch {
                            val step = with(density) { 120.dp.roundToPx() }
                            scrollState.animateScrollTo(
                                (scrollState.value + step).coerceAtMost(scrollState.maxValue)
                            )
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = rightAlpha)
                    )
                }
            }
        }
    }
}
