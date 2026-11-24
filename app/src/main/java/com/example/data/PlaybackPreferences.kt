package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.i18n.AppLanguage
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "plinth_prefs")

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val language: AppLanguage = AppLanguage.EN,
    val hapticsEnabled: Boolean = true,
    val reducedMotion: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.ALL,
    val lastTrackId: String? = null,
    val lastPositionMs: Long = 0L,
    val eqEnabled: Boolean = false,
    val eqPresetIndex: Int = 0,
    val eqBands: List<Int> = listOf(0, 0, 0, 0, 0),
)

class PlaybackPreferences(private val context: Context) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val HAPTICS = booleanPreferencesKey("haptics")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        val SHUFFLE = booleanPreferencesKey("shuffle")
        val REPEAT_MODE = stringPreferencesKey("repeat_mode")
        val LAST_TRACK_ID = stringPreferencesKey("last_track_id")
        val LAST_POSITION_MS = longPreferencesKey("last_position_ms")
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val EQ_PRESET = intPreferencesKey("eq_preset")
        val EQ_BANDS = stringPreferencesKey("eq_bands")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val theme = runCatching {
            ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.LIGHT.name)
        }.getOrDefault(ThemeMode.LIGHT)

        val lang = runCatching {
            AppLanguage.valueOf(prefs[Keys.LANGUAGE] ?: AppLanguage.EN.name)
        }.getOrDefault(AppLanguage.EN)

        val repeat = runCatching {
            RepeatMode.valueOf(prefs[Keys.REPEAT_MODE] ?: RepeatMode.ALL.name)
        }.getOrDefault(RepeatMode.ALL)

        val bands = prefs[Keys.EQ_BANDS]
            ?.split(",")
            ?.mapNotNull { it.toIntOrNull() }
            ?.takeIf { it.size == 5 }
            ?: listOf(0, 0, 0, 0, 0)

        UserPreferences(
            themeMode = theme,
            language = lang,
            hapticsEnabled = prefs[Keys.HAPTICS] ?: true,
            reducedMotion = prefs[Keys.REDUCED_MOTION] ?: false,
            shuffleEnabled = prefs[Keys.SHUFFLE] ?: false,
            repeatMode = repeat,
            lastTrackId = prefs[Keys.LAST_TRACK_ID],
            lastPositionMs = prefs[Keys.LAST_POSITION_MS] ?: 0L,
            eqEnabled = prefs[Keys.EQ_ENABLED] ?: false,
            eqPresetIndex = prefs[Keys.EQ_PRESET] ?: 0,
            eqBands = bands,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.name }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HAPTICS] = enabled }
    }

    suspend fun setReducedMotion(enabled: Boolean) {
        context.dataStore.edit { it[Keys.REDUCED_MOTION] = enabled }
    }

    suspend fun setShuffleEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHUFFLE] = enabled }
    }

    suspend fun setRepeatMode(mode: RepeatMode) {
        context.dataStore.edit { it[Keys.REPEAT_MODE] = mode.name }
    }

    suspend fun saveLastPlayback(trackId: String, positionMs: Long) {
        context.dataStore.edit {
            it[Keys.LAST_TRACK_ID] = trackId
            it[Keys.LAST_POSITION_MS] = positionMs
        }
    }

    suspend fun saveEqualizer(enabled: Boolean, presetIndex: Int, bands: List<Int>) {
        context.dataStore.edit {
            it[Keys.EQ_ENABLED] = enabled
            it[Keys.EQ_PRESET] = presetIndex
            it[Keys.EQ_BANDS] = bands.joinToString(",")
        }
    }
}
