package com.example.fishy.feature.home

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.fishy.FishyApp
import com.example.fishy.R
import com.example.fishy.data.settings.AppLanguage
import com.example.fishy.data.settings.FishySettings
import com.example.fishy.ui.components.FishyButton
import java.nio.charset.StandardCharsets
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EulaScreen(
    onBack: () -> Unit,
    requireAccept: Boolean = false,
    onAccepted: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val settings by FishyApp.instance.settingsRepository.settings.collectAsState(initial = FishySettings())
    val rawRes = remember(settings.language) { eulaRawResource(context, settings.language) }
    val eulaText = remember(rawRes) {
        context.resources.openRawResource(rawRes)
            .bufferedReader(StandardCharsets.UTF_8)
            .use { it.readText() }
    }
    val scrollState = rememberScrollState()
    val reachedEnd by remember {
        derivedStateOf {
            scrollState.maxValue == 0 || scrollState.value >= scrollState.maxValue
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.about_eula)) },
                navigationIcon = {
                    if (!requireAccept) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (requireAccept) {
                FishyButton(
                    onClick = { onAccepted?.invoke() ?: onBack() },
                    enabled = reachedEnd,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(stringResource(R.string.eula_accept))
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = eulaText,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Justify
            )
        }
    }
}

private fun eulaRawResource(context: Context, language: AppLanguage): Int {
    val tag = when (language) {
        AppLanguage.RU -> "ru"
        AppLanguage.EN -> "en"
        AppLanguage.ES -> "es"
        AppLanguage.ZH -> "zh"
        AppLanguage.KO -> "ko"
        AppLanguage.JA -> "ja"
        AppLanguage.SYSTEM -> Locale.getDefault().language
    }
    return when (tag) {
        "ru" -> R.raw.eula_ru
        "es" -> R.raw.eula_es
        "zh" -> R.raw.eula_zh
        "ko" -> R.raw.eula_ko
        "ja" -> R.raw.eula_ja
        else -> R.raw.eula_en
    }
}
