package com.example.fishy.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import com.example.fishy.database.entities.MultiPort
import com.example.fishy.screens.newshipment.MultiTotals
import com.example.fishy.ui.components.DictionaryAutocomplete
import com.example.fishy.viewmodels.DoubleControlStats
import com.example.fishy.viewmodels.ShipmentViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiPortItem(
    port: MultiPort,
    viewModel: ShipmentViewModel,
    onDeletePort: () -> Unit,
    portDictionary: List<DictionaryItem>,
    productDictionary: List<DictionaryItem>,
    manufacturerDictionary: List<DictionaryItem>
) {
    var expanded by remember { mutableStateOf(true) }
    var expandedInfo by remember { mutableStateOf(true) }
    var expandedProducts by remember { mutableStateOf(true) }
    var expandedTotals by remember { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // ВЫЧИСЛЯЕМ ИТОГИ ДЛЯ ПОРТА С УЧЕТОМ ДВОЙНОГО КОНТРОЛЯ
    val portTotals = remember(port) {
        var totalProductTypes = 0
        var totalPallets = 0
        var totalPlaces = 0
        var totalWeight = 0.0
        var totalQuantity = 0

        // Если двойной контроль включен, считаем только завезенные места
        var importedPallets = 0
        var importedPlaces = 0

        port.products.forEach { product ->
            totalProductTypes++
            totalPallets += product.palletCount
            totalWeight += product.totalWeight
            totalQuantity += product.quantity

            // Если двойной контроль включен, считаем только завезенные места
            if (port.doubleControlEnabled) {
                val productImportedPlaces = product.pallets.sumOf { if (it.isImported) it.places else 0 }
                val productImportedPallets = product.pallets.count { it.isImported }
                importedPlaces += productImportedPlaces
                importedPallets += productImportedPallets
                totalPlaces += productImportedPlaces // Используем только завезенные
            } else {
                totalPlaces += product.placesCount
            }
        }

        val totalRemainder = if (port.doubleControlEnabled) {
            totalQuantity - importedPlaces
        } else {
            totalQuantity - totalPlaces
        }

        MultiTotals(
            totalProductTypes = totalProductTypes,
            totalPallets = totalPallets,
            totalPlaces = if (port.doubleControlEnabled) importedPlaces else totalPlaces,
            totalWeight = totalWeight,
            totalRemainder = totalRemainder
        )
    }

    // Статистика двойного контроля для этого порта
    val portStats = remember(port) {
        if (!port.doubleControlEnabled) {
            DoubleControlStats()
        } else {
            val allPallets = port.products.flatMap { it.pallets }
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
            title = { Text("Удаление порта") },
            text = { Text("Вы уверены, что хотите удалить этот порт?") },
            confirmButton = {
                TextButton(onClick = {
                    onDeletePort()
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

    // Главный аккордеон порта
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column {
            // ЗАГОЛОВОК АККОРДЕОНА ПОРТА
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (port.port.isNotEmpty()) "ПОРТ: ${port.port}" else "НОВЫЙ ПОРТ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (portTotals.totalRemainder == 0)
                            Color(0xFF4CAF50) // ЗЕЛЕНЫЙ
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                    if (port.products.isNotEmpty()) {
                        Text(
                            text = "${port.products.size} видов продукции • ${if (port.doubleControlEnabled) portTotals.totalPlaces else port.products.sumOf { it.placesCount }} мест",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (portTotals.totalRemainder == 0)
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
                            tint = if (portTotals.totalRemainder == 0)
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
                            contentDescription = "Удалить порт",
                            tint = if (portTotals.totalRemainder == 0)
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
                    // Секция: Информация о порте (внутренний аккордеон)
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
                                Text("ИНФОРМАЦИЯ О ПОРТЕ", style = MaterialTheme.typography.bodyLarge)
                                IconButton(
                                    onClick = { expandedInfo = !expandedInfo },
                                    modifier = Modifier.size(24.dp)
                                ) {
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
                                        value = port.port,
                                        onValueChange = { viewModel.updateMultiPort(port.id, "port", it) },
                                        label = "Порт",
                                        dictionaryType = "port",
                                        modifier = Modifier.fillMaxWidth(),
                                        dictionaryItems = portDictionary,
                                        onAddToDictionary = { type, value -> viewModel.addDictionaryItem(type, value) },
                                        onSaveToDictionary = { value -> viewModel.saveDictionaryField("port", value) }
                                    )

                                    // Двойной контроль для порта
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("ДВОЙНОЙ КОНТРОЛЬ", style = MaterialTheme.typography.bodyMedium)
                                        Switch(
                                            checked = port.doubleControlEnabled,
                                            onCheckedChange = { viewModel.toggleMultiPortDoubleControl(port.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Секция: Продукция в порту (внутренний аккордеон)
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
                                Text("ПРОДУКЦИЯ В ПОРТУ", style = MaterialTheme.typography.bodyLarge)
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
                                    port.products.forEach { product ->
                                        MultiPortProductItem(
                                            product = product,
                                            portId = port.id,
                                            viewModel = viewModel,
                                            doubleControlEnabled = port.doubleControlEnabled,
                                            productDictionary = productDictionary,
                                            manufacturerDictionary = manufacturerDictionary
                                        )
                                    }

                                    // Кнопка добавления продукции
                                    Button(
                                        onClick = { viewModel.addMultiPortProduct(port.id) },
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

                    // Секция: Итоги по порту (внутренний аккордеон)
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
                                Text("ИТОГИ ПО ПОРТУ", style = MaterialTheme.typography.bodyLarge)
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
                                        Text("${portTotals.totalProductTypes}")
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Поддонов:")
                                        Text("${portTotals.totalPallets}")
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Мест:")
                                        Text("${portTotals.totalPlaces}")
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Общая масса:")
                                        Text("${String.format("%.2f", portTotals.totalWeight)} кг")
                                    }

                                    // Статус загрузки
                                    when {
                                        portTotals.totalRemainder > 0 -> {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "Недогруз:",
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                                Text(
                                                    text = "${portTotals.totalRemainder} мест",
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                        portTotals.totalRemainder < 0 -> {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "Перегруз:",
                                                    color = MaterialTheme.colorScheme.tertiary
                                                )
                                                Text(
                                                    text = "${-portTotals.totalRemainder} мест",
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

                                    // Статистика двойного контроля для порта
                                    if (port.doubleControlEnabled) {
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
                                                    "Двойной контроль по порту:",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    "Поддоны: ${portStats.importedPallets}/${portStats.totalPallets}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (portStats.importedPallets == portStats.totalPallets)
                                                        MaterialTheme.colorScheme.primary
                                                    else
                                                        MaterialTheme.colorScheme.error
                                                )
                                                Text(
                                                    "Места: ${portStats.importedPlaces}/${portStats.totalPlaces}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (portStats.importedPlaces == portStats.totalPlaces)
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