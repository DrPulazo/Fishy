package com.example.fishy.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Nesting depth of [AccordionCard]: 0 = top-level on screen background. */
private val LocalAccordionNesting = compositionLocalOf { 0 }

/** Optional title style override (e.g. compact scheduler forms). */
val LocalAccordionTitleStyle = compositionLocalOf<TextStyle?> { null }

@Composable
fun AccordionCard(
    title: String,
    modifier: Modifier = Modifier,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    subtitleColor: Color? = null,
    subtitle: String? = null,
    initiallyExpanded: Boolean = true,
    /** When this value changes to a non-null token, the card expands (e.g. smart FAB focus). */
    forceExpandToken: Any? = null,
    titleStyle: TextStyle? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    LaunchedEffect(forceExpandToken) {
        if (forceExpandToken != null) expanded = true
    }
    val nesting = LocalAccordionNesting.current
    // MD3 tonal containers: white/surface on canvas, then stepped grey insets when nested.
    val scheme = MaterialTheme.colorScheme
    val containerColor = when (nesting) {
        0 -> scheme.surface
        1 -> scheme.surfaceContainer
        2 -> scheme.surfaceContainerHigh
        else -> scheme.surfaceContainerHighest
    }
    val resolvedTitleStyle = titleStyle
        ?: LocalAccordionTitleStyle.current
        ?: MaterialTheme.typography.titleMedium

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = resolvedTitleStyle,
                        fontWeight = FontWeight.Bold,
                        color = titleColor
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = subtitleColor ?: titleColor.copy(alpha = 0.85f)
                        )
                    }
                }
                trailing?.invoke()
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = titleColor
                    )
                }
            }
            if (expanded) {
                CompositionLocalProvider(LocalAccordionNesting provides nesting + 1) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        content = content
                    )
                }
            }
        }
    }
}
