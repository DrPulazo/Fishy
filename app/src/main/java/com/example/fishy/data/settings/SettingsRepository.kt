package com.example.fishy.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("fishy_settings")

enum class ThemeMode { DARK, LIGHT, SYSTEM }

enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    RU("ru"),
    EN("en"),
    ZH("zh"),
    KO("ko"),
    JA("ja"),
    ES("es")
}

data class FishySettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val language: AppLanguage = AppLanguage.RU,
    val inputGuardEnabled: Boolean = false,
    val maxPlaceWeightKg: Double = 0.0,
    val maxPlacesPerPallet: Int = 0,
    /** Master switch for number auto-spacing; subtype checkboxes apply only when enabled. */
    val autoSpacesEnabled: Boolean = false,
    val autoSpaceContainers: Boolean = false,
    val autoSpaceVehicles: Boolean = false,
    /** Floating smart «+» on shipment screens. */
    val floatingFabEnabled: Boolean = true,
    val defaultBatchWarnThreshold: Int = 5,
    /** How many About opens while Russian UI is active before the next visit (0..11). Resets after 12. */
    val aboutOpenCount: Int = 0
) {
    val effectiveAutoSpaceContainers: Boolean get() = autoSpacesEnabled && autoSpaceContainers
    val effectiveAutoSpaceVehicles: Boolean get() = autoSpacesEnabled && autoSpaceVehicles
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val language = stringPreferencesKey("language")
        val inputGuard = booleanPreferencesKey("input_guard")
        val maxWeight = doublePreferencesKey("max_place_weight")
        val maxPlaces = intPreferencesKey("max_places_pallet")
        val spaceEnabled = booleanPreferencesKey("space_enabled")
        val spaceContainers = booleanPreferencesKey("space_containers")
        val spaceVehicles = booleanPreferencesKey("space_vehicles")
        val floatingFab = booleanPreferencesKey("floating_fab")
        val batchWarn = intPreferencesKey("batch_warn")
        val aboutOpenCount = intPreferencesKey("about_open_count")
    }

    val settings: Flow<FishySettings> = context.dataStore.data.map { prefs ->
        prefs.toFishySettings()
    }

    suspend fun update(transform: (FishySettings) -> FishySettings) {
        context.dataStore.edit { prefs ->
            val next = transform(prefs.toFishySettings())
            prefs[Keys.theme] = next.themeMode.name
            // Persist BCP-47 tag; SYSTEM uses empty string so AppCompat can follow the device.
            prefs[Keys.language] = next.language.tag
            prefs[Keys.inputGuard] = next.inputGuardEnabled
            prefs[Keys.maxWeight] = next.maxPlaceWeightKg
            prefs[Keys.maxPlaces] = next.maxPlacesPerPallet
            prefs[Keys.spaceEnabled] = next.autoSpacesEnabled
            prefs[Keys.spaceContainers] = next.autoSpaceContainers
            prefs[Keys.spaceVehicles] = next.autoSpaceVehicles
            prefs[Keys.floatingFab] = next.floatingFabEnabled
            prefs[Keys.batchWarn] = next.defaultBatchWarnThreshold
            prefs[Keys.aboutOpenCount] = next.aboutOpenCount
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    private fun Preferences.toFishySettings(): FishySettings {
        val languageRaw = this[Keys.language]
        val language = when {
            languageRaw == null || languageRaw.isBlank() || languageRaw == "SYSTEM" ->
                AppLanguage.RU
            else -> AppLanguage.entries.find { it.tag == languageRaw || it.name == languageRaw }
                ?.takeUnless { it == AppLanguage.SYSTEM }
                ?: AppLanguage.RU
        }
        val spaceContainers = this[Keys.spaceContainers] ?: false
        val spaceVehicles = this[Keys.spaceVehicles] ?: false
        // Legacy: if either subtype was on before the master switch existed, treat master as on.
        val spaceEnabled = this[Keys.spaceEnabled] ?: (spaceContainers || spaceVehicles)
        return FishySettings(
            themeMode = this[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?.let { if (it == ThemeMode.SYSTEM) ThemeMode.DARK else it }
                ?: ThemeMode.DARK,
            language = language,
            inputGuardEnabled = this[Keys.inputGuard] ?: false,
            maxPlaceWeightKg = this[Keys.maxWeight] ?: 0.0,
            maxPlacesPerPallet = this[Keys.maxPlaces] ?: 0,
            autoSpacesEnabled = spaceEnabled,
            autoSpaceContainers = spaceContainers,
            autoSpaceVehicles = spaceVehicles,
            floatingFabEnabled = this[Keys.floatingFab] ?: true,
            defaultBatchWarnThreshold = this[Keys.batchWarn] ?: 5,
            aboutOpenCount = this[Keys.aboutOpenCount] ?: 0
        )
    }
}
