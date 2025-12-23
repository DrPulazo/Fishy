// AppNavigation.kt
package com.example.fishy.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.fishy.screens.*

sealed class Screen(val route: String) {
    object Main : Screen("main")
    object NewShipment : Screen("new_shipment")
    object NewShipmentFromScheduled : Screen("new_shipment/{scheduledShipmentId}") {
        fun createRoute(scheduledShipmentId: Long) = "new_shipment/$scheduledShipmentId"
    }
    object Archive : Screen("archive")
    object Scheduler : Screen("scheduler")
    object EditScheduledShipment : Screen("edit_scheduled_shipment/{id}") {
        fun createRoute(id: Long) = "edit_scheduled_shipment/$id"
    }
    object ShipmentDetail : Screen("shipment_detail/{id}") {
        fun createRoute(id: Long) = "shipment_detail/$id"
    }
    object Drafts : Screen("drafts")
    object Templates : Screen("templates")
    object ReportScreen : Screen("report/{shipmentId}")
}

@Composable
fun AppNavigation(fromNotification: Boolean = false) {
    val navController = rememberNavController()

    // Если открыто из уведомления, автоматически переходим на экран планировщика
    LaunchedEffect(fromNotification) {
        if (fromNotification) {
            // Добавляем небольшую задержку для плавного перехода
            kotlinx.coroutines.delay(100)
            navController.navigate(Screen.Scheduler.route) {
                // Очищаем back stack, чтобы нельзя было вернуться назад к Main
                popUpTo(Screen.Main.route) { inclusive = true }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Main.route
    ) {
        composable(Screen.Main.route) {
            MainScreen(navController = navController)
        }

        composable(
            route = Screen.ReportScreen.route,
            arguments = listOf(navArgument("shipmentId") { type = NavType.LongType })
        ) { backStackEntry ->
            val shipmentId = backStackEntry.arguments?.getLong("shipmentId")
            ReportScreen(navController, shipmentId)
        }

        composable(Screen.NewShipment.route) {
            NewShipmentScreen(navController = navController, scheduledShipmentId = null)
        }

        composable(Screen.NewShipmentFromScheduled.route) { backStackEntry ->
            val scheduledShipmentId = backStackEntry.arguments?.getString("scheduledShipmentId")?.toLongOrNull()
            NewShipmentScreen(
                navController = navController,
                scheduledShipmentId = scheduledShipmentId
            )
        }

        composable(Screen.Archive.route) {
            ArchiveScreen(navController = navController)
        }

        composable(Screen.Drafts.route) {
            DraftsScreen(navController = navController)
        }

        composable(Screen.Templates.route) {
            TemplatesScreen(navController = navController)
        }

        composable(Screen.Scheduler.route) {
            SchedulerScreen(navController = navController)
        }

        composable(Screen.ShipmentDetail.route) { backStackEntry: NavBackStackEntry ->
            val id = backStackEntry.arguments?.getString("id")?.toLongOrNull()
            ShipmentDetailScreen(
                navController = navController,
                shipmentId = id
            )
        }
    }
}