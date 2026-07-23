package com.example.fishy.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.fishy.R
import com.example.fishy.ui.theme.Error
import com.example.fishy.ui.theme.Success
import com.example.fishy.ui.theme.Warning

/**
 * Centered checklist progress plaque.
 * Empty: checklist icon + “no items” text.
 * With items: “Completed: x/y” on tinted background (red / yellow / green).
 */
@Composable
fun ChecklistStatusBanner(
    completed: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    val statusColor = when {
        total <= 0 -> MaterialTheme.colorScheme.onSurface
        completed <= 0 -> Error
        completed >= total -> Success
        else -> Warning
    }
    val targetBg = if (total <= 0) {
        MaterialTheme.colorScheme.background
    } else {
        statusColor.copy(alpha = 0.12f)
    }
    val animatedBg by animateColorAsState(
        targetValue = targetBg,
        animationSpec = tween(200),
        label = "checklistBannerBg"
    )
    val animatedFg by animateColorAsState(
        targetValue = if (total <= 0) {
            MaterialTheme.colorScheme.onSurface
        } else {
            statusColor
        },
        animationSpec = tween(200),
        label = "checklistBannerFg"
    )

    if (total <= 0) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Checklist,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.checklist_empty),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    } else {
        Text(
            text = stringResource(R.string.checklist_done_progress, completed, total),
            modifier = modifier
                .fillMaxWidth()
                .background(animatedBg)
                .padding(16.dp),
            color = animatedFg,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
