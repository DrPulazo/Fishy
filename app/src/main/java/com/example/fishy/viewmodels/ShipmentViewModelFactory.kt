package com.example.fishy.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.fishy.database.AppDatabase

class ShipmentViewModelFactory(
    private val context: Context,
    private val database: AppDatabase
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ShipmentViewModel::class.java)) {
            return ShipmentViewModel(database, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}