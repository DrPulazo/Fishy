package com.example.fishy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.fishy.R
import com.example.fishy.domain.format.ContainerSpaceVisualTransformation
import com.example.fishy.domain.format.NumberFormatters
import com.example.fishy.domain.format.TrailerSpaceVisualTransformation
import com.example.fishy.domain.format.VehicleSpaceVisualTransformation
import com.example.fishy.domain.model.Transport
import com.example.fishy.domain.validation.ContainerWagonValidator
import com.example.fishy.domain.validation.ValidationState

/**
 * Transport fields matching v1 UX:
 * wagon XOR road set, order Вагон → Контейнер → Авто|Прицеп → Пломба,
 * live ISO6346 / wagon check-digit validation with error chrome.
 */
@Composable
fun TransportFields(
    transport: Transport,
    onChange: (Transport) -> Unit,
    autoSpaceContainers: Boolean = false,
    autoSpaceVehicles: Boolean = false,
    modifier: Modifier = Modifier
) {
    val hasWagon = transport.wagonNumber.isNotEmpty()
    val hasVehicle = transport.containerNumber.isNotEmpty() ||
        transport.truckNumber.isNotEmpty() ||
        transport.trailerNumber.isNotEmpty()
    val showWagon = !hasVehicle || hasWagon
    val showVehicle = !hasWagon || hasVehicle

    val containerValidation = remember(transport.containerNumber) {
        ContainerWagonValidator.validateContainerNumberLive(transport.containerNumber)
    }
    val wagonValidation = remember(transport.wagonNumber) {
        ContainerWagonValidator.validateWagonNumberLive(transport.wagonNumber)
    }
    val hasContainerError = containerValidation is ValidationState.Invalid ||
        containerValidation is ValidationState.InvalidWithSuggestion
    val hasWagonError = wagonValidation is ValidationState.Invalid ||
        wagonValidation is ValidationState.InvalidWithSuggestion
    val invalidMsg = stringResource(R.string.invalid_number)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showWagon) {
            OutlinedTextField(
                value = transport.wagonNumber,
                onValueChange = { raw ->
                    val clean = NumberFormatters.stripSpaces(raw)
                    onChange(
                        if (clean.isNotEmpty()) {
                            transport.copy(
                                wagonNumber = clean,
                                containerNumber = "",
                                truckNumber = "",
                                trailerNumber = ""
                            )
                        } else {
                            transport.copy(wagonNumber = "")
                        }
                    )
                },
                label = { Text(stringResource(R.string.wagon_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = hasWagonError,
                trailingIcon = {
                    if (hasWagonError) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = stringResource(R.string.error_cd),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                supportingText = {
                    if (hasWagonError) Text(invalidMsg)
                }
            )
        }

        if (showVehicle) {
            OutlinedTextField(
                value = transport.containerNumber,
                onValueChange = { raw ->
                    val clean = NumberFormatters.stripSpaces(raw).uppercase()
                    onChange(
                        if (clean.isNotEmpty()) {
                            transport.copy(containerNumber = clean, wagonNumber = "")
                        } else {
                            transport.copy(containerNumber = "")
                        }
                    )
                },
                label = { Text(stringResource(R.string.container_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = hasContainerError,
                visualTransformation = if (autoSpaceContainers) {
                    ContainerSpaceVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                trailingIcon = {
                    if (hasContainerError) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = stringResource(R.string.error_cd),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                supportingText = {
                    if (hasContainerError) Text(invalidMsg)
                }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = transport.truckNumber,
                    onValueChange = { raw ->
                        val clean = NumberFormatters.stripSpaces(raw).uppercase()
                        onChange(
                            if (clean.isNotEmpty()) {
                                transport.copy(truckNumber = clean, wagonNumber = "")
                            } else {
                                transport.copy(truckNumber = "")
                            }
                        )
                    },
                    label = { Text(stringResource(R.string.truck_label)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    visualTransformation = if (autoSpaceVehicles) {
                        VehicleSpaceVisualTransformation()
                    } else {
                        VisualTransformation.None
                    }
                )
                OutlinedTextField(
                    value = transport.trailerNumber,
                    onValueChange = { raw ->
                        val clean = NumberFormatters.stripSpaces(raw).uppercase()
                        onChange(
                            if (clean.isNotEmpty()) {
                                transport.copy(trailerNumber = clean, wagonNumber = "")
                            } else {
                                transport.copy(trailerNumber = "")
                            }
                        )
                    },
                    label = { Text(stringResource(R.string.trailer_label)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    visualTransformation = if (autoSpaceVehicles) {
                        TrailerSpaceVisualTransformation()
                    } else {
                        VisualTransformation.None
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(0.dp))
        OutlinedTextField(
            value = transport.sealNumber,
            onValueChange = { onChange(transport.copy(sealNumber = it.uppercase())) },
            label = { Text(stringResource(R.string.seal_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}

/**
 * Unload reception point: warehouse, wagon, container, vehicle, seal — mutually exclusive groups.
 * Order: склад → вагон → контейнер → авто/прицеп → пломба.
 */
@Composable
fun UnloadReceptionFields(
    warehouse: String,
    transport: Transport,
    onWarehouseChange: (String) -> Unit,
    onTransportChange: (Transport) -> Unit,
    autoSpaceContainers: Boolean = false,
    autoSpaceVehicles: Boolean = false,
    modifier: Modifier = Modifier
) {
    val hasWarehouse = warehouse.isNotBlank()
    val hasWagon = transport.wagonNumber.isNotBlank()
    val hasRoad = transport.containerNumber.isNotBlank() ||
        transport.truckNumber.isNotBlank() ||
        transport.trailerNumber.isNotBlank()
    val hasSealOnly = transport.sealNumber.isNotBlank() && !hasWagon && !hasRoad

    val containerValidation = remember(transport.containerNumber) {
        ContainerWagonValidator.validateContainerNumberLive(transport.containerNumber)
    }
    val wagonValidation = remember(transport.wagonNumber) {
        ContainerWagonValidator.validateWagonNumberLive(transport.wagonNumber)
    }
    val hasContainerError = containerValidation is ValidationState.Invalid ||
        containerValidation is ValidationState.InvalidWithSuggestion
    val hasWagonError = wagonValidation is ValidationState.Invalid ||
        wagonValidation is ValidationState.InvalidWithSuggestion
    val invalidMsg = stringResource(R.string.invalid_number)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!hasWagon && !hasRoad && !hasSealOnly) {
            OutlinedTextField(
                value = warehouse,
                onValueChange = { v ->
                    if (v.isNotBlank()) {
                        onWarehouseChange(v)
                        onTransportChange(Transport())
                    } else {
                        onWarehouseChange("")
                    }
                },
                label = { Text(stringResource(R.string.warehouse_destination)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        if (!hasWarehouse && !hasRoad) {
            OutlinedTextField(
                value = transport.wagonNumber,
                onValueChange = { raw ->
                    val clean = NumberFormatters.stripSpaces(raw)
                    onTransportChange(
                        if (clean.isNotEmpty()) {
                            Transport(wagonNumber = clean)
                        } else {
                            transport.copy(wagonNumber = "")
                        }
                    )
                    if (clean.isNotEmpty()) onWarehouseChange("")
                },
                label = { Text(stringResource(R.string.wagon_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = hasWagonError,
                trailingIcon = {
                    if (hasWagonError) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = stringResource(R.string.error_cd),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                supportingText = {
                    if (hasWagonError) Text(invalidMsg)
                }
            )
        }

        if (!hasWarehouse && !hasWagon) {
            OutlinedTextField(
                value = transport.containerNumber,
                onValueChange = { raw ->
                    val clean = NumberFormatters.stripSpaces(raw).uppercase()
                    val base = if (clean.isNotEmpty()) Transport(containerNumber = clean) else transport.copy(containerNumber = "")
                    onTransportChange(base.copy(wagonNumber = ""))
                    if (clean.isNotEmpty()) onWarehouseChange("")
                },
                label = { Text(stringResource(R.string.container_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = hasContainerError,
                visualTransformation = if (autoSpaceContainers) {
                    ContainerSpaceVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                trailingIcon = {
                    if (hasContainerError) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = stringResource(R.string.error_cd),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                supportingText = {
                    if (hasContainerError) Text(invalidMsg)
                }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = transport.truckNumber,
                    onValueChange = { raw ->
                        val clean = NumberFormatters.stripSpaces(raw).uppercase()
                        val next = if (clean.isNotEmpty()) {
                            transport.copy(truckNumber = clean, wagonNumber = "")
                        } else {
                            transport.copy(truckNumber = "")
                        }
                        onTransportChange(next)
                        if (clean.isNotEmpty()) onWarehouseChange("")
                    },
                    label = { Text(stringResource(R.string.truck_label)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    visualTransformation = if (autoSpaceVehicles) {
                        VehicleSpaceVisualTransformation()
                    } else {
                        VisualTransformation.None
                    }
                )
                OutlinedTextField(
                    value = transport.trailerNumber,
                    onValueChange = { raw ->
                        val clean = NumberFormatters.stripSpaces(raw).uppercase()
                        val next = if (clean.isNotEmpty()) {
                            transport.copy(trailerNumber = clean, wagonNumber = "")
                        } else {
                            transport.copy(trailerNumber = "")
                        }
                        onTransportChange(next)
                        if (clean.isNotEmpty()) onWarehouseChange("")
                    },
                    label = { Text(stringResource(R.string.trailer_label)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    visualTransformation = if (autoSpaceVehicles) {
                        TrailerSpaceVisualTransformation()
                    } else {
                        VisualTransformation.None
                    }
                )
            }
        }

        if (!hasWarehouse && !hasWagon && !hasRoad) {
            OutlinedTextField(
                value = transport.sealNumber,
                onValueChange = { v ->
                    onTransportChange(
                        if (v.isNotBlank()) Transport(sealNumber = v.uppercase()) else transport.copy(sealNumber = "")
                    )
                    if (v.isNotBlank()) onWarehouseChange("")
                },
                label = { Text(stringResource(R.string.seal_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        } else if (!hasWarehouse && !hasWagon) {
            OutlinedTextField(
                value = transport.sealNumber,
                onValueChange = { v ->
                    onTransportChange(transport.copy(sealNumber = v.uppercase()))
                },
                label = { Text(stringResource(R.string.seal_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}

@Composable
fun transportTitle(transport: Transport): String {
    val parts = mutableListOf<String>()
    if (transport.wagonNumber.isNotEmpty()) {
        parts += stringResource(R.string.wagon_prefix, transport.wagonNumber)
    } else {
        if (transport.containerNumber.isNotEmpty()) {
            parts += stringResource(R.string.container_prefix, transport.containerNumber)
        }
        if (transport.truckNumber.isNotEmpty()) {
            parts += stringResource(R.string.truck_prefix, transport.truckNumber)
        }
        if (transport.trailerNumber.isNotEmpty() && transport.containerNumber.isEmpty()) {
            parts += stringResource(R.string.trailer_prefix, transport.trailerNumber)
        }
        if (transport.sealNumber.isNotEmpty() && parts.isEmpty()) {
            parts += stringResource(R.string.seal_prefix, transport.sealNumber)
        }
    }
    return if (parts.isEmpty()) stringResource(R.string.new_transport) else parts.joinToString(" • ")
}

@Composable
fun unloadReceptionTitle(warehouse: String, transport: Transport): String {
    if (warehouse.isNotBlank()) return warehouse
    val label = transportTitle(transport)
    return if (label != stringResource(R.string.new_transport)) label
    else stringResource(R.string.reception_point)
}
