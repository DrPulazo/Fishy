package com.example.fishy.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.fishy.database.entities.DictionaryItem
import com.example.fishy.database.entities.MultiVehicle
import com.example.fishy.screens.newshipment.MultiTotals
import com.example.fishy.utils.ContainerWagonValidator
import com.example.fishy.utils.ValidationState
import com.example.fishy.viewmodels.ShipmentViewModel
import com.example.fishy.viewmodels.DoubleControlStats

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiVehicleItem(
    vehicle: MultiVehicle,
    viewModel: ShipmentViewModel,
    onDeleteVehicle: () -> Unit,
    productDictionary: List<DictionaryItem>,
    manufacturerDictionary: List<DictionaryItem>
) {
    var expanded by remember { mutableStateOf(true) }
    var expandedTransport by remember { mutableStateOf(true) }
    var expandedProducts by remember { mutableStateOf(true) }
    var expandedTotals by remember { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Формирование названия транспорта
    val vehicleName = remember(vehicle) {
        val parts = mutableListOf<String>()

        if (vehicle.wagonNumber.isNotEmpty()) {
            parts.add("Вагон: ${vehicle.wagonNumber}")
        } else {
            if (vehicle.containerNumber.isNotEmpty()) {
                parts.add("Контейнер: ${vehicle.containerNumber}")
            }
            if (vehicle.truckNumber.isNotEmpty()) {
                parts.add("Авто: ${vehicle.truckNumber}")
            }
            if (vehicle.trailerNumber.isNotEmpty() && vehicle.containerNumber.isEmpty()) {
                parts.add("Прицеп: ${vehicle.trailerNumber}")
            }
        }

        if (parts.isEmpty()) "НОВЫЙ ТРАНСПОРТ" else parts.joinToString(" • ")
    }

    // Получаем состояния валидации для этого транспорта
    val containerValidation = ContainerWagonValidator.validateContainerNumberLive(vehicle.containerNumber)
    val wagonValidation = ContainerWagonValidator.validateWagonNumberLive(vehicle.wagonNumber)

    val hasContainerError = containerValidation is ValidationState.INVALID ||
            containerValidation is ValidationState.INVALID_WITH_SUGGESTION
    val hasWagonError = wagonValidation is ValidationState.INVALID ||
            wagonValidation is ValidationState.INVALID_WITH_SUGGESTION

    // Определяем, какие поля транспорта показывать
    val hasWagon = vehicle.wagonNumber.isNotEmpty()
    val hasVehicle = vehicle.containerNumber.isNotEmpty() ||
            vehicle.truckNumber.isNotEmpty() ||
            vehicle.trailerNumber.isNotEmpty()
    val showWagon = !hasVehicle || hasWagon
    val showVehicle = !hasWagon || hasVehicle

    // ВЫЧИСЛЯЕМ ИТОГИ ДЛЯ ТРАНСПОРТА С УЧЕТОМ ДВОЙНОГО КОНТРОЛЯ
    val vehicleTotals = remember(vehicle) {
        var totalProductTypes = 0
        var totalPallets = 0
        var totalPlaces = 0
        var totalWeight = 0.0
        var totalQuantity = 0

        // Если двойной контроль включен, считаем только завезенные места
        var importedPallets = 0
        var importedPlaces = 0

        vehicle.products.forEach { product ->
            totalProductTypes++
            totalPallets += product.palletCount
            totalWeight += product.totalWeight
            totalQuantity += product.quantity

            // Если двойной контроль включен, считаем только завезенные места
            if (vehicle.doubleControlEnabled) {
                val productImportedPlaces = product.pallets.sumOf { if (it.isImported) it.places else 0 }
                val productImportedPallets = product.pallets.count { it.isImported }
                importedPlaces += productImportedPlaces
                importedPallets += productImportedPallets
                totalPlaces += productImportedPlaces // Используем только завезенные
            } else {
                totalPlaces += product.placesCount
            }
        }

        val totalRemainder = if (vehicle.doubleControlEnabled) {
            totalQuantity - importedPlaces
        } else {
            totalQuantity - totalPlaces
        }

        MultiTotals(
            totalProductTypes = totalProductTypes,
            totalPallets = totalPallets,
            totalPlaces = if (vehicle.doubleControlEnabled) importedPlaces else totalPlaces,
            totalWeight = totalWeight,
            totalRemainder = totalRemainder
        )
    }

    // Вычисляем статистику для этого транспорта
    val vehicleStats = remember(vehicle) {
        if (!vehicle.doubleControlEnabled) {
            DoubleControlStats()
        } else {
            val allPallets = vehicle.products.flatMap { it.pallets }
            val totalPallets = allPallets.size
            val importedPallets = allPallets.count { it.isImported }
            val totalPlaces = allPallets.sumOf { it.places }
            val importedPlaces = allPallets.sumOf { if (it.isImported) it.places else 0 }

            DoubleControlStats(
                totalPallets = totalPallets,
                exportedPallets = totalPallets,
                importedPallets = importedPallets,
                totalPlaces = totalPlaces,
                exportedPlaces = totalPlaces,
                importedPlaces = importedPlaces
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удаление транспорта") },
            text = { Text("Вы уверены, что хотите удалить этот транспорт?") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteVehicle()
                    showDeleteDialog = false
                }) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    // Главный аккордеон транспорта
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column {
            // ЗАГОЛОВОК АККОРДЕОНА ТРАНСПОРТА
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = vehicleName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (vehicleTotals.totalRemainder == 0)
                            Color(0xFF4CAF50) // ЗЕЛЕНЫЙ
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                    if (vehicle.products.isNotEmpty()) {
                        Text(
                            text = "${vehicle.products.size} видов продукции • ${if (vehicle.doubleControlEnabled) vehicleTotals.totalPlaces else vehicle.products.sumOf { it.placesCount }} мест",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (vehicleTotals.totalRemainder == 0)
                                Color(0xFF4CAF50) // ЗЕЛЕНЫЙ
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row {
                    IconButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = if (vehicleTotals.totalRemainder == 0)
                                Color(0xFF4CAF50) // ЗЕЛЕНЫЙ
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Удалить транспорт",
                            tint = if (vehicleTotals.totalRemainder == 0)
                                Color(0xFF4CAF50) // ЗЕЛЕНЫЙ
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // СОДЕРЖИМОЕ АККОРДЕОНА (показывается только если expanded = true)
            if (expanded) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    // Секция: Транспорт (внутренний аккордеон)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("ТРАНСПОРТ", style = MaterialTheme.typography.bodyLarge)
                                IconButton(
                                    onClick = { expandedTransport = !expandedTransport },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        if (expandedTransport) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null
                                    )
                                }
                            }

                            if (expandedTransport) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Вагон (показывается только если нет авто/контейнера/прицепа)
                                    if (showWagon) {
                                        val hasWagonError = wagonValidation is ValidationState.INVALID ||
                                                wagonValidation is ValidationState.INVALID_WITH_SUGGESTION

                                        OutlinedTextField(
                                            value = vehicle.wagonNumber,
                                            onValueChange = { viewModel.updateMultiVehicleWithValidation(vehicle.id, "wagon", it) },
                                            label = { Text("Вагон") },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            isError = hasWagonError,
                                            trailingIcon = {
                                                if (hasWagonError) {
                                                    Icon(
                                                        Icons.Default.Error,
                                                        contentDescription = "Ошибка",
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                        )
                                    }

                                    // Контейнер, Авто, Прицеп (показываются только если нет вагона)
                                    if (showVehicle) {
                                        val hasContainerError = containerValidation is ValidationState.INVALID ||
                                                containerValidation is ValidationState.INVALID_WITH_SUGGESTION

                                        // Контейнер
                                        OutlinedTextField(
                                            value = vehicle.containerNumber,
                                            onValueChange = { viewModel.updateMultiVehicleWithValidation(vehicle.id, "container", it.uppercase())},
                                            label = { Text("Контейнер") },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            isError = hasContainerError,
                                            trailingIcon = {
                                                if (hasContainerError) {
                                                    Icon(
                                                        Icons.Default.Error,
                                                        contentDescription = "Ошибка",
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                        )

                                        // Авто и Прицеп
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            OutlinedTextField(
                                                value = vehicle.truckNumber,
                                                onValueChange = { viewModel.updateMultiVehicleWithValidation(vehicle.id, "truck", it.uppercase())},
                                                label = { Text("Авто") },
                                                modifier = Modifier.weight(1f),
                                                singleLine = true
                                            )

                                            OutlinedTextField(
                                                value = vehicle.trailerNumber,
                                                onValueChange = { viewModel.updateMultiVehicleWithValidation(vehicle.id, "trailer", it.uppercase())},
                                                label = { Text("Прицеп") },
                                                modifier = Modifier.weight(1f),
                                                singleLine = true
                                            )
                                        }
                                    }

                                    // Пломба
                                    OutlinedTextField(
                                        value = vehicle.sealNumber,
                                        onValueChange = { viewModel.updateMultiVehicleWithValidation(vehicle.id, "seal", it) },
                                        label = { Text("Пломба") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )

                                    // Двойной контроль для транспорта
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("ДВОЙНОЙ КОНТРОЛЬ", style = MaterialTheme.typography.bodyMedium)
                                        Switch(
                                            checked = vehicle.doubleControlEnabled,
                                            onCheckedChange = { viewModel.toggleMultiVehicleDoubleControl(vehicle.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Секция: Продукция в транспорте (внутренний аккордеон)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("ПРОДУКЦИЯ В ТРАНСПОРТЕ", style = MaterialTheme.typography.bodyLarge)
                                IconButton(
                                    onClick = { expandedProducts = !expandedProducts },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        if (expandedProducts) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null
                                    )
                                }
                            }

                            if (expandedProducts) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // Список продукции
                                    vehicle.products.forEach { product ->
                                        MultiVehicleProductItem(
                                            product = product,
                                            vehicleId = vehicle.id,
                                            viewModel = viewModel,
                                            doubleControlEnabled = vehicle.doubleControlEnabled,
                                            productDictionary = productDictionary,
                                            manufacturerDictionary = manufacturerDictionary
                                        )
                                    }

                                    // Кнопка добавления продукции
                                    Button(
                                        onClick = { viewModel.addMultiVehicleProduct(vehicle.id) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onSurface
                                        )
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Добавить продукцию")
                                        Spacer(Modifier.width(8.dp))
                                        Text("Добавить вид продукции")
                                    }
                                }
                            }
                        }
                    }

                    // Секция: Итоги по транспорту (внутренний аккордеон)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("ИТОГИ ПО ТРАНСПОРТУ", style = MaterialTheme.typography.bodyLarge)
                                IconButton(
                                    onClick = { expandedTotals = !expandedTotals },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        if (expandedTotals) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null
                                    )
                                }
                            }

                            if (expandedTotals) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Видов продукции:")
                                        Text("${vehicleTotals.totalProductTypes}")
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Поддонов:")
                                        Text("${vehicleTotals.totalPallets}")
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Мест:")
                                        Text("${vehicleTotals.totalPlaces}")
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Общая масса:")
                                        Text("${String.format("%.2f", vehicleTotals.totalWeight)} кг")
                                    }

                                    // Статус загрузки
                                    when {
                                        vehicleTotals.totalRemainder > 0 -> {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "Недогруз:",
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                                Text(
                                                    text = "${vehicleTotals.totalRemainder} мест",
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                        vehicleTotals.totalRemainder < 0 -> {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "Перегруз:",
                                                    color = MaterialTheme.colorScheme.tertiary
                                                )
                                                Text(
                                                    text = "${-vehicleTotals.totalRemainder} мест",
                                                    color = MaterialTheme.colorScheme.tertiary
                                                )
                                            }
                                        }
                                        else -> {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "✓ Погрузка завершена",
                                                    color = Color(0xFF4CAF50) // ЗЕЛЕНЫЙ
                                                )
                                            }
                                        }
                                    }

                                    // Статистика двойного контроля для транспорта
                                    if (vehicle.doubleControlEnabled) {
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    "Двойной контроль по транспорту:",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    "Поддоны: ${vehicleStats.importedPallets}/${vehicleStats.totalPallets}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (vehicleStats.importedPallets == vehicleStats.totalPallets)
                                                        MaterialTheme.colorScheme.primary
                                                    else
                                                        MaterialTheme.colorScheme.error
                                                )
                                                Text(
                                                    "Места: ${vehicleStats.importedPlaces}/${vehicleStats.totalPlaces}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (vehicleStats.importedPlaces == vehicleStats.totalPlaces)
                                                        MaterialTheme.colorScheme.primary
                                                    else
                                                        MaterialTheme.colorScheme.error
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
        }
    }
}