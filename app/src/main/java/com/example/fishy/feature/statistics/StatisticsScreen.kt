package com.example.fishy.feature.statistics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fishy.FishyApp
import com.example.fishy.R
import com.example.fishy.data.local.entity.ShipmentEntity
import com.example.fishy.data.serialization.FishyJson
import com.example.fishy.domain.calc.ShipmentCalculator
import com.example.fishy.domain.model.DictionaryType
import com.example.fishy.domain.stats.StatBarEntry
import com.example.fishy.domain.stats.StatisticsAggregator
import com.example.fishy.ui.components.FilterDropdown
import com.example.fishy.ui.components.VerticalBarChart
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onBack: () -> Unit,
    onOpenShipment: (Long) -> Unit
) {
    val repo = FishyApp.instance.repository
    val scope = rememberCoroutineScope()
    val customers by repo.observeDictionary(DictionaryType.CUSTOMER).collectAsState(initial = emptyList())
    val ports by repo.observeDictionary(DictionaryType.PORT).collectAsState(initial = emptyList())
    val products by repo.observeDictionary(DictionaryType.PRODUCT).collectAsState(initial = emptyList())

    var customer by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var productFilter by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf<List<ShipmentEntity>>(emptyList()) }
    var monthlyChart by remember { mutableStateOf<List<StatBarEntry>>(emptyList()) }
    var selectedBarIndex by remember { mutableIntStateOf(-1) }

    fun applyClientFilters(entities: List<ShipmentEntity>): List<ShipmentEntity> =
        entities.filter { entity ->
            val payload = FishyJson.decodePayload(entity.payloadJson)
            productFilter.isBlank() ||
                ShipmentCalculator.allProducts(payload).any { it.name.equals(productFilter, true) }
        }

    fun reload() {
        scope.launch {
            val (fromMillis, toMillis) = StatisticsAggregator.last12MonthsRange()
            val current = applyClientFilters(repo.filterStats(fromMillis, toMillis, customer, port))
            rows = current
            monthlyChart = StatisticsAggregator.tonnageLast12Months(current)
            selectedBarIndex = -1
        }
    }

    LaunchedEffect(customer, port, productFilter) { reload() }

    val selectedEntry = monthlyChart.getOrNull(selectedBarIndex)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.nav_statistics)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            FilterDropdown(
                label = stringResource(R.string.customer),
                value = customer,
                options = listOf("") + customers.map { it.value },
                onSelect = { customer = it }
            )
            FilterDropdown(
                label = stringResource(R.string.port),
                value = port,
                options = listOf("") + ports.map { it.value },
                onSelect = { port = it }
            )
            FilterDropdown(
                label = stringResource(R.string.product),
                value = productFilter,
                options = listOf("") + products.map { it.value },
                onSelect = { productFilter = it }
            )

            VerticalBarChart(
                title = stringResource(R.string.stats_chart_monthly),
                entries = monthlyChart,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                selectedIndex = selectedBarIndex.takeIf { it >= 0 },
                onBarClick = { _, index ->
                    selectedBarIndex = if (selectedBarIndex == index) -1 else index
                }
            )

            selectedEntry?.let { entry ->
                Text(
                    text = stringResource(
                        R.string.stats_month_kg,
                        entry.label,
                        String.format(Locale.getDefault(), "%.0f", entry.valueKg)
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
