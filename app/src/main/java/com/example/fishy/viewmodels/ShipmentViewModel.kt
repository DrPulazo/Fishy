package com.example.fishy.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.*
import com.example.fishy.database.*
import com.example.fishy.database.entities.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

class ShipmentViewModel(private val database: AppDatabase) : ViewModel() {

    private val dao = database.shipmentDao()

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

    // Вычисляемые остатки
    val productRemainders: StateFlow<Map<Long, Int>> = combine(
        _currentProducts,
        _currentPallets
    ) { products, palletsMap ->
        products.associate { product ->
            val pallets = palletsMap[product.id] ?: emptyList()
            val placesCount = pallets.sumOf { it.places }
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

    // Выбранная отгрузка для просмотра
    private val _selectedShipmentId = MutableStateFlow<Long?>(null)
    val selectedShipmentProducts: Flow<List<ProductItem>> = _selectedShipmentId.flatMapLatest { id ->
        id?.let { dao.getProductItemsForShipment(it) } ?: flowOf(emptyList())
    }

    // Функции для работы с отгрузками
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
        _currentShipment.update { shipment ->
            shipment.copy(
                containerNumber = if (field == "container") value else shipment.containerNumber,
                truckNumber = if (field == "truck") value else shipment.truckNumber,
                trailerNumber = if (field == "trailer") value else shipment.trailerNumber,
                wagonNumber = if (field == "wagon") value else shipment.wagonNumber,
                sealNumber = if (field == "seal") value else shipment.sealNumber,
                port = if (field == "port") value else shipment.port,
                vessel = if (field == "vessel") value else shipment.vessel,
                customer = if (field == "customer") value else shipment.customer
            )
        }
    }

    fun saveShipment() {
        viewModelScope.launch {
            // Пересчитываем итоги перед сохранением
            val products = _currentProducts.value
            val totalProductTypes = products.size
            var totalPallets = 0
            var totalPlaces = 0
            var totalWeight = 0.0

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
                createdAt = Date()
            )

            val shipmentId = dao.insertShipment(shipmentToSave)

            // Сохраняем товары
            products.forEach { product ->
                val productWithShipmentId = product.copy(shipmentId = shipmentId)
                val productId = dao.insertProductItem(productWithShipmentId)

                // Сохраняем поддоны для этого товара
                val pallets = _currentPallets.value[product.id] ?: emptyList()
                pallets.forEach { pallet ->
                    val palletWithProductId = pallet.copy(productItemId = productId)
                    dao.insertPallet(palletWithProductId)
                }
            }

            // Сбрасываем текущие данные
            resetCurrentData()
        }
    }

    fun deleteShipment(shipment: Shipment) {
        viewModelScope.launch {
            dao.deleteShipment(shipment)
        }
    }

    // Функции для работы с товарами
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

        _currentProducts.update { currentList ->
            currentList + newProduct
        }
    }

    fun updateProductItem(productId: Long, field: String, value: Any) {
        _currentProducts.update { products ->
            products.map { product ->
                if (product.id == productId) {
                    when (field) {
                        "name" -> product.copy(name = value as String)
                        "manufacturer" -> product.copy(manufacturer = value as String)
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
                            val updatedProduct = product.copy(
                                quantity = quantity,
                                totalWeight = totalWeight
                            )
                            if (product.quantity != quantity) {
                                updatePalletPlacesForProduct(productId, quantity)
                            }
                            updatedProduct
                        }
                        else -> product
                    }
                } else {
                    product
                }
            }
        }

        updateProductPalletInfo(productId)
    }

    fun deleteProductItem(productId: Long) {
        _currentProducts.update { products ->
            products.filter { it.id != productId }
        }
        _currentPallets.update { pallets ->
            pallets.filterKeys { it != productId }
        }
    }

    // Функции для работы с поддонами
    fun addPallet(productItemId: Long) {
        val currentPalletsForProduct = _currentPallets.value[productItemId] ?: emptyList()
        val palletNumber = currentPalletsForProduct.size + 1

        val newPallet = Pallet(
            id = System.currentTimeMillis(),
            productItemId = productItemId,
            palletNumber = palletNumber,
            places = 0
        )

        _currentPallets.update { currentMap ->
            val updatedList = currentPalletsForProduct + newPallet
            currentMap + (productItemId to updatedList)
        }

        updateProductPalletInfo(productItemId)
    }

    fun updatePalletPlaces(productItemId: Long, palletId: Long, places: Int) {
        _currentPallets.update { palletsMap ->
            val productPallets = palletsMap[productItemId] ?: emptyList()
            val updatedPallets = productPallets.map { pallet ->
                if (pallet.id == palletId) {
                    pallet.copy(places = places)
                } else {
                    pallet
                }
            }
            palletsMap + (productItemId to updatedPallets)
        }

        updateProductPalletInfo(productItemId)
    }

    fun deletePallet(productItemId: Long, palletId: Long) {
        _currentPallets.update { palletsMap ->
            val productPallets = palletsMap[productItemId] ?: emptyList()
            val updatedPallets = productPallets.filter { it.id != palletId }
                .mapIndexed { index, pallet -> pallet.copy(palletNumber = index + 1) }

            palletsMap + (productItemId to updatedPallets)
        }

        updateProductPalletInfo(productItemId)
    }

    // Вспомогательные функции
    private fun loadProductsForShipment(shipmentId: Long) {
        viewModelScope.launch {
            dao.getProductItemsForShipment(shipmentId).collect { products ->
                _currentProducts.value = products

                products.forEach { product ->
                    dao.getPalletsForProductItem(product.id).collect { pallets ->
                        _currentPallets.update { currentMap ->
                            currentMap + (product.id to pallets)
                        }
                    }
                }
            }
        }
    }

    private fun updateProductPalletInfo(productItemId: Long) {
        val pallets = _currentPallets.value[productItemId] ?: emptyList()
        val palletCount = pallets.size
        val placesCount = pallets.sumOf { it.places }

        _currentProducts.update { products ->
            products.map { product ->
                if (product.id == productItemId) {
                    product.copy(
                        palletCount = palletCount,
                        placesCount = placesCount
                    )
                } else {
                    product
                }
            }
        }
    }

    private fun updatePalletPlacesForProduct(productItemId: Long, targetQuantity: Int) {
        val pallets = _currentPallets.value[productItemId] ?: emptyList()

        if (pallets.isEmpty()) {
            updateProductPalletInfo(productItemId)
            return
        }

        if (targetQuantity <= 0) {
            _currentPallets.update { palletsMap ->
                val updatedPallets = pallets.map { it.copy(places = 0) }
                palletsMap + (productItemId to updatedPallets)
            }
            updateProductPalletInfo(productItemId)
            return
        }

        val basePlaces = targetQuantity / pallets.size
        val remainder = targetQuantity % pallets.size

        _currentPallets.update { palletsMap ->
            val updatedPallets = pallets.mapIndexed { index, pallet ->
                var places = basePlaces
                if (index < remainder) {
                    places += 1
                }
                pallet.copy(places = places)
            }
            palletsMap + (productItemId to updatedPallets)
        }

        updateProductPalletInfo(productItemId)
    }

    private fun resetCurrentData() {
        _currentShipment.value = Shipment()
        _currentProducts.value = emptyList()
        _currentPallets.value = emptyMap()
    }

    fun setSelectedShipmentId(id: Long?) {
        _selectedShipmentId.value = id
    }
}