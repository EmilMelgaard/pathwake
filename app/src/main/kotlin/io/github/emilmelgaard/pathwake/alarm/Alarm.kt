package io.github.emilmelgaard.pathwake.alarm

import io.github.emilmelgaard.pathwake.puzzle.Difficulty
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** An inclusive range of days. */
data class DateRange(val start: LocalDate, val end: LocalDate) {
    init {
        require(!end.isBefore(start)) { "End $end is before start $start" }
    }

    operator fun contains(date: LocalDate) = !date.isBefore(start) && !date.isAfter(end)
}

/** A time at which [alarms] ring. */
data class Occurrence(val at: ZonedDateTime, val alarms: List<Alarm>)

/**
 * An alarm. It repeats on [repeatDays], or rings once: on [date] if set, otherwise at the next [time].
 *
 * @property pauses days on which a repeating alarm doesn't ring (for example vacations).
 * @property skippedDate a repeating alarm doesn't ring on this day ("skip next").
 * @property puzzleCount number of puzzles to solve to stop the alarm.
 * @property autoSilence when the alarm stops ringing by itself if its puzzles aren't solved.
 */
data class Alarm(
    val id: Long = 0,
    val time: LocalTime = LocalTime.of(7, 0),
    val label: String = "",
    val enabled: Boolean = true,
    val repeatDays: Set<DayOfWeek> = emptySet(),
    val date: LocalDate? = null,
    val pauses: List<DateRange> = emptyList(),
    val skippedDate: LocalDate? = null,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val puzzleCount: Int = 1,
    val autoSilence: AutoSilence = AutoSilence.DEFAULT,
    val sound: AlarmSound = AlarmSound.Default,
    val vibrate: Boolean = true
) {
    init {
        require(puzzleCount in 1..MAX_PUZZLES) { "Puzzle count must be 1..$MAX_PUZZLES, was $puzzleCount" }
    }

    val repeats: Boolean get() = repeatDays.isNotEmpty()

    companion object {
        const val MAX_PUZZLES = 10
    }
}

/** When a ringing alarm whose puzzles aren't solved stops by itself. */
sealed interface AutoSilence {
    /** Rings until the puzzles are solved. */
    data object Never : AutoSilence

    /** Plays the sound once instead of looping it. */
    data object WhenSoundEnds : AutoSilence

    data class After(val minutes: Int) : AutoSilence {
        init {
            require(minutes > 0) { "Minutes must be positive, was $minutes" }
        }
    }

    companion object {
        val DEFAULT: AutoSilence = After(10)

        /** The choices offered in the editor. */
        val CHOICES: List<AutoSilence> = listOf(1, 5, 10, 15, 20, 30).map(::After) + listOf(WhenSoundEnds, Never)
    }
}

/** What an alarm plays. */
sealed interface AlarmSound {
    /** The device's default alarm sound. */
    data object Default : AlarmSound

    /** Only vibration (if on). */
    data object Silent : AlarmSound

    /** A sound installed on the device, e.g. one of the system alarm tones. */
    data class Tone(val uri: String, override val title: String) : AlarmSound

    /** A sound file the user added. A copy lives in the app's sound library (see `SoundLibrary`). */
    data class Custom(val fileName: String, override val title: String) : AlarmSound

    /** Stored form: a key plus a title for tones and custom sounds. See [parse]. */
    val key: String
        get() = when (this) {
            Default -> DEFAULT_KEY
            Silent -> SILENT_KEY
            is Tone -> TONE_PREFIX + uri
            is Custom -> CUSTOM_PREFIX + fileName
        }

    /** The title shown for tones and custom sounds; null for the others. */
    val title: String? get() = null

    companion object {
        private const val DEFAULT_KEY = "default"
        private const val SILENT_KEY = "silent"
        private const val TONE_PREFIX = "tone:"
        private const val CUSTOM_PREFIX = "file:"

        /** Reads a sound stored as [key] and [title]; unknown keys become [Default]. */
        fun parse(key: String?, title: String?): AlarmSound = when {
            key == SILENT_KEY -> Silent
            key == null || key == DEFAULT_KEY -> Default
            key.startsWith(TONE_PREFIX) -> Tone(key.removePrefix(TONE_PREFIX), title.orEmpty())
            key.startsWith(CUSTOM_PREFIX) -> Custom(key.removePrefix(CUSTOM_PREFIX), title.orEmpty())
            else -> Default
        }
    }
}
