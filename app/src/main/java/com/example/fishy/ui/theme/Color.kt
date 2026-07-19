package com.example.fishy.ui.theme

import androidx.compose.ui.graphics.Color

/** Dark theme — neutral greys only (no Material purple tonal seed). */
val DarkBackground = Color(0xFF1A1A1A)
val FishySurface = Color(0xFF2D2D2D)
val FishySurfaceVariant = Color(0xFF3D3D3D)
val FishySurfaceContainer = Color(0xFF333333)
val FishySurfaceContainerHigh = Color(0xFF3A3A3A)
val FishySurfaceContainerHighest = Color(0xFF444444)
val FishySurfaceContainerLow = Color(0xFF262626)
val FishySurfaceContainerLowest = Color(0xFF121212)
val FishyOutline = Color(0xFF6B6B6B)
val FishyOutlineVariant = Color(0xFF454545)
val OnSurface = Color(0xFFE0E0E0)
val OnSurfaceVariant = Color(0xFFB0B0B0)
val Success = Color(0xFF4CAF50)
val Warning = Color(0xFFFF9800)
val Error = Color(0xFFF44336)
val ErrorContainer = Color(0xFF5C1A1A)
val OnErrorContainer = Color(0xFFFFDAD6)

val ProgressRed = Color(0xFFF44336)
val ProgressOrange = Color(0xFFFF9800)
val ProgressYellow = Color(0xFFFFEB3B)
val ProgressGreen = Color(0xFF4CAF50)
val SyntaxVar = Color(0xFF64B5F6)
val SyntaxText = Color(0xFFBDBDBD)
val PlaceholderGrey = Color(0xFF757575)
/** Forecast placeholder row — mid grey, not background and not red. */
val ForecastRowGrey = Color(0xFF3E3E3E)
/** Forecasted places digits — darker muted grey, readable on ForecastRowGrey. */
val ForecastPlacesGrey = Color(0xFF2A2A2A)

/** Brand / accent (filled buttons, dark-theme links). */
val FishyAccent = Color(0xFF45BBBB)
/** Same hue as [FishyAccent], darker — readable links on light surfaces. */
val FishyAccentLink = Color(0xFF0E8B8B)

/**
 * Light theme — MD3-aligned neutral surfaces (no mid-grey mud).
 * Hierarchy: near-white canvas → white surface → stepped surfaceContainer*.
 * @see https://m3.material.io/styles/color/roles
 */
val LightBackground = Color(0xFFF5F5F5)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceDim = Color(0xFFE3E3E3)
val LightSurfaceBright = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE8E8E8)
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFF0F0F0)
val LightSurfaceContainer = Color(0xFFEBEBEB)
val LightSurfaceContainerHigh = Color(0xFFE4E4E4)
val LightSurfaceContainerHighest = Color(0xFFDEDEDE)
val LightOutline = Color(0xFF757575)
val LightOutlineVariant = Color(0xFFC6C6C6)
/** Primary text / icons on light — soft near-black, not pure #000. */
val LightOnSurface = Color(0xFF2C2C2C)
val LightOnSurfaceVariant = Color(0xFF5C5C5C)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)
