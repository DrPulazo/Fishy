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
import androidx.compose.ui.unit.dp
import com.example.fishy.R
import com.example.fishy.domain.model.ShipmentMode

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
        title = { Text(stringResource(R.string.mode_picker_title)) },
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
            TextButton(onClick = { onConfirm(selected) }) {
                Text(stringResource(R.string.create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )

    hintMode?.let { mode ->
        val hintRes = options.first { it.first == mode }.second.second
        AlertDialog(
            onDismissRequest = { hintMode = null },
            title = {
                Text(stringResource(options.first { it.first == mode }.second.first))
            },
            text = { Text(stringResource(hintRes)) },
            confirmButton = {
                TextButton(onClick = { hintMode = null }) { Text("OK") }
            }
        )
    }
}
