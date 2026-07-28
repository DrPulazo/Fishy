package com.example.fishy.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.example.fishy.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    selectedDateMillis: Long,
    onDateSelected: (Long) -> Unit,
    label: String? = null,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    var showPicker by remember { mutableStateOf(false) }
    val dateFormatter = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }

    val normalizedMillis = remember(selectedDateMillis) {
        Calendar.getInstance().apply {
            timeInMillis = selectedDateMillis
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val yearRange = remember {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        (currentYear - 1)..(currentYear + 14)
    }

    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = dateFormatter.format(Date(selectedDateMillis)),
            onValueChange = {},
            label = label?.let { { Text(it, style = formLabelStyleOrDefault()) } },
            textStyle = formTextStyleOrDefault(),
            readOnly = true,
            enabled = false,
            isError = isError,
            trailingIcon = {
                Icon(
                    Icons.Default.CalendarToday,
                    contentDescription = stringResource(R.string.cd_pick_date)
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
                // Transparent so the field matches dialog / accordion surface (not a solid patch).
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
                .clickable { showPicker = true }
        )
    }

    if (showPicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = normalizedMillis,
            initialDisplayMode = DisplayMode.Picker,
            yearRange = yearRange
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            colors = DatePickerDefaults.colors(
                containerColor = MaterialTheme.colorScheme.background
            ),
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val calendar = Calendar.getInstance().apply {
                                timeInMillis = millis
                                set(Calendar.HOUR_OF_DAY, 12)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            onDateSelected(calendar.timeInMillis)
                        }
                        showPicker = false
                    }
                ) {
                    Text(stringResource(R.string.action_select))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
