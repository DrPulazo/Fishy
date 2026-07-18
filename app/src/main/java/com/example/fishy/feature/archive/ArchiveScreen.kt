package com.example.fishy.feature.archive

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import com.example.fishy.FishyApp
import com.example.fishy.R
import com.example.fishy.data.local.entity.ShipmentEntity
import com.example.fishy.data.serialization.FishyJson
import com.example.fishy.domain.calc.ShipmentCalculator
import com.example.fishy.domain.model.DictionaryType
import com.example.fishy.domain.model.ShipmentMode
import com.example.fishy.domain.model.ShipmentPayload
import com.example.fishy.ui.components.ConfirmDeleteDialog
import com.example.fishy.ui.components.EmptyListPlaceholder
import com.example.fishy.ui.components.FilterDropdown
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
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
    val customers by repo.observeDictionary(DictionaryType.CUSTOMER).collectAsState(initial = emptyList())
    val ports by repo.observeDictionary(DictionaryType.PORT).collectAsState(initial = emptyList())
    val vessels by repo.observeDictionary(DictionaryType.VESSEL).collectAsState(initial = emptyList())
    val products by repo.observeDictionary(DictionaryType.PRODUCT).collectAsState(initial = emptyList())
    val manufacturers by repo.observeDictionary(DictionaryType.MANUFACTURER).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val fmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    val dateFmt = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }

    var searchText by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<ShipmentEntity?>(null) }

    val cal = Calendar.getInstance()
    var toMillis by remember { mutableLongStateOf(cal.timeInMillis) }
    cal.set(Calendar.YEAR, 2000)
    cal.set(Calendar.MONTH, Calendar.JANUARY)
    cal.set(Calendar.DAY_OF_MONTH, 1)
    var fromMillis by remember { mutableLongStateOf(cal.timeInMillis) }
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
        customerFilter,
        portFilter,
        vesselFilter,
        productFilter,
        manufacturerFilter
    ) {
        items.filter { s ->
            val payload = FishyJson.decodePayload(s.payloadJson)
            val productsInShipment = ShipmentCalculator.allProducts(payload)
            val dateOk = s.completedAtMillis in fromMillis..toMillis
            val customerOk = customerFilter.isBlank() || s.customer == customerFilter
            val portOk = portFilter.isBlank() || s.port == portFilter ||
                payload.multiPorts.any { it.port.equals(portFilter, true) } ||
                payload.unloadReceptions.any { r ->
                    r.inbounds.any { it.port.equals(portFilter, true) }
                }
            val vesselOk = vesselFilter.isBlank() || archiveVessels(payload)
                .any { it.equals(vesselFilter, true) }
            val productOk = productFilter.isBlank() ||
                productsInShipment.any { it.name.equals(productFilter, true) }
            val manufacturerOk = manufacturerFilter.isBlank() ||
                productsInShipment.any { it.manufacturer.equals(manufacturerFilter, true) }
            val searchOk = if (searchText.isBlank()) {
                true
            } else {
                val q = searchText.lowercase()
                s.customer.lowercase().contains(q) ||
                    s.port.lowercase().contains(q) ||
                    s.transportSummary.lowercase().contains(q) ||
                    s.id.toString().contains(q) ||
                    fmt.format(Date(s.completedAtMillis)).lowercase().contains(q) ||
                    s.totalPlaces.toString().contains(q) ||
                    "%.3f".format(s.totalWeight).contains(q)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
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
                TextButton(
                    onClick = {
                        customerFilter = ""
                        portFilter = ""
                        vesselFilter = ""
                        productFilter = ""
                        manufacturerFilter = ""
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.archive_reset_filters))
                }
            }

            if (filtersExpanded) {
                ArchiveDatePickerRow(
                    label = stringResource(R.string.period_from, dateFmt.format(Date(fromMillis))),
                    onClick = {
                        val c = Calendar.getInstance().apply { timeInMillis = fromMillis }
                        android.app.DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                fromMillis = Calendar.getInstance().apply {
                                    set(y, m, d, 0, 0, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }.timeInMillis
                            },
                            c.get(Calendar.YEAR),
                            c.get(Calendar.MONTH),
                            c.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }
                )
                ArchiveDatePickerRow(
                    label = stringResource(R.string.period_to, dateFmt.format(Date(toMillis))),
                    onClick = {
                        val c = Calendar.getInstance().apply { timeInMillis = toMillis }
                        android.app.DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                toMillis = Calendar.getInstance().apply {
                                    set(y, m, d, 23, 59, 59)
                                    set(Calendar.MILLISECOND, 999)
                                }.timeInMillis
                            },
                            c.get(Calendar.YEAR),
                            c.get(Calendar.MONTH),
                            c.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }
                )
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

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
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
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered, key = { it.id }) { item ->
                        ArchiveShipmentCard(
                            item = item,
                            dateLabel = fmt.format(Date(item.completedAtMillis)),
                            onOpen = { onOpen(item.id) },
                            onOpenReport = { onOpenReport(item.id) },
                            onDuplicate = { duplicateItem(item) },
                            onDelete = { pendingDelete = item }
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { item ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_shipment_title),
            message = stringResource(R.string.delete_shipment_msg, item.customer.ifBlank { "#${item.id}" }),
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
private fun ArchiveDatePickerRow(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun ArchiveShipmentCard(
    item: ShipmentEntity,
    dateLabel: String,
    onOpen: () -> Unit,
    onOpenReport: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "#${item.id}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (item.customer.isNotBlank()) {
                    Text(stringResource(R.string.customer_binding, item.customer), style = MaterialTheme.typography.bodyMedium)
                }
                if (item.port.isNotBlank()) {
                    Text(stringResource(R.string.port_prefix, item.port), style = MaterialTheme.typography.bodyMedium)
                }
                if (item.transportSummary.isNotBlank()) {
                    Text(
                        stringResource(R.string.transport_prefix, item.transportSummary),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    stringResource(R.string.places_weight, item.totalPlaces, item.totalWeight),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row {
                    IconButton(onClick = onOpenReport) {
                        Icon(Icons.Default.Description, contentDescription = stringResource(R.string.report))
                    }
                    IconButton(onClick = onDuplicate) {
                        Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.duplicate))
                    }
                }
                Text(
                    dateLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.error
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
