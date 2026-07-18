package com.example.fishy.feature.scheduler

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import com.example.fishy.ui.components.FishyButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    onAddToDictionary: (DictionaryType, String) -> Unit
) {
    fun update(block: (ShipmentPayload) -> ShipmentPayload) = onChange(block(payload))

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        payload.unloadReceptions.forEach { reception ->
            val receptionTitle = unloadReceptionTitle(reception.name, reception.transport)
            AccordionCard(title = receptionTitle) {
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
                        autoSpaceContainers = autoSpaceContainers,
                        autoSpaceVehicles = autoSpaceVehicles
                    )
                }
                reception.inbounds.forEach { inbound ->
                    val inboundTitle = transportTitle(inbound.transport).let { t ->
                        if (t != stringResource(R.string.new_transport)) t
                        else stringResource(R.string.unload_source)
                    }
                    AccordionCard(title = inboundTitle, initiallyExpanded = true) {
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
                                },
                                onAddToDictionary = onAddToDictionary
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
                        TextButton(
                            onClick = {
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
                        ) { Text(stringResource(R.string.delete)) }
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
                TextButton(
                    onClick = {
                        update { p ->
                            val next = p.unloadReceptions.filter { it.id != reception.id }
                            p.copy(unloadReceptions = next.ifEmpty { listOf(emptyUnloadReception()) })
                        }
                    }
                ) { Text(stringResource(R.string.delete)) }
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
