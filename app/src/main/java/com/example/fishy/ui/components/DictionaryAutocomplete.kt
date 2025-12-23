// DictionaryAutocomplete.kt (обновите существующий файл)
package com.example.fishy.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.fishy.database.entities.DictionaryItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryAutocomplete(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    dictionaryType: String,
    dictionaryItems: List<DictionaryItem>,
    onAddToDictionary: (String, String) -> Unit,
    onSaveToDictionary: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue(value)) }
    var expanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    var showAddButton by remember { mutableStateOf(false) }

    // Синхронизируем с внешним значением
    LaunchedEffect(value) {
        if (value != textFieldValue.text) {
            textFieldValue = TextFieldValue(value)
        }
    }

    // Оптимизированная фильтрация
    val filteredItems by remember(textFieldValue.text, dictionaryItems) {
        derivedStateOf {
            if (textFieldValue.text.isEmpty()) {
                emptyList()
            } else {
                dictionaryItems
                    .filter { it.value.startsWith(textFieldValue.text, ignoreCase = true) }
                    .sortedByDescending { it.lastUsed }
                    .take(10)
            }
        }
    }

    // Проверяем точное совпадение
    val hasExactMatch by remember(textFieldValue.text, dictionaryItems) {
        derivedStateOf {
            dictionaryItems.any { it.value.equals(textFieldValue.text, ignoreCase = true) }
        }
    }

    // Управление фокусом
    LaunchedEffect(expanded) {
        if (expanded) {
            // Небольшая задержка для стабильности
            kotlinx.coroutines.delay(50)
            focusRequester.requestFocus()
        }
    }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                textFieldValue = newValue
                expanded = newValue.text.isNotBlank() && filteredItems.isNotEmpty()
                showAddButton = newValue.text.isNotBlank() && !hasExactMatch

                // Обновляем внешнее состояние с задержкой (для производительности)
                if (newValue.text != value) {
                    onValueChange(newValue.text)
                }
            },
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences
            ),
            trailingIcon = {
                Row {
                    if (showAddButton) {
                        IconButton(
                            onClick = {
                                // ВАЖНО: Добавляем только через onAddToDictionary
                                // НЕ вызываем onSaveToDictionary здесь
                                onAddToDictionary(dictionaryType, textFieldValue.text)
                                // После добавления скрываем кнопку
                                showAddButton = false
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Добавить в словарь")
                        }
                    }
                    IconButton(
                        onClick = { expanded = !expanded }
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess
                            else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Скрыть список" else "Показать список"
                        )
                    }
                }
            }
        )

        // Выпадающий список
        if (expanded && filteredItems.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .offset(y = (-8).dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    items(filteredItems) { item ->
                        TextButton(
                            onClick = {
                                textFieldValue = TextFieldValue(item.value)
                                // При выборе из списка сначала обновляем поле
                                onValueChange(item.value)
                                // Затем сохраняем в словарь (обновляем использование)
                                onSaveToDictionary(item.value)
                                expanded = false
                                showAddButton = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text(
                                text = item.value,
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Divider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }

    // Обновляем состояние кнопки "Добавить" при изменении текста
    LaunchedEffect(textFieldValue.text) {
        showAddButton = textFieldValue.text.isNotBlank() && !hasExactMatch
    }
}