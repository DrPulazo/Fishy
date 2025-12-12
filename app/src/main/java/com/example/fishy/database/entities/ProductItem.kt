package com.example.fishy.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "product_items",
    foreignKeys = [ForeignKey(
        entity = Shipment::class,
        parentColumns = ["id"],
        childColumns = ["shipmentId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["shipmentId"])]
)
data class ProductItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val shipmentId: Long = 0,
    val name: String = "",
    val manufacturer: String = "",
    val packageWeight: Double = 0.0,
    val quantity: Int = 0,
    val totalWeight: Double = 0.0,
    val palletCount: Int = 0,
    val placesCount: Int = 0
)