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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.fishy.FishyApp
import com.example.fishy.R
import com.example.fishy.data.settings.FishySettings
import com.example.fishy.ui.ErrorFeedback
import com.example.fishy.ui.theme.FishyAccent
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val FabSize = 56.dp
private val HaloSize = 72.dp
private val EdgeMargin = 16.dp
/** Default vertical placement: ~2/3 down the content area. */
private const val DefaultYFraction = 2f / 3f

/**
 * Circular accent FAB with a larger 50%-opacity halo behind it.
 * Long-press then drag to reposition; short tap adds a pallet (smart target).
 * Default: right edge, ~2/3 down. Last drag position is persisted in settings.
 */
@Composable
fun DraggableAddPalletFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val settingsRepo = FishyApp.instance.settingsRepository
    val settings by settingsRepo.settings.collectAsState(initial = FishySettings())

    val haloPx = with(density) { HaloSize.toPx() }
    val marginPx = with(density) { EdgeMargin.toPx() }

    var parentWidthPx by remember { mutableIntStateOf(0) }
    var parentHeightPx by remember { mutableIntStateOf(0) }

    val sized = parentWidthPx > 0 && parentHeightPx > 0
    val maxX = (parentWidthPx - haloPx).coerceAtLeast(0f)
    val maxY = (parentHeightPx - haloPx).coerceAtLeast(0f)
    val defaultX = (parentWidthPx - haloPx - marginPx).coerceIn(0f, maxX)
    val defaultY = ((parentHeightPx - haloPx) * DefaultYFraction).coerceIn(0f, maxY)

    val hasSavedPos = settings.fabPosXFraction >= 0f && settings.fabPosYFraction >= 0f

    // Local drag state; seeded from settings when parent is measured.
    var userDragged by remember { mutableStateOf(false) }
    var savedX by remember { mutableFloatStateOf(0f) }
    var savedY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(sized, maxX, maxY, settings.fabPosXFraction, settings.fabPosYFraction) {
        if (!sized) return@LaunchedEffect
        if (hasSavedPos) {
            savedX = (settings.fabPosXFraction * maxX).coerceIn(0f, maxX)
            savedY = (settings.fabPosYFraction * maxY).coerceIn(0f, maxY)
            userDragged = true
        } else if (userDragged) {
            savedX = savedX.coerceIn(0f, maxX)
            savedY = savedY.coerceIn(0f, maxY)
        }
    }

    val posX = if (userDragged) savedX.coerceIn(0f, maxX) else defaultX
    val posY = if (userDragged) savedY.coerceIn(0f, maxY) else defaultY
    val interactionSource = remember { MutableInteractionSource() }

    fun persistPosition(x: Float, y: Float) {
        if (maxX <= 0f || maxY <= 0f) return
        val fx = (x / maxX).coerceIn(0f, 1f)
        val fy = (y / maxY).coerceIn(0f, 1f)
        scope.launch {
            settingsRepo.update {
                it.copy(fabPosXFraction = fx, fabPosYFraction = fy)
            }
        }
    }

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
                .pointerInput(maxX, maxY, marginPx, defaultX, defaultY) {
                    val startX = defaultX
                    val startY = defaultY
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            ErrorFeedback.vibrate(context)
                            // Seed before first move; do not key pointerInput on userDragged —
                            // flipping it mid-gesture would cancel the current drag.
                            if (!userDragged) {
                                savedX = startX
                                savedY = startY
                                userDragged = true
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (!userDragged) {
                                savedX = startX
                                savedY = startY
                                userDragged = true
                            }
                            savedX = (savedX + dragAmount.x).coerceIn(0f, maxX)
                            savedY = (savedY + dragAmount.y).coerceIn(0f, maxY)
                        },
                        onDragEnd = {
                            persistPosition(savedX, savedY)
                        },
                        onDragCancel = {
                            persistPosition(savedX, savedY)
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
