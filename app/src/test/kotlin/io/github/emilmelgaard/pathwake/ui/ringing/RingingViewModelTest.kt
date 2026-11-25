package io.github.emilmelgaard.pathwake.ui.ringing

import io.github.emilmelgaard.pathwake.alarm.Alarm
import io.github.emilmelgaard.pathwake.puzzle.Difficulty
import io.github.emilmelgaard.pathwake.ring.RingingController
import io.github.emilmelgaard.pathwake.ring.RingingSession
import io.github.emilmelgaard.pathwake.ring.RingingSessionStore
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RingingViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val at = ZonedDateTime.of(2026, 11, 25, 7, 0, 0, 0, ZoneId.of("Europe/Copenhagen"))
    private val controller = RingingController(NoStore) { 42 }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): RingingViewModel {
        val viewModel = RingingViewModel(controller, generatorDispatcher = dispatcher, solvedPause = 500.milliseconds)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun showsThePuzzleForTheCurrentSeed() = runTest(dispatcher) {
        controller.ring(listOf(Alarm(difficulty = Difficulty.EASY, puzzleCount = 2)), at)
        val state = viewModel().uiState.value

        assertEquals(42L, state.puzzle?.seed)
        assertEquals(Difficulty.EASY, state.puzzle?.difficulty)
        assertFalse(state.finished)
    }

    @Test
    fun solving_showsSuccessThenMovesToTheNextPuzzle() = runTest(dispatcher) {
        controller.ring(listOf(Alarm(difficulty = Difficulty.EASY, puzzleCount = 2)), at)
        val viewModel = viewModel()

        viewModel.onSolved()
        advanceTimeBy(100)
        assertTrue(viewModel.uiState.value.solved)
        assertEquals(0, controller.session.value?.solved)

        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertFalse(state.solved)
        assertEquals(1, state.session?.solved)
        assertEquals(43L, state.puzzle?.seed)
    }

    @Test
    fun solvingTheLastPuzzle_finishes() = runTest(dispatcher) {
        controller.ring(listOf(Alarm(difficulty = Difficulty.EASY)), at)
        val viewModel = viewModel()

        viewModel.onSolved()
        viewModel.onSolved()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.finished)
    }

    @Test
    fun newPuzzle_replacesThePuzzleWithoutProgress() = runTest(dispatcher) {
        controller.ring(listOf(Alarm(difficulty = Difficulty.EASY, puzzleCount = 3)), at)
        val viewModel = viewModel()
        val first = viewModel.uiState.value.puzzle

        viewModel.newPuzzle()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotEquals(first, state.puzzle)
        assertEquals(0, state.session?.solved)
    }

    private object NoStore : RingingSessionStore {
        override fun load(): RingingSession? = null

        override fun save(session: RingingSession?) = Unit
    }
}
