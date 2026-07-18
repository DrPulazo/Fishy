package com.example.fishy.feature.shipment

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fishy.R
import com.example.fishy.domain.calc.BatchStatus
import com.example.fishy.domain.calc.DoubleControlStats
import com.example.fishy.domain.calc.ShipmentCalculator
import com.example.fishy.domain.calc.ShipmentTotals
import com.example.fishy.domain.model.BatchLimit
import com.example.fishy.domain.model.DictionaryType
import com.example.fishy.domain.model.Product
import com.example.fishy.domain.model.ShipmentMode
import com.example.fishy.domain.model.ShipmentPayload
import com.example.fishy.ui.ErrorFeedback
import com.example.fishy.ui.components.AccordionCard
import com.example.fishy.ui.components.ConfirmDeleteDialog
import com.example.fishy.ui.components.ConfirmSaveDialog
import com.example.fishy.ui.components.CenteredDialogMessage
import com.example.fishy.ui.components.CenteredDialogTitle
import com.example.fishy.ui.components.DialogCancelConfirmActions
import com.example.fishy.ui.components.DialogCenteredFishyButton
import com.example.fishy.ui.components.DictionaryAutocomplete
import com.example.fishy.ui.components.FillProgressBar
import com.example.fishy.ui.components.FishySentenceKeyboardOptions
import com.example.fishy.ui.components.PalletRow
import com.example.fishy.ui.components.PalletTableHeader
import com.example.fishy.ui.components.TransportFields
import com.example.fishy.ui.components.UnloadReceptionFields
import com.example.fishy.ui.components.transportTitle
import com.example.fishy.ui.components.unloadReceptionTitle
import com.example.fishy.ui.theme.Error
import com.example.fishy.ui.theme.PlaceholderGrey
import com.example.fishy.ui.theme.ProgressGreen
import com.example.fishy.ui.theme.ProgressYellow
import com.example.fishy.ui.theme.Success
import com.example.fishy.ui.theme.Warning
import kotlinx.coroutines.launch

private enum class CompletePlacesMismatch {
    None, Over, Under
}

@OptIn(ExperimentalMaterial3Api::class)
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
    val sessionKey by vm.sessionKey.collectAsState()
    val customers by vm.customers.collectAsState()
    val ports by vm.ports.collectAsState()
    val vessels by vm.vessels.collectAsState()
    val productsDict by vm.productsDict.collectAsState()
    val manufacturers by vm.manufacturers.collectAsState()
    val context = LocalContext.current

    var showChecklist by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var batchPanelExpanded by remember { mutableStateOf(true) }
    var batchEditor by remember { mutableStateOf<BatchLimit?>(null) }
    var showCompleteConfirm by remember { mutableStateOf(false) }
    var completePlacesMismatch by remember { mutableStateOf(CompletePlacesMismatch.None) }
    var guardDialog by remember { mutableStateOf<ShipmentUiEvent.GuardConfirm?>(null) }
    var pendingDelete by remember { mutableStateOf<PendingDelete?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val forecastRunningMsg = stringResource(R.string.forecast_running)
    var forecastRunningJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
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
                        forecastRunningJob?.cancel()
                        snackbarHostState.currentSnackbarData?.dismiss()
                        forecastRunningJob = scope.launch {
                            snackbarHostState.showSnackbar(
                                message = forecastRunningMsg,
                                duration = SnackbarDuration.Indefinite
                            )
                        }
                    } else {
                        forecastRunningJob?.cancel()
                        forecastRunningJob = null
                        snackbarHostState.currentSnackbarData?.dismiss()
                    }
                }
                is ShipmentUiEvent.ForecastExpectation -> {
                    forecastRunningJob?.cancel()
                    forecastRunningJob = null
                    snackbarHostState.currentSnackbarData?.dismiss()
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = event.message,
                            duration = SnackbarDuration.Long
                        )
                    }
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
        else -> ProgressYellow
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
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
                                            showSettingsMenu = false
                                            batchPanelExpanded = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                )
                FillProgressBar(progress = progress)
                if (payload.batchControlEnabled) {
                    StickyBatchControlBar(
                        payload = payload,
                        batchStatuses = batchStatuses,
                        expanded = batchPanelExpanded,
                        onExpandedChange = { batchPanelExpanded = it },
                        onEnterBatches = { batchEditor = BatchLimit() },
                        onEditBatch = { batchEditor = it }
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
                            unload = false
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
                        val done = vTotals.remainder == 0 && vehicle.products.any { it.quantity > 0 }
                        val dcStats = ShipmentCalculator.doubleControlStats(vehicle.products, dc)
                        val focusInVehicle = focusPalletTarget?.let { t ->
                            vehicle.products.any { it.id == t.productId }
                        } == true
                        AccordionCard(
                            title = transportTitle(vehicle.transport),
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
                                        unload = false
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
                                    doubleControlEnabled = dc,
                                    dcStats = dcStats
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
                        val done = gTotals.remainder == 0 && group.products.any { it.quantity > 0 }
                        val portTitle = if (group.port.isBlank()) stringResource(R.string.new_port) else stringResource(R.string.port_title, group.port)
                        val dcStats = ShipmentCalculator.doubleControlStats(group.products, dc)
                        val portSubtitle = if (group.products.isNotEmpty()) {
                            stringResource(
                                R.string.port_products_summary,
                                group.products.size,
                                gTotals.places
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
                                        unload = false
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
                                    doubleControlEnabled = dc,
                                    dcStats = dcStats
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
                        val receptionTitle = unloadReceptionTitle(reception.name, reception.transport)
                        val focusInReception = focusPalletTarget?.let { t ->
                            reception.inbounds.any { ib -> ib.products.any { it.id == t.productId } }
                        } == true
                        AccordionCard(
                            title = receptionTitle,
                            initiallyExpanded = sectionExpanded(resumeDraft, activeReceptionId, reception.id),
                            forceExpandToken = focusPalletTarget?.takeIf { focusInReception }
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
                                    autoSpaceContainers = settings.effectiveAutoSpaceContainers,
                                    autoSpaceVehicles = settings.effectiveAutoSpaceVehicles
                                )
                            }
                            reception.inbounds.forEach { inbound ->
                                val inboundTitle = transportTitle(inbound.transport).let { t ->
                                    if (t != stringResource(R.string.new_transport)) t
                                    else stringResource(R.string.unload_source)
                                }
                                val focusInInbound = focusPalletTarget?.let { t ->
                                    inbound.products.any { it.id == t.productId }
                                } == true
                                AccordionCard(
                                    title = inboundTitle,
                                    initiallyExpanded = !resumeDraft || reception.id == activeReceptionId,
                                    forceExpandToken = focusPalletTarget?.takeIf { focusInInbound }
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
                                            unload = true
                                        )
                                    }
                                    FishyButton(
                                        onClick = { vm.addUnloadProduct(reception.id, inbound.id) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(stringResource(R.string.add_product))
                                    }
                                    TextButton(onClick = {
                                        pendingDelete = PendingDelete(
                                            title = context.getString(R.string.delete_source_title),
                                            message = context.getString(R.string.delete_source_msg)
                                        ) { vm.deleteUnloadInbound(reception.id, inbound.id) }
                                    }) {
                                        Text(stringResource(R.string.delete))
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
                                TotalsBlock(totals = uTotals)
                            }
                            TextButton(onClick = {
                                pendingDelete = PendingDelete(
                                    title = context.getString(R.string.delete_reception_title),
                                    message = context.getString(R.string.delete_reception_msg)
                                ) { vm.deleteUnloadReception(reception.id) }
                            }) {
                                Text(stringResource(R.string.delete))
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
                val allProducts = ShipmentCalculator.allProducts(payload)
                val dcStats = ShipmentCalculator.doubleControlStats(
                    allProducts,
                    payload.doubleControlEnabled
                )
                AccordionCard(
                    title = stringResource(R.string.totals_overall),
                    initiallyExpanded = !resumeDraft
                ) {
                    TotalsBlock(
                        totals = totals,
                        doubleControlEnabled = payload.doubleControlEnabled,
                        dcStats = dcStats
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
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.save_shipment))
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(72.dp)) }
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
            onDelete = if (payload.batchLimits.any { it.id == editing.id }) {
                {
                    vm.deleteBatchLimit(editing.id)
                    batchEditor = null
                }
            } else null,
            onAddToDictionary = { type, value -> vm.addToDictionary(type, value) }
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

    guardDialog?.let { g ->
        AlertDialog(
            onDismissRequest = { guardDialog = null },
            containerColor = MaterialTheme.colorScheme.background,
            title = { CenteredDialogTitle(stringResource(R.string.guard_confirm)) },
            text = { CenteredDialogMessage("${g.field} = ${g.value}") },
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
    doubleControlEnabled: Boolean = false,
    dcStats: DoubleControlStats? = null
) {
    TotalsRow(stringResource(R.string.product_types), "${totals.productTypes}")
    TotalsRow(stringResource(R.string.loaded_pallets), "${totals.pallets}")
    TotalsRow(stringResource(R.string.loaded_places), "${totals.places}")
    TotalsRow(stringResource(R.string.target_qty), "${totals.quantity}")
    TotalsRow(stringResource(R.string.target_mass_label), stringResource(R.string.total_mass_value, totals.targetWeight))
    TotalsRow(stringResource(R.string.total_mass_label), stringResource(R.string.total_mass_value, totals.actualWeight))
    when {
        totals.remainder > 0 -> Text(
            stringResource(R.string.underload, totals.remainder),
            color = Error,
            fontWeight = FontWeight.Bold
        )
        totals.remainder < 0 -> Text(
            stringResource(R.string.overload, -totals.remainder),
            color = Warning,
            fontWeight = FontWeight.Bold
        )
        else -> Text(
            stringResource(R.string.loading_done),
            color = Success,
            fontWeight = FontWeight.Bold
        )
    }
    if (doubleControlEnabled && dcStats != null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(stringResource(R.string.double_control), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.pallets_dc, dcStats.importedPallets, dcStats.totalPallets))
                Text(stringResource(R.string.places_dc, dcStats.importedPlaces, dcStats.exportedPlaces))
            }
        }
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
                Switch(checked = checked, onCheckedChange = onCheckedChange)
            }
        },
        onClick = { onCheckedChange(!checked) }
    )
}

@Composable
private fun StickyBatchControlBar(
    payload: ShipmentPayload,
    batchStatuses: List<BatchStatus>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onEnterBatches: () -> Unit,
    onEditBatch: (BatchLimit) -> Unit
) {
    val statusByKey = remember(batchStatuses) { batchStatuses.associateBy { it.key } }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpandedChange(!expanded) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.batch_control),
                style = MaterialTheme.typography.titleMedium,
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
            if (payload.batchLimits.isEmpty()) {
                Text(
                    text = stringResource(R.string.enter_batches),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onEnterBatches)
                        .padding(vertical = 8.dp)
                )
            } else {
                payload.batchLimits.forEach { limit ->
                    val status = statusByKey[ShipmentCalculator.batchKey(limit)]
                    val color = when {
                        status == null -> MaterialTheme.colorScheme.onSurface
                        status.exhausted -> ProgressGreen
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    val tare = if (limit.packageWeight == 0.0) "0" else {
                        limit.packageWeight.toString().trimEnd('0').trimEnd('.')
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEditBatch(limit) }
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(
                                R.string.batch_plan_line,
                                limit.productName.ifBlank { "—" },
                                limit.batchName.ifBlank { "—" },
                                limit.manufacturer.ifBlank { "—" },
                                tare,
                                limit.plannedPlaces
                            ),
                            color = color,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (status != null) {
                            Text(
                                text = stringResource(
                                    R.string.batch_remaining,
                                    limit.batchName.ifBlank { "—" },
                                    status.remaining,
                                    status.planned
                                ),
                                color = color,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                TextButton(onClick = onEnterBatches) {
                    Text(stringResource(R.string.add_batch))
                }
            }
        }
    }
}

@Composable
private fun BatchEntryDialog(
    initial: BatchLimit,
    productsDict: List<com.example.fishy.data.local.entity.DictionaryEntity>,
    manufacturers: List<com.example.fishy.data.local.entity.DictionaryEntity>,
    onDismiss: () -> Unit,
    onSave: (BatchLimit) -> Unit,
    onDelete: (() -> Unit)?,
    onAddToDictionary: (DictionaryType, String) -> Unit
) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    val isNew = onDelete == null
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
                OutlinedTextField(
                    value = if (draft.packageWeight > 0) draft.packageWeight.toString() else "",
                    onValueChange = { value ->
                        val parsed = value.replace(',', '.').toDoubleOrNull() ?: 0.0
                        draft = draft.copy(packageWeight = parsed)
                    },
                    label = { Text(stringResource(R.string.tare)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = if (draft.plannedPlaces == 0) "" else draft.plannedPlaces.toString(),
                    onValueChange = { value ->
                        val digits = value.filter { it.isDigit() }.take(6)
                        draft = draft.copy(plannedPlaces = digits.toIntOrNull() ?: 0)
                    },
                    label = { Text(stringResource(R.string.places_count)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }) {
                Text(stringResource(R.string.ok_done))
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text(stringResource(R.string.delete))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}

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
    onPlaces: (Long, Int) -> Unit,
    onToggleImport: (Long) -> Unit,
    onDeletePallet: (Long) -> Unit,
    onDeleteProduct: () -> Unit,
    onWeightGuard: (Double, (Double) -> Unit) -> Unit,
    onAddToDictionary: (DictionaryType, String) -> Unit,
    unload: Boolean
) {
    val rem = ShipmentCalculator.remainder(product, doubleControl, unload)
    val title = productAccordionTitle(product, stringResource(R.string.new_product))
    val places = ShipmentCalculator.placesForProduct(product, doubleControl)
    val subtitle = if (product.quantity > 0) {
        val progress = "($places/${product.quantity})"
        when {
            rem > 0 -> "${stringResource(R.string.product_remainder_short, rem)} $progress"
            rem < 0 -> "${stringResource(R.string.product_overload_short, -rem)} $progress"
            else -> "${stringResource(R.string.loading_done)} $progress"
        }
    } else null
    val titleColor = if (rem == 0 && product.quantity > 0) Success else MaterialTheme.colorScheme.onSurface
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
            onAddToDictionary = onAddToDictionary
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
            keyboardOptions = FishySentenceKeyboardOptions
        )
        DictionaryAutocomplete(
            label = stringResource(R.string.manufacturer),
            value = product.manufacturer,
            suggestions = manufacturers,
            onValueChange = { v: String -> onUpdate { p: Product -> p.copy(manufacturer = v) } },
            dictionaryType = DictionaryType.MANUFACTURER,
            onAddToDictionary = onAddToDictionary
        )
        // Тара / Кол-во / Масса в одну строку — компактный ряд
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            OutlinedTextField(
                value = if (product.packageWeight > 0) product.packageWeight.toString() else "",
                onValueChange = { value ->
                    val weight = value.replace(',', '.').toDoubleOrNull() ?: 0.0
                    onWeightGuard(weight) { confirmed ->
                        onUpdate { p -> p.copy(packageWeight = confirmed) }
                    }
                },
                label = { Text(stringResource(R.string.tare), style = MaterialTheme.typography.bodySmall) },
                modifier = Modifier.weight(0.25f),
                textStyle = MaterialTheme.typography.bodySmall,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )
            OutlinedTextField(
                value = if (product.quantity > 0) product.quantity.toString() else "",
                onValueChange = { value ->
                    onUpdate { p -> p.copy(quantity = value.toIntOrNull() ?: 0) }
                },
                label = {
                    Text(
                        stringResource(R.string.quantity_short),
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                modifier = Modifier.weight(0.35f),
                textStyle = MaterialTheme.typography.bodySmall,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
            OutlinedTextField(
                value = String.format("%.1f", product.totalWeight),
                onValueChange = {},
                label = { Text(stringResource(R.string.mass), style = MaterialTheme.typography.bodySmall) },
                modifier = Modifier.weight(0.4f),
                textStyle = MaterialTheme.typography.bodySmall,
                readOnly = true,
                singleLine = true
            )
        }
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
                    onFocusHandled = onFocusHandled
                )
            }
        }
        FishyButton(onClick = onAddPallet, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.add_pallet))
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(statusColor.copy(alpha = 0.12f))
                .padding(8.dp)
        ) {
            val places = ShipmentCalculator.placesForProduct(product, doubleControl)
            Text(stringResource(R.string.places_progress, places, product.quantity))
            Text(
                text = when {
                    rem > 0 -> stringResource(R.string.underload, rem)
                    rem < 0 -> stringResource(R.string.overload, -rem)
                    else -> stringResource(R.string.norm_ok)
                },
                color = statusColor,
                fontWeight = FontWeight.Bold
            )
            if (doubleControl) {
                val exported = product.pallets.count { !it.isPlaceholder }
                val imported = product.pallets.count { it.isImported }
                Text(stringResource(R.string.exported_imported, exported, imported), style = MaterialTheme.typography.bodySmall)
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
        title = { Text(stringResource(R.string.checklist_shipment)) },
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
                                onCheckedChange = { vm.toggleChecklist(task.id) }
                            )
                            OutlinedTextField(
                                value = task.title,
                                onValueChange = { vm.editChecklistTask(task.id, it) },
                                modifier = Modifier.weight(1f),
                                keyboardOptions = FishySentenceKeyboardOptions
                            )
                            TextButton(onClick = { vm.deleteChecklistTask(task.id) }) {
                                Text("×")
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
