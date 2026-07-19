package com.example.fishy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.example.fishy.R

/**
 * Single primary action centered across the dialog width.
 * Put this in [androidx.compose.material3.AlertDialog]'s confirmButton and omit dismissButton.
 */
@Composable
fun DialogCenteredAction(
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * Cancel on the left, confirm on the right.
 * Put this in confirmButton only — do not also set dismissButton (M3 then mis-aligns).
 */
@Composable
fun DialogCancelConfirmActions(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    confirmText: String,
    cancelText: String = stringResource(R.string.cancel),
    confirmEnabled: Boolean = true,
    confirmColor: Color = MaterialTheme.colorScheme.primary,
    cancelColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TextButton(onClick = onCancel) {
            Text(text = cancelText, color = cancelColor)
        }
        TextButton(onClick = onConfirm, enabled = confirmEnabled) {
            Text(
                text = confirmText,
                color = if (confirmEnabled) {
                    confirmColor
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                }
            )
        }
    }
}

@Composable
fun DialogCenteredFishyButton(
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit
) {
    DialogCenteredAction {
        FishyButton(onClick = onClick, content = content)
    }
}
