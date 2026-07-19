package com.example.fishy.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.TextStyle

/**
 * Compact typography for scheduler forms only.
 * When null, shared fields use normal [MaterialTheme.typography.bodyLarge].
 */
val LocalFormTextStyle = compositionLocalOf<TextStyle?> { null }

@Composable
fun formTextStyleOrDefault(): TextStyle =
    LocalFormTextStyle.current ?: MaterialTheme.typography.bodyLarge

@Composable
fun formLabelStyleOrDefault(): TextStyle =
    LocalFormTextStyle.current ?: MaterialTheme.typography.bodyLarge
