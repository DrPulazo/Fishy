package com.example.fishy.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Scaffold body: optional top chrome, list content, or empty overlay
 * centered in the full body (chrome does not shift the empty block).
 */
@Composable
fun CenteredEmptyBody(
    isEmpty: Boolean,
    empty: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    topChrome: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            topChrome()
            if (isEmpty) {
                Spacer(modifier = Modifier.weight(1f))
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    content()
                }
            }
        }
        if (isEmpty) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                empty()
            }
        }
    }
}
