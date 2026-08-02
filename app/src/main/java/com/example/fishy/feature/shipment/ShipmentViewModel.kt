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
import com.example.fishy.domain.format.QuantityFormatters
import com.example.fishy.domain.model.BatchLimit
import com.example.fishy.domain.model.ChecklistTask
import com.example.fishy.domain.model.DictionaryType
import com.example.fishy.domain.model.mergeShipmentChecklist
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

sealed interface ShipmentUiEvent {
    data class Toast(val message: String, val isError: Boolean = false) : ShipmentUiEvent
    /** Blocking info dialog with a single OK action. */
    data class Alert(val message: String) : ShipmentUiEvent
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

    /** Product whose pallet places field currently has keyboard focus (FAB teleport gate). */
    private var focusedPalletProductId: Long? = null

    private val _fabSuccessTick = MutableStateFlow(0L)
    val fabSuccessTick: StateFlow<Long> = _fabSuccessTick.asStateFlow()

    private val confirmedGuards = mutableSetOf<String>()
    private var autoSaveJob: Job? = null
    private var manualSaveJob: Job? = null
    private var unknownBatchWarnJob: Job? = null
    /** productId|batchKey already toasted while unknown — avoid spam. */
    private val unknownBatchWarnedKeys = mutableSetOf<String>()
    private val draftSaveMutex = Mutex()
    private val completing = AtomicBoolean(false)
    private val saveGeneration = AtomicInteger(0)
    private val placesLogJobs = mutableMapOf<Long, Job>()
    private val pendingPlacesLogs = mutableMapOf<Long, PendingPlacesLog>()
    private val forecastJobs = mutableMapOf<Long, Job>()
    private var pendingForecastProductId: Long? = null
    /** productId → "quantity:firstPlaces" last successfully applied forecast. */
    private val forecastAppliedSignature = mutableMapOf<Long, String>()
    /** Encoded payload after init; autosave skipped while unchanged. Null = always dirty (e.g. from scheduler). */
    private var baselinePayloadJson: String? = null
    /** Scheduler row to mark completed once a draft exists or shipment is archived. */
    private var scheduledSourceId: Long? = null

    companion object {
        private const val FORECAST_DEBOUNCE_MS = 2_500L
        private const val PLACES_LOG_DEBOUNCE_MS = 1_000L
    }

    private data class PendingPlacesLog(
        val productId: Long,
        val palletId: Long,
        val palletNumber: Int,
        val productName: String,
        val oldPlaces: Double,
        val newPlaces: Double
    )

    val settings: StateFlow<FishySettings> = settingsRepo.settings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), FishySettings()
    )

    val settingsReady: StateFlow<Boolean> = settingsRepo.settings
        .map { true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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
        scheduledSourceId = null
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
            scheduledSourceId = repo.findScheduledByLinkedDraft(id)?.id
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
            val linkedDraft = scheduled.linkedDraftId
            if (linkedDraft != null) {
                val draftEntity = repo.getShipment(linkedDraft)
                if (draftEntity != null) {
                    scheduledSourceId = scheduledId
                    autoSaveJob?.cancel()
                    _draftId.value = linkedDraft
                    _sessionKey.value = "draft_$linkedDraft"
                    val decoded = FishyJson.decodePayload(draftEntity.payloadJson)
                    val payload = decoded.copy(checklistEnabled = true)
                    _payload.value = payload
                    baselinePayloadJson = FishyJson.encodePayload(payload)
                    return@launch
                }
                // Stale link: clear and fall through to a fresh start from the plan.
                repo.clearScheduleLinkByDraft(linkedDraft)
            }
            val base = ensurePayloadStructure(
                decodeScheduledPayload(
                    scheduled.payloadJson,
                    scheduled.mode,
                    scheduled.customer,
                    scheduled.port,
                    scheduled.vessel
                )
            )
            val incompletePrep = repo.getChecklist(scheduledId)
                .filter { !it.isCompleted }
                .map { ChecklistTask(title = it.title, isCompleted = false) }
            val payload = base.copy(
                createdAtMillis = System.currentTimeMillis(),
                completedAtMillis = null,
                editedReportText = null,
                batchWarnThreshold = settings.value.defaultBatchWarnThreshold,
                checklistEnabled = true,
                checklist = mergeShipmentChecklist(base.checklist, incompletePrep)
            )
            autoSaveJob?.cancel()
            _draftId.value = null
            scheduledSourceId = scheduledId
            _sessionKey.value = "from_sched_$scheduledId"
            _payload.value = payload
            baselinePayloadJson = null
            repo.log(
                _sessionKey.value,
                ShipmentEventType.STARTED,
                app.getString(R.string.history_msg_from_scheduler, scheduledId)
            )
            if (payload.hasShipmentCargoContent()) {
                saveDraftInternal(force = true)
            } else {
                scheduleAutoSave()
            }
        }
    }

    private suspend fun finishScheduledSource() {
        val id = scheduledSourceId ?: return
        repo.markScheduledCompleted(id)
        FishyApp.instance.notificationScheduler.cancel(id)
        scheduledSourceId = null
    }

    private suspend fun linkScheduledSourceToDraft(draftId: Long) {
        val schedId = scheduledSourceId ?: return
        repo.linkScheduledDraft(schedId, draftId)
        FishyApp.instance.notificationScheduler.cancel(schedId)
    }

    private fun update(block: (ShipmentPayload) -> ShipmentPayload) {
        _payload.update(block)
        scheduleAutoSave()
        scheduleUnknownBatchWarn()
    }

    private fun scheduleUnknownBatchWarn() {
        unknownBatchWarnJob?.cancel()
        unknownBatchWarnJob = viewModelScope.launch {
            delay(500)
            val p = _payload.value
            if (!p.batchControlEnabled || p.batchLimits.isEmpty()) {
                unknownBatchWarnedKeys.clear()
                return@launch
            }
            val unknown = ShipmentCalculator.allProducts(p)
                .filter { ShipmentCalculator.isUnknownBatch(it, p) }
            val activeKeys = unknown.map {
                "${it.id}|${ShipmentCalculator.batchKey(it)}"
            }.toSet()
            unknownBatchWarnedKeys.retainAll(activeKeys)
            val newlyUnknown = unknown.firstOrNull { product ->
                val key = "${product.id}|${ShipmentCalculator.batchKey(product)}"
                key !in unknownBatchWarnedKeys
            } ?: return@launch
            val warnKey = "${newlyUnknown.id}|${ShipmentCalculator.batchKey(newlyUnknown)}"
            unknownBatchWarnedKeys += warnKey
            vibrateShort()
            _events.emit(
                ShipmentUiEvent.Toast(
                    app.getString(R.string.batch_unknown),
                    isError = true
                )
            )
        }
    }

    private fun isDirty(): Boolean {
        val baseline = baselinePayloadJson ?: return true
        return FishyJson.encodePayload(_payload.value) != baseline
    }

    private fun scheduleAutoSave() {
        if (completing.get()) return
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(3000)
            saveDraftInternal(force = false)
        }
    }

    private suspend fun saveDraftInternal(force: Boolean = false) {
        if (completing.get()) return
        val gen = saveGeneration.get()
        draftSaveMutex.withLock {
            if (completing.get() || gen != saveGeneration.get()) return
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
            if (completing.get() || gen != saveGeneration.get()) return
            val previousKey = _sessionKey.value
            val id = repo.saveDraft(
                _draftId.value,
                getApplication<Application>().getString(R.string.draft_default),
                current
            )
            if (completing.get() || gen != saveGeneration.get()) return
            _draftId.value = id
            linkScheduledSourceToDraft(id)
            val newKey = "draft_$id"
            if (previousKey != newKey) {
                repo.rekeyEvents(previousKey, newKey)
                _sessionKey.value = newKey
            }
            baselinePayloadJson = FishyJson.encodePayload(current)
        }
    }

    fun saveDraftManual() {
        if (completing.get()) return
        flushPendingPlacesLog()
        autoSaveJob?.cancel()
        manualSaveJob?.cancel()
        manualSaveJob = viewModelScope.launch {
            if (!_payload.value.hasUserContent()) return@launch
            saveDraftInternal(force = true)
            if (completing.get() || _draftId.value == null) return@launch
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
        if (completing.get()) return
        flushPendingPlacesLog()
        autoSaveJob?.cancel()
        manualSaveJob?.cancel()
        manualSaveJob = viewModelScope.launch { saveDraftInternal(force = false) }
    }

    /** Await pending autosave before leaving the screen (back navigation). */
    suspend fun flushDraftAndAwait() {
        if (completing.get()) return
        flushPendingPlacesLogNow()
        autoSaveJob?.cancel()
        autoSaveJob = null
        manualSaveJob?.cancel()
        manualSaveJob = null
        saveDraftInternal(force = false)
    }

    fun addToDictionary(type: DictionaryType, value: String) {
        viewModelScope.launch {
            val inserted = repo.addDictionary(type, value)
            if (inserted) {
                _events.emit(
                    ShipmentUiEvent.Toast(
                        getApplication<Application>().getString(R.string.dict_added, value.trim())
                    )
                )
            }
        }
    }

    fun complete() {
        viewModelScope.launch {
            if (!completing.compareAndSet(false, true)) return@launch
            saveGeneration.incrementAndGet()
            try {
                flushPendingPlacesLogNow()
                autoSaveJob?.cancel()
                autoSaveJob = null
                manualSaveJob?.cancel()
                manualSaveJob = null
                forecastJobs.values.forEach { it.cancel() }
                forecastJobs.clear()
                // Wait out any in-flight save before flipping the row to archive.
                draftSaveMutex.withLock { /* barrier */ }
                val previousKey = _sessionKey.value
                val draftIdBefore = _draftId.value
                val id = repo.completeShipment(draftIdBefore, _payload.value)
                finishScheduledSource()
                repo.rekeyEvents(previousKey, id.toString())
                _sessionKey.value = id.toString()
                _draftId.value = null
                repo.log(
                    id.toString(),
                    ShipmentEventType.COMPLETED,
                    app.getString(R.string.history_msg_completed)
                )
                _events.emit(ShipmentUiEvent.NavigateArchiveDetail(id))
            } catch (t: Throwable) {
                completing.set(false)
                throw t
            }
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

    fun setChecklistReminderEnabled(v: Boolean) {
        update { it.copy(checklistReminderEnabled = v) }
    }

    fun setChecklistReminderIntervalMin(minutes: Int) = update {
        it.copy(checklistReminderIntervalMin = minutes)
    }

    fun setBatchControl(v: Boolean) = update {
        if (!v) unknownBatchWarnedKeys.clear()
        it.copy(batchControlEnabled = v)
    }
    fun setGrossWeightEnabled(v: Boolean) = update { it.copy(grossWeightEnabled = v) }
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

    private val _quickPlacesByKey = MutableStateFlow<Map<String, String>>(emptyMap())
    val quickPlacesByKey: StateFlow<Map<String, String>> = _quickPlacesByKey.asStateFlow()

    fun setQuickPlacesText(product: Product, raw: String) {
        val key = quickPlacesMapKey(product)
        val sanitized = QuantityFormatters.sanitizeDecimalInput(raw)
        _quickPlacesByKey.update { it + (key to sanitized) }
    }

    fun quickPlacesTextFor(product: Product): String =
        _quickPlacesByKey.value[quickPlacesMapKey(product)].orEmpty()

    /** Incomplete product identity → per-product slot so empty cards don't share one draft. */
    private fun quickPlacesMapKey(product: Product): String {
        if (product.name.isBlank() ||
            product.batch.isBlank() ||
            product.manufacturer.isBlank() ||
            product.packageWeight <= 0.0
        ) {
            return "id:${product.id}"
        }
        return ShipmentCalculator.batchKey(product)
    }

    /** Parsed draft for simplified counter; null if empty/invalid/non-positive. */
    private fun parseQuickPlacesDraft(product: Product): Double? {
        val parsed = QuantityFormatters.parseDecimalInput(quickPlacesTextFor(product)) ?: return null
        return parsed.takeIf { it > 0.0 }
    }

    private fun rejectQuickPlacesRequired() {
        vibrateShort()
        viewModelScope.launch {
            _events.emit(
                ShipmentUiEvent.Toast(
                    app.getString(R.string.quick_pallet_places_required),
                    isError = true
                )
            )
        }
    }

    fun addPallet(productId: Long, focusAfter: Boolean = false) {
        flushPendingForecast(productId)
        val s = settings.value
        if (s.simplifiedCounterEnabled) {
            stampNextPlaceholderOrAdd(productId)
        } else {
            addPalletWithPlaces(productId, places = 0.0, focusAfter = focusAfter)
        }
    }

    /**
     * Simplified counter: stamp next forecast placeholder with draft places, or add a new real
     * pallet (first add with places triggers forecast like typing on the first row).
     * @return false if draft places were missing/invalid (toast already shown).
     */
    private fun stampNextPlaceholderOrAdd(productId: Long): Boolean {
        val product = findProduct(productId) ?: return false
        val places = parseQuickPlacesDraft(product)
        if (places == null) {
            rejectQuickPlacesRequired()
            return false
        }
        if (_payload.value.palletForecastEnabled && product.pallets.any { it.isPlaceholder }) {
            val lastRealIndex = product.pallets.indexOfLast { !it.isPlaceholder }
            val next = product.pallets.getOrNull(lastRealIndex + 1)
                ?: product.pallets.firstOrNull { it.isPlaceholder }
            if (next != null) {
                touchLastUsedProduct(productId)
                updatePalletPlaces(productId, next.id, places)
                return true
            }
        }
        touchLastUsedProduct(productId)
        addPalletWithPlaces(productId, places, focusAfter = false)
        return true
    }

    private fun batchPlacesUsed(payload: ShipmentPayload, product: Product): Double {
        val key = ShipmentCalculator.batchKey(product)
        return ShipmentCalculator.allProducts(payload)
            .filter { ShipmentCalculator.batchKey(it) == key }
            .sumOf { ShipmentCalculator.placesForProduct(it, payload.doubleControlEnabled) }
    }

    private fun emitBatchLimitAlert(product: Product, availablePlaces: Double) {
        vibrateShort()
        viewModelScope.launch {
            _events.emit(
                ShipmentUiEvent.Alert(
                    app.getString(
                        R.string.batch_limit_exceeded,
                        product.batch.ifBlank { product.name }.ifBlank { "—" },
                        QuantityFormatters.formatCount(availablePlaces.coerceAtLeast(0.0))
                    )
                )
            )
        }
    }

    private fun addPalletWithPlaces(productId: Long, places: Double, focusAfter: Boolean) {
        flushPendingPlacesLog()
        val s = settings.value
        val product = findProduct(productId) ?: return

        if (places > 0.0 && !ShipmentCalculator.canAddPlaces(_payload.value, product, places)) {
            val limit = _payload.value.batchLimits.find {
                ShipmentCalculator.batchKey(it) == ShipmentCalculator.batchKey(product)
            }
            val used = batchPlacesUsed(_payload.value, product)
            val avail = (limit?.plannedPlaces ?: 0.0) - used
            emitBatchLimitAlert(product, avail)
            return
        }

        fun apply() {
            forecastAppliedSignature.remove(productId)
            var newPalletId: Long? = null
            var newPalletNumber = 0
            val productName = findProduct(productId)?.name.orEmpty()
            var realCountAfter = 0
            mutateProductPallet(productId) { p ->
                val real = ShipmentCalculator.realPallets(p)
                val nextNum = real.size + 1
                val newPallet = Pallet(palletNumber = nextNum, places = places)
                newPalletId = newPallet.id
                newPalletNumber = nextNum
                // Drop current placeholders; first pallet with places may re-forecast below.
                val next = p.copy(pallets = real + newPallet)
                realCountAfter = ShipmentCalculator.realPallets(next).size
                next
            }
            logEvent(
                ShipmentEventType.PALLET_ADDED,
                app.getString(
                    R.string.history_msg_pallet_added,
                    newPalletNumber,
                    productLabel(productName)
                )
            )
            if (places > 0.0) {
                newPalletId?.let { palletId ->
                    schedulePlacesLog(
                        productId = productId,
                        palletId = palletId,
                        palletNumber = newPalletNumber,
                        productName = productName,
                        oldPlaces = 0.0,
                        newPlaces = places
                    )
                }
            }
            if (_payload.value.batchControlEnabled &&
                ShipmentCalculator.batchStatuses(_payload.value, _payload.value.batchWarnThreshold)
                    .any { it.key == ShipmentCalculator.batchKey(product) && it.exhausted }
            ) {
                vibrateShort()
            }
            if (places > 0.0 &&
                _payload.value.palletForecastEnabled &&
                realCountAfter == 1
            ) {
                scheduleForecastIfEligible(productId)
            }
            if (focusAfter && places == 0.0) {
                notifyFabSuccess()
                val palletId = newPalletId ?: return
                if (shouldFocusAfterFab(productId)) {
                    viewModelScope.launch {
                        _events.emit(ShipmentUiEvent.FocusPalletPlaces(productId, palletId))
                    }
                }
            }
        }

        if (places > 0.0 &&
            s.inputGuardEnabled &&
            s.maxPlacesPerPallet > 0 &&
            places > s.maxPlacesPerPallet &&
            guardKey("places", "new:$productId") !in confirmedGuards
        ) {
            viewModelScope.launch {
                _events.emit(
                    ShipmentUiEvent.GuardConfirm("places", QuantityFormatters.formatCount(places)) {
                        confirmedGuards += guardKey("places", "new:$productId")
                        viewModelScope.launch {
                            repo.log(
                                _sessionKey.value,
                                ShipmentEventType.INPUT_GUARD_CONFIRMED,
                                app.getString(
                                    R.string.history_msg_guard_places,
                                    QuantityFormatters.formatCount(places)
                                )
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

    fun updatePalletPlaces(productId: Long, palletId: Long, places: Double) {
        val settings = settings.value
        val apply = {
            var shouldSchedule = false
            mutateProductPallet(productId) { product ->
                val target = product.pallets.find { it.id == palletId } ?: return@mutateProductPallet product
                val oldPlaces = target.places
                val delta = places - oldPlaces
                if (!ShipmentCalculator.canAddPlaces(_payload.value, product, delta)) {
                    val limit = _payload.value.batchLimits.find {
                        ShipmentCalculator.batchKey(it) == ShipmentCalculator.batchKey(product)
                    }
                    val used = batchPlacesUsed(_payload.value, product)
                    val avail = (limit?.plannedPlaces ?: 0.0) - used + oldPlaces
                    emitBatchLimitAlert(product, avail)
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
            guardKey("places", "$productId:$palletId") !in confirmedGuards
        ) {
            viewModelScope.launch {
                _events.emit(
                    ShipmentUiEvent.GuardConfirm("places", QuantityFormatters.formatCount(places)) {
                        confirmedGuards += guardKey("places", "$productId:$palletId")
                        viewModelScope.launch {
                            repo.log(
                                _sessionKey.value,
                                ShipmentEventType.INPUT_GUARD_CONFIRMED,
                                app.getString(
                                    R.string.history_msg_guard_places,
                                    QuantityFormatters.formatCount(places)
                                )
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
                QuantityFormatters.formatCount(pallet.places)
            )
        )
    }

    fun deletePallet(productId: Long, palletId: Long) {
        placesLogJobs.remove(palletId)?.cancel()
        pendingPlacesLogs.remove(palletId)
        flushPendingPlacesLog()
        var removedPlaceholder = false
        var removedNumber = 0
        var removedPlaces = 0.0
        val productName = findProduct(productId)?.name.orEmpty()
        mutateProductPallet(productId) { product ->
            val removed = product.pallets.find { it.id == palletId }
            removedPlaceholder = removed?.isPlaceholder == true
            removedNumber = removed?.palletNumber ?: 0
            removedPlaces = removed?.places ?: 0.0
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
                    QuantityFormatters.formatCount(removedPlaces)
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

    /** Smart FAB: finish current context, then sibling product, then next block, else toast. */
    fun smartAddPallet() {
        val p = _payload.value
        when (p.mode) {
            ShipmentMode.MONO -> {
                if (p.products.isEmpty()) {
                    addProduct()
                    return
                }
                val target = pickFabProduct(
                    blocks = listOf(p.products),
                    stickyProductId = p.lastUsedProductId,
                    forecastEnabled = p.palletForecastEnabled
                )
                if (target != null) {
                    focusOrAddPallet(target.id)
                } else {
                    emitAllFullToast()
                }
            }
            ShipmentMode.MULTI_VEHICLE -> {
                if (p.multiVehicles.isEmpty()) {
                    addVehicle()
                    return
                }
                val ordered = stickyOrder(p.multiVehicles, p.lastUsedVehicleId) { it.id }
                val target = pickFabProduct(
                    blocks = ordered.map { it.products },
                    stickyProductId = p.lastUsedProductId,
                    forecastEnabled = p.palletForecastEnabled
                )
                if (target != null) {
                    focusOrAddPallet(target.id)
                } else {
                    emitAllFullToast()
                }
            }
            ShipmentMode.MULTI_PORT -> {
                if (p.multiPorts.isEmpty()) {
                    addPortGroup()
                    return
                }
                val ordered = stickyOrder(p.multiPorts, p.lastUsedPortId) { it.id }
                val target = pickFabProduct(
                    blocks = ordered.map { it.products },
                    stickyProductId = p.lastUsedProductId,
                    forecastEnabled = p.palletForecastEnabled
                )
                if (target != null) {
                    focusOrAddPallet(target.id)
                } else {
                    emitAllFullToast()
                }
            }
            ShipmentMode.UNLOAD -> {
                if (p.unloadReceptions.isEmpty()) {
                    addUnloadReception()
                    return
                }
                val ordered = stickyOrder(p.unloadReceptions, p.lastUsedUnloadReceptionId) { it.id }
                // Within a reception: walk inbounds top-to-bottom; sticky product first if present.
                val blocks = ordered.map { reception ->
                    reception.inbounds.flatMap { it.products }
                }
                val target = pickFabProduct(
                    blocks = blocks,
                    stickyProductId = p.lastUsedProductId,
                    forecastEnabled = p.palletForecastEnabled
                )
                if (target != null) {
                    focusOrAddPallet(target.id)
                } else {
                    emitAllFullToast()
                }
            }
        }
    }

    /**
     * Prefer sticky (last-used) product/block while it still has work.
     * Neighbors with quantity==0 do not steal the FAB.
     * FAB fill uses all real pallet places (ignore DC import flags).
     */
    private fun pickFabProduct(
        blocks: List<List<Product>>,
        stickyProductId: Long?,
        forecastEnabled: Boolean
    ): Product? {
        fun productsInBlock(block: List<Product>): List<Product> =
            stickyOrder(block, stickyProductId) { it.id }

        stickyProductId?.let { id ->
            val sticky = blocks.flatten().find { it.id == id }
            if (sticky != null) {
                if (forecastEnabled && fabHasPlaceholders(sticky)) return sticky
                if (fabHasRoomSticky(sticky)) return sticky
            }
        }

        if (forecastEnabled) {
            for (block in blocks) {
                for (product in productsInBlock(block)) {
                    if (product.id == stickyProductId) continue
                    if (fabHasPlaceholders(product)) return product
                }
            }
        }
        for (block in blocks) {
            for (product in productsInBlock(block)) {
                if (product.id == stickyProductId) continue
                if (fabHasRoomNeighbor(product)) return product
            }
        }
        return null
    }

    private fun <T> stickyOrder(items: List<T>, stickyId: Long?, idOf: (T) -> Long): List<T> {
        if (stickyId == null || items.isEmpty()) return items
        val sticky = items.find { idOf(it) == stickyId } ?: return items
        return listOf(sticky) + items.filter { idOf(it) != stickyId }
    }

    private fun fabHasPlaceholders(product: Product): Boolean =
        product.pallets.any { it.isPlaceholder }

    /** Physical remainder ignoring double-control import flags. */
    private fun fabPhysicalRemainder(product: Product): Double =
        ShipmentCalculator.remainder(product, doubleControl = false, unload = false)

    private fun fabHasRoomSticky(product: Product): Boolean =
        product.quantity == 0 || fabPhysicalRemainder(product) > 0

    /** Neighbors without a quantity plan must not capture the FAB. */
    private fun fabHasRoomNeighbor(product: Product): Boolean =
        product.quantity > 0 && fabPhysicalRemainder(product) > 0

    private fun emitAllFullToast() {
        viewModelScope.launch {
            _events.emit(
                ShipmentUiEvent.Toast(
                    getApplication<Application>().getString(R.string.all_transports_full),
                    isError = true
                )
            )
        }
    }

    private fun flushPendingForecast(productId: Long) {
        val job = forecastJobs.remove(productId) ?: return
        job.cancel()
        applyForecastNow(productId)
        if (pendingForecastProductId == productId) pendingForecastProductId = null
        if (forecastJobs.isEmpty()) {
            viewModelScope.launch {
                _events.emit(ShipmentUiEvent.ForecastRunning(false))
            }
        }
    }

    /**
     * FAB: if forecast placeholders exist, focus the next pallet after the last real one
     * (keep placeholders). Otherwise add a new real pallet and focus it.
     * With simplified counter: stamp draft places onto next placeholder or add first + forecast.
     * Focus/scroll only when the places field of this product is focused and DC is off.
     */
    private fun focusOrAddPallet(productId: Long) {
        flushPendingForecast(productId)
        val product = ShipmentCalculator.allProducts(_payload.value).find { it.id == productId }
            ?: return
        val s = settings.value
        if (s.simplifiedCounterEnabled) {
            if (stampNextPlaceholderOrAdd(productId)) notifyFabSuccess()
            return
        }
        if (_payload.value.palletForecastEnabled && product.pallets.any { it.isPlaceholder }) {
            val lastRealIndex = product.pallets.indexOfLast { !it.isPlaceholder }
            val next = product.pallets.getOrNull(lastRealIndex + 1)
                ?: product.pallets.firstOrNull { it.isPlaceholder }
            if (next != null) {
                touchLastUsedProduct(productId)
                notifyFabSuccess()
                if (shouldFocusAfterFab(productId)) {
                    viewModelScope.launch {
                        _events.emit(ShipmentUiEvent.FocusPalletPlaces(productId, next.id))
                    }
                }
                return
            }
        }
        addPalletWithPlaces(productId, places = 0.0, focusAfter = true)
    }

    fun setPalletPlacesFocused(productId: Long, focused: Boolean) {
        if (focused) {
            focusedPalletProductId = productId
        } else if (focusedPalletProductId == productId) {
            focusedPalletProductId = null
        }
    }

    private fun notifyFabSuccess() {
        _fabSuccessTick.update { it + 1 }
    }

    private fun shouldFocusAfterFab(productId: Long): Boolean {
        if (effectiveDoubleControlForProduct(productId)) return false
        return focusedPalletProductId == productId
    }

    private fun effectiveDoubleControlForProduct(productId: Long): Boolean {
        val p = _payload.value
        return when (p.mode) {
            ShipmentMode.MONO -> p.doubleControlEnabled
            ShipmentMode.MULTI_VEHICLE -> {
                val vehicle = p.multiVehicles.find { v -> v.products.any { it.id == productId } }
                p.doubleControlEnabled || (vehicle?.doubleControlEnabled == true)
            }
            ShipmentMode.MULTI_PORT -> {
                val group = p.multiPorts.find { g -> g.products.any { it.id == productId } }
                p.doubleControlEnabled || (group?.doubleControlEnabled == true)
            }
            ShipmentMode.UNLOAD -> false
        }
    }

    private fun touchLastUsedProduct(productId: Long) {
        update { payload ->
            when (payload.mode) {
                ShipmentMode.MONO -> payload.copy(lastUsedProductId = productId)
                ShipmentMode.MULTI_VEHICLE -> {
                    val vehicleId = payload.multiVehicles.find { v ->
                        v.products.any { it.id == productId }
                    }?.id
                    payload.copy(
                        lastUsedProductId = productId,
                        lastUsedVehicleId = vehicleId ?: payload.lastUsedVehicleId
                    )
                }
                ShipmentMode.MULTI_PORT -> {
                    val portId = payload.multiPorts.find { g ->
                        g.products.any { it.id == productId }
                    }?.id
                    payload.copy(
                        lastUsedProductId = productId,
                        lastUsedPortId = portId ?: payload.lastUsedPortId
                    )
                }
                ShipmentMode.UNLOAD -> {
                    val receptionId = payload.unloadReceptions.find { r ->
                        r.inbounds.any { ib -> ib.products.any { it.id == productId } }
                    }?.id
                    payload.copy(
                        lastUsedProductId = productId,
                        lastUsedUnloadReceptionId = receptionId ?: payload.lastUsedUnloadReceptionId
                    )
                }
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
        p.copy(checklist = checklist).withRemindersOffIfChecklistComplete()
    }

    fun addChecklistTask(title: String) = update {
        it.copy(checklist = it.checklist + ChecklistTask(title = title))
    }

    fun deleteChecklistTask(id: Long) = update {
        it.copy(checklist = it.checklist.filter { t -> t.id != id })
            .withRemindersOffIfChecklistComplete()
    }

    fun editChecklistTask(id: Long, title: String) = update {
        it.copy(checklist = it.checklist.map { t -> if (t.id == id) t.copy(title = title) else t })
    }

    /** Auto-disable shipment checklist reminders when every item is done (non-empty list). */
    private fun ShipmentPayload.withRemindersOffIfChecklistComplete(): ShipmentPayload {
        val allDone = checklist.isNotEmpty() && checklist.all { it.isCompleted }
        return if (allDone && checklistReminderEnabled) {
            copy(checklistReminderEnabled = false)
        } else {
            this
        }
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
        if (s.inputGuardEnabled && s.maxPlaceWeightKg > 0 && weight > s.maxPlaceWeightKg &&
            guardKey("weight", "global") !in confirmedGuards
        ) {
            viewModelScope.launch {
                _events.emit(
                    ShipmentUiEvent.GuardConfirm("weight", weight.toString()) {
                        confirmedGuards += guardKey("weight", "global")
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

    private fun guardKey(field: String, scope: String): String = "$field:$scope"

    private fun schedulePlacesLog(
        productId: Long,
        palletId: Long,
        palletNumber: Int,
        productName: String,
        oldPlaces: Double,
        newPlaces: Double
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
                QuantityFormatters.formatCount(pending.oldPlaces),
                QuantityFormatters.formatCount(pending.newPlaces)
            )
        } else {
            app.getString(
                R.string.history_msg_pallet_places,
                pending.palletNumber,
                productLabel(pending.productName),
                QuantityFormatters.formatCount(pending.newPlaces)
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
