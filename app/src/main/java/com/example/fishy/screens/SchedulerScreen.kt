package com.example.fishy.screens

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.fishy.database.entities.ScheduledShipment
import com.example.fishy.navigation.Screen
import com.example.fishy.viewmodels.SchedulerViewModel
import com.example.fishy.viewmodels.SchedulerViewModelFactory
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulerScreen(navController: NavController) {
    val context = LocalContext.current
    val viewModel: SchedulerViewModel = viewModel(
        factory = SchedulerViewModelFactory(context.applicationContext as Application)
    )

    val allScheduledShipments by viewModel.allScheduledShipments.collectAsState(emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var editingShipment by remember { mutableStateOf<ScheduledShipment?>(null) }
    var showChecklistDialog by remember { mutableStateOf<Long?>(null) }

    // Ключ для обновления статусов при возврате на экран
    var refreshKey by remember { mutableStateOf(0) }

    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Планировщик отгрузок") },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Добавить отгрузку")
                    }
                }
            )
        },
        floatingActionButton = {
            if (allScheduledShipments.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (allScheduledShipments.isEmpty()) {
                EmptyStateView(onAddClick = { showAddDialog = true })
            } else {
                ShipmentsListView(
                    shipments = allScheduledShipments,
                    viewModel = viewModel,
                    refreshKey = refreshKey,
                    onEditClick = { editingShipment = it },
                    onStartClick = { shipment ->
                        // ИСПРАВЛЕНО: Используем правильный маршрут для запланированной отгрузки
                        navController.navigate(Screen.NewShipmentFromScheduled.createRoute(shipment.id))
                    },
                    onChecklistClick = { shipmentId ->
                        showChecklistDialog = shipmentId
                    },
                    onDuplicateClick = { shipment ->
                        viewModel.duplicateScheduledShipment(shipment)
                    },
                    onDeleteClick = { shipment ->
                        viewModel.deleteScheduledShipment(shipment)
                    }
                )
            }
        }
    }

    // Диалог добавления/редактирования
    if (showAddDialog || editingShipment != null) {
        AddEditScheduledShipmentDialog(
            shipment = editingShipment,
            onDismiss = {
                showAddDialog = false
                editingShipment = null
            },
            onSave = { newShipment ->
                coroutineScope.launch {
                    if (editingShipment != null) {
                        // ВАЖНО: Сохраняем отгрузку с сохранением существующего ID
                        viewModel.updateScheduledShipment(newShipment)
                        // НЕ удаляем и не сбрасываем чеклист - он остается привязанным к этому ID
                    } else {
                        val id = viewModel.insertScheduledShipment(newShipment)
                        // НЕ создаем чеклист по умолчанию
                    }
                    // Увеличиваем ключ обновления
                    refreshKey++
                }
                showAddDialog = false
                editingShipment = null
            }
        )
    }

    // Диалог чеклиста
    showChecklistDialog?.let { shipmentId ->
        ChecklistDialog(
            shipmentId = shipmentId,
            onDismiss = {
                showChecklistDialog = null
                // Увеличиваем ключ обновления при закрытии диалога
                refreshKey++
            },
            viewModel = viewModel
        )
    }
}

@Composable
fun EmptyStateView(onAddClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Schedule,
            contentDescription = "Нет отгрузок",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Нет запланированных отгрузок",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Добавьте первую отгрузку в планировщик",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onAddClick) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Добавить отгрузку")
        }
    }
}

@Composable
fun ShipmentsListView(
    shipments: List<ScheduledShipment>,
    viewModel: SchedulerViewModel,
    refreshKey: Int,
    onEditClick: (ScheduledShipment) -> Unit,
    onStartClick: (ScheduledShipment) -> Unit,
    onChecklistClick: (Long) -> Unit,
    onDuplicateClick: (ScheduledShipment) -> Unit,
    onDeleteClick: (ScheduledShipment) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(shipments) { shipment ->
            ScheduledShipmentCard(
                shipment = shipment,
                viewModel = viewModel,
                refreshKey = refreshKey,
                onEditClick = { onEditClick(shipment) },
                onStartClick = { onStartClick(shipment) },
                onChecklistClick = { onChecklistClick(shipment.id) },
                onDuplicateClick = { onDuplicateClick(shipment) },
                onDeleteClick = { onDeleteClick(shipment) }
            )
        }
    }
}

@Composable
fun ScheduledShipmentCard(
    shipment: ScheduledShipment,
    viewModel: SchedulerViewModel,
    refreshKey: Int,
    onEditClick: () -> Unit,
    onStartClick: () -> Unit,
    onChecklistClick: () -> Unit,
    onDuplicateClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var checklistStatus by remember { mutableStateOf<ChecklistStatus?>(null) }

    val coroutineScope = rememberCoroutineScope()

    // Загружаем статус чеклиста при создании карточки и при изменении refreshKey
    LaunchedEffect(shipment.id, refreshKey) {
        coroutineScope.launch {
            checklistStatus = viewModel.getChecklistStatus(shipment.id)
        }
    }

    // Форматирование даты с днем недели
    val dateFormat = SimpleDateFormat("dd.MM.yyyy (EEE)", Locale.getDefault())
    val dateStr = dateFormat.format(shipment.scheduledDate)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Заголовок карточки
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Дата и время
                    Text(
                        text = "$dateStr, ${shipment.scheduledTime}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    // Заказчик
                    Text(
                        text = shipment.customer.ifEmpty { "Без названия" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // Меню действий
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Действия")
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Редактировать") },
                            onClick = {
                                onEditClick()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Начать отгрузку") },
                            onClick = {
                                onStartClick()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Дублировать") },
                            onClick = {
                                onDuplicateClick()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.ContentCopy, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Чек-лист") },
                            onClick = {
                                onChecklistClick()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Checklist, contentDescription = null)
                            }
                        )
                        Divider()
                        DropdownMenuItem(
                            text = { Text("Удалить", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                onDeleteClick()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    }
                }
            }

            Divider(modifier = Modifier.padding(vertical = 12.dp))

            // Детали отгрузки
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Тип отгрузки
                Row {
                    Text(
                        text = "Тип: ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = when (shipment.shipmentType) {
                            "mono" -> "Моноотгрузка"
                            "multi_port" -> "Мультипорт"
                            "multi_vehicle" -> "Мультитранспорт"
                            else -> "Неизвестно"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Судно
                if (shipment.vessel.isNotEmpty()) {
                    Row {
                        Text(
                            text = "Судно: ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = shipment.vessel,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Для мультипорта: порты и время
                if (shipment.shipmentType == "multi_port" && shipment.ports.isNotEmpty()) {
                    shipment.ports.forEachIndexed { index, port ->
                        Row {
                            Text(
                                text = "Порт ${index + 1}: ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = port,
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (index < shipment.portTimes.size) {
                                Text(
                                    text = " (${shipment.portTimes[index]})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // Для моно и мультитранспорта: общий порт
                if ((shipment.shipmentType == "mono" || shipment.shipmentType == "multi_vehicle") &&
                    shipment.port.isNotEmpty()) {
                    Row {
                        Text(
                            text = "Порт: ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = shipment.port,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Для мультиавто: количество транспорта
                if (shipment.shipmentType == "multi_vehicle") {
                    Row {
                        Text(
                            text = "Транспорт: ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "${shipment.vehicleCount} ед.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // Тип транспорта
                    val transportType = when {
                        shipment.containerNumber.isNotEmpty() -> "Контейнер"
                        shipment.wagonNumber.isNotEmpty() -> "Вагон"
                        shipment.truckNumber.isNotEmpty() -> "Авто"
                        else -> ""
                    }
                    if (transportType.isNotEmpty()) {
                        Row {
                            Text(
                                text = "Тип: ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = transportType,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                // Общая масса
                if (shipment.totalWeight > 0) {
                    Row {
                        Text(
                            text = "Масса: ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "${String.format("%.1f", shipment.totalWeight)} кг",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Продукция
                if (shipment.productName.isNotEmpty()) {
                    Row {
                        Text(
                            text = "Продукция: ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = shipment.productName,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Divider(modifier = Modifier.padding(vertical = 12.dp))

            // Футер карточки
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Статус чеклиста
                val status = checklistStatus ?: ChecklistStatus.EMPTY

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Цветная точка
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                color = when (status) {
                                    ChecklistStatus.COMPLETED -> Color(0xFF4CAF50) // Зеленый
                                    ChecklistStatus.PARTIAL -> Color(0xFFFFC107)   // Желтый
                                    ChecklistStatus.NONE -> Color(0xFFF44336)      // Красный
                                    ChecklistStatus.EMPTY -> Color(0xFF9E9E9E)     // Серый
                                },
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                    Text(
                        text = when (status) {
                            ChecklistStatus.COMPLETED -> "Чек-лист выполнен"
                            ChecklistStatus.PARTIAL -> "Чек-лист выполнен частично"
                            ChecklistStatus.NONE -> "Чек-лист не выполнен"
                            ChecklistStatus.EMPTY -> "Нет чек-листа"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                // Кнопка начала
                Button(
                    onClick = onStartClick,
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Начать",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Начать")
                }
            }
        }
    }
}

enum class ChecklistStatus {
    COMPLETED, PARTIAL, NONE, EMPTY
}