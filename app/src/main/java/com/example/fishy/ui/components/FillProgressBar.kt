package com.example.fishy.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.example.fishy.ui.theme.ProgressGreen
import com.example.fishy.ui.theme.ProgressOrange
import com.example.fishy.ui.theme.ProgressRed
import com.example.fishy.ui.theme.ProgressYellow
import kotlinx.coroutines.launch

/** Set true to restore the white flash overlay on first reach of 100%. */
private const val ENABLE_PROGRESS_FLASH = false

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
    val fillColor = remember(progress, animated) {
        if (overloaded) ProgressRed else progressFillColor(animated)
    }
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    var prevProgress by remember { mutableFloatStateOf(progress) }
    val pulse = remember { Animatable(1f) }
    // Flash kept dormant — flip [ENABLE_PROGRESS_FLASH] to restore white lighten on 100%.
    val flash = remember { Animatable(0f) }
    val shimmer = remember { Animatable(0f) }

    LaunchedEffect(progress) {
        val crossedToFull =
            prevProgress < 1f && progress >= 1f && progress <= 1f
        prevProgress = progress
        if (!crossedToFull) return@LaunchedEffect

        pulse.snapTo(1f)
        flash.snapTo(0f)
        shimmer.snapTo(0f)
        launch {
            pulse.animateTo(1.35f, tween(150, easing = LinearEasing))
            pulse.animateTo(1f, tween(350, easing = LinearEasing))
        }
        if (ENABLE_PROGRESS_FLASH) {
            launch {
                flash.animateTo(1f, tween(120, easing = LinearEasing))
                flash.animateTo(0f, tween(380, easing = LinearEasing))
            }
        }
        launch {
            shimmer.animateTo(1f, tween(500, easing = LinearEasing))
            shimmer.snapTo(0f)
        }
    }

    val displayColor = if (ENABLE_PROGRESS_FLASH && flash.value > 0f && !overloaded) {
        lerp(fillColor, Color.White, flash.value * 0.45f)
    } else {
        fillColor
    }
    val pulseScale = pulse.value
    val shimmerT = shimmer.value

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .graphicsLayer {
                scaleY = pulseScale
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            }
            .clipToBounds()
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawRect(color = trackColor)
            val fillW = size.width * animated
            if (fillW > 0f) {
                drawRect(
                    color = displayColor,
                    size = Size(fillW, size.height)
                )
                if (shimmerT > 0f && animated >= 1f && !overloaded) {
                    val band = size.width * 0.35f
                    val x = -band + (size.width + band) * shimmerT
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.55f),
                                Color.Transparent
                            ),
                            startX = x,
                            endX = x + band
                        ),
                        topLeft = Offset(0f, 0f),
                        size = Size(fillW, size.height)
                    )
                }
            }
        }
    }
}
