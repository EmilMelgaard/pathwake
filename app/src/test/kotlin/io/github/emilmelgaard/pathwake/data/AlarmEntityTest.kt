package io.github.emilmelgaard.pathwake.data

import io.github.emilmelgaard.pathwake.alarm.Alarm
import io.github.emilmelgaard.pathwake.alarm.AlarmSound
import io.github.emilmelgaard.pathwake.alarm.AutoSilence
import io.github.emilmelgaard.pathwake.puzzle.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmEntityTest {
    @Test
    fun alarmsStoredWithARenamedDifficultyKeepIt() {
        val stored = Alarm(id = 1).toEntity().copy(difficulty = "FIENDISH")
        assertEquals(Difficulty.BRUTAL, stored.toAlarm().difficulty)
    }

    @Test
    fun soundsAndAutoSilenceSurviveStorage() {
        val alarms = listOf(
            Alarm(id = 1, sound = AlarmSound.Silent, autoSilence = AutoSilence.Never, vibrate = false),
            Alarm(id = 2, sound = AlarmSound.Tone("content://media/internal/audio/media/9", "Cesium")),
            Alarm(id = 3, sound = AlarmSound.Custom("17_Birds.mp3", "Birds"), autoSilence = AutoSilence.WhenSoundEnds),
            Alarm(id = 4, autoSilence = AutoSilence.After(15))
        )
        alarms.forEach { assertEquals(it, it.toEntity().toAlarm()) }
    }

    @Test
    fun unknownSoundsBecomeTheDefault() {
        assertEquals(AlarmSound.Default, AlarmSound.parse("ringtone:42", null))
        assertEquals(AlarmSound.Default, AlarmSound.parse(null, null))
    }

    @Test
    fun unknownDifficultiesFallBackToMedium() {
        val stored = Alarm(id = 1, difficulty = Difficulty.HARD).toEntity().copy(difficulty = "IMPOSSIBLE")
        assertEquals(Difficulty.MEDIUM, stored.toAlarm().difficulty)
    }
}
