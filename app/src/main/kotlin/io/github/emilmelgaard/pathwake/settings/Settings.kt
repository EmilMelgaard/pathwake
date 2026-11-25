package io.github.emilmelgaard.pathwake.settings

import io.github.emilmelgaard.pathwake.alarm.Alarm
import io.github.emilmelgaard.pathwake.alarm.AlarmSound
import io.github.emilmelgaard.pathwake.alarm.AutoSilence
import io.github.emilmelgaard.pathwake.puzzle.Difficulty
import io.github.emilmelgaard.pathwake.ui.puzzle.PuzzleLook
import java.time.DayOfWeek

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** How loud a ringing alarm is while the puzzles are being solved. */
enum class SolvingVolume { FULL, LOWER, MUTE }

/** What a new alarm starts with. */
data class AlarmDefaults(
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val puzzleCount: Int = 1,
    val autoSilence: AutoSilence = AutoSilence.DEFAULT,
    val sound: AlarmSound = AlarmSound.Default,
    val vibrate: Boolean = true
) {
    fun newAlarm(): Alarm = Alarm(
        difficulty = difficulty,
        puzzleCount = puzzleCount,
        autoSilence = autoSilence,
        sound = sound,
        vibrate = vibrate
    )
}

/**
 * App-wide settings.
 *
 * @property firstDayOfWeek null: the locale's.
 * @property crescendoSeconds how long a ringing alarm takes to reach full volume; 0 starts at full volume.
 * @property lockVolumeButtons volume buttons (and other ways of turning down the alarm volume) don't lower a ringing
 *   alarm.
 * @property colorMarks colored puzzle symbols get a mark per color, so they don't rely on color vision.
 */
data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val puzzleLook: PuzzleLook = PuzzleLook.PANEL,
    val colorMarks: Boolean = false,
    val firstDayOfWeek: DayOfWeek? = null,
    val crescendoSeconds: Int = 0,
    val solvingVolume: SolvingVolume = SolvingVolume.LOWER,
    val lockVolumeButtons: Boolean = true,
    val alarmDefaults: AlarmDefaults = AlarmDefaults()
) {
    companion object {
        val CRESCENDO_CHOICES = listOf(0, 15, 30, 60, 120, 300)
    }
}
