package io.github.emilmelgaard.pathwake.ui.playground

import io.github.emilmelgaard.pathwake.puzzle.Difficulty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaygroundViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): PlaygroundViewModel {
        var seed = 0L
        return PlaygroundViewModel(generatorDispatcher = dispatcher, nextSeed = { seed++ }).also { advanceUntilIdle() }
    }

    @Test
    fun generatesAPuzzleOnStart() = runTest(dispatcher) {
        val state = viewModel().uiState.value
        assertFalse(state.generating)
        assertNotNull(state.puzzle)
        assertEquals(Difficulty.MEDIUM, state.puzzle?.difficulty)
    }

    @Test
    fun changingDifficultyGeneratesANewPuzzle() = runTest(dispatcher) {
        val vm = viewModel()
        vm.selectDifficulty(Difficulty.EASY)
        assertTrue(vm.uiState.value.generating)
        advanceUntilIdle()
        assertEquals(Difficulty.EASY, vm.uiState.value.puzzle?.difficulty)
    }

    @Test
    fun solvingCountsOncePerPuzzle() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onSolved()
        vm.onSolved()
        assertTrue(vm.uiState.value.solved)
        assertEquals(1, vm.uiState.value.solvedCount)
        vm.newPuzzle()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.solved)
        vm.onSolved()
        assertEquals(2, vm.uiState.value.solvedCount)
    }

    @Test
    fun solutionToggle_doesNotRegenerate() = runTest(dispatcher) {
        val vm = viewModel()
        val puzzle = vm.uiState.value.puzzle
        vm.toggleSolution()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.showSolution)
        assertEquals(puzzle, vm.uiState.value.puzzle)
    }
}
