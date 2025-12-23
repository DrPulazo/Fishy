package com.example.fishy.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    selectedDate: Date,
    onDateSelected: (Date) -> Unit,
    label: String = "Дата",
    modifier: Modifier = Modifier,
    dateRange: ClosedRange<Long>? = null
) {
    var showDatePicker by remember { mutableStateOf(false) }
    val dateFormatter = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }

    // Нормализуем дату (убираем время, оставляем только день)
    val normalizedDate = remember(selectedDate) {
        Calendar.getInstance().apply {
            time = selectedDate
            set(Calendar.HOUR_OF_DAY, 12) // Полдень, чтобы избежать проблем с часовыми поясами
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time
    }

    // Определяем диапазон годов
    val yearRange = remember(dateRange) {
        if (dateRange != null) {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = dateRange.start
            val startYear = calendar.get(Calendar.YEAR)
            calendar.timeInMillis = dateRange.endInclusive
            val endYear = calendar.get(Calendar.YEAR)
            startYear..endYear
        } else {
            // Текущий год ±10 лет
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            (currentYear - 0)..(currentYear + 14)
        }
    }

    // Создаем состояние DatePicker
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = normalizedDate.time,
        initialDisplayMode = DisplayMode.Picker,
        yearRange = yearRange
    )

    // Обновляем выбранную дату при изменении selectedDate
    LaunchedEffect(normalizedDate) {
        datePickerState.setSelection(normalizedDate.time)
    }

    OutlinedTextField(
        value = dateFormatter.format(selectedDate),
        onValueChange = { },
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        readOnly = true,
        trailingIcon = {
            IconButton(onClick = { showDatePicker = true }) {
                Icon(Icons.Default.CalendarToday, contentDescription = "Выбрать дату")
            }
        }
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            // Создаем Date из миллисекунд и нормализуем (ставим полдень)
                            val calendar = Calendar.getInstance().apply {
                                timeInMillis = millis
                                set(Calendar.HOUR_OF_DAY, 12)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            onDateSelected(calendar.time)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Выбрать")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Отмена")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}