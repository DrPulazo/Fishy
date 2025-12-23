package com.example.fishy.viewmodels

data class DoubleControlStats(
    val totalPallets: Int = 0,
    val exportedPallets: Int = 0,
    val importedPallets: Int = 0,
    val totalPlaces: Int = 0,
    val exportedPlaces: Int = 0,
    val importedPlaces: Int = 0
) {
    val remainingExportPallets: Int get() = totalPallets - exportedPallets
    val remainingExportPlaces: Int get() = totalPlaces - exportedPlaces
    val remainingImportPallets: Int get() = exportedPallets - importedPallets
    val remainingImportPlaces: Int get() = exportedPlaces - importedPlaces
    val isComplete: Boolean get() = importedPallets == exportedPallets && importedPlaces == exportedPlaces
}