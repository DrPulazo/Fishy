package com.example.fishy.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fishy.database.entities.DictionaryItem
import com.example.fishy.database.entities.Pallet
import com.example.fishy.database.entities.ProductItem
import com.example.fishy.theme.OnSurfaceVariant
import com.example.fishy.theme.Success
import com.example.fishy.ui.components.DictionaryAutocomplete
import com.example.fishy.viewmodels.DoubleControlStats
import com.example.fishy.viewmodels.ShipmentViewModel

@Composable
fun MonoProductItem(
    product: ProductItem,
    viewModel: ShipmentViewModel,
    pallets: List<Pallet>,
    productStats: DoubleControlStats?,
    onDelete: () -> Unit,
    productDictionary: List<DictionaryItem>,
    manufacturerDictionary: List<DictionaryItem>
) {
    var expanded by remember { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val currentShipment by viewModel.currentShipment.collectAsState()
    val productRemainders by viewModel.productRemainders.collectAsState()
    val remainder = productRemainders[product.id] ?: 0

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удаление продукции") },
            text = { Text("Вы уверены, что хотите удалить этот вид продукции?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
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
                        onValueChange = { viewModel.updateProductItem(product.id, "name", it) },
                        label = "Наименование",
                        dictionaryType = "product",
                        modifier = Modifier.fillMaxWidth(),
                        dictionaryItems = productDictionary,
                        onAddToDictionary = { type, value -> viewModel.addDictionaryItem(type, value) },
                        onSaveToDictionary = { value -> viewModel.saveProductDictionaryField("product", value) } // ДОБАВЛЕНО
                    )

                    OutlinedTextField(
                        value = product.batch,
                        onValueChange = { viewModel.updateProductItem(product.id, "batch", it) },
                        label = { Text("Партия") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    DictionaryAutocomplete(
                        value = product.manufacturer,
                        onValueChange = { viewModel.updateProductItem(product.id, "manufacturer", it) },
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
                                viewModel.updateProductItem(product.id, "packageWeight", weight)
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
                                viewModel.updateProductItem(product.id, "quantity", quantity)
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
                    if (pallets.isNotEmpty()) {
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
                                if (currentShipment.doubleControlEnabled) {
                                    Text(
                                        "Завезен",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Строки таблицы
                            pallets.forEach { pallet ->
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
                                            viewModel.updatePalletPlaces(product.id, pallet.id, places)
                                        },
                                        modifier = Modifier.weight(2f),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true
                                    )

                                    // Чекбокс завезен (если включен двойной контроль)
                                    if (currentShipment.doubleControlEnabled) {
                                        Checkbox(
                                            checked = pallet.isImported,
                                            onCheckedChange = {
                                                viewModel.togglePalletImported(product.id, pallet.id)
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
                            Text(text = "Места: ${pallets.sumOf { it.places }}/${product.quantity}")
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
                        if (currentShipment.doubleControlEnabled) {
                            val exported = pallets.count { it.isExported }
                            val imported = pallets.count { it.isImported }
                            Text(text = "Вывезено: $exported, Завезено: $imported", fontSize = 14.sp)
                        }
                    }
                    // Кнопка добавления поддона
                    Button(
                        onClick = {
                            viewModel.addPallet(product.id)
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