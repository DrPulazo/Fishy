package com.example.fishy.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.fishy.R
import android.app.TimePickerDialog

@Composable
fun TimePickerField(
    time: String,
    onTimeChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    val context = LocalContext.current
    val (hour, minute) = remember(time) {
        val parts = time.split(":")
        Pair(
            parts.getOrNull(0)?.toIntOrNull() ?: 9,
            parts.getOrNull(1)?.toIntOrNull() ?: 0
        )
    }

    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = time,
            onValueChange = {},
            label = { Text(label, style = formLabelStyleOrDefault()) },
            textStyle = formTextStyleOrDefault(),
            readOnly = true,
            enabled = false,
            isError = isError,
            trailingIcon = {
                Icon(
                    Icons.Default.AccessTime,
                    contentDescription = stringResource(R.string.cd_pick_time)
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.outline
                },
                disabledLabelColor = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                disabledTrailingIconColor = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                disabledContainerColor = Color.Transparent,
                errorBorderColor = MaterialTheme.colorScheme.error,
                errorLabelColor = MaterialTheme.colorScheme.error,
                errorTrailingIconColor = MaterialTheme.colorScheme.error
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable {
                    TimePickerDialog(
                        context,
                        { _, selectedHour, selectedMinute ->
                            onTimeChange("%02d:%02d".format(selectedHour, selectedMinute))
                        },
                        hour,
                        minute,
                        true
                    ).show()
                }
        )
    }
}
