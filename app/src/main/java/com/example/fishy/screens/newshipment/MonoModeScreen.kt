package com.example.fishy.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.fishy.database.entities.DictionaryItem
import com.example.fishy.ui.components.DictionaryAutocomplete
import com.example.fishy.utils.ValidationState
import com.example.fishy.viewmodels.ShipmentViewModel
import com.example.fishy.theme.Success

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonoModeScreen(
    viewModel: ShipmentViewModel,
    navController: NavController,
    customerDictionary: List<DictionaryItem>,
    portDictionary: List<DictionaryItem>,
    vesselDictionary: List<DictionaryItem>,
    productDictionary: List<DictionaryItem>,
    manufacturerDictionary: List<DictionaryItem>
) {
    val currentShipment by viewModel.currentShipment.collectAsState()
    val currentProducts by viewModel.currentProducts.collectAsState()
    val currentPallets by viewModel.currentPallets.collectAsState()
    val productRemainders by viewModel.productRemainders.collectAsState()
    val totalRemainder by viewModel.totalRemainder.collectAsState()
    val doubleControlStats by viewModel.doubleControlStats.collectAsState()
    val productDoubleControlStats by viewModel.productDoubleControlStats.collectAsState()
    var showSaveConfirmation by remember { mutableStateOf(false) }
    var expandedInfo by remember { mutableStateOf(true) }
    var expandedTransport by remember { mutableStateOf(true) }
    var expandedProducts by remember { mutableStateOf(true) }
    var expandedTotals by remember { mutableStateOf(true) }

    // Определяем, какие поля транспорта показывать
    val hasWagon = currentShipment.wagonNumber.isNotEmpty()
    val hasVehicle = currentShipment.containerNumber.isNotEmpty() ||
            currentShipment.truckNumber.isNotEmpty() ||
            currentShipment.trailerNumber.isNotEmpty()

    val showWagon = !hasVehicle || hasWagon
    val showVehicle = !hasWagon || hasVehicle

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Информация о погрузке
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ИНФОРМАЦИЯ О ПОГРУЗКЕ", style = MaterialTheme.typography.bodyLarge)
                        IconButton(onClick = { expandedInfo = !expandedInfo }) {
                            Icon(
                                if (expandedInfo) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                        }
                    }

                    if (expandedInfo) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            DictionaryAutocomplete(
                                value = currentShipment.customer,
                                onValueChange = { value ->
                                    viewModel.updateShipmentField("customer", value)
                                },
                                label = "Заказчик",
                                dictionaryType = "customer",
                                modifier = Modifier.fillMaxWidth(),
                                dictionaryItems = customerDictionary,
                                onAddToDictionary = { type, value ->
                                    viewModel.addDictionaryItem(type, value)
                                },
                                onSaveToDictionary = { value -> viewModel.saveDictionaryField("customer", value) }
                            )

                            DictionaryAutocomplete(
                                value = currentShipment.port,
                                onValueChange = { value ->
                                    viewModel.updateShipmentField("port", value)
                                },
                                label = "Порт",
                                dictionaryType = "port",
                                modifier = Modifier.fillMaxWidth(),
                                dictionaryItems = portDictionary,
                                onAddToDictionary = { type, value ->
                                    viewModel.addDictionaryItem(type, value)
                                },
                                onSaveToDictionary = { value -> viewModel.saveDictionaryField("port", value) }
                            )

                            DictionaryAutocomplete(
                                value = currentShipment.vessel,
                                onValueChange = { value ->
                                    viewModel.updateShipmentField("vessel", value)
                                },
                                label = "Судно",
                                dictionaryType = "vessel",
                                modifier = Modifier.fillMaxWidth(),
                                dictionaryItems = vesselDictionary,
                                onAddToDictionary = { type, value ->
                                    viewModel.addDictionaryItem(type, value)
                                },
                                onSaveToDictionary = { value -> viewModel.saveDictionaryField("vessel", value) }
                            )
                        }
                    }
                }
            }

            // Транспорт
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ТРАНСПОРТ", style = MaterialTheme.typography.bodyLarge)
                        IconButton(onClick = { expandedTransport = !expandedTransport }) {
                            Icon(
                                if (expandedTransport) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                        }
                    }

                    if (expandedTransport) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            // Состояния валидации
                            val containerValidation by viewModel.containerValidation.collectAsState()
                            val wagonValidation by viewModel.wagonValidation.collectAsState()

                            // Вагон (показывается только если нет авто/контейнера/прицепа)
                            if (showWagon) {
                                val hasWagonError = wagonValidation is ValidationState.INVALID ||
                                        wagonValidation is ValidationState.INVALID_WITH_SUGGESTION

                                OutlinedTextField(
                                    value = currentShipment.wagonNumber,
                                    onValueChange = { viewModel.updateShipmentField("wagon", it) },
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
                                    },
                                    supportingText = { null }
                                )
                            }

                            // Контейнер, Авто, Прицеп (показываются только если нет вагона)
                            if (showVehicle) {
                                val hasContainerError = containerValidation is ValidationState.INVALID ||
                                        containerValidation is ValidationState.INVALID_WITH_SUGGESTION

                                // Контейнер
                                OutlinedTextField(
                                    value = currentShipment.containerNumber,
                                    onValueChange = {
                                        viewModel.updateShipmentField("container", it.uppercase())
                                    },
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
                                    },
                                    supportingText = { null }
                                )

                                // Авто и Прицеп (без валидации)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = currentShipment.truckNumber,
                                        onValueChange = { viewModel.updateShipmentField("truck", it.uppercase())},
                                        label = { Text("Авто") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )

                                    OutlinedTextField(
                                        value = currentShipment.trailerNumber,
                                        onValueChange = { viewModel.updateShipmentField("trailer", it.uppercase())},
                                        label = { Text("Прицеп") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            // Пломба (всегда показывается)
                            OutlinedTextField(
                                value = currentShipment.sealNumber,
                                onValueChange = { viewModel.updateShipmentField("seal", it.uppercase())},
                                label = { Text("Пломба") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("ДВОЙНОЙ КОНТРОЛЬ", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = currentShipment.doubleControlEnabled,
                        onCheckedChange = { viewModel.toggleDoubleControl() }
                    )
                }
            }

            // Продукция
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ПРОДУКЦИЯ", style = MaterialTheme.typography.bodyLarge)
                        IconButton(onClick = { expandedProducts = !expandedProducts }) {
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
                            // Список продукции (перенесен вверх)
                            currentProducts.forEach { product ->
                                val palletsForProduct = currentPallets[product.id] ?: emptyList()
                                val productStats = productDoubleControlStats[product.id]

                                MonoProductItem(
                                    product = product,
                                    viewModel = viewModel,
                                    pallets = palletsForProduct,
                                    productStats = productStats,
                                    onDelete = { viewModel.deleteProductItem(product.id) },
                                    productDictionary = productDictionary,
                                    manufacturerDictionary = manufacturerDictionary
                                )
                            }

                            // Кнопка добавления вида продукции (перемещена вниз)
                            Button(
                                onClick = { viewModel.addProductItem() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Добавить вид продукции")
                            }
                        }
                    }
                }
            }

            // Итоги
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ИТОГИ", style = MaterialTheme.typography.bodyLarge)
                        IconButton(onClick = { expandedTotals = !expandedTotals }) {
                            Icon(
                                if (expandedTotals) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                        }
                    }

                    if (expandedTotals) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val totalPlaces = currentProducts.sumOf { it.placesCount }
                            val totalQuantity = currentProducts.sumOf { it.quantity }
                            val totalWeight = currentProducts.sumOf { it.totalWeight }
                            val totalPallets = currentProducts.sumOf { it.palletCount }

                            // Учитываем двойной контроль в расчетах
                            val actualPlaces = if (currentShipment.doubleControlEnabled) {
                                doubleControlStats.importedPlaces
                            } else {
                                totalPlaces
                            }

                            val remainder = totalQuantity - actualPlaces

                            Text("Видов продукции: ${currentProducts.size}")
                            Text("Загружено поддонов: $totalPallets")
                            Text("Загружено мест: $actualPlaces")
                            Text("Целевое количество: $totalQuantity")
                            Text("Общая масса: ${String.format("%.2f", totalWeight)} кг")

                            // Статус погрузки
                            when {
                                remainder > 0 -> {
                                    Text(
                                        text = "Недогруз: $remainder мест",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                remainder < 0 -> {
                                    Text(
                                        text = "Перегруз: ${-remainder} мест",
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                                else -> {
                                    Text(
                                        text = "✓ Погрузка завершена",
                                        color = Success
                                    )
                                }
                            }

                            // Статистика двойного контроля
                            if (currentShipment.doubleControlEnabled) {
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
                                            "Двойной контроль:",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            "Поддоны: ${doubleControlStats.importedPallets}/${doubleControlStats.totalPallets}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (doubleControlStats.importedPallets == doubleControlStats.totalPallets)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.error
                                        )
                                        Text(
                                            "Места: ${doubleControlStats.importedPlaces}/${doubleControlStats.totalPlaces}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (doubleControlStats.importedPlaces == doubleControlStats.totalPlaces)
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