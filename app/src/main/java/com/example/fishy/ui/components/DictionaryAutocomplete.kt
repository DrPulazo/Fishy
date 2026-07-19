package com.example.fishy.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.fishy.data.local.entity.DictionaryEntity
import com.example.fishy.domain.model.DictionaryType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryAutocomplete(
    label: String,
    value: String,
    suggestions: List<DictionaryEntity>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    dictionaryType: DictionaryType? = null,
    onAddToDictionary: ((DictionaryType, String) -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val filtered = remember(value, suggestions) {
        if (value.isBlank()) suggestions.take(20)
        else suggestions.filter { it.value.contains(value, ignoreCase = true) }.take(20)
    }
    val hasExactMatch = remember(value, suggestions) {
        value.isNotBlank() && suggestions.any { it.value.equals(value, ignoreCase = true) }
    }
    val showAddButton =
        onAddToDictionary != null &&
            dictionaryType != null &&
            value.isNotBlank() &&
            !hasExactMatch
    val menuExpanded = expanded && filtered.isNotEmpty() && !hasExactMatch
    val textStyle = formTextStyleOrDefault()
    val labelStyle = formLabelStyleOrDefault()

    ExposedDropdownMenuBox(
        expanded = menuExpanded,
        onExpandedChange = { want ->
            expanded = want && !hasExactMatch
        },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                val exact = it.isNotBlank() &&
                    suggestions.any { s -> s.value.equals(it, ignoreCase = true) }
                expanded = !exact
            },
            label = { Text(label, style = labelStyle) },
            textStyle = textStyle,
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            keyboardOptions = FishySentenceKeyboardOptions,
            trailingIcon = {
                Row {
                    if (showAddButton) {
                        IconButton(
                            onClick = {
                                expanded = false
                                onAddToDictionary?.invoke(dictionaryType!!, value.trim())
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                        }
                    }
                    ExposedDropdownMenuDefaults.TrailingIcon(menuExpanded)
                }
            },
            singleLine = true
        )
        ExposedDropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { expanded = false }
        ) {
            filtered.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.value) },
                    onClick = {
                        onValueChange(item.value)
                        expanded = false
                    }
                )
            }
        }
    }
}
