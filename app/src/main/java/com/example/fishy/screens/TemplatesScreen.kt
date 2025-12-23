package com.example.fishy.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.fishy.database.entities.DictionaryItem
import com.example.fishy.viewmodels.SchedulerViewModel
import com.example.fishy.viewmodels.SchedulerViewModelFactory
import android.app.Application
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val viewModel: SchedulerViewModel = viewModel(
        factory = SchedulerViewModelFactory(context.applicationContext as Application)
    )

    val coroutineScope = rememberCoroutineScope()

    // Триггер для обновления
    val refreshTrigger by viewModel.refreshTrigger.collectAsState()

    // Состояния для загрузки словарей
    val ports by viewModel.getDictionaryItems("port").collectAsState(emptyList())
    val customers by viewModel.getDictionaryItems("customer").collectAsState(emptyList())
    val products by viewModel.getDictionaryItems("product").collectAsState(emptyList())
    val vessels by viewModel.getDictionaryItems("vessel").collectAsState(emptyList())
    val manufacturers by viewModel.getDictionaryItems("manufacturer").collectAsState(emptyList())

    // Логируем изменения
    LaunchedEffect(ports) {
        println("DEBUG: Ports обновлены: ${ports.size}")
        ports.forEachIndexed { i, item ->
            println("  $i: id=${item.id}, value='${item.value}'")
        }
    }

    // Состояния для редактирования
    var editingItem by remember { mutableStateOf<DictionaryItem?>(null) }
    var editDialogValue by remember { mutableStateOf("") }
    var newItemValue by remember { mutableStateOf("") }
    var showEditDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var activeAccordion by remember { mutableStateOf<String?>(null) }
    var addingItemType by remember { mutableStateOf<String?>(null) }

    // Функция для обработки удаления
    fun deleteItem(item: DictionaryItem) {
        coroutineScope.launch {
            viewModel.deleteDictionaryItem(item)
            // Принудительно обновляем
            viewModel.triggerRefresh()
        }
    }

    // Функция для обработки редактирования
    fun startEditItem(item: DictionaryItem) {
        editingItem = item
        editDialogValue = item.value
        showEditDialog = true
    }

    // Функция для сохранения редактирования
    fun saveEditItem() {
        editingItem?.let { item ->
            coroutineScope.launch {
                viewModel.updateDictionaryItem(item.copy(value = editDialogValue))
                viewModel.triggerRefresh()
            }
        }
        showEditDialog = false
        editingItem = null
    }

    // НОВАЯ функция для добавления элемента
    fun addNewItem(type: String) {
        if (newItemValue.isNotBlank()) {
            coroutineScope.launch {
                println("DEBUG: Начинаем добавление элемента: type=$type, value='$newItemValue'")

                // 1. Проверяем текущие элементы до добавления
                val beforeItems = viewModel.getDictionaryItemsByType(type)
                println("DEBUG: Элементов типа '$type' до добавления: ${beforeItems.size}")

                // 2. Добавляем элемент с помощью нового метода
                val success = viewModel.addDictionaryItemAndRefresh(type, newItemValue)
                println("DEBUG: Результат добавления: $success")

                if (success) {
                    // 3. Ждем и проверяем
                    delay(100)

                    // 4. Проверяем элементы после добавления
                    val afterItems = viewModel.getDictionaryItemsByType(type)
                    println("DEBUG: Элементов типа '$type' после добавления: ${afterItems.size}")

                    // 5. Ищем новый элемент
                    val addedItem = afterItems.find { it.value == newItemValue }
                    if (addedItem != null) {
                        println("DEBUG: Найден добавленный элемент: id=${addedItem.id}, value='${addedItem.value}'")
                    } else {
                        println("DEBUG: Элемент не найден! Проверяем всю базу...")
                        val allItems = viewModel.getAllDictionaryItemsForDebug()
                        allItems.forEach { item ->
                            println("  id=${item.id}, type='${item.type}', value='${item.value}'")
                        }
                    }

                    // 6. Принудительно обновляем UI
                    viewModel.triggerRefresh()
                }

                newItemValue = ""
            }
        }
        showAddDialog = false
        addingItemType = null
    }


    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "ШАБЛОНЫ",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Аккордеон "ПОРТЫ"
                item {
                    DictionaryAccordion(
                        title = "ПОРТЫ",
                        type = "port",
                        items = ports,
                        isExpanded = activeAccordion == "port",
                        onExpandedChange = { activeAccordion = if (it) "port" else null },
                        onEditItem = ::startEditItem,
                        onDeleteItem = ::deleteItem,
                        onAddNew = {
                            newItemValue = ""
                            showAddDialog = true
                            activeAccordion = "port"
                        }
                    )
                }

                // Аккордеон "ЗАКАЗЧИКИ"
                item {
                    DictionaryAccordion(
                        title = "ЗАКАЗЧИКИ",
                        type = "customer",
                        items = customers,
                        isExpanded = activeAccordion == "customer",
                        onExpandedChange = { activeAccordion = if (it) "customer" else null },
                        onEditItem = ::startEditItem,
                        onDeleteItem = ::deleteItem,
                        onAddNew = {
                            newItemValue = ""
                            showAddDialog = true
                            activeAccordion = "customer"
                        }
                    )
                }

                // Аккордеон "ПРОДУКЦИЯ"
                item {
                    DictionaryAccordion(
                        title = "ПРОДУКЦИЯ",
                        type = "product",
                        items = products,
                        isExpanded = activeAccordion == "product",
                        onExpandedChange = { activeAccordion = if (it) "product" else null },
                        onEditItem = ::startEditItem,
                        onDeleteItem = ::deleteItem,
                        onAddNew = {
                            newItemValue = ""
                            showAddDialog = true
                            activeAccordion = "product"
                        }
                    )
                }

                // Аккордеон "ИЗГОТОВИТЕЛИ"
                item {
                    DictionaryAccordion(
                        title = "ИЗГОТОВИТЕЛИ",
                        type = "manufacturer",
                        items = manufacturers,
                        isExpanded = activeAccordion == "manufacturer",
                        onExpandedChange = { activeAccordion = if (it) "manufacturer" else null },
                        onEditItem = ::startEditItem,
                        onDeleteItem = ::deleteItem,
                        onAddNew = {
                            newItemValue = ""
                            showAddDialog = true
                            activeAccordion = "manufacturer"
                        }
                    )
                }

                // Аккордеон "СУДА"
                item {
                    DictionaryAccordion(
                        title = "СУДА",
                        type = "vessels",
                        items = vessels,
                        isExpanded = activeAccordion == "vessel",
                        onExpandedChange = { activeAccordion = if (it) "vessel" else null },
                        onEditItem = ::startEditItem,
                        onDeleteItem = ::deleteItem,
                        onAddNew = {
                            newItemValue = ""
                            showAddDialog = true
                            activeAccordion = "vessel"
                        }
                    )
                }
            }
        }
    }

    // Диалог редактирования
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = {
                showEditDialog = false
                editingItem = null
            },
            title = { Text("Редактирование") },
            text = {
                OutlinedTextField(
                    value = editDialogValue,
                    onValueChange = { editDialogValue = it },
                    label = { Text("Название") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = ::saveEditItem,
                    enabled = editDialogValue.isNotBlank()
                ) {
                    Text("СОХРАНИТЬ")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showEditDialog = false
                    editingItem = null
                }) {
                    Text("ОТМЕНА")
                }
            }
        )
    }

    // Диалог добавления
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                newItemValue = ""
            },
            title = { Text("Добавить новый элемент") },
            text = {
                OutlinedTextField(
                    value = newItemValue,
                    onValueChange = { newItemValue = it },
                    label = { Text("Название") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = { addNewItem(activeAccordion ?: "") },
                    enabled = newItemValue.isNotBlank()
                ) {
                    Text("ДОБАВИТЬ")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    newItemValue = ""
                }) {
                    Text("ОТМЕНА")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryAccordion(
    title: String,
    type: String,
    items: List<DictionaryItem>,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onEditItem: (DictionaryItem) -> Unit,
    onDeleteItem: (DictionaryItem) -> Unit,
    onAddNew: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            // Заголовок аккордеона
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandedChange(!isExpanded) }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$title (${items.size})",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Свернуть" else "Развернуть"
                )
            }

            // Контент аккордеона
            if (isExpanded) {
                Divider()

                if (items.isEmpty()) {
                    Text(
                        text = "Список пуст",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    ) {
                        items(items) { item ->
                            DictionaryItemRow(
                                item = item,
                                onEdit = { onEditItem(item) },
                                onDelete = { onDeleteItem(item) }
                            )
                        }
                    }
                }

                // Кнопка добавления
                TextButton(
                    onClick = onAddNew,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Добавить $title")
                }
            }
        }
    }
}

@Composable
fun DictionaryItemRow(
    item: DictionaryItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.value,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Использовано: ${item.usageCount} раз",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Row {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Редактировать",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = { showDeleteConfirm = true },
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

    // Диалог подтверждения удаления
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Удаление элемента") },
            text = { Text("Вы уверены, что хотите удалить \"${item.value}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    }
                ) {
                    Text("УДАЛИТЬ", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("ОТМЕНА")
                }
            }
        )
    }
}