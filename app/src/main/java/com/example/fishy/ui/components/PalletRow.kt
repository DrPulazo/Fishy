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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.fishy.R
import com.example.fishy.domain.calc.ShipmentCalculator
import com.example.fishy.domain.format.QuantityFormatters
import com.example.fishy.domain.model.Pallet
import com.example.fishy.ui.theme.forecastPlacesColor
import com.example.fishy.ui.theme.forecastRowBackground
import kotlinx.coroutines.delay

/** Shared horizontal rhythm: left inset == gap between columns. */
private val PalletColGap = 12.dp

@Composable
fun PalletTableHeader(doubleControl: Boolean) {
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
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stringResource(R.string.places_count),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        if (doubleControl) {
            Text(
                text = stringResource(R.string.imported_header),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }
        Box(modifier = Modifier.width(3.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PalletRow(
    pallet: Pallet,
    doubleControl: Boolean,
    onPlacesChange: (Double) -> Unit,
    onToggleImported: () -> Unit,
    onDelete: () -> Unit,
    requestFocus: Boolean = false,
    onFocusHandled: () -> Unit = {},
    thousandsSeparator: Boolean = false
) {
    var showDelete by remember { mutableStateOf(false) }
    var clearingPlaceholder by remember(pallet.id) { mutableStateOf(false) }
    var draftPlaces by remember(pallet.id) { mutableStateOf("") }
    var placesFocused by remember { mutableStateOf(false) }
    var placesText by remember(pallet.id) {
        mutableStateOf(QuantityFormatters.formatWeightInput(pallet.places, thousandsSeparator))
    }
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
            placesText = ""
        }
        if (!focused && clearingPlaceholder && draftPlaces.isEmpty()) {
            clearingPlaceholder = false
        }
    }

    LaunchedEffect(pallet.places, placesFocused, clearingPlaceholder, thousandsSeparator) {
        if (!placesFocused && !(pallet.isPlaceholder && clearingPlaceholder)) {
            placesText = QuantityFormatters.formatWeightInput(pallet.places, thousandsSeparator)
        }
    }

    val displayPlacesText = when {
        pallet.isPlaceholder && clearingPlaceholder -> draftPlaces
        else -> placesText
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
            OutlinedTextField(
                value = displayPlacesText,
                onValueChange = { value ->
                    val sanitized = QuantityFormatters.sanitizeDecimalInput(value).take(8)
                    if (pallet.isPlaceholder) {
                        clearingPlaceholder = true
                        draftPlaces = sanitized
                    }
                    placesText = sanitized
                    val parsed = QuantityFormatters.parseDecimalInput(sanitized)
                    if (parsed != null) onPlacesChange(parsed)
                },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { state ->
                        placesFocused = state.isFocused
                        if (state.isFocused && pallet.isPlaceholder) {
                            clearingPlaceholder = true
                            draftPlaces = ""
                            placesText = ""
                        }
                    },
                interactionSource = interactionSource,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
            if (doubleControl) {
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
