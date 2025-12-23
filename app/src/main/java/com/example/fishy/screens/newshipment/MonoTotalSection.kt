package com.example.fishy.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.fishy.database.entities.ProductItem
import com.example.fishy.viewmodels.ShipmentViewModel
import kotlinx.coroutines.launch

@Composable
fun MonoTotalSection(
    products: List<ProductItem>,
    remainders: Map<Long, Int>,
    viewModel: ShipmentViewModel,
    navController: NavController
) {
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("ИТОГИ", style = MaterialTheme.typography.bodyLarge)

            val totalPlaces = products.sumOf { it.placesCount }
            val totalQuantity = products.sumOf { it.quantity }
            val totalWeight = products.sumOf { it.totalWeight }

            Text("Всего мест: $totalPlaces")
            Text("Всего должно быть: $totalQuantity")
            Text("Общая масса: ${String.format("%.2f", totalWeight)} кг")

            val match = totalPlaces == totalQuantity
            Text(
                text = if (match) "✓ Места совпадают" else "⚠ Места не совпадают",
                color = if (match) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )

            Button(
                onClick = {
                    viewModel.saveShipment()
                    scope.launch {
                        // Можно показать snackbar
                    }
                    navController.popBackStack()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = products.isNotEmpty() && match
            ) {
                Text("СОХРАНИТЬ ОТГРУЗКУ")
            }
        }
    }
}