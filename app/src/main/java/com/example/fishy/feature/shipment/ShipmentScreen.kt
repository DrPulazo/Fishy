package com.example.fishy.feature.shipment

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.graphics.SolidColor
import com.example.fishy.ui.theme.FishyCornerRadius
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import com.example.fishy.ui.components.FishyButton
import com.example.fishy.ui.components.DraggableAddPalletFab
import com.example.fishy.ui.components.FishyOutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fishy.FishyApp
import com.example.fishy.R
import com.example.fishy.domain.calc.BatchStatus
import com.example.fishy.domain.calc.ShipmentCalculator
import com.example.fishy.domain.calc.ShipmentTotals
import com.example.fishy.domain.format.QuantityFormatters
import com.example.fishy.domain.model.BatchLimit
import com.example.fishy.domain.model.DictionaryType
import com.example.fishy.domain.model.Product
import com.example.fishy.domain.model.ShipmentMode
import com.example.fishy.domain.model.ShipmentPayload
import com.example.fishy.ui.ErrorFeedback
import com.example.fishy.ui.components.AccordionCard
import com.example.fishy.ui.components.BatchControlPanel
import com.example.fishy.ui.components.BatchEntryDialog
import com.example.fishy.ui.components.ConfirmDeleteDialog
import com.example.fishy.ui.components.ConfirmSaveDialog
import com.example.fishy.ui.components.CenteredDialogMessage
import com.example.fishy.ui.components.CenteredDialogTitle
import com.example.fishy.ui.components.DecimalNumberField
import com.example.fishy.ui.components.DialogCancelConfirmActions
import com.example.fishy.ui.components.DialogCenteredAction
import com.example.fishy.ui.components.DialogCenteredFishyButton
import com.example.fishy.ui.components.DictionaryAutocomplete
import com.example.fishy.ui.components.FillProgressBar
import com.example.fishy.ui.components.FishySentenceKeyboardOptions
import com.example.fishy.ui.components.ProductWeightQuantityFields
import com.example.fishy.ui.components.PalletRow
import com.example.fishy.ui.components.PalletTableHeader
import com.example.fishy.ui.components.TransportFields
import com.example.fishy.ui.components.UnloadReceptionFields
import com.example.fishy.ui.components.fishyCheckboxColors
import com.example.fishy.ui.components.fishySwitchColors
import com.example.fishy.ui.components.transportTitle
import com.example.fishy.ui.components.unloadReceptionTitle
import com.example.fishy.ui.theme.Error
import com.example.fishy.ui.theme.FishyAccent
import com.example.fishy.ui.theme.PlaceholderGrey
import com.example.fishy.ui.theme.ProgressGreen
import com.example.fishy.ui.theme.Success
import com.example.fishy.ui.theme.Warning
import com.example.fishy.ui.theme.isLightTheme

private enum class CompletePlacesMismatch {
    None, Over, Under
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ShipmentScreen(
    mode: ShipmentMode?,
    draftId: Long?,
    scheduledId: Long?,
    onBack: () -> Unit,
    onOpenHistory: (String) -> Unit,
    onShipmentCompleted: (Long) -> Unit,
    vm: ShipmentViewModel = viewModel()
) {
    val payload by vm.payload.collectAsState()
    val settings by vm.settings.collectAsState()
    val quickPlacesText by vm.quickPlacesText.collectAsState()
    val sessionKey by vm.sessionKey.collectAsState()
    val customers by vm.customers.collectAsState()
    val ports by vm.ports.collectAsState()
    val vessels by vm.vessels.collectAsState()
    val productsDict by vm.productsDict.collectAsState()
    val manufacturers by vm.manufacturers.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val leaveScreen: () -> Unit = {
        scope.launch {
            vm.flushDraftAndAwait()
            onBack()
        }
    }

    BackHandler(onBack = leaveScreen)

    var showChecklist by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var batchPanelExpanded by remember { mutableStateOf(true) }
    var batchEditor by remember { mutableStateOf<BatchLimit?>(null) }
    var showCompleteConfirm by remember { mutableStateOf(false) }
    var showIncompleteChecklistConfirm by remember { mutableStateOf(false) }
    var completePlacesMismatch by remember { mutableStateOf(CompletePlacesMismatch.None) }
    var guardDialog by remember { mutableStateOf<ShipmentUiEvent.GuardConfirm?>(null) }
    var pendingDelete by remember { mutableStateOf<PendingDelete?>(null) }
    var forecastExpectationMsg by remember { mutableStateOf<String?>(null) }
    val forecastRunningMsg = stringResource(R.string.forecast_running)
    var focusPalletTarget by remember {
        mutableStateOf<ShipmentUiEvent.FocusPalletPlaces?>(null)
    }

    LaunchedEffect(mode, draftId, scheduledId) {
        vm.ensureInitialized(mode, draftId, scheduledId)
    }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is ShipmentUiEvent.Toast -> {
                    if (event.isError) ErrorFeedback.vibrate(context)
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is ShipmentUiEvent.GuardConfirm -> {
                    ErrorFeedback.vibrate(context)
                    guardDialog = event
                }
                is ShipmentUiEvent.NavigateArchiveDetail -> onShipmentCompleted(event.id)
                ShipmentUiEvent.Saved -> Unit
                is ShipmentUiEvent.FocusPalletPlaces -> focusPalletTarget = event
                is ShipmentUiEvent.ForecastRunning -> {
                    if (event.running) {
                        Toast.makeText(context, forecastRunningMsg, Toast.LENGTH_SHORT).show()
                    }
                }
                is ShipmentUiEvent.ForecastExpectation -> {
                    ErrorFeedback.vibrate(context)
                    forecastExpectationMsg = event.message
                }
            }
        }
    }

    val progress = ShipmentCalculator.progressPercent(payload)
    val resumeDraft = draftId != null
    val activeProductId = payload.lastUsedProductId ?: payload.products.lastOrNull()?.id
    val activeVehicleId = payload.lastUsedVehicleId ?: payload.multiVehicles.lastOrNull()?.id
    val activePortId = payload.lastUsedPortId ?: payload.multiPorts.lastOrNull()?.id
    val activeReceptionId = payload.lastUsedUnloadReceptionId ?: payload.unloadReceptions.lastOrNull()?.id
    val batchStatuses = ShipmentCalculator.batchStatuses(payload, payload.batchWarnThreshold)
    val checklistIconColor = when {
        payload.checklist.isEmpty() -> PlaceholderGrey
        payload.checklist.none { it.isCompleted } -> Error
        payload.checklist.all { it.isCompleted } -> Success
        else -> Warning
    }

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { },
                    navigationIcon = {
                        IconButton(onClick = leaveScreen) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        IconButton(onClick = { showChecklist = true }) {
                            Icon(
                                Icons.Default.CheckBox,
                                contentDescription = stringResource(R.string.checklist),
                                tint = checklistIconColor
                            )
                        }
                        IconButton(onClick = {
                            vm.flushDraft()
                            onOpenHistory(sessionKey)
                        }) {
                            Icon(Icons.Default.History, contentDescription = stringResource(R.string.history))
                        }
                        IconButton(onClick = { vm.saveDraftManual() }) {
                            Icon(Icons.Default.Save, contentDescription = stringResource(R.string.save))
                        }
                        Box {
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
                                SettingsMenuSwitchRow(
                                    label = stringResource(R.string.double_control),
                                    checked = payload.doubleControlEnabled,
                                    onCheckedChange = vm::setDoubleControl
                                )
                                SettingsMenuSwitchRow(
                                    label = stringResource(R.string.pallet_forecast),
                                    checked = payload.palletForecastEnabled,
                                    onCheckedChange = vm::setPalletForecast
                                )
                                SettingsMenuSwitchRow(
                                    label = stringResource(R.string.batch_control),
                                    checked = payload.batchControlEnabled,
                                    onCheckedChange = { enabled ->
                                        vm.setBatchControl(enabled)
                                        if (enabled) {
                                            batchPanelExpanded = true
                                        }
                                    }
                                )
                                SettingsMenuSwitchRow(
                                    label = stringResource(R.string.gross_weight),
                                    checked = payload.grossWeightEnabled,
                                    onCheckedChange = vm::setGrossWeightEnabled
                                )
                                SettingsMenuSwitchRow(
                                    label = stringResource(R.string.floating_fab),
                                    checked = settings.floatingFabEnabled,
                                    onCheckedChange = { enabled ->
                                        scope.launch {
                                            FishyApp.instance.settingsRepository.update {
                                                it.copy(floatingFabEnabled = enabled)
                                            }
                                        }
                                    }
                                )
                                SettingsMenuSwitchRow(
                                    label = stringResource(R.string.simplified_counter),
                                    checked = settings.simplifiedCounterEnabled,
                                    onCheckedChange = { enabled ->
                                        scope.launch {
                                            FishyApp.instance.settingsRepository.update {
                                                it.copy(simplifiedCounterEnabled = enabled)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                )
                FillProgressBar(progress = progress)
                if (payload.batchControlEnabled) {
                    BatchControlPanel(
                        payload = payload,
                        batchStatuses = batchStatuses,
                        expanded = batchPanelExpanded,
                        onExpandedChange = { batchPanelExpanded = it },
                        onEnterBatches = { batchEditor = BatchLimit() },
                        onEditBatch = { batchEditor = it },
                        onDeleteBatch = { vm.deleteBatchLimit(it.id) }
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
            item {
                AccordionCard(
                    title = stringResource(R.string.info_loading),
                    initiallyExpanded = !resumeDraft
                ) {
                    DictionaryAutocomplete(
                        label = stringResource(R.string.customer),
                        value = payload.customer,
                        suggestions = customers,
                        onValueChange = vm::setCustomer,
                        dictionaryType = DictionaryType.CUSTOMER,
                        onAddToDictionary = vm::addToDictionary
                    )
                    when (payload.mode) {
                        ShipmentMode.MONO -> {
                            DictionaryAutocomplete(
                                label = stringResource(R.string.port),
                                value = payload.port,
                                suggestions = ports,
                                onValueChange = vm::setPort,
                                dictionaryType = DictionaryType.PORT,
                                onAddToDictionary = vm::addToDictionary
                            )
                            DictionaryAutocomplete(
                                label = stringResource(R.string.vessel),
                                value = payload.vessel,
                                suggestions = vessels,
                                onValueChange = vm::setVessel,
                                dictionaryType = DictionaryType.VESSEL,
                                onAddToDictionary = vm::addToDictionary
                            )
                        }
                        ShipmentMode.MULTI_VEHICLE -> {
                            DictionaryAutocomplete(
                                label = stringResource(R.string.port),
                                value = payload.port,
                                suggestions = ports,
                                onValueChange = vm::setPort,
                                dictionaryType = DictionaryType.PORT,
                                onAddToDictionary = vm::addToDictionary
                            )
                            DictionaryAutocomplete(
                                label = stringResource(R.string.vessel),
                                value = payload.vessel,
                                suggestions = vessels,
                                onValueChange = vm::setVessel,
                                dictionaryType = DictionaryType.VESSEL,
                                onAddToDictionary = vm::addToDictionary
                            )
                        }
                        ShipmentMode.MULTI_PORT, ShipmentMode.UNLOAD -> Unit
                    }
                }
            }

            if (payload.mode == ShipmentMode.MONO || payload.mode == ShipmentMode.MULTI_PORT) {
                item {
                    AccordionCard(
                        title = stringResource(R.string.transport_section),
                        initiallyExpanded = !resumeDraft
                    ) {
                        TransportFields(
                            transport = payload.transport,
                            onChange = { t -> vm.updateTransport { t } },
                            autoSpaceContainers = settings.effectiveAutoSpaceContainers,
                            autoSpaceVehicles = settings.effectiveAutoSpaceVehicles
                        )
                    }
                }
            }

            when (payload.mode) {
                ShipmentMode.MONO -> {
                    items(payload.products, key = { it.id }) { product ->
                        ProductCard(
                            product = product,
                            initiallyExpanded = sectionExpanded(resumeDraft, activeProductId, product.id),
                            forceExpandToken = focusPalletTarget
                                ?.takeIf { it.productId == product.id },
                            focusPalletId = focusPalletTarget
                                ?.takeIf { it.productId == product.id }
                                ?.palletId,
                            onFocusHandled = { focusPalletTarget = null },
                            doubleControl = payload.doubleControlEnabled,
                            productsDict = productsDict,
                            manufacturers = manufacturers,
                            onUpdate = { transform: (Product) -> Product ->
                                vm.updateProduct(product.id, transform)
                            },
                            onAddPallet = { vm.addPallet(product.id) },
                            onPlaces = { pid, places -> vm.updatePalletPlaces(product.id, pid, places) },
                            onToggleImport = { pid -> vm.togglePalletImported(product.id, pid) },
                            onDeletePallet = { pid -> vm.deletePallet(product.id, pid) },
                            onDeleteProduct = {
                                pendingDelete = PendingDelete(
                                    title = context.getString(R.string.delete_product_title),
                                    message = context.getString(R.string.delete_product_msg, product.name.ifBlank { context.getString(R.string.new_product) })
                                ) { vm.deleteProduct(product.id) }
                            },
                            onWeightGuard = { w, apply -> vm.checkPackageWeight(w, apply) },
                            onAddToDictionary = vm::addToDictionary,
                            unload = false,
                            thousandsSeparator = settings.effectiveThousandsSeparator,
                            batchMismatch = ShipmentCalculator.isUnknownBatch(product, payload),
                            grossWeightEnabled = payload.grossWeightEnabled,
                            simplifiedCounterEnabled = settings.simplifiedCounterEnabled,
                            quickPlacesText = quickPlacesText,
                            onQuickPlacesChange = vm::setQuickPlacesText
                        )
                    }
                    item {
                        FishyButton(onClick = { vm.addProduct() }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.add_product))
                        }
                    }
                }
                ShipmentMode.MULTI_VEHICLE -> {
                    items(payload.multiVehicles, key = { it.id }) { vehicle ->
                        val dc = payload.doubleControlEnabled || vehicle.doubleControlEnabled
                        val vTotals = ShipmentCalculator.totalsForProducts(vehicle.products, dc)
                        val done = kotlin.math.abs(vTotals.remainder) < 1e-9 && vehicle.products.any { it.quantity > 0 }
                        val focusInVehicle = focusPalletTarget?.let { t ->
                            vehicle.products.any { it.id == t.productId }
                        } == true
                        AccordionCard(
                            title = transportTitle(
                                vehicle.transport,
                                settings.effectiveAutoSpaceContainers,
                                settings.effectiveAutoSpaceVehicles
                            ),
                            titleColor = if (done) Success else MaterialTheme.colorScheme.onSurface,
                            initiallyExpanded = sectionExpanded(resumeDraft, activeVehicleId, vehicle.id),
                            forceExpandToken = focusPalletTarget?.takeIf { focusInVehicle },
                            trailing = {
                                IconButton(onClick = {
                                    pendingDelete = PendingDelete(
                                        title = context.getString(R.string.delete_vehicle_title),
                                        message = context.getString(R.string.delete_vehicle_msg)
                                    ) { vm.deleteVehicle(vehicle.id) }
                                }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.delete),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        ) {
                            AccordionCard(
                                title = stringResource(R.string.transport_section),
                                initiallyExpanded = !resumeDraft || vehicle.id == activeVehicleId
                            ) {
                                TransportFields(
                                    transport = vehicle.transport,
                                    onChange = { t ->
                                        vm.updateVehicle(vehicle.id) { vg -> vg.copy(transport = t) }
                                    },
                                    autoSpaceContainers = settings.effectiveAutoSpaceContainers,
                                    autoSpaceVehicles = settings.effectiveAutoSpaceVehicles
                                )
                            }
                            AccordionCard(
                                title = stringResource(R.string.products_section),
                                initiallyExpanded = !resumeDraft || vehicle.id == activeVehicleId,
                                forceExpandToken = focusPalletTarget?.takeIf { focusInVehicle }
                            ) {
                                vehicle.products.forEach { product ->
                                    ProductCard(
                                        product = product,
                                        initiallyExpanded = sectionExpanded(
                                            resumeDraft && vehicle.id == activeVehicleId,
                                            payload.lastUsedProductId ?: vehicle.products.lastOrNull()?.id,
                                            product.id
                                        ),
                                        forceExpandToken = focusPalletTarget
                                            ?.takeIf { it.productId == product.id },
                                        focusPalletId = focusPalletTarget
                                            ?.takeIf { it.productId == product.id }
                                            ?.palletId,
                                        onFocusHandled = { focusPalletTarget = null },
                                        doubleControl = dc,
                                        productsDict = productsDict,
                                        manufacturers = manufacturers,
                                        onUpdate = { transform ->
                                            vm.updateProduct(product.id, transform)
                                        },
                                        onAddPallet = { vm.addPallet(product.id) },
                                        onPlaces = { pid, places ->
                                            vm.updatePalletPlaces(product.id, pid, places)
                                        },
                                        onToggleImport = { pid ->
                                            vm.togglePalletImported(product.id, pid)
                                        },
                                        onDeletePallet = { pid -> vm.deletePallet(product.id, pid) },
                                        onDeleteProduct = {
                                            pendingDelete = PendingDelete(
                                                title = context.getString(R.string.delete_product_title),
                                                message = context.getString(R.string.delete_product_msg, product.name.ifBlank { context.getString(R.string.new_product) })
                                            ) {
                                                vm.updateVehicle(vehicle.id) { vg ->
                                                    vg.copy(products = vg.products.filter { it.id != product.id })
                                                }
                                            }
                                        },
                                        onWeightGuard = { w, apply -> vm.checkPackageWeight(w, apply) },
                                        onAddToDictionary = vm::addToDictionary,
                                        unload = false,
                                        thousandsSeparator = settings.effectiveThousandsSeparator,
                                        batchMismatch = ShipmentCalculator.isUnknownBatch(product, payload),
                                        grossWeightEnabled = payload.grossWeightEnabled,
                                        simplifiedCounterEnabled = settings.simplifiedCounterEnabled,
                                        quickPlacesText = quickPlacesText,
                                        onQuickPlacesChange = vm::setQuickPlacesText
                                    )
                                }
                                FishyButton(
                                    onClick = { vm.addVehicleProduct(vehicle.id) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.add_product))
                                }
                            }
                            AccordionCard(
                                title = stringResource(R.string.totals_by_transport),
                                initiallyExpanded = !resumeDraft || vehicle.id == activeVehicleId
                            ) {
                                TotalsBlock(
                                    totals = vTotals,
                                    thousandsSeparator = settings.effectiveThousandsSeparator,
                                    grossWeightEnabled = payload.grossWeightEnabled
                                )
                            }
                        }
                    }
                    item {
                        FishyButton(onClick = { vm.addVehicle() }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.add_vehicle))
                        }
                    }
                }
                ShipmentMode.MULTI_PORT -> {
                    items(payload.multiPorts, key = { it.id }) { group ->
                        val dc = payload.doubleControlEnabled || group.doubleControlEnabled
                        val gTotals = ShipmentCalculator.totalsForProducts(group.products, dc)
                        val done = kotlin.math.abs(gTotals.remainder) < 1e-9 && group.products.any { it.quantity > 0 }
                        val portTitle = if (group.port.isBlank()) stringResource(R.string.new_port) else stringResource(R.string.port_title, group.port)
                        val portSubtitle = if (group.products.isNotEmpty()) {
                            stringResource(
                                R.string.port_products_summary,
                                group.products.size,
                                QuantityFormatters.formatCount(
                                    gTotals.places,
                                    settings.effectiveThousandsSeparator
                                )
                            )
                        } else null
                        val focusInPort = focusPalletTarget?.let { t ->
                            group.products.any { it.id == t.productId }
                        } == true
                        AccordionCard(
                            title = portTitle,
                            subtitle = portSubtitle,
                            titleColor = if (done) Success else MaterialTheme.colorScheme.onSurface,
                            initiallyExpanded = sectionExpanded(resumeDraft, activePortId, group.id),
                            forceExpandToken = focusPalletTarget?.takeIf { focusInPort },
                            trailing = {
                                IconButton(onClick = {
                                    pendingDelete = PendingDelete(
                                        title = context.getString(R.string.delete_port_title),
                                        message = context.getString(R.string.delete_port_msg)
                                    ) { vm.deletePortGroup(group.id) }
                                }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.delete),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        ) {
                            DictionaryAutocomplete(
                                label = stringResource(R.string.port),
                                value = group.port,
                                suggestions = ports,
                                onValueChange = { v ->
                                    vm.updatePortGroup(group.id) { it.copy(port = v) }
                                },
                                dictionaryType = DictionaryType.PORT,
                                onAddToDictionary = vm::addToDictionary
                            )
                            DictionaryAutocomplete(
                                label = stringResource(R.string.vessel),
                                value = group.vessel,
                                suggestions = vessels,
                                onValueChange = { v ->
                                    vm.updatePortGroup(group.id) { it.copy(vessel = v) }
                                },
                                dictionaryType = DictionaryType.VESSEL,
                                onAddToDictionary = vm::addToDictionary
                            )
                            AccordionCard(
                                title = stringResource(R.string.products_section),
                                initiallyExpanded = !resumeDraft || group.id == activePortId,
                                forceExpandToken = focusPalletTarget?.takeIf { focusInPort }
                            ) {
                                group.products.forEach { product ->
                                    ProductCard(
                                        product = product,
                                        initiallyExpanded = sectionExpanded(
                                            resumeDraft && group.id == activePortId,
                                            payload.lastUsedProductId ?: group.products.lastOrNull()?.id,
                                            product.id
                                        ),
                                        forceExpandToken = focusPalletTarget
                                            ?.takeIf { it.productId == product.id },
                                        focusPalletId = focusPalletTarget
                                            ?.takeIf { it.productId == product.id }
                                            ?.palletId,
                                        onFocusHandled = { focusPalletTarget = null },
                                        doubleControl = dc,
                                        productsDict = productsDict,
                                        manufacturers = manufacturers,
                                        onUpdate = { transform ->
                                            vm.updateProduct(product.id, transform)
                                        },
                                        onAddPallet = { vm.addPallet(product.id) },
                                        onPlaces = { pid, places ->
                                            vm.updatePalletPlaces(product.id, pid, places)
                                        },
                                        onToggleImport = { pid ->
                                            vm.togglePalletImported(product.id, pid)
                                        },
                                        onDeletePallet = { pid -> vm.deletePallet(product.id, pid) },
                                        onDeleteProduct = {
                                            pendingDelete = PendingDelete(
                                                title = context.getString(R.string.delete_product_title),
                                                message = context.getString(R.string.delete_product_msg, product.name.ifBlank { context.getString(R.string.new_product) })
                                            ) {
                                                vm.updatePortGroup(group.id) { g ->
                                                    g.copy(products = g.products.filter { it.id != product.id })
                                                }
                                            }
                                        },
                                        onWeightGuard = { w, apply -> vm.checkPackageWeight(w, apply) },
                                        onAddToDictionary = vm::addToDictionary,
                                        unload = false,
                                        thousandsSeparator = settings.effectiveThousandsSeparator,
                                        batchMismatch = ShipmentCalculator.isUnknownBatch(product, payload),
                                        grossWeightEnabled = payload.grossWeightEnabled,
                                        simplifiedCounterEnabled = settings.simplifiedCounterEnabled,
                                        quickPlacesText = quickPlacesText,
                                        onQuickPlacesChange = vm::setQuickPlacesText
                                    )
                                }
                                FishyButton(
                                    onClick = { vm.addPortProduct(group.id) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.add_product))
                                }
                            }
                            AccordionCard(
                                title = stringResource(R.string.totals_by_port),
                                initiallyExpanded = !resumeDraft || group.id == activePortId
                            ) {
                                TotalsBlock(
                                    totals = gTotals,
                                    thousandsSeparator = settings.effectiveThousandsSeparator,
                                    grossWeightEnabled = payload.grossWeightEnabled
                                )
                            }
                        }
                    }
                    item {
                        FishyButton(onClick = { vm.addPortGroup() }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.add_port))
                        }
                    }
                }
                ShipmentMode.UNLOAD -> {
                    items(payload.unloadReceptions, key = { it.id }) { reception ->
                        val products = reception.inbounds.flatMap { it.products }
                        val uTotals = ShipmentCalculator.totalsForProducts(
                            products,
                            doubleControl = false,
                            unload = true
                        )
                        val receptionTitle = unloadReceptionTitle(
                            reception.name,
                            reception.transport,
                            settings.effectiveAutoSpaceContainers,
                            settings.effectiveAutoSpaceVehicles
                        )
                        val focusInReception = focusPalletTarget?.let { t ->
                            reception.inbounds.any { ib -> ib.products.any { it.id == t.productId } }
                        } == true
                        AccordionCard(
                            title = receptionTitle,
                            initiallyExpanded = sectionExpanded(resumeDraft, activeReceptionId, reception.id),
                            forceExpandToken = focusPalletTarget?.takeIf { focusInReception },
                            trailing = {
                                IconButton(onClick = {
                                    pendingDelete = PendingDelete(
                                        title = context.getString(R.string.delete_reception_title),
                                        message = context.getString(R.string.delete_reception_msg)
                                    ) { vm.deleteUnloadReception(reception.id) }
                                }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.delete),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        ) {
                            AccordionCard(
                                title = stringResource(R.string.reception_point),
                                initiallyExpanded = !resumeDraft || reception.id == activeReceptionId
                            ) {
                                UnloadReceptionFields(
                                    warehouse = reception.name,
                                    transport = reception.transport,
                                    onWarehouseChange = { v ->
                                        vm.updateUnloadReception(reception.id) { r -> r.copy(name = v) }
                                    },
                                    onTransportChange = { t ->
                                        vm.updateUnloadReception(reception.id) { r -> r.copy(transport = t) }
                                    },
                                    ports = ports,
                                    onAddToDictionary = { type, value -> vm.addToDictionary(type, value) },
                                    autoSpaceContainers = settings.effectiveAutoSpaceContainers,
                                    autoSpaceVehicles = settings.effectiveAutoSpaceVehicles
                                )
                            }
                            reception.inbounds.forEach { inbound ->
                                val inboundTitle = transportTitle(
                                    inbound.transport,
                                    settings.effectiveAutoSpaceContainers,
                                    settings.effectiveAutoSpaceVehicles
                                ).let { t ->
                                    if (t != stringResource(R.string.new_transport)) t
                                    else stringResource(R.string.unload_source)
                                }
                                val focusInInbound = focusPalletTarget?.let { t ->
                                    inbound.products.any { it.id == t.productId }
                                } == true
                                AccordionCard(
                                    title = inboundTitle,
                                    initiallyExpanded = !resumeDraft || reception.id == activeReceptionId,
                                    forceExpandToken = focusPalletTarget?.takeIf { focusInInbound },
                                    trailing = {
                                        IconButton(onClick = {
                                            pendingDelete = PendingDelete(
                                                title = context.getString(R.string.delete_source_title),
                                                message = context.getString(R.string.delete_source_msg)
                                            ) { vm.deleteUnloadInbound(reception.id, inbound.id) }
                                        }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = stringResource(R.string.delete),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                ) {
                                    DictionaryAutocomplete(
                                        label = stringResource(R.string.port),
                                        value = inbound.port,
                                        suggestions = ports,
                                        onValueChange = { v ->
                                            vm.updateUnloadInbound(reception.id, inbound.id) { ib ->
                                                ib.copy(port = v)
                                            }
                                        },
                                        dictionaryType = DictionaryType.PORT,
                                        onAddToDictionary = vm::addToDictionary
                                    )
                                    DictionaryAutocomplete(
                                        label = stringResource(R.string.vessel),
                                        value = inbound.vessel,
                                        suggestions = vessels,
                                        onValueChange = { v ->
                                            vm.updateUnloadInbound(reception.id, inbound.id) { ib ->
                                                ib.copy(vessel = v)
                                            }
                                        },
                                        dictionaryType = DictionaryType.VESSEL,
                                        onAddToDictionary = vm::addToDictionary
                                    )
                                    TransportFields(
                                        transport = inbound.transport,
                                        onChange = { t ->
                                            vm.updateUnloadInbound(reception.id, inbound.id) { ib ->
                                                ib.copy(transport = t)
                                            }
                                        },
                                        autoSpaceContainers = settings.effectiveAutoSpaceContainers,
                                        autoSpaceVehicles = settings.effectiveAutoSpaceVehicles
                                    )
                                    inbound.products.forEach { product ->
                                        ProductCard(
                                            product = product,
                                            initiallyExpanded = sectionExpanded(
                                                resumeDraft && reception.id == activeReceptionId,
                                                payload.lastUsedProductId ?: inbound.products.lastOrNull()?.id,
                                                product.id
                                            ),
                                            forceExpandToken = focusPalletTarget
                                                ?.takeIf { it.productId == product.id },
                                            focusPalletId = focusPalletTarget
                                                ?.takeIf { it.productId == product.id }
                                                ?.palletId,
                                            onFocusHandled = { focusPalletTarget = null },
                                            doubleControl = false,
                                            productsDict = productsDict,
                                            manufacturers = manufacturers,
                                            onUpdate = { transform ->
                                                vm.updateProduct(product.id, transform)
                                            },
                                            onAddPallet = { vm.addPallet(product.id) },
                                            onPlaces = { pid, places ->
                                                vm.updatePalletPlaces(product.id, pid, places)
                                            },
                                            onToggleImport = {},
                                            onDeletePallet = { pid -> vm.deletePallet(product.id, pid) },
                                            onDeleteProduct = {
                                                pendingDelete = PendingDelete(
                                                    title = context.getString(R.string.delete_product_title),
                                                    message = context.getString(
                                                        R.string.delete_product_msg,
                                                        product.name.ifBlank { context.getString(R.string.new_product) }
                                                    )
                                                ) {
                                                    vm.updateUnloadInbound(reception.id, inbound.id) { ib ->
                                                        val next = ib.products.filter { it.id != product.id }
                                                        ib.copy(products = next.ifEmpty { listOf(Product()) })
                                                    }
                                                }
                                            },
                                            onWeightGuard = { w, apply -> vm.checkPackageWeight(w, apply) },
                                            onAddToDictionary = vm::addToDictionary,
                                            unload = true,
                                            thousandsSeparator = settings.effectiveThousandsSeparator,
                                            batchMismatch = ShipmentCalculator.isUnknownBatch(product, payload),
                                            grossWeightEnabled = payload.grossWeightEnabled,
                                            simplifiedCounterEnabled = settings.simplifiedCounterEnabled,
                                            quickPlacesText = quickPlacesText,
                                            onQuickPlacesChange = vm::setQuickPlacesText
                                        )
                                    }
                                    FishyButton(
                                        onClick = { vm.addUnloadProduct(reception.id, inbound.id) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(stringResource(R.string.add_product))
                                    }
                                    val inboundTotals = ShipmentCalculator.totalsForProducts(
                                        inbound.products,
                                        doubleControl = false,
                                        unload = true
                                    )
                                    AccordionCard(
                                        title = stringResource(R.string.totals_by_transport),
                                        initiallyExpanded = !resumeDraft || reception.id == activeReceptionId
                                    ) {
                                        TotalsBlock(
                                            totals = inboundTotals,
                                            thousandsSeparator = settings.effectiveThousandsSeparator,
                                            grossWeightEnabled = payload.grossWeightEnabled,
                                            unload = true
                                        )
                                    }
                                }
                            }
                            FishyButton(
                                onClick = { vm.addUnloadInbound(reception.id) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.add_source))
                            }
                            AccordionCard(
                                title = stringResource(R.string.totals_section),
                                initiallyExpanded = !resumeDraft || reception.id == activeReceptionId
                            ) {
                                TotalsBlock(
                                    totals = uTotals,
                                    thousandsSeparator = settings.effectiveThousandsSeparator,
                                    grossWeightEnabled = payload.grossWeightEnabled,
                                    unload = true
                                )
                            }
                        }
                    }
                    item {
                        FishyButton(
                            onClick = { vm.addUnloadReception() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.add_reception_point))
                        }
                    }
                }
            }

            item {
                val totals = ShipmentCalculator.totals(payload)
                AccordionCard(
                    title = stringResource(R.string.totals_overall),
                    initiallyExpanded = !resumeDraft
                ) {
                    TotalsBlock(
                        totals = totals,
                        thousandsSeparator = settings.effectiveThousandsSeparator,
                        grossWeightEnabled = payload.grossWeightEnabled,
                        unload = payload.mode == ShipmentMode.UNLOAD
                    )
                }
            }

            item {
                AccordionCard(
                    title = stringResource(R.string.notes_section),
                    initiallyExpanded = !resumeDraft
                ) {
                    OutlinedTextField(
                        value = payload.notes,
                        onValueChange = vm::setNotes,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 8,
                        keyboardOptions = FishySentenceKeyboardOptions
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FishyOutlinedButton(
                        onClick = { vm.saveDraftManual() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.save_draft))
                    }
                    FishyButton(
                        onClick = {
                            if (payload.checklist.any { !it.isCompleted }) {
                                ErrorFeedback.vibrate(context)
                                showIncompleteChecklistConfirm = true
                            } else {
                                val totals = ShipmentCalculator.totals(payload)
                                completePlacesMismatch = when {
                                    totals.places > totals.quantity -> {
                                        ErrorFeedback.vibrate(context)
                                        CompletePlacesMismatch.Over
                                    }
                                    totals.places < totals.quantity -> {
                                        ErrorFeedback.vibrate(context)
                                        CompletePlacesMismatch.Under
                                    }
                                    else -> CompletePlacesMismatch.None
                                }
                                showCompleteConfirm = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.save_shipment))
                    }
                }
            }
            }

            if (settings.floatingFabEnabled) {
                DraggableAddPalletFab(onClick = { vm.smartAddPallet() })
            }
        }
    }

    if (showChecklist) {
        ChecklistDialog(vm = vm, onDismiss = { showChecklist = false })
    }

    batchEditor?.let { editing ->
        BatchEntryDialog(
            initial = editing,
            productsDict = productsDict,
            manufacturers = manufacturers,
            onDismiss = { batchEditor = null },
            onSave = { limit ->
                vm.upsertBatchLimit(limit)
                batchEditor = null
            },
            onAddToDictionary = { type, value -> vm.addToDictionary(type, value) },
            isNew = payload.batchLimits.none { it.id == editing.id }
        )
    }

    if (showIncompleteChecklistConfirm) {
        ConfirmSaveDialog(
            title = stringResource(R.string.checklist_incomplete_title),
            message = stringResource(R.string.checklist_incomplete_finish_msg),
            confirmText = stringResource(R.string.action_finish),
            onConfirm = {
                showIncompleteChecklistConfirm = false
                val totals = ShipmentCalculator.totals(payload)
                completePlacesMismatch = when {
                    totals.places > totals.quantity -> {
                        ErrorFeedback.vibrate(context)
                        CompletePlacesMismatch.Over
                    }
                    totals.places < totals.quantity -> {
                        ErrorFeedback.vibrate(context)
                        CompletePlacesMismatch.Under
                    }
                    else -> CompletePlacesMismatch.None
                }
                showCompleteConfirm = true
            },
            onDismiss = {
                showIncompleteChecklistConfirm = false
                showChecklist = true
            }
        )
    }

    if (showCompleteConfirm) {
        val messageRes = when (completePlacesMismatch) {
            CompletePlacesMismatch.Over -> R.string.complete_confirm_places_over
            CompletePlacesMismatch.Under -> R.string.complete_confirm_places_under
            CompletePlacesMismatch.None -> R.string.complete_confirm_msg
        }
        ConfirmSaveDialog(
            title = stringResource(R.string.complete_confirm_title),
            message = stringResource(messageRes),
            onConfirm = {
                showCompleteConfirm = false
                vm.complete()
            },
            onDismiss = { showCompleteConfirm = false }
        )
    }

    forecastExpectationMsg?.let { msg ->
        val messageColor = if (isLightTheme()) {
            MaterialTheme.colorScheme.onSurface
        } else {
            Color.White
        }
        val okColors = if (isLightTheme()) {
            ButtonDefaults.buttonColors(
                containerColor = FishyAccent,
                contentColor = Color.White
            )
        } else {
            ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color(0xFF2C2C2C)
            )
        }
        AlertDialog(
            onDismissRequest = { forecastExpectationMsg = null },
            containerColor = MaterialTheme.colorScheme.background,
            text = {
                Text(
                    text = msg,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    color = messageColor
                )
            },
            confirmButton = {
                DialogCenteredAction {
                    FishyButton(
                        onClick = { forecastExpectationMsg = null },
                        colors = okColors
                    ) {
                        Text("OK")
                    }
                }
            }
        )
    }

    guardDialog?.let { g ->
        AlertDialog(
            onDismissRequest = { guardDialog = null },
            containerColor = MaterialTheme.colorScheme.background,
            title = { CenteredDialogTitle(stringResource(R.string.guard_confirm)) },
            text = {
                val fieldLabel = when (g.field) {
                    "weight" -> stringResource(R.string.guard_field_tare)
                    "places" -> stringResource(R.string.guard_field_places)
                    "quantity" -> stringResource(R.string.guard_field_quantity)
                    else -> g.field
                }
                CenteredDialogMessage("$fieldLabel = ${g.value}")
            },
            confirmButton = {
                DialogCancelConfirmActions(
                    onCancel = { guardDialog = null },
                    onConfirm = {
                        g.onConfirm()
                        guardDialog = null
                    },
                    confirmText = stringResource(R.string.confirm),
                    cancelText = stringResource(R.string.no)
                )
            }
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
}

private data class PendingDelete(
    val title: String,
    val message: String,
    val onConfirm: () -> Unit
)

@Composable
private fun TotalsBlock(
    totals: ShipmentTotals,
    thousandsSeparator: Boolean = false,
    grossWeightEnabled: Boolean = false,
    unload: Boolean = false
) {
    val placesFmt = QuantityFormatters.formatCount(totals.places, thousandsSeparator)
    val palletsFmt = QuantityFormatters.formatInteger(totals.pallets, thousandsSeparator)
    val qtyFmt = QuantityFormatters.formatInteger(totals.quantity, thousandsSeparator)
    val typesFmt = QuantityFormatters.formatInteger(totals.productTypes, thousandsSeparator)
    TotalsRow(stringResource(R.string.product_types), typesFmt)
    TotalsRow(
        stringResource(if (unload) R.string.unloaded_pallets else R.string.loaded_pallets),
        palletsFmt
    )
    TotalsRow(
        stringResource(if (unload) R.string.unloaded_places else R.string.loaded_places),
        placesFmt
    )
    TotalsRow(stringResource(R.string.target_qty), qtyFmt)
    TotalsRow(
        stringResource(R.string.target_mass_label),
        stringResource(
            R.string.total_mass_value,
            QuantityFormatters.formatWeight(totals.targetWeight, thousandsSeparator)
        )
    )
    TotalsRow(
        stringResource(R.string.total_mass_label),
        stringResource(
            R.string.total_mass_value,
            QuantityFormatters.formatWeight(totals.actualWeight, thousandsSeparator)
        )
    )
    if (grossWeightEnabled) {
        TotalsRow(
            stringResource(R.string.target_gross_mass_label),
            stringResource(
                R.string.total_mass_value,
                QuantityFormatters.formatWeight(totals.targetGrossWeight, thousandsSeparator)
            )
        )
        TotalsRow(
            stringResource(R.string.total_gross_mass_label),
            stringResource(
                R.string.total_mass_value,
                QuantityFormatters.formatWeight(totals.actualGrossWeight, thousandsSeparator)
            )
        )
    }
    when {
        totals.remainder > 0 -> Text(
            stringResource(R.string.underload, ShipmentCalculator.formatPlacesRu(totals.remainder, thousandsSeparator)),
            color = Error,
            fontWeight = FontWeight.Bold
        )
        totals.remainder < 0 -> Text(
            stringResource(R.string.overload, ShipmentCalculator.formatPlacesRu(-totals.remainder, thousandsSeparator)),
            color = Warning,
            fontWeight = FontWeight.Bold
        )
        else -> Text(
            stringResource(if (unload) R.string.unloading_done else R.string.loading_done),
            color = Success,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TotalsRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SettingsMenuSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    DropdownMenuItem(
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(label, modifier = Modifier.weight(1f).padding(end = 12.dp))
                Switch(checked = checked, onCheckedChange = onCheckedChange, colors = fishySwitchColors())
            }
        },
        onClick = { onCheckedChange(!checked) }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProductCard(
    product: Product,
    initiallyExpanded: Boolean = true,
    forceExpandToken: Any? = null,
    focusPalletId: Long? = null,
    onFocusHandled: () -> Unit = {},
    doubleControl: Boolean,
    productsDict: List<com.example.fishy.data.local.entity.DictionaryEntity>,
    manufacturers: List<com.example.fishy.data.local.entity.DictionaryEntity>,
    onUpdate: ((Product) -> Product) -> Unit,
    onAddPallet: () -> Unit,
    onPlaces: (Long, Double) -> Unit,
    onToggleImport: (Long) -> Unit,
    onDeletePallet: (Long) -> Unit,
    onDeleteProduct: () -> Unit,
    onWeightGuard: (Double, (Double) -> Unit) -> Unit,
    onAddToDictionary: (DictionaryType, String) -> Unit,
    unload: Boolean,
    thousandsSeparator: Boolean = false,
    batchMismatch: Boolean = false,
    grossWeightEnabled: Boolean = false,
    simplifiedCounterEnabled: Boolean = false,
    quickPlacesText: String = "",
    onQuickPlacesChange: (String) -> Unit = {}
) {
    val rem = ShipmentCalculator.remainder(product, doubleControl, unload)
    val title = productAccordionTitle(product, stringResource(R.string.new_product))
    val places = ShipmentCalculator.placesForProduct(product, doubleControl)
    val placesFmt = QuantityFormatters.formatCount(places, thousandsSeparator)
    val qtyFmt = QuantityFormatters.formatInteger(product.quantity, thousandsSeparator)
    val subtitle = if (product.quantity > 0) {
        val progress = "($placesFmt/$qtyFmt)"
        when {
            rem > 0 -> "${stringResource(R.string.product_remainder_short, ShipmentCalculator.formatPlacesRu(rem, thousandsSeparator))} $progress"
            rem < 0 -> "${stringResource(R.string.product_overload_short, ShipmentCalculator.formatPlacesRu(-rem, thousandsSeparator))} $progress"
            else -> "${stringResource(R.string.loading_done)} $progress"
        }
    } else null
    val titleColor = if (kotlin.math.abs(rem) < 1e-9 && product.quantity > 0) Success else MaterialTheme.colorScheme.onSurface
    val subtitleColor = when {
        rem > 0 -> MaterialTheme.colorScheme.onSurfaceVariant
        rem < 0 -> Warning
        product.quantity > 0 -> Success
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusColor = when {
        rem > 0 -> Error
        rem < 0 -> Warning
        else -> Success
    }

    AccordionCard(
        title = title,
        subtitle = subtitle,
        titleColor = titleColor,
        subtitleColor = subtitleColor,
        initiallyExpanded = initiallyExpanded,
        forceExpandToken = forceExpandToken,
        trailing = {
            IconButton(onClick = onDeleteProduct) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    ) {
        DictionaryAutocomplete(
            label = stringResource(R.string.product),
            value = product.name,
            suggestions = productsDict,
            onValueChange = { v: String -> onUpdate { p: Product -> p.copy(name = v) } },
            dictionaryType = DictionaryType.PRODUCT,
            onAddToDictionary = onAddToDictionary,
            isError = batchMismatch
        )
        OutlinedTextField(
            value = product.batch,
            onValueChange = { v: String ->
                onUpdate { p: Product -> p.copy(batch = v) }
            },
            label = { Text(stringResource(R.string.batch)) },
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = batchMismatch,
            keyboardOptions = FishySentenceKeyboardOptions
        )
        DictionaryAutocomplete(
            label = stringResource(R.string.manufacturer),
            value = product.manufacturer,
            suggestions = manufacturers,
            onValueChange = { v: String -> onUpdate { p: Product -> p.copy(manufacturer = v) } },
            dictionaryType = DictionaryType.MANUFACTURER,
            onAddToDictionary = onAddToDictionary,
            isError = batchMismatch
        )
        // Тара / Кол-во / Масса (или 3×2 при включённом брутто)
        ProductWeightQuantityFields(
            product = product,
            grossWeightEnabled = grossWeightEnabled,
            onPackageWeightChange = { weight ->
                onWeightGuard(weight) { confirmed ->
                    onUpdate { p -> p.copy(packageWeight = confirmed) }
                }
            },
            onQuantityChange = { qty -> onUpdate { p -> p.copy(quantity = qty) } },
            onCoefficientChange = { k -> onUpdate { p -> p.copy(grossCoefficient = k) } },
            thousandsSeparator = thousandsSeparator,
            tareError = batchMismatch
        )
        if (product.pallets.isNotEmpty()) {
            PalletTableHeader(doubleControl = doubleControl)
            product.pallets.forEach { pallet ->
                PalletRow(
                    pallet = pallet,
                    doubleControl = doubleControl,
                    onPlacesChange = { onPlaces(pallet.id, it) },
                    onToggleImported = { onToggleImport(pallet.id) },
                    onDelete = { onDeletePallet(pallet.id) },
                    requestFocus = focusPalletId == pallet.id,
                    onFocusHandled = onFocusHandled,
                    thousandsSeparator = thousandsSeparator
                )
            }
        }

        val footerBringIntoView = remember { BringIntoViewRequester() }
        val realPalletCount = product.pallets.count { !it.isPlaceholder }
        var prevRealPalletCount by remember { mutableStateOf(realPalletCount) }
        LaunchedEffect(realPalletCount) {
            if (realPalletCount > prevRealPalletCount && simplifiedCounterEnabled) {
                footerBringIntoView.bringIntoView()
            }
            prevRealPalletCount = realPalletCount
        }

        Column(modifier = Modifier.bringIntoViewRequester(footerBringIntoView)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(statusColor.copy(alpha = 0.12f))
                    .padding(8.dp)
            ) {
                val placesTotal = ShipmentCalculator.placesForProduct(product, doubleControl)
                Text(
                    stringResource(
                        R.string.places_progress,
                        QuantityFormatters.formatCount(placesTotal, thousandsSeparator),
                        QuantityFormatters.formatInteger(product.quantity, thousandsSeparator)
                    )
                )
                Text(
                    text = when {
                        rem > 0 -> stringResource(R.string.underload, ShipmentCalculator.formatPlacesRu(rem, thousandsSeparator))
                        rem < 0 -> stringResource(R.string.overload, ShipmentCalculator.formatPlacesRu(-rem, thousandsSeparator))
                        else -> stringResource(R.string.norm_ok)
                    },
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
                if (doubleControl) {
                    val real = ShipmentCalculator.realPallets(product)
                    val exportedCount = real.size
                    val importedPallets = real.filter { it.isImported }
                    val exportedPlaces = real.sumOf { it.places }
                    val importedPlaces = importedPallets.sumOf { it.places }
                    Text(
                        stringResource(
                            R.string.dc_exported_line,
                            ShipmentCalculator.formatPalletsRu(exportedCount),
                            ShipmentCalculator.formatPlacesRu(exportedPlaces, thousandsSeparator)
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        stringResource(
                            R.string.dc_imported_line,
                            ShipmentCalculator.formatPalletsRu(importedPallets.size),
                            ShipmentCalculator.formatPlacesRu(importedPlaces, thousandsSeparator)
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (simplifiedCounterEnabled) {
                val counterRowHeight = 56.dp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .requiredHeight(counterRowHeight)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(FishyCornerRadius)
                            )
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = quickPlacesText,
                            onValueChange = onQuickPlacesChange,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            textStyle = LocalTextStyle.current.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { inner ->
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (quickPlacesText.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.places_per_pallet_short),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    inner()
                                }
                            }
                        )
                    }
                    FishyButton(
                        onClick = onAddPallet,
                        modifier = Modifier
                            .weight(1f)
                            .requiredHeight(counterRowHeight)
                            .defaultMinSize(minHeight = 0.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.add_pallet),
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                    }
                }
            } else {
                FishyButton(onClick = onAddPallet, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.add_pallet))
                }
            }
        }
    }
}

@Composable
private fun ChecklistDialog(vm: ShipmentViewModel, onDismiss: () -> Unit) {
    val payload by vm.payload.collectAsState()
    var newTitle by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    val completed = payload.checklist.count { it.isCompleted }
    val total = payload.checklist.size
    val percent = if (total > 0) completed * 100 / total else 0
    val percentColor = when {
        total > 0 && completed == total -> Success
        completed > 0 -> Warning
        total > 0 -> Error
        else -> PlaceholderGrey
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        title = { CenteredDialogTitle(stringResource(R.string.checklist_shipment)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.checklist_done_progress, completed, total))
                        Text(
                            "$percent%",
                            color = percentColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (payload.checklist.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Checklist,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            stringResource(R.string.checklist_empty),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    payload.checklist.forEach { task ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = task.isCompleted,
                                onCheckedChange = { vm.toggleChecklist(task.id) },
                                colors = fishyCheckboxColors()
                            )
                            OutlinedTextField(
                                value = task.title,
                                onValueChange = { vm.editChecklistTask(task.id, it) },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = FishySentenceKeyboardOptions
                            )
                            IconButton(onClick = { vm.deleteChecklistTask(task.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = null)
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
                            vm.addChecklistTask(newTitle.trim())
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

private fun sectionExpanded(resumeDraft: Boolean, activeId: Long?, thisId: Long): Boolean =
    !resumeDraft || activeId == thisId
