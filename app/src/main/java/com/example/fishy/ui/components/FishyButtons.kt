package com.example.fishy.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingActionButtonElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import com.example.fishy.ui.theme.FishyAccent
import com.example.fishy.ui.theme.FishyCornerRadius
import com.example.fishy.ui.theme.isLightTheme

val FishyButtonShape: Shape
    get() = RoundedCornerShape(FishyCornerRadius)

/** Filled buttons: brand teal in light; Material primary in dark (better contrast). */
@Composable
fun fishyFilledButtonColors(): ButtonColors {
    return if (isLightTheme()) {
        ButtonDefaults.buttonColors(
            containerColor = FishyAccent,
            contentColor = Color.White
        )
    } else {
        ButtonDefaults.buttonColors()
    }
}

/** Checkboxes: brand teal in light; Material defaults in dark. */
@Composable
fun fishyCheckboxColors(): CheckboxColors {
    return if (isLightTheme()) {
        CheckboxDefaults.colors(
            checkedColor = FishyAccent,
            uncheckedColor = FishyAccent,
            checkmarkColor = Color.White
        )
    } else {
        CheckboxDefaults.colors()
    }
}

/** Switches: brand teal in both themes. */
@Composable
fun fishySwitchColors(): SwitchColors =
    SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = FishyAccent,
        checkedBorderColor = FishyAccent,
        uncheckedThumbColor = Color.White,
        uncheckedTrackColor = FishyAccent.copy(alpha = 0.35f),
        uncheckedBorderColor = FishyAccent
    )

@Composable
fun FishyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = fishyFilledButtonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = FishyButtonShape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content
    )
}

@Composable
fun FishyOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = FishyButtonShape,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content
    )
}

@Composable
fun FishyFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = FishyAccent,
    contentColor: Color = Color.White,
    elevation: FloatingActionButtonElevation = FloatingActionButtonDefaults.elevation(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = elevation,
        interactionSource = interactionSource,
        content = content
    )
}
