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
import com.trigeo.app.map.MapTileStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "trigeo_settings")

class SettingsRepository(context: Context) {

    private val store = context.applicationContext.dataStore

    val defaultBidirectional: Flow<Boolean> =
        store.data.map { it[KEY_DEFAULT_BIDIRECTIONAL] ?: false }

    suspend fun setDefaultBidirectional(value: Boolean) {
        store.edit { it[KEY_DEFAULT_BIDIRECTIONAL] = value }
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

    private companion object {
        val KEY_DEFAULT_BIDIRECTIONAL = booleanPreferencesKey("default_bidirectional")
        val KEY_TILE_STYLE = stringPreferencesKey("tile_style")
        val KEY_DEFAULT_UNCERTAINTY = floatPreferencesKey("default_uncertainty_deg")
    }
}
