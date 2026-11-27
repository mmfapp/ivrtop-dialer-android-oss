package com.ivrtop.dialer.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "ivrtop_settings")

/**
 * Non-secret settings. Stored as one JSON document so the schema is identical to the iOS
 * UserDefaults representation (contracts/schemas/app-settings.schema.json).
 */
@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val key = stringPreferencesKey("settings_json_v1")

    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { e -> Timber.e(e, "settings read failed"); emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { prefs -> prefs[key]?.let { decode(it) } ?: AppSettings() }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { prefs ->
            val cur = prefs[key]?.let { decode(it) } ?: AppSettings()
            prefs[key] = json.encodeToString(AppSettings.serializer(), transform(cur))
        }
    }

    suspend fun clear() = context.settingsDataStore.edit { it.clear() }

    private fun decode(raw: String): AppSettings = try {
        json.decodeFromString(AppSettings.serializer(), raw)
    } catch (e: Exception) {
        Timber.w(e, "settings corrupt – resetting")
        AppSettings()
    }
}
