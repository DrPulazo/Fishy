package com.example.fishy.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.fishy.database.entities.Pallet
import com.example.fishy.database.entities.ProductItem
import com.example.fishy.database.entities.Shipment
import kotlinx.coroutines.flow.Flow

@Dao
interface ShipmentDao {

    // Shipment operations
    @Query("SELECT * FROM shipments ORDER BY createdAt DESC")
    fun getAllShipments(): Flow<List<Shipment>>

    @Query("SELECT * FROM shipments WHERE id = :id")
    suspend fun getShipmentById(id: Long): Shipment?

    @Insert
    suspend fun insertShipment(shipment: Shipment): Long

    @Update
    suspend fun updateShipment(shipment: Shipment)

    @Delete
    suspend fun deleteShipment(shipment: Shipment)

    // ProductItem operations
    @Query("SELECT * FROM product_items WHERE shipmentId = :shipmentId ORDER BY id")
    fun getProductItemsForShipment(shipmentId: Long): Flow<List<ProductItem>>

    @Insert
    suspend fun insertProductItem(productItem: ProductItem): Long

    @Update
    suspend fun updateProductItem(productItem: ProductItem)

    @Delete
    suspend fun deleteProductItem(productItem: ProductItem)

    // Pallet operations
    @Query("SELECT * FROM pallets WHERE productItemId = :productItemId ORDER BY palletNumber")
    fun getPalletsForProductItem(productItemId: Long): Flow<List<Pallet>>

    @Insert
    suspend fun insertPallet(pallet: Pallet): Long

    @Update
    suspend fun updatePallet(pallet: Pallet)

    @Delete
    suspend fun deletePallet(pallet: Pallet)

    @Query("DELETE FROM pallets WHERE productItemId = :productItemId")
    suspend fun deletePalletsForProductItem(productItemId: Long)
}