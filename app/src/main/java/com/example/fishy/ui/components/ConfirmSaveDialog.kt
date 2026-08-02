package com.example.fishy.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.example.fishy.R

@Composable
fun CenteredDialogTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
}

@Composable
fun CenteredDialogMessage(text: String) {
    DialogMessage(text = text, textAlign = TextAlign.Center)
}

@Composable
fun DialogMessage(
    text: String,
    textAlign: TextAlign = TextAlign.Center
) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        textAlign = textAlign
    )
}

@Composable
fun ConfirmSaveDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = stringResource(R.string.save),
    messageTextAlign: TextAlign = TextAlign.Center
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        title = { CenteredDialogTitle(title) },
        text = if (message.isNotBlank()) {
            { DialogMessage(text = message, textAlign = messageTextAlign) }
        } else {
            null
        },
        confirmButton = {
            DialogCancelConfirmActions(
                onCancel = onDismiss,
                onConfirm = onConfirm,
                confirmText = confirmText
            )
        }
    )
}
