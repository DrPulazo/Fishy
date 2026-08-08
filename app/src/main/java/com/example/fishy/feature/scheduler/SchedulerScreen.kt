package com.example.fishy.feature.scheduler

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.fishy.FishyApp
import com.example.fishy.R
import com.example.fishy.data.local.entity.ChecklistItemEntity
import com.example.fishy.data.local.entity.ScheduledReminderEntity
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
import com.example.fishy.domain.model.ShipmentSummaries
import com.example.fishy.domain.model.SummaryStrings
import com.example.fishy.notifications.NotificationScheduler
import com.example.fishy.ui.ErrorFeedback
import com.example.fishy.ui.components.BatchEntryDialog
import com.example.fishy.ui.components.ConfirmDeleteDialog
import com.example.fishy.ui.components.ConfirmSaveDialog
import com.example.fishy.ui.components.CenteredDialogMessage
import com.example.fishy.ui.components.CenteredDialogTitle
import com.example.fishy.ui.components.CenteredEmptyBody
import com.example.fishy.ui.components.ChecklistStatusBanner
import com.example.fishy.ui.components.ColumnScrollIndicator
import com.example.fishy.ui.components.DatePickerField
import com.example.fishy.ui.components.DialogCancelConfirmActions
import com.example.fishy.ui.components.DialogCenteredFishyButton
import com.example.fishy.ui.components.EmptyListPlaceholder
import com.example.fishy.ui.components.FabContentClearance
import com.example.fishy.ui.components.FabEndInsetForScrollbar
import com.example.fishy.ui.components.FishyButton
import com.example.fishy.ui.components.FishyFloatingActionButton
import com.example.fishy.ui.components.FishyPulseCheckbox
import com.example.fishy.ui.components.FishySentenceKeyboardOptions
import com.example.fishy.ui.components.LazyListScrollIndicator
import com.example.fishy.ui.components.LocalAccordionTitleStyle
import com.example.fishy.ui.components.LocalFormTextStyle
import com.example.fishy.ui.components.TimePickerField
import com.example.fishy.ui.components.fishyCheckboxColors
import com.example.fishy.ui.components.fishySwitchColors
import com.example.fishy.feature.shipment.ModePickerDialog
import com.example.fishy.ui.components.formLabelStyleOrDefault
import com.example.fishy.ui.components.formTextStyleOrDefault
import com.example.fishy.ui.theme.Error
import com.example.fishy.ui.theme.PlaceholderGrey
import com.example.fishy.ui.theme.isLightTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val MAX_PREP_REMINDERS = 15

private data class ReminderDraft(
    val localKey: Long,
    val id: Long,
    val atMillis: Long
)

private fun List<ReminderDraft>.sortedByAt(): List<ReminderDraft> =
    sortedBy { it.atMillis }

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

/** Calendar day from [dateMillis] combined with "HH:mm" [timeHhMm]. */
private fun combineScheduledAt(dateMillis: Long, timeHhMm: String): Long {
    val parts = timeHhMm.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
    return Calendar.getInstance().apply {
        timeInMillis = dateMillis
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private const val REMINDER_MIN_GAP_MS = 5L * 60L * 1000L
private const val REMINDER_HOUR_MS = 60L * 60L * 1000L

private fun reminderDeadline(shipmentAt: Long): Long = shipmentAt - REMINDER_MIN_GAP_MS

private fun eightAmOnDay(dayMillis: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = dayMillis
        set(Calendar.HOUR_OF_DAY, 8)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

/**
 * First prep slot: 08:00 if <= deadline, else deadline;
 * if still <= now, retry deadline; null if no future slot.
 */
private fun firstReminderAt(shipmentAt: Long, dayMillis: Long, now: Long): Long? {
    val deadline = reminderDeadline(shipmentAt)
    val eight = eightAmOnDay(dayMillis)
    var candidate = if (eight <= deadline) eight else deadline
    if (candidate <= now) candidate = deadline
    return candidate.takeIf { it > now }
}

/**
 * Next slot: last+1h (or [firstReminderAt] if empty), skipping [occupied].
 * Null if result is past or after deadline.
 */
private fun nextReminderCandidate(
    lastAt: Long?,
    occupied: Collection<Long>,
    shipmentAt: Long,
    dayMillis: Long,
    now: Long
): Long? {
    if (lastAt == null) return firstReminderAt(shipmentAt, dayMillis, now)
    val deadline = reminderDeadline(shipmentAt)
    var candidate = lastAt + REMINDER_HOUR_MS
    while (candidate in occupied) {
        candidate += REMINDER_HOUR_MS
    }
    if (candidate <= now || candidate > deadline) return null
    return candidate
}

private enum class ReminderTimeError {
    PAST, AFTER_SHIPMENT, DUPLICATE
}

private fun validateReminderAt(
    at: Long,
    shipmentAt: Long,
    now: Long,
    otherTimes: Collection<Long>
): ReminderTimeError? = when {
    at <= now -> ReminderTimeError.PAST
    at > reminderDeadline(shipmentAt) -> ReminderTimeError.AFTER_SHIPMENT
    at in otherTimes -> ReminderTimeError.DUPLICATE
    else -> null
}

private fun reminderErrorRes(error: ReminderTimeError): Int = when (error) {
    ReminderTimeError.PAST -> R.string.notify_error_past
    ReminderTimeError.AFTER_SHIPMENT -> R.string.notify_error_after_shipment
    ReminderTimeError.DUPLICATE -> R.string.notify_error_duplicate
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SchedulerScreen(
    onBack: () -> Unit,
    onStartShipment: (scheduledId: Long, linkedDraftId: Long?) -> Unit,
    openPrepChecklistId: Long? = null,
    onPrepChecklistConsumed: () -> Unit = {}
) {
    val app = FishyApp.instance
    val repo = app.repository
    val scheduler = app.notificationScheduler
    val items by repo.observeScheduled().collectAsState(initial = emptyList())
    val duplicatedKeys by repo.observeDuplicatedDraftKeys().collectAsState(initial = emptySet())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showEditor by remember { mutableStateOf(false) }
    var showNewModePicker by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ScheduledShipmentEntity?>(null) }
    var editorScrollToNotifications by remember { mutableStateOf(false) }
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

    LaunchedEffect(openPrepChecklistId) {
        val id = openPrepChecklistId
        if (id != null && id > 0L) {
            checklistFor = id
        }
    }

    fun openNewEditor() {
        showNewModePicker = true
    }

    fun openNewEditorWithMode(mode: ShipmentMode) {
        val tomorrowStart = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val payload = emptyPayloadForMode(mode)
        editing = ScheduledShipmentEntity(
            scheduledDateMillis = tomorrowStart.timeInMillis,
            scheduledTime = "13:00",
            mode = mode.name,
            payloadJson = FishyJson.encodePayload(payload),
            notificationEnabled = true,
            notificationAtMillis = null
        )
        editorScrollToNotifications = false
        showEditor = true
    }

    fun duplicate(item: ScheduledShipmentEntity) {
        scope.launch {
            val now = System.currentTimeMillis()
            val copy = item.copy(
                id = 0,
                title = item.title,
                notificationAtMillis = null,
                notificationSent = false,
                startNotificationSent = false,
                isCompleted = false,
                linkedDraftId = null,
                createdAtMillis = now,
                updatedAtMillis = now
            )
            val oldStart = NotificationScheduler.scheduledStartMillis(item)
            val newStart = NotificationScheduler.scheduledStartMillis(copy)
            val delta = newStart - oldStart
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
            val reminders = repo.getReminders(item.id)
            repo.replaceReminders(
                newId,
                reminders.mapIndexed { index, row ->
                    ScheduledReminderEntity(
                        id = 0,
                        scheduledShipmentId = newId,
                        atMillis = row.atMillis + delta,
                        sent = false,
                        sortOrder = index
                    )
                }.filter { it.atMillis > now }
                    .mapIndexed { index, row -> row.copy(sortOrder = index) }
            )
            repo.log("sched_$newId", ShipmentEventType.DUPLICATED, "sched#${item.id}")
            val saved = copy.copy(id = newId)
            scheduler.scheduleSuspend(saved)
        }
    }

    val listState = rememberLazyListState()

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
            FishyFloatingActionButton(
                onClick = { openNewEditor() },
                modifier = Modifier.padding(end = FabEndInsetForScrollbar)
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.schedule_add))
            }
        }
    ) { padding ->
        CenteredEmptyBody(
            isEmpty = items.isEmpty(),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            empty = {
                EmptyListPlaceholder(
                    emoji = "🕗",
                    title = stringResource(R.string.schedule_empty_title),
                    hint = stringResource(R.string.schedule_empty_hint)
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
                    contentPadding = PaddingValues(bottom = FabContentClearance)
                ) {
                    items(items, key = { it.id }) { item ->
                        val whenDate = Date(item.scheduledDateMillis)
                        val weekday = weekdayFmt.format(whenDate).replaceFirstChar { ch ->
                            if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
                        }
                        val dateLabel =
                            "${dateFmt.format(whenDate)} — $weekday — ${item.scheduledTime}"
                        ScheduledShipmentCard(
                            item = item,
                            dateLabel = dateLabel,
                            isDuplicated = "sched_${item.id}" in duplicatedKeys,
                            thousandsSeparator = settings.effectiveThousandsSeparator,
                            onEdit = {
                                editorScrollToNotifications = false
                                editing = item
                                showEditor = true
                            },
                            onEditNotifications = {
                                editorScrollToNotifications = true
                                editing = item
                                showEditor = true
                            },
                            onOpenChecklist = { checklistFor = item.id },
                            onStart = {
                                val draftId = item.linkedDraftId
                                if (draftId != null) {
                                    scope.launch {
                                        if (repo.getShipment(draftId) != null) {
                                            onStartShipment(item.id, draftId)
                                        } else {
                                            repo.clearScheduleLinkByDraft(draftId)
                                            pendingStart = item
                                        }
                                    }
                                } else {
                                    pendingStart = item
                                }
                            },
                            onDuplicate = { duplicate(item) },
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

    if (showNewModePicker) {
        ModePickerDialog(
            initialMode = ShipmentMode.MONO,
            confirmText = stringResource(R.string.action_select),
            onDismiss = { showNewModePicker = false },
            onConfirm = { mode ->
                showNewModePicker = false
                openNewEditorWithMode(mode)
            }
        )
    }

    if (showEditor && editing != null) {
        ScheduledEditorDialog(
            initial = editing!!,
            scrollToNotifications = editorScrollToNotifications,
            onDismiss = {
                showEditor = false
                editorScrollToNotifications = false
            },
            onSave = { entity, reminderDrafts ->
                scope.launch {
                    val previous = editing
                    val startChanged = previous != null && (
                        NotificationScheduler.scheduledStartMillis(previous) !=
                            NotificationScheduler.scheduledStartMillis(entity)
                    )
                    val cleaned = entity.copy(
                        notificationAtMillis = null,
                        notificationSent = false,
                        startNotificationSent = if (startChanged) false else entity.startNotificationSent
                    )
                    val id = repo.upsertScheduled(cleaned)
                    val savedId = if (entity.id == 0L) id else entity.id
                    repo.replaceReminders(
                        savedId,
                        reminderDrafts.mapIndexed { index, draft ->
                            ScheduledReminderEntity(
                                id = draft.id,
                                scheduledShipmentId = savedId,
                                atMillis = draft.atMillis,
                                sortOrder = index
                            )
                        }
                    )
                    val saved = repo.getScheduled(savedId) ?: cleaned.copy(id = savedId)
                    scheduler.scheduleSuspend(saved)
                    showEditor = false
                    editorScrollToNotifications = false
                }
            }
        )
    }

    checklistFor?.let { id ->
        LaunchedEffect(id, openPrepChecklistId) {
            if (openPrepChecklistId == id) {
                onPrepChecklistConsumed()
            }
            scheduler.deliverDueAndSchedule(id)
        }
        ScheduledChecklistDialog(scheduledId = id, onDismiss = { checklistFor = null })
    }

    pendingDelete?.let { item ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_scheduled_title),
            message = stringResource(R.string.delete_scheduled_msg, item.customer.ifBlank { item.title.ifBlank { "#${item.id}" } }),
            onConfirm = {
                scope.launch {
                    scheduler.cancelSuspend(item.id)
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
        val dateTimeLine = "${dateFmt.format(whenDate)} — $weekday — ${item.scheduledTime}"
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
                        onStartShipment(id, null)
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
    thousandsSeparator: Boolean,
    onEdit: () -> Unit,
    onEditNotifications: () -> Unit,
    onOpenChecklist: () -> Unit,
    onStart: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val repo = FishyApp.instance.repository
    val checklist by repo.observeChecklist(item.id).collectAsState(initial = emptyList())
    val status = remember(checklist) { checklistStatusOf(checklist) }
    val checklistDone = checklist.count { it.isCompleted }
    val checklistTotal = checklist.size
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val summaryCopy = remember(context) { SummaryStrings.from(context.resources) }
    val bodyLines = remember(
        item.payloadJson,
        item.mode,
        item.customer,
        item.port,
        item.vessel,
        thousandsSeparator,
        summaryCopy
    ) {
        val payload = decodeScheduledPayload(
            item.payloadJson,
            item.mode,
            item.customer,
            item.port,
            item.vessel
        )
        ShipmentSummaries.scheduleCardBodyLines(payload, thousandsSeparator, summaryCopy)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        dateLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.linkedDraftId != null) {
                        Text(
                            stringResource(R.string.schedule_status_started),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    } else if (isDuplicated) {
                        Text(
                            stringResource(R.string.draft_duplicated_badge),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                val reminders by repo.observeReminders(item.id).collectAsState(initial = emptyList())
                val reminderCount = reminders.size
                val remindersActive = item.notificationEnabled && reminderCount > 0
                val countLabel = when {
                    !remindersActive -> null
                    reminderCount > 9 -> "!"
                    else -> reminderCount.toString()
                }
                val notifyCd = stringResource(
                    if (remindersActive) R.string.notify_cd else R.string.notify_off_cd
                )
                IconButton(onClick = onEditNotifications) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (remindersActive) {
                                Icons.Default.NotificationsActive
                            } else {
                                Icons.Default.NotificationsOff
                            },
                            contentDescription = if (countLabel != null) {
                                "$notifyCd, $countLabel"
                            } else {
                                notifyCd
                            },
                            modifier = Modifier.size(24.dp),
                            tint = if (remindersActive) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                PlaceholderGrey
                            }
                        )
                        if (countLabel != null) {
                            Text(
                                text = countLabel,
                                color = MaterialTheme.colorScheme.background,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    lineHeight = 13.sp
                                ),
                                modifier = Modifier.offset(y = 0.5.dp)
                            )
                        }
                    }
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

            Column(
                modifier = Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    if (item.customer.isBlank()) {
                        stringResource(R.string.customer_not_specified)
                    } else {
                        stringResource(R.string.customer_binding, item.customer)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                bodyLines.forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodyMedium)
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
                            ChecklistStatus.COMPLETED -> stringResource(
                                R.string.checklist_completed,
                                checklistDone,
                                checklistTotal
                            )
                            ChecklistStatus.PARTIAL -> stringResource(
                                R.string.checklist_partial,
                                checklistDone,
                                checklistTotal
                            )
                            ChecklistStatus.NONE -> stringResource(
                                R.string.checklist_none,
                                checklistDone,
                                checklistTotal
                            )
                            ChecklistStatus.EMPTY -> stringResource(R.string.checklist_missing)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                FishyButton(
                    onClick = onStart,
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    // Same width for Start/Continue; icon+label stay tight and centered.
                    val startLabel = stringResource(R.string.start)
                    val continueLabel = stringResource(R.string.schedule_continue)
                    val label =
                        if (item.linkedDraftId != null) continueLabel else startLabel
                    Box(contentAlignment = Alignment.Center) {
                        Row(
                            modifier = Modifier.alpha(0f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Box {
                                Text(startLabel)
                                Text(continueLabel)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(label)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ScheduledEditorDialog(
    initial: ScheduledShipmentEntity,
    scrollToNotifications: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (ScheduledShipmentEntity, List<ReminderDraft>) -> Unit
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
    var time by remember { mutableStateOf(initial.scheduledTime) }
    var dateMillis by remember { mutableLongStateOf(initial.scheduledDateMillis) }
    var notify by remember { mutableStateOf(initial.notificationEnabled) }
    var reminders by remember { mutableStateOf<List<ReminderDraft>>(emptyList()) }
    var nextLocalKey by remember { mutableLongStateOf(-1L) }
    var remindersReady by remember { mutableStateOf(false) }
    var baselineNotify by remember { mutableStateOf(initial.notificationEnabled) }
    var baselineReminderTimes by remember { mutableStateOf<List<Long>>(emptyList()) }
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
    var pendingDelete by remember { mutableStateOf<PendingSchedulerDelete?>(null) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var pendingBatchMismatchSave by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var batchForceExpandToken by remember { mutableStateOf<Any?>(null) }
    var batchEditor by remember { mutableStateOf<BatchLimit?>(null) }
    var showShipmentChecklist by remember { mutableStateOf(false) }
    var errorReminderKeys by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var pendingReminderDelete by remember { mutableStateOf<ReminderDraft?>(null) }
    val notificationsBringIntoView = remember { BringIntoViewRequester() }
    val addReminderBringIntoView = remember { BringIntoViewRequester() }
    val editorScrollState = rememberScrollState()

    LaunchedEffect(initial.id) {
        val now = System.currentTimeMillis()
        val shipmentAt = combineScheduledAt(initial.scheduledDateMillis, initial.scheduledTime)
        if (initial.id == 0L) {
            val at = firstReminderAt(shipmentAt, initial.scheduledDateMillis, now)
            if (at != null) {
                reminders = listOf(ReminderDraft(localKey = -1L, id = 0L, atMillis = at))
                notify = true
                nextLocalKey = -2L
            } else {
                reminders = emptyList()
                notify = false
                nextLocalKey = -1L
            }
        } else {
            FishyApp.instance.notificationScheduler.deliverDueAndSchedule(initial.id)
            val loaded = repo.getReminders(initial.id)
            reminders = if (loaded.isNotEmpty()) {
                loaded.map { ReminderDraft(localKey = it.id, id = it.id, atMillis = it.atMillis) }
                    .sortedByAt()
            } else {
                emptyList()
            }
            notify = initial.notificationEnabled && reminders.isNotEmpty()
            nextLocalKey = -1L
        }
    baselineNotify = notify
    baselineReminderTimes = reminders.map { it.atMillis }
    errorReminderKeys = emptySet()
    remindersReady = true
}

    val isDirty =
        remindersReady && (
            FishyJson.encodePayload(payload) != baselinePayloadJson ||
                time != baselineTime ||
                dateMillis != baselineDateMillis ||
                notify != baselineNotify ||
                reminders.map { it.atMillis } != baselineReminderTimes ||
                reminders.size != baselineReminderTimes.size
            )

    fun requestDismiss() {
        if (isDirty) showDiscardConfirm = true else onDismiss()
    }

    fun scrollToAddReminder() {
        scope.launch {
            delay(80)
            addReminderBringIntoView.bringIntoView()
        }
    }

    fun setReminderTime(localKey: Long, updated: Long) {
        reminders = reminders.map {
            if (it.localKey == localKey) it.copy(atMillis = updated) else it
        }.sortedByAt()
        errorReminderKeys = errorReminderKeys - localKey
    }

    fun toastNoReminderSlot() {
        ErrorFeedback.vibrate(context)
        Toast.makeText(
            context,
            context.getString(R.string.notify_error_no_slot),
            Toast.LENGTH_SHORT
        ).show()
    }

    fun enableRemindersOrFail(): Boolean {
        if (reminders.isNotEmpty()) {
            notify = true
            scrollToAddReminder()
            return true
        }
        val shipmentAt = combineScheduledAt(dateMillis, time)
        val at = firstReminderAt(shipmentAt, dateMillis, System.currentTimeMillis())
        if (at == null) {
            toastNoReminderSlot()
            return false
        }
        val key = nextLocalKey
        nextLocalKey -= 1
        reminders = listOf(ReminderDraft(localKey = key, id = 0L, atMillis = at))
        notify = true
        scrollToAddReminder()
        return true
    }

    fun addReminderOrFail() {
        val shipmentAt = combineScheduledAt(dateMillis, time)
        val at = nextReminderCandidate(
            lastAt = reminders.lastOrNull()?.atMillis,
            occupied = reminders.map { it.atMillis },
            shipmentAt = shipmentAt,
            dayMillis = dateMillis,
            now = System.currentTimeMillis()
        )
        if (at == null) {
            toastNoReminderSlot()
            return
        }
        val key = nextLocalKey
        nextLocalKey -= 1
        reminders = (reminders + ReminderDraft(localKey = key, id = 0L, atMillis = at)).sortedByAt()
        scrollToAddReminder()
    }

    LaunchedEffect(scrollToNotifications, remindersReady) {
        if (!scrollToNotifications || !remindersReady) return@LaunchedEffect
        delay(80)
        if (notify) {
            addReminderBringIntoView.bringIntoView()
        } else {
            notificationsBringIntoView.bringIntoView()
        }
    }

    val dateFmt = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }
    val notifyFmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    val modeLabel = when (payload.mode) {
        ShipmentMode.MONO -> stringResource(R.string.mode_mono)
        ShipmentMode.MULTI_VEHICLE -> stringResource(R.string.mode_multi_vehicle)
        ShipmentMode.MULTI_PORT -> stringResource(R.string.mode_multi_port)
        ShipmentMode.UNLOAD -> stringResource(R.string.mode_unload)
    }

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
                        if (
                            payload.mode == ShipmentMode.MULTI_VEHICLE ||
                            payload.mode == ShipmentMode.UNLOAD
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
                        }
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(end = 8.dp)
                    .verticalScroll(editorScrollState),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = modeLabel,
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = {
                        Text(
                            stringResource(R.string.mode_field),
                            style = formLabelStyleOrDefault()
                        )
                    },
                    textStyle = formTextStyleOrDefault(),
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledContainerColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

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
                        scope.launch {
                            if (repo.addDictionary(type, value)) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.dict_added, value.trim()),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
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
                    Text(stringResource(R.string.checklist_shipment))
                }

                HorizontalDivider()
                Column(
                    modifier = Modifier.bringIntoViewRequester(notificationsBringIntoView),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.notify_enable))
                        Switch(
                            checked = notify,
                            onCheckedChange = { enabled ->
                                if (!enabled) {
                                    notify = false
                                } else {
                                    enableRemindersOrFail()
                                }
                            },
                            colors = fishySwitchColors()
                        )
                    }
                    if (notify) {
                        reminders.forEachIndexed { index, draft ->
                            key(draft.localKey) {
                            val reminderCal = Calendar.getInstance().apply { timeInMillis = draft.atMillis }
                            val reminderTime =
                                "%02d:%02d".format(
                                    reminderCal.get(Calendar.HOUR_OF_DAY),
                                    reminderCal.get(Calendar.MINUTE)
                                )
                            val rowError = draft.localKey in errorReminderKeys
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    DatePickerField(
                                        selectedDateMillis = draft.atMillis,
                                        onDateSelected = { newDate ->
                                            val c = Calendar.getInstance().apply { timeInMillis = draft.atMillis }
                                            val h = c.get(Calendar.HOUR_OF_DAY)
                                            val m = c.get(Calendar.MINUTE)
                                            val updated = Calendar.getInstance().apply {
                                                timeInMillis = newDate
                                                set(Calendar.HOUR_OF_DAY, h)
                                                set(Calendar.MINUTE, m)
                                                set(Calendar.SECOND, 0)
                                                set(Calendar.MILLISECOND, 0)
                                            }.timeInMillis
                                            setReminderTime(draft.localKey, updated)
                                        },
                                        modifier = Modifier.weight(1.1f),
                                        isError = rowError
                                    )
                                    TimePickerField(
                                        time = reminderTime,
                                        onTimeChange = { newTime ->
                                            val parts = newTime.split(":")
                                            val h = parts.getOrNull(0)?.toIntOrNull() ?: 9
                                            val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                                            val updated = Calendar.getInstance().apply {
                                                timeInMillis = draft.atMillis
                                                set(Calendar.HOUR_OF_DAY, h)
                                                set(Calendar.MINUTE, m)
                                                set(Calendar.SECOND, 0)
                                                set(Calendar.MILLISECOND, 0)
                                            }.timeInMillis
                                            setReminderTime(draft.localKey, updated)
                                        },
                                        modifier = Modifier.weight(0.9f),
                                        isError = rowError
                                    )
                                    IconButton(
                                        onClick = { pendingReminderDelete = draft }
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                                if (index < reminders.lastIndex) {
                                    HorizontalDivider()
                                }
                            }
                            }
                        }
                        if (reminders.size < MAX_PREP_REMINDERS) {
                            FishyButton(
                                onClick = { addReminderOrFail() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bringIntoViewRequester(addReminderBringIntoView)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.reminders_add))
                            }
                        } else {
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bringIntoViewRequester(addReminderBringIntoView)
                            )
                        }
                    } else {
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .bringIntoViewRequester(addReminderBringIntoView)
                        )
                    }
                }
            }
            ColumnScrollIndicator(
                scrollState = editorScrollState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .padding(vertical = 4.dp)
            )
            }
            }
        },
        confirmButton = {
            DialogCancelConfirmActions(
                onCancel = { requestDismiss() },
                onConfirm = {
                    val now = System.currentTimeMillis()
                    val shipmentAt = combineScheduledAt(dateMillis, time)
                    if (shipmentAt < now) {
                        ErrorFeedback.vibrate(context)
                        Toast.makeText(
                            context,
                            context.getString(R.string.schedule_error_past),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        val reminderConflicts = if (notify && reminders.isNotEmpty()) {
                            val conflicts = linkedSetOf<Long>()
                            var firstError: ReminderTimeError? = null
                            reminders.forEach { draft ->
                                val others = reminders
                                    .filter { it.localKey != draft.localKey }
                                    .map { it.atMillis }
                                val error = validateReminderAt(draft.atMillis, shipmentAt, now, others)
                                if (error != null) {
                                    conflicts += draft.localKey
                                    if (firstError == null || error.ordinal < firstError!!.ordinal) {
                                        firstError = error
                                    }
                                }
                            }
                            if (conflicts.isNotEmpty()) {
                                errorReminderKeys = conflicts
                                ErrorFeedback.vibrate(context)
                                Toast.makeText(
                                    context,
                                    context.getString(reminderErrorRes(firstError!!)),
                                    Toast.LENGTH_SHORT
                                ).show()
                                true
                            } else {
                                false
                            }
                        } else {
                            false
                        }
                        if (!reminderConflicts) {
                            errorReminderKeys = emptySet()
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
                            val doSave = {
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
                                        notificationEnabled = notify && reminders.isNotEmpty(),
                                        notificationAtMillis = null,
                                        notificationSent = false,
                                        mode = finalPayload.mode.name,
                                        updatedAtMillis = System.currentTimeMillis()
                                    ),
                                    reminders
                                )
                            }
                            if (ShipmentCalculator.batchTransportMismatches(finalPayload).isNotEmpty()) {
                                pendingBatchMismatchSave = doSave
                            } else {
                                doSave()
                            }
                        }
                    }
                },
                confirmText = stringResource(R.string.save)
            )
        }
    )

    pendingReminderDelete?.let { draft ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_confirm_title),
            message = notifyFmt.format(Date(draft.atMillis)),
            onConfirm = {
                val next = reminders.filter { it.localKey != draft.localKey }
                reminders = next
                errorReminderKeys = errorReminderKeys - draft.localKey
                if (next.isEmpty()) notify = false
                pendingReminderDelete = null
            },
            onDismiss = { pendingReminderDelete = null }
        )
    }

    pendingBatchMismatchSave?.let { doSave ->
        ConfirmSaveDialog(
            title = stringResource(R.string.complete_confirm_title),
            message = stringResource(R.string.batch_transport_mismatch),
            onConfirm = {
                pendingBatchMismatchSave = null
                doSave()
            },
            onDismiss = { pendingBatchMismatchSave = null }
        )
    }

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
                scope.launch {
                    if (repo.addDictionary(type, value)) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.dict_added, value.trim()),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
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
        properties = DialogProperties(dismissOnClickOutside = false),
        containerColor = MaterialTheme.colorScheme.background,
        title = { CenteredDialogTitle(stringResource(R.string.checklist_prep)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ChecklistStatusBanner(completed = completed, total = total)

                if (items.isNotEmpty()) {
                    val listScroll = rememberScrollState()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 8.dp)
                                .verticalScroll(listScroll),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items.forEach { item ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    FishyPulseCheckbox(
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
                        ColumnScrollIndicator(
                            scrollState = listScroll,
                            modifier = Modifier.align(Alignment.CenterEnd)
                        )
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
                    Column(
                        modifier = Modifier.animateContentSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        checklist.forEach { task ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                FishyPulseCheckbox(
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
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
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
