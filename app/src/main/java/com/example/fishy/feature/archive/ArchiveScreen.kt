package com.example.fishy.feature.archive

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fishy.FishyApp
import com.example.fishy.R
import com.example.fishy.data.local.entity.ShipmentEntity
import com.example.fishy.data.serialization.FishyJson
import com.example.fishy.domain.calc.ShipmentCalculator
import com.example.fishy.domain.format.QuantityFormatters
import com.example.fishy.domain.model.DictionaryType
import com.example.fishy.domain.model.ShipmentMode
import com.example.fishy.domain.model.ShipmentFilters
import com.example.fishy.domain.model.ShipmentPayload
import com.example.fishy.domain.model.ShipmentSummaries
import com.example.fishy.ui.ErrorFeedback
import com.example.fishy.ui.components.CenteredEmptyBody
import com.example.fishy.ui.components.ConfirmDeleteDialog
import com.example.fishy.ui.components.DatePickerField
import com.example.fishy.ui.components.EmptyListPlaceholder
import com.example.fishy.ui.components.FilterDropdown
import com.example.fishy.ui.components.FishyButton
import com.example.fishy.ui.components.LazyListScrollbar
import com.example.fishy.ui.components.ListCardActionRow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ArchiveScreen(
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onOpenReport: (Long) -> Unit = onOpen,
    onOpenDraft: (Long) -> Unit
) {
    val repo = FishyApp.instance.repository
    val context = LocalContext.current
    val items by repo.observeArchive().collectAsState(initial = emptyList())
    val settings by FishyApp.instance.settingsRepository.settings.collectAsState(
        initial = com.example.fishy.data.settings.FishySettings()
    )
    val archiveNumbers = remember(items) {
        val pairs = items.map { it.id to it.completedAtMillis }
        items.associate { it.id to ShipmentSummaries.archiveNumber(it.id, pairs) }
    }
    val customers by repo.observeDictionary(DictionaryType.CUSTOMER).collectAsState(initial = emptyList())
    val ports by repo.observeDictionary(DictionaryType.PORT).collectAsState(initial = emptyList())
    val vessels by repo.observeDictionary(DictionaryType.VESSEL).collectAsState(initial = emptyList())
    val products by repo.observeDictionary(DictionaryType.PRODUCT).collectAsState(initial = emptyList())
    val manufacturers by repo.observeDictionary(DictionaryType.MANUFACTURER).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val fmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    val dateFmt = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }

    var searchText by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<ShipmentEntity?>(null) }

    var fromMillis by remember { mutableLongStateOf(0L) }
    var toMillis by remember { mutableLongStateOf(0L) }
    var toInitialized by remember { mutableStateOf(false) }
    var fromLocked by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!toInitialized) {
            toMillis = endOfDay(Calendar.getInstance())
            toInitialized = true
        }
    }

    LaunchedEffect(items) {
        val now = Calendar.getInstance()
        if (!toInitialized) {
            toMillis = endOfDay(now)
            toInitialized = true
        }
        if (fromLocked) return@LaunchedEffect
        val earliest = items.minOfOrNull { it.completedAtMillis }
        if (earliest != null) {
            fromMillis = startOfDay(earliest)
            fromLocked = true
        } else {
            // Empty archive / still loading — temporary today, do not lock.
            fromMillis = startOfDay(now.timeInMillis)
        }
    }

    var customerFilter by remember { mutableStateOf("") }
    var portFilter by remember { mutableStateOf("") }
    var vesselFilter by remember { mutableStateOf("") }
    var productFilter by remember { mutableStateOf("") }
    var manufacturerFilter by remember { mutableStateOf("") }
    var filtersExpanded by remember { mutableStateOf(false) }

    val hasActiveFilters = customerFilter.isNotBlank() ||
        portFilter.isNotBlank() ||
        vesselFilter.isNotBlank() ||
        productFilter.isNotBlank() ||
        manufacturerFilter.isNotBlank()

    val filtered = remember(
        items,
        searchText,
        fromMillis,
        toMillis,
        toInitialized,
        customerFilter,
        portFilter,
        vesselFilter,
        productFilter,
        manufacturerFilter
    ) {
        if (!toInitialized) emptyList()
        else items.filter { s ->
            val payload = FishyJson.decodePayloadOrNull(s.payloadJson)
            val productsInShipment = payload?.let { ShipmentCalculator.allProducts(it) }.orEmpty()
            val dateOk = s.completedAtMillis in fromMillis..toMillis
            val customerOk = customerFilter.isBlank() || s.customer == customerFilter
            val portOk = payload?.let {
                ShipmentFilters.matchesPortFilter(s, it, portFilter)
            } ?: portFilter.isBlank()
            val vesselOk = payload?.let {
                vesselFilter.isBlank() || archiveVessels(it)
                    .any { vessel -> vessel.equals(vesselFilter, true) }
            } ?: vesselFilter.isBlank()
            val productOk = productFilter.isBlank() ||
                productsInShipment.any { it.name.equals(productFilter, true) }
            val manufacturerOk = manufacturerFilter.isBlank() ||
                productsInShipment.any { it.manufacturer.equals(manufacturerFilter, true) }
            val searchOk = if (searchText.isBlank()) {
                true
            } else if (payload == null) {
                false
            } else {
                val q = searchText.lowercase()
                ShipmentSummaries.searchHaystack(s, payload).contains(q) ||
                    fmt.format(Date(s.completedAtMillis)).lowercase().contains(q)
            }
            dateOk && customerOk && portOk && vesselOk && productOk && manufacturerOk && searchOk
        }
    }

    fun duplicateItem(item: ShipmentEntity) {
        scope.launch {
            runCatching {
                val name = if (item.customer.isBlank()) {
                    context.getString(R.string.copy_default)
                } else {
                    context.getString(R.string.copy_suffix, item.customer)
                }
                repo.duplicateShipmentAsDraft(item.id, name)
            }.onSuccess { newId ->
                Toast.makeText(context, context.getString(R.string.duplicate_created), Toast.LENGTH_SHORT).show()
                onOpenDraft(newId)
            }.onFailure {
                ErrorFeedback.vibrate(context)
                Toast.makeText(context, it.message ?: "Error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.nav_archive)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        CenteredEmptyBody(
            isEmpty = filtered.isEmpty(),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            topChrome = {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    label = { Text(stringResource(R.string.search)) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (searchText.isNotEmpty()) {
                                IconButton(onClick = { searchText = "" }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.search_clear)
                                    )
                                }
                            }
                            IconButton(onClick = { filtersExpanded = !filtersExpanded }) {
                                Icon(
                                    Icons.Default.FilterList,
                                    contentDescription = stringResource(R.string.archive_filters),
                                    tint = if (filtersExpanded || hasActiveFilters) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp)
                )

                if (hasActiveFilters) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            onClick = {
                                customerFilter = ""
                                portFilter = ""
                                vesselFilter = ""
                                productFilter = ""
                                manufacturerFilter = ""
                            },
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            Text(stringResource(R.string.archive_reset_filters))
                        }
                    }
                }

                if (filtersExpanded) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DatePickerField(
                            label = stringResource(R.string.stats_period_from),
                            value = dateFmt.format(Date(fromMillis)),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val c = Calendar.getInstance().apply { timeInMillis = fromMillis }
                                android.app.DatePickerDialog(
                                    context,
                                    { _, y, m, d ->
                                        fromMillis = Calendar.getInstance().apply {
                                            set(y, m, d, 0, 0, 0)
                                            set(Calendar.MILLISECOND, 0)
                                        }.timeInMillis
                                        fromLocked = true
                                        if (fromMillis > toMillis) {
                                            toMillis = endOfDay(
                                                Calendar.getInstance().apply { timeInMillis = fromMillis }
                                            )
                                        }
                                    },
                                    c.get(Calendar.YEAR),
                                    c.get(Calendar.MONTH),
                                    c.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }
                        )
                        DatePickerField(
                            label = stringResource(R.string.stats_period_to),
                            value = dateFmt.format(Date(toMillis)),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val c = Calendar.getInstance().apply { timeInMillis = toMillis }
                                android.app.DatePickerDialog(
                                    context,
                                    { _, y, m, d ->
                                        toMillis = Calendar.getInstance().apply {
                                            set(y, m, d, 23, 59, 59)
                                            set(Calendar.MILLISECOND, 999)
                                        }.timeInMillis
                                        if (toMillis < fromMillis) {
                                            fromMillis = startOfDay(toMillis)
                                        }
                                    },
                                    c.get(Calendar.YEAR),
                                    c.get(Calendar.MONTH),
                                    c.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }
                        )
                    }
                    FilterDropdown(
                        label = stringResource(R.string.customer),
                        value = customerFilter,
                        options = listOf("") + customers.map { it.value },
                        onSelect = { customerFilter = it }
                    )
                    FilterDropdown(
                        label = stringResource(R.string.port),
                        value = portFilter,
                        options = listOf("") + ports.map { it.value },
                        onSelect = { portFilter = it }
                    )
                    FilterDropdown(
                        label = stringResource(R.string.vessel),
                        value = vesselFilter,
                        options = listOf("") + vessels.map { it.value },
                        onSelect = { vesselFilter = it }
                    )
                    FilterDropdown(
                        label = stringResource(R.string.product),
                        value = productFilter,
                        options = listOf("") + products.map { it.value },
                        onSelect = { productFilter = it }
                    )
                    FilterDropdown(
                        label = stringResource(R.string.manufacturer),
                        value = manufacturerFilter,
                        options = listOf("") + manufacturers.map { it.value },
                        onSelect = { manufacturerFilter = it }
                    )
                }

                Text(
                    text = stringResource(R.string.found_count, filtered.size, items.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            },
            empty = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (items.isEmpty()) {
                        EmptyListPlaceholder(
                            emoji = "📦",
                            title = stringResource(R.string.archive_empty),
                            hint = stringResource(R.string.archive_empty_hint)
                        )
                    } else if (searchText.isEmpty()) {
                        EmptyListPlaceholder(
                            emoji = "🔍",
                            title = stringResource(R.string.archive_no_filters_match),
                            hint = ""
                        )
                    } else {
                        Icon(
                            Icons.Default.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.nothing_found))
                        Text(
                            stringResource(R.string.query_label, searchText),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.Top
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { item ->
                        ArchiveShipmentCard(
                            item = item,
                            displayNumber = archiveNumbers[item.id] ?: 0,
                            dateLabel = fmt.format(Date(item.completedAtMillis)),
                            thousandsSeparator = settings.effectiveThousandsSeparator,
                            onOpen = { onOpen(item.id) },
                            onOpenReport = { onOpenReport(item.id) },
                            onDuplicate = { duplicateItem(item) },
                            onDelete = { pendingDelete = item },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                LazyListScrollbar(
                    listState = listState,
                    width = 16.dp,
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(vertical = 4.dp)
                )
            }
        }
    }

    pendingDelete?.let { item ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_shipment_title),
            message = stringResource(
                R.string.delete_shipment_msg,
                item.customer.ifBlank { "#${archiveNumbers[item.id] ?: item.id}" }
            ),
            onConfirm = {
                scope.launch {
                    repo.deleteShipment(item.id)
                    pendingDelete = null
                }
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun ArchiveShipmentCard(
    item: ShipmentEntity,
    displayNumber: Int,
    dateLabel: String,
    thousandsSeparator: Boolean,
    onOpen: () -> Unit,
    onOpenReport: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val payload = remember(item.payloadJson) {
        FishyJson.decodePayloadOrNull(item.payloadJson)
    }
    val allProducts = remember(payload) {
        payload?.let { ShipmentCalculator.allProducts(it) } ?: emptyList()
    }
    val ports = remember(payload, item.port) {
        payload?.let { ShipmentSummaries.ports(it) }.orEmpty()
            .ifEmpty { listOfNotNull(item.port.takeIf { it.isNotBlank() }) }
    }
    val vessels = remember(payload) {
        val fromSummary = payload?.let { ShipmentSummaries.loadingVessels(it) }.orEmpty()
        if (fromSummary.isNotEmpty()) fromSummary
        else listOfNotNull(payload?.vessel?.trim()?.takeIf { it.isNotBlank() })
    }
    val transportCounts = remember(payload) {
        payload?.let { ShipmentSummaries.transportCountsRu(it) }.orEmpty()
    }
    val products = remember(payload) {
        payload?.let { ShipmentSummaries.productNames(it) }.orEmpty()
    }
    val manufacturers = remember(payload) {
        payload?.let { ShipmentSummaries.manufacturers(it) }.orEmpty()
    }
    val productTypesCount = remember(payload) {
        payload?.let { ShipmentSummaries.productTypesCount(it) } ?: 0
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    "#$displayNumber",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    dateLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (payload == null) {
                    Text(
                        stringResource(R.string.data_corrupted),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                if (item.customer.isNotBlank()) {
                    Text(
                        stringResource(R.string.customer_binding, item.customer),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (ports.isNotEmpty()) {
                    Text(
                        stringResource(R.string.port_prefix, ports.joinToString(", ")),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (vessels.isNotEmpty()) {
                    Text(
                        stringResource(R.string.vessel_prefix, vessels.joinToString(", ")),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (transportCounts.isNotBlank()) {
                    Text(transportCounts, style = MaterialTheme.typography.bodyMedium)
                } else if (item.transportSummary.isNotBlank()) {
                    Text(
                        stringResource(R.string.transport_prefix, item.transportSummary),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (productTypesCount > 1) {
                    Text(
                        stringResource(R.string.product_types_count, productTypesCount),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    val p = allProducts.firstOrNull()
                    if (p != null) {
                        val name = p.name.trim()
                        val batch = p.batch.trim()
                        val manufacturer = p.manufacturer.trim()

                        val productAndBatch = when {
                            name.isNotEmpty() && batch.isNotEmpty() -> "$name $batch"
                            name.isNotEmpty() -> name
                            batch.isNotEmpty() -> batch
                            else -> products.firstOrNull().orEmpty()
                        }
                        val fullLine = if (manufacturer.isNotEmpty()) {
                            "$productAndBatch — $manufacturer"
                        } else {
                            productAndBatch
                        }
                        if (fullLine.isNotBlank()) {
                            Text(
                                fullLine,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        if (products.isNotEmpty()) {
                            Text(
                                products.joinToString(", "),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (manufacturers.isNotEmpty()) {
                            Text(
                                manufacturers.joinToString(", "),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                if (item.totalWeight > 0.0) {
                    Text(
                        stringResource(
                            R.string.weight_label,
                            QuantityFormatters.formatWeight(item.totalWeight, thousandsSeparator)
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FishyButton(
                    onClick = onOpenReport,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(end = 4.dp),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                ) {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.report),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onDuplicate) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.duplicate),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                FishyButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(start = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onError
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.delete),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onError,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun archiveVessels(payload: ShipmentPayload): List<String> {
    val vessels = mutableListOf<String>()
    if (payload.vessel.isNotBlank()) vessels += payload.vessel
    when (payload.mode) {
        ShipmentMode.MULTI_PORT ->
            payload.multiPorts.forEach { if (it.vessel.isNotBlank()) vessels += it.vessel }
        ShipmentMode.UNLOAD ->
            payload.unloadReceptions.forEach { reception ->
                reception.inbounds.forEach { if (it.vessel.isNotBlank()) vessels += it.vessel }
            }
        ShipmentMode.MONO, ShipmentMode.MULTI_VEHICLE -> Unit
    }
    return vessels
}

private fun startOfDay(millis: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun endOfDay(calendar: Calendar): Long =
    Calendar.getInstance().apply {
        timeInMillis = calendar.timeInMillis
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis
