package com.example.fishy.screens

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.fishy.theme.CardBackground
import com.example.fishy.theme.ErrorColor
import com.example.fishy.theme.SuccessColor
import com.example.fishy.viewmodels.ShipmentViewModel
import com.example.fishy.viewmodels.ShipmentViewModelFactory
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import com.example.fishy.utils.DraftManager

@Composable
fun NewShipmentScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val viewModel: ShipmentViewModel = viewModel(
        factory = ShipmentViewModelFactory(context.applicationContext as Application)
    )

    // ========== АВТОСОХРАНЕНИЕ ==========
    val draftManager = remember { DraftManager(context) }

    // Загрузка черновика при первом открытии
    LaunchedEffect(Unit) {
        val draft = draftManager.loadDraft()
        draft?.let {
            viewModel.updateShipmentField("container", it.containerNumber)
            viewModel.updateShipmentField("truck", it.truckNumber)
            viewModel.updateShipmentField("trailer", it.trailerNumber)
            viewModel.updateShipmentField("wagon", it.wagonNumber)
            viewModel.updateShipmentField("seal", it.sealNumber)
            viewModel.updateShipmentField("port", it.port)
            viewModel.updateShipmentField("vessel", it.vessel)
            viewModel.updateShipmentField("customer", it.customer)
        }
    }

    val currentShipment by viewModel.currentShipment.collectAsState()
    val currentProducts by viewModel.currentProducts.collectAsState()
    val currentPallets by viewModel.currentPallets.collectAsState()
    val productRemainders by viewModel.productRemainders.collectAsState()
    val totalRemainder by viewModel.totalRemainder.collectAsState()
    var expandedInfo by remember { mutableStateOf(true) }
    var expandedProducts by remember { mutableStateOf(true) }
    var activeProductId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(currentProducts) {
        if (activeProductId == null && currentProducts.isNotEmpty()) {
            activeProductId = currentProducts.first().id
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Секция информации о погрузке
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = CardBackground
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
                                Text(
                                    text = "ИНФОРМАЦИЯ О ПОГРУЗКЕ",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                IconButton(
                                    onClick = { expandedInfo = !expandedInfo }
                                ) {
                                    Icon(
                                        imageVector = if (expandedInfo) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (expandedInfo) "Свернуть" else "Развернуть"
                                    )
                                }
                            }

                            if (expandedInfo) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedTextField(
                                        value = currentShipment.containerNumber,
                                        onValueChange = { viewModel.updateShipmentField("container", it) },
                                        label = { Text("Номер контейнера") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )

                                    OutlinedTextField(
                                        value = currentShipment.truckNumber,
                                        onValueChange = { viewModel.updateShipmentField("truck", it) },
                                        label = { Text("Номер авто") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )

                                    OutlinedTextField(
                                        value = currentShipment.trailerNumber,
                                        onValueChange = { viewModel.updateShipmentField("trailer", it) },
                                        label = { Text("Номер прицепа") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )

                                    OutlinedTextField(
                                        value = currentShipment.wagonNumber,
                                        onValueChange = { viewModel.updateShipmentField("wagon", it) },
                                        label = { Text("Номер вагона") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )

                                    OutlinedTextField(
                                        value = currentShipment.sealNumber,
                                        onValueChange = { viewModel.updateShipmentField("seal", it) },
                                        label = { Text("Номер пломбы") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )

                                    OutlinedTextField(
                                        value = currentShipment.port,
                                        onValueChange = { viewModel.updateShipmentField("port", it) },
                                        label = { Text("Порт") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )

                                    OutlinedTextField(
                                        value = currentShipment.vessel,
                                        onValueChange = { viewModel.updateShipmentField("vessel", it) },
                                        label = { Text("Судно") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )

                                    OutlinedTextField(
                                        value = currentShipment.customer,
                                        onValueChange = { viewModel.updateShipmentField("customer", it) },
                                        label = { Text("Заказчик") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }
                            }
                        }
                    }
                }

                // Секция продукции
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = CardBackground
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
                                Text(
                                    text = "ПРОДУКЦИЯ",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                IconButton(
                                    onClick = { expandedProducts = !expandedProducts }
                                ) {
                                    Icon(
                                        imageVector = if (expandedProducts) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (expandedProducts) "Свернуть" else "Развернуть"
                                    )
                                }
                            }

                            if (expandedProducts) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // Кнопка добавления продукции
                                    Button(
                                        onClick = { viewModel.addProductItem() },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Добавить вид продукции")
                                    }

                                    // Список продукции
                                    currentProducts.forEachIndexed { idx, product ->
                                        ProductItemCard(
                                            product = product,
                                            index = idx + 1,
                                            pallets = currentPallets[product.id] ?: emptyList(),
                                            onUpdate = { field, value ->
                                                viewModel.updateProductItem(product.id, field, value)
                                            },
                                            onDelete = {
                                                viewModel.deleteProductItem(product.id)
                                            },
                                            onAddPallet = {
                                                viewModel.addPallet(product.id)
                                                activeProductId = product.id
                                            },
                                            onUpdatePallet = { palletId, places ->
                                                viewModel.updatePalletPlaces(product.id, palletId, places)
                                            },
                                            onDeletePallet = { palletId ->
                                                viewModel.deletePallet(product.id, palletId)
                                            },
                                            isActive = product.id == activeProductId,
                                            onSetActive = { activeProductId = product.id }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Итоги
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = CardBackground
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "ИТОГИ",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // Индивидуальные итоги по видам продукции
                            currentProducts.forEachIndexed { idx, product ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color.Transparent
                                    ),
                                    border = CardDefaults.outlinedCardBorder()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp)
                                    ) {
                                        // Динамическое наименование
                                        val productDisplayName = buildString {
                                            if (product.name.isNotEmpty()) {
                                                append(product.name)
                                                if (product.manufacturer.isNotEmpty()) {
                                                    append(" - ${product.manufacturer}")
                                                }
                                            } else {
                                                append("Продукция #${idx + 1}")
                                            }
                                        }

                                        Text(
                                            text = productDisplayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )

                                        // Изменено: каждая графа на отдельной строке
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text("Поддоны: ${product.palletCount}")
                                            Text("Места: ${product.placesCount}")
                                            Text("Масса: ${String.format("%.2f", product.totalWeight)} кг")
                                        }

                                        // Индивидуальный остаток для каждого продукта - перегруз оранжевым
                                        val productRemainder = product.quantity - product.placesCount
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            Text(
                                                text = if (productRemainder >= 0)
                                                    "Остаток: $productRemainder мест"
                                                else
                                                    "Перегруз: ${-productRemainder} мест",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (productRemainder < 0) Color(0xFFFF9800) else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            // Общие итоги
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.Transparent
                                ),
                                border = CardDefaults.outlinedCardBorder()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "ОБЩИЕ ИТОГИ",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )

                                    val totalProductTypes = currentProducts.size
                                    val totalPallets = currentProducts.sumOf { it.palletCount }
                                    val totalPlaces = currentProducts.sumOf { it.placesCount }
                                    val totalWeight = currentProducts.sumOf { it.totalWeight }
                                    val totalQuantity = currentProducts.sumOf { it.quantity }

                                    Text("Видов продукции: $totalProductTypes")
                                    Text("Всего поддонов: $totalPallets")
                                    Text("Всего мест: $totalPlaces")
                                    Text("Общая масса: ${String.format("%.2f", totalWeight)} кг")

                                    // Сравнение мест
                                    val placesMatch = totalPlaces == totalQuantity
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("Места: ")

                                        // Иконка совпадения
                                        Icon(
                                            imageVector = if (placesMatch) Icons.Default.CheckCircle else Icons.Default.Warning,
                                            contentDescription = if (placesMatch) "Совпадают" else "Не совпадают",
                                            tint = if (placesMatch) SuccessColor else ErrorColor,
                                            modifier = Modifier.size(20.dp)
                                        )

                                        // Текст статуса
                                        Text(
                                            text = if (placesMatch) "Совпадают" else "Не совпадают",
                                            color = if (placesMatch) SuccessColor else ErrorColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // Подсказка при несовпадении
                                    if (!placesMatch) {
                                        Text(
                                            text = "Мест указано: $totalPlaces, должно быть: $totalQuantity",
                                            color = ErrorColor,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }

                                    // Общий остаток - перегруз оранжевым
                                    val totalRemainder = totalQuantity - totalPlaces
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = if (totalRemainder >= 0)
                                                "Общий остаток: $totalRemainder мест"
                                            else
                                                "Общий перегруз: ${-totalRemainder} мест",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (totalRemainder < 0) Color(0xFFFF9800) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Кнопка сохранения
                item {
                    Button(
                        onClick = {
                            viewModel.saveShipment()
                            navController.popBackStack()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        enabled = currentProducts.isNotEmpty()
                    ) {
                        Text("СОХРАНИТЬ ОТГРУЗКУ")
                    }
                }
            }

            // Плавающая кнопка добавления поддона (только если есть товары)
            if (currentProducts.isNotEmpty() && activeProductId != null) {
                FloatingActionButton(
                    onClick = {
                        // Добавляем поддон к активной продукции
                        viewModel.addPallet(activeProductId!!)
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить поддон")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductItemCard(
    product: com.example.fishy.database.entities.ProductItem,
    index: Int,
    pallets: List<com.example.fishy.database.entities.Pallet>,
    onUpdate: (String, Any) -> Unit,
    onDelete: () -> Unit,
    onAddPallet: () -> Unit,
    onUpdatePallet: (Long, Int) -> Unit,
    onDeletePallet: (Long) -> Unit,
    isActive: Boolean = false,
    onSetActive: () -> Unit
) {
    var expanded by remember { mutableStateOf(true) }
    var showDeleteProductDialog by remember { mutableStateOf(false) }
    var palletToDelete by remember { mutableStateOf<Long?>(null) }

    // Локальные состояния для числовых полей
    var packageWeightText by remember { mutableStateOf("") }
    var quantityText by remember { mutableStateOf("") }

    // Инициализация значений
    LaunchedEffect(product.id) {
        packageWeightText = if (product.packageWeight > 0) product.packageWeight.toString() else ""
        quantityText = if (product.quantity > 0) product.quantity.toString() else ""
    }

    // Обновление при изменении значений извне
    LaunchedEffect(product.packageWeight) {
        if (product.packageWeight == 0.0) {
            packageWeightText = ""
        }
    }

    LaunchedEffect(product.quantity) {
        if (product.quantity == 0) {
            quantityText = ""
        }
    }

    // Диалог подтверждения удаления поддона
    if (palletToDelete != null) {
        AlertDialog(
            onDismissRequest = { palletToDelete = null },
            title = { Text("Удаление поддона") },
            text = { Text("Вы уверены, что хотите удалить этот поддон?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        palletToDelete?.let { onDeletePallet(it) }
                        palletToDelete = null
                    }
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { palletToDelete = null }
                ) {
                    Text("Отмена")
                }
            }
        )
    }

    // Диалог подтверждения удаления всей продукции
    if (showDeleteProductDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteProductDialog = false },
            title = { Text("Удаление продукции") },
            text = { Text("Вы уверены, что хотите удалить этот вид продукции?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteProductDialog = false
                    }
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteProductDialog = false }
                ) {
                    Text("Отмена")
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        border = CardDefaults.outlinedCardBorder(),
        onClick = { onSetActive() }
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Динамическое наименование продукции
                val displayName = remember(product.name, product.manufacturer) {
                    buildString {
                        if (product.name.isNotEmpty()) {
                            append(product.name)
                            if (product.manufacturer.isNotEmpty()) {
                                append(" - ${product.manufacturer}")
                            }
                        } else {
                            append("Продукция #$index")
                        }
                    }
                }

                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Row {
                    IconButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Свернуть" else "Развернуть"
                        )
                    }
                    IconButton(
                        onClick = { showDeleteProductDialog = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Удалить")
                    }
                }
            }

            if (expanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = product.name,
                        onValueChange = { onUpdate("name", it) },
                        label = { Text("Наименование") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = product.manufacturer,
                        onValueChange = { onUpdate("manufacturer", it) },
                        label = { Text("Изготовитель") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Поле тары с локальным состоянием - УБРАЛИ "(кг)"
                        OutlinedTextField(
                            value = packageWeightText,
                            onValueChange = { newValue ->
                                packageWeightText = newValue
                                val weight = newValue.toDoubleOrNull() ?: 0.0
                                onUpdate("packageWeight", weight)
                            },
                            label = { Text("Тара") }, // Изменено: было "Тара (кг)"
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )

                        // Поле количества с локальным состоянием
                        OutlinedTextField(
                            value = quantityText,
                            onValueChange = { newValue ->
                                quantityText = newValue
                                val qty = newValue.toIntOrNull() ?: 0
                                onUpdate("quantity", qty)
                            },
                            label = { Text("Кол-во") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )

                        // Поле массы - УБРАЛИ "(кг)"
                        OutlinedTextField(
                            value = String.format("%.2f", product.totalWeight),
                            onValueChange = { },
                            label = { Text("Масса") }, // Изменено: было "Масса (кг)"
                            modifier = Modifier.weight(1f),
                            readOnly = true,
                            singleLine = true
                        )
                    }

                    // Таблица поддонов
                    if (pallets.isNotEmpty()) {
                        Text(
                            text = "Поддоны:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )

                        Column {
                            // Заголовок таблицы
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "№",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(0.4f)
                                )
                                Text(
                                    text = "Кол-во мест",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1.2f)
                                )
                                Text(
                                    text = "",
                                    modifier = Modifier.weight(0.4f)
                                )
                            }

                            // Строки таблицы
                            pallets.forEach { pallet ->
                                PalletRow(
                                    pallet = pallet,
                                    onUpdatePallet = { places ->
                                        onUpdatePallet(pallet.id, places)
                                    },
                                    onDeletePallet = {
                                        palletToDelete = pallet.id
                                    }
                                )
                            }
                        }
                    }

                    // Кнопка добавления поддона для этого товара
                    Button(
                        onClick = onAddPallet,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Добавить поддон")
                        Text("Добавить поддон")
                    }

                    // Промежуточные итоги для этой продукции
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Итоги по продукции:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )

                            // Динамическое наименование для итогов
                            val productDisplayName = buildString {
                                if (product.name.isNotEmpty()) {
                                    append(product.name)
                                    if (product.manufacturer.isNotEmpty()) {
                                        append(" - ${product.manufacturer}")
                                    }
                                } else {
                                    append("Продукция #$index")
                                }
                            }

                            Text(
                                text = productDisplayName,
                                style = MaterialTheme.typography.bodySmall
                            )

                            Column {
                                Text("Поддоны: ${product.palletCount}", style = MaterialTheme.typography.bodySmall)
                                Text("Места: ${product.placesCount}", style = MaterialTheme.typography.bodySmall)
                                Text("Масса: ${String.format("%.2f", product.totalWeight)} кг",
                                    style = MaterialTheme.typography.bodySmall)

                                // Остаток в итогах - перегруз оранжевым
                                val remainder = product.quantity - product.placesCount
                                Text(
                                    text = if (remainder >= 0)
                                        "Остаток: $remainder мест"
                                    else
                                        "Перегруз: ${-remainder} мест",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (remainder < 0) Color(0xFFFF9800) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PalletRow(
    pallet: com.example.fishy.database.entities.Pallet,
    onUpdatePallet: (Int) -> Unit,
    onDeletePallet: () -> Unit
) {
    // Локальное состояние для количества мест в поддоне
    var placesText by remember(pallet.id) {
        mutableStateOf(if (pallet.places > 0) pallet.places.toString() else "")
    }

    // Обновление при изменении извне
    LaunchedEffect(pallet.places) {
        if (pallet.places == 0) {
            placesText = ""
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = pallet.palletNumber.toString(),
            modifier = Modifier.weight(1f)
        )

        OutlinedTextField(
            value = placesText,
            onValueChange = { newValue ->
                placesText = newValue
                val places = newValue.toIntOrNull() ?: 0
                onUpdatePallet(places)
            },
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )

        IconButton(
            onClick = onDeletePallet,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Удалить поддон",
                tint = ErrorColor
            )
        }
    }
}