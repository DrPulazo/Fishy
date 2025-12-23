package com.example.fishy.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fishy.database.entities.DictionaryItem
import com.example.fishy.database.entities.MultiPortProduct
import com.example.fishy.theme.OnSurfaceVariant
import com.example.fishy.ui.components.DictionaryAutocomplete
import com.example.fishy.viewmodels.ShipmentViewModel
import com.example.fishy.viewmodels.DoubleControlStats
import com.example.fishy.theme.Success

@Composable
fun MultiPortProductItem(
    product: MultiPortProduct,
    portId: Long,
    viewModel: ShipmentViewModel,
    doubleControlEnabled: Boolean,
    productDictionary: List<DictionaryItem>,
    manufacturerDictionary: List<DictionaryItem>
) {
    var expanded by remember { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Вычисляем остаток для этого товара
    val remainder = remember(product, doubleControlEnabled) {
        if (doubleControlEnabled) {
            val importedPlaces = product.pallets.sumOf { if (it.isImported) it.places else 0 }
            product.quantity - importedPlaces
        } else {
            product.quantity - product.placesCount
        }
    }

    // Статистика двойного контроля для этого товара
    val productStats = remember(product, doubleControlEnabled) {
        if (!doubleControlEnabled) {
            DoubleControlStats()
        } else {
            val importedPallets = product.pallets.count { it.isImported }
            val importedPlaces = product.pallets.sumOf { if (it.isImported) it.places else 0 }

            DoubleControlStats(
                totalPallets = product.pallets.size,
                exportedPallets = product.pallets.size,
                importedPallets = importedPallets,
                totalPlaces = product.placesCount,
                exportedPlaces = product.placesCount,
                importedPlaces = importedPlaces
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удаление продукции") },
            text = { Text("Вы уверены, что хотите удалить этот вид продукции?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMultiPortProduct(portId, product.id)
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Заголовок
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (product.name.isNotEmpty()) {
                            if (product.manufacturer.isNotEmpty()) {
                                "${product.name} - ${product.manufacturer}"
                            } else {
                                product.name
                            }
                        } else {
                            "Новый товар"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (remainder == 0) Success else MaterialTheme.colorScheme.onSurface // ЗЕЛЕНЫЙ ПРИ ЗАВЕРШЕНИИ
                    )
                    if (product.quantity > 0) {
                        val status = when {
                            remainder > 0 -> "Остаток: $remainder мест"
                            remainder < 0 -> "Перегруз: ${-remainder} мест"
                            else -> "✓ Загружено"
                        }
                        val color = when {
                            remainder > 0 -> OnSurfaceVariant
                            remainder < 0 -> MaterialTheme.colorScheme.tertiary
                            else -> Color.Green
                        }
                        Text(
                            text = "$status (${product.placesCount}/${product.quantity})",
                            style = MaterialTheme.typography.bodySmall,
                            color = color
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
                            contentDescription = null
                        )
                    }
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Удалить")
                    }
                }
            }

            if (expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Поля товара
                    DictionaryAutocomplete(
                        value = product.name,
                        onValueChange = { viewModel.updateMultiPortProduct(portId, product.id, "name", it) },
                        label = "Наименование",
                        dictionaryType = "product",
                        modifier = Modifier.fillMaxWidth(),
                        dictionaryItems = productDictionary,
                        onAddToDictionary = { type, value -> viewModel.addDictionaryItem(type, value) },
                        onSaveToDictionary = { value -> viewModel.saveProductDictionaryField("product", value) } // ДОБАВЛЕНО
                    )

                    OutlinedTextField(
                        value = product.batch,
                        onValueChange = { viewModel.updateMultiPortProduct(portId, product.id, "batch", it) },
                        label = { Text("Партия") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    DictionaryAutocomplete(
                        value = product.manufacturer,
                        onValueChange = { viewModel.updateMultiPortProduct(portId, product.id, "manufacturer", it) },
                        label = "Изготовитель",
                        dictionaryType = "manufacturer",
                        modifier = Modifier.fillMaxWidth(),
                        dictionaryItems = manufacturerDictionary,
                        onAddToDictionary = { type, value -> viewModel.addDictionaryItem(type, value) },
                        onSaveToDictionary = { value -> viewModel.saveProductDictionaryField("manufacturer", value) } // ДОБАВЛЕНО
                    )

                    // Тара, Количество, Масса в одной строке
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        // Тара
                        OutlinedTextField(
                            value = if (product.packageWeight > 0) product.packageWeight.toString() else "",
                            onValueChange = { value ->
                                val weight = value.toDoubleOrNull() ?: 0.0
                                viewModel.updateMultiPortProduct(portId, product.id, "packageWeight", weight)
                            },
                            label = { Text("Тара", style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.weight(0.25f),
                            textStyle = MaterialTheme.typography.bodySmall,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )

                        // Количество
                        OutlinedTextField(
                            value = if (product.quantity > 0) product.quantity.toString() else "",
                            onValueChange = { value ->
                                val quantity = value.toIntOrNull() ?: 0
                                viewModel.updateMultiPortProduct(portId, product.id, "quantity", quantity)
                            },
                            label = { Text("Кол-во", style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.weight(0.35f),
                            textStyle = MaterialTheme.typography.bodySmall,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )

                        // Масса (только чтение)
                        OutlinedTextField(
                            value = String.format("%.1f", product.totalWeight),
                            onValueChange = {},
                            label = { Text("Масса", style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.weight(0.4f),
                            textStyle = MaterialTheme.typography.bodySmall,
                            readOnly = true,
                            singleLine = true,
                        )
                    }

                    // Поддоны в виде таблицы
                    if (product.pallets.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Заголовки таблицы
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "№",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "Количество мест",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(2f),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (doubleControlEnabled) {
                                    Text(
                                        "Завезен",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Строки таблицы
                            product.pallets.forEach { pallet ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Номер поддона
                                    Text(
                                        text = "${pallet.palletNumber}",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )

                                    // Количество мест
                                    OutlinedTextField(
                                        value = if (pallet.places == 0) "" else pallet.places.toString(),
                                        onValueChange = { value ->
                                            val places = if (value.isEmpty()) 0 else value.toIntOrNull() ?: 0
                                            viewModel.updateMultiPortPalletPlaces(portId, product.id, pallet.id, places)
                                        },
                                        modifier = Modifier.weight(2f),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true
                                    )

                                    // Чекбокс завезен (если включен двойной контроль)
                                    if (doubleControlEnabled) {
                                        Checkbox(
                                            checked = pallet.isImported,
                                            onCheckedChange = {
                                                viewModel.toggleMultiPortPalletImported(portId, product.id, pallet.id)
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Промежуточные итоги для товара
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .background(
                                color = when {
                                    remainder == 0 -> Color.Green.copy(alpha = 0.1f)
                                    remainder > 0 -> Color.Red.copy(alpha = 0.1f)
                                    else -> Color.Yellow.copy(alpha = 0.1f)
                                },
                                shape = MaterialTheme.shapes.small
                            )
                            .padding(8.dp)
                    ) {
                        // Основная статистика
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Места: ${product.placesCount}/${product.quantity}")
                        }

                        // Остаток/перегруз
                        Text(
                            text = when {
                                remainder > 0 -> "Недогруз: $remainder мест"
                                remainder < 0 -> "Перегруз: ${-remainder} мест"
                                else -> "✓ Норма"
                            },
                            color = when {
                                remainder > 0 -> Color.Red
                                remainder < 0 -> Color.Yellow
                                else -> Color.Green
                            }
                        )

                        // Статистика двойного контроля (если включен)
                        if (doubleControlEnabled) {
                            val exported = product.pallets.size
                            val imported = product.pallets.count { it.isImported }
                            Text(text = "Вывезено: $exported, Завезено: $imported", fontSize = 14.sp)
                        }
                    }

                    // Кнопка добавления поддона
                    Button(
                        onClick = {
                            viewModel.addMultiPortPallet(portId, product.id)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Добавить поддон")
                    }
                }
            }
        }
    }
}