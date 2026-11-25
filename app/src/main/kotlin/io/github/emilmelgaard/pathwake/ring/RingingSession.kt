package io.github.emilmelgaard.pathwake.ring

import io.github.emilmelgaard.pathwake.alarm.Alarm
import io.github.emilmelgaard.pathwake.alarm.AlarmSound
import io.github.emilmelgaard.pathwake.alarm.AutoSilence
import io.github.emilmelgaard.pathwake.puzzle.Difficulty
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * An alarm that is ringing until [puzzleCount] puzzles are solved. Alarms due at the same time ring together, with
 * the hardest difficulty, the most puzzles and the longest auto-silence among them, and the first sound that isn't
 * silent.
 *
 * @property startedAt when the alarm started ringing (its alarm time).
 * @property alarmTime the alarm time, for display.
 * @property silenceAt when the alarm stops by itself; null if it rings until the puzzles are solved (or, with
 *   [stopWhenSoundEnds], until the sound has played once).
 * @property seed puzzles are generated from `seed + puzzleNumber`, so they survive the app being restarted.
 * @property puzzleNumber counts solved and replaced ("new puzzle") puzzles.
 * @property test started from the app to try an alarm: it can be cancelled, leaves no notifications and isn't
 *   resumed after a restart.
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
    val puzzleNumber: Int = 0,
    val sound: AlarmSound = AlarmSound.Default,
    val vibrate: Boolean = true,
    val stopWhenSoundEnds: Boolean = false,
    val test: Boolean = false
) {
    val puzzleSeed: Long get() = seed + puzzleNumber

    /** Whether the alarm rings until its puzzles are solved. */
    val ringsUntilSolved: Boolean get() = silenceAt == null && !stopWhenSoundEnds

    /**
     * Adds [alarms] that are due now to this ringing session, keeping its progress and difficulty. The sound that is
     * playing keeps playing, unless it is silent.
     */
    fun join(alarms: List<Alarm>, at: ZonedDateTime): RingingSession {
        val joined = start(alarms, at, seed)
        val untilSolved = ringsUntilSolved || joined.ringsUntilSolved
        return copy(
            alarmIds = (alarmIds + joined.alarmIds).distinct(),
            labels = (labels + joined.labels).distinct(),
            puzzleCount = maxOf(puzzleCount, joined.puzzleCount),
            silenceAt = if (untilSolved) null else listOfNotNull(silenceAt, joined.silenceAt).maxOrNull(),
            stopWhenSoundEnds = !untilSolved && stopWhenSoundEnds && joined.stopWhenSoundEnds,
            sound = if (sound == AlarmSound.Silent) joined.sound else sound,
            vibrate = vibrate || joined.vibrate
        )
    }

    companion object {
        fun start(alarms: List<Alarm>, at: ZonedDateTime, seed: Long): RingingSession {
            require(alarms.isNotEmpty()) { "No alarms to ring" }
            val silences = alarms.map { it.autoSilence }
            val untilSolved = AutoSilence.Never in silences
            val minutes = silences.filterIsInstance<AutoSilence.After>().maxOfOrNull { it.minutes }
            return RingingSession(
                alarmIds = alarms.map { it.id },
                labels = alarms.map { it.label.trim() }.filter { it.isNotEmpty() }.distinct(),
                startedAt = at.toInstant(),
                alarmTime = at.toLocalTime().withSecond(0).withNano(0),
                silenceAt = if (untilSolved || minutes == null) null else at.plusMinutes(minutes.toLong()).toInstant(),
                difficulty = alarms.maxOf { it.difficulty },
                puzzleCount = alarms.maxOf { it.puzzleCount },
                seed = seed,
                sound = alarms.map { it.sound }.firstOrNull { it != AlarmSound.Silent } ?: AlarmSound.Silent,
                vibrate = alarms.any { it.vibrate },
                stopWhenSoundEnds = !untilSolved && minutes == null
            )
        }
    }
}
