package com.example.fishy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.fishy.R
import com.example.fishy.domain.format.QuantityFormatters
import com.example.fishy.domain.model.GrossWeightMath
import com.example.fishy.domain.model.Product

/**
 * Weight/qty fields for a product.
 * Without gross: one row — tare / qty / mass (0.33 / 0.33 / 0.33).
 * With gross: three rows of two equal fields —
 * qty | coeff, net tare | gross tare, net mass | gross mass.
 */
@Composable
fun ProductWeightQuantityFields(
    product: Product,
    grossWeightEnabled: Boolean,
    onPackageWeightChange: (Double) -> Unit,
    onQuantityChange: (Int) -> Unit,
    onCoefficientChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    labelStyle: TextStyle = MaterialTheme.typography.bodySmall,
    textStyle: TextStyle = MaterialTheme.typography.bodySmall,
    thousandsSeparator: Boolean = false,
    tareError: Boolean = false
) {
    val onGrossTareChange: (Double) -> Unit = { grossTare ->
        val k = GrossWeightMath.coefficientFromGrossTare(product.packageWeight, grossTare)
        if (k != null) onCoefficientChange(k)
    }

    if (!grossWeightEnabled) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            DecimalNumberField(
                value = product.packageWeight,
                onValueChange = onPackageWeightChange,
                label = { Text(stringResource(R.string.tare), style = labelStyle) },
                modifier = Modifier
                    .weight(0.33f)
                    .defaultMinSize(minWidth = 0.dp),
                textStyle = textStyle,
                isError = tareError,
                thousandsSeparator = thousandsSeparator
            )
            OutlinedTextField(
                value = if (product.quantity > 0) {
                    QuantityFormatters.formatInteger(product.quantity, thousandsSeparator)
                } else {
                    ""
                },
                onValueChange = { value ->
                    onQuantityChange(
                        value.replace(" ", "").replace("\u00A0", "").toIntOrNull() ?: 0
                    )
                },
                label = { Text(stringResource(R.string.quantity_short), style = labelStyle) },
                modifier = Modifier
                    .weight(0.33f)
                    .defaultMinSize(minWidth = 0.dp),
                textStyle = textStyle,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
            OutlinedTextField(
                value = QuantityFormatters.formatWeight(product.totalWeight, thousandsSeparator),
                onValueChange = {},
                label = { Text(stringResource(R.string.mass), style = labelStyle) },
                modifier = Modifier
                    .weight(0.34f)
                    .defaultMinSize(minWidth = 0.dp),
                textStyle = textStyle,
                readOnly = true,
                singleLine = true
            )
        }
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        TwoEqualFieldsRow {
            OutlinedTextField(
                value = if (product.quantity > 0) {
                    QuantityFormatters.formatInteger(product.quantity, thousandsSeparator)
                } else {
                    ""
                },
                onValueChange = { value ->
                    onQuantityChange(
                        value.replace(" ", "").replace("\u00A0", "").toIntOrNull() ?: 0
                    )
                },
                label = { Text(stringResource(R.string.quantity_short), style = labelStyle) },
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minWidth = 0.dp),
                textStyle = textStyle,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
            DecimalNumberField(
                value = product.grossCoefficient,
                onValueChange = onCoefficientChange,
                label = { Text(stringResource(R.string.gross_k), style = labelStyle) },
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minWidth = 0.dp),
                textStyle = textStyle,
                thousandsSeparator = thousandsSeparator
            )
        }
        TwoEqualFieldsRow {
            DecimalNumberField(
                value = product.packageWeight,
                onValueChange = onPackageWeightChange,
                label = { Text(stringResource(R.string.tare_net), style = labelStyle) },
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minWidth = 0.dp),
                textStyle = textStyle,
                isError = tareError,
                thousandsSeparator = thousandsSeparator
            )
            DecimalNumberField(
                value = product.grossPackageWeight,
                onValueChange = onGrossTareChange,
                label = { Text(stringResource(R.string.gross_tare), style = labelStyle) },
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minWidth = 0.dp),
                textStyle = textStyle,
                thousandsSeparator = thousandsSeparator
            )
        }
        TwoEqualFieldsRow {
            OutlinedTextField(
                value = QuantityFormatters.formatWeight(product.totalWeight, thousandsSeparator),
                onValueChange = {},
                label = { Text(stringResource(R.string.mass_net), style = labelStyle) },
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minWidth = 0.dp),
                textStyle = textStyle,
                readOnly = true,
                singleLine = true
            )
            OutlinedTextField(
                value = QuantityFormatters.formatWeight(product.totalGrossWeight, thousandsSeparator),
                onValueChange = {},
                label = { Text(stringResource(R.string.gross_mass), style = labelStyle) },
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minWidth = 0.dp),
                textStyle = textStyle,
                readOnly = true,
                singleLine = true
            )
        }
    }
}

@Composable
private fun TwoEqualFieldsRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        content = content
    )
}
