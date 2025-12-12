package com.example.fishy.screens

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.fishy.theme.CardBackground
import com.example.fishy.viewmodels.ShipmentViewModel
import com.example.fishy.viewmodels.ShipmentViewModelFactory
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val viewModel: ShipmentViewModel = viewModel(
        factory = ShipmentViewModelFactory(context.applicationContext as Application)
    )

    val shipments by viewModel.allShipments.collectAsState(initial = emptyList())
    var searchText by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var shipmentToDelete by remember { mutableStateOf<com.example.fishy.database.entities.Shipment?>(null) }

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
                        ShipmentCard(
                            shipment = shipment,
                            onClick = {
                                navController.navigate("shipment_detail/${shipment.id}")
                            },
                            onDelete = {
                                shipmentToDelete = shipment
                                showDeleteDialog = true
                            }
                        )
                    }
                }
            }
        }

        // ДИАЛОГ ПОДТВЕРЖДЕНИЯ УДАЛЕНИЯ
        if (showDeleteDialog && shipmentToDelete != null) {
            AlertDialog(
                onDismissRequest = {
                    showDeleteDialog = false
                    shipmentToDelete = null
                },
                title = { Text("Удаление отгрузки") },
                text = { Text("Вы уверены, что хотите удалить эту отгрузку?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            shipmentToDelete?.let { viewModel.deleteShipment(it) }
                            showDeleteDialog = false
                            shipmentToDelete = null
                        }
                    ) {
                        Text("УДАЛИТЬ")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            shipmentToDelete = null
                        }
                    ) {
                        Text("ОТМЕНА")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShipmentCard(
    shipment: com.example.fishy.database.entities.Shipment,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = CardBackground
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Заголовок
            Text(
                text = getShipmentTitle(shipment),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Детали
            if (shipment.port.isNotEmpty()) {
                Text(
                    text = "Порт: ${shipment.port}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (shipment.vessel.isNotEmpty()) {
                Text(
                    text = "Судно: ${shipment.vessel}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (shipment.customer.isNotEmpty()) {
                Text(
                    text = "Заказчик: ${shipment.customer}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

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

            Spacer(modifier = Modifier.height(8.dp))

            // Дата и кнопка удаления
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDate(shipment.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

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

private fun getShipmentTitle(shipment: com.example.fishy.database.entities.Shipment): String {
    return when {
        shipment.containerNumber.isNotEmpty() -> "Контейнер: ${shipment.containerNumber}"
        shipment.truckNumber.isNotEmpty() -> "Авто: ${shipment.truckNumber}"
        shipment.wagonNumber.isNotEmpty() -> "Вагон: ${shipment.wagonNumber}"
        else -> "Отгрузка #${shipment.id}"
    }
}

private fun formatDate(date: Date): String {
    val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    return formatter.format(date)
}