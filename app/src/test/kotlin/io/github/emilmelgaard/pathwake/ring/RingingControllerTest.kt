package io.github.emilmelgaard.pathwake.ring

import io.github.emilmelgaard.pathwake.alarm.Alarm
import io.github.emilmelgaard.pathwake.alarm.AlarmSound
import io.github.emilmelgaard.pathwake.alarm.AutoSilence
import io.github.emilmelgaard.pathwake.puzzle.Difficulty
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RingingControllerTest {
    private val at = ZonedDateTime.of(2026, 11, 25, 7, 0, 0, 0, ZoneId.of("Europe/Copenhagen"))
    private val store = MemorySessionStore()
    private val controller = RingingController(store) { 100 }

    private val work = Alarm(id = 1, label = "Work", difficulty = Difficulty.HARD, puzzleCount = 2)
    private val backup =
        Alarm(id = 2, label = "", difficulty = Difficulty.EXPERT, puzzleCount = 1, autoSilence = AutoSilence.After(30))

    @Test
    fun ring_usesTheHardestDifficultyTheMostPuzzlesAndTheLongestAutoSilence() {
        controller.ring(listOf(work, backup), at)

        assertEquals(
            RingingSession(
                alarmIds = listOf(1, 2),
                labels = listOf("Work"),
                startedAt = at.toInstant(),
                alarmTime = LocalTime.of(7, 0),
                silenceAt = at.plusMinutes(30).toInstant(),
                difficulty = Difficulty.EXPERT,
                puzzleCount = 2,
                seed = 100
            ),
            controller.session.value
        )
    }

    @Test
    fun ring_neverSilencesIfOneAlarmNeverDoes() {
        controller.ring(listOf(work, backup.copy(autoSilence = AutoSilence.Never)), at)
        assertNull(controller.session.value?.silenceAt)
    }

    @Test
    fun solvingEveryPuzzleStopsTheAlarm() {
        controller.ring(listOf(work), at)

        controller.puzzleSolved(0)
        assertEquals(1, controller.session.value?.solved)
        assertEquals(101L, controller.session.value?.puzzleSeed)

        controller.puzzleSolved(0) // Already solved: ignored.
        assertEquals(1, controller.session.value?.solved)

        controller.puzzleSolved(1)
        assertNull(controller.session.value)
        assertNull(store.saved)
    }

    @Test
    fun newPuzzle_replacesThePuzzleWithoutCountingIt() {
        controller.ring(listOf(work), at)
        controller.newPuzzle(0)

        assertEquals(0, controller.session.value?.solved)
        assertEquals(1, controller.session.value?.puzzleNumber)
    }

    @Test
    fun ringingAgain_keepsProgressAndAddsPuzzles() {
        controller.ring(listOf(backup), at)
        controller.newPuzzle(0)
        controller.ring(listOf(work), at.plusMinutes(1))

        val session = controller.session.value!!
        assertEquals(listOf(2L, 1L), session.alarmIds)
        assertEquals(Difficulty.EXPERT, session.difficulty)
        assertEquals(2, session.puzzleCount)
        assertEquals(1, session.puzzleNumber)
        assertEquals(at.plusMinutes(30).toInstant(), session.silenceAt)
    }

    @Test
    fun test_ringsLikeTheAlarmAndCanBeCancelled() {
        assertTrue(controller.test(work, at))
        val session = controller.session.value!!
        assertTrue(session.test)
        assertEquals(Difficulty.HARD, session.difficulty)
        assertEquals(2, session.puzzleCount)

        controller.cancelTest()
        assertNull(controller.session.value)
    }

    @Test
    fun aDueAlarm_replacesATest() {
        controller.test(backup, at)
        controller.newPuzzle(0)
        controller.ring(listOf(work), at.plusMinutes(1))

        val session = controller.session.value!!
        assertFalse(session.test)
        assertEquals(listOf(1L), session.alarmIds)
        assertEquals(0, session.puzzleNumber)
    }

    @Test
    fun aRingingAlarm_canNotBeTestedOrCancelled() {
        controller.ring(listOf(work), at)

        assertFalse(controller.test(backup, at))
        controller.cancelTest()
        assertEquals(listOf(1L), controller.session.value?.alarmIds)
        assertFalse(controller.session.value!!.test)
    }

    @Test
    fun aRingingAlarmSurvivesARestart() {
        controller.ring(listOf(work), at)
        controller.puzzleSolved(0)

        assertEquals(controller.session.value, RingingController(store).session.value)
    }

    @Test
    fun ring_playsTheFirstSoundThatIsNotSilentAndVibratesIfAnyAlarmDoes() {
        val birds = AlarmSound.Custom("1_Birds.mp3", "Birds")
        controller.ring(
            listOf(work.copy(sound = AlarmSound.Silent, vibrate = false), backup.copy(sound = birds, vibrate = true)),
            at
        )
        val session = controller.session.value!!
        assertEquals(birds, session.sound)
        assertEquals(true, session.vibrate)
    }

    @Test
    fun ownVolume_goesWithTheSoundThatPlays() {
        val birds = AlarmSound.Custom("1_Birds.mp3", "Birds")
        val silent = work.copy(sound = AlarmSound.Silent, volume = 90)
        val quietBirds = backup.copy(sound = birds, volume = 30)

        controller.ring(listOf(silent, quietBirds, work.copy(id = 3, volume = 80)), at)
        assertEquals(30, controller.session.value?.volume)

        controller.stop()
        controller.ring(listOf(work.copy(volume = null), quietBirds), at)
        assertEquals(null, controller.session.value?.volume)

        controller.stop()
        controller.ring(listOf(silent), at)
        controller.ring(listOf(quietBirds), at.plusMinutes(1))
        assertEquals(birds, controller.session.value?.sound)
        assertEquals(30, controller.session.value?.volume)
    }

    @Test
    fun whenTheSoundEnds_onlyIfEveryAlarmSaysSo() {
        val once = work.copy(autoSilence = AutoSilence.WhenSoundEnds)
        controller.ring(listOf(once, backup.copy(autoSilence = AutoSilence.WhenSoundEnds)), at)
        assertEquals(true, controller.session.value?.stopWhenSoundEnds)
        assertNull(controller.session.value?.silenceAt)

        controller.stop()
        controller.ring(listOf(once, backup), at)
        assertEquals(false, controller.session.value?.stopWhenSoundEnds)
        assertEquals(at.plusMinutes(30).toInstant(), controller.session.value?.silenceAt)
    }

    @Test
    fun aJoiningAlarmGivesASilentAlarmItsSound() {
        controller.ring(listOf(work.copy(sound = AlarmSound.Silent)), at)
        controller.ring(listOf(backup), at.plusMinutes(1))
        assertEquals(AlarmSound.Default, controller.session.value?.sound)
    }

    private class MemorySessionStore : RingingSessionStore {
        var saved: RingingSession? = null

        override fun load() = saved

        override fun save(session: RingingSession?) {
            saved = session
        }
    }
}
