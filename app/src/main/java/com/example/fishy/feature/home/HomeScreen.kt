package com.example.fishy.feature.home

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.ui.layout.ContentScale
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
import com.example.fishy.ui.components.ColumnScrollIndicator
import com.example.fishy.ui.components.FishyButton
import com.example.fishy.ui.components.FishyOutlinedButton
import com.example.fishy.ui.components.DialogCenteredFishyButton
import com.example.fishy.ui.theme.FishyAccent
import com.example.fishy.ui.theme.FishyAccentLink
import com.example.fishy.ui.theme.isLightTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val HomeButtonHeight = 50.dp
private val HomeTopBarIconSize = 56.dp
/** Small breathing room under the last home button (above system nav). */
private val HomeBottomBreathing = 14.dp

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
    var menuExpanded by remember { mutableStateOf(false) }
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
            // 0→1 and 1→2 always; 2→11 at ~33%; 11→12 at 50%.
            val advanceChance = when (current) {
                0, 1 -> 1f
                11 -> 0.5f
                else -> 1f / 3f
            }
            val advanced = kotlin.random.Random.nextFloat() < advanceChance
            val next = if (advanced) (current + 1).coerceAtMost(12) else current
            val stored = if (advanced && next >= 12) 0 else next.coerceAtMost(11)
            aboutBeerVisit = next
            scope.launch {
                settingsRepo.update { it.copy(aboutOpenCount = stored) }
            }
            if (next >= 12) {
                ErrorFeedback.vibrate(context)
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

    val menuIconTint = if (isLightTheme()) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val darkThemeOn = !isLightTheme()
    val titleText = if (isRussianLanguageActive(settings.language)) {
        stringResource(R.string.home_title_ru)
    } else {
        stringResource(R.string.home_title)
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                // Always size as if "Continue" is present so logo/title don't jump when a draft appears.
                val layoutButtonCount = 7
                val topBarReserve = 80.dp
                val contentMaxHeight = (maxHeight - topBarReserve).coerceAtLeast(0.dp)
                val layout = remember(contentMaxHeight, layoutButtonCount) {
                    computeHomeLayoutMetrics(contentMaxHeight, layoutButtonCount)
                }
                val titleStyle = if (layout.useTightTitle) {
                    MaterialTheme.typography.headlineLarge
                } else {
                    MaterialTheme.typography.displayLarge
                }
                val hasContinue = lastDraftId != null

                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .heightIn(min = if (layout.useTightTitle) 56.dp else 72.dp)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = titleText,
                            style = titleStyle,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = HomeTopBarIconSize)
                        )
                    }

                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        val innerLayout = remember(maxHeight) {
                            computeHomeLayoutMetrics(maxHeight, layoutButtonCount)
                        }

                        val logo: @Composable () -> Unit = {
                            Image(
                                painter = painterResource(id = R.drawable.fishylogo),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .height(innerLayout.logoSize)
                                    .aspectRatio(667f / 1024f)
                                    .clickable {
                                        if (!isRussianLanguageActive(settings.language)) {
                                            return@clickable
                                        }
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
                            )
                        }

                        val buttons: @Composable () -> Unit = {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(innerLayout.buttonGap),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                HomeButton(stringResource(R.string.nav_new_shipment)) {
                                    showModePicker = true
                                }
                                if (hasContinue) {
                                    HomeButton(stringResource(R.string.nav_continue)) {
                                        onContinueDraft(lastDraftId!!)
                                    }
                                }
                                HomeButton(stringResource(R.string.nav_scheduler), onNavigateScheduler)
                                HomeButton(stringResource(R.string.nav_archive), onNavigateArchive)
                                HomeButton(stringResource(R.string.nav_drafts), onNavigateDrafts)
                                HomeButton(stringResource(R.string.nav_templates), onNavigateTemplates)
                                HomeButton(stringResource(R.string.nav_statistics), onNavigateStatistics)
                                // Keep "Новая отгрузка" Y fixed: reserve Continue height at the bottom
                                // (spacedBy already adds one gap before this spacer).
                                if (!hasContinue) {
                                    Spacer(modifier = Modifier.height(HomeButtonHeight))
                                }
                            }
                        }

                        if (innerLayout.contentFits) {
                            // Logo in flexible zone; logo→button gap is never less than buttonGap.
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Spacer(modifier = Modifier.weight(0.40f))
                                    logo()
                                    Spacer(modifier = Modifier.weight(0.60f))
                                }
                                Spacer(modifier = Modifier.height(innerLayout.buttonGap))
                                buttons()
                                Spacer(modifier = Modifier.height(HomeBottomBreathing))
                            }
                        } else {
                            val homeScroll = rememberScrollState()
                            Box(modifier = Modifier.fillMaxSize()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(end = 8.dp)
                                        .verticalScroll(homeScroll)
                                        .padding(bottom = HomeBottomBreathing),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    logo()
                                    Spacer(modifier = Modifier.height(innerLayout.buttonGap))
                                    buttons()
                                }
                                ColumnScrollIndicator(
                                    scrollState = homeScroll,
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .fillMaxHeight()
                                        .padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 8.dp, top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = { menuExpanded = !menuExpanded },
                modifier = Modifier.size(HomeTopBarIconSize)
            ) {
                Icon(
                    imageVector = if (menuExpanded) Icons.Default.Close else Icons.Default.Menu,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = menuIconTint
                )
            }
            AnimatedVisibility(
                visible = menuExpanded,
                enter = expandVertically(animationSpec = tween(200)) + fadeIn(animationSpec = tween(200)),
                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(150))
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = {
                            menuExpanded = false
                            onNavigateSettings()
                        },
                        modifier = Modifier.size(HomeTopBarIconSize)
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.nav_settings),
                            modifier = Modifier.size(32.dp),
                            tint = menuIconTint
                        )
                    }
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
                        modifier = Modifier.size(HomeTopBarIconSize)
                    ) {
                        Icon(
                            imageVector = if (darkThemeOn) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                            contentDescription = stringResource(R.string.theme_toggle_cd),
                            modifier = Modifier.size(32.dp),
                            tint = menuIconTint
                        )
                    }
                    IconButton(
                        onClick = {
                            menuExpanded = false
                            onNavigateFaq()
                        },
                        modifier = Modifier.size(HomeTopBarIconSize)
                    ) {
                        Icon(
                            Icons.Outlined.Help,
                            contentDescription = stringResource(R.string.faq_cd),
                            modifier = Modifier.size(32.dp),
                            tint = menuIconTint
                        )
                    }
                    IconButton(
                        onClick = {
                            menuExpanded = false
                            openAboutDialog()
                        },
                        modifier = Modifier.size(HomeTopBarIconSize)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = menuIconTint
                        )
                    }
                }
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
            .height(HomeButtonHeight)
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

/**
 * Prefer a large logo and normal button gap; shrink logo first, then gap, on short screens.
 * Logo→first-button distance is always at least [HomeLayoutMetrics.buttonGap] (same as button-to-button).
 * [maxHeight] is the area below the top bar (logo + gaps + buttons).
 */
private fun computeHomeLayoutMetrics(maxHeight: Dp, buttonCount: Int): HomeLayoutMetrics {
    val buttonHeight = HomeButtonHeight
    val maxLogo = 280.dp
    val minLogo = 80.dp
    val normalGap = 16.dp
    val reducedGap = 10.dp

    fun buttonsBlock(buttonGap: Dp): Dp =
        buttonHeight * buttonCount + buttonGap * (buttonCount - 1).coerceAtLeast(0)

    // Fixed stack under the logo: logo→button gap (>= button gap) + buttons + bottom breathing.
    fun fixedUnderLogo(buttonGap: Dp): Dp =
        buttonGap + buttonsBlock(buttonGap) + HomeBottomBreathing

    fun result(
        buttonGap: Dp,
        logoSize: Dp,
        useTightTitle: Boolean = false,
        contentFits: Boolean = true
    ): HomeLayoutMetrics = HomeLayoutMetrics(
        logoSize = logoSize,
        logoVerticalPadding = 0.dp,
        buttonGap = buttonGap,
        useTightTitle = useTightTitle,
        contentFits = contentFits
    )

    // 1) Normal gap, full logo if it fits above the fixed under-logo stack
    val availNormal = maxHeight - fixedUnderLogo(normalGap)
    if (availNormal >= maxLogo) {
        return result(normalGap, maxLogo)
    }

    // 2) Normal gap, shrink logo until the min logo→button gap still fits
    if (availNormal >= minLogo) {
        return result(normalGap, availNormal)
    }

    // 3) Reduced gap, shrink logo as needed
    val availReduced = maxHeight - fixedUnderLogo(reducedGap)
    if (availReduced >= minLogo) {
        return result(reducedGap, minLogo.coerceAtMost(availReduced), useTightTitle = true)
    }

    // 4) Last resort: as small as possible; may need scroll
    val tightLogo = availReduced.coerceAtLeast(48.dp)
    return result(
        buttonGap = reducedGap,
        logoSize = tightLogo,
        useTightTitle = true,
        contentFits = tightLogo + fixedUnderLogo(reducedGap) <= maxHeight
    )
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
