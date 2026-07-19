package com.example.fishy.feature.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.fishy.FishyApp
import com.example.fishy.R
import com.example.fishy.data.settings.AppLanguage
import com.example.fishy.data.settings.ThemeMode
import com.example.fishy.ui.components.CenteredDialogMessage
import com.example.fishy.ui.components.CenteredDialogTitle
import com.example.fishy.ui.components.ConfirmDeleteDialog
import com.example.fishy.ui.components.DialogCancelConfirmActions
import com.example.fishy.ui.components.fishyCheckboxColors
import com.example.fishy.ui.components.fishySwitchColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class WipeDialogStep { None, FirstConfirm, TypeConfirm }

private fun formatMaxWeightKg(value: Double): String =
    if (value == 0.0) ""
    else if (value == value.toLong().toDouble()) value.toLong().toString()
    else value.toString()

private fun formatMaxPlaces(value: Int): String =
    if (value == 0) "" else value.toString()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = FishyApp.instance
    val settingsRepo = app.settingsRepository
    val settings by settingsRepo.settings.collectAsState(
        initial = com.example.fishy.data.settings.FishySettings()
    )
    val scope = rememberCoroutineScope()
    var languageMenuOpen by remember { mutableStateOf(false) }
    var wipeStep by remember { mutableStateOf(WipeDialogStep.None) }
    var wipeConfirmText by remember { mutableStateOf("") }
    var maxWeightText by remember { mutableStateOf("") }
    var maxPlacesText by remember { mutableStateOf("") }
    var maxWeightFocused by remember { mutableStateOf(false) }
    var maxPlacesFocused by remember { mutableStateOf(false) }
    val darkThemeOn = settings.themeMode != ThemeMode.LIGHT
    val languages = remember {
        AppLanguage.entries.filter { it != AppLanguage.SYSTEM }
    }
    val wipeWord = stringResource(R.string.wipe_data_confirm_word)
    val canConfirmWipe = wipeConfirmText == wipeWord

    LaunchedEffect(settings.maxPlaceWeightKg) {
        if (!maxWeightFocused) {
            maxWeightText = formatMaxWeightKg(settings.maxPlaceWeightKg)
        }
    }
    LaunchedEffect(settings.maxPlacesPerPallet) {
        if (!maxPlacesFocused) {
            maxPlacesText = formatMaxPlaces(settings.maxPlacesPerPallet)
        }
    }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.nav_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = languageMenuOpen,
                    onExpandedChange = { languageMenuOpen = it }
                ) {
                    OutlinedTextField(
                        value = languageOptionLabel(settings.language),
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageMenuOpen) },
                        modifier = Modifier
                            .menuAnchor(type = MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = languageMenuOpen,
                        onDismissRequest = { languageMenuOpen = false }
                    ) {
                        languages.forEach { lang ->
                            DropdownMenuItem(
                                text = { Text(languageOptionLabel(lang)) },
                                onClick = {
                                    languageMenuOpen = false
                                    scope.launch {
                                        settingsRepo.update { it.copy(language = lang) }
                                        FishyApp.applyAppLanguage(lang)
                                    }
                                }
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.theme_dark), modifier = Modifier.weight(1f))
                    Switch(
                        checked = darkThemeOn,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                settingsRepo.update {
                                    it.copy(themeMode = if (enabled) ThemeMode.DARK else ThemeMode.LIGHT)
                                }
                            }
                        },
                        colors = fishySwitchColors()
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.input_guard), modifier = Modifier.weight(1f))
                    Switch(
                        checked = settings.inputGuardEnabled,
                        onCheckedChange = { v ->
                            scope.launch { settingsRepo.update { it.copy(inputGuardEnabled = v) } }
                        },
                        colors = fishySwitchColors()
                    )
                }
                if (settings.inputGuardEnabled) {
                    OutlinedTextField(
                        value = maxWeightText,
                        onValueChange = { v ->
                            val filtered = v.filter { it.isDigit() || it == '.' || it == ',' }
                            maxWeightText = filtered
                            scope.launch {
                                settingsRepo.update {
                                    it.copy(
                                        maxPlaceWeightKg = filtered.replace(',', '.')
                                            .toDoubleOrNull() ?: 0.0
                                    )
                                }
                            }
                        },
                        label = { Text(stringResource(R.string.settings_max_place_weight)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { maxWeightFocused = it.isFocused },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = maxPlacesText,
                        onValueChange = { v ->
                            val filtered = v.filter { it.isDigit() }
                            maxPlacesText = filtered
                            scope.launch {
                                settingsRepo.update {
                                    it.copy(maxPlacesPerPallet = filtered.toIntOrNull() ?: 0)
                                }
                            }
                        },
                        label = { Text(stringResource(R.string.settings_max_places_pallet)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { maxPlacesFocused = it.isFocused },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.auto_spaces), modifier = Modifier.weight(1f))
                    Switch(
                        checked = settings.autoSpacesEnabled,
                        onCheckedChange = { v ->
                            scope.launch { settingsRepo.update { it.copy(autoSpacesEnabled = v) } }
                        },
                        colors = fishySwitchColors()
                    )
                }
                if (settings.autoSpacesEnabled) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(
                            checked = settings.autoSpaceContainers,
                            onCheckedChange = { v ->
                                scope.launch { settingsRepo.update { it.copy(autoSpaceContainers = v) } }
                            },
                            colors = fishyCheckboxColors()
                        )
                        Text(
                            stringResource(R.string.auto_spaces_containers),
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(
                            checked = settings.autoSpaceVehicles,
                            onCheckedChange = { v ->
                                scope.launch { settingsRepo.update { it.copy(autoSpaceVehicles = v) } }
                            },
                            colors = fishyCheckboxColors()
                        )
                        Text(
                            stringResource(R.string.auto_spaces_vehicles),
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.floating_fab), modifier = Modifier.weight(1f))
                    Switch(
                        checked = settings.floatingFabEnabled,
                        onCheckedChange = { v ->
                            scope.launch { settingsRepo.update { it.copy(floatingFabEnabled = v) } }
                        },
                        colors = fishySwitchColors()
                    )
                }
            }

            TextButton(
                onClick = {
                    wipeConfirmText = ""
                    wipeStep = WipeDialogStep.FirstConfirm
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.wipe_data),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    when (wipeStep) {
        WipeDialogStep.None -> Unit
        WipeDialogStep.FirstConfirm -> {
            ConfirmDeleteDialog(
                title = stringResource(R.string.wipe_data_title),
                message = stringResource(R.string.wipe_data_message),
                onConfirm = {
                    wipeConfirmText = ""
                    wipeStep = WipeDialogStep.TypeConfirm
                },
                onDismiss = { wipeStep = WipeDialogStep.None }
            )
        }
        WipeDialogStep.TypeConfirm -> {
            AlertDialog(
                onDismissRequest = { wipeStep = WipeDialogStep.None },
                containerColor = MaterialTheme.colorScheme.background,
                title = {
                    CenteredDialogTitle(stringResource(R.string.wipe_data_confirm_title))
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CenteredDialogMessage(stringResource(R.string.wipe_data_confirm_message))
                        OutlinedTextField(
                            value = wipeConfirmText,
                            onValueChange = { wipeConfirmText = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    DialogCancelConfirmActions(
                        onCancel = { wipeStep = WipeDialogStep.None },
                        onConfirm = {
                            wipeStep = WipeDialogStep.None
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    app.notificationScheduler.cancelAll()
                                    app.repository.wipeAll()
                                    app.settingsRepository.clear()
                                }
                                FishyApp.applyAppLanguage(AppLanguage.RU)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.wipe_data_done),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        confirmText = stringResource(R.string.delete),
                        confirmEnabled = canConfirmWipe,
                        confirmColor = MaterialTheme.colorScheme.error
                    )
                }
            )
        }
    }
}

/** Native-script labels — never taken from locale resources (avoids mojibake). */
private fun languageOptionLabel(lang: AppLanguage): String = when (lang) {
    AppLanguage.SYSTEM, AppLanguage.RU -> "🇷🇺 Русский"
    AppLanguage.EN -> "🇬🇧 English"
    AppLanguage.ZH -> "🇨🇳 中文"
    AppLanguage.KO -> "🇰🇷 한국어"
    AppLanguage.JA -> "🇯🇵 日本語"
    AppLanguage.ES -> "🇪🇸 Español"
}
