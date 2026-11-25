package io.github.emilmelgaard.pathwake.data

import io.github.emilmelgaard.pathwake.alarm.Alarm
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
    fun unknownDifficultiesFallBackToMedium() {
        val stored = Alarm(id = 1, difficulty = Difficulty.HARD).toEntity().copy(difficulty = "IMPOSSIBLE")
        assertEquals(Difficulty.MEDIUM, stored.toAlarm().difficulty)
    }
}
