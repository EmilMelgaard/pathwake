package io.github.emilmelgaard.pathwake.ring

import io.github.emilmelgaard.pathwake.alarm.Alarm
import io.github.emilmelgaard.pathwake.puzzle.Difficulty
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * An alarm that is ringing until [puzzleCount] puzzles are solved. Alarms due at the same time ring together, with
 * the hardest difficulty and the most puzzles among them.
 *
 * @property startedAt when the alarm started ringing (its alarm time).
 * @property alarmTime the alarm time, for display.
 * @property silenceAt when the alarm stops by itself; null if it rings until the puzzles are solved.
 * @property seed puzzles are generated from `seed + puzzleNumber`, so they survive the app being restarted.
 * @property puzzleNumber counts solved and replaced ("new puzzle") puzzles.
 */
data class RingingSession(
    val alarmIds: List<Long>,
    val labels: List<String>,
    val startedAt: Instant,
    val alarmTime: LocalTime,
    val silenceAt: Instant?,
    val difficulty: Difficulty,
    val puzzleCount: Int,
    val solved: Int = 0,
    val seed: Long,
    val puzzleNumber: Int = 0
) {
    val puzzleSeed: Long get() = seed + puzzleNumber

    /** How long the alarm rings before it stops by itself; null if it doesn't. */
    val autoSilence: Duration? get() = silenceAt?.let { Duration.between(startedAt, it) }

    /** Adds [alarms] that are due now to this ringing session, keeping its progress and difficulty. */
    fun join(alarms: List<Alarm>, at: ZonedDateTime): RingingSession {
        val joined = start(alarms, at, seed)
        return copy(
            alarmIds = (alarmIds + joined.alarmIds).distinct(),
            labels = (labels + joined.labels).distinct(),
            puzzleCount = maxOf(puzzleCount, joined.puzzleCount),
            silenceAt = if (silenceAt == null || joined.silenceAt == null) null else maxOf(silenceAt, joined.silenceAt)
        )
    }

    companion object {
        fun start(alarms: List<Alarm>, at: ZonedDateTime, seed: Long): RingingSession {
            require(alarms.isNotEmpty()) { "No alarms to ring" }
            val silenceMinutes = alarms.map { it.autoSilenceMinutes }
            return RingingSession(
                alarmIds = alarms.map { it.id },
                labels = alarms.map { it.label.trim() }.filter { it.isNotEmpty() }.distinct(),
                startedAt = at.toInstant(),
                alarmTime = at.toLocalTime().withSecond(0).withNano(0),
                silenceAt = if (null in silenceMinutes) {
                    null
                } else {
                    at.plusMinutes(silenceMinutes.maxOf { it!!.toLong() }).toInstant()
                },
                difficulty = alarms.maxOf { it.difficulty },
                puzzleCount = alarms.maxOf { it.puzzleCount },
                seed = seed
            )
        }
    }
}
