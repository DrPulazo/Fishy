package com.example.fishy.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.example.fishy.ui.theme.ProgressGreen
import com.example.fishy.ui.theme.ProgressOrange
import com.example.fishy.ui.theme.ProgressRed
import com.example.fishy.ui.theme.ProgressYellow

/**
 * Smooth red → orange → yellow → green as fill grows (lerped per percent, not stepwise).
 * Overload (>100%) is solid red so excess places are never mistaken for "done".
 */
fun progressFillColor(progress: Float): Color {
    if (progress > 1f) return ProgressRed
    val t = progress.coerceIn(0f, 1f)
    return when {
        t <= 0.33f -> {
            val local = if (t <= 0f) 0f else t / 0.33f
            lerp(ProgressRed, ProgressOrange, local)
        }
        t <= 0.66f -> {
            val local = (t - 0.33f) / 0.33f
            lerp(ProgressOrange, ProgressYellow, local)
        }
        else -> {
            val local = (t - 0.66f) / 0.34f
            lerp(ProgressYellow, ProgressGreen, local.coerceIn(0f, 1f))
        }
    }
}

@Composable
fun FillProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val overloaded = progress > 1f
    val targetFill = if (overloaded) 1f else progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = targetFill,
        animationSpec = tween(200),
        label = "fill"
    )
    val color = remember(progress, animated) {
        if (overloaded) ProgressRed else progressFillColor(animated)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .background(color)
        )
    }
}
