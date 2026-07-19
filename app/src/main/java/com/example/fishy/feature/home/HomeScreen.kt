package com.example.fishy.feature.home

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.fishy.FishyApp
import com.example.fishy.R
import com.example.fishy.data.settings.AppLanguage
import com.example.fishy.data.settings.FishySettings
import com.example.fishy.domain.model.ShipmentMode
import com.example.fishy.feature.shipment.ModePickerDialog
import com.example.fishy.ui.ErrorFeedback
import com.example.fishy.ui.components.FishyButton
import com.example.fishy.ui.components.FishyOutlinedButton
import com.example.fishy.ui.components.DialogCenteredFishyButton
import com.example.fishy.ui.theme.FishyAccent
import com.example.fishy.ui.theme.FishyAccentLink
import com.example.fishy.ui.theme.isLightTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onOpenShipment: (ShipmentMode) -> Unit,
    onContinueDraft: (Long) -> Unit,
    onNavigateScheduler: () -> Unit,
    onNavigateArchive: () -> Unit,
    onNavigateDrafts: () -> Unit,
    onNavigateTemplates: () -> Unit,
    onNavigateStatistics: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateEasterEgg: () -> Unit,
    onNavigateEula: () -> Unit
) {
    var showInfoDialog by remember { mutableStateOf(false) }
    var showModePicker by remember { mutableStateOf(false) }
    var easterEggClickCount by remember { mutableIntStateOf(0) }
    var aboutBeerVisit by remember { mutableIntStateOf(0) }
    var lastClickTime by remember { mutableLongStateOf(0L) }
    var lastDraftId by remember { mutableStateOf<Long?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepo = FishyApp.instance.settingsRepository
    val settings by settingsRepo.settings.collectAsState(initial = FishySettings())

    fun openAboutDialog() {
        if (isRussianLanguageActive(settings.language)) {
            val next = settings.aboutOpenCount + 1
            aboutBeerVisit = next.coerceAtMost(12)
            // After the 12th visit (clickable dozen), cycle resets to 0.
            val stored = if (next >= 12) 0 else next
            scope.launch {
                settingsRepo.update { it.copy(aboutOpenCount = stored) }
            }
        } else {
            aboutBeerVisit = 0
        }
        showInfoDialog = true
    }

    LaunchedEffect(easterEggClickCount) {
        if (easterEggClickCount > 0) {
            delay(3000)
            easterEggClickCount = 0
        }
    }

    LaunchedEffect(Unit) {
        val drafts = FishyApp.instance.repository.getDrafts()
        lastDraftId = drafts.maxByOrNull { it.createdAtMillis }?.id
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (isRussianLanguageActive(settings.language)) {
                    stringResource(R.string.home_title_ru)
                } else {
                    stringResource(R.string.home_title)
                },
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Image(
                painter = painterResource(id = R.drawable.fishylogo),
                contentDescription = null,
                modifier = Modifier
                    .size(300.dp)
                    .clickable {
                        if (!isRussianLanguageActive(settings.language)) return@clickable
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastClickTime < 500) {
                            easterEggClickCount++
                            if (easterEggClickCount >= 10) {
                                easterEggClickCount = 0
                                ErrorFeedback.vibrate(context)
                                Toast.makeText(
                                    context,
                                    LOGO_EASTER_MESSAGES.random(),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                        lastClickTime = currentTime
                    }
                    .padding(vertical = 10.dp)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HomeButton(stringResource(R.string.nav_new_shipment)) { showModePicker = true }
                if (lastDraftId != null) {
                    HomeButton(stringResource(R.string.nav_continue)) {
                        onContinueDraft(lastDraftId!!)
                    }
                }
                HomeButton(stringResource(R.string.nav_scheduler), onNavigateScheduler)
                HomeButton(stringResource(R.string.nav_archive), onNavigateArchive)
                HomeButton(stringResource(R.string.nav_drafts), onNavigateDrafts)
                HomeButton(stringResource(R.string.nav_templates), onNavigateTemplates)
                HomeButton(stringResource(R.string.nav_statistics), onNavigateStatistics)
            }
        }

        IconButton(
            onClick = { openAboutDialog() },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .size(48.dp)
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = if (isLightTheme()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }

        IconButton(
            onClick = onNavigateSettings,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(48.dp)
        ) {
            Icon(
                Icons.Default.Settings,
                contentDescription = stringResource(R.string.nav_settings),
                tint = if (isLightTheme()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }

    if (showModePicker) {
        ModePickerDialog(
            onDismiss = { showModePicker = false },
            onConfirm = { mode ->
                showModePicker = false
                onOpenShipment(mode)
            }
        )
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            containerColor = MaterialTheme.colorScheme.background,
            title = {
                Text(
                    text = stringResource(R.string.about_title),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.about_version),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Text(
                        text = stringResource(R.string.about_budget_title),
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = when {
                            aboutBeerVisit <= 0 -> stringResource(R.string.about_budget_beer)
                            aboutBeerVisit >= 12 -> EASTER_EGG_ALE_LINE
                            else -> beerCratesLabel(aboutBeerVisit)
                        },
                        textAlign = TextAlign.Center,
                        color = if (aboutBeerVisit >= 12) {
                            if (isLightTheme()) FishyAccentLink else FishyAccent
                        } else {
                            Color.Unspecified
                        },
                        textDecoration = if (aboutBeerVisit >= 12) {
                            TextDecoration.Underline
                        } else {
                            TextDecoration.None
                        },
                        modifier = if (aboutBeerVisit >= 12) {
                            Modifier
                                .clickable {
                                    showInfoDialog = false
                                    onNavigateEasterEgg()
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        } else {
                            Modifier
                        }
                    )
                    Text(
                        text = stringResource(R.string.about_budget_nights),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Text(
                        text = stringResource(R.string.about_feedback),
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    AboutLink(
                        label = stringResource(R.string.about_link_telegram),
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/DrPulazo"))
                            )
                        }
                    )
                    AboutLink(
                        label = stringResource(R.string.about_link_email),
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_SENDTO,
                                    Uri.parse("mailto:FishyApp@mail.ru")
                                )
                            )
                        },
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = stringResource(R.string.about_source),
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    AboutLink(
                        label = stringResource(R.string.about_link_github),
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://github.com/DrPulazo/Fishy")
                                )
                            )
                        },
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Text(
                        text = stringResource(R.string.about_support_developer),
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    AboutLink(
                        label = stringResource(R.string.about_link_boosty),
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://boosty.to/drpulazo/donate")
                                )
                            )
                        },
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    AboutLink(
                        label = stringResource(R.string.about_eula),
                        onClick = {
                            showInfoDialog = false
                            onNavigateEula()
                        },
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Text(
                        text = stringResource(R.string.about_city_name),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.about_city_year),
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                DialogCenteredFishyButton(onClick = { showInfoDialog = false }) {
                    Text(stringResource(R.string.about_ok))
                }
            }
        )
    }
}

/** Link style in the About dialog. */
@Composable
private fun AboutLink(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        text = label,
        color = if (isLightTheme()) FishyAccentLink else FishyAccent,
        textAlign = TextAlign.Center,
        textDecoration = TextDecoration.Underline,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

@Composable
private fun HomeButton(text: String, onClick: () -> Unit) {
    FishyOutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .width(250.dp)
            .height(50.dp),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

/** Explicit RU, or SYSTEM when the device locale is Russian. */
private fun isRussianLanguageActive(language: AppLanguage): Boolean =
    language == AppLanguage.RU || language == AppLanguage.SYSTEM

/** Russian pluralization for beer crate count on visits 1..11. */
private fun beerCratesLabel(count: Int): String = when (count) {
    1 -> "Ящик пива"
    in 2..4 -> "$count ящика пива"
    else -> "$count ящиков пива"
}
