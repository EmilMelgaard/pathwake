package io.github.emilmelgaard.pathwake.ui.alarms

import io.github.emilmelgaard.pathwake.alarm.Alarm
import io.github.emilmelgaard.pathwake.alarm.AlarmSchedule
import io.github.emilmelgaard.pathwake.alarm.DateRange
import io.github.emilmelgaard.pathwake.data.FakeAlarmRepository
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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
class AlarmListViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    /** Wednesday 2026-11-25 06:00. */
    private val now = ZonedDateTime.of(2026, 11, 25, 6, 0, 0, 0, ZoneId.of("Europe/Copenhagen"))
    private val today: LocalDate = now.toLocalDate()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(repository: FakeAlarmRepository): AlarmListViewModel {
        val viewModel = AlarmListViewModel(repository, { now }, flowOf(now))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun listsAlarmsAndPicksTheUpcomingOne() = runTest(dispatcher) {
        val repository = FakeAlarmRepository(
            Alarm(id = 1, time = LocalTime.of(5, 0)),
            Alarm(id = 2, time = LocalTime.of(7, 0)),
            Alarm(id = 3, time = LocalTime.of(6, 30), enabled = false)
        )
        val state = viewModel(repository).uiState.value

        assertFalse(state.loading)
        assertEquals(listOf(1L, 3L, 2L), state.items.map { it.alarm.id })
        assertNull(state.items[1].next)
        assertEquals(2L, state.upcoming?.alarm?.id)
        assertEquals(Duration.ofHours(1), state.upcoming?.timeUntil)
    }

    @Test
    fun showsSkipsAndCurrentPausesOfRepeatingAlarms() = runTest(dispatcher) {
        val pause = DateRange(today.plusDays(3), today.plusDays(5))
        val repository = FakeAlarmRepository(
            Alarm(
                id = 1,
                repeatDays = AlarmSchedule.WEEKDAYS,
                skippedDate = today,
                pauses = listOf(DateRange(today.minusDays(9), today.minusDays(2)), pause)
            )
        )
        val item = viewModel(repository).uiState.value.items.single()

        assertEquals(today, item.skippedDate)
        assertEquals(listOf(pause), item.pauses)
        assertEquals(today.plusDays(1), item.next?.toLocalDate())
    }

    @Test
    fun enablingAOneTimeAlarmWhoseDateHasPassedMakesItRingNextTime() = runTest(dispatcher) {
        val alarm = Alarm(id = 1, time = LocalTime.of(7, 0), date = today.minusDays(1), enabled = false)
        val repository = FakeAlarmRepository(alarm)
        val viewModel = viewModel(repository)

        viewModel.setEnabled(alarm, true)
        advanceUntilIdle()

        val stored = repository.current.single()
        assertTrue(stored.enabled)
        assertNull(stored.date)
        assertEquals(today, viewModel.uiState.value.items.single().next?.toLocalDate())
    }

    @Test
    fun skipsAndUnskipsTheNextOccurrence() = runTest(dispatcher) {
        val alarm = Alarm(id = 1, time = LocalTime.of(7, 0), repeatDays = AlarmSchedule.WEEKDAYS)
        val repository = FakeAlarmRepository(alarm)
        val viewModel = viewModel(repository)

        viewModel.skipNext(alarm)
        advanceUntilIdle()
        assertEquals(today, repository.current.single().skippedDate)
        assertEquals(today.plusDays(1), viewModel.uiState.value.items.single().next?.toLocalDate())

        viewModel.cancelSkip(repository.current.single())
        advanceUntilIdle()
        assertNull(repository.current.single().skippedDate)
    }

    @Test
    fun duplicatesWithoutTheSkippedDay() = runTest(dispatcher) {
        val alarm = Alarm(id = 4, label = "Gym", repeatDays = AlarmSchedule.WEEKEND, skippedDate = today.plusDays(3))
        val repository = FakeAlarmRepository(alarm)

        viewModel(repository).duplicate(alarm)
        advanceUntilIdle()

        val copy = repository.current.single { it.id != 4L }
        assertEquals(alarm.copy(id = copy.id, skippedDate = null), copy)
    }

    @Test
    fun deletes() = runTest(dispatcher) {
        val alarm = Alarm(id = 1)
        val repository = FakeAlarmRepository(alarm)
        val viewModel = viewModel(repository)

        viewModel.delete(alarm)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.items.isEmpty())
        assertNull(viewModel.uiState.value.upcoming)
    }
}
