package com.example.fishy.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.fishy.database.AppDatabase
import com.example.fishy.screens.newshipment.MultiTotals
import com.example.fishy.theme.Success
import com.example.fishy.ui.components.DictionaryAutocomplete
import com.example.fishy.ui.components.ShipmentTypeDropdown
import com.example.fishy.utils.ValidationState
import com.example.fishy.viewmodels.ShipmentViewModel
import com.example.fishy.viewmodels.ShipmentViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewShipmentScreen(
    navController: NavController,
    scheduledShipmentId: Long? = null
) {
    val context = LocalContext.current
    val viewModel: ShipmentViewModel = viewModel(
        factory = ShipmentViewModelFactory(
            context = context,
            database = AppDatabase.getDatabase(context)
        )
    )

    // Состояния для отслеживания процесса сохранения
    var isSaving by remember { mutableStateOf(false) }

    // Состояние для отображения диалогового окна
    var showSaveConfirmationDialog by remember { mutableStateOf(false) }

    // Состояние для отслеживания, была ли уже инициализация
    var isInitialized by remember { mutableStateOf(false) }

    val currentShipment by viewModel.currentShipment.collectAsState()
    val shipmentType by viewModel.shipmentType.collectAsState()
    val multiPorts by viewModel.multiPorts.collectAsState()
    val multiVehicles by viewModel.multiVehicles.collectAsState()
    val currentProducts by viewModel.currentProducts.collectAsState()

    // Для исчезающих полей транспорта в общих данных
    val hasWagon = currentShipment.wagonNumber.isNotEmpty()
    val hasVehicle = currentShipment.containerNumber.isNotEmpty() ||
            currentShipment.truckNumber.isNotEmpty() ||
            currentShipment.trailerNumber.isNotEmpty()
    val showWagon = !hasVehicle || hasWagon
    val showVehicle = !hasWagon || hasVehicle

    // Получаем словари для автодополнения
    val customerDictionary by viewModel.getDictionaryItems("customer").collectAsState(emptyList())
    val portDictionary by viewModel.getDictionaryItems("port").collectAsState(emptyList())
    val vesselDictionary by viewModel.getDictionaryItems("vessel").collectAsState(emptyList())
    val productDictionary by viewModel.getDictionaryItems("product").collectAsState(emptyList())
    val manufacturerDictionary by viewModel.getDictionaryItems("manufacturer").collectAsState(emptyList())

    // Состояния для аккордеонов
    var multiPortGeneralDataExpanded by remember { mutableStateOf(true) }
    var multiVehicleGeneralDataExpanded by remember { mutableStateOf(true) }
    var multiPortTotalsExpanded by remember { mutableStateOf(true) }
    var multiVehicleTotalsExpanded by remember { mutableStateOf(true) }

    // Загрузка данных при открытии экрана
    LaunchedEffect(scheduledShipmentId) {
        if (!isInitialized) {
            if (scheduledShipmentId != null) {
                // Загружаем данные из запланированной отгрузки
                viewModel.loadFromScheduledShipment(scheduledShipmentId)
            }
            // НЕ загружаем черновик автоматически - только при явном вызове из MainScreen или DraftsScreen
            isInitialized = true
        }
    }

    // Функция для сохранения отгрузки
    fun saveShipmentAndNavigate() {
        if (isSaving) return

        isSaving = true
        viewModel.saveShipment()

        // Показываем успешное сообщение
        Toast.makeText(context, "Отгрузка сохранена", Toast.LENGTH_SHORT).show()

        // Возвращаемся на предыдущий экран
        navController.popBackStack()
        isSaving = false
    }

    // Общие итоги для мультипорта с учетом двойного контроля
    val multiPortTotals = remember(multiPorts) {
        var totalProductTypes = 0
        var totalPallets = 0
        var totalPlaces = 0
        var totalWeight = 0.0
        var totalQuantity = 0

        // Счетчики для двойного контроля (если где-то включен)
        var importedPallets = 0
        var importedPlaces = 0
        var totalPalletsWithDC = 0
        var totalPlacesWithDC = 0

        multiPorts.forEach { port ->
            totalProductTypes += port.products.size

            port.products.forEach { product ->
                totalPallets += product.palletCount
                totalWeight += product.totalWeight
                totalQuantity += product.quantity

                // Если в порту включен двойной контроль, считаем только завезенные места
                if (port.doubleControlEnabled) {
                    val productImportedPlaces = product.pallets.sumOf { if (it.isImported) it.places else 0 }
                    val productImportedPallets = product.pallets.count { it.isImported }
                    importedPlaces += productImportedPlaces
                    importedPallets += productImportedPallets
                    totalPlaces += productImportedPlaces

                    // Общие счетчики для статистики
                    totalPalletsWithDC += product.palletCount
                    totalPlacesWithDC += product.placesCount
                } else {
                    totalPlaces += product.placesCount
                }
            }
        }

        // Создаем объект MultiTotals с учетом двойного контроля
        MultiTotals(
            totalProductTypes = totalProductTypes,
            totalPallets = totalPallets,
            totalPlaces = totalPlaces,
            totalWeight = totalWeight,
            totalRemainder = totalQuantity - totalPlaces,
            totalQuantity = totalQuantity
        )
    }

    // Статистика двойного контроля для общих итогов мультипорта
    val multiPortDoubleControlStats = remember(multiPorts) {
        val portsWithDC = multiPorts.filter { it.doubleControlEnabled }
        if (portsWithDC.isEmpty()) {
            com.example.fishy.viewmodels.DoubleControlStats()
        } else {
            val allPallets = portsWithDC.flatMap { port ->
                port.products.flatMap { it.pallets }
            }
            val totalPallets = allPallets.size
            val importedPallets = allPallets.count { it.isImported }
            val totalPlaces = allPallets.sumOf { it.places }
            val importedPlaces = allPallets.sumOf { if (it.isImported) it.places else 0 }

            com.example.fishy.viewmodels.DoubleControlStats(
                totalPallets = totalPallets,
                exportedPallets = totalPallets,
                importedPallets = importedPallets,
                totalPlaces = totalPlaces,
                exportedPlaces = totalPlaces,
                importedPlaces = importedPlaces
            )
        }
    }

    // Общие итоги для мультиавто с учетом двойного контроля
    val multiVehicleTotals = remember(multiVehicles) {
        var totalProductTypes = 0
        var totalPallets = 0
        var totalPlaces = 0
        var totalWeight = 0.0
        var totalQuantity = 0

        // Счетчики для двойного контроля (если где-то включен)
        var importedPallets = 0
        var importedPlaces = 0
        var totalPalletsWithDC = 0
        var totalPlacesWithDC = 0

        multiVehicles.forEach { vehicle ->
            totalProductTypes += vehicle.products.size

            vehicle.products.forEach { product ->
                totalPallets += product.palletCount
                totalWeight += product.totalWeight
                totalQuantity += product.quantity

                // Если в транспорте включен двойной контроль, считаем только завезенные места
                if (vehicle.doubleControlEnabled) {
                    val productImportedPlaces = product.pallets.sumOf { if (it.isImported) it.places else 0 }
                    val productImportedPallets = product.pallets.count { it.isImported }
                    importedPlaces += productImportedPlaces
                    importedPallets += productImportedPallets
                    totalPlaces += productImportedPlaces

                    // Общие счетчики для статистики
                    totalPalletsWithDC += product.palletCount
                    totalPlacesWithDC += product.placesCount
                } else {
                    totalPlaces += product.placesCount
                }
            }
        }

        // Создаем объект MultiTotals с учетом двойного контроля
        MultiTotals(
            totalProductTypes = totalProductTypes,
            totalPallets = totalPallets,
            totalPlaces = totalPlaces,
            totalWeight = totalWeight,
            totalRemainder = totalQuantity - totalPlaces,
            totalQuantity = totalQuantity
        )
    }

    // Статистика двойного контроля для общих итогов мультиавто
    val multiVehicleDoubleControlStats = remember(multiVehicles) {
        val vehiclesWithDC = multiVehicles.filter { it.doubleControlEnabled }
        if (vehiclesWithDC.isEmpty()) {
            com.example.fishy.viewmodels.DoubleControlStats()
        } else {
            val allPallets = vehiclesWithDC.flatMap { vehicle ->
                vehicle.products.flatMap { it.pallets }
            }
            val totalPallets = allPallets.size
            val importedPallets = allPallets.count { it.isImported }
            val totalPlaces = allPallets.sumOf { it.places }
            val importedPlaces = allPallets.sumOf { if (it.isImported) it.places else 0 }

            com.example.fishy.viewmodels.DoubleControlStats(
                totalPallets = totalPallets,
                exportedPallets = totalPallets,
                importedPallets = importedPallets,
                totalPlaces = totalPlaces,
                exportedPlaces = totalPlaces,
                importedPlaces = importedPlaces
            )
        }
    }

    // Проверяем, включен ли двойной контроль в любом порту/транспорте
    val anyPortHasDoubleControl = multiPorts.any { it.doubleControlEnabled }
    val anyVehicleHasDoubleControl = multiVehicles.any { it.doubleControlEnabled }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (scheduledShipmentId != null) "Начать отгрузку" else "Новая погрузка"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        // Если есть запланированная отгрузка, сбрасываем её ID
                        // чтобы она не удалилась при сохранении в будущем
                        if (scheduledShipmentId != null) {
                            viewModel.clearScheduledShipmentId()
                        }
                        navController.popBackStack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Выбор типа погрузки
                item {
                    ShipmentTypeDropdown(
                        selectedType = shipmentType,
                        onTypeSelected = { viewModel.setShipmentType(it) }
                    )
                }

                when (shipmentType) {
                    "mono" -> {
                        item {
                            MonoModeScreen(
                                viewModel = viewModel,
                                navController = navController,
                                customerDictionary = customerDictionary,
                                portDictionary = portDictionary,
                                vesselDictionary = vesselDictionary,
                                productDictionary = productDictionary,
                                manufacturerDictionary = manufacturerDictionary
                            )
                        }
                    }

                    "multi_port" -> {
                        // Аккордеон "ОБЩИЕ ДАННЫЕ" для мультипорта
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Column {
                                    // Заголовок аккордеона
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "ОБЩИЕ ДАННЫЕ",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        IconButton(
                                            onClick = { multiPortGeneralDataExpanded = !multiPortGeneralDataExpanded },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (multiPortGeneralDataExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = if (multiPortGeneralDataExpanded) "Свернуть" else "Развернуть"
                                            )
                                        }
                                    }

                                    // Контент аккордеона
                                    if (multiPortGeneralDataExpanded) {
                                        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                                            // Заказчик
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

                                            Spacer(modifier = Modifier.height(8.dp))

                                            // Получаем состояния валидации
                                            val containerValidation by viewModel.containerValidation.collectAsState()
                                            val wagonValidation by viewModel.wagonValidation.collectAsState()

                                            // Вагон (только если нет авто/контейнера/прицепа)
                                            if (showWagon) {
                                                val hasWagonError = wagonValidation is ValidationState.INVALID ||
                                                        wagonValidation is ValidationState.INVALID_WITH_SUGGESTION

                                                OutlinedTextField(
                                                    value = currentShipment.wagonNumber,
                                                    onValueChange = { viewModel.updateShipmentField("wagon", it.uppercase())},
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
                                            Spacer(modifier = Modifier.height(8.dp))
                                            // Контейнер, Авто, Прицеп (только если нет вагона)
                                            if (showVehicle) {
                                                val hasContainerError = containerValidation is ValidationState.INVALID ||
                                                        containerValidation is ValidationState.INVALID_WITH_SUGGESTION

                                                // Контейнер
                                                OutlinedTextField(
                                                    value = currentShipment.containerNumber,
                                                    onValueChange = { viewModel.updateShipmentField("container", it.uppercase())},
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

                                                Spacer(modifier = Modifier.height(8.dp))

                                                // Авто и прицеп вместе
                                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        }

                        // Список портов (каждый порт в своем аккордеоне - уже реализовано в MultiPortItem)
                        items(multiPorts) { port ->
                            MultiPortItem(
                                port = port,
                                viewModel = viewModel,
                                onDeletePort = { viewModel.deleteMultiPort(port.id) },
                                portDictionary = portDictionary,
                                productDictionary = productDictionary,
                                manufacturerDictionary = manufacturerDictionary
                            )
                        }

                        // Кнопка добавления порта
                        item {
                            Button(
                                onClick = { viewModel.addMultiPort() },
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(16.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Добавить порт")
                                Spacer(Modifier.width(8.dp))
                                Text("Добавить порт")
                            }
                        }

                        // Аккордеон "ОБЩИЕ ИТОГИ" для мультипорта
                        item {
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
                                        Text("ОБЩИЕ ИТОГИ", style = MaterialTheme.typography.bodyLarge)
                                        IconButton(
                                            onClick = { multiPortTotalsExpanded = !multiPortTotalsExpanded },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                if (multiPortTotalsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = null
                                            )
                                        }
                                    }

                                    if (multiPortTotalsExpanded) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Видов продукции:")
                                                Text("${multiPortTotals.totalProductTypes}")
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Поддонов:")
                                                Text("${multiPortTotals.totalPallets}")
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Мест:")
                                                Text("${multiPortTotals.totalPlaces}")
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Общая масса:")
                                                Text("${String.format("%.2f", multiPortTotals.totalWeight)} кг")
                                            }

                                            // Учитываем двойной контроль в расчетах
                                            val actualPlaces = if (anyPortHasDoubleControl) {
                                                multiPortDoubleControlStats.importedPlaces
                                            } else {
                                                multiPortTotals.totalPlaces
                                            }

                                            val remainder = multiPortTotals.totalQuantity - actualPlaces

                                            // Статус загрузки с учетом двойного контроля
                                            when {
                                                remainder > 0 -> {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(
                                                            text = "Недогруз:",
                                                            color = MaterialTheme.colorScheme.error
                                                        )
                                                        Text(
                                                            text = "$remainder мест",
                                                            color = MaterialTheme.colorScheme.error
                                                        )
                                                    }
                                                }
                                                remainder < 0 -> {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(
                                                            text = "Перегруз:",
                                                            color = MaterialTheme.colorScheme.tertiary
                                                        )
                                                        Text(
                                                            text = "${-remainder} мест",
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
                                                            color = Success
                                                        )
                                                    }
                                                }
                                            }

                                            // Статистика двойного контроля
                                            if (anyPortHasDoubleControl) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Card(
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                                    ),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(12.dp),
                                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            "Двойной контроль:",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            fontWeight = FontWeight.Bold
                                                        )

                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text(
                                                                "Поддоны:",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                            Text(
                                                                "${multiPortDoubleControlStats.importedPallets}/${multiPortDoubleControlStats.totalPallets}",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = if (multiPortDoubleControlStats.importedPallets == multiPortDoubleControlStats.totalPallets)
                                                                    MaterialTheme.colorScheme.primary
                                                                else
                                                                    MaterialTheme.colorScheme.error
                                                            )
                                                        }

                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text(
                                                                "Места:",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                            Text(
                                                                "${multiPortDoubleControlStats.importedPlaces}/${multiPortDoubleControlStats.totalPlaces}",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = if (multiPortDoubleControlStats.importedPlaces == multiPortDoubleControlStats.totalPlaces)
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

                    "multi_vehicle" -> {
                        // Аккордеон "ОБЩИЕ ДАННЫЕ" для мультиавто
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Column {
                                    // Заголовок аккордеона
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "ОБЩИЕ ДАННЫЕ",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        IconButton(
                                            onClick = { multiVehicleGeneralDataExpanded = !multiVehicleGeneralDataExpanded },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (multiVehicleGeneralDataExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = if (multiVehicleGeneralDataExpanded) "Свернуть" else "Развернуть"
                                            )
                                        }
                                    }

                                    // Контент аккордеона
                                    if (multiVehicleGeneralDataExpanded) {
                                        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                                            // Заказчик
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

                                            Spacer(modifier = Modifier.height(8.dp))

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

                                            Spacer(modifier = Modifier.height(8.dp))

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
                        }

                        // Список транспорта (каждый транспорт в своем аккордеоне - уже реализовано в MultiVehicleItem)
                        items(multiVehicles) { vehicle ->
                            MultiVehicleItem(
                                vehicle = vehicle,
                                viewModel = viewModel,
                                onDeleteVehicle = { viewModel.deleteMultiVehicle(vehicle.id) },
                                productDictionary = productDictionary,
                                manufacturerDictionary = manufacturerDictionary
                            )
                        }

                        // Кнопка добавления транспорта
                        item {
                            Button(
                                onClick = { viewModel.addMultiVehicle() },
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(16.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Добавить транспорт")
                                Spacer(Modifier.width(8.dp))
                                Text("Добавить транспорт")
                            }
                        }

                        // Аккордеон "ОБЩИЕ ИТОГИ" для мультиавто
                        item {
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
                                        Text("ОБЩИЕ ИТОГИ", style = MaterialTheme.typography.bodyLarge)
                                        IconButton(
                                            onClick = { multiVehicleTotalsExpanded = !multiVehicleTotalsExpanded },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                if (multiVehicleTotalsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = null
                                            )
                                        }
                                    }

                                    if (multiVehicleTotalsExpanded) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Мест:")
                                                Text("${multiVehicleTotals.totalPlaces}")
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Общая масса:")
                                                Text("${String.format("%.2f", multiVehicleTotals.totalWeight)} кг")
                                            }

                                            // Учитываем двойной контроль в расчетах
                                            val actualPlaces = if (anyVehicleHasDoubleControl) {
                                                multiVehicleDoubleControlStats.importedPlaces
                                            } else {
                                                multiVehicleTotals.totalPlaces
                                            }

                                            val remainder = multiVehicleTotals.totalQuantity - actualPlaces

                                            // Статус загрузки с учетом двойного контроля
                                            when {
                                                remainder > 0 -> {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(
                                                            text = "Недогруз:",
                                                            color = MaterialTheme.colorScheme.error
                                                        )
                                                        Text(
                                                            text = "$remainder мест",
                                                            color = MaterialTheme.colorScheme.error
                                                        )
                                                    }
                                                }
                                                remainder < 0 -> {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(
                                                            text = "Перегруз:",
                                                            color = MaterialTheme.colorScheme.tertiary
                                                        )
                                                        Text(
                                                            text = "${-remainder} мест",
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
                                                            color = Success
                                                        )
                                                    }
                                                }
                                            }

                                            // Статистика двойного контроля
                                            if (anyVehicleHasDoubleControl) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Card(
                                                    colors = CardDefaults.cardColors(
                                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                                    ),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(12.dp),
                                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            "Двойной контроль:",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            fontWeight = FontWeight.Bold
                                                        )

                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text(
                                                                "Поддоны:",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                            Text(
                                                                "${multiVehicleDoubleControlStats.importedPallets}/${multiVehicleDoubleControlStats.totalPallets}",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = if (multiVehicleDoubleControlStats.importedPallets == multiVehicleDoubleControlStats.totalPallets)
                                                                    MaterialTheme.colorScheme.primary
                                                                else
                                                                    MaterialTheme.colorScheme.error
                                                            )
                                                        }

                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text(
                                                                "Места:",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                            Text(
                                                                "${multiVehicleDoubleControlStats.importedPlaces}/${multiVehicleDoubleControlStats.totalPlaces}",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = if (multiVehicleDoubleControlStats.importedPlaces == multiVehicleDoubleControlStats.totalPlaces)
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

                // Кнопка сохранения черновика и отгрузки
                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Кнопка сохранения черновика
                        Button(
                            onClick = {
                                val draftId = viewModel.saveDraft()
                                if (draftId != 0L) {
                                    Toast.makeText(context, "Черновик сохранен", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Ошибка сохранения черновика", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            enabled = !isSaving
                        ) {
                            Text("Сохранить черновик")
                        }

                        // Кнопка сохранения отгрузки
                        val canSave = when (shipmentType) {
                            "mono" -> currentProducts.isNotEmpty()
                            "multi_port" -> multiPorts.isNotEmpty() && multiPorts.any { it.products.isNotEmpty() }
                            "multi_vehicle" -> multiVehicles.isNotEmpty() && multiVehicles.any { it.products.isNotEmpty() }
                            else -> false
                        }

                        Button(
                            onClick = {
                                // Показываем диалоговое окно подтверждения вместо прямого вызова
                                showSaveConfirmationDialog = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = canSave && !isSaving,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Сохранение...")
                            } else {
                                Text("СОХРАНИТЬ ОТГРУЗКУ")
                            }
                        }
                    }
                }
            }

            // Диалоговое окно подтверждения сохранения
            if (showSaveConfirmationDialog) {
                AlertDialog(
                    onDismissRequest = {
                        // При клике вне диалога скрываем его
                        showSaveConfirmationDialog = false
                    },
                    title = {
                        Text(
                            text = "Завершение отгрузки",
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    text = {
                        Text("Завершить отгрузку и сохранить её в архив?")
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showSaveConfirmationDialog = false
                                saveShipmentAndNavigate()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("OK")
                        }
                    },
                    dismissButton = {
                        Button(
                            onClick = {
                                showSaveConfirmationDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text("Отмена")
                        }
                    }
                )
            }
        }
    }
}