package com.example.fishy.data.repo

import com.example.fishy.data.local.FishyDatabase
import com.example.fishy.data.local.entity.ChecklistItemEntity
import com.example.fishy.data.local.entity.DictionaryEntity
import com.example.fishy.data.local.entity.ReportTemplateEntity
import com.example.fishy.data.local.entity.ScheduledReminderEntity
import com.example.fishy.data.local.entity.ScheduledShipmentEntity
import com.example.fishy.data.local.entity.ShipmentEntity
import com.example.fishy.data.local.entity.ShipmentEventEntity
import com.example.fishy.data.serialization.FishyJson
import com.example.fishy.domain.calc.ShipmentCalculator
import com.example.fishy.domain.model.DictionaryType
import com.example.fishy.domain.model.ShipmentDuplicator
import com.example.fishy.domain.model.ShipmentEventType
import com.example.fishy.domain.model.ShipmentPayload
import com.example.fishy.domain.model.ShipmentSummaries
import com.example.fishy.domain.report.ReportTemplate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

class FishyRepository(private val db: FishyDatabase) {

    private val shipments = db.shipmentDao()
    private val scheduled = db.scheduledShipmentDao()
    private val dictionary = db.dictionaryDao()
    private val events = db.eventDao()
    private val templates = db.reportTemplateDao()

    fun observeArchive(): Flow<List<ShipmentEntity>> = shipments.observeArchive()
    fun observeDrafts(): Flow<List<ShipmentEntity>> = shipments.observeDrafts()
    suspend fun getShipment(id: Long) = shipments.getById(id)
    suspend fun getDrafts() = shipments.getDrafts()

    suspend fun saveDraft(id: Long?, name: String, payload: ShipmentPayload): Long {
        val totals = ShipmentCalculator.totals(payload)
        val existingId = id?.takeIf { it != 0L } ?: 0L
        val entity = ShipmentEntity(
            id = existingId,
            payloadJson = FishyJson.encodePayload(payload),
            customer = payload.customer,
            port = summaryPort(payload),
            mode = payload.mode.name,
            totalPlaces = totals.places.roundToInt(),
            totalWeight = totals.actualWeight,
            transportSummary = payload.transport.containerNumber.ifBlank {
                payload.transport.wagonNumber.ifBlank { payload.transport.truckNumber }
            },
            createdAtMillis = payload.createdAtMillis,
            completedAtMillis = System.currentTimeMillis(),
            isDraft = true,
            draftName = name.ifBlank { "Черновик" }
        )
        val rowId = shipments.upsert(entity)
        return if (existingId != 0L) existingId else rowId
    }

    suspend fun completeShipment(id: Long?, payload: ShipmentPayload): Long {
        val completed = payload.copy(completedAtMillis = System.currentTimeMillis())
        val totals = ShipmentCalculator.totals(completed)
        val existingId = id?.takeIf { it != 0L } ?: 0L
        val entity = ShipmentEntity(
            id = existingId,
            payloadJson = FishyJson.encodePayload(completed),
            customer = completed.customer,
            port = summaryPort(completed),
            mode = completed.mode.name,
            totalPlaces = totals.places.roundToInt(),
            totalWeight = totals.actualWeight,
            transportSummary = completed.transport.containerNumber.ifBlank {
                completed.transport.wagonNumber.ifBlank { completed.transport.truckNumber }
            },
            createdAtMillis = completed.createdAtMillis,
            completedAtMillis = completed.completedAtMillis!!,
            isDraft = false,
            draftName = ""
        )
        val rowId = shipments.upsert(entity)
        val savedId = if (existingId != 0L) existingId else rowId
        rememberDictionaryValues(completed)
        return savedId
    }

    /** Update an already-archived shipment (report edit/reset) without lifecycle events. */
    suspend fun updateArchivedShipment(id: Long, payload: ShipmentPayload) {
        val existing = getShipment(id) ?: return
        val totals = ShipmentCalculator.totals(payload)
        val preservedCompletedAt = existing.completedAtMillis
        val updatedPayload = payload.copy(completedAtMillis = preservedCompletedAt)
        shipments.upsert(
            existing.copy(
                payloadJson = FishyJson.encodePayload(updatedPayload),
                customer = updatedPayload.customer,
                port = summaryPort(updatedPayload),
                mode = updatedPayload.mode.name,
                totalPlaces = totals.places.roundToInt(),
                totalWeight = totals.actualWeight,
                transportSummary = updatedPayload.transport.containerNumber.ifBlank {
                    updatedPayload.transport.wagonNumber.ifBlank { updatedPayload.transport.truckNumber }
                },
                createdAtMillis = updatedPayload.createdAtMillis,
                completedAtMillis = preservedCompletedAt,
                isDraft = false,
                draftName = ""
            )
        )
    }

    suspend fun deleteShipment(id: Long) {
        shipments.deleteById(id)
        events.deleteByKey(id.toString())
        events.deleteByKey("draft_$id")
    }

    suspend fun duplicateShipmentAsDraft(sourceId: Long, draftName: String): Long {
        val entity = getShipment(sourceId) ?: throw IllegalArgumentException("Shipment $sourceId not found")
        val copy = ShipmentDuplicator.forNewDraft(FishyJson.decodePayload(entity.payloadJson))
        val newId = saveDraft(null, draftName, copy)
        val logKey = if (entity.isDraft) "draft#$sourceId" else "archive#$sourceId"
        log("draft_$newId", ShipmentEventType.DUPLICATED, logKey)
        return newId
    }

    suspend fun filterStats(from: Long, to: Long, customer: String, port: String) =
        shipments.filterStats(from, to, customer, port)

    fun observeScheduled() = scheduled.observeAll()
    suspend fun getScheduled(id: Long) = scheduled.getById(id)
    suspend fun upsertScheduled(entity: ScheduledShipmentEntity) = scheduled.upsert(entity)
    suspend fun deleteScheduled(id: Long) {
        scheduled.deleteChecklistForShipment(id)
        scheduled.deleteRemindersForShipment(id)
        scheduled.deleteById(id)
    }
    suspend fun markScheduledCompleted(id: Long) {
        val entity = scheduled.getById(id) ?: return
        if (entity.isCompleted) return
        scheduled.update(entity.copy(isCompleted = true, updatedAtMillis = System.currentTimeMillis()))
    }
    suspend fun pendingNotifications() = scheduled.getPendingNotifications()
    suspend fun allScheduledIds() = scheduled.getAllIds()
    suspend fun updateScheduled(entity: ScheduledShipmentEntity) = scheduled.update(entity)

    /** Wipe all Room tables (archives, drafts, schedule, dictionary, events, templates). */
    suspend fun wipeAll() {
        db.clearAllTables()
    }

    fun observeChecklist(scheduledId: Long) = scheduled.observeChecklist(scheduledId)
    suspend fun getChecklist(scheduledId: Long) = scheduled.getChecklist(scheduledId)
    suspend fun upsertChecklistItem(item: ChecklistItemEntity) = scheduled.upsertChecklistItem(item)
    suspend fun deleteChecklistItem(id: Long) = scheduled.deleteChecklistItem(id)

    fun observeReminders(scheduledId: Long) = scheduled.observeReminders(scheduledId)
    suspend fun getReminders(scheduledId: Long) = scheduled.getReminders(scheduledId)
    suspend fun getReminder(id: Long) = scheduled.getReminderById(id)
    suspend fun upsertReminder(item: ScheduledReminderEntity) = scheduled.upsertReminder(item)
    suspend fun deleteReminder(id: Long) = scheduled.deleteReminder(id)

    /**
     * Removes sent or past prep reminders. If none remain, turns off [ScheduledShipmentEntity.notificationEnabled].
     * @return updated shipment entity from DB (or null if missing)
     */
    suspend fun pruneReminders(shipmentId: Long, nowMillis: Long = System.currentTimeMillis()): ScheduledShipmentEntity? {
        scheduled.pruneDueReminders(shipmentId)
        return syncNotificationFlagAfterReminderChange(shipmentId)
    }

    /**
     * After catch-up: drop all past reminders (sent or not — e.g. notify was off).
     * Future unsent rows stay for the next arm.
     */
    suspend fun pruneRemindersAfterCatchUp(
        shipmentId: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): ScheduledShipmentEntity? {
        scheduled.deletePastReminders(shipmentId, nowMillis)
        return syncNotificationFlagAfterReminderChange(shipmentId)
    }

    private suspend fun syncNotificationFlagAfterReminderChange(shipmentId: Long): ScheduledShipmentEntity? {
        val remaining = scheduled.getReminders(shipmentId)
        val entity = scheduled.getById(shipmentId) ?: return null
        if (remaining.isEmpty() && entity.notificationEnabled) {
            val updated = entity.copy(
                notificationEnabled = false,
                updatedAtMillis = System.currentTimeMillis()
            )
            scheduled.update(updated)
            return updated
        }
        return entity
    }

    suspend fun getActiveScheduled() = scheduled.getActiveScheduled()

    /**
     * Replaces all prep reminders for [shipmentId].
     * Preserves [ScheduledReminderEntity.sent] when the same id keeps the same [atMillis].
     */
    suspend fun replaceReminders(shipmentId: Long, drafts: List<ScheduledReminderEntity>) {
        val existing = scheduled.getReminders(shipmentId).associateBy { it.id }
        scheduled.deleteRemindersForShipment(shipmentId)
        drafts.forEachIndexed { index, draft ->
            val prev = existing[draft.id]
            val sent = when {
                prev == null -> false
                prev.atMillis != draft.atMillis -> false
                else -> prev.sent
            }
            scheduled.upsertReminder(
                ScheduledReminderEntity(
                    id = 0,
                    scheduledShipmentId = shipmentId,
                    atMillis = draft.atMillis,
                    sent = sent,
                    sortOrder = index
                )
            )
        }
    }

    fun observeDictionary(type: DictionaryType) = dictionary.observeByType(type.key)
    suspend fun addDictionary(type: DictionaryType, value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return
        val now = System.currentTimeMillis()
        val existing = dictionary.findByTypeAndValue(type.key, trimmed)
        if (existing != null) {
            dictionary.insert(existing.copy(lastUsedAtMillis = now))
        } else {
            dictionary.insert(
                DictionaryEntity(type = type.key, value = trimmed, lastUsedAtMillis = now)
            )
        }
    }

    suspend fun deleteDictionary(id: Long) = dictionary.deleteById(id)

    suspend fun upsertDictionary(entity: DictionaryEntity) {
        val trimmed = entity.value.trim()
        if (trimmed.isEmpty()) return
        val now = System.currentTimeMillis()
        val existing = dictionary.findByTypeAndValue(entity.type, trimmed)
        if (existing != null && existing.id != entity.id) {
            // Rename collided with another row — keep the other, drop this id.
            if (entity.id > 0L) dictionary.deleteById(entity.id)
            dictionary.insert(existing.copy(lastUsedAtMillis = now))
            return
        }
        dictionary.insert(
            entity.copy(
                value = trimmed,
                lastUsedAtMillis = if (entity.lastUsedAtMillis > 0L) entity.lastUsedAtMillis else now
            )
        )
    }

    suspend fun rememberDictionaryValues(payload: ShipmentPayload) {
        addDictionary(DictionaryType.CUSTOMER, payload.customer)
        addDictionary(DictionaryType.PORT, payload.port)
        addDictionary(DictionaryType.VESSEL, payload.vessel)
        payload.multiPorts.forEach { group ->
            addDictionary(DictionaryType.PORT, group.port)
            addDictionary(DictionaryType.VESSEL, group.vessel)
        }
        payload.unloadReceptions.forEach { reception ->
            reception.inbounds.forEach { inbound ->
                addDictionary(DictionaryType.PORT, inbound.port)
                addDictionary(DictionaryType.VESSEL, inbound.vessel)
            }
        }
        ShipmentCalculator.allProducts(payload).forEach { p ->
            addDictionary(DictionaryType.PRODUCT, p.name)
            addDictionary(DictionaryType.MANUFACTURER, p.manufacturer)
        }
    }

    fun observeEvents(key: String) = events.observeForKey(key)

    fun observeDuplicatedDraftKeys(): Flow<Set<String>> =
        events.observeKeysByType(ShipmentEventType.DUPLICATED.name).map { it.toSet() }

    suspend fun rekeyEvents(oldKey: String, newKey: String) {
        if (oldKey.isBlank() || newKey.isBlank() || oldKey == newKey) return
        events.rekey(oldKey, newKey)
    }

    suspend fun deleteEvents(key: String) {
        if (key.isBlank()) return
        events.deleteByKey(key)
    }

    suspend fun log(shipmentKey: String, type: ShipmentEventType, message: String) {
        events.insert(
            ShipmentEventEntity(
                shipmentKey = shipmentKey,
                type = type.name,
                message = message
            )
        )
    }

    fun observeTemplates(): Flow<List<ReportTemplate>> =
        templates.observeAll().map { list ->
            list.map {
                ReportTemplate(it.id, it.name, it.body, it.customerBinding)
            }
        }

    suspend fun upsertTemplate(template: ReportTemplate): Long =
        templates.upsert(
            ReportTemplateEntity(
                id = template.id,
                name = template.name,
                body = template.body,
                customerBinding = template.customerBinding
            )
        )

    suspend fun deleteTemplate(id: Long) = templates.deleteById(id)

    suspend fun templateForCustomer(customer: String): ReportTemplate? =
        templates.getForCustomer(customer)?.let {
            ReportTemplate(it.id, it.name, it.body, it.customerBinding)
        }

    private fun summaryPort(payload: ShipmentPayload): String =
        ShipmentSummaries.ports(payload).joinToString(", ").ifBlank { payload.port.trim() }
}
