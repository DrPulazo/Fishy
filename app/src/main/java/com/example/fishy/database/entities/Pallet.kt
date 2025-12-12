package com.example.fishy.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "pallets",
    foreignKeys = [ForeignKey(
        entity = ProductItem::class,
        parentColumns = ["id"],
        childColumns = ["productItemId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["productItemId"])]
)
data class Pallet(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val productItemId: Long = 0,
    val palletNumber: Int = 1,
    val places: Int = 0
)