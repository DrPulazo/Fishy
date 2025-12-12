package com.example.fishy.viewmodels

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.fishy.database.AppDatabase

class ShipmentViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ShipmentViewModel::class.java)) {
            val database = AppDatabase.getDatabase(application)
            @Suppress("UNCHECKED_CAST")
            return ShipmentViewModel(database) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}