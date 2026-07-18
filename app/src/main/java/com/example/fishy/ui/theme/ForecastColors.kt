package com.example.fishy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

@Composable
fun forecastRowBackground(): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (dark) Color(0xFF3A3A3A) else MaterialTheme.colorScheme.surfaceContainerHigh
}

@Composable
fun forecastPlacesColor(): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (dark) Color(0xFF9E9E9E) else MaterialTheme.colorScheme.onSurfaceVariant
}
