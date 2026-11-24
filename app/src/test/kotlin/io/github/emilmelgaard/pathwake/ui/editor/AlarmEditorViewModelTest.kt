package io.github.emilmelgaard.pathwake.ui.editor

import io.github.emilmelgaard.pathwake.alarm.Alarm
import io.github.emilmelgaard.pathwake.alarm.AlarmSchedule
import io.github.emilmelgaard.pathwake.alarm.DateRange
import io.github.emilmelgaard.pathwake.data.FakeAlarmRepository
import io.github.emilmelgaard.pathwake.puzzle.Difficulty
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlarmEditorViewModelTest {
    // Unconfined, so that edits show up in the UI state right away.
    private val dispatcher = UnconfinedTestDispatcher()

    /** Wednesday 2026-11-25 06:00. */
    private val now = ZonedDateTime.of(2026, 11, 25, 6, 0, 0, 0, ZoneId.of("Europe/Copenhagen"))
    private val today: LocalDate = now.toLocalDate()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(repository: FakeAlarmRepository, alarmId: Long = 0): AlarmEditorViewModel {
        val viewModel = AlarmEditorViewModel(alarmId, repository, { now }, flowOf(now))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        return viewModel
    }

    private val AlarmEditorViewModel.state get() = uiState.value

    @Test
    fun newAlarm_startsCleanAndBecomesDirtyWhenEdited() = runTest(dispatcher) {
        val viewModel = viewModel(FakeAlarmRepository())
        assertFalse(viewModel.state.loading)
        assertTrue(viewModel.state.isNew)
        assertFalse(viewModel.state.isDirty)

        viewModel.setTime(LocalTime.of(8, 15))
        assertTrue(viewModel.state.isDirty)
        assertEquals(Duration.ofMinutes(135), viewModel.state.timeUntil)

        viewModel.setTime(Alarm().time)
        assertFalse(viewModel.state.isDirty)
    }

    @Test
    fun loadsAnExistingAlarm_orReportsItMissing() = runTest(dispatcher) {
        val alarm = Alarm(id = 7, label = "Work", repeatDays = AlarmSchedule.WEEKDAYS)
        val repository = FakeAlarmRepository(alarm)

        val existing = viewModel(repository, alarmId = 7).state
        assertFalse(existing.isNew)
        assertEquals(alarm, existing.alarm)

        val missing = viewModel(repository, alarmId = 8).state
        assertTrue(missing.missing)
    }

    @Test
    fun repeatDaysAndDateExcludeEachOther() = runTest(dispatcher) {
        val viewModel = viewModel(FakeAlarmRepository())

        viewModel.setDate(today.plusDays(10))
        assertEquals(today.plusDays(10), viewModel.state.next?.toLocalDate())

        viewModel.toggleDay(DayOfWeek.FRIDAY)
        assertNull(viewModel.state.alarm.date)
        assertEquals(setOf(DayOfWeek.FRIDAY), viewModel.state.alarm.repeatDays)

        viewModel.setDate(today.plusDays(1))
        assertTrue(viewModel.state.alarm.repeatDays.isEmpty())
    }

    @Test
    fun pausesAreKeptSortedWithoutDuplicates() = runTest(dispatcher) {
        val viewModel = viewModel(FakeAlarmRepository())
        val later = DateRange(today.plusDays(20), today.plusDays(25))
        val sooner = DateRange(today.plusDays(2), today.plusDays(4))

        viewModel.addPause(later)
        viewModel.addPause(sooner)
        viewModel.addPause(later)
        assertEquals(listOf(sooner, later), viewModel.state.alarm.pauses)

        viewModel.removePause(sooner)
        assertEquals(listOf(later), viewModel.state.alarm.pauses)
    }

    @Test
    fun skipNext_skipsTheUpcomingOccurrence() = runTest(dispatcher) {
        val viewModel = viewModel(FakeAlarmRepository())
        viewModel.setRepeatDays(AlarmSchedule.WEEKDAYS)

        viewModel.setSkipNext(true)
        assertEquals(today, viewModel.state.skippedDate)
        assertEquals(today.plusDays(1), viewModel.state.next?.toLocalDate())

        viewModel.setSkipNext(false)
        assertNull(viewModel.state.skippedDate)
    }

    @Test
    fun puzzleCountStaysInRange() = runTest(dispatcher) {
        val viewModel = viewModel(FakeAlarmRepository())
        viewModel.setPuzzleCount(0)
        assertEquals(1, viewModel.state.alarm.puzzleCount)
        viewModel.setPuzzleCount(Alarm.MAX_PUZZLES + 1)
        assertEquals(Alarm.MAX_PUZZLES, viewModel.state.alarm.puzzleCount)
    }

    @Test
    fun save_storesAnEnabledAlarmAndReportsWhenItRings() = runTest(dispatcher) {
        val repository = FakeAlarmRepository()
        val viewModel = viewModel(repository)
        viewModel.setLabel("Run")
        viewModel.setDifficulty(Difficulty.EXPERT)
        viewModel.addPause(DateRange(today.minusDays(5), today.minusDays(1)))

        var saved: SavedAlarm? = null
        viewModel.save { saved = it }
        advanceUntilIdle()

        val stored = repository.current.single()
        assertEquals(Alarm(id = stored.id, label = "Run", difficulty = Difficulty.EXPERT), stored)
        assertEquals(SavedAlarm(stored.id, now.plusHours(1), Duration.ofHours(1)), saved)
        assertFalse(viewModel.state.isNew)
        assertFalse(viewModel.state.isDirty)
    }

    @Test
    fun save_enablesADisabledAlarm() = runTest(dispatcher) {
        val repository = FakeAlarmRepository(Alarm(id = 3, enabled = false))
        val viewModel = viewModel(repository, alarmId = 3)

        viewModel.save {}
        advanceUntilIdle()

        assertTrue(repository.current.single().enabled)
    }

    @Test
    fun delete_removesTheStoredAlarm() = runTest(dispatcher) {
        val alarm = Alarm(id = 5, label = "Stored")
        val repository = FakeAlarmRepository(alarm)
        val viewModel = viewModel(repository, alarmId = 5)
        viewModel.setLabel("Edited")

        var deleted: Alarm? = null
        viewModel.delete { deleted = it }
        advanceUntilIdle()

        assertTrue(repository.current.isEmpty())
        assertEquals(alarm, deleted)
    }
}
