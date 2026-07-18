package com.example.fishy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.fishy.R
import com.example.fishy.ui.theme.FishyAccent
import kotlin.math.roundToInt

private val FabSize = 56.dp
private val HaloSize = 72.dp
private val EdgeMargin = 16.dp

/**
 * Circular accent FAB with a larger 50%-opacity halo behind it.
 * Long-press then drag to reposition; short tap adds a pallet (smart target).
 * Same look in light and dark themes, all shipment modes.
 */
@Composable
fun DraggableAddPalletFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val maxX = with(density) { (maxWidth - HaloSize).toPx() }.coerceAtLeast(0f)
        val maxY = with(density) { (maxHeight - HaloSize).toPx() }.coerceAtLeast(0f)
        val defaultX = with(density) { (maxWidth - HaloSize - EdgeMargin).toPx() }.coerceIn(0f, maxX)
        val defaultY = with(density) { (maxHeight - HaloSize - EdgeMargin).toPx() }.coerceIn(0f, maxY)

        var x by rememberSaveable { mutableFloatStateOf(Float.NaN) }
        var y by rememberSaveable { mutableFloatStateOf(Float.NaN) }

        LaunchedEffect(defaultX, defaultY, maxX, maxY) {
            if (x.isNaN() || y.isNaN()) {
                x = defaultX
                y = defaultY
            } else {
                x = x.coerceIn(0f, maxX)
                y = y.coerceIn(0f, maxY)
            }
        }

        val posX = if (x.isNaN()) defaultX else x.coerceIn(0f, maxX)
        val posY = if (y.isNaN()) defaultY else y.coerceIn(0f, maxY)
        val interactionSource = remember { MutableInteractionSource() }

        Box(
            modifier = Modifier
                .offset { IntOffset(posX.roundToInt(), posY.roundToInt()) }
                .size(HaloSize)
                .pointerInput(maxX, maxY, defaultX, defaultY) {
                    detectDragGesturesAfterLongPress(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val curX = if (x.isNaN()) defaultX else x
                            val curY = if (y.isNaN()) defaultY else y
                            x = (curX + dragAmount.x).coerceIn(0f, maxX)
                            y = (curY + dragAmount.y).coerceIn(0f, maxY)
                        }
                    )
                }
                .clickable(
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
