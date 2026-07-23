package com.example.fishy.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.fishy.data.settings.ThemeMode

private const val ThemeAnimMs = 200

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
private fun animateColorSchemeAsState(
    target: ColorScheme,
    animationSpec: AnimationSpec<Color> = tween(ThemeAnimMs)
): ColorScheme {
    val primary by animateColorAsState(target.primary, animationSpec, label = "primary")
    val onPrimary by animateColorAsState(target.onPrimary, animationSpec, label = "onPrimary")
    val primaryContainer by animateColorAsState(target.primaryContainer, animationSpec, label = "primaryContainer")
    val onPrimaryContainer by animateColorAsState(target.onPrimaryContainer, animationSpec, label = "onPrimaryContainer")
    val inversePrimary by animateColorAsState(target.inversePrimary, animationSpec, label = "inversePrimary")
    val secondary by animateColorAsState(target.secondary, animationSpec, label = "secondary")
    val onSecondary by animateColorAsState(target.onSecondary, animationSpec, label = "onSecondary")
    val secondaryContainer by animateColorAsState(target.secondaryContainer, animationSpec, label = "secondaryContainer")
    val onSecondaryContainer by animateColorAsState(target.onSecondaryContainer, animationSpec, label = "onSecondaryContainer")
    val tertiary by animateColorAsState(target.tertiary, animationSpec, label = "tertiary")
    val onTertiary by animateColorAsState(target.onTertiary, animationSpec, label = "onTertiary")
    val tertiaryContainer by animateColorAsState(target.tertiaryContainer, animationSpec, label = "tertiaryContainer")
    val onTertiaryContainer by animateColorAsState(target.onTertiaryContainer, animationSpec, label = "onTertiaryContainer")
    val background by animateColorAsState(target.background, animationSpec, label = "background")
    val onBackground by animateColorAsState(target.onBackground, animationSpec, label = "onBackground")
    val surface by animateColorAsState(target.surface, animationSpec, label = "surface")
    val onSurface by animateColorAsState(target.onSurface, animationSpec, label = "onSurface")
    val surfaceVariant by animateColorAsState(target.surfaceVariant, animationSpec, label = "surfaceVariant")
    val onSurfaceVariant by animateColorAsState(target.onSurfaceVariant, animationSpec, label = "onSurfaceVariant")
    val surfaceTint by animateColorAsState(target.surfaceTint, animationSpec, label = "surfaceTint")
    val inverseSurface by animateColorAsState(target.inverseSurface, animationSpec, label = "inverseSurface")
    val inverseOnSurface by animateColorAsState(target.inverseOnSurface, animationSpec, label = "inverseOnSurface")
    val error by animateColorAsState(target.error, animationSpec, label = "error")
    val onError by animateColorAsState(target.onError, animationSpec, label = "onError")
    val errorContainer by animateColorAsState(target.errorContainer, animationSpec, label = "errorContainer")
    val onErrorContainer by animateColorAsState(target.onErrorContainer, animationSpec, label = "onErrorContainer")
    val outline by animateColorAsState(target.outline, animationSpec, label = "outline")
    val outlineVariant by animateColorAsState(target.outlineVariant, animationSpec, label = "outlineVariant")
    val scrim by animateColorAsState(target.scrim, animationSpec, label = "scrim")
    val surfaceBright by animateColorAsState(target.surfaceBright, animationSpec, label = "surfaceBright")
    val surfaceDim by animateColorAsState(target.surfaceDim, animationSpec, label = "surfaceDim")
    val surfaceContainer by animateColorAsState(target.surfaceContainer, animationSpec, label = "surfaceContainer")
    val surfaceContainerHigh by animateColorAsState(target.surfaceContainerHigh, animationSpec, label = "surfaceContainerHigh")
    val surfaceContainerHighest by animateColorAsState(target.surfaceContainerHighest, animationSpec, label = "surfaceContainerHighest")
    val surfaceContainerLow by animateColorAsState(target.surfaceContainerLow, animationSpec, label = "surfaceContainerLow")
    val surfaceContainerLowest by animateColorAsState(target.surfaceContainerLowest, animationSpec, label = "surfaceContainerLowest")

    return target.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = inversePrimary,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = surfaceTint,
        inverseSurface = inverseSurface,
        inverseOnSurface = inverseOnSurface,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        outline = outline,
        outlineVariant = outlineVariant,
        scrim = scrim,
        surfaceBright = surfaceBright,
        surfaceDim = surfaceDim,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainerLowest = surfaceContainerLowest
    )
}

@Composable
fun FishyTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val targetScheme = if (dark) DarkColorScheme else LightColorScheme
    val colorScheme = animateColorSchemeAsState(targetScheme)
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
