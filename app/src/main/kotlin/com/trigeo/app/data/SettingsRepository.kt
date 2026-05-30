package com.trigeo.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.trigeo.app.domain.Defaults
import com.trigeo.app.domain.ReadingDirection
import com.trigeo.app.map.MapTileStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "trigeo_settings")

class SettingsRepository(context: Context) {

    private val store = context.applicationContext.dataStore

    val defaultDirection: Flow<ReadingDirection> =
        store.data.map { prefs ->
            val raw = prefs[KEY_DEFAULT_DIRECTION]
            if (raw != null) {
                ReadingDirection.entries.firstOrNull { it.name == raw } ?: ReadingDirection.NORMAL
            } else if (prefs[KEY_DEFAULT_BIDIRECTIONAL] == true) {
                ReadingDirection.BIDIRECTIONAL
            } else {
                ReadingDirection.NORMAL
            }
        }

    suspend fun setDefaultDirection(value: ReadingDirection) {
        store.edit { it[KEY_DEFAULT_DIRECTION] = value.name }
    }

    val tileStyle: Flow<MapTileStyle> =
        store.data.map { prefs ->
            val raw = prefs[KEY_TILE_STYLE]
            MapTileStyle.entries.firstOrNull { it.name == raw } ?: MapTileStyle.OSM
        }

    suspend fun setTileStyle(value: MapTileStyle) {
        store.edit { it[KEY_TILE_STYLE] = value.name }
    }

    val defaultUncertaintyDeg: Flow<Float> =
        store.data.map { prefs ->
            (prefs[KEY_DEFAULT_UNCERTAINTY] ?: Defaults.UNCERTAINTY_DEG.toFloat())
                .coerceIn(1f, 30f)
        }

    suspend fun setDefaultUncertaintyDeg(value: Float) {
        store.edit { it[KEY_DEFAULT_UNCERTAINTY] = value.coerceIn(1f, 30f) }
    }

    val tipButtonEnabled: Flow<Boolean> =
        store.data.map { it[KEY_TIP_BUTTON_ENABLED] ?: true }

    suspend fun setTipButtonEnabled(value: Boolean) {
        store.edit { it[KEY_TIP_BUTTON_ENABLED] = value }
    }

    val minFixRangeMeters: Flow<Float> =
        store.data.map { prefs ->
            (prefs[KEY_MIN_FIX_RANGE] ?: Defaults.MIN_FIX_RANGE_METERS.toFloat())
                .coerceIn(MIN_FIX_RANGE_MIN, MIN_FIX_RANGE_MAX)
        }

    suspend fun setMinFixRangeMeters(value: Float) {
        store.edit { it[KEY_MIN_FIX_RANGE] = value.coerceIn(MIN_FIX_RANGE_MIN, MIN_FIX_RANGE_MAX) }
    }

    private companion object {
        const val MIN_FIX_RANGE_MIN = 5f
        const val MIN_FIX_RANGE_MAX = 50f

        val KEY_DEFAULT_BIDIRECTIONAL = booleanPreferencesKey("default_bidirectional")
        val KEY_DEFAULT_DIRECTION = stringPreferencesKey("default_direction")
        val KEY_TILE_STYLE = stringPreferencesKey("tile_style")
        val KEY_DEFAULT_UNCERTAINTY = floatPreferencesKey("default_uncertainty_deg")
        val KEY_TIP_BUTTON_ENABLED = booleanPreferencesKey("tip_button_enabled")
        val KEY_MIN_FIX_RANGE = floatPreferencesKey("min_fix_range_meters")
    }
}
