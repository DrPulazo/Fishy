package com.example.fishy.feature.scheduler

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.fishy.FishyApp
import com.example.fishy.R
import com.example.fishy.data.local.entity.ChecklistItemEntity
import com.example.fishy.data.local.entity.ScheduledShipmentEntity
import com.example.fishy.data.serialization.FishyJson
import com.example.fishy.data.settings.FishySettings
import com.example.fishy.domain.calc.ShipmentCalculator
import com.example.fishy.domain.format.QuantityFormatters
import com.example.fishy.domain.model.BatchLimit
import com.example.fishy.domain.model.ChecklistTask
import com.example.fishy.domain.model.DictionaryType
import com.example.fishy.domain.model.ShipmentEventType
import com.example.fishy.domain.model.ShipmentMode
import com.example.fishy.notifications.NotificationScheduler
import com.example.fishy.ui.components.BatchEntryDialog
import com.example.fishy.ui.components.ConfirmDeleteDialog
import com.example.fishy.ui.components.CenteredDialogMessage
import com.example.fishy.ui.components.CenteredDialogTitle
import com.example.fishy.ui.components.ChecklistStatusBanner
import com.example.fishy.ui.components.DatePickerField
import com.example.fishy.ui.components.DialogCancelConfirmActions
import com.example.fishy.ui.components.DialogCenteredFishyButton
import com.example.fishy.ui.components.FishyButton
import com.example.fishy.ui.components.FishyFloatingActionButton
import com.example.fishy.ui.components.FishySentenceKeyboardOptions
import com.example.fishy.ui.components.LocalAccordionTitleStyle
import com.example.fishy.ui.components.LocalFormTextStyle
import com.example.fishy.ui.components.TimePickerField
import com.example.fishy.ui.components.fishyCheckboxColors
import com.example.fishy.ui.components.fishySwitchColors
import com.example.fishy.ui.components.formLabelStyleOrDefault
import com.example.fishy.ui.components.formTextStyleOrDefault
import com.example.fishy.ui.theme.Error
import com.example.fishy.ui.theme.PlaceholderGrey
import com.example.fishy.ui.theme.Success
import com.example.fishy.ui.theme.Warning
import com.example.fishy.ui.theme.isLightTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class ChecklistStatus {
    COMPLETED, PARTIAL, NONE, EMPTY
}

private fun checklistStatusOf(items: List<ChecklistItemEntity>): ChecklistStatus {
    if (items.isEmpty()) return ChecklistStatus.EMPTY
    val done = items.count { it.isCompleted }
    return when {
        done == items.size -> ChecklistStatus.COMPLETED
        done > 0 -> ChecklistStatus.PARTIAL
        else -> ChecklistStatus.NONE
    }
}

@Composable
private fun modeLabel(mode: String): String = when (mode) {
    ShipmentMode.MONO.name -> stringResource(R.string.mode_mono)
    ShipmentMode.MULTI_VEHICLE.name -> stringResource(R.string.mode_multi_vehicle)
    ShipmentMode.MULTI_PORT.name -> stringResource(R.string.mode_multi_port)
    ShipmentMode.UNLOAD.name -> stringResource(R.string.mode_unload)
    else -> mode
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SchedulerScreen(
    onBack: () -> Unit,
    onStartShipment: (Long) -> Unit
) {
    val app = FishyApp.instance
    val repo = app.repository
    val scheduler = app.notificationScheduler
    val items by repo.observeScheduled().collectAsState(initial = emptyList())
    val duplicatedKeys by repo.observeDuplicatedDraftKeys().collectAsState(initial = emptySet())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ScheduledShipmentEntity?>(null) }
    var checklistFor by remember { mutableStateOf<Long?>(null) }
    var pendingDelete by remember { mutableStateOf<ScheduledShipmentEntity?>(null) }
    var pendingStart by remember { mutableStateOf<ScheduledShipmentEntity?>(null) }
    val settings by app.settingsRepository.settings.collectAsState(initial = FishySettings())
    val dateFmt = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }
    val weekdayFmt = remember { SimpleDateFormat("EEEE", Locale.getDefault()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun openNewEditor() {
        val tomorrowStart = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val notifyDefault = Calendar.getInstance().apply {
            timeInMillis = tomorrowStart.timeInMillis
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        editing = ScheduledShipmentEntity(
            scheduledDateMillis = tomorrowStart.timeInMillis,
            scheduledTime = "13:00",
            notificationEnabled = true,
            notificationAtMillis = notifyDefault.timeInMillis
        )
        showEditor = true
    }

    fun duplicate(item: ScheduledShipmentEntity) {
        scope.launch {
            val now = System.currentTimeMillis()
            val copy = item.copy(
                id = 0,
                title = item.title,
                notificationSent = false,
                startNotificationSent = false,
                isCompleted = false,
                createdAtMillis = now,
                updatedAtMillis = now
            )
            val newId = repo.upsertScheduled(copy)
            val checklist = repo.getChecklist(item.id)
            checklist.forEachIndexed { index, row ->
                repo.upsertChecklistItem(
                    ChecklistItemEntity(
                        scheduledShipmentId = newId,
                        title = row.title,
                        isCompleted = false,
                        sortOrder = index
                    )
                )
            }
            repo.log("sched_$newId", ShipmentEventType.DUPLICATED, "sched#${item.id}")
            val saved = copy.copy(id = newId)
            scheduler.schedule(saved)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.nav_scheduler)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        floatingActionButton = {
            if (items.isNotEmpty()) {
                FishyFloatingActionButton(onClick = { openNewEditor() }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                }
            }
        }
    ) { padding ->
        if (items.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(R.string.schedule_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                FishyButton(onClick = { openNewEditor() }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.schedule_add))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    val whenDate = Date(item.scheduledDateMillis)
                    val weekday = weekdayFmt.format(whenDate).replaceFirstChar { ch ->
                        if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
                    }
                    val dateLabel =
                        "${dateFmt.format(whenDate)} — $weekday, ${item.scheduledTime}"
                    ScheduledShipmentCard(
                        item = item,
                        dateLabel = dateLabel,
                        isDuplicated = "sched_${item.id}" in duplicatedKeys,
                        onEdit = {
                            editing = item
                            showEditor = true
                        },
                        onOpenChecklist = { checklistFor = item.id },
                        onStart = { pendingStart = item },
                        onDuplicate = { duplicate(item) },
                        onDelete = { pendingDelete = item },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }

    if (showEditor && editing != null) {
        ScheduledEditorDialog(
            initial = editing!!,
            onDismiss = { showEditor = false },
            onSave = { entity ->
                scope.launch {
                    val id = repo.upsertScheduled(entity)
                    val saved = entity.copy(id = if (entity.id == 0L) id else entity.id)
                    scheduler.schedule(saved)
                    showEditor = false
                }
            }
        )
    }

    checklistFor?.let { id ->
        ScheduledChecklistDialog(scheduledId = id, onDismiss = { checklistFor = null })
    }

    pendingDelete?.let { item ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_scheduled_title),
            message = stringResource(R.string.delete_scheduled_msg, item.customer.ifBlank { item.title.ifBlank { "#${item.id}" } }),
            onConfirm = {
                scope.launch {
                    scheduler.cancel(item.id)
                    repo.deleteScheduled(item.id)
                    pendingDelete = null
                }
            },
            onDismiss = { pendingDelete = null }
        )
    }

    pendingStart?.let { item ->
        val whenDate = Date(item.scheduledDateMillis)
        val weekday = weekdayFmt.format(whenDate).replaceFirstChar { ch ->
            if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
        }
        val dateTimeLine = "${dateFmt.format(whenDate)} — $weekday, ${item.scheduledTime}"
        val customerLine = item.customer.ifBlank {
            stringResource(R.string.customer_not_specified)
        }
        val payload = decodeScheduledPayload(
            item.payloadJson,
            item.mode,
            item.customer,
            item.port,
            item.vessel
        )
        val tonnageKg = ShipmentCalculator.totals(payload).targetWeight
        val tonnageLine = stringResource(
            R.string.weight_label,
            QuantityFormatters.formatWeight(tonnageKg, settings.effectiveThousandsSeparator)
        )
        AlertDialog(
            onDismissRequest = { pendingStart = null },
            containerColor = MaterialTheme.colorScheme.background,
            title = {
                CenteredDialogTitle(stringResource(R.string.start_shipment_confirm_title))
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CenteredDialogMessage(customerLine)
                    CenteredDialogMessage(dateTimeLine)
                    CenteredDialogMessage(tonnageLine)
                }
            },
            confirmButton = {
                DialogCancelConfirmActions(
                    onCancel = { pendingStart = null },
                    onConfirm = {
                        val id = item.id
                        pendingStart = null
                        onStartShipment(id)
                    },
                    confirmText = stringResource(R.string.start)
                )
            }
        )
    }
}

@Composable
private fun ScheduledShipmentCard(
    item: ScheduledShipmentEntity,
    dateLabel: String,
    isDuplicated: Boolean,
    onEdit: () -> Unit,
    onOpenChecklist: () -> Unit,
    onStart: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val repo = FishyApp.instance.repository
    val checklist by repo.observeChecklist(item.id).collectAsState(initial = emptyList())
    val status = remember(checklist) { checklistStatusOf(checklist) }
    var showMenu by remember { mutableStateOf(false) }
    val productName = remember(item.payloadJson) { productLabelFromPayloadJson(item.payloadJson) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        dateLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (item.customer.isBlank()) {
                            stringResource(R.string.customer_not_specified)
                        } else {
                            item.customer
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (isDuplicated) {
                    Text(
                        stringResource(R.string.draft_duplicated_badge),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.actions))
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit)) },
                            onClick = {
                                showMenu = false
                                onEdit()
                            },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.duplicate)) },
                            onClick = {
                                showMenu = false
                                onDuplicate()
                            },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.checklist)) },
                            onClick = {
                                showMenu = false
                                onOpenChecklist()
                            },
                            leadingIcon = { Icon(Icons.Default.Checklist, contentDescription = null) }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                            },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    modeLabel(item.mode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                if (item.vessel.isNotBlank()) {
                    Text(stringResource(R.string.vessel_prefix, item.vessel), style = MaterialTheme.typography.bodyMedium)
                }
                if (item.port.isNotBlank()) {
                    Text(stringResource(R.string.port_prefix, item.port), style = MaterialTheme.typography.bodyMedium)
                }
                if (productName.isNotBlank()) {
                    Text(stringResource(R.string.product_prefix, productName), style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clickable(onClick = onOpenChecklist)
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = when (status) {
                            ChecklistStatus.COMPLETED -> "🟢"
                            ChecklistStatus.PARTIAL -> "🟡"
                            ChecklistStatus.NONE -> "🔴"
                            ChecklistStatus.EMPTY -> if (isLightTheme()) "⚫" else "⚪"
                        },
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = when (status) {
                            ChecklistStatus.COMPLETED -> stringResource(R.string.checklist_completed)
                            ChecklistStatus.PARTIAL -> stringResource(R.string.checklist_partial)
                            ChecklistStatus.NONE -> stringResource(R.string.checklist_none)
                            ChecklistStatus.EMPTY -> stringResource(R.string.checklist_missing)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                FishyButton(onClick = onStart, modifier = Modifier.height(36.dp)) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.start),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.start))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduledEditorDialog(
    initial: ScheduledShipmentEntity,
    onDismiss: () -> Unit,
    onSave: (ScheduledShipmentEntity) -> Unit
) {
    val context = LocalContext.current
    val repo = FishyApp.instance.repository
    val settings by FishyApp.instance.settingsRepository.settings.collectAsState(initial = FishySettings())
    val customers by repo.observeDictionary(DictionaryType.CUSTOMER).collectAsState(initial = emptyList())
    val ports by repo.observeDictionary(DictionaryType.PORT).collectAsState(initial = emptyList())
    val vessels by repo.observeDictionary(DictionaryType.VESSEL).collectAsState(initial = emptyList())
    val productsDict by repo.observeDictionary(DictionaryType.PRODUCT).collectAsState(initial = emptyList())
    val manufacturers by repo.observeDictionary(DictionaryType.MANUFACTURER).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var payload by remember {
        mutableStateOf(
            decodeScheduledPayload(
                initial.payloadJson,
                initial.mode,
                initial.customer,
                initial.port,
                initial.vessel
            )
        )
    }
    var modeExpanded by remember { mutableStateOf(false) }
    var time by remember { mutableStateOf(initial.scheduledTime) }
    var dateMillis by remember { mutableLongStateOf(initial.scheduledDateMillis) }
    var notify by remember { mutableStateOf(initial.notificationEnabled) }
    var notifyAt by remember {
        mutableLongStateOf(
            initial.notificationAtMillis ?: Calendar.getInstance().apply {
                timeInMillis = dateMillis
                set(Calendar.HOUR_OF_DAY, 8)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        )
    }
    val baselinePayloadJson = remember {
        FishyJson.encodePayload(
            decodeScheduledPayload(
                initial.payloadJson,
                initial.mode,
                initial.customer,
                initial.port,
                initial.vessel
            )
        )
    }
    val baselineTime = remember { initial.scheduledTime }
    val baselineDateMillis = remember { initial.scheduledDateMillis }
    val baselineNotify = remember { initial.notificationEnabled }
    val baselineNotifyAt = remember { notifyAt }
    var pendingDelete by remember { mutableStateOf<PendingSchedulerDelete?>(null) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var batchForceExpandToken by remember { mutableStateOf<Any?>(null) }
    var batchEditor by remember { mutableStateOf<BatchLimit?>(null) }
    var showShipmentChecklist by remember { mutableStateOf(false) }

    val isDirty =
        FishyJson.encodePayload(payload) != baselinePayloadJson ||
            time != baselineTime ||
            dateMillis != baselineDateMillis ||
            notify != baselineNotify ||
            (notify && notifyAt != baselineNotifyAt) ||
            (!notify && baselineNotify)

    fun requestDismiss() {
        if (isDirty) showDiscardConfirm = true else onDismiss()
    }

    val dateFmt = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }
    val notifyFmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    val modeOptions = listOf(
        ShipmentMode.MONO to stringResource(R.string.mode_mono),
        ShipmentMode.MULTI_VEHICLE to stringResource(R.string.mode_multi_vehicle),
        ShipmentMode.MULTI_PORT to stringResource(R.string.mode_multi_port),
        ShipmentMode.UNLOAD to stringResource(R.string.mode_unload)
    )

    AlertDialog(
        onDismissRequest = { requestDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        containerColor = MaterialTheme.colorScheme.background,
        title = {
            Box(modifier = Modifier.fillMaxWidth()) {
                CenteredDialogTitle(
                    stringResource(if (initial.id == 0L) R.string.schedule_new else R.string.schedule_edit)
                )
                Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                    IconButton(onClick = { showSettingsMenu = true }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.shipment_settings)
                        )
                    }
                    DropdownMenu(
                        expanded = showSettingsMenu,
                        onDismissRequest = { showSettingsMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        stringResource(R.string.batch_control),
                                        modifier = Modifier.weight(1f).padding(end = 12.dp)
                                    )
                                    Switch(
                                        checked = payload.batchControlEnabled,
                                        onCheckedChange = { enabled ->
                                            payload = payload.copy(batchControlEnabled = enabled)
                                            if (enabled) {
                                                batchForceExpandToken = System.currentTimeMillis()
                                            }
                                        },
                                        colors = fishySwitchColors()
                                    )
                                }
                            },
                            onClick = {
                                val enabled = !payload.batchControlEnabled
                                payload = payload.copy(batchControlEnabled = enabled)
                                if (enabled) {
                                    batchForceExpandToken = System.currentTimeMillis()
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        stringResource(R.string.gross_weight),
                                        modifier = Modifier.weight(1f).padding(end = 12.dp)
                                    )
                                    Switch(
                                        checked = payload.grossWeightEnabled,
                                        onCheckedChange = { enabled ->
                                            payload = payload.copy(grossWeightEnabled = enabled)
                                        },
                                        colors = fishySwitchColors()
                                    )
                                }
                            },
                            onClick = {
                                payload = payload.copy(grossWeightEnabled = !payload.grossWeightEnabled)
                            }
                        )
                    }
                }
            }
        },
        text = {
            CompositionLocalProvider(
                LocalAccordionTitleStyle provides MaterialTheme.typography.bodySmall,
                LocalFormTextStyle provides MaterialTheme.typography.bodySmall
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = modeExpanded,
                    onExpandedChange = { modeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = modeOptions.first { it.first == payload.mode }.second,
                        onValueChange = {},
                        readOnly = true,
                        label = {
                            Text(
                                stringResource(R.string.mode_field),
                                style = formLabelStyleOrDefault()
                            )
                        },
                        textStyle = formTextStyleOrDefault(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = modeExpanded,
                        onDismissRequest = { modeExpanded = false }
                    ) {
                        modeOptions.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    payload = switchPayloadMode(payload, value)
                                    modeExpanded = false
                                }
                            )
                        }
                    }
                }

                DatePickerField(
                    selectedDateMillis = dateMillis,
                    onDateSelected = { dateMillis = it },
                    label = stringResource(R.string.schedule_date_field)
                )
                TimePickerField(
                    time = time,
                    onTimeChange = { time = it },
                    label = stringResource(R.string.schedule_time_field)
                )

                HorizontalDivider()

                ScheduledPayloadFields(
                    payload = payload,
                    onChange = { payload = it },
                    customers = customers,
                    ports = ports,
                    vessels = vessels,
                    productsDict = productsDict,
                    manufacturers = manufacturers,
                    autoSpaceContainers = settings.effectiveAutoSpaceContainers,
                    autoSpaceVehicles = settings.effectiveAutoSpaceVehicles,
                    thousandsSeparator = settings.effectiveThousandsSeparator,
                    onAddToDictionary = { type, value ->
                        scope.launch { repo.addDictionary(type, value) }
                    },
                    onRequestDelete = { title, message, onConfirm ->
                        pendingDelete = PendingSchedulerDelete(title, message, onConfirm)
                    },
                    batchForceExpandToken = batchForceExpandToken,
                    onEnterBatches = { batchEditor = BatchLimit() },
                    onEditBatch = { batchEditor = it },
                    onDeleteBatch = { limit ->
                        payload = payload.copy(
                            batchLimits = payload.batchLimits.filter { it.id != limit.id }
                        )
                    }
                )

                FishyButton(
                    onClick = { showShipmentChecklist = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Checklist, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.checklist))
                }

                HorizontalDivider()
                Text(
                    stringResource(R.string.notifications),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.notify_enable))
                    Switch(checked = notify, onCheckedChange = { notify = it }, colors = fishySwitchColors())
                }
                if (notify) {
                    val notifyTime = remember(notifyAt) {
                        val c = Calendar.getInstance().apply { timeInMillis = notifyAt }
                        "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
                    }
                    DatePickerField(
                        selectedDateMillis = notifyAt,
                        onDateSelected = { newDate ->
                            val c = Calendar.getInstance().apply { timeInMillis = notifyAt }
                            val h = c.get(Calendar.HOUR_OF_DAY)
                            val m = c.get(Calendar.MINUTE)
                            notifyAt = Calendar.getInstance().apply {
                                timeInMillis = newDate
                                set(Calendar.HOUR_OF_DAY, h)
                                set(Calendar.MINUTE, m)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }.timeInMillis
                        },
                        label = stringResource(R.string.notify_date_field)
                    )
                    TimePickerField(
                        time = notifyTime,
                        onTimeChange = { newTime ->
                            val parts = newTime.split(":")
                            val h = parts.getOrNull(0)?.toIntOrNull() ?: 9
                            val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                            notifyAt = Calendar.getInstance().apply {
                                timeInMillis = notifyAt
                                set(Calendar.HOUR_OF_DAY, h)
                                set(Calendar.MINUTE, m)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }.timeInMillis
                        },
                        label = stringResource(R.string.notify_time_field)
                    )
                    Text(
                        stringResource(R.string.notify_preview, notifyFmt.format(Date(notifyAt))),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
            }
        },
        confirmButton = {
            DialogCancelConfirmActions(
                onCancel = { requestDismiss() },
                onConfirm = {
                    val finalPayload = ensurePayloadStructure(payload)
                    val autoTitle = listOf(
                        finalPayload.customer,
                        finalPayload.vessel.ifBlank {
                            finalPayload.multiPorts.firstOrNull()?.vessel.orEmpty()
                        },
                        dateFmt.format(Date(dateMillis))
                    )
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                        .ifBlank { context.getString(R.string.shipment_default) }
                    onSave(
                        initial.copy(
                            title = autoTitle,
                            customer = finalPayload.customer,
                            port = finalPayload.port.ifBlank {
                                finalPayload.multiPorts.firstOrNull()?.port.orEmpty()
                            },
                            vessel = finalPayload.vessel.ifBlank {
                                finalPayload.multiPorts.firstOrNull()?.vessel.orEmpty()
                            },
                            payloadJson = FishyJson.encodePayload(finalPayload),
                            scheduledTime = time,
                            scheduledDateMillis = dateMillis,
                            notificationEnabled = notify,
                            notificationAtMillis = if (notify) notifyAt else null,
                            mode = finalPayload.mode.name,
                            updatedAtMillis = System.currentTimeMillis()
                        )
                    )
                },
                confirmText = stringResource(R.string.save)
            )
        }
    )

    pendingDelete?.let { del ->
        ConfirmDeleteDialog(
            title = del.title,
            message = del.message,
            onConfirm = {
                del.onConfirm()
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            containerColor = MaterialTheme.colorScheme.background,
            title = { CenteredDialogTitle(stringResource(R.string.cancel_planning_title)) },
            confirmButton = {
                DialogCancelConfirmActions(
                    onCancel = { showDiscardConfirm = false },
                    onConfirm = {
                        showDiscardConfirm = false
                        onDismiss()
                    },
                    cancelText = stringResource(R.string.no),
                    confirmText = stringResource(R.string.confirm)
                )
            }
        )
    }

    batchEditor?.let { editing ->
        BatchEntryDialog(
            initial = editing,
            productsDict = productsDict,
            manufacturers = manufacturers,
            onDismiss = { batchEditor = null },
            onSave = { limit ->
                val exists = payload.batchLimits.any { it.id == limit.id }
                payload = payload.copy(
                    batchLimits = if (exists) {
                        payload.batchLimits.map { if (it.id == limit.id) limit else it }
                    } else {
                        payload.batchLimits + limit
                    }
                )
                batchEditor = null
            },
            onAddToDictionary = { type, value ->
                scope.launch { repo.addDictionary(type, value) }
            },
            isNew = payload.batchLimits.none { it.id == editing.id }
        )
    }

    if (showShipmentChecklist) {
        PayloadChecklistDialog(
            checklist = payload.checklist,
            onChange = { payload = payload.copy(checklist = it) },
            onDismiss = { showShipmentChecklist = false }
        )
    }
}

private data class PendingSchedulerDelete(
    val title: String,
    val message: String,
    val onConfirm: () -> Unit
)

@Composable
private fun ScheduledChecklistDialog(scheduledId: Long, onDismiss: () -> Unit) {
    val repo = FishyApp.instance.repository
    val items by repo.observeChecklist(scheduledId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    val completed = items.count { it.isCompleted }
    val total = items.size

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        title = { CenteredDialogTitle(stringResource(R.string.checklist_prep)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ChecklistStatusBanner(completed = completed, total = total)

                if (items.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items.forEach { item ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = item.isCompleted,
                                    onCheckedChange = {
                                        scope.launch {
                                            repo.upsertChecklistItem(item.copy(isCompleted = it))
                                        }
                                    },
                                    colors = fishyCheckboxColors()
                                )
                                Text(item.title, modifier = Modifier.weight(1f))
                                IconButton(onClick = {
                                    scope.launch { repo.deleteChecklistItem(item.id) }
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                }
                            }
                        }
                    }
                }

                FishyButton(
                    onClick = { showAdd = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.checklist_add_item))
                }
            }
        },
        confirmButton = {
            DialogCenteredFishyButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok_done))
            }
        }
    )

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            containerColor = MaterialTheme.colorScheme.background,
            title = { CenteredDialogTitle(stringResource(R.string.checklist_add_item)) },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text(stringResource(R.string.checklist_item)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = FishySentenceKeyboardOptions
                )
            },
            confirmButton = {
                DialogCancelConfirmActions(
                    onCancel = { showAdd = false },
                    onConfirm = {
                        if (newTitle.isNotBlank()) {
                            scope.launch {
                                repo.upsertChecklistItem(
                                    ChecklistItemEntity(
                                        scheduledShipmentId = scheduledId,
                                        title = newTitle.trim(),
                                        sortOrder = items.size
                                    )
                                )
                                newTitle = ""
                                showAdd = false
                            }
                        }
                    },
                    confirmText = stringResource(R.string.save)
                )
            }
        )
    }
}

@Composable
private fun PayloadChecklistDialog(
    checklist: List<ChecklistTask>,
    onChange: (List<ChecklistTask>) -> Unit,
    onDismiss: () -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    val completed = checklist.count { it.isCompleted }
    val total = checklist.size

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        title = { CenteredDialogTitle(stringResource(R.string.checklist_shipment)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ChecklistStatusBanner(completed = completed, total = total)

                if (checklist.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        checklist.forEach { task ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = task.isCompleted,
                                    onCheckedChange = { checked ->
                                        onChange(
                                            checklist.map {
                                                if (it.id == task.id) it.copy(isCompleted = checked) else it
                                            }
                                        )
                                    },
                                    colors = fishyCheckboxColors()
                                )
                                OutlinedTextField(
                                    value = task.title,
                                    onValueChange = { title ->
                                        onChange(
                                            checklist.map {
                                                if (it.id == task.id) it.copy(title = title) else it
                                            }
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = FishySentenceKeyboardOptions
                                )
                                IconButton(onClick = {
                                    onChange(checklist.filter { it.id != task.id })
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                }
                            }
                        }
                    }
                }

                FishyButton(
                    onClick = { showAdd = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.checklist_add_item))
                }
            }
        },
        confirmButton = {
            DialogCenteredFishyButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok_done))
            }
        }
    )

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            containerColor = MaterialTheme.colorScheme.background,
            title = { CenteredDialogTitle(stringResource(R.string.checklist_add_item)) },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text(stringResource(R.string.checklist_item)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = FishySentenceKeyboardOptions
                )
            },
            confirmButton = {
                DialogCancelConfirmActions(
                    onCancel = { showAdd = false },
                    onConfirm = {
                        if (newTitle.isNotBlank()) {
                            onChange(
                                checklist + ChecklistTask(
                                    title = newTitle.trim()
                                )
                            )
                            newTitle = ""
                            showAdd = false
                        }
                    },
                    confirmText = stringResource(R.string.save)
                )
            }
        )
    }
}
