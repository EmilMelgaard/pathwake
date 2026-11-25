package io.github.emilmelgaard.pathwake.system

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import io.github.emilmelgaard.pathwake.alarm.AlarmSound
import io.github.emilmelgaard.pathwake.alarm.SchedulerState
import io.github.emilmelgaard.pathwake.data.difficultyOf
import io.github.emilmelgaard.pathwake.puzzle.Difficulty
import io.github.emilmelgaard.pathwake.ring.RingingSession
import io.github.emilmelgaard.pathwake.ring.RingingSessionStore
import java.time.Instant
import java.time.LocalTime

// All stores live in device-protected storage, so alarms work after a reboot before the user unlocks the device.
// Writes are synchronous (commit) because they often happen right before the process may be stopped.

private fun Context.devicePreferences(name: String): SharedPreferences =
    createDeviceProtectedStorageContext().getSharedPreferences(name, Context.MODE_PRIVATE)

class PreferencesSchedulerState(context: Context) : SchedulerState {
    private val preferences = context.devicePreferences("scheduler")

    override var scheduled: Instant?
        get() = preferences.instant(SCHEDULED)
        set(value) = preferences.putInstant(SCHEDULED, value)

    override var handled: Instant?
        get() = preferences.instant(HANDLED)
        set(value) = preferences.putInstant(HANDLED, value)

    private fun SharedPreferences.instant(key: String) =
        if (contains(key)) Instant.ofEpochMilli(getLong(key, 0)) else null

    private fun SharedPreferences.putInstant(key: String, value: Instant?) = edit(commit = true) {
        if (value == null) remove(key) else putLong(key, value.toEpochMilli())
    }

    private companion object {
        const val SCHEDULED = "scheduled"
        const val HANDLED = "handled"
    }
}

class PreferencesRingingSessionStore(context: Context) : RingingSessionStore {
    private val preferences = context.devicePreferences("ringing")

    override fun load(): RingingSession? = runCatching {
        if (!preferences.contains(SEED)) return null
        RingingSession(
            alarmIds = preferences.getString(ALARM_IDS, "")!!.split(',').filter { it.isNotEmpty() }.map { it.toLong() },
            labels = preferences.getString(LABELS, "")!!.split(SEPARATOR).filter { it.isNotEmpty() },
            startedAt = Instant.ofEpochMilli(preferences.getLong(STARTED_AT, 0)),
            alarmTime = LocalTime.ofSecondOfDay(preferences.getInt(ALARM_TIME, 0).toLong()),
            silenceAt = if (preferences.contains(
                    SILENCE_AT
                )
            ) {
                Instant.ofEpochMilli(preferences.getLong(SILENCE_AT, 0))
            } else {
                null
            },
            difficulty = difficultyOf(preferences.getString(DIFFICULTY, null))!!,
            puzzleCount = preferences.getInt(PUZZLE_COUNT, 1),
            solved = preferences.getInt(SOLVED, 0),
            seed = preferences.getLong(SEED, 0),
            puzzleNumber = preferences.getInt(PUZZLE_NUMBER, 0),
            sound = AlarmSound.parse(preferences.getString(SOUND, null), preferences.getString(SOUND_TITLE, null)),
            vibrate = preferences.getBoolean(VIBRATE, true),
            stopWhenSoundEnds = preferences.getBoolean(STOP_WHEN_SOUND_ENDS, false),
            test = preferences.getBoolean(TEST, false)
        )
    }.getOrNull()

    override fun save(session: RingingSession?) = preferences.edit(commit = true) {
        clear()
        if (session == null) return@edit
        putString(ALARM_IDS, session.alarmIds.joinToString(","))
        putString(LABELS, session.labels.joinToString(SEPARATOR))
        putLong(STARTED_AT, session.startedAt.toEpochMilli())
        putInt(ALARM_TIME, session.alarmTime.toSecondOfDay())
        session.silenceAt?.let { putLong(SILENCE_AT, it.toEpochMilli()) }
        putString(DIFFICULTY, session.difficulty.name)
        putInt(PUZZLE_COUNT, session.puzzleCount)
        putInt(SOLVED, session.solved)
        putLong(SEED, session.seed)
        putInt(PUZZLE_NUMBER, session.puzzleNumber)
        putString(SOUND, session.sound.key)
        session.sound.title?.let { putString(SOUND_TITLE, it) }
        putBoolean(VIBRATE, session.vibrate)
        putBoolean(STOP_WHEN_SOUND_ENDS, session.stopWhenSoundEnds)
        putBoolean(TEST, session.test)
    }

    private companion object {
        const val ALARM_IDS = "alarm_ids"
        const val LABELS = "labels"
        const val STARTED_AT = "started_at"
        const val ALARM_TIME = "alarm_time"
        const val SILENCE_AT = "silence_at"
        const val DIFFICULTY = "difficulty"
        const val PUZZLE_COUNT = "puzzle_count"
        const val SOLVED = "solved"
        const val SEED = "seed"
        const val PUZZLE_NUMBER = "puzzle_number"
        const val SOUND = "sound"
        const val SOUND_TITLE = "sound_title"
        const val VIBRATE = "vibrate"
        const val STOP_WHEN_SOUND_ENDS = "stop_when_sound_ends"
        const val TEST = "test"

        /** Unit separator: can't be typed into a label. */
        const val SEPARATOR = "\u001F"
    }
}

/** Small app-wide flags. */
class AppPreferences(context: Context) {
    private val preferences = context.devicePreferences("app")

    /** Whether the app has asked for the notification permission by itself (it only does that once). */
    var notificationPermissionRequested: Boolean
        get() = preferences.getBoolean(NOTIFICATION_PERMISSION_REQUESTED, false)
        set(value) = preferences.edit { putBoolean(NOTIFICATION_PERMISSION_REQUESTED, value) }

    private companion object {
        const val NOTIFICATION_PERMISSION_REQUESTED = "notification_permission_requested"
    }
}
