package com.example.fishy.ui.navigation

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.fishy.FishyApp
import com.example.fishy.data.settings.FishySettings
import com.example.fishy.domain.model.ShipmentMode
import com.example.fishy.feature.archive.ArchiveScreen
import com.example.fishy.feature.archive.ShipmentDetailScreen
import com.example.fishy.feature.drafts.DraftsScreen
import com.example.fishy.feature.history.HistoryScreen
import com.example.fishy.feature.home.EasterEggScreen
import com.example.fishy.feature.home.EulaScreen
import com.example.fishy.feature.home.HomeScreen
import com.example.fishy.feature.report.ReportScreen
import com.example.fishy.feature.scheduler.SchedulerScreen
import com.example.fishy.feature.settings.SettingsScreen
import com.example.fishy.feature.shipment.ShipmentScreen
import com.example.fishy.feature.statistics.StatisticsScreen
import com.example.fishy.feature.templates.TemplatesScreen
import kotlinx.coroutines.launch

@Composable
fun FishyNavHost(
    openScheduler: Boolean = false,
    startScheduledId: Long? = null,
    onNotificationNavConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val settingsRepo = FishyApp.instance.settingsRepository
    var settings by remember { mutableStateOf<FishySettings?>(null) }
    LaunchedEffect(Unit) {
        settingsRepo.settings.collect { settings = it }
    }
    val scope = rememberCoroutineScope()
    val versionCode = remember {
        val pm = context.packageManager
        val pkg = context.packageName
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= 28) {
            pm.getPackageInfo(pkg, 0).longVersionCode.toInt()
        } else {
            pm.getPackageInfo(pkg, 0).versionCode
        }
    }
    val loaded = settings
    if (loaded == null) {
        return
    }
    if (loaded.eulaAcceptedVersion < versionCode) {
        EulaScreen(
            onBack = {},
            requireAccept = true,
            onAccepted = {
                scope.launch {
                    settingsRepo.update { it.copy(eulaAcceptedVersion = versionCode) }
                }
            }
        )
        return
    }

    val navController = rememberNavController()

    LaunchedEffect(openScheduler, startScheduledId) {
        when {
            startScheduledId != null && startScheduledId > 0L -> {
                navController.navigate("shipment_from_scheduled/$startScheduledId") {
                    popUpTo(FishyRoute.Home.route)
                }
                onNotificationNavConsumed()
            }
            openScheduler -> {
                navController.navigate(FishyRoute.Scheduler.route) {
                    popUpTo(FishyRoute.Home.route)
                }
                onNotificationNavConsumed()
            }
        }
    }

    NavHost(navController = navController, startDestination = FishyRoute.Home.route) {
        composable(FishyRoute.Home.route) {
            HomeScreen(
                onOpenShipment = { mode ->
                    navController.navigate(FishyRoute.NewShipment.create(mode.name))
                },
                onContinueDraft = { id ->
                    navController.navigate(FishyRoute.EditShipment.create(id))
                },
                onNavigateScheduler = { navController.navigate(FishyRoute.Scheduler.route) },
                onNavigateArchive = { navController.navigate(FishyRoute.Archive.route) },
                onNavigateDrafts = { navController.navigate(FishyRoute.Drafts.route) },
                onNavigateTemplates = { navController.navigate(FishyRoute.Templates.route) },
                onNavigateStatistics = { navController.navigate(FishyRoute.Statistics.route) },
                onNavigateSettings = { navController.navigate(FishyRoute.Settings.route) },
                onNavigateEasterEgg = { navController.navigate(FishyRoute.EasterEgg.route) },
                onNavigateEula = { navController.navigate(FishyRoute.Eula.route) }
            )
        }

        composable(
            route = FishyRoute.NewShipment.route,
            arguments = listOf(navArgument("mode") { type = NavType.StringType })
        ) { entry ->
            val mode = runCatching {
                ShipmentMode.valueOf(entry.arguments?.getString("mode") ?: "MONO")
            }.getOrDefault(ShipmentMode.MONO)
            ShipmentScreen(
                mode = mode,
                draftId = null,
                scheduledId = null,
                onBack = { navController.popBackStack() },
                onOpenHistory = { key -> navController.navigate(FishyRoute.History.create(key)) },
                onShipmentCompleted = { id ->
                    navController.navigate(FishyRoute.ShipmentDetail.create(id)) {
                        popUpTo(FishyRoute.Home.route)
                    }
                }
            )
        }

        composable(
            route = FishyRoute.EditShipment.route,
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: return@composable
            ShipmentScreen(
                mode = null,
                draftId = id,
                scheduledId = null,
                onBack = { navController.popBackStack() },
                onOpenHistory = { key -> navController.navigate(FishyRoute.History.create(key)) },
                onShipmentCompleted = { reportId ->
                    navController.navigate(FishyRoute.ShipmentDetail.create(reportId)) {
                        popUpTo(FishyRoute.Home.route)
                    }
                }
            )
        }

        composable(FishyRoute.Scheduler.route) {
            SchedulerScreen(
                onBack = { navController.popBackStack() },
                onStartShipment = { scheduledId ->
                    navController.navigate("shipment_from_scheduled/$scheduledId")
                }
            )
        }

        composable(
            route = "shipment_from_scheduled/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: return@composable
            ShipmentScreen(
                mode = null,
                draftId = null,
                scheduledId = id,
                onBack = { navController.popBackStack() },
                onOpenHistory = { key -> navController.navigate(FishyRoute.History.create(key)) },
                onShipmentCompleted = { reportId ->
                    navController.navigate(FishyRoute.ShipmentDetail.create(reportId)) {
                        popUpTo(FishyRoute.Home.route)
                    }
                }
            )
        }

        composable(FishyRoute.Archive.route) {
            ArchiveScreen(
                onBack = { navController.popBackStack() },
                onOpen = { id -> navController.navigate(FishyRoute.ShipmentDetail.create(id)) },
                onOpenReport = { id -> navController.navigate(FishyRoute.Report.create(id)) },
                onOpenDraft = { id ->
                    navController.navigate(FishyRoute.EditShipment.create(id)) {
                        popUpTo(FishyRoute.Home.route)
                    }
                }
            )
        }

        composable(FishyRoute.Drafts.route) {
            DraftsScreen(
                onBack = { navController.popBackStack() },
                onOpen = { id -> navController.navigate(FishyRoute.EditShipment.create(id)) }
            )
        }

        composable(FishyRoute.Templates.route) {
            TemplatesScreen(onBack = { navController.popBackStack() })
        }

        composable(FishyRoute.Statistics.route) {
            StatisticsScreen(
                onBack = { navController.popBackStack() },
                onOpenShipment = { id -> navController.navigate(FishyRoute.ShipmentDetail.create(id)) }
            )
        }

        composable(FishyRoute.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(FishyRoute.EasterEgg.route) {
            EasterEggScreen(onBack = { navController.popBackStack() })
        }

        composable(FishyRoute.Eula.route) {
            EulaScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = FishyRoute.ShipmentDetail.route,
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: return@composable
            ShipmentDetailScreen(
                shipmentId = id,
                onBack = { navController.popBackStack() },
                onOpenReport = { navController.navigate(FishyRoute.Report.create(it)) },
                onOpenHistory = { navController.navigate(FishyRoute.History.create(it)) },
                onOpenDraft = { draftId ->
                    navController.navigate(FishyRoute.EditShipment.create(draftId)) {
                        popUpTo(FishyRoute.Home.route)
                    }
                }
            )
        }

        composable(
            route = FishyRoute.Report.route,
            arguments = listOf(navArgument("id") { type = NavType.LongType })
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: return@composable
            ReportScreen(shipmentId = id, onBack = { navController.popBackStack() })
        }

        composable(
            route = FishyRoute.History.route,
            arguments = listOf(navArgument("key") { type = NavType.StringType })
        ) { entry ->
            val key = entry.arguments?.getString("key") ?: return@composable
            HistoryScreen(shipmentKey = key, onBack = { navController.popBackStack() })
        }
    }
}
