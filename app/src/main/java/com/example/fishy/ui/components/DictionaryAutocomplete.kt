// DictionaryAutocomplete.kt
package com.example.fishy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
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
    val focusManager = LocalFocusManager.current

    // Синхронизируем с внешним значением
    LaunchedEffect(value) {
        if (value != textFieldValue.text) {
            textFieldValue = TextFieldValue(value)
        }
    }

    // Оптимизированная фильтрация - показываем все элементы при пустом поле, если список раскрыт
    val filteredItems by remember(textFieldValue.text, dictionaryItems, expanded) {
        derivedStateOf {
            when {
                textFieldValue.text.isEmpty() && expanded -> {
                    // При пустом поле и раскрытом списке показываем все элементы
                    dictionaryItems
                        .sortedByDescending { it.lastUsed }
                        .take(15)
                }
                textFieldValue.text.isNotEmpty() -> {
                    // При вводе текста фильтруем
                    dictionaryItems
                        .filter { it.value.startsWith(textFieldValue.text, ignoreCase = true) }
                        .sortedByDescending { it.lastUsed }
                        .take(10)
                }
                else -> {
                    // В остальных случаях пустой список
                    emptyList()
                }
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
            kotlinx.coroutines.delay(50)
            focusRequester.requestFocus()
        } else {
            focusManager.clearFocus()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(2f) // Чтобы поле ввода было над затемненным фоном
        ) {
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { newValue ->
                    textFieldValue = newValue
                    // Автоматически раскрываем список при вводе, если есть результаты
                    expanded = newValue.text.isNotBlank() &&
                            dictionaryItems.any { it.value.startsWith(newValue.text, ignoreCase = true) }

                    showAddButton = newValue.text.isNotBlank() && !hasExactMatch

                    // Обновляем внешнее состояние
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
                                    onAddToDictionary(dictionaryType, textFieldValue.text)
                                    showAddButton = false
                                }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Добавить в словарь")
                            }
                        }
                        IconButton(
                            onClick = {
                                expanded = !expanded
                            }
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
                        .offset(y = (-8).dp)
                        .zIndex(3f), // Список должен быть над затемненным фоном
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(filteredItems) { item ->
                            TextButton(
                                onClick = {
                                    textFieldValue = TextFieldValue(item.value)
                                    onValueChange(item.value)
                                    onSaveToDictionary(item.value)
                                    expanded = false // Закрываем список после выбора
                                    showAddButton = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                shape = RoundedCornerShape(0.dp)
                            ) {
                                Text(
                                    text = item.value,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp, horizontal = 16.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }

        // Затемненный фон и обработчик клика вне компонента
        if (expanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .clickable(
                        enabled = expanded,
                        onClick = { expanded = false }
                    )
                    .zIndex(1f) // Фон под полем ввода и списком
            )
        }
    }

    // Обновляем состояние кнопки "Добавить" при изменении текста
    LaunchedEffect(textFieldValue.text) {
        showAddButton = textFieldValue.text.isNotBlank() && !hasExactMatch
    }
}