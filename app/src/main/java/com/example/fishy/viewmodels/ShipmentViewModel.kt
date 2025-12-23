package com.example.fishy.viewmodels

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fishy.database.AppDatabase
import com.example.fishy.database.entities.DictionaryItem
import com.example.fishy.database.entities.MultiPort
import com.example.fishy.database.entities.MultiPortPallet
import com.example.fishy.database.entities.MultiPortProduct
import com.example.fishy.database.entities.MultiVehicle
import com.example.fishy.database.entities.MultiVehiclePallet
import com.example.fishy.database.entities.MultiVehicleProduct
import com.example.fishy.database.entities.Pallet
import com.example.fishy.database.entities.ProductItem
import com.example.fishy.database.entities.ScheduledShipment
import com.example.fishy.database.entities.Shipment
import com.example.fishy.database.entities.getPortData
import com.example.fishy.database.entities.getProducts
import com.example.fishy.database.entities.getVehicleData
import com.example.fishy.utils.ContainerWagonValidator
import com.example.fishy.utils.DraftData
import com.example.fishy.utils.DraftManager
import com.example.fishy.utils.ValidationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Date

class ShipmentViewModel(private val database: AppDatabase, private val context: Context) : ViewModel() {

    private val dao = database.shipmentDao()
    private val dictionaryDao = database.dictionaryDao()
    private val scheduledShipmentDao = database.scheduledShipmentDao()
    private val draftManager = DraftManager(context)

    // Для хранения ID запланированной отгрузки (для удаления после сохранения)
    private val _scheduledShipmentId = MutableStateFlow<Long?>(null)

    // Для хранения ID текущего черновика
    private val _currentDraftId = MutableStateFlow<Long?>(null)

    // Для автоматического сохранения черновика
    private var autoSaveJob: Job? = null
    private val autoSaveDelay = 2000L // 2 секунды

    // Live данные для всех отгрузок
    val allShipments: Flow<List<Shipment>> = dao.getAllShipments()

    // Текущая отгрузка (для создания/редактирования)
    private val _currentShipment = MutableStateFlow(Shipment())
    val currentShipment: StateFlow<Shipment> = _currentShipment.asStateFlow()

    // Товары текущей отгрузки
    private val _currentProducts = MutableStateFlow<List<ProductItem>>(emptyList())
    val currentProducts: StateFlow<List<ProductItem>> = _currentProducts.asStateFlow()

    // Поддоны для выбранного товара
    private val _currentPallets = MutableStateFlow<Map<Long, List<Pallet>>>(emptyMap())
    val currentPallets: StateFlow<Map<Long, List<Pallet>>> = _currentPallets.asStateFlow()

    // Тип погрузки
    private val _shipmentType = MutableStateFlow("mono")
    val shipmentType: StateFlow<String> = _shipmentType.asStateFlow()

    // Данные для мультипорта
    private val _multiPorts = MutableStateFlow<List<MultiPort>>(emptyList())
    val multiPorts: StateFlow<List<MultiPort>> = _multiPorts.asStateFlow()

    // Данные для мультиавто
    private val _multiVehicles = MutableStateFlow<List<MultiVehicle>>(emptyList())
    val multiVehicles: StateFlow<List<MultiVehicle>> = _multiVehicles.asStateFlow()

    // Выбранная отгрузка для просмотра
    private val _selectedShipmentId = MutableStateFlow<Long?>(null)
    val selectedShipmentProducts: Flow<List<ProductItem>> = _selectedShipmentId.flatMapLatest { id ->
        id?.let { dao.getProductItemsForShipment(it) } ?: flowOf(emptyList())
    }

    // Вспомогательный поток для отслеживания изменений
    private val _changesTrigger = MutableStateFlow(0)
    val changesTrigger: StateFlow<Int> = _changesTrigger.asStateFlow()

    // Вычисляемые остатки (учитывая двойной контроль)
    val productRemainders: StateFlow<Map<Long, Int>> = combine(
        _currentProducts,
        _currentPallets,
        _currentShipment
    ) { products, palletsMap, shipment ->
        products.associate { product ->
            val pallets = palletsMap[product.id] ?: emptyList()

            // Если двойной контроль включен, считаем только завезенные места
            val placesCount = if (shipment.doubleControlEnabled) {
                pallets.sumOf { if (it.isImported) it.places else 0 }
            } else {
                pallets.sumOf { it.places }
            }

            product.id to (product.quantity - placesCount)
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyMap()
    )

    // Общий остаток для транспорта
    val totalRemainder: StateFlow<Int> = productRemainders.map { remainders ->
        remainders.values.sum()
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        0
    )

    // Статистика двойного контроля
    val doubleControlStats: StateFlow<DoubleControlStats> = combine(
        _currentPallets,
        _currentShipment
    ) { palletsMap, shipment ->
        if (!shipment.doubleControlEnabled) {
            DoubleControlStats()
        } else {
            val allPallets = palletsMap.values.flatten()
            val totalPallets = allPallets.size
            val exportedPallets = allPallets.size // Все поддоны вывезены по умолчанию
            val importedPallets = allPallets.count { it.isImported }
            val totalPlaces = allPallets.sumOf { it.places }
            val exportedPlaces = totalPlaces // Все места вывезены по умолчанию
            val importedPlaces = allPallets.sumOf { if (it.isImported) it.places else 0 }

            DoubleControlStats(
                totalPallets = totalPallets,
                exportedPallets = exportedPallets,
                importedPallets = importedPallets,
                totalPlaces = totalPlaces,
                exportedPlaces = exportedPlaces,
                importedPlaces = importedPlaces
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        DoubleControlStats()
    )

    // Индивидуальная статистика двойного контроля по товарам
    val productDoubleControlStats: StateFlow<Map<Long, DoubleControlStats>> = combine(
        _currentProducts,
        _currentPallets,
        _currentShipment
    ) { products, palletsMap, shipment ->
        if (!shipment.doubleControlEnabled) {
            emptyMap()
        } else {
            products.associate { product ->
                val pallets = palletsMap[product.id] ?: emptyList()
                val totalPallets = pallets.size
                val importedPallets = pallets.count { it.isImported }
                val totalPlaces = pallets.sumOf { it.places }
                val importedPlaces = pallets.sumOf { if (it.isImported) it.places else 0 }

                product.id to DoubleControlStats(
                    totalPallets = totalPallets,
                    exportedPallets = totalPallets,
                    importedPallets = importedPallets,
                    totalPlaces = totalPlaces,
                    exportedPlaces = totalPlaces,
                    importedPlaces = importedPlaces
                )
            }
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyMap()
    )

    // Состояния валидации
    private val _containerValidation = MutableStateFlow<ValidationState>(ValidationState.EMPTY)
    val containerValidation: StateFlow<ValidationState> = _containerValidation.asStateFlow()

    private val _wagonValidation = MutableStateFlow<ValidationState>(ValidationState.EMPTY)
    val wagonValidation: StateFlow<ValidationState> = _wagonValidation.asStateFlow()

    // ========== СПРАВОЧНИКИ ==========
    fun getDictionaryItems(type: String): Flow<List<DictionaryItem>> {
        return dictionaryDao.getItemsByType(type)
    }

    // ========== ЗАПЛАНИРОВАННЫЕ ОТГРУЗКИ ==========
    fun loadFromScheduledShipment(scheduledShipmentId: Long) {
        viewModelScope.launch {
            // Сохраняем ID для последующего удаления
            _scheduledShipmentId.value = scheduledShipmentId

            val scheduledShipment = scheduledShipmentDao.getScheduledShipmentById(scheduledShipmentId)
            scheduledShipment?.let { shipment ->
                // Устанавливаем тип отгрузки
                _shipmentType.value = shipment.shipmentType

                // Заполняем общие поля
                _currentShipment.value = _currentShipment.value.copy(
                    customer = shipment.customer,
                    port = shipment.port,
                    vessel = shipment.vessel,
                    containerNumber = shipment.containerNumber,
                    wagonNumber = shipment.wagonNumber,
                    truckNumber = shipment.truckNumber,
                    trailerNumber = shipment.trailerNumber,
                    sealNumber = shipment.sealNumber
                )

                // Загружаем данные в зависимости от типа отгрузки
                when (shipment.shipmentType) {
                    "mono" -> loadMonoShipment(shipment)
                    "multi_port" -> loadMultiPortShipment(shipment)
                    "multi_vehicle" -> loadMultiVehicleShipment(shipment)
                }

                // Обновляем справочники для автодополнения
                updateDictionaries(shipment)

                showToast("Данные из запланированной отгрузки загружены")
            } ?: run {
                _scheduledShipmentId.value = null
                showToast("Запланированная отгрузка не найдена")
            }
        }
    }

    private fun loadMonoShipment(shipment: ScheduledShipment) {
        val scheduledProducts = shipment.getProducts()
        val productItems = scheduledProducts.mapIndexed { index, scheduledProduct ->
            ProductItem(
                id = System.currentTimeMillis() + index,
                name = scheduledProduct.name,
                manufacturer = scheduledProduct.manufacturer,
                batch = scheduledProduct.batch,
                packageWeight = scheduledProduct.packageWeight,
                quantity = scheduledProduct.quantity,
                totalWeight = scheduledProduct.totalWeight,
                palletCount = 0,
                placesCount = 0
            )
        }
        _currentProducts.value = productItems
    }

    private fun loadMultiPortShipment(shipment: ScheduledShipment) {
        val portDataList = shipment.getPortData()
        val ports = mutableListOf<MultiPort>()

        if (portDataList.isNotEmpty()) {
            portDataList.forEachIndexed { portIndex, portData ->
                val multiPort = MultiPort(
                    port = portData.portName,
                    vessel = portData.vessel.ifEmpty { shipment.vessel },
                    doubleControlEnabled = false
                )

                val portProducts = portData.products.mapIndexed { productIndex, scheduledProduct ->
                    MultiPortProduct(
                        id = System.currentTimeMillis() + portIndex * 1000L + productIndex,
                        name = scheduledProduct.name,
                        manufacturer = scheduledProduct.manufacturer,
                        batch = scheduledProduct.batch,
                        packageWeight = scheduledProduct.packageWeight,
                        quantity = scheduledProduct.quantity,
                        totalWeight = scheduledProduct.totalWeight,
                        palletCount = 0,
                        placesCount = 0,
                        pallets = emptyList()
                    )
                }

                ports.add(multiPort.copy(products = portProducts))
            }
        } else {
            if (shipment.ports.isNotEmpty()) {
                shipment.ports.forEachIndexed { index, portName ->
                    val multiPort = MultiPort(
                        port = portName,
                        vessel = shipment.vessel,
                        doubleControlEnabled = false
                    )

                    val scheduledProducts = shipment.getProducts()
                    val portProducts = scheduledProducts.mapIndexed { productIndex, scheduledProduct ->
                        MultiPortProduct(
                            id = System.currentTimeMillis() + index * 1000L + productIndex,
                            name = scheduledProduct.name,
                            manufacturer = scheduledProduct.manufacturer,
                            batch = scheduledProduct.batch,
                            packageWeight = scheduledProduct.packageWeight,
                            quantity = scheduledProduct.quantity,
                            totalWeight = scheduledProduct.totalWeight,
                            palletCount = 0,
                            placesCount = 0,
                            pallets = emptyList()
                        )
                    }

                    ports.add(multiPort.copy(products = portProducts))
                }
            } else {
                val multiPort = MultiPort(
                    port = shipment.port,
                    vessel = shipment.vessel,
                    doubleControlEnabled = false
                )

                val scheduledProducts = shipment.getProducts()
                val portProducts = scheduledProducts.mapIndexed { index, scheduledProduct ->
                    MultiPortProduct(
                        id = System.currentTimeMillis() + index,
                        name = scheduledProduct.name,
                        manufacturer = scheduledProduct.manufacturer,
                        batch = scheduledProduct.batch,
                        packageWeight = scheduledProduct.packageWeight,
                        quantity = scheduledProduct.quantity,
                        totalWeight = scheduledProduct.totalWeight,
                        palletCount = 0,
                        placesCount = 0,
                        pallets = emptyList()
                    )
                }

                ports.add(multiPort.copy(products = portProducts))
            }
        }

        _multiPorts.value = ports
    }

    private fun loadMultiVehicleShipment(shipment: ScheduledShipment) {
        val vehicleDataList = shipment.getVehicleData()
        val vehicles = mutableListOf<MultiVehicle>()

        if (vehicleDataList.isNotEmpty()) {
            vehicleDataList.forEachIndexed { vehicleIndex, vehicleData ->
                val vehicle = MultiVehicle(
                    wagonNumber = vehicleData.wagonNumber,
                    containerNumber = vehicleData.containerNumber,
                    truckNumber = vehicleData.truckNumber,
                    trailerNumber = vehicleData.trailerNumber,
                    sealNumber = vehicleData.sealNumber,
                    doubleControlEnabled = false
                )

                val vehicleProducts = vehicleData.products.mapIndexed { productIndex, scheduledProduct ->
                    MultiVehicleProduct(
                        id = System.currentTimeMillis() + vehicleIndex * 1000L + productIndex,
                        name = scheduledProduct.name,
                        manufacturer = scheduledProduct.manufacturer,
                        batch = scheduledProduct.batch,
                        packageWeight = scheduledProduct.packageWeight,
                        quantity = scheduledProduct.quantity,
                        totalWeight = scheduledProduct.totalWeight,
                        palletCount = 0,
                        placesCount = 0,
                        pallets = emptyList()
                    )
                }

                vehicles.add(vehicle.copy(products = vehicleProducts))
            }
        } else {
            for (i in 1..shipment.vehicleCount) {
                val suffix = if (shipment.vehicleCount > 1) "-$i" else ""
                val vehicle = MultiVehicle(
                    wagonNumber = if (shipment.wagonNumber.isNotEmpty()) "${shipment.wagonNumber}$suffix" else "",
                    containerNumber = if (shipment.containerNumber.isNotEmpty()) "${shipment.containerNumber}$suffix" else "",
                    truckNumber = if (shipment.truckNumber.isNotEmpty()) "${shipment.truckNumber}$suffix" else "",
                    trailerNumber = if (shipment.trailerNumber.isNotEmpty()) "${shipment.trailerNumber}$suffix" else "",
                    sealNumber = if (shipment.sealNumber.isNotEmpty()) "${shipment.sealNumber}$suffix" else "",
                    doubleControlEnabled = false
                )

                val scheduledProducts = shipment.getProducts()
                val vehicleProducts = scheduledProducts.mapIndexed { index, scheduledProduct ->
                    MultiVehicleProduct(
                        id = System.currentTimeMillis() + i * 1000L + index,
                        name = scheduledProduct.name,
                        manufacturer = scheduledProduct.manufacturer,
                        batch = scheduledProduct.batch,
                        packageWeight = scheduledProduct.packageWeight,
                        quantity = scheduledProduct.quantity,
                        totalWeight = scheduledProduct.totalWeight,
                        palletCount = 0,
                        placesCount = 0,
                        pallets = emptyList()
                    )
                }

                vehicles.add(vehicle.copy(products = vehicleProducts))
            }
        }

        _multiVehicles.value = vehicles
    }

    private fun updateDictionaries(shipment: ScheduledShipment) {
        if (shipment.customer.isNotEmpty()) {
            addDictionaryItem("customer", shipment.customer)
        }
        if (shipment.port.isNotEmpty()) {
            addDictionaryItem("port", shipment.port)
        }
        if (shipment.vessel.isNotEmpty()) {
            addDictionaryItem("vessel", shipment.vessel)
        }

        when (shipment.shipmentType) {
            "mono" -> {
                shipment.getProducts().forEach { product ->
                    if (product.name.isNotEmpty()) {
                        addDictionaryItem("product", product.name)
                    }
                    if (product.manufacturer.isNotEmpty()) {
                        addDictionaryItem("manufacturer", product.manufacturer)
                    }
                }
            }
            "multi_port" -> {
                shipment.getPortData().forEach { portData ->
                    portData.products.forEach { product ->
                        if (product.name.isNotEmpty()) {
                            addDictionaryItem("product", product.name)
                        }
                        if (product.manufacturer.isNotEmpty()) {
                            addDictionaryItem("manufacturer", product.manufacturer)
                        }
                    }
                }
            }
            "multi_vehicle" -> {
                shipment.getVehicleData().forEach { vehicleData ->
                    vehicleData.products.forEach { product ->
                        if (product.name.isNotEmpty()) {
                            addDictionaryItem("product", product.name)
                        }
                        if (product.manufacturer.isNotEmpty()) {
                            addDictionaryItem("manufacturer", product.manufacturer)
                        }
                    }
                }
            }
        }
    }

    fun clearScheduledShipmentId() {
        _scheduledShipmentId.value = null
    }

    private suspend fun deleteScheduledShipmentIfNeeded() {
        _scheduledShipmentId.value?.let { scheduledId ->
            try {
                val shipment = scheduledShipmentDao.getScheduledShipmentById(scheduledId)
                shipment?.let {
                    scheduledShipmentDao.deleteScheduledShipment(it)
                }

                _scheduledShipmentId.value = null
                showToast("Запланированная отгрузка удалена")
            } catch (e: Exception) {
                showToast("Ошибка при удалении запланированной отгрузки: ${e.message}")
            }
        }
    }

    // ========== АРХИВ ОТГРУЗОК ==========
    fun loadShipment(id: Long) {
        viewModelScope.launch {
            val shipment = dao.getShipmentById(id)
            shipment?.let {
                _currentShipment.value = it
                loadProductsForShipment(id)
            }
        }
    }

    fun updateShipmentField(field: String, value: String) {
        _currentShipment.update { current ->
            when (field) {
                "customer" -> current.copy(customer = value)
                "port" -> current.copy(port = value)
                "vessel" -> current.copy(vessel = value)
                "container" -> {
                    val updated = current.copy(containerNumber = value)
                    if (value.isNotEmpty()) {
                        _wagonValidation.value = ValidationState.EMPTY
                        updated.copy(wagonNumber = "")
                    }
                    val validationState = ContainerWagonValidator.validateContainerNumberLive(value)
                    _containerValidation.value = validationState

                    when (validationState) {
                        is ValidationState.INVALID -> {
                            if (value.length > 11) {
                                showToast("Номер контейнера: 4 буквы + 7 цифр")
                            } else if (!value.matches(Regex("^[A-Z]{0,4}\\d{0,7}$"))) {
                                showToast("Номер контейнера: 4 буквы + 7 цифр")
                            }
                        }
                        is ValidationState.INVALID_WITH_SUGGESTION -> {
                            showToast("Неверный номер контейнера")
                        }
                        else -> {}
                    }

                    if (value.isNotEmpty()) {
                        _wagonValidation.value = ValidationState.EMPTY
                        updated.copy(wagonNumber = "")
                    } else {
                        updated
                    }
                }
                "truck" -> {
                    val updated = current.copy(truckNumber = value)
                    if (value.isNotEmpty()) {
                        _wagonValidation.value = ValidationState.EMPTY
                        updated.copy(wagonNumber = "")
                    } else {
                        updated
                    }
                }
                "trailer" -> {
                    val updated = current.copy(trailerNumber = value)
                    if (value.isNotEmpty()) {
                        _wagonValidation.value = ValidationState.EMPTY
                        updated.copy(wagonNumber = "")
                    } else {
                        updated
                    }
                }
                "wagon" -> {
                    val updated = current.copy(wagonNumber = value)
                    if (value.isNotEmpty()) {
                        _containerValidation.value = ValidationState.EMPTY
                        updated.copy(
                            containerNumber = "",
                            truckNumber = "",
                            trailerNumber = ""
                        )
                    }
                    val validationState = ContainerWagonValidator.validateWagonNumberLive(value)
                    _wagonValidation.value = validationState

                    when (validationState) {
                        is ValidationState.INVALID -> {
                            if (value.length > 8) {
                                showToast("Номер вагона: 8 цифр")
                            } else if (!value.matches(Regex("^\\d{0,8}$"))) {
                                showToast("Номер вагона: 8 цифр")
                            }
                        }
                        is ValidationState.INVALID_WITH_SUGGESTION -> {
                            showToast("Неверный номер вагона")
                        }
                        else -> {}
                    }

                    if (value.isNotEmpty()) {
                        _containerValidation.value = ValidationState.EMPTY
                        updated.copy(
                            containerNumber = "",
                            truckNumber = "",
                            trailerNumber = ""
                        )
                    } else {
                        updated
                    }
                }
                "seal" -> current.copy(sealNumber = value)
                else -> current
            }
        }

        when (field) {
            "container" -> {
                _containerValidation.value = ContainerWagonValidator.validateContainerNumberLive(value)
            }
            "wagon" -> {
                _wagonValidation.value = ContainerWagonValidator.validateWagonNumberLive(value)
            }
        }

        scheduleAutoSave()
    }

    // Измененная функция - только обновляет использование
    fun saveDictionaryField(field: String, value: String) {
        if (value.isNotBlank()) {
            viewModelScope.launch {
                updateDictionaryUsage(field, value)
            }
        }
    }

    fun updateProductItem(productId: Long, field: String, value: Any) {
        val updatedProducts = _currentProducts.value.map { product ->
            if (product.id == productId) {
                when (field) {
                    "name" -> {
                        val name = value as String
                        product.copy(name = name)
                    }
                    "manufacturer" -> {
                        val manufacturer = value as String
                        product.copy(manufacturer = manufacturer)
                    }
                    "packageWeight" -> {
                        val weight = when (value) {
                            is Double -> value
                            is Int -> value.toDouble()
                            is String -> value.toDoubleOrNull() ?: 0.0
                            else -> 0.0
                        }
                        val totalWeight = weight * product.quantity
                        product.copy(
                            packageWeight = weight,
                            totalWeight = totalWeight
                        )
                    }
                    "quantity" -> {
                        val quantity = when (value) {
                            is Int -> value
                            is Double -> value.toInt()
                            is String -> value.toIntOrNull() ?: 0
                            else -> 0
                        }
                        val totalWeight = product.packageWeight * quantity
                        product.copy(
                            quantity = quantity,
                            totalWeight = totalWeight
                        )
                    }
                    "batch" -> {
                        product.copy(batch = value as String)
                    }
                    else -> product
                }
            } else {
                product
            }
        }
        _currentProducts.value = updatedProducts
        updateProductPalletInfo(productId)
        scheduleAutoSave()
    }

    fun saveProductDictionaryField(field: String, value: String) {
        if (value.isNotBlank()) {
            viewModelScope.launch {
                when (field) {
                    "product" -> updateDictionaryUsage("product", value)
                    "manufacturer" -> updateDictionaryUsage("manufacturer", value)
                }
            }
        }
    }

    // ========== СОХРАНЕНИЕ ОТГРУЗКИ ==========
    fun saveShipment() {
        viewModelScope.launch {
            val shipmentType = _shipmentType.value
            var totalProductTypes = 0
            var totalPallets = 0
            var totalPlaces = 0
            var totalWeight = 0.0

            when (shipmentType) {
                "mono" -> {
                    val doubleControlEnabled = _currentShipment.value.doubleControlEnabled
                    if (doubleControlEnabled) {
                        val allPallets = _currentPallets.value.values.flatten()
                        val notImportedPallets = allPallets.count { !it.isImported }
                        if (notImportedPallets > 0) {
                            // Можно выбросить исключение или показать ошибку
                        }
                    }

                    val products = _currentProducts.value
                    totalProductTypes = products.size
                    products.forEach { product ->
                        totalPallets += product.palletCount
                        totalPlaces += product.placesCount
                        totalWeight += product.totalWeight
                    }

                    val shipmentToSave = _currentShipment.value.copy(
                        totalProductTypes = totalProductTypes,
                        totalPallets = totalPallets,
                        totalPlaces = totalPlaces,
                        totalWeight = totalWeight,
                        shipmentType = shipmentType,
                        createdAt = Date()
                    )

                    val shipmentId = dao.insertShipment(shipmentToSave)

                    // Сохраняем товары
                    products.forEach { product ->
                        val productWithShipmentId = product.copy(
                            id = 0,
                            shipmentId = shipmentId
                        )
                        val productId = dao.insertProductItem(productWithShipmentId)

                        // Сохраняем поддоны
                        val pallets = _currentPallets.value[product.id] ?: emptyList()
                        pallets.forEach { pallet ->
                            val palletWithProductId = pallet.copy(
                                id = 0,
                                productItemId = productId,
                                isExported = true,
                                exportedPlaces = pallet.places
                            )
                            dao.insertPallet(palletWithProductId)
                        }

                        // Обновляем использование в словаре
                        if (product.name.isNotBlank()) {
                            updateDictionaryUsage("product", product.name)
                        }
                        if (product.manufacturer.isNotBlank()) {
                            updateDictionaryUsage("manufacturer", product.manufacturer)
                        }
                    }

                    // Удаляем запланированную отгрузку
                    deleteScheduledShipmentIfNeeded()

                    // Удаляем текущий черновик после сохранения отгрузки
                    _currentDraftId.value?.let { draftId ->
                        draftManager.deleteDraft(draftId)
                        _currentDraftId.value = null
                    }

                    // Сбрасываем данные
                    resetCurrentData()

                    showToast("Отгрузка сохранена")
                }
                "multi_port" -> {
                    val ports = _multiPorts.value
                    ports.forEach { port ->
                        totalProductTypes += port.products.size
                        port.products.forEach { product ->
                            totalPallets += product.palletCount
                            totalPlaces += product.placesCount
                            totalWeight += product.totalWeight
                        }
                    }

                    val shipmentToSave = _currentShipment.value.copy(
                        totalProductTypes = totalProductTypes,
                        totalPallets = totalPallets,
                        totalPlaces = totalPlaces,
                        totalWeight = totalWeight,
                        shipmentType = shipmentType,
                        multiPortData = Json.encodeToString(ports),
                        createdAt = Date()
                    )

                    dao.insertShipment(shipmentToSave)

                    // Удаляем запланированную отгрузку
                    deleteScheduledShipmentIfNeeded()

                    // Удаляем текущий черновик
                    _currentDraftId.value?.let { draftId ->
                        draftManager.deleteDraft(draftId)
                        _currentDraftId.value = null
                    }

                    resetCurrentData()
                    showToast("Отгрузка сохранена")
                }
                "multi_vehicle" -> {
                    val vehicles = _multiVehicles.value
                    vehicles.forEach { vehicle ->
                        totalProductTypes += vehicle.products.size
                        vehicle.products.forEach { product ->
                            totalPallets += product.palletCount
                            totalPlaces += product.placesCount
                            totalWeight += product.totalWeight
                        }
                    }

                    val shipmentToSave = _currentShipment.value.copy(
                        totalProductTypes = totalProductTypes,
                        totalPallets = totalPallets,
                        totalPlaces = totalPlaces,
                        totalWeight = totalWeight,
                        shipmentType = shipmentType,
                        multiVehicleData = Json.encodeToString(vehicles),
                        createdAt = Date()
                    )

                    dao.insertShipment(shipmentToSave)

                    // Удаляем запланированную отгрузку
                    deleteScheduledShipmentIfNeeded()

                    // Удаляем текущий черновик
                    _currentDraftId.value?.let { draftId ->
                        draftManager.deleteDraft(draftId)
                        _currentDraftId.value = null
                    }

                    resetCurrentData()
                    showToast("Отгрузка сохранена")
                }
            }
        }
    }

    fun deleteShipment(shipment: Shipment) {
        viewModelScope.launch {
            dao.deleteShipment(shipment)
            showToast("Отгрузка удалена")
        }
    }

    // ========== ТОВАРЫ (MONO РЕЖИМ) ==========
    fun addProductItem() {
        val newProduct = ProductItem(
            id = System.currentTimeMillis(),
            name = "",
            manufacturer = "",
            packageWeight = 0.0,
            quantity = 0,
            totalWeight = 0.0,
            palletCount = 0,
            placesCount = 0
        )

        val currentList = _currentProducts.value.toMutableList()
        currentList.add(newProduct)
        _currentProducts.value = currentList
        scheduleAutoSave()
    }

    fun deleteProductItem(productId: Long) {
        val filteredProducts = _currentProducts.value.filter { it.id != productId }
        _currentProducts.value = filteredProducts

        val filteredPallets = _currentPallets.value.filterKeys { it != productId }
        _currentPallets.value = filteredPallets
        scheduleAutoSave()
    }

    // ========== ПОДДОНЫ (MONO РЕЖИМ) ==========
    fun addPallet(productItemId: Long) {
        val currentPalletsForProduct = _currentPallets.value[productItemId] ?: emptyList()
        val palletNumber = currentPalletsForProduct.size + 1

        val newPallet = Pallet(
            id = System.currentTimeMillis(),
            productItemId = productItemId,
            palletNumber = palletNumber,
            places = 0,
            isExported = true,
            exportedPlaces = 0,
            isImported = false,
            importedPlaces = 0
        )

        val updatedMap = _currentPallets.value.toMutableMap()
        val updatedList = currentPalletsForProduct.toMutableList()
        updatedList.add(newPallet)
        updatedMap[productItemId] = updatedList
        _currentPallets.value = updatedMap

        updateProductPalletInfo(productItemId)
        scheduleAutoSave()
    }

    fun updatePalletPlaces(productItemId: Long, palletId: Long, places: Int) {
        val updatedMap = _currentPallets.value.toMutableMap()
        val productPallets = updatedMap[productItemId] ?: emptyList()
        val updatedPallets = productPallets.map { pallet ->
            if (pallet.id == palletId) {
                pallet.copy(
                    places = places,
                    exportedPlaces = if (pallet.isExported) places else 0,
                    importedPlaces = if (pallet.isImported) places else 0
                )
            } else {
                pallet
            }
        }
        updatedMap[productItemId] = updatedPallets
        _currentPallets.value = updatedMap

        updateProductPalletInfo(productItemId)
        scheduleAutoSave()
    }

    fun deletePallet(productItemId: Long, palletId: Long) {
        val updatedMap = _currentPallets.value.toMutableMap()
        val productPallets = updatedMap[productItemId] ?: emptyList()
        val updatedPallets = productPallets.filter { it.id != palletId }
            .mapIndexed { index, pallet -> pallet.copy(palletNumber = index + 1) }

        updatedMap[productItemId] = updatedPallets
        _currentPallets.value = updatedMap

        updateProductPalletInfo(productItemId)
        scheduleAutoSave()
    }

    // ========== ДВОЙНОЙ КОНТРОЛЬ ==========
    fun toggleDoubleControl() {
        _currentShipment.update { current ->
            current.copy(doubleControlEnabled = !current.doubleControlEnabled)
        }
        scheduleAutoSave()
    }

    fun togglePalletImported(productItemId: Long, palletId: Long) {
        val updatedMap = _currentPallets.value.toMutableMap()
        val productPallets = updatedMap[productItemId] ?: emptyList()
        val updatedPallets = productPallets.map { pallet ->
            if (pallet.id == palletId) {
                val newImported = !pallet.isImported
                pallet.copy(
                    isImported = newImported,
                    importedPlaces = if (newImported) pallet.places else 0
                )
            } else {
                pallet
            }
        }
        updatedMap[productItemId] = updatedPallets
        _currentPallets.value = updatedMap
        scheduleAutoSave()
    }

    // ========== ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ ==========
    private fun loadProductsForShipment(shipmentId: Long) {
        viewModelScope.launch {
            dao.getProductItemsForShipment(shipmentId).collect { products ->
                _currentProducts.value = products

                products.forEach { product ->
                    dao.getPalletsForProductItem(product.id).collect { pallets ->
                        val currentMap = _currentPallets.value.toMutableMap()
                        currentMap[product.id] = pallets
                        _currentPallets.value = currentMap
                    }
                }
            }
        }
    }

    private fun updateProductPalletInfo(productItemId: Long) {
        val pallets = _currentPallets.value[productItemId] ?: emptyList()
        val palletCount = pallets.size
        val placesCount = pallets.sumOf { it.places }

        val updatedProducts = _currentProducts.value.map { product ->
            if (product.id == productItemId) {
                product.copy(
                    palletCount = palletCount,
                    placesCount = placesCount
                )
            } else {
                product
            }
        }
        _currentProducts.value = updatedProducts
    }

    private fun resetCurrentData() {
        _currentDraftId.value = null
        _currentShipment.value = Shipment()
        _currentProducts.value = emptyList()
        _currentPallets.value = emptyMap()
        _shipmentType.value = "mono"
        _multiPorts.value = emptyList()
        _multiVehicles.value = emptyList()
        _scheduledShipmentId.value = null
    }

    fun setSelectedShipmentId(id: Long?) {
        _selectedShipmentId.value = id
    }

    fun setShipmentType(type: String) {
        _shipmentType.value = type
        scheduleAutoSave()
    }

    // ========== СЛОВАРИ ==========
    fun addDictionaryItem(type: String, value: String) {
        viewModelScope.launch {
            val existingItem = dictionaryDao.getItemByTypeAndValue(type, value)
            if (existingItem == null) {
                dictionaryDao.insertDictionaryItem(
                    DictionaryItem(
                        type = type,
                        value = value,
                        lastUsed = System.currentTimeMillis(),
                        usageCount = 1
                    )
                )
                showToast("'$value' добавлено в словарь")
            } else {
                updateDictionaryItemUsage(existingItem)
            }
        }
    }

    private suspend fun updateDictionaryUsage(type: String, value: String) {
        val existingItem = dictionaryDao.getItemByTypeAndValue(type, value)
        if (existingItem != null) {
            updateDictionaryItemUsage(existingItem)
        }
    }

    private suspend fun updateDictionaryItemUsage(item: DictionaryItem) {
        dictionaryDao.updateDictionaryItem(
            item.copy(
                lastUsed = System.currentTimeMillis(),
                usageCount = item.usageCount + 1
            )
        )
    }

    // Методы для работы со справочниками (для TemplatesScreen)
    fun deleteDictionaryItem(item: DictionaryItem) {
        viewModelScope.launch {
            dictionaryDao.deleteDictionaryItem(item)
        }
    }

    fun updateDictionaryItem(item: DictionaryItem) {
        viewModelScope.launch {
            dictionaryDao.updateDictionaryItem(item)
        }
    }

    suspend fun getDictionaryItemById(id: Long): DictionaryItem? {
        return dictionaryDao.getItemById(id)
    }

    // ========== МУЛЬТИПОРТ ==========
    fun addMultiPort() {
        _multiPorts.value = _multiPorts.value + MultiPort()
        scheduleAutoSave()
    }

    fun updateMultiPort(portId: Long, field: String, value: String) {
        _multiPorts.value = _multiPorts.value.map { port ->
            if (port.id == portId) {
                when (field) {
                    "port" -> port.copy(port = value)
                    "vessel" -> port.copy(vessel = value)
                    else -> port
                }
            } else {
                port
            }
        }
        scheduleAutoSave()
    }

    fun deleteMultiPort(portId: Long) {
        _multiPorts.value = _multiPorts.value.filter { it.id != portId }
        scheduleAutoSave()
    }

    fun addMultiPortProduct(portId: Long) {
        _multiPorts.value = _multiPorts.value.map { port ->
            if (port.id == portId) {
                port.copy(products = port.products + MultiPortProduct())
            } else {
                port
            }
        }
        scheduleAutoSave()
    }

    fun updateMultiPortProduct(portId: Long, productId: Long, field: String, value: Any) {
        _multiPorts.value = _multiPorts.value.map { port ->
            if (port.id == portId) {
                port.copy(products = port.products.map { product ->
                    if (product.id == productId) {
                        when (field) {
                            "name" -> product.copy(name = value as String)
                            "manufacturer" -> product.copy(manufacturer = value as String)
                            "batch" -> product.copy(batch = value as String)
                            "packageWeight" -> {
                                val weight = when (value) {
                                    is Double -> value
                                    is Int -> value.toDouble()
                                    is String -> value.toDoubleOrNull() ?: 0.0
                                    else -> 0.0
                                }
                                val totalWeight = weight * product.quantity
                                product.copy(
                                    packageWeight = weight,
                                    totalWeight = totalWeight
                                )
                            }
                            "quantity" -> {
                                val quantity = when (value) {
                                    is Int -> value
                                    is Double -> value.toInt()
                                    is String -> value.toIntOrNull() ?: 0
                                    else -> 0
                                }
                                val totalWeight = product.packageWeight * quantity
                                product.copy(
                                    quantity = quantity,
                                    totalWeight = totalWeight
                                )
                            }
                            else -> product
                        }
                    } else {
                        product
                    }
                })
            } else {
                port
            }
        }
        scheduleAutoSave()
    }

    fun deleteMultiPortProduct(portId: Long, productId: Long) {
        _multiPorts.value = _multiPorts.value.map { port ->
            if (port.id == portId) {
                port.copy(products = port.products.filter { it.id != productId })
            } else {
                port
            }
        }
        scheduleAutoSave()
    }

    fun addMultiPortPallet(portId: Long, productId: Long) {
        _multiPorts.value = _multiPorts.value.map { port ->
            if (port.id == portId) {
                port.copy(products = port.products.map { product ->
                    if (product.id == productId) {
                        val palletNumber = product.pallets.size + 1
                        val newPallet = MultiPortPallet(palletNumber = palletNumber)
                        val updatedPallets = product.pallets + newPallet
                        val updatedPlaces = updatedPallets.sumOf { it.places }
                        product.copy(
                            pallets = updatedPallets,
                            palletCount = updatedPallets.size,
                            placesCount = updatedPlaces,
                            totalWeight = product.packageWeight * product.quantity
                        )
                    } else {
                        product
                    }
                })
            } else {
                port
            }
        }
        scheduleAutoSave()
    }

    fun updateMultiPortPalletPlaces(portId: Long, productId: Long, palletId: Long, places: Int) {
        _multiPorts.value = _multiPorts.value.map { port ->
            if (port.id == portId) {
                port.copy(products = port.products.map { product ->
                    if (product.id == productId) {
                        val updatedPallets = product.pallets.map { pallet ->
                            if (pallet.id == palletId) {
                                pallet.copy(places = places)
                            } else {
                                pallet
                            }
                        }
                        val updatedPlaces = updatedPallets.sumOf { it.places }
                        product.copy(
                            pallets = updatedPallets,
                            placesCount = updatedPlaces,
                            totalWeight = product.packageWeight * product.quantity
                        )
                    } else {
                        product
                    }
                })
            } else {
                port
            }
        }
        scheduleAutoSave()
    }

    fun deleteMultiPortPallet(portId: Long, productId: Long, palletId: Long) {
        _multiPorts.value = _multiPorts.value.map { port ->
            if (port.id == portId) {
                port.copy(products = port.products.map { product ->
                    if (product.id == productId) {
                        val updatedPallets = product.pallets.filter { it.id != palletId }
                            .mapIndexed { index, pallet -> pallet.copy(palletNumber = index + 1) }
                        val updatedPlaces = updatedPallets.sumOf { it.places }
                        product.copy(
                            pallets = updatedPallets,
                            palletCount = updatedPallets.size,
                            placesCount = updatedPlaces,
                            totalWeight = product.packageWeight * product.quantity
                        )
                    } else {
                        product
                    }
                })
            } else {
                port
            }
        }
        scheduleAutoSave()
    }

    fun toggleMultiPortPalletImported(portId: Long, productId: Long, palletId: Long) {
        _multiPorts.value = _multiPorts.value.map { port ->
            if (port.id == portId) {
                port.copy(products = port.products.map { product ->
                    if (product.id == productId) {
                        val updatedPallets = product.pallets.map { pallet ->
                            if (pallet.id == palletId) {
                                pallet.copy(isImported = !pallet.isImported)
                            } else {
                                pallet
                            }
                        }
                        product.copy(pallets = updatedPallets)
                    } else {
                        product
                    }
                })
            } else {
                port
            }
        }
        scheduleAutoSave()
    }

    fun toggleMultiPortDoubleControl(portId: Long) {
        _multiPorts.value = _multiPorts.value.map { port ->
            if (port.id == portId) {
                port.copy(doubleControlEnabled = !port.doubleControlEnabled)
            } else {
                port
            }
        }
        scheduleAutoSave()
    }

    // ========== МУЛЬТИАВТО ==========
    fun addMultiVehicle() {
        _multiVehicles.value = _multiVehicles.value + MultiVehicle()
        scheduleAutoSave()
    }

    fun updateMultiVehicle(vehicleId: Long, field: String, value: String) {
        _multiVehicles.value = _multiVehicles.value.map { vehicle ->
            if (vehicle.id == vehicleId) {
                when (field) {
                    "wagon" -> vehicle.copy(wagonNumber = value)
                    "container" -> vehicle.copy(containerNumber = value)
                    "truck" -> vehicle.copy(truckNumber = value)
                    "trailer" -> vehicle.copy(trailerNumber = value)
                    "seal" -> vehicle.copy(sealNumber = value)
                    else -> vehicle
                }
            } else {
                vehicle
            }
        }
        scheduleAutoSave()
    }

    fun deleteMultiVehicle(vehicleId: Long) {
        _multiVehicles.value = _multiVehicles.value.filter { it.id != vehicleId }
        scheduleAutoSave()
    }

    fun toggleMultiVehicleDoubleControl(vehicleId: Long) {
        _multiVehicles.value = _multiVehicles.value.map { vehicle ->
            if (vehicle.id == vehicleId) {
                vehicle.copy(doubleControlEnabled = !vehicle.doubleControlEnabled)
            } else {
                vehicle
            }
        }
        scheduleAutoSave()
    }

    fun addMultiVehicleProduct(vehicleId: Long) {
        _multiVehicles.value = _multiVehicles.value.map { vehicle ->
            if (vehicle.id == vehicleId) {
                vehicle.copy(products = vehicle.products + MultiVehicleProduct())
            } else {
                vehicle
            }
        }
        scheduleAutoSave()
    }

    fun updateMultiVehicleProduct(vehicleId: Long, productId: Long, field: String, value: Any) {
        _multiVehicles.value = _multiVehicles.value.map { vehicle ->
            if (vehicle.id == vehicleId) {
                vehicle.copy(products = vehicle.products.map { product ->
                    if (product.id == productId) {
                        when (field) {
                            "name" -> product.copy(name = value as String)
                            "manufacturer" -> product.copy(manufacturer = value as String)
                            "batch" -> product.copy(batch = value as String)
                            "packageWeight" -> {
                                val weight = when (value) {
                                    is Double -> value
                                    is Int -> value.toDouble()
                                    is String -> value.toDoubleOrNull() ?: 0.0
                                    else -> 0.0
                                }
                                val totalWeight = weight * product.quantity
                                product.copy(
                                    packageWeight = weight,
                                    totalWeight = totalWeight
                                )
                            }
                            "quantity" -> {
                                val quantity = when (value) {
                                    is Int -> value
                                    is Double -> value.toInt()
                                    is String -> value.toIntOrNull() ?: 0
                                    else -> 0
                                }
                                val totalWeight = product.packageWeight * quantity
                                product.copy(
                                    quantity = quantity,
                                    totalWeight = totalWeight
                                )
                            }
                            else -> product
                        }
                    } else {
                        product
                    }
                })
            } else {
                vehicle
            }
        }
        scheduleAutoSave()
    }

    fun deleteMultiVehicleProduct(vehicleId: Long, productId: Long) {
        _multiVehicles.value = _multiVehicles.value.map { vehicle ->
            if (vehicle.id == vehicleId) {
                vehicle.copy(products = vehicle.products.filter { it.id != productId })
            } else {
                vehicle
            }
        }
        scheduleAutoSave()
    }

    fun addMultiVehiclePallet(vehicleId: Long, productId: Long) {
        _multiVehicles.value = _multiVehicles.value.map { vehicle ->
            if (vehicle.id == vehicleId) {
                vehicle.copy(products = vehicle.products.map { product ->
                    if (product.id == productId) {
                        val palletNumber = product.pallets.size + 1
                        val newPallet = MultiVehiclePallet(palletNumber = palletNumber)
                        val updatedPallets = product.pallets + newPallet
                        val updatedPlaces = updatedPallets.sumOf { it.places }
                        product.copy(
                            pallets = updatedPallets,
                            palletCount = updatedPallets.size,
                            placesCount = updatedPlaces,
                            totalWeight = product.packageWeight * product.quantity
                        )
                    } else {
                        product
                    }
                })
            } else {
                vehicle
            }
        }
        scheduleAutoSave()
    }

    fun updateMultiVehiclePalletPlaces(vehicleId: Long, productId: Long, palletId: Long, places: Int) {
        _multiVehicles.value = _multiVehicles.value.map { vehicle ->
            if (vehicle.id == vehicleId) {
                vehicle.copy(products = vehicle.products.map { product ->
                    if (product.id == productId) {
                        val updatedPallets = product.pallets.map { pallet ->
                            if (pallet.id == palletId) {
                                pallet.copy(places = places)
                            } else {
                                pallet
                            }
                        }
                        val updatedPlaces = updatedPallets.sumOf { it.places }
                        product.copy(
                            pallets = updatedPallets,
                            placesCount = updatedPlaces,
                            totalWeight = product.packageWeight * product.quantity
                        )
                    } else {
                        product
                    }
                })
            } else {
                vehicle
            }
        }
        scheduleAutoSave()
    }

    fun deleteMultiVehiclePallet(vehicleId: Long, productId: Long, palletId: Long) {
        _multiVehicles.value = _multiVehicles.value.map { vehicle ->
            if (vehicle.id == vehicleId) {
                vehicle.copy(products = vehicle.products.map { product ->
                    if (product.id == productId) {
                        val updatedPallets = product.pallets.filter { it.id != palletId }
                            .mapIndexed { index, pallet -> pallet.copy(palletNumber = index + 1) }
                        val updatedPlaces = updatedPallets.sumOf { it.places }
                        product.copy(
                            pallets = updatedPallets,
                            palletCount = updatedPallets.size,
                            placesCount = updatedPlaces,
                            totalWeight = product.packageWeight * product.quantity
                        )
                    } else {
                        product
                    }
                })
            } else {
                vehicle
            }
        }
        scheduleAutoSave()
    }

    fun toggleMultiVehiclePalletImported(vehicleId: Long, productId: Long, palletId: Long) {
        _multiVehicles.value = _multiVehicles.value.map { vehicle ->
            if (vehicle.id == vehicleId) {
                vehicle.copy(products = vehicle.products.map { product ->
                    if (product.id == productId) {
                        val updatedPallets = product.pallets.map { pallet ->
                            if (pallet.id == palletId) {
                                pallet.copy(isImported = !pallet.isImported)
                            } else {
                                pallet
                            }
                        }
                        product.copy(pallets = updatedPallets)
                    } else {
                        product
                    }
                })
            } else {
                vehicle
            }
        }
        scheduleAutoSave()
    }

    // ========== ЧЕРНОВИКИ ==========
    fun saveDraft(): Long {
        val draftId = draftManager.saveDraft(
            id = _currentDraftId.value,
            shipment = _currentShipment.value,
            products = _currentProducts.value,
            pallets = _currentPallets.value,
            shipmentType = _shipmentType.value,
            multiPorts = _multiPorts.value,
            multiVehicles = _multiVehicles.value
        )

        if (draftId != 0L) {
            _currentDraftId.value = draftId
        }

        return draftId
    }

    // Загрузка черновика по ID
    fun loadDraftById(draftId: Long) {
        viewModelScope.launch {
            val draft = draftManager.getDraft(draftId)
            draft?.let { draftData ->
                _currentDraftId.value = draftId
                _currentShipment.value = draftData.shipment
                _currentProducts.value = draftData.products
                _currentPallets.value = draftData.pallets
                _shipmentType.value = draftData.shipmentType
                _multiPorts.value = draftData.multiPorts
                _multiVehicles.value = draftData.multiVehicles
                _scheduledShipmentId.value = null
            }
        }
    }

    // Загрузка последнего черновика (для обратной совместимости)
    fun loadDraft() {
        viewModelScope.launch {
            val draft = draftManager.getLastDraft()
            draft?.let { draftData ->
                _currentDraftId.value = draftData.id
                _currentShipment.value = draftData.shipment
                _currentProducts.value = draftData.products
                _currentPallets.value = draftData.pallets
                _shipmentType.value = draftData.shipmentType
                _multiPorts.value = draftData.multiPorts
                _multiVehicles.value = draftData.multiVehicles
                _scheduledShipmentId.value = null
            }
        }
    }

    suspend fun hasDraft(): Boolean {
        return draftManager.hasDrafts()
    }

    // Получение всех черновиков
    fun getAllDrafts(): List<DraftData> {
        return draftManager.getDrafts()
    }

    // Удаление черновика
    fun deleteDraft(draftId: Long) {
        viewModelScope.launch {
            draftManager.deleteDraft(draftId)
            if (_currentDraftId.value == draftId) {
                _currentDraftId.value = null
            }
        }
    }

    // Начало новой отгрузки (сброс текущих данных)
    fun startNewShipment() {
        _currentDraftId.value = null
        _currentShipment.value = Shipment()
        _currentProducts.value = emptyList()
        _currentPallets.value = emptyMap()
        _shipmentType.value = "mono"
        _multiPorts.value = emptyList()
        _multiVehicles.value = emptyList()
        _scheduledShipmentId.value = null
    }

    private fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(autoSaveDelay)
            saveDraft()
        }
        _changesTrigger.value++
    }

    fun validateContainerAndShowError(containerNumber: String) {
        val validation = ContainerWagonValidator.validateContainerNumberLive(containerNumber)
        when (validation) {
            is ValidationState.INVALID -> {
                if (containerNumber.length > 11) {
                    showToast("Номер контейнера: 4 буквы + 7 цифр")
                } else if (!containerNumber.matches(Regex("^[A-Z]{0,4}\\d{0,7}$"))) {
                    showToast("Номер контейнера: 4 буквы + 7 цифр")
                }
            }
            is ValidationState.INVALID_WITH_SUGGESTION -> {
                showToast("Неверный номер контейнера")
            }
            else -> {}
        }
    }

    fun validateWagonAndShowError(wagonNumber: String) {
        val validation = ContainerWagonValidator.validateWagonNumberLive(wagonNumber)
        when (validation) {
            is ValidationState.INVALID -> {
                if (wagonNumber.length > 8) {
                    showToast("Номер вагона: 8 цифр")
                } else if (!wagonNumber.matches(Regex("^\\d{0,8}$"))) {
                    showToast("Номер вагона: 8 цифр")
                }
            }
            is ValidationState.INVALID_WITH_SUGGESTION -> {
                showToast("Неверный номер вагона")
            }
            else -> {}
        }
    }

    fun updateMultiVehicleWithValidation(vehicleId: Long, field: String, value: String) {
        updateMultiVehicle(vehicleId, field, value)
        when (field) {
            "container" -> validateContainerAndShowError(value)
            "wagon" -> validateWagonAndShowError(value)
        }
    }

    private fun showToast(message: String) {
        viewModelScope.launch(Dispatchers.Main) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}