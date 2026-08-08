package com.example.fishy.feature.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.fishy.FishyApp
import com.example.fishy.R
import com.example.fishy.data.local.entity.ShipmentEntity
import com.example.fishy.data.serialization.FishyJson
import com.example.fishy.domain.calc.ShipmentCalculator
import com.example.fishy.domain.format.QuantityFormatters
import com.example.fishy.domain.model.DictionaryType
import com.example.fishy.domain.model.ShipmentFilters
import com.example.fishy.domain.stats.StackedChartResult
import com.example.fishy.domain.stats.StatDimension
import com.example.fishy.domain.stats.StatSplit
import com.example.fishy.domain.stats.StatisticsAggregator
import com.example.fishy.domain.stats.StatisticsBreakdown
import com.example.fishy.ui.components.AccordionCard
import com.example.fishy.ui.components.CenteredEmptyBody
import com.example.fishy.ui.components.ChartLegend
import com.example.fishy.ui.components.ColumnScrollIndicator
import com.example.fishy.ui.components.EmptyListPlaceholder
import com.example.fishy.ui.components.FilterDropdown
import com.example.fishy.ui.components.HintedScrollableTabs
import com.example.fishy.ui.components.StackedVerticalBarChart
import kotlinx.coroutines.launch

private enum class StatsChartTab {
    MONTHS, CUSTOMERS, PORTS, PRODUCTS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onBack: () -> Unit,
    onOpenShipment: (Long) -> Unit
) {
    val repo = FishyApp.instance.repository
    val scope = rememberCoroutineScope()
    val settings by FishyApp.instance.settingsRepository.settings.collectAsState(
        initial = com.example.fishy.data.settings.FishySettings()
    )
    val customers by repo.observeDictionary(DictionaryType.CUSTOMER).collectAsState(initial = emptyList())
    val ports by repo.observeDictionary(DictionaryType.PORT).collectAsState(initial = emptyList())
    val products by repo.observeDictionary(DictionaryType.PRODUCT).collectAsState(initial = emptyList())
    val archive by repo.observeArchive().collectAsState(initial = emptyList())

    val monthChoices = remember { StatisticsAggregator.monthChoices(count = 36) }
    val monthLabels = remember(monthChoices) { monthChoices.map { it.label } }
    val labelToStart = remember(monthChoices) {
        monthChoices.associate { it.label to it.startMillis }
    }
    val defaultBounds = remember { StatisticsAggregator.lastMonthsBounds() }

    var customer by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var productFilter by remember { mutableStateOf("") }
    var fromMonthStart by remember { mutableLongStateOf(defaultBounds.first) }
    var toMonthStart by remember { mutableLongStateOf(defaultBounds.second) }
    var chartTab by remember { mutableStateOf(StatsChartTab.MONTHS) }
    var chartResult by remember { mutableStateOf(StackedChartResult(emptyList(), emptyList())) }
    var filteredEntities by remember { mutableStateOf<List<ShipmentEntity>>(emptyList()) }
    var selectedBarIndex by remember { mutableIntStateOf(-1) }
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }

    val otherLabel = stringResource(R.string.stats_series_other)
    val tabTitles = listOf(
        stringResource(R.string.stats_tab_months),
        stringResource(R.string.stats_tab_customers),
        stringResource(R.string.stats_tab_ports),
        stringResource(R.string.stats_tab_products)
    )

    val fromLabel = monthChoices.firstOrNull { it.startMillis == fromMonthStart }?.label
        ?: monthChoices.lastOrNull()?.label.orEmpty()
    val toLabel = monthChoices.firstOrNull { it.startMillis == toMonthStart }?.label
        ?: monthChoices.firstOrNull()?.label.orEmpty()

    fun applyClientFilters(entities: List<ShipmentEntity>): List<ShipmentEntity> =
        entities.filter { entity ->
            val payload = FishyJson.decodePayloadOrNull(entity.payloadJson) ?: return@filter false
            val customerOk = customer.isBlank() || entity.customer == customer
            val portOk = ShipmentFilters.matchesPortFilter(entity, payload, port)
            val productOk = productFilter.isBlank() ||
                ShipmentCalculator.allProducts(payload).any { it.name.equals(productFilter, true) }
            customerOk && portOk && productOk
        }

    fun resolveAxes(): Pair<StatDimension, StatSplit> = when (chartTab) {
        StatsChartTab.MONTHS -> StatDimension.MONTH to StatSplit.PRODUCT
        StatsChartTab.CUSTOMERS -> StatDimension.CUSTOMER to StatSplit.PRODUCT
        StatsChartTab.PORTS -> StatDimension.PORT to StatSplit.PRODUCT
        StatsChartTab.PRODUCTS -> StatDimension.PRODUCT to StatSplit.MONTH
    }

    val (activeGroupBy, _) = resolveAxes()
    val chartTitleText = statsChartTitle(activeGroupBy)

    LaunchedEffect(
        customer, port, productFilter, fromMonthStart, toMonthStart, chartTab
    ) {
        scope.launch {
            val (fromMillis, toMillis) = StatisticsAggregator.monthRangeMillis(fromMonthStart, toMonthStart)
            val current = applyClientFilters(repo.filterStats(fromMillis, toMillis, customer, ""))
            filteredEntities = current
            val (groupBy, splitBy) = resolveAxes()
            chartResult = StatisticsAggregator.stackedChart(
                entities = current,
                groupBy = groupBy,
                splitBy = splitBy,
                fromMonthStart = fromMonthStart,
                toMonthStart = toMonthStart,
                otherSeriesLabel = otherLabel,
                otherGroupLabel = otherLabel,
                portFilter = port,
                productFilter = productFilter
            )
            selectedBarIndex = -1
        }
    }

    val selectedEntry = chartResult.bars.getOrNull(selectedBarIndex)
    val totalTonnageKg = remember(filteredEntities, port, productFilter) {
        StatisticsBreakdown.totalWeightKg(
            filteredEntities,
            portFilter = port,
            productFilter = productFilter
        )
    }

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
        val showEmpty = !filtersExpanded && filteredEntities.isEmpty()
        val statsScroll = rememberScrollState()
        CenteredEmptyBody(
            isEmpty = showEmpty,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            topChrome = {
                HintedScrollableTabs(
                    selectedIndex = chartTab.ordinal,
                    titles = tabTitles,
                    onSelect = { index ->
                        chartTab = StatsChartTab.entries[index]
                    }
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 16.dp)
                ) {
                    AccordionCard(
                        title = stringResource(R.string.archive_filters),
                        initiallyExpanded = false,
                        onExpandedChange = { filtersExpanded = it }
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterDropdown(
                                label = stringResource(R.string.stats_period_from),
                                value = fromLabel,
                                options = monthLabels,
                                modifier = Modifier.weight(1f),
                                onSelect = { label ->
                                    val start = labelToStart[label] ?: return@FilterDropdown
                                    fromMonthStart = start
                                    if (start > toMonthStart) toMonthStart = start
                                }
                            )
                            FilterDropdown(
                                label = stringResource(R.string.stats_period_to),
                                value = toLabel,
                                options = monthLabels,
                                modifier = Modifier.weight(1f),
                                onSelect = { label ->
                                    val start = labelToStart[label] ?: return@FilterDropdown
                                    toMonthStart = start
                                    if (start < fromMonthStart) fromMonthStart = start
                                }
                            )
                        }
                    }
                }
            },
            empty = {
                if (archive.isEmpty()) {
                    EmptyListPlaceholder(
                        emoji = "📊",
                        title = stringResource(R.string.stats_empty_title),
                        hint = stringResource(R.string.stats_empty_hint)
                    )
                } else {
                    EmptyListPlaceholder(
                        emoji = "📊",
                        title = stringResource(R.string.archive_no_filters_match),
                        hint = stringResource(R.string.stats_filters_empty_hint)
                    )
                }
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
            ) {
                if (filtersExpanded) {
                    Spacer(modifier = Modifier.weight(1f))
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(statsScroll)
                                .padding(vertical = 12.dp)
                                .padding(end = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.stats_summary_tonnage,
                                    QuantityFormatters.formatWeight(
                                        totalTonnageKg,
                                        settings.effectiveThousandsSeparator
                                    )
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            selectedEntry?.let { entry ->
                                val kgFmt = QuantityFormatters.formatWeight(
                                    entry.totalKg,
                                    settings.effectiveThousandsSeparator
                                )
                                Text(
                                    text = stringResource(R.string.stats_month_kg, entry.label, kgFmt),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                entry.segments
                                    .sortedByDescending { it.valueKg }
                                    .forEach { seg ->
                                        val w = QuantityFormatters.formatWeight(
                                            seg.valueKg,
                                            settings.effectiveThousandsSeparator
                                        )
                                        Text(
                                            text = "${seg.label}: $w кг",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                            }
                            if (chartResult.legend.isNotEmpty()) {
                                ChartLegend(
                                    items = chartResult.legend,
                                    totalKg = chartResult.legend.sumOf { it.totalKg },
                                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                                )
                            }
                        }
                        ColumnScrollIndicator(
                            scrollState = statsScroll,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .padding(vertical = 4.dp)
                        )
                    }

                    val chartHeightDp =
                        (LocalConfiguration.current.screenHeightDp / 3).coerceAtLeast(160)
                    StackedVerticalBarChart(
                        title = chartTitleText,
                        entries = chartResult.bars,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(chartHeightDp.dp),
                        fillHeight = true,
                        selectedIndex = selectedBarIndex.takeIf { it >= 0 },
                        onBarClick = { _, index ->
                            selectedBarIndex = if (selectedBarIndex == index) -1 else index
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun statsChartTitle(groupBy: StatDimension): String = when (groupBy) {
    StatDimension.MONTH -> stringResource(R.string.stats_chart_monthly)
    StatDimension.CUSTOMER -> stringResource(R.string.stats_chart_by_customers)
    StatDimension.PORT -> stringResource(R.string.stats_chart_by_ports)
    StatDimension.PRODUCT -> stringResource(R.string.stats_chart_by_products)
    StatDimension.MANUFACTURER -> stringResource(R.string.stats_dim_manufacturer)
    StatDimension.VESSEL -> stringResource(R.string.stats_dim_vessel)
    StatDimension.MODE -> stringResource(R.string.stats_dim_mode)
}
