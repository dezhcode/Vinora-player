package com.example.data

import android.content.Context
import androidx.compose.runtime.Immutable
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
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "plinth_prefs")

enum class GraphicsQuality {
    AUTO, HIGH, MEDIUM, LOW
}

@Immutable
data class CustomEqPreset(
    val id: String,
    val name: String,
    val bandsDb: List<Int>,
)

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val language: AppLanguage = AppLanguage.EN,
    val hapticsEnabled: Boolean = true,
    val uiSoundsEnabled: Boolean = true,
    val graphicsQuality: GraphicsQuality = GraphicsQuality.AUTO,
    val batterySaverMode: Boolean = false,
    val reducedMotion: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.ALL,
    val lastTrackId: String? = null,
    val lastPositionMs: Long = 0L,
    val eqEnabled: Boolean = false,
    val eqPresetIndex: Int = 0,
    val eqBands: List<Int> = listOf(0, 0, 0, 0, 0),
    val eqCustomPresetId: String? = null,
    val customEqPresets: List<CustomEqPreset> = emptyList(),
    val crossfadeEnabled: Boolean = true,
    val homeSortField: HomeSortField = HomeSortField.DATE,
    val homeSortAscending: Boolean = false,
    val homeFilterType: HomeFilterType = HomeFilterType.ALL,
)

class PlaybackPreferences(private val context: Context) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val HAPTICS = booleanPreferencesKey("haptics")
        val UI_SOUNDS = booleanPreferencesKey("ui_sounds")
        val GRAPHICS_QUALITY = stringPreferencesKey("graphics_quality")
        val BATTERY_SAVER = booleanPreferencesKey("battery_saver")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        val SHUFFLE = booleanPreferencesKey("shuffle")
        val REPEAT_MODE = stringPreferencesKey("repeat_mode")
        val LAST_TRACK_ID = stringPreferencesKey("last_track_id")
        val LAST_POSITION_MS = longPreferencesKey("last_position_ms")
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val EQ_PRESET = intPreferencesKey("eq_preset")
        val EQ_BANDS = stringPreferencesKey("eq_bands")
        val EQ_CUSTOM_PRESET_ID = stringPreferencesKey("eq_custom_preset_id")
        val CUSTOM_EQ_PRESETS = stringPreferencesKey("custom_eq_presets")
        val CROSSFADE_ENABLED = booleanPreferencesKey("crossfade_enabled")
        val HOME_SORT_FIELD = stringPreferencesKey("home_sort_field")
        val HOME_SORT_ASCENDING = booleanPreferencesKey("home_sort_ascending")
        val HOME_FILTER_TYPE = stringPreferencesKey("home_filter_type")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val theme = runCatching {
            ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.LIGHT.name)
        }.getOrDefault(ThemeMode.LIGHT)

        val lang = runCatching {
            AppLanguage.valueOf(prefs[Keys.LANGUAGE] ?: AppLanguage.EN.name)
        }.getOrDefault(AppLanguage.EN)

        val quality = runCatching {
            GraphicsQuality.valueOf(prefs[Keys.GRAPHICS_QUALITY] ?: GraphicsQuality.AUTO.name)
        }.getOrDefault(GraphicsQuality.AUTO)

        val repeat = runCatching {
            RepeatMode.valueOf(prefs[Keys.REPEAT_MODE] ?: RepeatMode.ALL.name)
        }.getOrDefault(RepeatMode.ALL)

        val bands = prefs[Keys.EQ_BANDS]
            ?.split(",")
            ?.mapNotNull { it.toIntOrNull() }
            ?.takeIf { it.size == 5 }
            ?: listOf(0, 0, 0, 0, 0)

        val customPresets = decodeCustomPresets(prefs[Keys.CUSTOM_EQ_PRESETS])

        val homeSortField = runCatching {
            HomeSortField.valueOf(prefs[Keys.HOME_SORT_FIELD] ?: HomeSortField.DATE.name)
        }.getOrDefault(HomeSortField.DATE)

        val homeFilterType = runCatching {
            HomeFilterType.valueOf(prefs[Keys.HOME_FILTER_TYPE] ?: HomeFilterType.ALL.name)
        }.getOrDefault(HomeFilterType.ALL)

        UserPreferences(
            themeMode = theme,
            language = lang,
            hapticsEnabled = prefs[Keys.HAPTICS] ?: true,
            uiSoundsEnabled = prefs[Keys.UI_SOUNDS] ?: true,
            graphicsQuality = quality,
            batterySaverMode = prefs[Keys.BATTERY_SAVER] ?: false,
            reducedMotion = prefs[Keys.REDUCED_MOTION] ?: false,
            shuffleEnabled = prefs[Keys.SHUFFLE] ?: false,
            repeatMode = repeat,
            lastTrackId = prefs[Keys.LAST_TRACK_ID],
            lastPositionMs = prefs[Keys.LAST_POSITION_MS] ?: 0L,
            eqEnabled = prefs[Keys.EQ_ENABLED] ?: false,
            eqPresetIndex = prefs[Keys.EQ_PRESET] ?: 0,
            eqBands = bands,
            eqCustomPresetId = prefs[Keys.EQ_CUSTOM_PRESET_ID]?.takeIf { it.isNotBlank() },
            customEqPresets = customPresets,
            crossfadeEnabled = prefs[Keys.CROSSFADE_ENABLED] ?: true,
            homeSortField = homeSortField,
            homeSortAscending = prefs[Keys.HOME_SORT_ASCENDING] ?: homeSortField.defaultAscending(),
            homeFilterType = homeFilterType,
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

    suspend fun setUiSoundsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.UI_SOUNDS] = enabled }
    }

    suspend fun setGraphicsQuality(quality: GraphicsQuality) {
        context.dataStore.edit { it[Keys.GRAPHICS_QUALITY] = quality.name }
    }

    suspend fun setBatterySaverMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BATTERY_SAVER] = enabled }
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

    suspend fun setCrossfadeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CROSSFADE_ENABLED] = enabled }
    }

    suspend fun setHomeSort(field: HomeSortField, ascending: Boolean) {
        context.dataStore.edit {
            it[Keys.HOME_SORT_FIELD] = field.name
            it[Keys.HOME_SORT_ASCENDING] = ascending
        }
    }

    suspend fun setHomeFilter(filter: HomeFilterType) {
        context.dataStore.edit { it[Keys.HOME_FILTER_TYPE] = filter.name }
    }

    suspend fun saveLastPlayback(trackId: String, positionMs: Long) {
        context.dataStore.edit {
            it[Keys.LAST_TRACK_ID] = trackId
            it[Keys.LAST_POSITION_MS] = positionMs
        }
    }

    suspend fun saveEqualizer(
        enabled: Boolean,
        presetIndex: Int,
        bands: List<Int>,
        customPresetId: String? = null,
    ) {
        context.dataStore.edit {
            it[Keys.EQ_ENABLED] = enabled
            it[Keys.EQ_PRESET] = presetIndex
            it[Keys.EQ_BANDS] = bands.joinToString(",")
            if (customPresetId != null) {
                it[Keys.EQ_CUSTOM_PRESET_ID] = customPresetId
            } else {
                it.remove(Keys.EQ_CUSTOM_PRESET_ID)
            }
        }
    }

    suspend fun saveCustomEqPresets(presets: List<CustomEqPreset>) {
        context.dataStore.edit {
            it[Keys.CUSTOM_EQ_PRESETS] = encodeCustomPresets(presets)
        }
    }

    companion object {
        fun encodeCustomPresets(presets: List<CustomEqPreset>): String {
            val arr = JSONArray()
            for (preset in presets) {
                val bandsArr = JSONArray()
                preset.bandsDb.forEach { bandsArr.put(it.coerceIn(-12, 12)) }
                arr.put(
                    JSONObject()
                        .put("id", preset.id)
                        .put("name", preset.name)
                        .put("bands", bandsArr)
                )
            }
            return arr.toString()
        }

        fun decodeCustomPresets(raw: String?): List<CustomEqPreset> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                val out = mutableListOf<CustomEqPreset>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id").takeIf { it.isNotBlank() } ?: continue
                    val name = obj.optString("name").takeIf { it.isNotBlank() } ?: continue
                    val bandsJson = obj.optJSONArray("bands") ?: continue
                    if (bandsJson.length() != 5) continue
                    val bands = (0 until 5).map { idx -> bandsJson.optInt(idx, 0).coerceIn(-12, 12) }
                    out.add(CustomEqPreset(id = id, name = name, bandsDb = bands))
                }
                out
            }.getOrDefault(emptyList())
        }
    }
}
