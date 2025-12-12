package com.example.fishy.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "shipments")
data class Shipment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val containerNumber: String = "",
    val truckNumber: String = "",
    val trailerNumber: String = "",
    val wagonNumber: String = "",
    val sealNumber: String = "",
    val port: String = "",
    val vessel: String = "",
    val customer: String = "",
    val createdAt: Date = Date(),
    val totalProductTypes: Int = 0,
    val totalPallets: Int = 0,
    val totalPlaces: Int = 0,
    val totalWeight: Double = 0.0
)