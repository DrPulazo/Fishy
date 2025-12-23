package com.example.fishy.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
    val batch: String = "", // Новое поле: партия (П-2 ПБГ М, В-5 НР S и т.д.)
    val packageWeight: Double = 0.0,
    val quantity: Int = 0,
    val totalWeight: Double = 0.0,
    val palletCount: Int = 0,
    val placesCount: Int = 0
)