package com.example.fishy.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Brief scale pulse when a checkbox becomes checked. */
@Composable
fun FishyPulseCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: CheckboxColors = fishyCheckboxColors()
) {
    val scale = remember { Animatable(1f) }
    var wasChecked by remember { mutableStateOf(checked) }
    LaunchedEffect(checked) {
        if (checked && !wasChecked) {
            scale.snapTo(1f)
            scale.animateTo(1.08f, tween(60))
            scale.animateTo(1f, tween(80))
        }
        wasChecked = checked
    }
    val scaleValue = scale.value
    Checkbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = colors,
        modifier = modifier.graphicsLayer {
            scaleX = scaleValue
            scaleY = scaleValue
        }
    )
}

/** Horizontal shake once when [hasError] rises false → true. */
fun Modifier.shakeOnError(hasError: Boolean): Modifier = composed {
    val offsetX = remember { Animatable(0f) }
    var wasError by remember { mutableStateOf(false) }
    LaunchedEffect(hasError) {
        if (hasError && !wasError) {
            for (delta in floatArrayOf(-6f, 6f, -4f, 4f, -2f, 2f, 0f)) {
                offsetX.animateTo(delta, tween(35))
            }
        }
        wasError = hasError
    }
    val x = offsetX.value
    offset { IntOffset(x.roundToInt(), 0) }
}

/**
 * Fade + slight slide-in for forecast placeholder rows on first appear.
 * Real rows get [Modifier] unchanged (no graphicsLayer).
 */
@Composable
fun rememberPlaceholderEnter(isPlaceholder: Boolean): Modifier {
    val alpha = remember { Animatable(if (isPlaceholder) 0f else 1f) }
    val offsetY = remember { Animatable(if (isPlaceholder) 10f else 0f) }
    LaunchedEffect(isPlaceholder) {
        if (!isPlaceholder) {
            alpha.snapTo(1f)
            offsetY.snapTo(0f)
            return@LaunchedEffect
        }
        alpha.snapTo(0f)
        offsetY.snapTo(10f)
        launch { alpha.animateTo(1f, tween(180)) }
        launch { offsetY.animateTo(0f, tween(180)) }
    }
    if (!isPlaceholder) return Modifier
    val a = alpha.value
    val y = offsetY.value
    return Modifier.graphicsLayer {
        this.alpha = a
        translationY = y
    }
}
