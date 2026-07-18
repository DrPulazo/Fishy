package com.example.fishy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun forecastRowBackground(): Color {
    return if (isLightTheme()) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        FishySurfaceContainerHigh
    }
}

@Composable
fun forecastPlacesColor(): Color {
    return if (isLightTheme()) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        PlaceholderGrey
    }
}
