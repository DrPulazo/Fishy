package com.example.fishy.feature.shipment

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.fishy.R
import com.example.fishy.domain.model.ShipmentMode
import com.example.fishy.ui.components.DialogCancelConfirmActions
import com.example.fishy.ui.components.DialogCenteredAction

@Composable
fun ModePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (ShipmentMode) -> Unit
) {
    var selected by remember { mutableStateOf(ShipmentMode.MONO) }
    var hintMode by remember { mutableStateOf<ShipmentMode?>(null) }

    val options = listOf(
        ShipmentMode.MONO to (R.string.mode_mono to R.string.mode_mono_hint),
        ShipmentMode.MULTI_VEHICLE to (R.string.mode_multi_vehicle to R.string.mode_multi_vehicle_hint),
        ShipmentMode.MULTI_PORT to (R.string.mode_multi_port to R.string.mode_multi_port_hint),
        ShipmentMode.UNLOAD to (R.string.mode_unload to R.string.mode_unload_hint)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        title = {
            Text(
                text = stringResource(R.string.mode_picker_title),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column {
                options.forEach { (mode, texts) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected == mode,
                                onClick = { selected = mode },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected == mode,
                            onClick = { selected = mode }
                        )
                        Text(
                            text = stringResource(texts.first),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { hintMode = mode }) {
                            Icon(Icons.Default.HelpOutline, contentDescription = null)
                        }
                    }
                }
            }
        },
        confirmButton = {
            DialogCancelConfirmActions(
                onCancel = onDismiss,
                onConfirm = { onConfirm(selected) },
                confirmText = stringResource(R.string.create)
            )
        }
    )

    hintMode?.let { mode ->
        val hintRes = options.first { it.first == mode }.second.second
        AlertDialog(
            onDismissRequest = { hintMode = null },
            containerColor = MaterialTheme.colorScheme.background,
            title = {
                Text(
                    text = stringResource(options.first { it.first == mode }.second.first),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = stringResource(hintRes),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                DialogCenteredAction {
                    TextButton(onClick = { hintMode = null }) { Text("OK") }
                }
            }
        )
    }
}
