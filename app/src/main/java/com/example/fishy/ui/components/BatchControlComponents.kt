package com.example.fishy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.fishy.R
import com.example.fishy.data.local.entity.DictionaryEntity
import com.example.fishy.domain.calc.BatchStatus
import com.example.fishy.domain.calc.ShipmentCalculator
import com.example.fishy.domain.format.QuantityFormatters
import com.example.fishy.domain.model.BatchLimit
import com.example.fishy.domain.model.DictionaryType
import com.example.fishy.domain.model.ShipmentPayload
import com.example.fishy.ui.theme.ProgressGreen
import com.example.fishy.ui.theme.isLightTheme

/**
 * Compact expandable batch-limits panel (shipment sticky bar).
 * Pass [batchStatuses] empty in the planner (no live usage yet).
 */
@Composable
fun BatchControlPanel(
    payload: ShipmentPayload,
    batchStatuses: List<BatchStatus>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onEnterBatches: () -> Unit,
    onEditBatch: (BatchLimit) -> Unit,
    onDeleteBatch: (BatchLimit) -> Unit = {},
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val titleStyle = if (compact) {
        MaterialTheme.typography.titleSmall
    } else {
        MaterialTheme.typography.titleMedium
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = if (compact) 6.dp else 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpandedChange(!expanded) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.batch_control),
                style = titleStyle,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null
            )
        }
        if (expanded) {
            Spacer(modifier = Modifier.height(8.dp))
            BatchLimitsList(
                payload = payload,
                batchStatuses = batchStatuses,
                onEnterBatches = onEnterBatches,
                onEditBatch = onEditBatch,
                onDeleteBatch = onDeleteBatch
            )
        }
    }
}

/** List body for sticky panel or scheduler accordion under «Информация о погрузке». */
@Composable
fun BatchLimitsList(
    payload: ShipmentPayload,
    batchStatuses: List<BatchStatus>,
    onEnterBatches: () -> Unit,
    onEditBatch: (BatchLimit) -> Unit,
    onDeleteBatch: (BatchLimit) -> Unit = {}
) {
    val statusByKey = remember(batchStatuses) { batchStatuses.associateBy { it.key } }
    var pendingDelete by remember { mutableStateOf<BatchLimit?>(null) }
    val editIconTint = if (isLightTheme()) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    if (payload.batchLimits.isNotEmpty()) {
        payload.batchLimits.forEach { limit ->
            val status = statusByKey[ShipmentCalculator.batchKey(limit)]
            val color = when {
                status == null -> MaterialTheme.colorScheme.onSurface
                status.exhausted -> ProgressGreen
                else -> MaterialTheme.colorScheme.onSurface
            }
            val tare = QuantityFormatters.formatWeight(limit.packageWeight, thousandsSeparator = false)
            val nameBatch = listOf(limit.productName.trim(), limit.batchName.trim())
                .filter { it.isNotEmpty() }
                .joinToString(" ")
                .ifBlank { "—" }
            val manufacturer = limit.manufacturer.trim().ifBlank { "—" }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onEditBatch(limit) }
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = stringResource(
                            R.string.batch_plan_line,
                            nameBatch,
                            tare,
                            manufacturer
                        ),
                        color = color,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (status != null) {
                        Text(
                            text = stringResource(
                                R.string.batch_remaining,
                                QuantityFormatters.formatCount(status.used),
                                QuantityFormatters.formatCount(status.planned),
                                QuantityFormatters.formatCount(status.remaining)
                            ),
                            color = color,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        val plannedTonnage = limit.packageWeight * limit.plannedPlaces
                        Text(
                            text = stringResource(
                                R.string.batch_plan_places_tonnage,
                                ShipmentCalculator.formatPlacesRu(limit.plannedPlaces),
                                QuantityFormatters.formatWeight(plannedTonnage)
                            ),
                            color = color,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                IconButton(onClick = { onEditBatch(limit) }) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.edit),
                        tint = editIconTint
                    )
                }
                IconButton(onClick = { pendingDelete = limit }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
    FishyButton(
        onClick = onEnterBatches,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = stringResource(R.string.add_batch)
        )
    }
    pendingDelete?.let { limit ->
        val tare = QuantityFormatters.formatWeight(limit.packageWeight, thousandsSeparator = false)
        val nameBatch = listOf(limit.productName.trim(), limit.batchName.trim())
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .ifBlank { "—" }
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete),
            message = stringResource(
                R.string.batch_plan_line,
                nameBatch,
                tare,
                limit.manufacturer.trim().ifBlank { "—" }
            ),
            onConfirm = {
                onDeleteBatch(limit)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
fun BatchEntryDialog(
    initial: BatchLimit,
    productsDict: List<DictionaryEntity>,
    manufacturers: List<DictionaryEntity>,
    onDismiss: () -> Unit,
    onSave: (BatchLimit) -> Unit,
    onAddToDictionary: (DictionaryType, String) -> Unit,
    isNew: Boolean = true
) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        title = {
            CenteredDialogTitle(
                stringResource(if (isNew) R.string.enter_batches else R.string.edit_batch)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DictionaryAutocomplete(
                    label = stringResource(R.string.product),
                    value = draft.productName,
                    suggestions = productsDict,
                    onValueChange = { v -> draft = draft.copy(productName = v) },
                    dictionaryType = DictionaryType.PRODUCT,
                    onAddToDictionary = onAddToDictionary
                )
                OutlinedTextField(
                    value = draft.batchName,
                    onValueChange = { draft = draft.copy(batchName = it) },
                    label = { Text(stringResource(R.string.batch)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = FishySentenceKeyboardOptions
                )
                DictionaryAutocomplete(
                    label = stringResource(R.string.manufacturer),
                    value = draft.manufacturer,
                    suggestions = manufacturers,
                    onValueChange = { v -> draft = draft.copy(manufacturer = v) },
                    dictionaryType = DictionaryType.MANUFACTURER,
                    onAddToDictionary = onAddToDictionary
                )
                DecimalNumberField(
                    value = draft.packageWeight,
                    onValueChange = { draft = draft.copy(packageWeight = it) },
                    label = { Text(stringResource(R.string.tare)) },
                    modifier = Modifier.fillMaxWidth()
                )
                DecimalNumberField(
                    value = draft.plannedPlaces,
                    onValueChange = { draft = draft.copy(plannedPlaces = it) },
                    label = { Text(stringResource(R.string.places_count)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            DialogCancelConfirmActions(
                onCancel = onDismiss,
                onConfirm = { onSave(draft) },
                confirmText = stringResource(
                    if (isNew) R.string.add else R.string.save
                )
            )
        }
    )
}
