package com.example.fishy.feature.shipment

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fishy.FishyApp
import com.example.fishy.R
import com.example.fishy.data.serialization.FishyJson
import com.example.fishy.data.settings.FishySettings
import com.example.fishy.domain.calc.ShipmentCalculator
import com.example.fishy.domain.format.NumberFormatters
import com.example.fishy.domain.model.BatchLimit
import com.example.fishy.domain.model.ChecklistTask
import com.example.fishy.domain.model.DictionaryType
import com.example.fishy.domain.model.Pallet
import com.example.fishy.domain.model.PortGroup
import com.example.fishy.domain.model.Product
import com.example.fishy.domain.model.ShipmentEventType
import com.example.fishy.domain.model.ShipmentMode
import com.example.fishy.domain.model.ShipmentPayload
import com.example.fishy.domain.model.Transport
import com.example.fishy.domain.model.UnloadInbound
import com.example.fishy.domain.model.UnloadReception
import com.example.fishy.domain.model.VehicleGroup
import com.example.fishy.feature.scheduler.decodeScheduledPayload
import com.example.fishy.feature.scheduler.ensurePayloadStructure
import com.example.fishy.ui.ErrorFeedback
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ShipmentUiEvent {
    data class Toast(val message: String, val isError: Boolean = false) : ShipmentUiEvent
    class GuardConfirm(
        val field: String,
        val value: String,
        val onConfirm: () -> Unit
    ) : ShipmentUiEvent
    data object Saved : ShipmentUiEvent
    data class NavigateArchiveDetail(val id: Long) : ShipmentUiEvent
    data class ForecastRunning(val running: Boolean) : ShipmentUiEvent
    data class ForecastExpectation(val message: String) : ShipmentUiEvent
    /** After smart FAB adds a pallet — UI should expand and focus places field. */
    data class FocusPalletPlaces(val productId: Long, val palletId: Long) : ShipmentUiEvent
}

class ShipmentViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = FishyApp.instance.repository
    private val settingsRepo = FishyApp.instance.settingsRepository
    private val app get() = getApplication<Application>()

    private val _payload = MutableStateFlow(ShipmentPayload())
    val payload: StateFlow<ShipmentPayload> = _payload.asStateFlow()

    private val _draftId = MutableStateFlow<Long?>(null)
    val draftId: StateFlow<Long?> = _draftId.asStateFlow()

    private val _sessionKey = MutableStateFlow("session_${System.currentTimeMillis()}")
    val sessionKey: StateFlow<String> = _sessionKey.asStateFlow()

    private val confirmedGuards = mutableSetOf<String>()
    private var autoSaveJob: Job? = null
    private val placesLogJobs = mutableMapOf<Long, Job>()
    private val pendingPlacesLogs = mutableMapOf<Long, PendingPlacesLog>()
    private val forecastJobs = mutableMapOf<Long, Job>()
    private var pendingForecastProductId: Long? = null
    /** productId → "quantity:firstPlaces" last successfully applied forecast. */
    private val forecastAppliedSignature = mutableMapOf<Long, String>()
    /** Encoded payload after init; autosave skipped while unchanged. Null = always dirty (e.g. from scheduler). */
    private var baselinePayloadJson: String? = null

    companion object {
        private const val FORECAST_DEBOUNCE_MS = 2_500L
        private const val PLACES_LOG_DEBOUNCE_MS = 1_000L
    }

    private data class PendingPlacesLog(
        val productId: Long,
        val palletId: Long,
        val palletNumber: Int,
        val productName: String,
        val oldPlaces: Int,
        val newPlaces: Int
    )

    val settings: StateFlow<FishySettings> = settingsRepo.settings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), FishySettings()
    )

    private val _events = MutableSharedFlow<ShipmentUiEvent>()
    val events = _events.asSharedFlow()

    val customers = repo.observeDictionary(DictionaryType.CUSTOMER)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList<com.example.fishy.data.local.entity.DictionaryEntity>()
        )
    val ports = repo.observeDictionary(DictionaryType.PORT)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList<com.example.fishy.data.local.entity.DictionaryEntity>()
        )
    val vessels = repo.observeDictionary(DictionaryType.VESSEL)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList<com.example.fishy.data.local.entity.DictionaryEntity>()
        )
    val productsDict = repo.observeDictionary(DictionaryType.PRODUCT)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList<com.example.fishy.data.local.entity.DictionaryEntity>()
        )
    val manufacturers = repo.observeDictionary(DictionaryType.MANUFACTURER)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList<com.example.fishy.data.local.entity.DictionaryEntity>()
        )

    private var lastInitKey: Triple<ShipmentMode?, Long?, Long?>? = null

    fun ensureInitialized(mode: ShipmentMode?, draftId: Long?, scheduledId: Long?) {
        val key = Triple(mode, draftId, scheduledId)
        if (lastInitKey == key) return
        when {
            draftId != null -> {
                lastInitKey = key
                loadDraft(draftId)
            }
            scheduledId != null -> {
                lastInitKey = key
                loadFromScheduled(scheduledId)
            }
            mode != null -> {
                lastInitKey = key
                startNew(mode)
            }
        }
    }

    fun startNew(mode: ShipmentMode) {
        autoSaveJob?.cancel()
        autoSaveJob = null
        forecastJobs.values.forEach { it.cancel() }
        forecastJobs.clear()
        forecastAppliedSignature.clear()
        _draftId.value = null
        _sessionKey.value = "session_${System.currentTimeMillis()}"
        var payload = ShipmentPayload(
            mode = mode,
            checklistEnabled = true,
            batchWarnThreshold = settings.value.defaultBatchWarnThreshold
        )
        payload = when (mode) {
            ShipmentMode.MONO -> payload.copy(products = listOf(Product()))
            ShipmentMode.MULTI_VEHICLE ->
                payload.copy(multiVehicles = listOf(VehicleGroup(products = listOf(Product()))))
            ShipmentMode.MULTI_PORT ->
                payload.copy(multiPorts = listOf(PortGroup(products = listOf(Product()))))
            ShipmentMode.UNLOAD -> payload.copy(
                unloadReceptions = listOf(
                    UnloadReception(inbounds = listOf(UnloadInbound(products = listOf(Product()))))
                )
            )
        }
        _payload.value = payload
        baselinePayloadJson = FishyJson.encodePayload(payload)
        viewModelScope.launch {
            repo.log(
                _sessionKey.value,
                ShipmentEventType.STARTED,
                app.getString(R.string.history_msg_started, modeLabel(mode))
            )
        }
    }

    fun loadDraft(id: Long) {
        viewModelScope.launch {
            val entity = repo.getShipment(id) ?: return@launch
            autoSaveJob?.cancel()
            _draftId.value = id
            _sessionKey.value = "draft_$id"
            val decoded = FishyJson.decodePayload(entity.payloadJson)
            val payload = decoded.copy(checklistEnabled = true)
            _payload.value = payload
            baselinePayloadJson = FishyJson.encodePayload(payload)
            // Do not log STARTED again — reopen should not spam the audit trail.
        }
    }

    fun loadFromScheduled(scheduledId: Long) {
        viewModelScope.launch {
            val scheduled = repo.getScheduled(scheduledId) ?: return@launch
            val base = ensurePayloadStructure(
                decodeScheduledPayload(
                    scheduled.payloadJson,
                    scheduled.mode,
                    scheduled.customer,
                    scheduled.port,
                    scheduled.vessel
                )
            )
            val payload = base.copy(
                createdAtMillis = System.currentTimeMillis(),
                completedAtMillis = null,
                editedReportText = null,
                batchWarnThreshold = settings.value.defaultBatchWarnThreshold,
                checklistEnabled = true
            )
            autoSaveJob?.cancel()
            _draftId.value = null
            _sessionKey.value = "from_sched_$scheduledId"
            _payload.value = payload
            // Always allow autosave: schedule entry is already marked completed.
            baselinePayloadJson = null
            repo.markScheduledCompleted(scheduledId)
            FishyApp.instance.notificationScheduler.cancel(scheduledId)
            repo.log(
                _sessionKey.value,
                ShipmentEventType.STARTED,
                app.getString(R.string.history_msg_from_scheduler, scheduledId)
            )
            scheduleAutoSave()
        }
    }

    private fun update(block: (ShipmentPayload) -> ShipmentPayload) {
        _payload.update(block)
        scheduleAutoSave()
    }

    private fun isDirty(): Boolean {
        val baseline = baselinePayloadJson ?: return true
        return FishyJson.encodePayload(_payload.value) != baseline
    }

    private fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(3000)
            saveDraftInternal(force = false)
        }
    }

    private suspend fun saveDraftInternal(force: Boolean = false) {
        val current = _payload.value
        if (!current.hasUserContent()) {
            val existingId = _draftId.value
            if (existingId != null) {
                repo.deleteShipment(existingId)
                _draftId.value = null
                baselinePayloadJson = FishyJson.encodePayload(current)
            }
            return
        }
        if (!force && !isDirty()) return
        val previousKey = _sessionKey.value
        val id = repo.saveDraft(
            _draftId.value,
            getApplication<Application>().getString(R.string.draft_default),
            current
        )
        _draftId.value = id
        val newKey = "draft_$id"
        if (previousKey != newKey) {
            repo.rekeyEvents(previousKey, newKey)
            _sessionKey.value = newKey
        }
        repo.rememberDictionaryValues(current)
        baselinePayloadJson = FishyJson.encodePayload(current)
    }

    fun saveDraftManual() {
        flushPendingPlacesLog()
        autoSaveJob?.cancel()
        viewModelScope.launch {
            if (!_payload.value.hasUserContent()) return@launch
            saveDraftInternal(force = true)
            if (_draftId.value == null) return@launch
            repo.log(
                _sessionKey.value,
                ShipmentEventType.DRAFT_SAVED,
                app.getString(R.string.history_msg_draft_saved)
            )
            _events.emit(ShipmentUiEvent.Toast(app.getString(R.string.draft_saved)))
        }
    }

    /** Flush pending autosave immediately (e.g. before opening history). */
    fun flushDraft() {
        flushPendingPlacesLog()
        autoSaveJob?.cancel()
        viewModelScope.launch { saveDraftInternal(force = false) }
    }

    fun addToDictionary(type: DictionaryType, value: String) {
        viewModelScope.launch {
            repo.addDictionary(type, value)
            _events.emit(
                ShipmentUiEvent.Toast(
                    getApplication<Application>().getString(R.string.dict_added, value.trim())
                )
            )
        }
    }

    fun complete() {
        viewModelScope.launch {
            flushPendingPlacesLogNow()
            autoSaveJob?.cancel()
            autoSaveJob = null
            forecastJobs.values.forEach { it.cancel() }
            forecastJobs.clear()
            val previousKey = _sessionKey.value
            val draftIdBefore = _draftId.value
            val id = repo.completeShipment(draftIdBefore, _payload.value)
            repo.rekeyEvents(previousKey, id.toString())
            _sessionKey.value = id.toString()
            _draftId.value = null
            repo.log(
                id.toString(),
                ShipmentEventType.COMPLETED,
                app.getString(R.string.history_msg_completed)
            )
            _events.emit(ShipmentUiEvent.NavigateArchiveDetail(id))
        }
    }

    fun setCustomer(v: String) = update { it.copy(customer = v) }
    fun setPort(v: String) = update { it.copy(port = v) }
    fun setVessel(v: String) = update { it.copy(vessel = v) }
    fun setNotes(v: String) = update { it.copy(notes = v) }

    fun setDoubleControl(v: Boolean) = update { it.copy(doubleControlEnabled = v) }
    fun setPalletForecast(v: Boolean) {
        if (!v) {
            forecastJobs.values.forEach { it.cancel() }
            forecastJobs.clear()
            pendingForecastProductId = null
            forecastAppliedSignature.clear()
            viewModelScope.launch { _events.emit(ShipmentUiEvent.ForecastRunning(false)) }
            update { payload ->
                clearAllForecastPlaceholders(payload).copy(palletForecastEnabled = false)
            }
        } else {
            // Only enable the flag — do not auto-run until qty + first pallet places exist.
            update { it.copy(palletForecastEnabled = true) }
        }
    }
    fun setChecklistEnabled(v: Boolean) = update {
        it.copy(checklistEnabled = v)
    }
    fun setBatchControl(v: Boolean) = update { it.copy(batchControlEnabled = v) }
    fun setBatchWarn(v: Int) = update { it.copy(batchWarnThreshold = v) }

    fun updateTransport(transform: (Transport) -> Transport) = update {
        it.copy(transport = transform(it.transport))
    }

    fun setContainer(raw: String) {
        val clean = NumberFormatters.stripSpaces(raw).uppercase()
        updateTransport { it.copy(containerNumber = clean) }
    }

    fun setTruck(raw: String) {
        val clean = NumberFormatters.stripSpaces(raw).uppercase()
        updateTransport { it.copy(truckNumber = clean) }
    }

    fun setTrailer(raw: String) {
        val clean = NumberFormatters.stripSpaces(raw).uppercase()
        updateTransport { it.copy(trailerNumber = clean) }
    }

    fun setWagon(raw: String) = updateTransport { it.copy(wagonNumber = NumberFormatters.stripSpaces(raw)) }
    fun setSeal(raw: String) = updateTransport { it.copy(sealNumber = raw) }

    // ---- Mono products ----
    fun addProduct() {
        update { it.copy(products = it.products + Product()) }
        logEvent(ShipmentEventType.PRODUCT_ADDED, app.getString(R.string.history_msg_product_added))
    }

    fun updateProduct(productId: Long, transform: (Product) -> Product) {
        val before = findProduct(productId)
        mutateProductPallet(productId, transform)
        val after = findProduct(productId) ?: return
        if (!_payload.value.palletForecastEnabled || before == null) return
        if (before.quantity == after.quantity) return
        // Quantity changed → allow one new forecast if still only the first real pallet.
        forecastAppliedSignature.remove(productId)
        mutateProductPallet(productId) { it.copy(pallets = ShipmentCalculator.realPallets(it)) }
        scheduleForecastIfEligible(productId)
    }

    fun deleteProduct(productId: Long) {
        val name = findProduct(productId)?.name.orEmpty()
        forecastAppliedSignature.remove(productId)
        when (_payload.value.mode) {
            ShipmentMode.MONO -> update {
                it.copy(products = it.products.filter { p -> p.id != productId })
            }
            ShipmentMode.MULTI_VEHICLE -> update {
                it.copy(
                    multiVehicles = it.multiVehicles.map { v ->
                        v.copy(products = v.products.filter { p -> p.id != productId })
                    }
                )
            }
            ShipmentMode.MULTI_PORT -> update {
                it.copy(
                    multiPorts = it.multiPorts.map { g ->
                        g.copy(products = g.products.filter { p -> p.id != productId })
                    }
                )
            }
            ShipmentMode.UNLOAD -> update {
                it.copy(
                    unloadReceptions = it.unloadReceptions.map { r ->
                        r.copy(
                            inbounds = r.inbounds.map { inbound ->
                                inbound.copy(products = inbound.products.filter { p -> p.id != productId })
                            }
                        )
                    }
                )
            }
        }
        logEvent(
            ShipmentEventType.PRODUCT_DELETED,
            app.getString(
                R.string.history_msg_product_deleted,
                name.ifBlank { app.getString(R.string.new_product) }
            )
        )
    }

    fun addPallet(productId: Long, focusAfter: Boolean = false) {
        flushPendingPlacesLog()
        forecastAppliedSignature.remove(productId)
        var newPalletId: Long? = null
        var newPalletNumber = 0
        val productName = findProduct(productId)?.name.orEmpty()
        mutateProductPallet(productId) { product ->
            val real = ShipmentCalculator.realPallets(product)
            val nextNum = real.size + 1
            val newPallet = Pallet(palletNumber = nextNum, places = 0)
            newPalletId = newPallet.id
            newPalletNumber = nextNum
            // Manual add takes over — drop placeholders, do not auto-forecast.
            product.copy(pallets = real + newPallet)
        }
        logEvent(
            ShipmentEventType.PALLET_ADDED,
            app.getString(
                R.string.history_msg_pallet_added,
                newPalletNumber,
                productLabel(productName)
            )
        )
        if (focusAfter) {
            val palletId = newPalletId ?: return
            viewModelScope.launch {
                _events.emit(ShipmentUiEvent.FocusPalletPlaces(productId, palletId))
            }
        }
    }

    fun updatePalletPlaces(productId: Long, palletId: Long, places: Int) {
        val settings = settings.value
        val apply = {
            var shouldSchedule = false
            mutateProductPallet(productId) { product ->
                val target = product.pallets.find { it.id == palletId } ?: return@mutateProductPallet product
                val oldPlaces = target.places
                val delta = places - oldPlaces
                if (!ShipmentCalculator.canAddPlaces(_payload.value, product, delta)) {
                    viewModelScope.launch {
                        val key = ShipmentCalculator.batchKey(product)
                        val limit = _payload.value.batchLimits.find {
                            ShipmentCalculator.batchKey(it) == key
                        }
                        val used = ShipmentCalculator.placesForProduct(product, _payload.value.doubleControlEnabled)
                        val avail = (limit?.plannedPlaces ?: 0) - used + oldPlaces
                        _events.emit(
                            ShipmentUiEvent.Toast(
                                app.getString(
                                    R.string.batch_limit_exceeded,
                                    product.batch.ifBlank { product.name }.ifBlank { "—" },
                                    avail.coerceAtLeast(0)
                                ),
                                isError = true
                            )
                        )
                    }
                    return@mutateProductPallet product
                }
                if (_payload.value.batchControlEnabled &&
                    ShipmentCalculator.batchStatuses(_payload.value, _payload.value.batchWarnThreshold)
                        .any { it.key == ShipmentCalculator.batchKey(product) && it.exhausted }
                ) {
                    vibrateShort()
                }
                if (oldPlaces != places) {
                    schedulePlacesLog(
                        productId = productId,
                        palletId = palletId,
                        palletNumber = target.palletNumber,
                        productName = product.name,
                        oldPlaces = oldPlaces,
                        newPlaces = places
                    )
                }

                if (target.isPlaceholder) {
                    // Confirm one forecast row as real; keep other placeholders. No re-forecast.
                    return@mutateProductPallet product.copy(
                        pallets = product.pallets.map {
                            if (it.id == palletId) it.copy(places = places, isPlaceholder = false) else it
                        }
                    )
                }

                val real = ShipmentCalculator.realPallets(product)
                val isFirst = real.firstOrNull()?.id == palletId
                val updatedReals = real.map {
                    if (it.id == palletId) it.copy(places = places, isPlaceholder = false) else it
                }
                val placeholders = product.pallets.filter { it.isPlaceholder }

                when {
                    // Only first real pallet exists → typing places may (re)run forecast.
                    isFirst && real.size == 1 -> {
                        forecastAppliedSignature.remove(productId)
                        shouldSchedule = places > 0 && product.quantity > 0
                        product.copy(pallets = updatedReals) // drop old placeholders until debounce fires
                    }
                    // Second+ reals already present → editing first must not re-forecast.
                    isFirst -> {
                        product.copy(pallets = updatedReals + placeholders)
                    }
                    else -> {
                        product.copy(pallets = updatedReals + placeholders)
                    }
                }
            }
            if (shouldSchedule) scheduleForecastIfEligible(productId)
        }

        if (settings.inputGuardEnabled &&
            settings.maxPlacesPerPallet > 0 &&
            places > settings.maxPlacesPerPallet &&
            "places" !in confirmedGuards
        ) {
            viewModelScope.launch {
                _events.emit(
                    ShipmentUiEvent.GuardConfirm("places", places.toString()) {
                        confirmedGuards += "places"
                        viewModelScope.launch {
                            repo.log(
                                _sessionKey.value,
                                ShipmentEventType.INPUT_GUARD_CONFIRMED,
                                app.getString(R.string.history_msg_guard_places, places)
                            )
                        }
                        apply()
                    }
                )
            }
            return
        }
        apply()
    }

    fun togglePalletImported(productId: Long, palletId: Long) {
        val product = findProduct(productId) ?: return
        val pallet = product.pallets.find { it.id == palletId } ?: return
        val nowImported = !pallet.isImported
        mutateProductPallet(productId) { p ->
            p.copy(
                pallets = p.pallets.map {
                    if (it.id == palletId) it.copy(isImported = nowImported) else it
                }
            )
        }
        val msgRes = if (nowImported) {
            R.string.history_msg_pallet_imported
        } else {
            R.string.history_msg_pallet_unimported
        }
        logEvent(
            ShipmentEventType.PALLET_IMPORTED,
            app.getString(
                msgRes,
                pallet.palletNumber,
                productLabel(product.name),
                pallet.places
            )
        )
    }

    fun deletePallet(productId: Long, palletId: Long) {
        placesLogJobs.remove(palletId)?.cancel()
        pendingPlacesLogs.remove(palletId)
        flushPendingPlacesLog()
        var removedPlaceholder = false
        var removedNumber = 0
        var removedPlaces = 0
        val productName = findProduct(productId)?.name.orEmpty()
        mutateProductPallet(productId) { product ->
            val removed = product.pallets.find { it.id == palletId }
            removedPlaceholder = removed?.isPlaceholder == true
            removedNumber = removed?.palletNumber ?: 0
            removedPlaces = removed?.places ?: 0
            var num = 1
            val remaining = product.pallets
                .filter { it.id != palletId }
                .map { it.copy(palletNumber = num++) }
            if (!removedPlaceholder) {
                // Deleting a real pallet invalidates forecast lock.
                forecastAppliedSignature.remove(productId)
            }
            product.copy(pallets = remaining)
        }
        if (!removedPlaceholder) {
            logEvent(
                ShipmentEventType.PALLET_DELETED,
                app.getString(
                    R.string.history_msg_pallet_deleted,
                    removedNumber,
                    productLabel(productName),
                    removedPlaces
                )
            )
            scheduleForecastIfEligible(productId)
        }
    }

    private fun scheduleForecastIfEligible(productId: Long) {
        if (!_payload.value.palletForecastEnabled) return
        val product = findProduct(productId) ?: return
        if (!ShipmentCalculator.canAutoForecast(product)) return
        val signature = ShipmentCalculator.forecastSignature(product) ?: return
        if (forecastAppliedSignature[productId] == signature) return

        pendingForecastProductId = productId
        forecastJobs[productId]?.cancel()
        forecastJobs[productId] = viewModelScope.launch {
            _events.emit(ShipmentUiEvent.ForecastRunning(true))
            try {
                delay(FORECAST_DEBOUNCE_MS)
                val message = applyForecastNow(productId)
                if (message != null) {
                    _events.emit(ShipmentUiEvent.ForecastExpectation(message))
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } finally {
                forecastJobs.remove(productId)
                if (pendingForecastProductId == productId) pendingForecastProductId = null
                if (forecastJobs.isEmpty()) {
                    _events.emit(ShipmentUiEvent.ForecastRunning(false))
                }
            }
        }
    }

    /** @return expectation snackbar text, or null if nothing was applied */
    private fun applyForecastNow(productId: Long): String? {
        val product = findProduct(productId) ?: return null
        if (!ShipmentCalculator.canAutoForecast(product)) return null
        val signature = ShipmentCalculator.forecastSignature(product) ?: return null
        if (forecastAppliedSignature[productId] == signature) return null

        val expected = ShipmentCalculator.expectedForecastPallets(product)
        if (expected != null && expected > ShipmentCalculator.MAX_FORECAST_PALLETS) {
            mutateProductPallet(productId) {
                it.copy(pallets = ShipmentCalculator.realPallets(it))
            }
            viewModelScope.launch {
                _events.emit(
                    ShipmentUiEvent.Toast(
                        getApplication<Application>().getString(R.string.forecast_error),
                        isError = true
                    )
                )
            }
            return null
        }

        val firstPlaces = ShipmentCalculator.realPallets(product).first().places
        val message = ShipmentCalculator.formatForecastExpectationRu(product.quantity, firstPlaces)
        mutateProductPallet(productId) { ShipmentCalculator.applyForecastPlaceholders(it) }
        forecastAppliedSignature[productId] = signature
        return message
    }

    private fun findProduct(productId: Long): Product? =
        ShipmentCalculator.allProducts(_payload.value).find { it.id == productId }

    private fun clearAllForecastPlaceholders(payload: ShipmentPayload): ShipmentPayload {
        fun Product.strip() = copy(pallets = ShipmentCalculator.realPallets(this))
        return when (payload.mode) {
            ShipmentMode.MONO -> payload.copy(products = payload.products.map { it.strip() })
            ShipmentMode.MULTI_VEHICLE -> payload.copy(
                multiVehicles = payload.multiVehicles.map { v ->
                    v.copy(products = v.products.map { it.strip() })
                }
            )
            ShipmentMode.MULTI_PORT -> payload.copy(
                multiPorts = payload.multiPorts.map { g ->
                    g.copy(products = g.products.map { it.strip() })
                }
            )
            ShipmentMode.UNLOAD -> payload.copy(
                unloadReceptions = payload.unloadReceptions.map { r ->
                    r.copy(
                        inbounds = r.inbounds.map { inbound ->
                            inbound.copy(products = inbound.products.map { it.strip() })
                        }
                    )
                }
            )
        }
    }

    private fun mutateProductPallet(productId: Long, transform: (Product) -> Product) {
        val receptionId = _payload.value.unloadReceptions.firstOrNull { r ->
            r.inbounds.any { ib -> ib.products.any { p -> p.id == productId } }
        }?.id
        when (_payload.value.mode) {
            ShipmentMode.MONO -> update {
                it.copy(
                    lastUsedProductId = productId,
                    products = it.products.map { p -> if (p.id == productId) transform(p) else p }
                )
            }
            ShipmentMode.MULTI_VEHICLE -> update {
                it.copy(
                    lastUsedProductId = productId,
                    multiVehicles = it.multiVehicles.map { v ->
                        v.copy(products = v.products.map { p -> if (p.id == productId) transform(p) else p })
                    }
                )
            }
            ShipmentMode.MULTI_PORT -> update {
                it.copy(
                    lastUsedProductId = productId,
                    multiPorts = it.multiPorts.map { g ->
                        g.copy(products = g.products.map { p -> if (p.id == productId) transform(p) else p })
                    }
                )
            }
            ShipmentMode.UNLOAD -> update {
                it.copy(
                    lastUsedProductId = productId,
                    lastUsedUnloadReceptionId = receptionId ?: it.lastUsedUnloadReceptionId,
                    unloadReceptions = it.unloadReceptions.map { r ->
                        r.copy(
                            inbounds = r.inbounds.map { inbound ->
                                inbound.copy(
                                    products = inbound.products.map { p ->
                                        if (p.id == productId) transform(p) else p
                                    }
                                )
                            }
                        )
                    }
                )
            }
        }
    }

    /** Smart FAB (4.14) */
    fun smartAddPallet() {
        val p = _payload.value
        when (p.mode) {
            ShipmentMode.MONO -> {
                val product = p.products.lastOrNull() ?: run {
                    addProduct()
                    return
                }
                if (ShipmentCalculator.remainder(product, p.doubleControlEnabled, false) <= 0 && product.quantity > 0) {
                    viewModelScope.launch {
                        _events.emit(ShipmentUiEvent.Toast(getApplication<Application>().getString(R.string.all_transports_full), isError = true))
                    }
                    return
                }
                focusOrAddPallet(product.id)
            }
            ShipmentMode.MULTI_VEHICLE -> {
                val vehicles = p.multiVehicles
                if (vehicles.isEmpty()) {
                    addVehicle()
                    return
                }
                val lastId = p.lastUsedVehicleId
                val ordered = if (lastId != null) {
                    val last = vehicles.find { it.id == lastId }
                    if (last != null) listOf(last) + vehicles.filter { it.id != lastId } else vehicles
                } else vehicles

                for (v in ordered) {
                    val product = v.products.lastOrNull() ?: continue
                    val rem = ShipmentCalculator.remainder(product, p.doubleControlEnabled || v.doubleControlEnabled, false)
                    if (product.quantity == 0 || rem > 0) {
                        update { it.copy(lastUsedVehicleId = v.id) }
                        focusOrAddPallet(product.id)
                        return
                    }
                }
                viewModelScope.launch {
                    _events.emit(ShipmentUiEvent.Toast(getApplication<Application>().getString(com.example.fishy.R.string.all_transports_full), isError = true))
                }
            }
            ShipmentMode.MULTI_PORT -> {
                val product = ShipmentCalculator.allProducts(p).lastOrNull()
                if (product != null) focusOrAddPallet(product.id)
            }
            ShipmentMode.UNLOAD -> {
                val product = ShipmentCalculator.allProducts(p).lastOrNull()
                if (product != null) focusOrAddPallet(product.id)
            }
        }
    }

    /**
     * FAB: if forecast placeholders exist, focus the next pallet after the last real one
     * (keep placeholders). Otherwise add a new real pallet and focus it.
     */
    private fun focusOrAddPallet(productId: Long) {
        val product = ShipmentCalculator.allProducts(_payload.value).find { it.id == productId }
            ?: return
        if (_payload.value.palletForecastEnabled && product.pallets.any { it.isPlaceholder }) {
            val lastRealIndex = product.pallets.indexOfLast { !it.isPlaceholder }
            val next = product.pallets.getOrNull(lastRealIndex + 1)
                ?: product.pallets.firstOrNull { it.isPlaceholder }
            if (next != null) {
                touchLastUsedProduct(productId)
                viewModelScope.launch {
                    _events.emit(ShipmentUiEvent.FocusPalletPlaces(productId, next.id))
                }
                return
            }
        }
        addPallet(productId, focusAfter = true)
    }

    private fun touchLastUsedProduct(productId: Long) {
        val receptionId = _payload.value.unloadReceptions.firstOrNull { r ->
            r.inbounds.any { ib -> ib.products.any { p -> p.id == productId } }
        }?.id
        update { payload ->
            when (payload.mode) {
                ShipmentMode.UNLOAD -> payload.copy(
                    lastUsedProductId = productId,
                    lastUsedUnloadReceptionId = receptionId ?: payload.lastUsedUnloadReceptionId
                )
                else -> payload.copy(lastUsedProductId = productId)
            }
        }
    }

    // Multi-vehicle
    fun addVehicle() {
        update {
            it.copy(multiVehicles = it.multiVehicles + VehicleGroup(products = listOf(Product())))
        }
        logEvent(ShipmentEventType.TRANSPORT_ADDED, app.getString(R.string.history_msg_transport_added))
    }

    fun deleteVehicle(id: Long) {
        update {
            it.copy(multiVehicles = it.multiVehicles.filter { v -> v.id != id })
        }
        logEvent(ShipmentEventType.TRANSPORT_DELETED, app.getString(R.string.history_msg_transport_deleted))
    }

    fun updateVehicle(id: Long, transform: (VehicleGroup) -> VehicleGroup) = update {
        it.copy(
            lastUsedVehicleId = id,
            multiVehicles = it.multiVehicles.map { v -> if (v.id == id) transform(v) else v }
        )
    }

    fun addVehicleProduct(vehicleId: Long) {
        updateVehicle(vehicleId) {
            it.copy(products = it.products + Product())
        }
        logEvent(ShipmentEventType.PRODUCT_ADDED, app.getString(R.string.history_msg_product_added))
    }

    // Multi-port
    fun addPortGroup() {
        update {
            it.copy(multiPorts = it.multiPorts + PortGroup(products = listOf(Product())))
        }
        logEvent(ShipmentEventType.PORT_ADDED, app.getString(R.string.history_msg_port_added))
    }

    fun deletePortGroup(id: Long) {
        update {
            it.copy(multiPorts = it.multiPorts.filter { g -> g.id != id })
        }
        logEvent(ShipmentEventType.PORT_DELETED, app.getString(R.string.history_msg_port_deleted))
    }

    fun updatePortGroup(id: Long, transform: (PortGroup) -> PortGroup) = update {
        it.copy(
            lastUsedPortId = id,
            multiPorts = it.multiPorts.map { g -> if (g.id == id) transform(g) else g }
        )
    }

    fun addPortProduct(portId: Long) {
        updatePortGroup(portId) { g ->
            g.copy(products = g.products + Product())
        }
        logEvent(ShipmentEventType.PRODUCT_ADDED, app.getString(R.string.history_msg_product_added))
    }

    // Unload — reception (destination) is parent; inbounds are sources nested under it
    fun addUnloadReception() = update {
        it.copy(
            unloadReceptions = it.unloadReceptions + UnloadReception(
                inbounds = listOf(UnloadInbound(products = listOf(Product())))
            )
        )
    }

    fun deleteUnloadReception(id: Long) = update {
        it.copy(unloadReceptions = it.unloadReceptions.filter { r -> r.id != id })
    }

    fun updateUnloadReception(id: Long, transform: (UnloadReception) -> UnloadReception) = update {
        it.copy(
            lastUsedUnloadReceptionId = id,
            unloadReceptions = it.unloadReceptions.map { r -> if (r.id == id) transform(r) else r }
        )
    }

    fun addUnloadInbound(receptionId: Long) = updateUnloadReception(receptionId) {
        it.copy(inbounds = it.inbounds + UnloadInbound(products = listOf(Product())))
    }

    fun deleteUnloadInbound(receptionId: Long, inboundId: Long) = updateUnloadReception(receptionId) {
        val next = it.inbounds.filter { inbound -> inbound.id != inboundId }
        it.copy(inbounds = next.ifEmpty { listOf(UnloadInbound(products = listOf(Product()))) })
    }

    fun updateUnloadInbound(
        receptionId: Long,
        inboundId: Long,
        transform: (UnloadInbound) -> UnloadInbound
    ) = updateUnloadReception(receptionId) {
        it.copy(inbounds = it.inbounds.map { inbound ->
            if (inbound.id == inboundId) transform(inbound) else inbound
        })
    }

    fun addUnloadProduct(receptionId: Long, inboundId: Long) {
        updateUnloadInbound(receptionId, inboundId) { ib ->
            ib.copy(products = ib.products + Product())
        }
        logEvent(ShipmentEventType.PRODUCT_ADDED, app.getString(R.string.history_msg_product_added))
    }

    // Checklist
    fun toggleChecklist(taskId: Long) = update { p ->
        val now = System.currentTimeMillis()
        val checklist = p.checklist.map {
            if (it.id == taskId) {
                val done = !it.isCompleted
                viewModelScope.launch {
                    repo.log(
                        _sessionKey.value,
                        ShipmentEventType.CHECKLIST_CHANGED,
                        app.getString(
                            if (done) R.string.history_msg_checklist_done else R.string.history_msg_checklist_undone,
                            it.title
                        )
                    )
                }
                it.copy(isCompleted = done, completedAtMillis = if (done) now else null)
            } else it
        }
        p.copy(checklist = checklist)
    }

    fun addChecklistTask(title: String) = update {
        it.copy(checklist = it.checklist + ChecklistTask(title = title))
    }

    fun deleteChecklistTask(id: Long) = update {
        it.copy(checklist = it.checklist.filter { t -> t.id != id })
    }

    fun editChecklistTask(id: Long, title: String) = update {
        it.copy(checklist = it.checklist.map { t -> if (t.id == id) t.copy(title = title) else t })
    }

    // Batches
    fun addBatchLimit() = update { it.copy(batchLimits = it.batchLimits + BatchLimit()) }
    fun upsertBatchLimit(limit: BatchLimit) = update { payload ->
        val exists = payload.batchLimits.any { it.id == limit.id }
        payload.copy(
            batchLimits = if (exists) {
                payload.batchLimits.map { if (it.id == limit.id) limit else it }
            } else {
                payload.batchLimits + limit
            }
        )
    }
    fun updateBatchLimit(id: Long, transform: (BatchLimit) -> BatchLimit) = update {
        it.copy(batchLimits = it.batchLimits.map { b -> if (b.id == id) transform(b) else b })
    }
    fun deleteBatchLimit(id: Long) = update {
        it.copy(batchLimits = it.batchLimits.filter { b -> b.id != id })
    }

    fun setEditedReport(text: String?) = update {
        if (text != null) {
            viewModelScope.launch {
                repo.log(
                    _sessionKey.value,
                    ShipmentEventType.REPORT_EDITED,
                    app.getString(R.string.history_msg_report_edited)
                )
            }
        }
        it.copy(editedReportText = text)
    }

    fun checkPackageWeight(weight: Double, apply: (Double) -> Unit) {
        val s = settings.value
        if (s.inputGuardEnabled && s.maxPlaceWeightKg > 0 && weight > s.maxPlaceWeightKg && "weight" !in confirmedGuards) {
            viewModelScope.launch {
                _events.emit(
                    ShipmentUiEvent.GuardConfirm("weight", weight.toString()) {
                        confirmedGuards += "weight"
                        viewModelScope.launch {
                            repo.log(
                                _sessionKey.value,
                                ShipmentEventType.INPUT_GUARD_CONFIRMED,
                                app.getString(R.string.history_msg_guard_weight, weight)
                            )
                        }
                        apply(weight)
                    }
                )
            }
        } else apply(weight)
    }

    private fun schedulePlacesLog(
        productId: Long,
        palletId: Long,
        palletNumber: Int,
        productName: String,
        oldPlaces: Int,
        newPlaces: Int
    ) {
        val existing = pendingPlacesLogs[palletId]
        pendingPlacesLogs[palletId] = PendingPlacesLog(
            productId = productId,
            palletId = palletId,
            palletNumber = palletNumber,
            productName = productName,
            oldPlaces = existing?.oldPlaces ?: oldPlaces,
            newPlaces = newPlaces
        )
        placesLogJobs[palletId]?.cancel()
        placesLogJobs[palletId] = viewModelScope.launch {
            delay(PLACES_LOG_DEBOUNCE_MS)
            flushPlacesLogFor(palletId)
        }
    }

    private fun flushPendingPlacesLog() {
        placesLogJobs.values.forEach { it.cancel() }
        placesLogJobs.clear()
        val pending = pendingPlacesLogs.toMap()
        pendingPlacesLogs.clear()
        if (pending.isEmpty()) return
        viewModelScope.launch {
            pending.values.forEach { writePlacesLog(it) }
        }
    }

    private suspend fun flushPendingPlacesLogNow() {
        placesLogJobs.values.forEach { it.cancel() }
        placesLogJobs.clear()
        val pending = pendingPlacesLogs.toMap()
        pendingPlacesLogs.clear()
        pending.values.forEach { writePlacesLog(it) }
    }

    private suspend fun flushPlacesLogFor(palletId: Long) {
        placesLogJobs.remove(palletId)
        val pending = pendingPlacesLogs.remove(palletId) ?: return
        writePlacesLog(pending)
    }

    private suspend fun writePlacesLog(pending: PendingPlacesLog) {
        if (pending.oldPlaces == pending.newPlaces) return
        val message = if (pending.oldPlaces > 0) {
            app.getString(
                R.string.history_msg_pallet_places_changed,
                pending.palletNumber,
                productLabel(pending.productName),
                pending.oldPlaces,
                pending.newPlaces
            )
        } else {
            app.getString(
                R.string.history_msg_pallet_places,
                pending.palletNumber,
                productLabel(pending.productName),
                pending.newPlaces
            )
        }
        repo.log(_sessionKey.value, ShipmentEventType.PALLET_PLACES, message)
    }

    private fun logEvent(type: ShipmentEventType, message: String) {
        viewModelScope.launch {
            repo.log(_sessionKey.value, type, message)
        }
    }

    private fun productLabel(name: String): String =
        name.ifBlank { app.getString(R.string.new_product) }

    private fun modeLabel(mode: ShipmentMode): String = when (mode) {
        ShipmentMode.MONO -> app.getString(R.string.mode_mono)
        ShipmentMode.MULTI_VEHICLE -> app.getString(R.string.mode_multi_vehicle)
        ShipmentMode.MULTI_PORT -> app.getString(R.string.mode_multi_port)
        ShipmentMode.UNLOAD -> app.getString(R.string.mode_unload)
    }

    private fun vibrateShort() {
        ErrorFeedback.vibrate(app)
        viewModelScope.launch {
            repo.log(
                _sessionKey.value,
                ShipmentEventType.BATCH_LIMIT_HIT,
                app.getString(R.string.history_msg_batch_limit)
            )
        }
    }

}
