package com.example.fishy.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = LightText,
    secondary = ButtonBorder,
    background = DarkBackground,
    surface = CardBackground,
    onPrimary = DarkBackground,
    onSecondary = DarkBackground,
    onBackground = LightText,
    onSurface = LightText
)

@Composable
fun FishyTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}