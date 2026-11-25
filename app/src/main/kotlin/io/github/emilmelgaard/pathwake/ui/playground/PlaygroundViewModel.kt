package io.github.emilmelgaard.pathwake.ui.playground

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.emilmelgaard.pathwake.puzzle.Difficulty
import io.github.emilmelgaard.pathwake.puzzle.GeneratedPuzzle
import io.github.emilmelgaard.pathwake.puzzle.Generator
import io.github.emilmelgaard.pathwake.ui.puzzle.PuzzleLook
import kotlin.random.Random
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlaygroundUiState(
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val look: PuzzleLook = PuzzleLook.PANEL,
    val puzzle: GeneratedPuzzle? = null,
    val generating: Boolean = true,
    val generationMillis: Long = 0,
    val solved: Boolean = false,
    val solvedCount: Int = 0,
    val showSolution: Boolean = false
)

/** Lets the user try generated puzzles outside of an alarm. */
class PlaygroundViewModel(
    initialDifficulty: Difficulty = Difficulty.MEDIUM,
    initialLook: PuzzleLook = PuzzleLook.PANEL,
    private val generatorDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val nextSeed: () -> Long = { Random.nextLong() }
) : ViewModel() {
    private val _uiState = MutableStateFlow(PlaygroundUiState(difficulty = initialDifficulty, look = initialLook))
    val uiState: StateFlow<PlaygroundUiState> = _uiState.asStateFlow()

    private var generation: Job? = null

    init {
        newPuzzle()
    }

    fun selectDifficulty(difficulty: Difficulty) {
        if (difficulty == _uiState.value.difficulty) return
        _uiState.update { it.copy(difficulty = difficulty) }
        newPuzzle()
    }

    fun selectLook(look: PuzzleLook) = _uiState.update { it.copy(look = look) }

    fun toggleSolution() = _uiState.update { it.copy(showSolution = !it.showSolution) }

    fun onSolved() = _uiState.update { if (it.solved) it else it.copy(solved = true, solvedCount = it.solvedCount + 1) }

    fun newPuzzle() {
        generation?.cancel()
        val difficulty = _uiState.value.difficulty
        val seed = nextSeed()
        _uiState.update { it.copy(generating = true, solved = false, showSolution = false) }
        generation = viewModelScope.launch {
            val started = TimeSource.Monotonic.markNow()
            val puzzle = withContext(generatorDispatcher) { Generator.generate(difficulty, seed) }
            _uiState.update {
                it.copy(
                    puzzle = puzzle,
                    generating = false,
                    generationMillis = started.elapsedNow().inWholeMilliseconds
                )
            }
        }
    }
}
