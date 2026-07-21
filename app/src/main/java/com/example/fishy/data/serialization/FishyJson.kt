package com.example.fishy.data.serialization

import com.example.fishy.domain.model.Product
import com.example.fishy.domain.model.ShipmentPayload
import com.example.fishy.domain.model.Transport
import com.example.fishy.domain.model.UnloadInbound
import com.example.fishy.domain.model.UnloadReception
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

object FishyJson {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encodePayload(payload: ShipmentPayload): String = json.encodeToString(payload)

    fun decodePayload(raw: String): ShipmentPayload {
        if (raw.isBlank()) return ShipmentPayload()
        val root = json.parseToJsonElement(raw).jsonObject
        val payload = json.decodeFromString<ShipmentPayload>(raw)
        if (payload.unloadReceptions.isNotEmpty()) return payload
        val legacy = root["unloadSources"] ?: return payload
        val sources = runCatching {
            json.decodeFromJsonElement<List<LegacyUnloadSource>>(legacy)
        }.getOrElse { return payload }
        if (sources.isEmpty()) return payload
        return payload.copy(unloadReceptions = migrateLegacyUnload(sources))
    }

    fun decodePayloadOrNull(raw: String): ShipmentPayload? =
        runCatching { decodePayload(raw) }.getOrNull()

    private fun migrateLegacyUnload(sources: List<LegacyUnloadSource>): List<UnloadReception> {
        val receptions = mutableListOf<UnloadReception>()
        sources.forEach { src ->
            if (src.warehouses.isEmpty()) {
                receptions += UnloadReception(
                    name = src.warehouseName,
                    transport = Transport(),
                    inbounds = listOf(
                        UnloadInbound(
                            transport = src.transport,
                            vessel = src.vessel,
                            products = src.products.ifEmpty { listOf(Product()) }
                        )
                    )
                )
            } else {
                src.warehouses.forEach { wh ->
                    receptions += UnloadReception(
                        name = wh.name.ifBlank { src.warehouseName },
                        transport = wh.transport,
                        inbounds = listOf(
                            UnloadInbound(
                                transport = src.transport,
                                vessel = src.vessel,
                                products = wh.products.ifEmpty { listOf(Product()) }
                            )
                        )
                    )
                }
                if (src.products.isNotEmpty()) {
                    receptions += UnloadReception(
                        name = src.warehouseName,
                        transport = Transport(),
                        inbounds = listOf(
                            UnloadInbound(
                                transport = src.transport,
                                vessel = src.vessel,
                                products = src.products
                            )
                        )
                    )
                }
            }
        }
        return mergeByDestination(receptions)
    }

    /** Merge receptions that share the same wagon/container or name so 3 trucks → 2 wagons coalesce. */
    private fun mergeByDestination(items: List<UnloadReception>): List<UnloadReception> {
        if (items.isEmpty()) return items
        val map = linkedMapOf<String, UnloadReception>()
        items.forEach { r ->
            val key = destinationKey(r)
            val existing = map[key]
            if (existing == null) {
                map[key] = r
            } else {
                map[key] = existing.copy(
                    name = existing.name.ifBlank { r.name },
                    transport = if (isBlankTransport(existing.transport)) r.transport else existing.transport,
                    inbounds = existing.inbounds + r.inbounds
                )
            }
        }
        return map.values.toList()
    }

    private fun destinationKey(r: UnloadReception): String {
        val t = r.transport
        return when {
            t.wagonNumber.isNotBlank() -> "w:${t.wagonNumber}"
            t.containerNumber.isNotBlank() -> "c:${t.containerNumber}"
            t.truckNumber.isNotBlank() || t.trailerNumber.isNotBlank() ->
                "v:${t.truckNumber}|${t.trailerNumber}"
            r.name.isNotBlank() -> "n:${r.name}"
            else -> "id:${r.id}"
        }
    }

    private fun isBlankTransport(t: Transport): Boolean =
        t.wagonNumber.isBlank() && t.containerNumber.isBlank() &&
            t.truckNumber.isBlank() && t.trailerNumber.isBlank() && t.sealNumber.isBlank()

    @Serializable
    private data class LegacyUnloadWarehouse(
        val id: Long = 0,
        val name: String = "",
        val transport: Transport = Transport(),
        val products: List<Product> = emptyList()
    )

    @Serializable
    private data class LegacyUnloadSource(
        val id: Long = 0,
        val transport: Transport = Transport(),
        val vessel: String = "",
        val warehouseName: String = "",
        val warehouses: List<LegacyUnloadWarehouse> = emptyList(),
        val products: List<Product> = emptyList()
    )
}
