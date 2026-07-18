package com.example.fishy.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.fishy.R

@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        title = { CenteredDialogTitle(title) },
        text = if (message.isNotBlank()) {
            { CenteredDialogMessage(message) }
        } else {
            null
        },
        confirmButton = {
            DialogCancelConfirmActions(
                onCancel = onDismiss,
                onConfirm = onConfirm,
                confirmText = stringResource(R.string.delete),
                confirmColor = MaterialTheme.colorScheme.error
            )
        }
    )
}
