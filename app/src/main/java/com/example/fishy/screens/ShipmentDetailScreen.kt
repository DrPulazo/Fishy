package com.example.fishy.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.fishy.theme.CardBackground
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fishy.viewmodels.ShipmentViewModel
import com.example.fishy.viewmodels.ShipmentViewModelFactory

@Composable
fun ShipmentDetailScreen(
    navController: NavController,
    shipmentId: Long?
) {
    val context = LocalContext.current
    val viewModel: ShipmentViewModel = viewModel(
        factory = ShipmentViewModelFactory(context.applicationContext as Application)
    )

    val shipment by viewModel.currentShipment.collectAsState()
    val products by viewModel.selectedShipmentProducts.collectAsState(initial = emptyList())

    LaunchedEffect(shipmentId) {
        shipmentId?.let { viewModel.loadShipment(it) }
        viewModel.setSelectedShipmentId(shipmentId)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Кнопка возврата
            Button(
                onClick = { navController.popBackStack() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text("ВЕРНУТЬСЯ В АРХИВ")
            }

            if (shipment.id == 0L) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Информация о погрузке
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = CardBackground
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "ИНФОРМАЦИЯ О ПОГРУЗКЕ",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                if (shipment.containerNumber.isNotEmpty()) {
                                    DetailRow("Номер контейнера:", shipment.containerNumber)
                                }
                                if (shipment.truckNumber.isNotEmpty()) {
                                    DetailRow("Номер авто:", shipment.truckNumber)
                                }
                                if (shipment.trailerNumber.isNotEmpty()) {
                                    DetailRow("Номер прицепа:", shipment.trailerNumber)
                                }
                                if (shipment.wagonNumber.isNotEmpty()) {
                                    DetailRow("Номер вагона:", shipment.wagonNumber)
                                }
                                if (shipment.sealNumber.isNotEmpty()) {
                                    DetailRow("Номер пломбы:", shipment.sealNumber)
                                }
                                if (shipment.port.isNotEmpty()) {
                                    DetailRow("Порт:", shipment.port)
                                }
                                if (shipment.vessel.isNotEmpty()) {
                                    DetailRow("Судно:", shipment.vessel)
                                }
                                if (shipment.customer.isNotEmpty()) {
                                    DetailRow("Заказчик:", shipment.customer)
                                }

                                DetailRow(
                                    "Дата создания:",
                                    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                                        .format(shipment.createdAt)
                                )
                            }
                        }
                    }

                    // Продукция
                    if (products.isNotEmpty()) {
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
                                        text = "ПРОДУКЦИЯ",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    products.forEachIndexed { index, product ->
                                        ProductDetailCard(
                                            product = product,
                                            index = index + 1
                                        )
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

                                DetailRow("Видов продукции:", shipment.totalProductTypes.toString())
                                DetailRow("Всего поддонов:", shipment.totalPallets.toString())
                                DetailRow("Всего мест:", shipment.totalPlaces.toString())
                                DetailRow("Общая масса:", "${String.format("%.2f", shipment.totalWeight)} кг")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ProductDetailCard(
    product: com.example.fishy.database.entities.ProductItem,
    index: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(8.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Вид продукции #$index: ${product.name}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )

            DetailRow("Изготовитель:", product.manufacturer)
            DetailRow("Тара:", "${String.format("%.2f", product.packageWeight)} кг")
            DetailRow("Количество:", product.quantity.toString())
            DetailRow("Масса:", "${String.format("%.2f", product.totalWeight)} кг")
            DetailRow("Поддоны:", product.palletCount.toString())
            DetailRow("Места:", product.placesCount.toString())
        }
    }
}