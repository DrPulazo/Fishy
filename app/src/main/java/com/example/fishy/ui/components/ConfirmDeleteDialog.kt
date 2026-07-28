package com.example.fishy.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.fishy.R
import com.example.fishy.ui.ErrorFeedback

@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    strongHaptic: Boolean = false
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        if (strongHaptic) ErrorFeedback.vibrateStrong(context, ignoreUserSetting = true)
        else ErrorFeedback.vibrate(context)
    }

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
