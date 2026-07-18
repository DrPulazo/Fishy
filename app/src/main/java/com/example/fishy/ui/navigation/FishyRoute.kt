package com.example.fishy.ui.navigation

sealed class FishyRoute(val route: String) {
    data object Home : FishyRoute("home")
    data object NewShipment : FishyRoute("new_shipment/{mode}") {
        fun create(mode: String) = "new_shipment/$mode"
    }
    data object EditShipment : FishyRoute("edit_shipment/{id}") {
        fun create(id: Long) = "edit_shipment/$id"
    }
    data object Scheduler : FishyRoute("scheduler")
    data object Archive : FishyRoute("archive")
    data object Drafts : FishyRoute("drafts")
    data object Templates : FishyRoute("templates")
    data object Statistics : FishyRoute("statistics")
    data object Settings : FishyRoute("settings")
    data object EasterEgg : FishyRoute("easter_egg")
    data object Eula : FishyRoute("eula")
    data object ShipmentDetail : FishyRoute("shipment_detail/{id}") {
        fun create(id: Long) = "shipment_detail/$id"
    }
    data object Report : FishyRoute("report/{id}") {
        fun create(id: Long) = "report/$id"
    }
    data object History : FishyRoute("history/{key}") {
        fun create(key: String) = "history/${android.net.Uri.encode(key)}"
    }
}
