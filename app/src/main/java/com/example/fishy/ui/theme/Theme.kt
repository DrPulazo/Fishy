package com.example.fishy.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = OnSurface,
    secondary = SurfaceVariant,
    primaryContainer = SurfaceVariant,
    secondaryContainer = Surface,
    tertiary = Warning, // желтый для перегруза
    background = DarkBackground,
    surface = Surface,
    onPrimary = DarkBackground,
    onSecondary = OnSurface,
    onTertiary = DarkBackground,
    onBackground = OnSurface,
    onSurface = OnSurface,
    onSurfaceVariant = OnSurfaceVariant,
    error = Error,
    surfaceVariant = SurfaceVariant
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