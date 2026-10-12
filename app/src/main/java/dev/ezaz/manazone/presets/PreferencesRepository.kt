package dev.ezaz.manazone.presets

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

data class UserSettings(
    val vibrationEnabled: Boolean = true,
    val soundEnabled: Boolean = false,
    val dndEnabled: Boolean = false,
    /** ARGB of the countdown digits. */
    val clockColor: Int = DEFAULT_CLOCK_COLOR,
    /** Font weight (100..900) of the countdown digits. */
    val clockWeight: Int = DEFAULT_CLOCK_WEIGHT,
    /** Name of a [dev.ezaz.manazone.ui.focus.ClockStyle]. */
    val clockStyle: String = "CLASSIC",
    val soundId: String = "ding_dong",
    /** Completion sound loudness, 10..100 percent. */
    val soundVolume: Int = 80,
)

const val DEFAULT_CLOCK_COLOR = 0xFF34C48B.toInt()
const val DEFAULT_CLOCK_WEIGHT = 300

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "manazone_prefs")

/** Presets and settings, persisted in Preferences DataStore. */
class PreferencesRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    private val data: Flow<Preferences> = store.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val presets: Flow<List<Int>> = data.map { prefs ->
        val raw = prefs[PRESETS] ?: return@map DEFAULT_PRESETS
        PresetRules.sanitize(raw.split(',').mapNotNull { it.trim().toIntOrNull() })
    }

    val settings: Flow<UserSettings> = data.map { prefs ->
        UserSettings(
            vibrationEnabled = prefs[VIBRATION] ?: true,
            soundEnabled = prefs[SOUND] ?: false,
            dndEnabled = prefs[DND] ?: false,
            clockColor = prefs[CLOCK_COLOR] ?: DEFAULT_CLOCK_COLOR,
            clockWeight = (prefs[CLOCK_WEIGHT] ?: DEFAULT_CLOCK_WEIGHT).coerceIn(100, 900),
            clockStyle = prefs[CLOCK_STYLE] ?: "CLASSIC",
            soundId = prefs[SOUND_ID] ?: "ding_dong",
            soundVolume = (prefs[SOUND_VOLUME] ?: 80).coerceIn(10, 100),
        )
    }

    suspend fun currentSettings(): UserSettings = settings.first()

    suspend fun setPresets(presets: List<Int>) {
        store.edit { it[PRESETS] = presets.joinToString(",") }
    }

    suspend fun setVibration(enabled: Boolean) = store.edit { it[VIBRATION] = enabled }
    suspend fun setSound(enabled: Boolean) = store.edit { it[SOUND] = enabled }
    suspend fun setDnd(enabled: Boolean) = store.edit { it[DND] = enabled }

    suspend fun setClockColor(argb: Int) = store.edit { it[CLOCK_COLOR] = argb }
    suspend fun setClockWeight(weight: Int) = store.edit { it[CLOCK_WEIGHT] = weight.coerceIn(100, 900) }

    suspend fun setClockStyle(style: String) = store.edit { it[CLOCK_STYLE] = style }
    suspend fun setSoundId(id: String) = store.edit { it[SOUND_ID] = id }
    suspend fun setSoundVolume(percent: Int) = store.edit { it[SOUND_VOLUME] = percent.coerceIn(10, 100) }

    suspend fun clear() = store.edit { it.clear() }

    private companion object {
        val PRESETS = stringPreferencesKey("presets")
        val VIBRATION = booleanPreferencesKey("completion_vibration")
        val SOUND = booleanPreferencesKey("completion_sound")
        val DND = booleanPreferencesKey("dnd_enabled")
        val CLOCK_COLOR = intPreferencesKey("clock_color")
        val CLOCK_WEIGHT = intPreferencesKey("clock_weight")
        val CLOCK_STYLE = stringPreferencesKey("clock_style")
        val SOUND_ID = stringPreferencesKey("completion_sound_id")
        val SOUND_VOLUME = intPreferencesKey("completion_sound_volume")
    }
}
