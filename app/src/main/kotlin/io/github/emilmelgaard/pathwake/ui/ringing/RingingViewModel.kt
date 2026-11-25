package io.github.emilmelgaard.pathwake.ui.ringing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.emilmelgaard.pathwake.puzzle.Difficulty
import io.github.emilmelgaard.pathwake.puzzle.GeneratedPuzzle
import io.github.emilmelgaard.pathwake.puzzle.Generator
import io.github.emilmelgaard.pathwake.ring.RingingController
import io.github.emilmelgaard.pathwake.ring.RingingSession
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * @property puzzle the current puzzle; null while it is being generated.
 * @property solved the current puzzle was just solved (shown briefly before the next one).
 * @property finished the alarm stopped: all puzzles were solved, or it was silenced.
 */
data class RingingUiState(
    val session: RingingSession? = null,
    val puzzle: GeneratedPuzzle? = null,
    val solved: Boolean = false,
    val finished: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class RingingViewModel(
    private val controller: RingingController,
    private val generatorDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val solvedPause: Duration = 800.milliseconds
) : ViewModel() {
    private data class PuzzleKey(val difficulty: Difficulty, val seed: Long)

    /** Number of the puzzle that was just solved. */
    private val solvedNumber = MutableStateFlow<Int?>(null)

    private val puzzle = controller.session
        .map { session -> session?.let { PuzzleKey(it.difficulty, it.puzzleSeed) } }
        .distinctUntilChanged()
        .transformLatest { key ->
            emit(null)
            if (key != null) emit(withContext(generatorDispatcher) { Generator.generate(key.difficulty, key.seed) })
        }

    val uiState: StateFlow<RingingUiState> = combine(controller.session, puzzle, solvedNumber) {
            session,
            puzzle,
            solved
        ->
        RingingUiState(
            session = session,
            puzzle = puzzle?.takeIf {
                session != null && it.seed == session.puzzleSeed &&
                    it.difficulty == session.difficulty
            },
            solved = session != null && solved == session.puzzleNumber,
            finished = session == null
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        RingingUiState(session = controller.session.value, finished = controller.session.value == null)
    )

    /** The current puzzle was solved: shows that briefly, then moves on (or stops the alarm after the last one). */
    fun onSolved() {
        val session = controller.session.value ?: return
        if (solvedNumber.value == session.puzzleNumber) return
        solvedNumber.value = session.puzzleNumber
        viewModelScope.launch {
            delay(solvedPause)
            controller.puzzleSolved(session.puzzleNumber)
        }
    }

    /** The puzzle was touched (the alarm is quieter while solving). */
    fun interacted() = controller.interacted()

    fun newPuzzle() {
        val session = controller.session.value ?: return
        if (solvedNumber.value != session.puzzleNumber) controller.newPuzzle(session.puzzleNumber)
    }
}
