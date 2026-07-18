package com.example.fishy.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.fishy.data.settings.ThemeMode

/** True when the active Fishy color scheme is the light palette. */
@Composable
fun isLightTheme(): Boolean =
    MaterialTheme.colorScheme.background.luminance() >= 0.5f

/**
 * Fully specified neutral schemes. Leaving roles unset lets Material3 seed
 * purple-tinted surfaceContainer* / surfaceTint from the default tonal palette.
 */
private val DarkColorScheme = darkColorScheme(
    primary = OnSurface,
    onPrimary = DarkBackground,
    primaryContainer = FishySurfaceVariant,
    onPrimaryContainer = OnSurface,
    inversePrimary = OnSurfaceVariant,

    secondary = OnSurfaceVariant,
    onSecondary = DarkBackground,
    secondaryContainer = FishySurfaceContainer,
    onSecondaryContainer = OnSurface,

    tertiary = Warning,
    onTertiary = DarkBackground,
    tertiaryContainer = Color(0xFF4A3A20),
    onTertiaryContainer = Color(0xFFFFE0B2),

    background = DarkBackground,
    onBackground = OnSurface,

    surface = FishySurface,
    onSurface = OnSurface,
    surfaceVariant = FishySurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceTint = Color.Transparent,

    inverseSurface = OnSurface,
    inverseOnSurface = DarkBackground,

    error = Error,
    onError = Color.White,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,

    outline = FishyOutline,
    outlineVariant = FishyOutlineVariant,
    scrim = Color.Black,

    surfaceBright = FishySurfaceContainerHighest,
    surfaceDim = DarkBackground,
    surfaceContainer = FishySurfaceContainer,
    surfaceContainerHigh = FishySurfaceContainerHigh,
    surfaceContainerHighest = FishySurfaceContainerHighest,
    surfaceContainerLow = FishySurfaceContainerLow,
    surfaceContainerLowest = FishySurfaceContainerLowest
)

private val LightColorScheme = lightColorScheme(
    primary = LightOnSurface,
    onPrimary = LightSurface,
    primaryContainer = LightSurfaceContainer,
    onPrimaryContainer = LightOnSurface,
    inversePrimary = LightOnSurfaceVariant,

    secondary = LightOnSurfaceVariant,
    onSecondary = LightSurface,
    secondaryContainer = LightSurfaceContainerHigh,
    onSecondaryContainer = LightOnSurface,

    tertiary = Warning,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE0B2),
    onTertiaryContainer = Color(0xFF4A3A20),

    background = LightBackground,
    onBackground = LightOnSurface,

    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = Color.Transparent,

    inverseSurface = LightOnSurface,
    inverseOnSurface = LightSurface,

    error = Error,
    onError = Color.White,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,

    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    scrim = Color.Black,

    surfaceBright = LightSurfaceBright,
    surfaceDim = LightSurfaceDim,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainerLowest = LightSurfaceContainerLowest
)

@Composable
fun FishyTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.DARK, ThemeMode.SYSTEM -> true
        ThemeMode.LIGHT -> false
    }
    val colorScheme = if (dark) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = fishyTypography(),
        shapes = FishyShapes,
        content = content
    )
}
