package com.example.fishy.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.fishy.database.AppDatabase
import com.example.fishy.database.entities.Shipment
import com.example.fishy.viewmodels.ShipmentViewModel
import com.example.fishy.viewmodels.ShipmentViewModelFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val viewModel: ShipmentViewModel = viewModel(
        factory = ShipmentViewModelFactory(
            context = context,
            database = AppDatabase.getDatabase(context)
        )
    )

    val shipments by viewModel.allShipments.collectAsState(initial = emptyList())
    var searchText by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var shipmentToDelete by remember { mutableStateOf<Shipment?>(null) }

    // ФИЛЬТРУЕМ ОТГРУЗКИ ПО ПОИСКУ
    val filteredShipments = remember(shipments, searchText) {
        if (searchText.isEmpty()) {
            shipments
        } else {
            val query = searchText.lowercase()
            shipments.filter { shipment ->
                shipment.containerNumber.lowercase().contains(query) ||
                        shipment.truckNumber.lowercase().contains(query) ||
                        shipment.trailerNumber.lowercase().contains(query) ||
                        shipment.wagonNumber.lowercase().contains(query) ||
                        shipment.port.lowercase().contains(query) ||
                        shipment.vessel.lowercase().contains(query) ||
                        shipment.customer.lowercase().contains(query) ||
                        shipment.sealNumber.lowercase().contains(query) ||
                        shipment.id.toString().contains(query) ||
                        formatDate(shipment.createdAt).lowercase().contains(query)
            }
        }
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
                text = "АРХИВ ОТГРУЗОК",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // ПОЛЕ ПОИСКА
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                label = { Text("Поиск") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Поиск")
                },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = { searchText = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Очистить")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )

            // РЕЗУЛЬТАТЫ ПОИСКА
            Text(
                text = "Найдено: ${filteredShipments.size} из ${shipments.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (filteredShipments.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (searchText.isEmpty()) {
                            Text(
                                text = "📦",
                                fontSize = 64.sp,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            Text(
                                text = "Архив пуст",
                                color = Color(240, 240, 240),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Text(
                                text = "Здесь появятся сохраненные отгрузки",
                                color = Color(150, 150, 150),
                                fontSize = 16.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        } else {
                            Icon(
                                Icons.Default.SearchOff,
                                contentDescription = "Ничего не найдено",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Ничего не найдено",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = "По запросу: \"$searchText\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredShipments) { shipment ->
                        SimpleShipmentCard(
                            shipment = shipment,
                            onClick = {
                                navController.navigate("shipment_detail/${shipment.id}")
                            },
                            onDelete = {
                                shipmentToDelete = shipment
                                showDeleteDialog = true
                            },
                            onViewReport = {
                                // Навигация на экран отчета
                                navController.navigate("report/${shipment.id}")
                            }
                        )
                    }
                }
            }
        }
    }

    // Диалог подтверждения удаления
    if (showDeleteDialog && shipmentToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                shipmentToDelete = null
            },
            title = { Text("Удаление отгрузки") },
            text = { Text("Вы уверены, что хотите удалить эту отгрузку из архива?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        shipmentToDelete?.let { viewModel.deleteShipment(it) }
                        showDeleteDialog = false
                        shipmentToDelete = null
                    }
                ) {
                    Text("УДАЛИТЬ", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    shipmentToDelete = null
                }) {
                    Text("ОТМЕНА")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleShipmentCard(
    shipment: Shipment,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onViewReport: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Первая строка: тип отгрузки и номер
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Тип отгрузки
                val typeName = when (shipment.shipmentType) {
                    "mono" -> "Моноотгрузка"
                    "multi_port" -> "Мультипорт"
                    "multi_vehicle" -> "Мультитранспорт"
                    else -> "Отгрузка"
                }

                Text(
                    text = typeName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // ID отгрузки
                Text(
                    text = "#${shipment.id}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Основная информация
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Заказчик
                if (shipment.customer.isNotEmpty()) {
                    Text(
                        text = "Заказчик: ${shipment.customer}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // Порт
                if (shipment.port.isNotEmpty()) {
                    Text(
                        text = "Порт: ${shipment.port}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // Транспорт (по новой логике)
                val transportText = getTransportText(shipment)
                if (transportText.isNotEmpty()) {
                    Text(
                        text = "Транспорт: $transportText",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Статистика
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Видов: ${shipment.totalProductTypes}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Поддоны: ${shipment.totalPallets}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Места: ${shipment.totalPlaces}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Нижняя строка: кнопки по краям, дата по центру
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Кнопка отчета - СЛЕВА
                IconButton(
                    onClick = onViewReport,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = "Показать отчёт",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Дата и время - ПО ЦЕНТРУ
                Text(
                    text = formatDate(shipment.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                // Кнопка удаления - СПРАВА
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
}

// Простая логика отображения транспорта
private fun getTransportText(shipment: Shipment): String {
    return when {
        // Сначала контейнер
        shipment.containerNumber.isNotEmpty() -> "Контейнер ${shipment.containerNumber}"
        // Потом вагон
        shipment.wagonNumber.isNotEmpty() -> "Вагон ${shipment.wagonNumber}"
        // Потом авто и прицеп
        shipment.truckNumber.isNotEmpty() -> {
            if (shipment.trailerNumber.isNotEmpty()) {
                "Авто ${shipment.truckNumber}, Прицеп ${shipment.trailerNumber}"
            } else {
                "Авто ${shipment.truckNumber}"
            }
        }
        // Если ничего нет
        else -> ""
    }
}

private fun formatDate(date: Date): String {
    val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    return formatter.format(date)
}