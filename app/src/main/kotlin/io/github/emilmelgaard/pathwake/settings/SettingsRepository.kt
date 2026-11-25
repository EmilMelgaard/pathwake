package io.github.emilmelgaard.pathwake.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.emilmelgaard.pathwake.alarm.Alarm
import io.github.emilmelgaard.pathwake.alarm.AlarmSound
import io.github.emilmelgaard.pathwake.alarm.AutoSilence
import io.github.emilmelgaard.pathwake.data.difficultyOf
import java.time.DayOfWeek
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Reads and writes [Settings]. Unknown or missing values fall back to the defaults. */
class SettingsRepository(private val dataStore: DataStore<Preferences>, scope: CoroutineScope) {
    val settings: Flow<Settings> = dataStore.data.map(::read)

    /** The latest settings, for code that can't wait (key handling, the first frame of the UI). */
    val current: StateFlow<Settings> = settings.stateIn(scope, SharingStarted.Eagerly, Settings())

    suspend fun get(): Settings = settings.first()

    suspend fun update(transform: (Settings) -> Settings) {
        dataStore.edit { preferences -> write(transform(read(preferences)), preferences) }
    }

    private fun read(p: Preferences): Settings {
        val defaults = Settings()
        val alarm = defaults.alarmDefaults
        return Settings(
            theme = enumOf(p[THEME]) ?: defaults.theme,
            dynamicColor = p[DYNAMIC_COLOR] ?: defaults.dynamicColor,
            puzzleLook = enumOf(p[PUZZLE_LOOK]) ?: defaults.puzzleLook,
            firstDayOfWeek = enumOf<DayOfWeek>(p[FIRST_DAY_OF_WEEK]),
            crescendoSeconds = p[CRESCENDO_SECONDS]?.coerceAtLeast(0) ?: defaults.crescendoSeconds,
            solvingVolume = enumOf(p[SOLVING_VOLUME]) ?: defaults.solvingVolume,
            lockVolumeButtons = p[LOCK_VOLUME_BUTTONS] ?: defaults.lockVolumeButtons,
            alarmDefaults = AlarmDefaults(
                difficulty = difficultyOf(p[DEFAULT_DIFFICULTY]) ?: alarm.difficulty,
                puzzleCount = p[DEFAULT_PUZZLE_COUNT]?.coerceIn(1, Alarm.MAX_PUZZLES) ?: alarm.puzzleCount,
                autoSilence = p[DEFAULT_AUTO_SILENCE]?.let(::autoSilenceOf) ?: alarm.autoSilence,
                sound = p[DEFAULT_SOUND]?.let { AlarmSound.parse(it, p[DEFAULT_SOUND_TITLE]) } ?: alarm.sound,
                vibrate = p[DEFAULT_VIBRATE] ?: alarm.vibrate
            )
        )
    }

    private fun write(s: Settings, p: MutablePreferences) {
        p[THEME] = s.theme.name
        p[DYNAMIC_COLOR] = s.dynamicColor
        p[PUZZLE_LOOK] = s.puzzleLook.name
        s.firstDayOfWeek?.let { p[FIRST_DAY_OF_WEEK] = it.name } ?: p.remove(FIRST_DAY_OF_WEEK)
        p[CRESCENDO_SECONDS] = s.crescendoSeconds
        p[SOLVING_VOLUME] = s.solvingVolume.name
        p[LOCK_VOLUME_BUTTONS] = s.lockVolumeButtons
        val alarm = s.alarmDefaults
        p[DEFAULT_DIFFICULTY] = alarm.difficulty.name
        p[DEFAULT_PUZZLE_COUNT] = alarm.puzzleCount
        p[DEFAULT_AUTO_SILENCE] = autoSilenceMinutes(alarm.autoSilence)
        p[DEFAULT_SOUND] = alarm.sound.key
        alarm.sound.title?.let { p[DEFAULT_SOUND_TITLE] = it } ?: p.remove(DEFAULT_SOUND_TITLE)
        p[DEFAULT_VIBRATE] = alarm.vibrate
    }

    private inline fun <reified E : Enum<E>> enumOf(name: String?): E? = enumValues<E>().firstOrNull { it.name == name }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val PUZZLE_LOOK = stringPreferencesKey("puzzle_look")
        val FIRST_DAY_OF_WEEK = stringPreferencesKey("first_day_of_week")
        val CRESCENDO_SECONDS = intPreferencesKey("crescendo_seconds")
        val SOLVING_VOLUME = stringPreferencesKey("solving_volume")
        val LOCK_VOLUME_BUTTONS = booleanPreferencesKey("lock_volume_buttons")
        val DEFAULT_DIFFICULTY = stringPreferencesKey("default_difficulty")
        val DEFAULT_PUZZLE_COUNT = intPreferencesKey("default_puzzle_count")
        val DEFAULT_AUTO_SILENCE = intPreferencesKey("default_auto_silence")
        val DEFAULT_SOUND = stringPreferencesKey("default_sound")
        val DEFAULT_SOUND_TITLE = stringPreferencesKey("default_sound_title")
        val DEFAULT_VIBRATE = booleanPreferencesKey("default_vibrate")

        /** -1: never, 0: when the sound ends, otherwise minutes. */
        fun autoSilenceMinutes(silence: AutoSilence) = when (silence) {
            AutoSilence.Never -> -1
            AutoSilence.WhenSoundEnds -> 0
            is AutoSilence.After -> silence.minutes
        }

        fun autoSilenceOf(minutes: Int): AutoSilence = when {
            minutes < 0 -> AutoSilence.Never
            minutes == 0 -> AutoSilence.WhenSoundEnds
            else -> AutoSilence.After(minutes)
        }
    }
}
