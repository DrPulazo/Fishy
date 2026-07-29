package com.example.fishy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.fishy.R
import com.example.fishy.data.local.entity.DictionaryEntity
import com.example.fishy.domain.format.ContainerSpaceVisualTransformation
import com.example.fishy.domain.format.NumberFormatters
import com.example.fishy.domain.format.TrailerSpaceVisualTransformation
import com.example.fishy.domain.format.VehicleSpaceVisualTransformation
import com.example.fishy.domain.model.DictionaryType
import com.example.fishy.domain.model.Transport
import com.example.fishy.domain.validation.ContainerWagonValidator
import com.example.fishy.domain.validation.ValidationState
import com.example.fishy.ui.ErrorFeedback
import com.example.fishy.ui.theme.Warning

private val CapsKeyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)

@Composable
private fun VibrateOnTransportError(hasError: Boolean) {
    val context = LocalContext.current
    var wasError by remember { mutableStateOf(false) }
    LaunchedEffect(hasError) {
        if (hasError && !wasError) {
            ErrorFeedback.vibrate(context)
        }
        wasError = hasError
    }
}

/** Yellow/amber outline while container (1..10) or wagon (1..7) length is incomplete. */
@Composable
private fun incompleteNumberFieldColors(incomplete: Boolean) =
    if (incomplete) {
        OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Warning,
            unfocusedBorderColor = Warning,
            focusedLabelColor = Warning,
            unfocusedLabelColor = Warning
        )
    } else {
        OutlinedTextFieldDefaults.colors()
    }

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
    val textStyle = formTextStyleOrDefault()
    val labelStyle = formLabelStyleOrDefault()

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
    val wagonIncomplete = !hasWagonError && wagonValidation is ValidationState.InProgress
    val containerIncomplete = !hasContainerError && containerValidation is ValidationState.InProgress
    val invalidMsg = stringResource(R.string.invalid_number)
    VibrateOnTransportError(hasContainerError || hasWagonError)

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
                label = { Text(stringResource(R.string.wagon_label), style = labelStyle) },
                textStyle = textStyle,
                modifier = Modifier
                    .fillMaxWidth()
                    .shakeOnError(hasWagonError),
                singleLine = true,
                keyboardOptions = CapsKeyboard,
                isError = hasWagonError,
                colors = incompleteNumberFieldColors(wagonIncomplete),
                trailingIcon = {
                    if (hasWagonError) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = stringResource(R.string.error_cd),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                supportingText = if (hasWagonError) {
                    { Text(invalidMsg) }
                } else {
                    null
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
                label = { Text(stringResource(R.string.container_label), style = labelStyle) },
                textStyle = textStyle,
                modifier = Modifier
                    .fillMaxWidth()
                    .shakeOnError(hasContainerError),
                singleLine = true,
                keyboardOptions = CapsKeyboard,
                isError = hasContainerError,
                colors = incompleteNumberFieldColors(containerIncomplete),
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
                supportingText = if (hasContainerError) {
                    { Text(invalidMsg) }
                } else {
                    null
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                    label = { Text(stringResource(R.string.truck_label), style = labelStyle) },
                    textStyle = textStyle,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minWidth = 0.dp),
                    singleLine = true,
                    keyboardOptions = CapsKeyboard,
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
                    label = { Text(stringResource(R.string.trailer_label), style = labelStyle) },
                    textStyle = textStyle,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minWidth = 0.dp),
                    singleLine = true,
                    keyboardOptions = CapsKeyboard,
                    visualTransformation = if (autoSpaceVehicles) {
                        TrailerSpaceVisualTransformation()
                    } else {
                        VisualTransformation.None
                    }
                )
            }
        }

        OutlinedTextField(
            value = transport.sealNumber,
            onValueChange = { onChange(transport.copy(sealNumber = it.uppercase())) },
            label = { Text(stringResource(R.string.seal_label), style = labelStyle) },
            textStyle = textStyle,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = CapsKeyboard
        )
    }
}

/**
 * Unload reception: port always visible; destination transport optional (wagon XOR road).
 * Port is not cleared when transport is filled.
 */
@Composable
fun UnloadReceptionFields(
    warehouse: String,
    transport: Transport,
    onWarehouseChange: (String) -> Unit,
    onTransportChange: (Transport) -> Unit,
    ports: List<DictionaryEntity> = emptyList(),
    onAddToDictionary: ((DictionaryType, String) -> Unit)? = null,
    autoSpaceContainers: Boolean = false,
    autoSpaceVehicles: Boolean = false,
    modifier: Modifier = Modifier
) {
    val hasWagon = transport.wagonNumber.isNotBlank()
    val hasRoad = transport.containerNumber.isNotBlank() ||
        transport.truckNumber.isNotBlank() ||
        transport.trailerNumber.isNotBlank()

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
    val wagonIncomplete = !hasWagonError && wagonValidation is ValidationState.InProgress
    val containerIncomplete = !hasContainerError && containerValidation is ValidationState.InProgress
    val invalidMsg = stringResource(R.string.invalid_number)
    val textStyle = formTextStyleOrDefault()
    val labelStyle = formLabelStyleOrDefault()
    VibrateOnTransportError(hasContainerError || hasWagonError)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DictionaryAutocomplete(
            label = stringResource(R.string.port),
            value = warehouse,
            suggestions = ports,
            onValueChange = onWarehouseChange,
            dictionaryType = DictionaryType.PORT,
            onAddToDictionary = onAddToDictionary
        )

        if (!hasRoad) {
            OutlinedTextField(
                value = transport.wagonNumber,
                onValueChange = { raw ->
                    val clean = NumberFormatters.stripSpaces(raw)
                    onTransportChange(
                        if (clean.isNotEmpty()) {
                            Transport(
                                wagonNumber = clean,
                                sealNumber = transport.sealNumber
                            )
                        } else {
                            transport.copy(wagonNumber = "")
                        }
                    )
                },
                label = { Text(stringResource(R.string.wagon_label), style = labelStyle) },
                textStyle = textStyle,
                modifier = Modifier
                    .fillMaxWidth()
                    .shakeOnError(hasWagonError),
                singleLine = true,
                keyboardOptions = CapsKeyboard,
                isError = hasWagonError,
                colors = incompleteNumberFieldColors(wagonIncomplete),
                trailingIcon = {
                    if (hasWagonError) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = stringResource(R.string.error_cd),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                supportingText = if (hasWagonError) {
                    { Text(invalidMsg) }
                } else {
                    null
                }
            )
        }

        if (!hasWagon) {
            OutlinedTextField(
                value = transport.containerNumber,
                onValueChange = { raw ->
                    val clean = NumberFormatters.stripSpaces(raw).uppercase()
                    onTransportChange(
                        if (clean.isNotEmpty()) {
                            transport.copy(containerNumber = clean, wagonNumber = "")
                        } else {
                            transport.copy(containerNumber = "")
                        }
                    )
                },
                label = { Text(stringResource(R.string.container_label), style = labelStyle) },
                textStyle = textStyle,
                modifier = Modifier
                    .fillMaxWidth()
                    .shakeOnError(hasContainerError),
                singleLine = true,
                keyboardOptions = CapsKeyboard,
                isError = hasContainerError,
                colors = incompleteNumberFieldColors(containerIncomplete),
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
                supportingText = if (hasContainerError) {
                    { Text(invalidMsg) }
                } else {
                    null
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = transport.truckNumber,
                    onValueChange = { raw ->
                        val clean = NumberFormatters.stripSpaces(raw).uppercase()
                        onTransportChange(
                            if (clean.isNotEmpty()) {
                                transport.copy(truckNumber = clean, wagonNumber = "")
                            } else {
                                transport.copy(truckNumber = "")
                            }
                        )
                    },
                    label = { Text(stringResource(R.string.truck_label), style = labelStyle) },
                    textStyle = textStyle,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minWidth = 0.dp),
                    singleLine = true,
                    keyboardOptions = CapsKeyboard,
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
                        onTransportChange(
                            if (clean.isNotEmpty()) {
                                transport.copy(trailerNumber = clean, wagonNumber = "")
                            } else {
                                transport.copy(trailerNumber = "")
                            }
                        )
                    },
                    label = { Text(stringResource(R.string.trailer_label), style = labelStyle) },
                    textStyle = textStyle,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minWidth = 0.dp),
                    singleLine = true,
                    keyboardOptions = CapsKeyboard,
                    visualTransformation = if (autoSpaceVehicles) {
                        TrailerSpaceVisualTransformation()
                    } else {
                        VisualTransformation.None
                    }
                )
            }
        }

        OutlinedTextField(
            value = transport.sealNumber,
            onValueChange = { v ->
                onTransportChange(transport.copy(sealNumber = v.uppercase()))
            },
            label = { Text(stringResource(R.string.seal_label), style = labelStyle) },
            textStyle = textStyle,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = CapsKeyboard
        )
    }
}

@Composable
fun transportTitle(
    transport: Transport,
    autoSpaceContainers: Boolean = false,
    autoSpaceVehicles: Boolean = false
): String {
    val wagon = transport.wagonNumber.trim()
    if (wagon.isNotEmpty()) {
        return stringResource(R.string.wagon_prefix, wagon)
    }
    val container = NumberFormatters.formatContainerForDisplay(
        transport.containerNumber.trim(),
        autoSpaceContainers
    )
    val truck = NumberFormatters.formatVehicleForDisplay(
        transport.truckNumber.trim(),
        autoSpaceVehicles
    )
    val trailer = NumberFormatters.formatTrailerForDisplay(
        transport.trailerNumber.trim(),
        autoSpaceVehicles
    )
    val parts = mutableListOf<String>()
    if (container.isNotEmpty()) {
        parts += stringResource(R.string.container_prefix, container)
    }
    if (truck.isNotEmpty()) {
        parts += stringResource(R.string.truck_prefix, truck)
    }
    if (trailer.isNotEmpty() && transport.containerNumber.trim().isEmpty()) {
        parts += stringResource(R.string.trailer_prefix, trailer)
    }
    return if (parts.isEmpty()) {
        stringResource(R.string.new_transport)
    } else {
        parts.joinToString(" ")
    }
}

@Composable
fun unloadReceptionTitle(
    warehouse: String,
    transport: Transport,
    autoSpaceContainers: Boolean = false,
    autoSpaceVehicles: Boolean = false
): String {
    val transportLabel = transportTitle(transport, autoSpaceContainers, autoSpaceVehicles)
    val hasTransport = transportLabel != stringResource(R.string.new_transport)
    return when {
        warehouse.isNotBlank() && hasTransport -> "$warehouse — $transportLabel"
        warehouse.isNotBlank() -> warehouse
        hasTransport -> transportLabel
        else -> stringResource(R.string.reception_point)
    }
}
