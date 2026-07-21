package com.example.fishy.feature.scheduler

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.fishy.R
import com.example.fishy.data.local.entity.DictionaryEntity
import com.example.fishy.domain.model.DictionaryType
import com.example.fishy.domain.model.Product
import com.example.fishy.domain.model.ShipmentPayload
import com.example.fishy.domain.model.UnloadInbound
import com.example.fishy.ui.components.AccordionCard
import com.example.fishy.ui.components.DictionaryAutocomplete
import com.example.fishy.ui.components.FishyButton
import com.example.fishy.ui.components.TransportFields
import com.example.fishy.ui.components.UnloadReceptionFields
import com.example.fishy.ui.components.transportTitle
import com.example.fishy.ui.components.unloadReceptionTitle

@Composable
fun ScheduledUnloadPrefill(
    payload: ShipmentPayload,
    onChange: (ShipmentPayload) -> Unit,
    ports: List<DictionaryEntity>,
    vessels: List<DictionaryEntity>,
    productsDict: List<DictionaryEntity>,
    manufacturers: List<DictionaryEntity>,
    autoSpaceContainers: Boolean,
    autoSpaceVehicles: Boolean,
    thousandsSeparator: Boolean = false,
    onAddToDictionary: (DictionaryType, String) -> Unit,
    onRequestDelete: SchedulerDeleteRequest
) {
    val context = LocalContext.current
    fun update(block: (ShipmentPayload) -> ShipmentPayload) = onChange(block(payload))

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        payload.unloadReceptions.forEach { reception ->
            val receptionTitle = unloadReceptionTitle(
                reception.name,
                reception.transport,
                autoSpaceContainers,
                autoSpaceVehicles
            )
            AccordionCard(
                title = receptionTitle,
                trailing = {
                    IconButton(onClick = {
                        onRequestDelete(
                            context.getString(R.string.delete_reception_title),
                            context.getString(R.string.delete_reception_msg)
                        ) {
                            update { p ->
                                val next = p.unloadReceptions.filter { it.id != reception.id }
                                p.copy(unloadReceptions = next.ifEmpty { listOf(emptyUnloadReception()) })
                            }
                        }
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
                    initiallyExpanded = true
                ) {
                    UnloadReceptionFields(
                        warehouse = reception.name,
                        transport = reception.transport,
                        onWarehouseChange = { v ->
                            update { p ->
                                p.copy(
                                    unloadReceptions = p.unloadReceptions.map {
                                        if (it.id == reception.id) it.copy(name = v) else it
                                    }
                                )
                            }
                        },
                        onTransportChange = { t ->
                            update { p ->
                                p.copy(
                                    unloadReceptions = p.unloadReceptions.map {
                                        if (it.id == reception.id) it.copy(transport = t) else it
                                    }
                                )
                            }
                        },
                        ports = ports,
                        onAddToDictionary = onAddToDictionary,
                        autoSpaceContainers = autoSpaceContainers,
                        autoSpaceVehicles = autoSpaceVehicles
                    )
                }
                reception.inbounds.forEach { inbound ->
                    val inboundTitle = transportTitle(
                        inbound.transport,
                        autoSpaceContainers,
                        autoSpaceVehicles
                    ).let { t ->
                        if (t != stringResource(R.string.new_transport)) t
                        else stringResource(R.string.unload_source)
                    }
                    AccordionCard(
                        title = inboundTitle,
                        initiallyExpanded = true,
                        trailing = {
                            IconButton(onClick = {
                                onRequestDelete(
                                    context.getString(R.string.delete_source_title),
                                    context.getString(R.string.delete_source_msg)
                                ) {
                                    update { p ->
                                        p.copy(
                                            unloadReceptions = p.unloadReceptions.map { r ->
                                                if (r.id != reception.id) r
                                                else {
                                                    val next = r.inbounds.filter { it.id != inbound.id }
                                                    r.copy(
                                                        inbounds = next.ifEmpty {
                                                            listOf(UnloadInbound(products = listOf(Product())))
                                                        }
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
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
                                update { p ->
                                    p.copy(
                                        unloadReceptions = p.unloadReceptions.map { r ->
                                            if (r.id != reception.id) r
                                            else r.copy(
                                                inbounds = r.inbounds.map {
                                                    if (it.id == inbound.id) it.copy(port = v) else it
                                                }
                                            )
                                        }
                                    )
                                }
                            },
                            dictionaryType = DictionaryType.PORT,
                            onAddToDictionary = onAddToDictionary
                        )
                        DictionaryAutocomplete(
                            label = stringResource(R.string.vessel),
                            value = inbound.vessel,
                            suggestions = vessels,
                            onValueChange = { v ->
                                update { p ->
                                    p.copy(
                                        unloadReceptions = p.unloadReceptions.map { r ->
                                            if (r.id != reception.id) r
                                            else r.copy(
                                                inbounds = r.inbounds.map {
                                                    if (it.id == inbound.id) it.copy(vessel = v) else it
                                                }
                                            )
                                        }
                                    )
                                }
                            },
                            dictionaryType = DictionaryType.VESSEL,
                            onAddToDictionary = onAddToDictionary
                        )
                        TransportFields(
                            transport = inbound.transport,
                            onChange = { t ->
                                update { p ->
                                    p.copy(
                                        unloadReceptions = p.unloadReceptions.map { r ->
                                            if (r.id != reception.id) r
                                            else r.copy(
                                                inbounds = r.inbounds.map {
                                                    if (it.id == inbound.id) it.copy(transport = t) else it
                                                }
                                            )
                                        }
                                    )
                                }
                            },
                            autoSpaceContainers = autoSpaceContainers,
                            autoSpaceVehicles = autoSpaceVehicles
                        )
                        inbound.products.forEach { product ->
                            ProductPrefillFields(
                                product = product,
                                productsDict = productsDict,
                                manufacturers = manufacturers,
                                onUpdate = { transform ->
                                    update { p ->
                                        p.copy(
                                            unloadReceptions = p.unloadReceptions.map { r ->
                                                if (r.id != reception.id) r
                                                else r.copy(
                                                    inbounds = r.inbounds.map { ib ->
                                                        if (ib.id != inbound.id) ib
                                                        else ib.copy(
                                                            products = ib.products.map {
                                                                if (it.id == product.id) transform(it) else it
                                                            }
                                                        )
                                                    }
                                                )
                                            }
                                        )
                                    }
                                },
                                onDelete = {
                                    onRequestDelete(
                                        context.getString(R.string.delete_product_title),
                                        context.getString(
                                            R.string.delete_product_msg,
                                            product.name.ifBlank {
                                                context.getString(R.string.new_product)
                                            }
                                        )
                                    ) {
                                        update { p ->
                                            p.copy(
                                                unloadReceptions = p.unloadReceptions.map { r ->
                                                    if (r.id != reception.id) r
                                                    else r.copy(
                                                        inbounds = r.inbounds.map { ib ->
                                                            if (ib.id != inbound.id) ib
                                                            else {
                                                                val next = ib.products.filter { it.id != product.id }
                                                                ib.copy(products = next.ifEmpty { listOf(Product()) })
                                                            }
                                                        }
                                                    )
                                                }
                                            )
                                        }
                                    }
                                },
                                onAddToDictionary = onAddToDictionary,
                                grossWeightEnabled = payload.grossWeightEnabled,
                                thousandsSeparator = thousandsSeparator
                            )
                        }
                        TextButton(
                            onClick = {
                                update { p ->
                                    p.copy(
                                        unloadReceptions = p.unloadReceptions.map { r ->
                                            if (r.id != reception.id) r
                                            else r.copy(
                                                inbounds = r.inbounds.map {
                                                    if (it.id == inbound.id) {
                                                        it.copy(products = it.products + Product())
                                                    } else it
                                                }
                                            )
                                        }
                                    )
                                }
                            }
                        ) { Text(stringResource(R.string.add_product)) }
                    }
                }
                FishyButton(
                    onClick = {
                        update { p ->
                            p.copy(
                                unloadReceptions = p.unloadReceptions.map {
                                    if (it.id == reception.id) {
                                        it.copy(
                                            inbounds = it.inbounds + UnloadInbound(
                                                products = listOf(Product())
                                            )
                                        )
                                    } else it
                                }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.add_source)) }
            }
        }
        FishyButton(
            onClick = {
                update { it.copy(unloadReceptions = it.unloadReceptions + emptyUnloadReception()) }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.add_reception_point)) }
    }
}
