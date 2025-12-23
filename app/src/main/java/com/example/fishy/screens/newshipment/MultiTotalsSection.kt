package com.example.fishy.screens.newshipment

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.example.fishy.theme.Success

@Composable
fun MultiTotalsSection(
    totals: MultiTotals,
    modifier: Modifier = Modifier,
    showPallets: Boolean = true,
    doubleControlEnabled: Boolean = false,
    doubleControlStats: DoubleControlStats = DoubleControlStats()
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Видов продукции:")
                Text("${totals.totalProductTypes}")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Поддонов:")
                Text("${totals.totalPallets}")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Мест:")
                Text("${totals.totalPlaces}")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Общая масса:")
                Text("${String.format("%.2f", totals.totalWeight)} кг")
            }

            // Учитываем двойной контроль в расчетах
            val actualPlaces = if (doubleControlEnabled) {
                doubleControlStats.importedPlaces
            } else {
                totals.totalPlaces
            }

            val remainder = totals.totalQuantity - actualPlaces

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
            if (doubleControlEnabled) {
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
                                "${doubleControlStats.importedPallets}/${doubleControlStats.totalPallets}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (doubleControlStats.importedPallets == doubleControlStats.totalPallets)
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
                                "${doubleControlStats.importedPlaces}/${doubleControlStats.totalPlaces}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (doubleControlStats.importedPlaces == doubleControlStats.totalPlaces)
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

data class MultiTotals(
    val totalProductTypes: Int = 0,
    val totalPallets: Int = 0,
    val totalPlaces: Int = 0,
    val totalWeight: Double = 0.0,
    val totalRemainder: Int = 0,
    val totalQuantity: Int = 0 // Добавляем для совместимости с логикой двойного контроля
)

data class DoubleControlStats(
    val importedPallets: Int = 0,
    val importedPlaces: Int = 0,
    val totalPallets: Int = 0,
    val totalPlaces: Int = 0
)