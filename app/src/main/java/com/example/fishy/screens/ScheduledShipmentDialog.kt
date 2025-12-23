package com.example.fishy.screens

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fishy.database.entities.ChecklistItem
import com.example.fishy.database.entities.DictionaryItem
import com.example.fishy.database.entities.ScheduledPortData
import com.example.fishy.database.entities.ScheduledProduct
import com.example.fishy.database.entities.ScheduledShipment
import com.example.fishy.database.entities.ScheduledVehicleData
import com.example.fishy.database.entities.getProducts
import com.example.fishy.ui.components.DatePickerField
import com.example.fishy.ui.components.DictionaryAutocomplete
import com.example.fishy.ui.components.TimePickerField
import com.example.fishy.viewmodels.SchedulerViewModel
import com.example.fishy.viewmodels.SchedulerViewModelFactory
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Классы данных для хранения состояния
data class PortData(
    val id: Long = System.currentTimeMillis(),
    var name: String = "",
    var time: String = "",
    var products: MutableList<ScheduledProduct> = mutableListOf()
)

data class VehicleData(
    val id: Long = System.currentTimeMillis(),
    var containerNumber: String = "",
    var truckNumber: String = "",
    var trailerNumber: String = "",
    var wagonNumber: String = "",
    var sealNumber: String = "",
    var products: MutableList<ScheduledProduct> = mutableListOf()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditScheduledShipmentDialog(
    shipment: ScheduledShipment?,
    onDismiss: () -> Unit,
    onSave: (ScheduledShipment) -> Unit
) {
    val context = LocalContext.current
    val viewModel: SchedulerViewModel = viewModel(
        factory = SchedulerViewModelFactory(context.applicationContext as Application)
    )

    // Текущая дата и время для проверок
    val currentDate = Date()

    // Загрузка существующих чек-листов для редактируемой отгрузки
    val checklistItems by viewModel.getChecklistItems(shipment?.id ?: 0L).collectAsState(emptyList())

    // Состояние для хранения состояния чек-листов во время редактирования
    var checklistState by remember {
        mutableStateOf<Map<Long, ChecklistItem>>(emptyMap())
    }

    // Сохраняем состояние чек-листов при загрузке
    LaunchedEffect(checklistItems, shipment?.id) {
        val stateMap = mutableMapOf<Long, ChecklistItem>()
        checklistItems.forEach { item ->
            stateMap[item.id] = item
        }
        checklistState = stateMap
    }

    // Состояние формы
    var customer by remember { mutableStateOf(shipment?.customer ?: "") }
    var shipmentType by remember { mutableStateOf(shipment?.shipmentType ?: "mono") }
    var selectedDate by remember { mutableStateOf(shipment?.scheduledDate ?: Date()) }
    var scheduledTime by remember { mutableStateOf(shipment?.scheduledTime ?: "09:00") }

    // Общие поля
    var port by remember { mutableStateOf(shipment?.port ?: "") }
    var vessel by remember { mutableStateOf(shipment?.vessel ?: "") }

    // Поля транспорта
    var containerNumber by remember { mutableStateOf(shipment?.containerNumber ?: "") }
    var wagonNumber by remember { mutableStateOf(shipment?.wagonNumber ?: "") }
    var truckNumber by remember { mutableStateOf(shipment?.truckNumber ?: "") }
    var trailerNumber by remember { mutableStateOf(shipment?.trailerNumber ?: "") }
    var sealNumber by remember { mutableStateOf(shipment?.sealNumber ?: "") }

    // Для мультипорта
    var ports by remember {
        mutableStateOf(
            if (shipment?.shipmentType == "multi_port" && shipment.ports.isNotEmpty()) {
                shipment.ports.mapIndexed { index, portName ->
                    PortData(
                        name = portName,
                        time = shipment.portTimes.getOrElse(index) { "" },
                        products = shipment.getProducts().toMutableList()
                    )
                }
            } else {
                listOf(PortData())
            }
        )
    }

    // Для мультиавто
    var vehicles by remember {
        mutableStateOf(
            if (shipment?.shipmentType == "multi_vehicle") {
                listOf(
                    VehicleData(
                        containerNumber = shipment.containerNumber ?: "",
                        truckNumber = shipment.truckNumber ?: "",
                        trailerNumber = shipment.trailerNumber ?: "",
                        wagonNumber = shipment.wagonNumber ?: "",
                        sealNumber = shipment.sealNumber ?: "",
                        products = shipment.getProducts().toMutableList()
                    )
                )
            } else {
                listOf(VehicleData())
            }
        )
    }

    // Продукция для mono
    val monoProducts = remember { mutableStateListOf<ScheduledProduct>() }

    // УВЕДОМЛЕНИЯ
    var notificationEnabled by remember { mutableStateOf(shipment?.notificationEnabled ?: true) }

    // ВАЖНО: Правильная инициализация даты уведомления
    var notificationDate by remember {
        mutableStateOf(
            shipment?.notificationDate ?: run {
                Calendar.getInstance().apply {
                    time = Date()
                    set(Calendar.HOUR_OF_DAY, 9)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.time
            }
        )
    }

    var notificationTime by remember { mutableStateOf(shipment?.notificationTime ?: "09:00") }
    var notificationDaysBefore by remember { mutableStateOf(shipment?.notificationDaysBefore ?: 0) }
    var notificationHoursBefore by remember { mutableStateOf(shipment?.notificationHoursBefore ?: 0) }

    // Флаги валидации
    var isShipmentDateValid by remember { mutableStateOf(true) }
    var isNotificationDateValid by remember { mutableStateOf(true) }

    val coroutineScope = rememberCoroutineScope()

    // Загрузка продукции для mono
    LaunchedEffect(shipment) {
        if (shipment?.shipmentType == "mono") {
            monoProducts.clear()
            monoProducts.addAll(shipment.getProducts())
        }
    }

    // Справочники
    val customerDictionary by viewModel.getDictionaryItems("customer").collectAsState(emptyList())
    val portDictionary by viewModel.getDictionaryItems("port").collectAsState(emptyList())
    val vesselDictionary by viewModel.getDictionaryItems("vessel").collectAsState(emptyList())
    val productDictionary by viewModel.getDictionaryItems("product").collectAsState(emptyList())
    val manufacturerDictionary by viewModel.getDictionaryItems("manufacturer").collectAsState(emptyList())

    // Функции для добавления в словарь (используются в DictionaryAutocomplete)
    val onAddToDictionary: (String, String) -> Unit = { type, value ->
        viewModel.addDictionaryItem(type, value)
    }

    // Функция для проверки даты отгрузки
    fun validateShipmentDate(): Boolean {
        val selectedCalendar = Calendar.getInstance().apply {
            time = selectedDate
            val timeParts = scheduledTime.split(":")
            if (timeParts.size == 2) {
                set(Calendar.HOUR_OF_DAY, timeParts[0].toIntOrNull() ?: 0)
                set(Calendar.MINUTE, timeParts[1].toIntOrNull() ?: 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        }

        val currentCalendar = Calendar.getInstance().apply {
            time = currentDate
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val isValid = !selectedCalendar.before(currentCalendar)
        isShipmentDateValid = isValid

        if (!isValid) {
            Toast.makeText(context, "Дата и время отгрузки не могут быть в прошлом", Toast.LENGTH_LONG).show()
        }

        return isValid
    }

    // Функция для проверки даты уведомления
    fun validateNotificationDate(): Boolean {
        if (!notificationEnabled) {
            isNotificationDateValid = true
            return true
        }

        // ВАЖНО: Если используется абсолютная дата (notificationDaysBefore == 0 && notificationHoursBefore == 0),
        // но notificationDate не установлен, используем selectedDate
        if (notificationDaysBefore == 0 && notificationHoursBefore == 0 && notificationDate == null) {
            notificationDate = selectedDate
        }

        val notificationDateTime: Date? = try {
            if (notificationDaysBefore == 0 && notificationHoursBefore == 0) {
                // Абсолютная дата уведомления
                val notificationCalendar = Calendar.getInstance().apply {
                    // Используем notificationDate или selectedDate
                    val dateToUse = notificationDate ?: selectedDate
                    time = dateToUse
                    val timeParts = notificationTime.split(":")
                    if (timeParts.size == 2) {
                        set(Calendar.HOUR_OF_DAY, timeParts[0].toIntOrNull() ?: 0)
                        set(Calendar.MINUTE, timeParts[1].toIntOrNull() ?: 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                }
                notificationCalendar.time
            } else {
                // Относительная дата уведомления (от даты отгрузки)
                val shipmentCalendar = Calendar.getInstance().apply {
                    time = selectedDate
                    val timeParts = scheduledTime.split(":")
                    if (timeParts.size == 2) {
                        set(Calendar.HOUR_OF_DAY, timeParts[0].toIntOrNull() ?: 0)
                        set(Calendar.MINUTE, timeParts[1].toIntOrNull() ?: 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    add(Calendar.DAY_OF_YEAR, -notificationDaysBefore)
                    add(Calendar.HOUR_OF_DAY, -notificationHoursBefore)
                }
                shipmentCalendar.time
            }
        } catch (e: Exception) {
            null
        }

        if (notificationDateTime == null) {
            isNotificationDateValid = true
            return true
        }

        val currentCalendar = Calendar.getInstance().apply {
            time = currentDate
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val isValid = !notificationDateTime.before(currentCalendar.time)
        isNotificationDateValid = isValid

        if (!isValid) {
            Toast.makeText(context, "Дата и время уведомления не могут быть в прошлом", Toast.LENGTH_LONG).show()
        }

        return isValid
    }

    // Функция для проверки всех дат перед сохранением
    fun validateAllDates(): Boolean {
        val isShipmentValid = validateShipmentDate()
        val isNotificationValid = validateNotificationDate()
        return isShipmentValid && isNotificationValid
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.95f),
        title = {
            Text(if (shipment == null) "Добавить отгрузку" else "Редактировать отгрузку")
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 700.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Дата и время
                item {
                    SectionCard(title = "Дата и время отгрузки") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            DatePickerField(
                                selectedDate = selectedDate,
                                onDateSelected = {
                                    selectedDate = it
                                    // Если используется абсолютная дата уведомления и дата еще не установлена,
                                    // обновляем notificationDate на новую дату отгрузки
                                    if (notificationDaysBefore == 0 && notificationHoursBefore == 0) {
                                        notificationDate = it
                                    }
                                    validateShipmentDate()
                                    validateNotificationDate()
                                },
                                label = "Дата отгрузки",
                                modifier = Modifier.fillMaxWidth()
                            )

                            TimePickerField(
                                time = scheduledTime,
                                onTimeChange = {
                                    scheduledTime = it
                                    validateShipmentDate()
                                },
                                label = "Время отгрузки",
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Отображение ошибки даты отгрузки, если есть
                            if (!isShipmentDateValid) {
                                Text(
                                    text = "Дата и время отгрузки не могут быть в прошлом",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                // Заказчик
                item {
                    SectionCard(title = "Заказчик") {
                        DictionaryAutocomplete(
                            value = customer,
                            onValueChange = { customer = it },
                            label = "Заказчик",
                            dictionaryType = "customer",
                            modifier = Modifier.fillMaxWidth(),
                            dictionaryItems = customerDictionary,
                            onAddToDictionary = onAddToDictionary,
                            onSaveToDictionary = { value -> viewModel.addDictionaryItem("customer", value) }
                        )
                    }
                }

                // Тип отгрузки
                item {
                    SectionCard(title = "Тип погрузки") {
                        var expanded by remember { mutableStateOf(false) }
                        val options = listOf(
                            "Моноотгрузка" to "mono",
                            "Мультипорт" to "multi_port",
                            "Мультитранспорт" to "multi_vehicle"
                        )
                        val selectedText = options.find { it.second == shipmentType }?.first ?: "Выберите тип"

                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded }
                        ) {
                            OutlinedTextField(
                                value = selectedText,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                                },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
                                label = { Text("Тип отгрузки") }
                            )

                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                options.forEach { (text, value) ->
                                    DropdownMenuItem(
                                        text = { Text(text) },
                                        onClick = {
                                            shipmentType = value
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // В зависимости от типа отгрузки
                when (shipmentType) {
                    "mono" -> {
                        // Порт и судно
                        item {
                            SectionCard(title = "Место отгрузки") {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    DictionaryAutocomplete(
                                        value = port,
                                        onValueChange = { port = it },
                                        label = "Порт",
                                        dictionaryType = "port",
                                        modifier = Modifier.fillMaxWidth(),
                                        dictionaryItems = portDictionary,
                                        onAddToDictionary = onAddToDictionary,
                                        onSaveToDictionary = { value -> viewModel.addDictionaryItem("port", value) }
                                    )

                                    DictionaryAutocomplete(
                                        value = vessel,
                                        onValueChange = { vessel = it },
                                        label = "Судно",
                                        dictionaryType = "vessel",
                                        modifier = Modifier.fillMaxWidth(),
                                        dictionaryItems = vesselDictionary,
                                        onAddToDictionary = onAddToDictionary,
                                        onSaveToDictionary = { value -> viewModel.addDictionaryItem("vessel", value) }
                                    )
                                }
                            }
                        }

                        // Транспорт
                        item {
                            SectionCard(title = "Транспортные данные") {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    val hasWagon = wagonNumber.isNotEmpty()
                                    val hasVehicle = containerNumber.isNotEmpty() ||
                                            truckNumber.isNotEmpty() ||
                                            trailerNumber.isNotEmpty()
                                    val showWagon = !hasVehicle || hasWagon
                                    val showVehicle = !hasWagon || hasVehicle

                                    if (showWagon) {
                                        OutlinedTextField(
                                            value = wagonNumber,
                                            onValueChange = { wagonNumber = it },
                                            label = { Text("Вагон") },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )
                                    }

                                    if (showVehicle) {
                                        OutlinedTextField(
                                            value = containerNumber,
                                            onValueChange = { containerNumber = it.uppercase() },
                                            label = { Text("Контейнер") },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )

                                        OutlinedTextField(
                                            value = truckNumber,
                                            onValueChange = { truckNumber = it.uppercase() },
                                            label = { Text("Авто") },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )

                                        OutlinedTextField(
                                            value = trailerNumber,
                                            onValueChange = { trailerNumber = it.uppercase() },
                                            label = { Text("Прицеп") },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )
                                    }

                                    OutlinedTextField(
                                        value = sealNumber,
                                        onValueChange = { sealNumber = it.uppercase() },
                                        label = { Text("Пломба") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }
                            }
                        }

                        // Продукция для mono
                        item {
                            SectionCard(title = "Продукция") {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if (monoProducts.isEmpty()) {
                                        Text(
                                            text = "Нет добавленной продукции",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        monoProducts.forEachIndexed { index, product ->
                                            ProductItemCard(
                                                product = product,
                                                index = index,
                                                onProductChange = { updatedProduct ->
                                                    monoProducts[index] = updatedProduct
                                                },
                                                onDelete = {
                                                    monoProducts.removeAt(index)
                                                },
                                                productDictionary = productDictionary,
                                                manufacturerDictionary = manufacturerDictionary,
                                                onAddToDictionary = onAddToDictionary
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            monoProducts.add(ScheduledProduct())
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Добавить продукцию")
                                    }
                                }
                            }
                        }
                    }

                    "multi_port" -> {
                        // Количество портов
                        item {
                            SectionCard(title = "Количество портов:") {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedTextField(
                                        value = ports.size.toString(),
                                        onValueChange = {},
                                        modifier = Modifier.width(100.dp),
                                        readOnly = true,
                                        enabled = false
                                    )
                                }
                            }
                        }

                        // Общий транспорт для всех портов
                        item {
                            SectionCard(title = "Транспорт") {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    val hasWagon = wagonNumber.isNotEmpty()
                                    val hasVehicle = containerNumber.isNotEmpty() ||
                                            truckNumber.isNotEmpty() ||
                                            trailerNumber.isNotEmpty()
                                    val showWagon = !hasVehicle || hasWagon
                                    val showVehicle = !hasWagon || hasVehicle

                                    if (showWagon) {
                                        OutlinedTextField(
                                            value = wagonNumber,
                                            onValueChange = { wagonNumber = it },
                                            label = { Text("Вагон") },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )
                                    }

                                    if (showVehicle) {
                                        OutlinedTextField(
                                            value = containerNumber,
                                            onValueChange = { containerNumber = it.uppercase() },
                                            label = { Text("Контейнер") },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )

                                        OutlinedTextField(
                                            value = truckNumber,
                                            onValueChange = { truckNumber = it.uppercase() },
                                            label = { Text("Авто") },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )

                                        OutlinedTextField(
                                            value = trailerNumber,
                                            onValueChange = { trailerNumber = it.uppercase() },
                                            label = { Text("Прицеп") },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )
                                    }

                                    OutlinedTextField(
                                        value = sealNumber,
                                        onValueChange = { sealNumber = it.uppercase() },
                                        label = { Text("Пломба") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }
                            }
                        }

                        // Список портов
                        items(ports) { portData ->
                            PortCard(
                                portData = portData,
                                onPortChange = { newName ->
                                    ports = ports.map { if (it.id == portData.id) it.copy(name = newName) else it }
                                },
                                onTimeChange = { newTime ->
                                    ports = ports.map { if (it.id == portData.id) it.copy(time = newTime) else it }
                                },
                                onProductsChange = { newProducts ->
                                    ports = ports.map { if (it.id == portData.id) it.copy(products = newProducts.toMutableList()) else it }
                                },
                                onDelete = {
                                    ports = ports.filter { it.id != portData.id }
                                },
                                portDictionary = portDictionary,
                                productDictionary = productDictionary,
                                manufacturerDictionary = manufacturerDictionary,
                                onAddToDictionary = onAddToDictionary
                            )
                        }

                        // Кнопка добавления порта
                        item {
                            Button(
                                onClick = {
                                    ports = ports + PortData()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Добавить порт")
                            }
                        }
                    }

                    "multi_vehicle" -> {
                        // Количество транспорта
                        item {
                            SectionCard(title = "Количество транспорта:") {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedTextField(
                                        value = vehicles.size.toString(),
                                        onValueChange = {},
                                        modifier = Modifier.width(100.dp),
                                        readOnly = true,
                                        enabled = false
                                    )
                                }
                            }
                        }

                        // Общие данные для мультиавто
                        item {
                            SectionCard(title = "Общие данные:") {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    DictionaryAutocomplete(
                                        value = port,
                                        onValueChange = { port = it },
                                        label = "Порт",
                                        dictionaryType = "port",
                                        modifier = Modifier.fillMaxWidth(),
                                        dictionaryItems = portDictionary,
                                        onAddToDictionary = onAddToDictionary,
                                        onSaveToDictionary = { value -> viewModel.addDictionaryItem("port", value) }
                                    )

                                    DictionaryAutocomplete(
                                        value = vessel,
                                        onValueChange = { vessel = it },
                                        label = "Судно",
                                        dictionaryType = "vessel",
                                        modifier = Modifier.fillMaxWidth(),
                                        dictionaryItems = vesselDictionary,
                                        onAddToDictionary = onAddToDictionary,
                                        onSaveToDictionary = { value -> viewModel.addDictionaryItem("vessel", value) }
                                    )
                                }
                            }
                        }

                        // Список транспорта
                        items(vehicles) { vehicleData ->
                            VehicleCard(
                                vehicleData = vehicleData,
                                onVehicleChange = { updatedVehicle ->
                                    vehicles = vehicles.map { if (it.id == vehicleData.id) updatedVehicle else it }
                                },
                                onDelete = {
                                    vehicles = vehicles.filter { it.id != vehicleData.id }
                                },
                                productDictionary = productDictionary,
                                manufacturerDictionary = manufacturerDictionary,
                                onAddToDictionary = onAddToDictionary
                            )
                        }

                        // Кнопка добавления транспорта
                        item {
                            Button(
                                onClick = {
                                    vehicles = vehicles + VehicleData()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Добавить транспорт")
                            }
                        }
                    }
                }

                item {
                    SectionCard(title = "Уведомления") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Уведомление")
                                Switch(
                                    checked = notificationEnabled,
                                    onCheckedChange = {
                                        notificationEnabled = it
                                        if (it) validateNotificationDate()
                                    }
                                )
                            }

                            if (notificationEnabled) {
                                Text(
                                    text = "Укажите дату и время:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )

                                val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

                                DatePickerField(
                                    selectedDate = notificationDate ?: selectedDate,
                                    onDateSelected = {
                                        notificationDate = it
                                        notificationDaysBefore = 0
                                        notificationHoursBefore = 0
                                        validateNotificationDate()
                                    },
                                    label = "Дата уведомления",
                                    modifier = Modifier.fillMaxWidth()
                                )

                                TimePickerField(
                                    time = notificationTime,
                                    onTimeChange = {
                                        notificationTime = it
                                        // При изменении времени уведомления переключаемся на абсолютную дату
                                        notificationDaysBefore = 0
                                        notificationHoursBefore = 0
                                        // Убеждаемся, что notificationDate установлен
                                        if (notificationDate == null) {
                                            notificationDate = selectedDate
                                        }
                                        validateNotificationDate()
                                    },
                                    label = "Время уведомления",
                                    modifier = Modifier.fillMaxWidth()
                                )

                                val notificationDateStr = if (notificationDaysBefore == 0 && notificationHoursBefore == 0) {
                                    // Абсолютная дата
                                    if (notificationDate != null) {
                                        dateFormat.format(notificationDate!!) + " в $notificationTime"
                                    } else {
                                        dateFormat.format(selectedDate) + " в $notificationTime"
                                    }
                                } else {
                                    // Относительная дата
                                    val calendar = Calendar.getInstance().apply {
                                        time = selectedDate
                                        val timeParts = scheduledTime.split(":")
                                        if (timeParts.size == 2) {
                                            set(Calendar.HOUR_OF_DAY, timeParts[0].toIntOrNull() ?: 9)
                                            set(Calendar.MINUTE, timeParts[1].toIntOrNull() ?: 0)
                                        }
                                        add(Calendar.DAY_OF_YEAR, -notificationDaysBefore)
                                        add(Calendar.HOUR_OF_DAY, -notificationHoursBefore)
                                    }
                                    dateFormat.format(calendar.time) + " в $notificationTime"
                                }

                                Text(
                                    text = "Уведомление придёт: \n$notificationDateStr",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                // Отображение ошибки даты уведомления, если есть
                                if (!isNotificationDateValid) {
                                    Text(
                                        text = "Дата и время уведомления не могут быть в прошлом",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }

                if (shipment != null) {
                    item {
                        SectionCard(
                            title = "Чек-лист",
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (checklistState.isEmpty()) {
                                    Text(
                                        text = "Нет пунктов чек-листа",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    checklistState.values.sortedBy { it.orderIndex }.forEachIndexed { index, item ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Checkbox(
                                                checked = item.isCompleted,
                                                onCheckedChange = { isChecked ->
                                                    val updatedItem = item.copy(isCompleted = isChecked)
                                                    val updatedState = checklistState.toMutableMap()
                                                    updatedState[item.id] = updatedItem
                                                    checklistState = updatedState

                                                    coroutineScope.launch {
                                                        viewModel.updateChecklistItem(updatedItem)
                                                    }
                                                }
                                            )
                                            Text(
                                                text = "${index + 1}. ${item.title}",
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                    }
                                }

                                val completedCount = checklistState.values.count { it.isCompleted }
                                val totalCount = checklistState.size
                                if (totalCount > 0) {
                                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Выполнено:",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            text = "$completedCount/$totalCount (${if (totalCount > 0) (completedCount * 100 / totalCount) else 0}%)",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = when {
                                                completedCount == totalCount -> Color(0xFF4CAF50)
                                                completedCount > 0 -> Color(0xFFFFC107)
                                                else -> Color(0xFFF44336)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    val totalWeight = when (shipmentType) {
                        "mono" -> monoProducts.sumOf { it.totalWeight }
                        "multi_port" -> ports.flatMap { it.products }.sumOf { it.totalWeight }
                        "multi_vehicle" -> vehicles.flatMap { it.products }.sumOf { it.totalWeight }
                        else -> 0.0
                    }

                    if (totalWeight > 0) {
                        SectionCard(
                            title = "Итоговая масса",
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Общая масса:",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${String.format("%.1f", totalWeight)} кг",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (validateAllDates()) {
                        coroutineScope.launch {
                            // ВАЖНО: Сохраняем все изменения в чек-листе перед сохранением отгрузки
                            checklistState.values.forEach { item ->
                                viewModel.updateChecklistItem(item)
                            }

                            // Создаем автоматическое название
                            val autoTitle = buildString {
                                if (customer.isNotEmpty()) append(customer)
                                if (vessel.isNotEmpty()) {
                                    if (isNotEmpty()) append(" - ")
                                    append(vessel)
                                }
                                if (isEmpty()) {
                                    append("Отгрузка ")
                                    append(SimpleDateFormat("dd.MM", Locale.getDefault()).format(selectedDate))
                                }
                            }

                            // Сериализуем данные
                            val productsJson = when (shipmentType) {
                                "mono" -> ScheduledProduct.toJson(monoProducts)
                                "multi_port" -> {
                                    val portDataList = ports.map { port ->
                                        ScheduledPortData(
                                            portName = port.name,
                                            portTime = port.time,
                                            products = port.products,
                                            vessel = vessel
                                        )
                                    }
                                    Json.encodeToString(portDataList)
                                }
                                "multi_vehicle" -> {
                                    val vehicleDataList = vehicles.mapIndexed { index, vehicle ->
                                        ScheduledVehicleData(
                                            containerNumber = vehicle.containerNumber,
                                            truckNumber = vehicle.truckNumber,
                                            trailerNumber = vehicle.trailerNumber,
                                            wagonNumber = vehicle.wagonNumber,
                                            sealNumber = vehicle.sealNumber,
                                            products = vehicle.products
                                        )
                                    }
                                    Json.encodeToString(vehicleDataList)
                                }
                                else -> "[]"
                            }

                            val newShipment = ScheduledShipment(
                                id = shipment?.id ?: 0,
                                title = autoTitle,
                                scheduledDate = selectedDate,
                                scheduledTime = scheduledTime,
                                shipmentType = shipmentType,
                                ports = ports.map { it.name },
                                portTimes = ports.map { it.time },
                                vehicleCount = vehicles.size,
                                customer = customer,
                                port = port,
                                vessel = vessel,
                                containerNumber = containerNumber,
                                truckNumber = truckNumber,
                                trailerNumber = trailerNumber,
                                wagonNumber = wagonNumber,
                                sealNumber = sealNumber,
                                productsJson = productsJson,
                                totalWeight = when (shipmentType) {
                                    "mono" -> monoProducts.sumOf { it.totalWeight }
                                    "multi_port" -> ports.flatMap { it.products }.sumOf { it.totalWeight }
                                    "multi_vehicle" -> vehicles.flatMap { it.products }.sumOf { it.totalWeight }
                                    else -> 0.0
                                },
                                notificationEnabled = notificationEnabled,
                                notificationDate = if (notificationEnabled && notificationDaysBefore == 0 && notificationHoursBefore == 0) notificationDate else null,
                                notificationTime = notificationTime,
                                notificationDaysBefore = if (notificationEnabled) notificationDaysBefore else 0,
                                notificationHoursBefore = if (notificationEnabled) notificationHoursBefore else 0,
                                notificationSent = shipment?.notificationSent ?: false,
                                isCompleted = shipment?.isCompleted ?: false,
                                createdAt = shipment?.createdAt ?: Date(),
                                updatedAt = Date()
                            )

                            // Только передаем данные, сохранение будет в onSave в SchedulerScreen
                            onSave(newShipment)
                            onDismiss()
                        }
                    }
                },
                enabled = customer.isNotBlank() && scheduledTime.isNotBlank() &&
                        isShipmentDateValid && isNotificationDateValid
            ) {
                Text("СОХРАНИТЬ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ОТМЕНА")
            }
        }
    )
}

@Composable
fun SectionCard(
    title: String,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            content()
        }
    }
}

@Composable
fun ProductItemCard(
    product: ScheduledProduct,
    index: Int,
    onProductChange: (ScheduledProduct) -> Unit,
    onDelete: () -> Unit,
    productDictionary: List<DictionaryItem>,
    manufacturerDictionary: List<DictionaryItem>,
    onAddToDictionary: (String, String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Продукция ${index + 1}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить")
                }
            }

            DictionaryAutocomplete(
                value = product.name,
                onValueChange = { newValue ->
                    onProductChange(product.copy(name = newValue))
                },
                label = "Продукция",
                dictionaryType = "product",
                modifier = Modifier.fillMaxWidth(),
                dictionaryItems = productDictionary,
                onAddToDictionary = onAddToDictionary,
                onSaveToDictionary = { value -> onAddToDictionary("product", value) }
            )

            OutlinedTextField(
                value = product.batch,
                onValueChange = { newValue ->
                    onProductChange(product.copy(batch = newValue))
                },
                label = { Text("Партия") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            DictionaryAutocomplete(
                value = product.manufacturer,
                onValueChange = { newValue ->
                    onProductChange(product.copy(manufacturer = newValue))
                },
                label = "Изготовитель",
                dictionaryType = "manufacturer",
                modifier = Modifier.fillMaxWidth(),
                dictionaryItems = manufacturerDictionary,
                onAddToDictionary = onAddToDictionary,
                onSaveToDictionary = { value -> onAddToDictionary("manufacturer", value) }
            )

            OutlinedTextField(
                value = if (product.packageWeight > 0) product.packageWeight.toString() else "",
                onValueChange = { newValue ->
                    val weight = newValue.toDoubleOrNull() ?: 0.0
                    onProductChange(product.copy(
                        packageWeight = weight,
                        totalWeight = weight * product.quantity
                    ))
                },
                label = { Text("Тара (кг)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )

            OutlinedTextField(
                value = if (product.quantity > 0) product.quantity.toString() else "",
                onValueChange = { newValue ->
                    val quantity = newValue.toIntOrNull() ?: 0
                    onProductChange(product.copy(
                        quantity = quantity,
                        totalWeight = product.packageWeight * quantity
                    ))
                },
                label = { Text("Количество мест") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )

            OutlinedTextField(
                value = String.format("%.2f", product.totalWeight),
                onValueChange = { },
                label = { Text("Масса (кг)") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                singleLine = true,
                trailingIcon = {
                    Icon(Icons.Default.Calculate, contentDescription = "Рассчитано автоматически")
                }
            )
        }
    }
}

@Composable
fun PortCard(
    portData: PortData,
    onPortChange: (String) -> Unit,
    onTimeChange: (String) -> Unit,
    onProductsChange: (List<ScheduledProduct>) -> Unit,
    onDelete: () -> Unit,
    portDictionary: List<DictionaryItem>,
    productDictionary: List<DictionaryItem>,
    manufacturerDictionary: List<DictionaryItem>,
    onAddToDictionary: (String, String) -> Unit
) {
    var localPortData by remember { mutableStateOf(portData) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Порт ${localPortData.name.ifEmpty { "Новый" }}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить порт")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DictionaryAutocomplete(
                    value = localPortData.name,
                    onValueChange = { newValue ->
                        localPortData = localPortData.copy(name = newValue)
                        onPortChange(newValue)
                    },
                    label = "Название порта",
                    dictionaryType = "port",
                    modifier = Modifier.fillMaxWidth(),
                    dictionaryItems = portDictionary,
                    onAddToDictionary = onAddToDictionary,
                    onSaveToDictionary = { value -> onAddToDictionary("port", value) }
                )

                TimePickerField(
                    time = localPortData.time,
                    onTimeChange = { newValue ->
                        localPortData = localPortData.copy(time = newValue)
                        onTimeChange(newValue)
                    },
                    label = "Время в порту",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Продукция:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                if (localPortData.products.isEmpty()) {
                    Text(
                        text = "Нет добавленной продукции",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    localPortData.products.forEachIndexed { index, product ->
                        ProductItemCard(
                            product = product,
                            index = index,
                            onProductChange = { updatedProduct ->
                                val newProducts = localPortData.products.toMutableList()
                                newProducts[index] = updatedProduct
                                localPortData = localPortData.copy(products = newProducts)
                                onProductsChange(newProducts)
                            },
                            onDelete = {
                                val newProducts = localPortData.products.toMutableList()
                                newProducts.removeAt(index)
                                localPortData = localPortData.copy(products = newProducts)
                                onProductsChange(newProducts)
                            },
                            productDictionary = productDictionary,
                            manufacturerDictionary = manufacturerDictionary,
                            onAddToDictionary = onAddToDictionary
                        )
                    }
                }

                Button(
                    onClick = {
                        val newProducts = localPortData.products.toMutableList()
                        newProducts.add(ScheduledProduct())
                        localPortData = localPortData.copy(products = newProducts)
                        onProductsChange(newProducts)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Добавить продукцию")
                }
            }
        }
    }
}

@Composable
fun VehicleCard(
    vehicleData: VehicleData,
    onVehicleChange: (VehicleData) -> Unit,
    onDelete: () -> Unit,
    productDictionary: List<DictionaryItem>,
    manufacturerDictionary: List<DictionaryItem>,
    onAddToDictionary: (String, String) -> Unit
) {
    var localVehicleData by remember { mutableStateOf(vehicleData) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val vehicleName = when {
                    localVehicleData.containerNumber.isNotEmpty() -> "Контейнер ${localVehicleData.containerNumber}"
                    localVehicleData.truckNumber.isNotEmpty() -> "Авто ${localVehicleData.truckNumber}"
                    localVehicleData.wagonNumber.isNotEmpty() -> "Вагон ${localVehicleData.wagonNumber}"
                    else -> "Новый транспорт"
                }

                Text(
                    text = vehicleName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить транспорт")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val hasWagon = localVehicleData.wagonNumber.isNotEmpty()
                val hasVehicle = localVehicleData.containerNumber.isNotEmpty() ||
                        localVehicleData.truckNumber.isNotEmpty() ||
                        localVehicleData.trailerNumber.isNotEmpty()
                val showWagon = !hasVehicle || hasWagon
                val showVehicle = !hasWagon || hasVehicle

                if (showWagon) {
                    OutlinedTextField(
                        value = localVehicleData.wagonNumber,
                        onValueChange = { newValue ->
                            localVehicleData = localVehicleData.copy(wagonNumber = newValue)
                            onVehicleChange(localVehicleData)
                        },
                        label = { Text("Вагон") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                if (showVehicle) {
                    OutlinedTextField(
                        value = localVehicleData.containerNumber,
                        onValueChange = { newValue ->
                            localVehicleData = localVehicleData.copy(containerNumber = newValue.uppercase())
                            onVehicleChange(localVehicleData)
                        },
                        label = { Text("Контейнер") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters
                        )
                    )

                    OutlinedTextField(
                        value = localVehicleData.truckNumber,
                        onValueChange = { newValue ->
                            localVehicleData = localVehicleData.copy(truckNumber = newValue.uppercase())
                            onVehicleChange(localVehicleData)
                        },
                        label = { Text("Авто") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters
                        )
                    )

                    OutlinedTextField(
                        value = localVehicleData.trailerNumber,
                        onValueChange = { newValue ->
                            localVehicleData = localVehicleData.copy(trailerNumber = newValue.uppercase())
                            onVehicleChange(localVehicleData)
                        },
                        label = { Text("Прицеп") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters
                        )
                    )
                }

                OutlinedTextField(
                    value = localVehicleData.sealNumber,
                    onValueChange = { newValue ->
                        localVehicleData = localVehicleData.copy(sealNumber = newValue.uppercase())
                        onVehicleChange(localVehicleData)
                    },
                    label = { Text("Пломба") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters
                    )
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Продукция:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                if (localVehicleData.products.isEmpty()) {
                    Text(
                        text = "Нет добавленной продукции",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    localVehicleData.products.forEachIndexed { index, product ->
                        ProductItemCard(
                            product = product,
                            index = index,
                            onProductChange = { updatedProduct ->
                                val newProducts = localVehicleData.products.toMutableList()
                                newProducts[index] = updatedProduct
                                localVehicleData = localVehicleData.copy(products = newProducts)
                                onVehicleChange(localVehicleData)
                            },
                            onDelete = {
                                val newProducts = localVehicleData.products.toMutableList()
                                newProducts.removeAt(index)
                                localVehicleData = localVehicleData.copy(products = newProducts)
                                onVehicleChange(localVehicleData)
                            },
                            productDictionary = productDictionary,
                            manufacturerDictionary = manufacturerDictionary,
                            onAddToDictionary = onAddToDictionary
                        )
                    }
                }

                Button(
                    onClick = {
                        val newProducts = localVehicleData.products.toMutableList()
                        newProducts.add(ScheduledProduct())
                        localVehicleData = localVehicleData.copy(products = newProducts)
                        onVehicleChange(localVehicleData)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Добавить продукцию")
                }
            }
        }
    }
}