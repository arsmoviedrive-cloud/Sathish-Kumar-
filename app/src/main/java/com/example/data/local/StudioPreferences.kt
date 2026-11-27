package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.ResolutionOption
import com.example.ui.theme.StudioThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.studioDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "framecut_studio_prefs"
)

data class LocalStudioSettings(
    val themeMode: StudioThemeMode = StudioThemeMode.OLED_DARK,
    val batterySaverEnabled: Boolean = false,
    val defaultResolution: ResolutionOption = ResolutionOption.UHD_4K,
    val autoOfflineBackup: Boolean = true
)

class StudioPreferences(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val BATTERY_SAVER = booleanPreferencesKey("battery_saver")
        val DEFAULT_RESOLUTION = stringPreferencesKey("default_resolution")
        val AUTO_OFFLINE_BACKUP = booleanPreferencesKey("auto_offline_backup")
    }

    val settingsFlow: Flow<LocalStudioSettings> = context.studioDataStore.data.map { prefs ->
        LocalStudioSettings(
            themeMode = StudioThemeMode.fromRaw(prefs[Keys.THEME_MODE]),
            batterySaverEnabled = prefs[Keys.BATTERY_SAVER] ?: false,
            defaultResolution = ResolutionOption.fromRaw(prefs[Keys.DEFAULT_RESOLUTION]),
            autoOfflineBackup = prefs[Keys.AUTO_OFFLINE_BACKUP] ?: true
        )
    }

    suspend fun setThemeMode(mode: StudioThemeMode) {
        context.studioDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setBatterySaver(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.BATTERY_SAVER] = enabled }
    }

    suspend fun setDefaultResolution(resolution: ResolutionOption) {
        context.studioDataStore.edit { it[Keys.DEFAULT_RESOLUTION] = resolution.raw }
    }

    suspend fun setAutoOfflineBackup(enabled: Boolean) {
        context.studioDataStore.edit { it[Keys.AUTO_OFFLINE_BACKUP] = enabled }
    }
}
