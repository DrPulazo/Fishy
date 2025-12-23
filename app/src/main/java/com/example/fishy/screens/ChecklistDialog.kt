package com.example.fishy.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.fishy.database.entities.ChecklistItem
import com.example.fishy.viewmodels.SchedulerViewModel
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistDialog(
    shipmentId: Long,
    onDismiss: () -> Unit,
    viewModel: SchedulerViewModel
) {
    val checklistItems by viewModel.getChecklistItems(shipmentId).collectAsState(emptyList())
    var newItemText by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Чек-лист отгрузки")
            }
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Статистика
                val completedCount = checklistItems.count { it.isCompleted }
                val totalCount = checklistItems.size

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Выполнено: $completedCount/$totalCount")
                        Text(
                            text = if (totalCount > 0) "${(completedCount * 100 / totalCount)}%" else "0%",
                            color = when {
                                completedCount == totalCount && totalCount > 0 -> Color(0xFF4CAF50)
                                completedCount > 0 -> Color(0xFFFFC107)
                                totalCount > 0 -> Color(0xFFF44336)
                                else -> Color(0xFF9E9E9E)
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Список пунктов
                if (checklistItems.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Checklist,
                            contentDescription = "Нет чек-листа",
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Нет пунктов чек-листа",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(checklistItems) { item ->
                            ChecklistItemRow(
                                item = item,
                                onToggle = {
                                    coroutineScope.launch {
                                        viewModel.updateChecklistItem(item.copy(isCompleted = !item.isCompleted))
                                    }
                                },
                                onDelete = {
                                    coroutineScope.launch {
                                        viewModel.deleteChecklistItem(item)
                                    }
                                }
                            )
                        }
                    }
                }

                // Кнопка добавления
                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Добавить пункт")
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("ГОТОВО")
            }
        }
    )

    // Диалог добавления нового пункта
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Добавить пункт") },
            text = {
                OutlinedTextField(
                    value = newItemText,
                    onValueChange = { newItemText = it },
                    label = { Text("Название пункта") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newItemText.isNotBlank()) {
                            coroutineScope.launch {
                                viewModel.addChecklistItem(shipmentId, newItemText, true)
                                newItemText = ""
                            }
                        }
                        showAddDialog = false
                    },
                    enabled = newItemText.isNotBlank()
                ) {
                    Text("ДОБАВИТЬ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("ОТМЕНА")
                }
            }
        )
    }
}

@Composable
fun ChecklistItemRow(
    item: ChecklistItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Checkbox(
                    checked = item.isCompleted,
                    onCheckedChange = { onToggle() }
                )

                Column {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (item.isCompleted) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Удалить",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}