package com.example.fishy.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import com.example.fishy.domain.format.QuantityFormatters

/**
 * Decimal weight/tare field that keeps intermediate input like `12,` without resetting,
 * and replaces `.` with `,` while typing. Optional thousands grouping when unfocused.
 */
@Composable
fun DecimalNumberField(
    value: Double,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    textStyle: TextStyle = TextStyle.Default,
    isError: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    thousandsSeparator: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    var text by remember {
        mutableStateOf(QuantityFormatters.formatWeightInput(value, thousandsSeparator))
    }

    LaunchedEffect(value, focused, thousandsSeparator) {
        if (!focused) {
            text = QuantityFormatters.formatWeightInput(value, thousandsSeparator)
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val sanitized = QuantityFormatters.sanitizeDecimalInput(raw)
            text = sanitized
            val parsed = QuantityFormatters.parseDecimalInput(sanitized)
            if (parsed != null) onValueChange(parsed)
        },
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        label = label,
        textStyle = textStyle,
        isError = isError,
        enabled = enabled,
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    )
}
