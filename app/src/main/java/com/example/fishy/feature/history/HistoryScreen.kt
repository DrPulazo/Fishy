package com.example.fishy.feature.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.fishy.FishyApp
import com.example.fishy.R
import com.example.fishy.data.local.entity.ShipmentEventEntity
import com.example.fishy.domain.model.ShipmentEventType
import com.example.fishy.ui.components.CenteredEmptyBody
import com.example.fishy.ui.components.EmptyListPlaceholder
import com.example.fishy.ui.components.LazyListScrollIndicator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    shipmentKey: String,
    onBack: () -> Unit
) {
    val events by FishyApp.instance.repository.observeEvents(shipmentKey)
        .collectAsState(initial = emptyList())
    val fmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()) }

    val milestones = remember(events) {
        events.filter { parseType(it.type)?.isMilestone == true }
    }
    val palletEvents = remember(events) {
        events.filter { parseType(it.type)?.isPallet == true }
    }
    val otherEvents = remember(events) {
        events.filter { event ->
            val t = parseType(event.type)
            t == null || (!t.isMilestone && !t.isPallet)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.history)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        val listState = rememberLazyListState()
        CenteredEmptyBody(
            isEmpty = events.isEmpty(),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            empty = {
                EmptyListPlaceholder(
                    emoji = "📋",
                    title = stringResource(R.string.history_empty),
                    hint = stringResource(R.string.history_empty_hint)
                )
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (milestones.isNotEmpty()) {
                        item {
                            SectionHeader(stringResource(R.string.history_section_milestones))
                        }
                        items(milestones, key = { "m-${it.id}" }) { event ->
                            HistoryEventCard(event, fmt, emphasizeTime = false, modifier = Modifier.animateItem())
                        }
                    }
                    if (palletEvents.isNotEmpty()) {
                        item {
                            SectionHeader(stringResource(R.string.history_section_pallets))
                        }
                        items(palletEvents, key = { "p-${it.id}" }) { event ->
                            HistoryEventCard(event, fmt, emphasizeTime = true, modifier = Modifier.animateItem())
                        }
                    }
                    if (otherEvents.isNotEmpty()) {
                        item {
                            SectionHeader(stringResource(R.string.history_section_other))
                        }
                        items(otherEvents, key = { "o-${it.id}" }) { event ->
                            HistoryEventCard(event, fmt, emphasizeTime = false, modifier = Modifier.animateItem())
                        }
                    }
                }
                LazyListScrollIndicator(
                    listState = listState,
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun HistoryEventCard(
    event: ShipmentEventEntity,
    fmt: SimpleDateFormat,
    emphasizeTime: Boolean,
    modifier: Modifier = Modifier
) {
    val type = parseType(event.type)
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (emphasizeTime) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = fmt.format(Date(event.timestampMillis)),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (emphasizeTime) FontFamily.Monospace else FontFamily.Default,
                fontWeight = if (emphasizeTime) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = eventTypeLabel(type),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            val message = formatEventMessage(event, type)
            if (message.isNotBlank()) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun parseType(raw: String): ShipmentEventType? =
    runCatching { ShipmentEventType.valueOf(raw) }.getOrNull()

@Composable
private fun eventTypeLabel(type: ShipmentEventType?): String = when (type) {
    ShipmentEventType.STARTED -> stringResource(R.string.history_type_started)
    ShipmentEventType.DRAFT_SAVED -> stringResource(R.string.history_type_draft_saved)
    ShipmentEventType.COMPLETED -> stringResource(R.string.history_type_completed)
    ShipmentEventType.DUPLICATED -> stringResource(R.string.history_type_duplicated)
    ShipmentEventType.REPORT_EDITED -> stringResource(R.string.history_type_report_edited)
    ShipmentEventType.PALLET_ADDED -> stringResource(R.string.history_type_pallet_added)
    ShipmentEventType.PALLET_PLACES -> stringResource(R.string.history_type_pallet_places)
    ShipmentEventType.PALLET_DELETED -> stringResource(R.string.history_type_pallet_deleted)
    ShipmentEventType.PALLET_IMPORTED -> stringResource(R.string.history_type_pallet_imported)
    ShipmentEventType.PRODUCT_ADDED -> stringResource(R.string.history_type_product_added)
    ShipmentEventType.PRODUCT_DELETED -> stringResource(R.string.history_type_product_deleted)
    ShipmentEventType.TRANSPORT_ADDED -> stringResource(R.string.history_type_transport_added)
    ShipmentEventType.TRANSPORT_DELETED -> stringResource(R.string.history_type_transport_deleted)
    ShipmentEventType.PORT_ADDED -> stringResource(R.string.history_type_port_added)
    ShipmentEventType.PORT_DELETED -> stringResource(R.string.history_type_port_deleted)
    ShipmentEventType.CHECKLIST_CHANGED -> stringResource(R.string.history_type_checklist)
    ShipmentEventType.INPUT_GUARD_CONFIRMED -> stringResource(R.string.history_type_guard)
    ShipmentEventType.BATCH_LIMIT_HIT -> stringResource(R.string.history_type_batch_limit)
    null -> stringResource(R.string.history_type_unknown)
}

@Composable
private fun formatEventMessage(event: ShipmentEventEntity, type: ShipmentEventType?): String {
    if (type == ShipmentEventType.DUPLICATED) {
        val id = event.message
            .removePrefix("archive#")
            .removePrefix("draft#")
            .ifBlank { event.message }
        return stringResource(R.string.history_msg_duplicated, id)
    }
    return event.message
}
