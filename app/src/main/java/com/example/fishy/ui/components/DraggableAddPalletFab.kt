package com.example.fishy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.fishy.R
import com.example.fishy.ui.ErrorFeedback
import com.example.fishy.ui.theme.FishyAccent
import kotlin.math.roundToInt

private val FabSize = 56.dp
private val HaloSize = 72.dp
private val EdgeMargin = 16.dp

/**
 * Circular accent FAB with a larger 50%-opacity halo behind it.
 * Long-press then drag to reposition; short tap adds a pallet (smart target).
 * Default: right edge, vertically centered.
 */
@Composable
fun DraggableAddPalletFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haloPx = with(density) { HaloSize.toPx() }
    val marginPx = with(density) { EdgeMargin.toPx() }

    var parentWidthPx by remember { mutableIntStateOf(0) }
    var parentHeightPx by remember { mutableIntStateOf(0) }

    val sized = parentWidthPx > 0 && parentHeightPx > 0
    val maxX = (parentWidthPx - haloPx).coerceAtLeast(0f)
    val maxY = (parentHeightPx - haloPx).coerceAtLeast(0f)
    val defaultX = (parentWidthPx - haloPx - marginPx).coerceIn(0f, maxX)
    val defaultY = ((parentHeightPx - haloPx) / 2f).coerceIn(0f, maxY)

    // Do not use NaN with rememberSaveable — it restores as 0 and pins FAB to top-left.
    var userDragged by rememberSaveable { mutableStateOf(false) }
    var savedX by rememberSaveable { mutableFloatStateOf(0f) }
    var savedY by rememberSaveable { mutableFloatStateOf(0f) }

    LaunchedEffect(maxX, maxY, sized) {
        if (!sized || !userDragged) return@LaunchedEffect
        savedX = savedX.coerceIn(0f, maxX)
        savedY = savedY.coerceIn(0f, maxY)
    }

    val posX = if (userDragged) savedX.coerceIn(0f, maxX) else defaultX
    val posY = if (userDragged) savedY.coerceIn(0f, maxY) else defaultY
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                parentWidthPx = size.width
                parentHeightPx = size.height
            }
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(posX.roundToInt(), posY.roundToInt()) }
                .size(HaloSize)
                .alpha(if (sized) 1f else 0f)
                .pointerInput(maxX, maxY, defaultX, defaultY, userDragged) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { ErrorFeedback.vibrate(context) },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (!userDragged) {
                                savedX = defaultX
                                savedY = defaultY
                                userDragged = true
                            }
                            savedX = (savedX + dragAmount.x).coerceIn(0f, maxX)
                            savedY = (savedY + dragAmount.y).coerceIn(0f, maxY)
                        }
                    )
                }
                .clickable(
                    enabled = sized,
                    interactionSource = interactionSource,
                    indication = ripple(bounded = false, radius = FabSize / 2),
                    role = Role.Button,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(FishyAccent.copy(alpha = 0.5f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(FabSize)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(FishyAccent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.add_pallet),
                    tint = Color.White
                )
            }
        }
    }
}
