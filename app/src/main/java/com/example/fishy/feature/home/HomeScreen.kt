package com.example.fishy.feature.home

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Help
import androidx.compose.material.icons.outlined.LightMode
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.fishy.FishyApp
import com.example.fishy.R
import com.example.fishy.data.settings.AppLanguage
import com.example.fishy.data.settings.FishySettings
import com.example.fishy.data.settings.ThemeMode
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
    onNavigateEula: () -> Unit,
    onNavigateFaq: () -> Unit
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
            val current = settings.aboutOpenCount.coerceIn(0, 11)
            // Progressive chance to advance: 0→1 and 1→2 = 100%; 2→3 = 10% … 10→11 = 90%; 11→12 = 100%.
            val advanceChance = when (current) {
                0, 1 -> 1f
                in 2..10 -> (current - 1) * 0.1f
                else -> 1f // 11 → dozen
            }
            if (kotlin.random.Random.nextFloat() < advanceChance) {
                val next = current + 1
                aboutBeerVisit = next.coerceAtMost(12)
                // After the 12th visit (clickable dozen), cycle resets to 0.
                val stored = if (next >= 12) 0 else next
                scope.launch {
                    settingsRepo.update { it.copy(aboutOpenCount = stored) }
                }
                if (aboutBeerVisit >= 12) {
                    ErrorFeedback.vibrate(context)
                }
            } else {
                aboutBeerVisit = current
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
        lastDraftId = drafts.maxByOrNull { it.completedAtMillis }?.id
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val availableHeight = maxHeight
            val buttonCount = if (lastDraftId != null) 7 else 6
            val layout = remember(availableHeight, buttonCount) {
                computeHomeLayoutMetrics(availableHeight, buttonCount)
            }
            val titleStyle = if (layout.useTightTitle) {
                MaterialTheme.typography.headlineLarge
            } else {
                MaterialTheme.typography.displayLarge
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                val homeHeader: @Composable () -> Unit = {
                    Text(
                        text = if (isRussianLanguageActive(settings.language)) {
                            stringResource(R.string.home_title_ru)
                        } else {
                            stringResource(R.string.home_title)
                        },
                        style = titleStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Image(
                        painter = painterResource(id = R.drawable.fishylogo),
                        contentDescription = null,
                        modifier = Modifier
                            .size(layout.logoSize)
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
                            .padding(vertical = layout.logoVerticalPadding)
                    )
                }
                val homeButtons: @Composable () -> Unit = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(layout.buttonGap),
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

                if (layout.contentFits) {
                    Spacer(modifier = Modifier.weight(1f))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        homeHeader()
                        homeButtons()
                    }
                    Spacer(modifier = Modifier.weight(1f))
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        homeHeader()
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        homeButtons()
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        val cornerIconTint = if (isLightTheme()) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurface
        }
        val darkThemeOn = settings.themeMode != ThemeMode.LIGHT

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = onNavigateFaq,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    Icons.Outlined.Help,
                    contentDescription = stringResource(R.string.faq_cd),
                    modifier = Modifier.size(32.dp),
                    tint = cornerIconTint
                )
            }
            IconButton(
                onClick = { openAboutDialog() },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = cornerIconTint
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = {
                    scope.launch {
                        settingsRepo.update {
                            it.copy(
                                themeMode = if (darkThemeOn) ThemeMode.LIGHT else ThemeMode.DARK
                            )
                        }
                    }
                },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = if (darkThemeOn) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                    contentDescription = stringResource(R.string.theme_toggle_cd),
                    modifier = Modifier.size(32.dp),
                    tint = cornerIconTint
                )
            }
            IconButton(
                onClick = onNavigateSettings,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = stringResource(R.string.nav_settings),
                    modifier = Modifier.size(32.dp),
                    tint = cornerIconTint
                )
            }
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
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(100),
        label = "homeButtonScale"
    )
    FishyOutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .width(250.dp)
            .defaultMinSize(minWidth = 250.dp, minHeight = 50.dp)
            .height(50.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface),
        interactionSource = interactionSource
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

private data class HomeLayoutMetrics(
    val logoSize: Dp,
    val logoVerticalPadding: Dp,
    val buttonGap: Dp,
    val useTightTitle: Boolean,
    val contentFits: Boolean
)

private fun computeHomeLayoutMetrics(maxHeight: Dp, buttonCount: Int): HomeLayoutMetrics {
    val verticalPadding = 16.dp * 2
    val buttonHeight = 50.dp
    val titleHeight = 57.dp
    val logoPadding = 10.dp
    val maxLogo = 300.dp
    val minLogo = 48.dp
    val normalGap = 20.dp
    val reducedGap = 12.dp

    fun fixedReserve(
        buttonGap: Dp,
        titleH: Dp = titleHeight,
        logoPad: Dp = logoPadding
    ): Dp {
        val buttonsBlock = buttonHeight * buttonCount + buttonGap * (buttonCount - 1).coerceAtLeast(0)
        return verticalPadding + titleH + buttonsBlock + logoPad * 2
    }

    fun result(
        buttonGap: Dp,
        logoSize: Dp,
        useTightTitle: Boolean = false,
        logoPad: Dp = logoPadding
    ): HomeLayoutMetrics {
        val titleH = if (useTightTitle) 45.dp else titleHeight
        val fixed = fixedReserve(buttonGap, titleH, logoPad)
        return HomeLayoutMetrics(
            logoSize = logoSize,
            logoVerticalPadding = logoPad,
            buttonGap = buttonGap,
            useTightTitle = useTightTitle,
            contentFits = fixed + logoSize <= maxHeight
        )
    }

    // 1) Normal gap, full logo
    if (maxHeight - fixedReserve(normalGap) >= maxLogo) {
        return result(normalGap, maxLogo)
    }

    // 2) Normal gap, shrink logo
    val shrunkLogo = (maxHeight - fixedReserve(normalGap)).coerceIn(minLogo, maxLogo)
    if (fixedReserve(normalGap) + minLogo <= maxHeight) {
        return result(normalGap, shrunkLogo)
    }

    // 3) Logo at minimum, reduced gap
    val reducedFixed = fixedReserve(reducedGap)
    if (maxHeight - reducedFixed >= minLogo) {
        return result(reducedGap, minLogo)
    }

    // 4) Last resort: tighter title + logo padding
    val tightFixed = fixedReserve(reducedGap, titleH = 45.dp, logoPad = 4.dp)
    val tightLogo = (maxHeight - tightFixed).coerceIn(minLogo, maxLogo)
    return result(reducedGap, tightLogo, useTightTitle = true, logoPad = 4.dp)
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
