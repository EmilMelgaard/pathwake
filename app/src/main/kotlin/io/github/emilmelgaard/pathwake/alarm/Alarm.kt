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
 * @property autoSilenceMinutes the alarm stops ringing by itself after this many minutes if its puzzles aren't
 *   solved; null means it rings until they are.
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
    val autoSilenceMinutes: Int? = DEFAULT_AUTO_SILENCE_MINUTES
) {
    init {
        require(puzzleCount in 1..MAX_PUZZLES) { "Puzzle count must be 1..$MAX_PUZZLES, was $puzzleCount" }
        require(autoSilenceMinutes == null || autoSilenceMinutes > 0) { "Auto-silence must be positive or null" }
    }

    val repeats: Boolean get() = repeatDays.isNotEmpty()

    companion object {
        const val MAX_PUZZLES = 10
        const val DEFAULT_AUTO_SILENCE_MINUTES = 10

        /** Auto-silence choices offered in the editor; null is "never". */
        val AUTO_SILENCE_CHOICES: List<Int?> = listOf(1, 5, 10, 15, 20, 30, null)
    }
}
