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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.fishy.database.AppDatabase
import com.example.fishy.database.entities.MultiPort
import com.example.fishy.database.entities.MultiVehicle
import com.example.fishy.database.entities.Shipment
import com.example.fishy.utils.DraftManager
import com.example.fishy.viewmodels.ShipmentViewModel
import com.example.fishy.viewmodels.ShipmentViewModelFactory
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DraftsScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val viewModel: ShipmentViewModel = viewModel(
        factory = ShipmentViewModelFactory(
            context = context,
            database = AppDatabase.getDatabase(context)
        )
    )
    val draftManager = DraftManager(context)
    val coroutineScope = rememberCoroutineScope()

    var draftData by remember { mutableStateOf<com.example.fishy.utils.DraftData?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        draftData = draftManager.loadDraft()
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
                text = "ЧЕРНОВИКИ",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (draftData == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "\uD83D\uDCCB",
                            fontSize = 64.sp,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Черновиков нет",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "Создайте новую отгрузку для появления черновика",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            } else {
                Card(
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
                        draftData?.let { draft ->
                            // Тип отгрузки с человеко-читаемыми названиями
                            val shipmentTypeName = when (draft.shipmentType) {
                                "mono" -> "Моноотгрузка"
                                "multi_port" -> "Мультипорт"
                                "multi_vehicle" -> "Мультитранспорт"
                                else -> draft.shipmentType
                            }

                            Text(
                                text = shipmentTypeName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            // Информация в зависимости от типа отгрузки
                            when (draft.shipmentType) {
                                "mono" -> {
                                    displayMonoInfo(draft.shipment, draft.multiPorts, draft.multiVehicles)
                                }
                                "multi_port" -> {
                                    displayMultiPortInfo(draft.shipment, draft.multiPorts)
                                }
                                "multi_vehicle" -> {
                                    displayMultiVehicleInfo(draft.shipment, draft.multiVehicles)
                                }
                            }

                            // Дата и время создания
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = formatDateTime(draft.shipment.createdAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )

                            // Кнопки действий
                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Кнопка ПРОДОЛЖИТЬ
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            viewModel.loadDraft()
                                            navController.navigate("new_shipment")
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Продолжить",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "ПРОДОЛЖИТЬ",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                // Кнопка УДАЛИТЬ
                                Button(
                                    onClick = { showDeleteConfirm = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    ),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Удалить",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "УДАЛИТЬ",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Диалог удаления
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Удаление черновика") },
            text = { Text("Черновик будет удален без возможности восстановления") },
            confirmButton = {
                TextButton(
                    onClick = {
                        draftManager.clearDraft()
                        draftData = null
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

@Composable
fun displayMonoInfo(
    shipment: Shipment,
    multiPorts: List<MultiPort>,
    multiVehicles: List<MultiVehicle>
) {
    Column {
        if (shipment.customer.isNotEmpty()) {
            Text(
                text = "Заказчик: ${shipment.customer}",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (shipment.port.isNotEmpty()) {
            Text(
                text = "Порт: ${shipment.port}",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        val transportInfo = getTransportDisplay(shipment)
        if (transportInfo.isNotEmpty()) {
            Text(
                text = "Транспорт: $transportInfo",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun displayMultiPortInfo(
    shipment: Shipment,
    multiPorts: List<MultiPort>
) {
    Column {
        if (shipment.customer.isNotEmpty()) {
            Text(
                text = "Заказчик: ${shipment.customer}",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // Список портов из multiPorts
        val ports = multiPorts.filter { it.port.isNotEmpty() }
        if (ports.isNotEmpty()) {
            val portsText = ports.joinToString(", ") { it.port }
            Text(
                text = "Порты: $portsText",
                style = MaterialTheme.typography.bodyMedium
            )
        } else if (shipment.port.isNotEmpty()) {
            Text(
                text = "Порт: ${shipment.port}",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        val transportInfo = getTransportDisplay(shipment)
        if (transportInfo.isNotEmpty()) {
            Text(
                text = "Транспорт: $transportInfo",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun displayMultiVehicleInfo(
    shipment: Shipment,
    multiVehicles: List<MultiVehicle>
) {
    Column {
        if (shipment.customer.isNotEmpty()) {
            Text(
                text = "Заказчик: ${shipment.customer}",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (shipment.port.isNotEmpty()) {
            Text(
                text = "Порт: ${shipment.port}",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // Список транспортов из multiVehicles
        val vehiclesInfo = getMultiVehiclesDisplay(multiVehicles)
        if (vehiclesInfo.isNotEmpty()) {
            Text(
                text = "Транспорты: $vehiclesInfo",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            val transportInfo = getTransportDisplay(shipment)
            if (transportInfo.isNotEmpty()) {
                Text(
                    text = "Транспорт: $transportInfo",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

fun getTransportDisplay(shipment: Shipment): String {
    return when {
        shipment.containerNumber.isNotEmpty() -> "Контейнер ${shipment.containerNumber}"
        shipment.wagonNumber.isNotEmpty() -> "Вагон ${shipment.wagonNumber}"
        shipment.truckNumber.isNotEmpty() -> {
            if (shipment.trailerNumber.isNotEmpty()) {
                "Авто ${shipment.truckNumber}, Прицеп ${shipment.trailerNumber}"
            } else {
                "Авто ${shipment.truckNumber}"
            }
        }
        else -> ""
    }
}

fun getMultiVehiclesDisplay(multiVehicles: List<MultiVehicle>): String {
    val vehicles = multiVehicles.filter {
        it.containerNumber.isNotEmpty() ||
                it.wagonNumber.isNotEmpty() ||
                it.truckNumber.isNotEmpty()
    }

    return vehicles.joinToString(", ") { vehicle ->
        when {
            vehicle.containerNumber.isNotEmpty() -> "Контейнер ${vehicle.containerNumber}"
            vehicle.wagonNumber.isNotEmpty() -> "Вагон ${vehicle.wagonNumber}"
            vehicle.truckNumber.isNotEmpty() -> {
                if (vehicle.trailerNumber.isNotEmpty()) {
                    "Авто ${vehicle.truckNumber} (${vehicle.trailerNumber})"
                } else {
                    "Авто ${vehicle.truckNumber}"
                }
            }
            else -> ""
        }
    }
}

fun formatDateTime(date: Date): String {
    val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    return formatter.format(date)
}