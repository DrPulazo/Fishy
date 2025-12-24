// AppNavigation.kt
package com.example.fishy.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.fishy.screens.ArchiveScreen
import com.example.fishy.screens.DraftsScreen
import com.example.fishy.screens.MainScreen
import com.example.fishy.screens.NewShipmentScreen
import com.example.fishy.screens.ReportScreen
import com.example.fishy.screens.SchedulerScreen
import com.example.fishy.screens.ShipmentDetailScreen
import com.example.fishy.screens.TemplatesScreen

sealed class Screen(val route: String) {
    object Main : Screen("main")

    // Новая отгрузка (без параметров) - чистая форма
    object NewShipment : Screen("new_shipment")

    // Отгрузка из запланированной (с ID запланированной отгрузки)
    object NewShipmentFromScheduled : Screen("new_shipment/scheduled/{scheduledShipmentId}") {
        fun createRoute(scheduledShipmentId: Long) = "new_shipment/scheduled/$scheduledShipmentId"
    }

    // Отгрузка из черновика (с ID черновика) - НОВЫЙ МАРШРУТ
    object NewShipmentFromDraft : Screen("new_shipment/draft/{draftId}") {
        fun createRoute(draftId: Long) = "new_shipment/draft/$draftId"
    }

    object Archive : Screen("archive")
    object Scheduler : Screen("scheduler")

    // Просмотр деталей отгрузки
    object ShipmentDetail : Screen("shipment_detail/{shipmentId}") {
        fun createRoute(shipmentId: Long) = "shipment_detail/$shipmentId"
    }

    object Drafts : Screen("drafts")
    object Templates : Screen("templates")

    // Отчет
    object Report : Screen("report/{shipmentId}") {
        fun createRoute(shipmentId: Long) = "report/$shipmentId"
    }
}

@Composable
fun AppNavigation(
    fromNotification: Boolean = false,
    navController: NavHostController = rememberNavController()
) {
    // Вспомогательная функция для получения текущего NavController
    val currentNavController = remember { navController }

    // Если открыто из уведомления, автоматически переходим на экран планировщика
    LaunchedEffect(fromNotification) {
        if (fromNotification) {
            // Добавляем небольшую задержку для плавного перехода
            kotlinx.coroutines.delay(100)
            currentNavController.navigate(Screen.Scheduler.route) {
                // Очищаем back stack, чтобы нельзя было вернуться назад к Main
                popUpTo(Screen.Main.route) { inclusive = true }
            }
        }
    }

    NavHost(
        navController = currentNavController,
        startDestination = Screen.Main.route
    ) {
        // Главный экран
        composable(Screen.Main.route) {
            MainScreen(navController = currentNavController)
        }

        // Новая отгрузка (без параметров) - чистая форма
        composable(Screen.NewShipment.route) {
            NewShipmentScreen(
                navController = currentNavController,
                scheduledShipmentId = null,
                draftId = null
            )
        }

        // Отгрузка из запланированной (с ID запланированной отгрузки)
        composable(
            route = Screen.NewShipmentFromScheduled.route,
            arguments = listOf(navArgument("scheduledShipmentId") {
                type = NavType.LongType
                defaultValue = -1L
            })
        ) { backStackEntry ->
            val scheduledShipmentId = backStackEntry.arguments?.getLong("scheduledShipmentId")
                ?.takeIf { it != -1L }

            NewShipmentScreen(
                navController = currentNavController,
                scheduledShipmentId = scheduledShipmentId,
                draftId = null
            )
        }

        // Отгрузка из черновика (с ID черновика) - НОВЫЙ МАРШРУТ
        composable(
            route = Screen.NewShipmentFromDraft.route,
            arguments = listOf(navArgument("draftId") {
                type = NavType.LongType
                defaultValue = -1L
            })
        ) { backStackEntry ->
            val draftId = backStackEntry.arguments?.getLong("draftId")
                ?.takeIf { it != -1L }

            NewShipmentScreen(
                navController = currentNavController,
                scheduledShipmentId = null,
                draftId = draftId
            )
        }

        // Архив отгрузок
        composable(Screen.Archive.route) {
            ArchiveScreen(navController = currentNavController)
        }

        // Планировщик
        composable(Screen.Scheduler.route) {
            SchedulerScreen(navController = currentNavController)
        }

        // Черновики
        composable(Screen.Drafts.route) {
            DraftsScreen(navController = currentNavController)
        }

        // Шаблоны
        composable(Screen.Templates.route) {
            TemplatesScreen(navController = currentNavController)
        }

        // Детали отгрузки
        composable(
            route = Screen.ShipmentDetail.route,
            arguments = listOf(navArgument("shipmentId") {
                type = NavType.LongType
                defaultValue = -1L
            })
        ) { backStackEntry: NavBackStackEntry ->
            val shipmentId = backStackEntry.arguments?.getLong("shipmentId")
                ?.takeIf { it != -1L }

            ShipmentDetailScreen(
                navController = currentNavController,
                shipmentId = shipmentId
            )
        }

        // Отчет
        composable(
            route = Screen.Report.route,
            arguments = listOf(navArgument("shipmentId") {
                type = NavType.LongType
                defaultValue = -1L
            })
        ) { backStackEntry ->
            val shipmentId = backStackEntry.arguments?.getLong("shipmentId")
                ?.takeIf { it != -1L }

            ReportScreen(
                navController = currentNavController,
                shipmentId = shipmentId
            )
        }
    }
}

// Вспомогательные функции для навигации
fun NavController.navigateToNewShipment() {
    navigate(Screen.NewShipment.route)
}

fun NavController.navigateToNewShipmentFromScheduled(scheduledShipmentId: Long) {
    navigate(Screen.NewShipmentFromScheduled.createRoute(scheduledShipmentId))
}

fun NavController.navigateToNewShipmentFromDraft(draftId: Long) {
    navigate(Screen.NewShipmentFromDraft.createRoute(draftId))
}

fun NavController.navigateToShipmentDetail(shipmentId: Long) {
    navigate(Screen.ShipmentDetail.createRoute(shipmentId))
}

fun NavController.navigateToReport(shipmentId: Long) {
    navigate(Screen.Report.createRoute(shipmentId))
}

// Функции для диалогов (если они открываются как экраны)
fun NavController.navigateToChecklistDialog(scheduledShipmentId: Long) {
    // Если ChecklistDialog открывается как отдельный экран, а не как диалог
    navigate("checklist_dialog/$scheduledShipmentId")
}

fun NavController.navigateToScheduledShipmentDialog(scheduledShipmentId: Long? = null) {
    val route = if (scheduledShipmentId != null) {
        "scheduled_shipment_dialog/$scheduledShipmentId"
    } else {
        "scheduled_shipment_dialog"
    }
    navigate(route)
}