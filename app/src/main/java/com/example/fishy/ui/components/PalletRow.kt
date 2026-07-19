package com.example.fishy.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.fishy.R
import com.example.fishy.domain.calc.ShipmentCalculator
import com.example.fishy.domain.model.Pallet
import com.example.fishy.ui.theme.forecastPlacesColor
import com.example.fishy.ui.theme.forecastRowBackground
import kotlinx.coroutines.delay

/** Shared horizontal rhythm: left inset == gap between columns. */
private val PalletColGap = 12.dp

@Composable
private fun placesFieldWidth(): Dp {
    val label = stringResource(R.string.places_count)
    val style = MaterialTheme.typography.bodySmall
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(label, style, density) {
        with(density) {
            measurer.measure(text = label, style = style).size.width.toDp()
        }
    }
}

@Composable
fun PalletTableHeader(doubleControl: Boolean) {
    val placesW = placesFieldWidth()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = PalletColGap, end = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(PalletColGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.pallet_number),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        if (doubleControl) {
            Text(
                text = stringResource(R.string.places_count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(placesW)
            )
            Text(
                text = stringResource(R.string.imported_header),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        } else {
            Box(
                modifier = Modifier.weight(2f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.places_count),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(placesW)
                )
            }
        }
        Box(modifier = Modifier.width(3.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PalletRow(
    pallet: Pallet,
    doubleControl: Boolean,
    onPlacesChange: (Int) -> Unit,
    onToggleImported: () -> Unit,
    onDelete: () -> Unit,
    requestFocus: Boolean = false,
    onFocusHandled: () -> Unit = {}
) {
    var showDelete by remember { mutableStateOf(false) }
    var clearingPlaceholder by remember(pallet.id) { mutableStateOf(false) }
    var draftPlaces by remember(pallet.id) { mutableStateOf("") }
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val focusRequester = remember { FocusRequester() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(requestFocus, pallet.id) {
        if (!requestFocus) return@LaunchedEffect
        // Wait for accordion expand / LazyColumn layout.
        delay(80)
        bringIntoViewRequester.bringIntoView()
        focusRequester.requestFocus()
        keyboard?.show()
        onFocusHandled()
    }

    LaunchedEffect(focused, pallet.isPlaceholder) {
        if (focused && pallet.isPlaceholder && !clearingPlaceholder) {
            clearingPlaceholder = true
            draftPlaces = ""
        }
        if (!focused && clearingPlaceholder && draftPlaces.isEmpty()) {
            clearingPlaceholder = false
        }
    }

    val placesText = when {
        pallet.isPlaceholder && clearingPlaceholder -> draftPlaces
        pallet.places == 0 -> ""
        else -> pallet.places.toString()
    }
    val placesTextColor = if (pallet.isPlaceholder && !clearingPlaceholder) {
        forecastPlacesColor()
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) {
                showDelete = true
                false
            } else true
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.error)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onError
                )
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(
                    if (pallet.isPlaceholder) forecastRowBackground()
                    else MaterialTheme.colorScheme.surface
                )
                .padding(start = PalletColGap)
                .bringIntoViewRequester(bringIntoViewRequester),
            horizontalArrangement = Arrangement.spacedBy(PalletColGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${pallet.palletNumber}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (pallet.isPlaceholder) forecastPlacesColor()
                else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            val placesW = placesFieldWidth()
            val placesField: @Composable (Modifier: Modifier) -> Unit = { fieldModifier ->
                OutlinedTextField(
                    value = placesText,
                    onValueChange = { value ->
                        val digits = value.filter { it.isDigit() }.take(4)
                        if (pallet.isPlaceholder) {
                            clearingPlaceholder = true
                            draftPlaces = digits
                        }
                        onPlacesChange(if (digits.isEmpty()) 0 else digits.toIntOrNull() ?: 0)
                    },
                    modifier = fieldModifier
                        .focusRequester(focusRequester)
                        .onFocusChanged { state ->
                            if (state.isFocused && pallet.isPlaceholder) {
                                clearingPlaceholder = true
                                draftPlaces = ""
                            }
                        },
                    interactionSource = interactionSource,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        textAlign = TextAlign.Center,
                        color = placesTextColor
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = placesTextColor,
                        unfocusedTextColor = placesTextColor,
                        disabledTextColor = placesTextColor
                    )
                )
            }
            if (doubleControl) {
                Box(
                    modifier = Modifier.width(placesW),
                    contentAlignment = Alignment.Center
                ) {
                    placesField(Modifier.fillMaxWidth())
                }
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Checkbox(
                        checked = pallet.isImported,
                        onCheckedChange = { onToggleImported() },
                        enabled = !pallet.isPlaceholder,
                        colors = fishyCheckboxColors()
                    )
                }
            } else {
                Box(
                    modifier = Modifier.weight(2f),
                    contentAlignment = Alignment.Center
                ) {
                    placesField(Modifier.width(placesW))
                }
            }
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.65f))
            )
        }
    }

    if (showDelete) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_pallet_title),
            message = stringResource(
                R.string.delete_pallet_msg,
                pallet.palletNumber,
                ShipmentCalculator.formatPlacesRu(pallet.places)
            ),
            onConfirm = {
                showDelete = false
                onDelete()
            },
            onDismiss = { showDelete = false }
        )
    }
}
