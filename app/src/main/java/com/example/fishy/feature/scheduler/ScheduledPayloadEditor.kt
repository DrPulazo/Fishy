package com.example.fishy.feature.scheduler

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import com.example.fishy.ui.components.FishyButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.fishy.R
import com.example.fishy.data.local.entity.DictionaryEntity
import com.example.fishy.data.serialization.FishyJson
import com.example.fishy.domain.model.DictionaryType
import com.example.fishy.domain.model.PortGroup
import com.example.fishy.domain.model.Product
import com.example.fishy.domain.model.ShipmentMode
import com.example.fishy.domain.model.ShipmentPayload
import com.example.fishy.domain.model.UnloadInbound
import com.example.fishy.domain.model.UnloadReception
import com.example.fishy.domain.model.VehicleGroup
import com.example.fishy.ui.components.AccordionCard
import com.example.fishy.ui.components.DictionaryAutocomplete
import com.example.fishy.ui.components.TransportFields
import com.example.fishy.ui.components.transportTitle
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun emptyUnloadReception() = UnloadReception(
    inbounds = listOf(UnloadInbound(products = listOf(Product())))
)

fun emptyPayloadForMode(mode: ShipmentMode): ShipmentPayload {
    return when (mode) {
        ShipmentMode.MONO -> ShipmentPayload(
            mode = mode,
            products = listOf(Product())
        )
        ShipmentMode.MULTI_VEHICLE -> ShipmentPayload(
            mode = mode,
            multiVehicles = listOf(VehicleGroup(products = listOf(Product())))
        )
        ShipmentMode.MULTI_PORT -> ShipmentPayload(
            mode = mode,
            multiPorts = listOf(PortGroup(products = listOf(Product())))
        )
        ShipmentMode.UNLOAD -> ShipmentPayload(
            mode = mode,
            unloadReceptions = listOf(emptyUnloadReception())
        )
    }
}

fun ensurePayloadStructure(payload: ShipmentPayload): ShipmentPayload {
    return when (payload.mode) {
        ShipmentMode.MONO -> if (payload.products.isEmpty()) {
            payload.copy(products = listOf(Product()))
        } else payload
        ShipmentMode.MULTI_VEHICLE -> if (payload.multiVehicles.isEmpty()) {
            payload.copy(multiVehicles = listOf(VehicleGroup(products = listOf(Product()))))
        } else payload.copy(
            multiVehicles = payload.multiVehicles.map { vg ->
                if (vg.products.isEmpty()) vg.copy(products = listOf(Product())) else vg
            }
        )
        ShipmentMode.MULTI_PORT -> if (payload.multiPorts.isEmpty()) {
            payload.copy(multiPorts = listOf(PortGroup(products = listOf(Product()))))
        } else payload.copy(
            multiPorts = payload.multiPorts.map { pg ->
                if (pg.products.isEmpty()) pg.copy(products = listOf(Product())) else pg
            }
        )
        ShipmentMode.UNLOAD -> if (payload.unloadReceptions.isEmpty()) {
            payload.copy(unloadReceptions = listOf(emptyUnloadReception()))
        } else payload.copy(
            unloadReceptions = payload.unloadReceptions.map { reception ->
                if (reception.inbounds.isEmpty()) {
                    reception.copy(inbounds = listOf(UnloadInbound(products = listOf(Product()))))
                } else {
                    reception.copy(
                        inbounds = reception.inbounds.map { inbound ->
                            if (inbound.products.isEmpty()) {
                                inbound.copy(products = listOf(Product()))
                            } else inbound
                        }
                    )
                }
            }
        )
    }
}

fun switchPayloadMode(current: ShipmentPayload, newMode: ShipmentMode): ShipmentPayload {
    if (current.mode == newMode) return current
    return emptyPayloadForMode(newMode).copy(
        customer = current.customer,
        port = current.port,
        vessel = current.vessel
    )
}

fun firstProductName(payload: ShipmentPayload): String {
    return when (payload.mode) {
        ShipmentMode.MONO -> payload.products.firstOrNull()?.name
        ShipmentMode.MULTI_VEHICLE -> payload.multiVehicles.firstOrNull()?.products?.firstOrNull()?.name
        ShipmentMode.MULTI_PORT -> payload.multiPorts.firstOrNull()?.products?.firstOrNull()?.name
        ShipmentMode.UNLOAD -> payload.unloadReceptions.firstOrNull()
            ?.inbounds?.firstOrNull()
            ?.products?.firstOrNull()
            ?.name
    }.orEmpty()
}

fun productLabelFromPayloadJson(payloadJson: String): String {
    if (payloadJson.isBlank()) return ""
    return try {
        val element = FishyJson.json.parseToJsonElement(payloadJson).jsonObject
        if (element.containsKey("productName") && !element.containsKey("mode")) {
            return element["productName"]?.jsonPrimitive?.content.orEmpty()
        }
        firstProductName(FishyJson.decodePayload(payloadJson))
    } catch (_: Exception) {
        ""
    }
}

fun decodeScheduledPayload(
    payloadJson: String,
    modeName: String,
    customer: String,
    port: String,
    vessel: String
): ShipmentPayload {
    val entityMode = runCatching { ShipmentMode.valueOf(modeName) }.getOrDefault(ShipmentMode.MONO)
    if (payloadJson.isBlank()) {
        return emptyPayloadForMode(entityMode).copy(
            customer = customer,
            port = port,
            vessel = vessel
        )
    }
    return try {
        val element = FishyJson.json.parseToJsonElement(payloadJson).jsonObject
        if (element.containsKey("productName") && !element.containsKey("mode")) {
            val name = element["productName"]?.jsonPrimitive?.content.orEmpty()
            emptyPayloadForMode(entityMode).copy(
                customer = customer,
                port = port,
                vessel = vessel,
                products = if (entityMode == ShipmentMode.MONO) {
                    listOf(Product(name = name))
                } else emptyList()
            ).let { ensurePayloadStructure(it) }.let { base ->
                when (base.mode) {
                    ShipmentMode.MONO -> base
                    ShipmentMode.MULTI_VEHICLE -> base.copy(
                        multiVehicles = base.multiVehicles.mapIndexed { i, vg ->
                            if (i == 0) vg.copy(products = listOf(Product(name = name))) else vg
                        }
                    )
                    ShipmentMode.MULTI_PORT -> base.copy(
                        multiPorts = base.multiPorts.mapIndexed { i, pg ->
                            if (i == 0) pg.copy(products = listOf(Product(name = name))) else pg
                        }
                    )
                    ShipmentMode.UNLOAD -> base.copy(
                        unloadReceptions = base.unloadReceptions.mapIndexed { i, reception ->
                            if (i == 0) {
                                reception.copy(
                                    inbounds = reception.inbounds.mapIndexed { j, inbound ->
                                        if (j == 0) inbound.copy(products = listOf(Product(name = name)))
                                        else inbound
                                    }
                                )
                            } else reception
                        }
                    )
                }
            }
        } else {
            val decoded = FishyJson.decodePayload(payloadJson)
            ensurePayloadStructure(
                decoded.copy(
                    mode = entityMode,
                    customer = decoded.customer.ifBlank { customer },
                    port = decoded.port.ifBlank { port },
                    vessel = decoded.vessel.ifBlank { vessel }
                )
            )
        }
    } catch (_: Exception) {
        emptyPayloadForMode(entityMode).copy(
            customer = customer,
            port = port,
            vessel = vessel
        )
    }
}

@Composable
fun ProductPrefillFields(
    product: Product,
    productsDict: List<DictionaryEntity>,
    manufacturers: List<DictionaryEntity>,
    onUpdate: ((Product) -> Product) -> Unit,
    onDelete: (() -> Unit)?,
    onAddToDictionary: (DictionaryType, String) -> Unit
) {
    AccordionCard(
        title = when {
            product.name.isBlank() && product.manufacturer.isBlank() -> stringResource(R.string.new_product)
            product.manufacturer.isBlank() -> product.name
            product.name.isBlank() -> product.manufacturer
            else -> "${product.name} - ${product.manufacturer}"
        }
    ) {
        DictionaryAutocomplete(
            label = stringResource(R.string.product),
            value = product.name,
            suggestions = productsDict,
            onValueChange = { v -> onUpdate { it.copy(name = v) } },
            dictionaryType = DictionaryType.PRODUCT,
            onAddToDictionary = onAddToDictionary
        )
        OutlinedTextField(
            value = product.batch,
            onValueChange = { v -> onUpdate { it.copy(batch = v) } },
            label = { Text(stringResource(R.string.batch)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        DictionaryAutocomplete(
            label = stringResource(R.string.manufacturer),
            value = product.manufacturer,
            suggestions = manufacturers,
            onValueChange = { v -> onUpdate { it.copy(manufacturer = v) } },
            dictionaryType = DictionaryType.MANUFACTURER,
            onAddToDictionary = onAddToDictionary
        )
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            OutlinedTextField(
                value = if (product.packageWeight > 0) product.packageWeight.toString() else "",
                onValueChange = { value ->
                    val weight = value.replace(',', '.').toDoubleOrNull() ?: 0.0
                    onUpdate { it.copy(packageWeight = weight) }
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
                    onUpdate { it.copy(quantity = value.toIntOrNull() ?: 0) }
                },
                label = {
                    Text(stringResource(R.string.quantity_short), style = MaterialTheme.typography.bodySmall)
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
        if (onDelete != null) {
            TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
        }
    }
}

@Composable
fun ScheduledPayloadFields(
    payload: ShipmentPayload,
    onChange: (ShipmentPayload) -> Unit,
    customers: List<DictionaryEntity>,
    ports: List<DictionaryEntity>,
    vessels: List<DictionaryEntity>,
    productsDict: List<DictionaryEntity>,
    manufacturers: List<DictionaryEntity>,
    autoSpaceContainers: Boolean,
    autoSpaceVehicles: Boolean,
    onAddToDictionary: (DictionaryType, String) -> Unit
) {
    fun update(block: (ShipmentPayload) -> ShipmentPayload) = onChange(block(payload))

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DictionaryAutocomplete(
            label = stringResource(R.string.customer),
            value = payload.customer,
            suggestions = customers,
            onValueChange = { v -> update { it.copy(customer = v) } },
            dictionaryType = DictionaryType.CUSTOMER,
            onAddToDictionary = onAddToDictionary
        )

        when (payload.mode) {
            ShipmentMode.MONO -> {
                AccordionCard(title = stringResource(R.string.info_loading)) {
                    DictionaryAutocomplete(
                        label = stringResource(R.string.port),
                        value = payload.port,
                        suggestions = ports,
                        onValueChange = { v -> update { it.copy(port = v) } },
                        dictionaryType = DictionaryType.PORT,
                        onAddToDictionary = onAddToDictionary
                    )
                    DictionaryAutocomplete(
                        label = stringResource(R.string.vessel),
                        value = payload.vessel,
                        suggestions = vessels,
                        onValueChange = { v -> update { it.copy(vessel = v) } },
                        dictionaryType = DictionaryType.VESSEL,
                        onAddToDictionary = onAddToDictionary
                    )
                }
                AccordionCard(title = stringResource(R.string.transport_section)) {
                    TransportFields(
                        transport = payload.transport,
                        onChange = { t -> update { it.copy(transport = t) } },
                        autoSpaceContainers = autoSpaceContainers,
                        autoSpaceVehicles = autoSpaceVehicles
                    )
                }
                Text(
                    stringResource(R.string.products_section),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                payload.products.forEach { product ->
                    ProductPrefillFields(
                        product = product,
                        productsDict = productsDict,
                        manufacturers = manufacturers,
                        onUpdate = { transform ->
                            update { p ->
                                p.copy(products = p.products.map {
                                    if (it.id == product.id) transform(it) else it
                                })
                            }
                        },
                        onDelete = {
                            update { p ->
                                val next = p.products.filter { it.id != product.id }
                                p.copy(products = next.ifEmpty { listOf(Product()) })
                            }
                        },
                        onAddToDictionary = onAddToDictionary
                    )
                }
                FishyButton(
                    onClick = { update { it.copy(products = it.products + Product()) } },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.add_product))
                }
            }

            ShipmentMode.MULTI_VEHICLE -> {
                DictionaryAutocomplete(
                    label = stringResource(R.string.port),
                    value = payload.port,
                    suggestions = ports,
                    onValueChange = { v -> update { it.copy(port = v) } },
                    dictionaryType = DictionaryType.PORT,
                    onAddToDictionary = onAddToDictionary
                )
                DictionaryAutocomplete(
                    label = stringResource(R.string.vessel),
                    value = payload.vessel,
                    suggestions = vessels,
                    onValueChange = { v -> update { it.copy(vessel = v) } },
                    dictionaryType = DictionaryType.VESSEL,
                    onAddToDictionary = onAddToDictionary
                )
                payload.multiVehicles.forEach { vehicle ->
                    AccordionCard(title = transportTitle(vehicle.transport)) {
                        AccordionCard(
                            title = stringResource(R.string.transport_section),
                            initiallyExpanded = true
                        ) {
                            TransportFields(
                                transport = vehicle.transport,
                                onChange = { t ->
                                    update { p ->
                                        p.copy(
                                            multiVehicles = p.multiVehicles.map {
                                                if (it.id == vehicle.id) it.copy(transport = t) else it
                                            }
                                        )
                                    }
                                },
                                autoSpaceContainers = autoSpaceContainers,
                                autoSpaceVehicles = autoSpaceVehicles
                            )
                        }
                        AccordionCard(
                            title = stringResource(R.string.products_section),
                            initiallyExpanded = true
                        ) {
                            vehicle.products.forEach { product ->
                                ProductPrefillFields(
                                    product = product,
                                    productsDict = productsDict,
                                    manufacturers = manufacturers,
                                    onUpdate = { transform ->
                                        update { p ->
                                            p.copy(
                                                multiVehicles = p.multiVehicles.map { vg ->
                                                    if (vg.id != vehicle.id) vg
                                                    else vg.copy(
                                                        products = vg.products.map {
                                                            if (it.id == product.id) transform(it) else it
                                                        }
                                                    )
                                                }
                                            )
                                        }
                                    },
                                    onDelete = {
                                        update { p ->
                                            p.copy(
                                                multiVehicles = p.multiVehicles.map { vg ->
                                                    if (vg.id != vehicle.id) vg
                                                    else {
                                                        val next = vg.products.filter { it.id != product.id }
                                                        vg.copy(products = next.ifEmpty { listOf(Product()) })
                                                    }
                                                }
                                            )
                                        }
                                    },
                                    onAddToDictionary = onAddToDictionary
                                )
                            }
                            TextButton(
                                onClick = {
                                    update { p ->
                                        p.copy(
                                            multiVehicles = p.multiVehicles.map {
                                                if (it.id == vehicle.id) {
                                                    it.copy(products = it.products + Product())
                                                } else it
                                            }
                                        )
                                    }
                                }
                            ) { Text(stringResource(R.string.add_product)) }
                        }
                        TextButton(
                            onClick = {
                                update { p ->
                                    val next = p.multiVehicles.filter { it.id != vehicle.id }
                                    p.copy(
                                        multiVehicles = next.ifEmpty {
                                            listOf(VehicleGroup(products = listOf(Product())))
                                        }
                                    )
                                }
                            }
                        ) { Text(stringResource(R.string.delete)) }
                    }
                }
                FishyButton(
                    onClick = {
                        update {
                            it.copy(
                                multiVehicles = it.multiVehicles + VehicleGroup(products = listOf(Product()))
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.add_vehicle)) }
            }

            ShipmentMode.MULTI_PORT -> {
                AccordionCard(title = stringResource(R.string.transport_section)) {
                    TransportFields(
                        transport = payload.transport,
                        onChange = { t -> update { it.copy(transport = t) } },
                        autoSpaceContainers = autoSpaceContainers,
                        autoSpaceVehicles = autoSpaceVehicles
                    )
                }
                payload.multiPorts.forEach { group ->
                    val portTitle = if (group.port.isBlank()) {
                        stringResource(R.string.new_port)
                    } else {
                        stringResource(R.string.port_title, group.port)
                    }
                    AccordionCard(title = portTitle) {
                        DictionaryAutocomplete(
                            label = stringResource(R.string.port),
                            value = group.port,
                            suggestions = ports,
                            onValueChange = { v ->
                                update { p ->
                                    p.copy(
                                        multiPorts = p.multiPorts.map {
                                            if (it.id == group.id) it.copy(port = v) else it
                                        }
                                    )
                                }
                            },
                            dictionaryType = DictionaryType.PORT,
                            onAddToDictionary = onAddToDictionary
                        )
                        DictionaryAutocomplete(
                            label = stringResource(R.string.vessel),
                            value = group.vessel,
                            suggestions = vessels,
                            onValueChange = { v ->
                                update { p ->
                                    p.copy(
                                        multiPorts = p.multiPorts.map {
                                            if (it.id == group.id) it.copy(vessel = v) else it
                                        }
                                    )
                                }
                            },
                            dictionaryType = DictionaryType.VESSEL,
                            onAddToDictionary = onAddToDictionary
                        )
                        AccordionCard(
                            title = stringResource(R.string.products_section),
                            initiallyExpanded = true
                        ) {
                            group.products.forEach { product ->
                                ProductPrefillFields(
                                    product = product,
                                    productsDict = productsDict,
                                    manufacturers = manufacturers,
                                    onUpdate = { transform ->
                                        update { p ->
                                            p.copy(
                                                multiPorts = p.multiPorts.map { pg ->
                                                    if (pg.id != group.id) pg
                                                    else pg.copy(
                                                        products = pg.products.map {
                                                            if (it.id == product.id) transform(it) else it
                                                        }
                                                    )
                                                }
                                            )
                                        }
                                    },
                                    onDelete = {
                                        update { p ->
                                            p.copy(
                                                multiPorts = p.multiPorts.map { pg ->
                                                    if (pg.id != group.id) pg
                                                    else {
                                                        val next = pg.products.filter { it.id != product.id }
                                                        pg.copy(products = next.ifEmpty { listOf(Product()) })
                                                    }
                                                }
                                            )
                                        }
                                    },
                                    onAddToDictionary = onAddToDictionary
                                )
                            }
                            TextButton(
                                onClick = {
                                    update { p ->
                                        p.copy(
                                            multiPorts = p.multiPorts.map {
                                                if (it.id == group.id) {
                                                    it.copy(products = it.products + Product())
                                                } else it
                                            }
                                        )
                                    }
                                }
                            ) { Text(stringResource(R.string.add_product)) }
                        }
                        TextButton(
                            onClick = {
                                update { p ->
                                    val next = p.multiPorts.filter { it.id != group.id }
                                    p.copy(
                                        multiPorts = next.ifEmpty {
                                            listOf(PortGroup(products = listOf(Product())))
                                        }
                                    )
                                }
                            }
                        ) { Text(stringResource(R.string.delete)) }
                    }
                }
                FishyButton(
                    onClick = {
                        update {
                            it.copy(multiPorts = it.multiPorts + PortGroup(products = listOf(Product())))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.add_port)) }
            }

            ShipmentMode.UNLOAD -> {
                ScheduledUnloadPrefill(
                    payload = payload,
                    onChange = onChange,
                    ports = ports,
                    vessels = vessels,
                    productsDict = productsDict,
                    manufacturers = manufacturers,
                    autoSpaceContainers = autoSpaceContainers,
                    autoSpaceVehicles = autoSpaceVehicles,
                    onAddToDictionary = onAddToDictionary
                )
            }
        }
    }
}
