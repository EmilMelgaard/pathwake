package io.github.emilmelgaard.pathwake.alarm

import io.github.emilmelgaard.pathwake.puzzle.Difficulty
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** An inclusive range of days. */
data class DateRange(val start: LocalDate, val end: LocalDate) {
    init {
        require(!end.isBefore(start)) { "End $end is before start $start" }
    }

    operator fun contains(date: LocalDate) = !date.isBefore(start) && !date.isAfter(end)
}

/**
 * An alarm. It repeats on [repeatDays], or rings once: on [date] if set, otherwise at the next [time].
 *
 * @property pauses days on which a repeating alarm doesn't ring (for example vacations).
 * @property skippedDate a repeating alarm doesn't ring on this day ("skip next").
 * @property puzzleCount number of puzzles to solve to stop the alarm.
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
    val puzzleCount: Int = 1
) {
    init {
        require(puzzleCount in 1..MAX_PUZZLES) { "Puzzle count must be 1..$MAX_PUZZLES, was $puzzleCount" }
    }

    val repeats: Boolean get() = repeatDays.isNotEmpty()

    companion object {
        const val MAX_PUZZLES = 10
    }
}
