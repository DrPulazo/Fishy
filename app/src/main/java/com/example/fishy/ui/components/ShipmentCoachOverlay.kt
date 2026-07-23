package com.example.fishy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.fishy.R
import com.example.fishy.ui.theme.Error
import com.example.fishy.ui.theme.FishyAccent

private val TipShape = RoundedCornerShape(12.dp)
/** Was 1.5.dp; +~1–2 px → ~3.dp. */
private val TipBorderWidth = 3.dp

/**
 * One-shot tip card: accent border, dismiss only via close button.
 */
@Composable
fun SoftCoachTip(
    text: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .widthIn(max = 300.dp)
            .border(TipBorderWidth, FishyAccent, TipShape),
        shape = TipShape,
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.coach_tip_dismiss),
                    tint = Error
                )
            }
        }
    }
}

/**
 * Full-screen modal coach: 50% scrim, blocks clicks except the tip close button.
 * Shows at most one tip at a time, centered.
 */
@Composable
fun ShipmentCoachOverlay(
    showFabTip: Boolean,
    showSwipeTip: Boolean,
    onDismissFabTip: () -> Unit,
    onDismissSwipeTip: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!showFabTip && !showSwipeTip) return
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        )
        SoftCoachTip(
            text = stringResource(
                if (showFabTip) R.string.coach_tip_fab_drag else R.string.coach_tip_pallet_swipe
            ),
            onDismiss = if (showFabTip) onDismissFabTip else onDismissSwipeTip,
            modifier = Modifier
                .align(Alignment.Center)
                .zIndex(1f)
                .padding(horizontal = 24.dp)
        )
    }
}
