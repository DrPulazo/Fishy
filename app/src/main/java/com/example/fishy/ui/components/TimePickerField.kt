package com.example.fishy.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

@Composable
fun TimePickerField(
    time: String,
    onTimeChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }

    // Разбираем текущее время на часы и минуты
    val (hour, minute) = remember(time) {
        val parts = time.split(":")
        if (parts.size == 2) {
            Pair(parts[0].toIntOrNull() ?: 0, parts[1].toIntOrNull() ?: 0)
        } else {
            Pair(0, 0)
        }
    }

    OutlinedTextField(
        value = time,
        onValueChange = { },
        readOnly = true,
        label = { Text(label) },
        modifier = modifier,
        trailingIcon = {
            IconButton(
                onClick = { showDialog = true }
            ) {
                Icon(Icons.Default.AccessTime, contentDescription = "Выбрать время")
            }
        }
    )

    if (showDialog) {
        // Используем TimePickerDialog с темной темой для API 21+ и 24-часовым форматом
        val timePickerDialog = android.app.TimePickerDialog(
            context,
            android.R.style.Theme_Material_Dialog_Alert, // Темная тема
            { _, selectedHour, selectedMinute ->
                val formattedTime = String.format("%02d:%02d", selectedHour, selectedMinute)
                onTimeChange(formattedTime)
                showDialog = false
            },
            hour,
            minute,
            true // Явно указываем 24-часовой формат
        )

        // Устанавливаем слушатели
        timePickerDialog.setOnCancelListener { showDialog = false }
        timePickerDialog.setButton(android.app.Dialog.BUTTON_NEGATIVE, "Отмена") { _, _ ->
            showDialog = false
        }

        // Устанавливаем темные цвета для кнопок
        timePickerDialog.setOnShowListener {
            // Получаем доступ к кнопкам
            val buttonPositive = timePickerDialog.getButton(android.app.Dialog.BUTTON_POSITIVE)
            val buttonNegative = timePickerDialog.getButton(android.app.Dialog.BUTTON_NEGATIVE)

            // Устанавливаем белый цвет текста для кнопок
            buttonPositive?.setTextColor(android.graphics.Color.WHITE)
            buttonNegative?.setTextColor(android.graphics.Color.WHITE)
        }

        // Показываем диалог
        LaunchedEffect(showDialog) {
            if (showDialog) {
                timePickerDialog.show()
            }
        }

        // Закрываем при уходе
        DisposableEffect(Unit) {
            onDispose {
                if (showDialog && timePickerDialog.isShowing) {
                    timePickerDialog.dismiss()
                    showDialog = false
                }
            }
        }
    }
}