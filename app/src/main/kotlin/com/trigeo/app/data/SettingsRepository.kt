package com.trigeo.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
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

    private companion object {
        val KEY_DEFAULT_BIDIRECTIONAL = booleanPreferencesKey("default_bidirectional")
    }
}
