package com.example.fishy.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.fishy.screens.*

sealed class Screen(val route: String) {
    object Main : Screen("main")
    object NewShipment : Screen("new_shipment")
    object Archive : Screen("archive")
    object ShipmentDetail : Screen("shipment_detail/{id}") {
        fun createRoute(id: Long) = "shipment_detail/$id"
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Main.route
    ) {
        composable(Screen.Main.route) {
            MainScreen(navController = navController)
        }

        composable(Screen.NewShipment.route) {
            NewShipmentScreen(navController = navController)
        }

        composable(Screen.Archive.route) {
            ArchiveScreen(navController = navController)
        }

        composable(Screen.ShipmentDetail.route) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id")?.toLongOrNull()
            ShipmentDetailScreen(
                navController = navController,
                shipmentId = id
            )
        }
    }
}