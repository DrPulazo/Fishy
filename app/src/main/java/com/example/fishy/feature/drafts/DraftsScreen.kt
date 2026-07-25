package com.example.fishy.feature.drafts

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
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
import com.example.fishy.domain.model.ShipmentSummaries
import com.example.fishy.ui.ErrorFeedback
import com.example.fishy.ui.components.CenteredEmptyBody
import com.example.fishy.ui.components.ConfirmDeleteDialog
import com.example.fishy.ui.components.EmptyListPlaceholder
import com.example.fishy.ui.components.FishyButton
import com.example.fishy.ui.components.LazyListScrollIndicator
import com.example.fishy.ui.components.ListCardActionRow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DraftsScreen(
    onBack: () -> Unit,
    onOpen: (Long) -> Unit
) {
    val repo = FishyApp.instance.repository
    val context = LocalContext.current
    val items by repo.observeDrafts().collectAsState(initial = emptyList())
    val duplicatedKeys by repo.observeDuplicatedDraftKeys().collectAsState(initial = emptySet())
    val scope = rememberCoroutineScope()
    val fmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    val deleteFmt = remember { SimpleDateFormat("dd.MM.yyyy HH.mm", Locale.getDefault()) }
    var pendingDelete by remember { mutableStateOf<ShipmentEntity?>(null) }

    fun duplicateDraft(item: ShipmentEntity) {
        scope.launch {
            runCatching {
                val name = if (item.customer.isBlank()) {
                    context.getString(R.string.copy_default)
                } else {
                    context.getString(R.string.copy_suffix, item.customer)
                }
                repo.duplicateShipmentAsDraft(item.id, name)
            }.onSuccess {
                Toast.makeText(context, context.getString(R.string.duplicate_created), Toast.LENGTH_SHORT).show()
            }.onFailure {
                ErrorFeedback.vibrate(context)
                Toast.makeText(context, it.message ?: "Error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.nav_drafts)) },
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
            isEmpty = items.isEmpty(),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            empty = {
                EmptyListPlaceholder(
                    emoji = "📋",
                    title = stringResource(R.string.draft_empty),
                    hint = stringResource(R.string.draft_empty_hint)
                )
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        DraftCard(
                            item = item,
                            isDuplicated = "draft_${item.id}" in duplicatedKeys,
                            modifiedLabel = fmt.format(Date(item.completedAtMillis)),
                            onContinue = { onOpen(item.id) },
                            onDuplicate = { duplicateDraft(item) },
                            onDelete = { pendingDelete = item },
                            modifier = Modifier.animateItem()
                        )
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

    pendingDelete?.let { item ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_draft_title),
            message = stringResource(
                R.string.delete_draft_msg,
                deleteFmt.format(Date(item.completedAtMillis))
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
private fun DraftCard(
    item: ShipmentEntity,
    isDuplicated: Boolean,
    modifiedLabel: String,
    onContinue: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val payload = remember(item.payloadJson) {
        FishyJson.decodePayloadOrNull(item.payloadJson)
    }
    val title = item.customer.ifBlank { stringResource(R.string.no_customer) }
    val ports = payload?.let { ShipmentSummaries.ports(it) }.orEmpty()
        .ifEmpty { listOfNotNull(item.port.takeIf { it.isNotBlank() }) }
    val transports = payload?.let { ShipmentSummaries.transportLabels(it) }.orEmpty()
        .ifEmpty { listOfNotNull(item.transportSummary.takeIf { it.isNotBlank() }) }
    val receptions = payload?.let { ShipmentSummaries.receptionPoints(it) }.orEmpty()

    Card(modifier = modifier.fillMaxWidth()) {
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
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        modifiedLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    if (isDuplicated) {
                        Text(
                            stringResource(R.string.draft_duplicated_badge),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        )
                    }
                }
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
                if (ports.isNotEmpty()) {
                    Text(
                        stringResource(R.string.port_prefix, ports.joinToString(", ")),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (transports.isNotEmpty()) {
                    Text(
                        stringResource(R.string.transport_prefix, transports.joinToString(", ")),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (receptions.isNotEmpty()) {
                    Text(
                        stringResource(R.string.reception_prefix, receptions.joinToString(", ")),
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
                    onClick = onContinue,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(end = 4.dp),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.continue_action),
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
