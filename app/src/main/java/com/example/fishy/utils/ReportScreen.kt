package com.example.fishy.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.fishy.database.AppDatabase
import com.example.fishy.database.entities.*
import com.example.fishy.viewmodels.ShipmentViewModel
import com.example.fishy.viewmodels.ShipmentViewModelFactory
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    navController: NavController,
    shipmentId: Long?
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val viewModel: ShipmentViewModel = viewModel(
        factory = ShipmentViewModelFactory(
            context = context,
            database = AppDatabase.getDatabase(context)
        )
    )

    val shipment by viewModel.currentShipment.collectAsState()
    val products by viewModel.selectedShipmentProducts.collectAsState(initial = emptyList())

    // Генерируем текст отчета
    val reportText = remember(shipment, products) {
        generateReportText(shipment, products)
    }

    LaunchedEffect(shipmentId) {
        if (shipmentId != null) {
            viewModel.loadShipment(shipmentId)
            viewModel.setSelectedShipmentId(shipmentId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Отчёт по отгрузке") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(reportText))
                            scope.launch {
                                // Показываем Snackbar о копировании
                                // (можно заменить на Toast если хотите)
                                // Пока просто копируем
                            }
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Копировать")
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
            SelectionContainer(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = reportText,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private fun generateReportText(shipment: Shipment, products: List<ProductItem>): String {
    if (shipment.id == 0L) return "Загрузка отчета..."

    val report = StringBuilder()
    val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    // 1. Дата отгрузки
    report.append(dateFormat.format(shipment.createdAt))
    report.append("\n\n")

    when (shipment.shipmentType) {
        "mono" -> {
            // Транспортный номер
            val transportNumber = when {
                shipment.containerNumber.isNotEmpty() -> shipment.containerNumber
                shipment.wagonNumber.isNotEmpty() -> shipment.wagonNumber
                shipment.truckNumber.isNotEmpty() -> shipment.truckNumber
                else -> ""
            }

            if (transportNumber.isNotEmpty()) {
                report.append(transportNumber)
                report.append("\n")
            }

            // Товары
            products.forEach { product ->
                report.append(formatProduct(product))
                report.append("\n")
            }

            report.append("\n")
            report.append("Общий тоннаж: ${String.format("%.0f", shipment.totalWeight)} кг")
        }

        "multi_vehicle" -> {
            val vehicles = shipment.multiVehicleData.toMultiVehicleList()
            var totalWeight = 0.0

            vehicles.forEach { vehicle ->
                // Транспортный номер для каждого транспорта
                val transportNumber = when {
                    vehicle.containerNumber.isNotEmpty() -> vehicle.containerNumber
                    vehicle.wagonNumber.isNotEmpty() -> vehicle.wagonNumber
                    vehicle.truckNumber.isNotEmpty() -> vehicle.truckNumber
                    else -> ""
                }

                if (transportNumber.isNotEmpty()) {
                    report.append(transportNumber)
                    report.append("\n")
                }

                // Товары в транспорте
                vehicle.products.forEach { product ->
                    report.append(formatMultiVehicleProduct(product))
                    report.append("\n")
                    totalWeight += product.totalWeight
                }

                report.append("\n")
            }

            report.append("Общий тоннаж: ${String.format("%.0f", totalWeight)} кг")
        }

        "multi_port" -> {
            // Для мультипорта - транспорт из основного объекта Shipment
            val transportNumber = when {
                shipment.containerNumber.isNotEmpty() -> shipment.containerNumber
                shipment.wagonNumber.isNotEmpty() -> shipment.wagonNumber
                shipment.truckNumber.isNotEmpty() -> shipment.truckNumber
                else -> ""
            }

            // Выводим транспорт один раз
            if (transportNumber.isNotEmpty()) {
                report.append(transportNumber)
                report.append("\n")
            }

            val ports = shipment.multiPortData.toMultiPortList()
            var totalWeight = 0.0

            // Собираем ВСЕ товары из ВСЕХ портов
            ports.forEach { port ->
                port.products.forEach { product ->
                    report.append(formatMultiPortProduct(product))
                    report.append("\n")
                    totalWeight += product.totalWeight
                }
            }

            report.append("\n")
            report.append("Общий тоннаж: ${String.format("%.0f", totalWeight)} кг")
        }
    }
    report.append("\n")
    report.append("\n")
    report.append("Сгенерировано приложением «Fishy».")
    report.append("\n")
    report.append("${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())}")

    return report.toString()
}

private fun formatProduct(product: ProductItem): String {
    val batchInfo = if (product.batch.isNotEmpty()) " ${product.batch}" else ""
    val packageWeightStr = if (product.packageWeight > 0) "/${product.packageWeight.toInt()}" else ""

    return "${product.name}${batchInfo} (1$packageWeightStr) - ${product.manufacturer} - ${product.quantity} мест - ${String.format("%.0f", product.totalWeight)} кг"
}

private fun formatMultiVehicleProduct(product: MultiVehicleProduct): String {
    val batchInfo = if (product.batch.isNotEmpty()) " ${product.batch}" else ""
    val packageWeightStr = if (product.packageWeight > 0) "/${product.packageWeight.toInt()}" else ""

    return "${product.name}${batchInfo} (1$packageWeightStr) - ${product.manufacturer} - ${product.quantity} мест - ${String.format("%.0f", product.totalWeight)} кг"
}

private fun formatMultiPortProduct(product: MultiPortProduct): String {
    val batchInfo = if (product.batch.isNotEmpty()) " ${product.batch}" else ""
    val packageWeightStr = if (product.packageWeight > 0) "/${product.packageWeight.toInt()}" else ""

    return "${product.name}${batchInfo} (1$packageWeightStr) - ${product.manufacturer} - ${product.quantity} мест - ${String.format("%.0f", product.totalWeight)} кг"
}