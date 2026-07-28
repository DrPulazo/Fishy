package com.example.fishy.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
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
    ES("es"),
    ZH("zh"),
    KO("ko"),
    JA("ja")
}

data class FishySettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.RU,
    val inputGuardEnabled: Boolean = false,
    val maxPlaceWeightKg: Double = 0.0,
    val maxPlacesPerPallet: Int = 0,
    /** Master switch for number auto-spacing; subtype checkboxes apply only when enabled. */
    val autoSpacesEnabled: Boolean = false,
    val autoSpaceContainers: Boolean = false,
    val autoSpaceVehicles: Boolean = false,
    /** Group places/weight with a space between thousands (27 000). Off by default. */
    val thousandsSeparatorEnabled: Boolean = false,
    /** Floating smart «+» on shipment screens. */
    val floatingFabEnabled: Boolean = true,
    /** Prefill places when adding pallets (field above «Add pallet» + FAB). */
    val simplifiedCounterEnabled: Boolean = false,
    /** In-app haptics (errors, delete confirms, etc.). Wipe-data dialogs always vibrate. */
    val vibrationEnabled: Boolean = true,
    val defaultBatchWarnThreshold: Int = 5,
    /** How many About opens while Russian UI is active before the next visit (0..11). Resets after 12. */
    val aboutOpenCount: Int = 0,
    /** App versionCode for which the user agreement was accepted; 0 = never. */
    val eulaAcceptedVersion: Int = 0,
    /** One-time tip: long-press FAB to drag (shown when FAB is visible). */
    val fabDragTipSeen: Boolean = false,
    /** One-time tip: swipe pallet row to delete (shown after first pallet appears). */
    val palletSwipeTipSeen: Boolean = false,
    /**
     * Last FAB position as fraction of drag range (0..1). Negative = never moved → use default.
     */
    val fabPosXFraction: Float = -1f,
    val fabPosYFraction: Float = -1f
) {
    val effectiveAutoSpaceContainers: Boolean get() = autoSpacesEnabled && autoSpaceContainers
    val effectiveAutoSpaceVehicles: Boolean get() = autoSpacesEnabled && autoSpaceVehicles
    val effectiveThousandsSeparator: Boolean get() = autoSpacesEnabled && thousandsSeparatorEnabled
}

class SettingsRepository(private val context: Context) {

    /**
     * Hot cache for [ErrorFeedback] (main-thread vibrate checks without collecting Flow).
     * Updated on every [update] / read of preferences.
     */
    @Volatile
    var vibrationEnabledCached: Boolean = true
        private set

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val language = stringPreferencesKey("language")
        val inputGuard = booleanPreferencesKey("input_guard")
        val maxWeight = doublePreferencesKey("max_place_weight")
        val maxPlaces = intPreferencesKey("max_places_pallet")
        val spaceEnabled = booleanPreferencesKey("space_enabled")
        val spaceContainers = booleanPreferencesKey("space_containers")
        val spaceVehicles = booleanPreferencesKey("space_vehicles")
        val thousandsSeparator = booleanPreferencesKey("thousands_separator")
        val floatingFab = booleanPreferencesKey("floating_fab")
        val simplifiedCounter = booleanPreferencesKey("simplified_counter")
        val vibration = booleanPreferencesKey("vibration_enabled")
        val batchWarn = intPreferencesKey("batch_warn")
        val aboutOpenCount = intPreferencesKey("about_open_count")
        val eulaAcceptedVersion = intPreferencesKey("eula_accepted_version")
        val fabDragTipSeen = booleanPreferencesKey("fab_drag_tip_seen")
        val palletSwipeTipSeen = booleanPreferencesKey("pallet_swipe_tip_seen")
        val fabPosXFraction = floatPreferencesKey("fab_pos_x_frac")
        val fabPosYFraction = floatPreferencesKey("fab_pos_y_frac")
    }

    val settings: Flow<FishySettings> = context.dataStore.data.map { prefs ->
        prefs.toFishySettings().also { vibrationEnabledCached = it.vibrationEnabled }
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
            prefs[Keys.thousandsSeparator] = next.thousandsSeparatorEnabled
            prefs[Keys.floatingFab] = next.floatingFabEnabled
            prefs[Keys.simplifiedCounter] = next.simplifiedCounterEnabled
            prefs[Keys.vibration] = next.vibrationEnabled
            prefs[Keys.batchWarn] = next.defaultBatchWarnThreshold
            vibrationEnabledCached = next.vibrationEnabled
            prefs[Keys.aboutOpenCount] = next.aboutOpenCount
            prefs[Keys.eulaAcceptedVersion] = next.eulaAcceptedVersion
            prefs[Keys.fabDragTipSeen] = next.fabDragTipSeen
            prefs[Keys.palletSwipeTipSeen] = next.palletSwipeTipSeen
            prefs[Keys.fabPosXFraction] = next.fabPosXFraction
            prefs[Keys.fabPosYFraction] = next.fabPosYFraction
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
                ?: ThemeMode.SYSTEM,
            language = language,
            inputGuardEnabled = this[Keys.inputGuard] ?: false,
            maxPlaceWeightKg = this[Keys.maxWeight] ?: 0.0,
            maxPlacesPerPallet = this[Keys.maxPlaces] ?: 0,
            autoSpacesEnabled = spaceEnabled,
            autoSpaceContainers = spaceContainers,
            autoSpaceVehicles = spaceVehicles,
            thousandsSeparatorEnabled = this[Keys.thousandsSeparator] ?: false,
            floatingFabEnabled = this[Keys.floatingFab] ?: true,
            simplifiedCounterEnabled = this[Keys.simplifiedCounter] ?: false,
            vibrationEnabled = this[Keys.vibration] ?: true,
            defaultBatchWarnThreshold = this[Keys.batchWarn] ?: 5,
            aboutOpenCount = this[Keys.aboutOpenCount] ?: 0,
            eulaAcceptedVersion = this[Keys.eulaAcceptedVersion] ?: 0,
            fabDragTipSeen = this[Keys.fabDragTipSeen] ?: false,
            palletSwipeTipSeen = this[Keys.palletSwipeTipSeen] ?: false,
            fabPosXFraction = this[Keys.fabPosXFraction] ?: -1f,
            fabPosYFraction = this[Keys.fabPosYFraction] ?: -1f
        )
    }
}
