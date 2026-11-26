package io.github.emilmelgaard.pathwake.ui.playground

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.emilmelgaard.pathwake.R
import io.github.emilmelgaard.pathwake.appContainer
import io.github.emilmelgaard.pathwake.puzzle.Difficulty
import io.github.emilmelgaard.pathwake.puzzle.Generator
import io.github.emilmelgaard.pathwake.puzzle.Mechanic
import io.github.emilmelgaard.pathwake.ui.common.difficultyName
import io.github.emilmelgaard.pathwake.ui.puzzle.PuzzlePanel
import io.github.emilmelgaard.pathwake.ui.theme.PathwakeTheme
import kotlinx.coroutines.launch

@Composable
fun PlaygroundScreen(
    initialDifficulty: Difficulty,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaygroundViewModel = viewModel { PlaygroundViewModel(initialDifficulty) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val container = LocalContext.current.appContainer
    val settings by container.settings.current.collectAsStateWithLifecycle()
    PlaygroundScreen(
        state = state,
        onBack = onBack,
        onSelectDifficulty = viewModel::selectDifficulty,
        onNewPuzzle = viewModel::newPuzzle,
        onToggleSolution = viewModel::toggleSolution,
        onSolved = {
            // Drawing the shown solution doesn't count as solving it.
            if (!state.showSolution) state.puzzle?.difficulty?.let(container.progress::solved)
            viewModel.onSolved()
        },
        modifier = modifier,
        colorMarks = settings.colorMarks
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaygroundScreen(
    state: PlaygroundUiState,
    onBack: () -> Unit,
    onSelectDifficulty: (Difficulty) -> Unit,
    onNewPuzzle: () -> Unit,
    onToggleSolution: () -> Unit,
    onSolved: () -> Unit,
    modifier: Modifier = Modifier,
    colorMarks: Boolean = false
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.playground_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val controls = @Composable { controlsModifier: Modifier ->
                PlaygroundControls(
                    state,
                    onSelectDifficulty,
                    onNewPuzzle,
                    onToggleSolution,
                    controlsModifier
                )
            }
            if (maxWidth > maxHeight) {
                Row(Modifier.fillMaxSize()) {
                    controls(
                        Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(16.dp)
                    )
                    PuzzleArea(state, colorMarks, onSolved, Modifier.weight(1f).fillMaxHeight().padding(16.dp))
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    controls(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
                    PuzzleArea(state, colorMarks, onSolved, Modifier.weight(1f).fillMaxWidth().padding(8.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaygroundControls(
    state: PlaygroundUiState,
    onSelectDifficulty: (Difficulty) -> Unit,
    onNewPuzzle: () -> Unit,
    onToggleSolution: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.difficulty), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Difficulty.entries.forEach { difficulty ->
                FilterChip(
                    selected = difficulty == state.difficulty,
                    onClick = { onSelectDifficulty(difficulty) },
                    label = { Text(difficultyName(difficulty)) }
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onNewPuzzle) { Text(stringResource(R.string.new_puzzle)) }
            OutlinedButton(onClick = onToggleSolution, enabled = state.puzzle != null) {
                Text(stringResource(if (state.showSolution) R.string.hide_solution else R.string.show_solution))
            }
        }
        state.puzzle?.let { generated ->
            val puzzle = generated.puzzle
            val mechanics = generated.mechanics.map { mechanicName(it) }.joinToString()
                .ifEmpty { stringResource(R.string.no_symbols) }
            val solutions = generated.solutionCount?.let {
                pluralStringResource(R.plurals.solution_count, it, it)
            } ?: stringResource(R.string.solution_count_unknown)
            Text(
                stringResource(
                    R.string.puzzle_info,
                    puzzle.width,
                    puzzle.height,
                    mechanics,
                    solutions,
                    state.generationMillis
                ),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(
            stringResource(R.string.solved_count, state.solvedCount),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            stringResource(R.string.rules_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PuzzleArea(
    state: PlaygroundUiState,
    colorMarks: Boolean,
    onSolved: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        val generated = state.puzzle
        if (generated != null) {
            key(generated) {
                PuzzlePanel(
                    puzzle = generated.puzzle,
                    onSolved = { onSolved() },
                    colorMarks = colorMarks,
                    solutionHint = generated.solution.takeIf { state.showSolution },
                    enabled = !state.generating,
                    modifier = Modifier.fillMaxSize().testTag(PUZZLE_TAG)
                )
            }
        }
        if (state.generating) CircularProgressIndicator()
        if (state.solved) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    stringResource(R.string.solved),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

internal const val PUZZLE_TAG = "puzzle"

@Composable
private fun mechanicName(mechanic: Mechanic) = stringResource(
    when (mechanic) {
        Mechanic.DOTS -> R.string.mechanic_dots
        Mechanic.GAPS -> R.string.mechanic_gaps
        Mechanic.SQUARES -> R.string.mechanic_squares
        Mechanic.STARS -> R.string.mechanic_stars
        Mechanic.TRIANGLES -> R.string.mechanic_triangles
        Mechanic.TETRIS -> R.string.mechanic_tetris
        Mechanic.NEGATIVE_TETRIS -> R.string.mechanic_negative_tetris
        Mechanic.ELIMINATION -> R.string.mechanic_elimination
        Mechanic.SYMMETRY -> R.string.mechanic_symmetry
        Mechanic.MULTIPLE_STARTS_AND_EXITS -> R.string.mechanic_multiple_starts_exits
    }
)

@Preview(showBackground = true, widthDp = 1280, heightDp = 800)
@Preview(showBackground = true, widthDp = 411, heightDp = 891)
@Composable
private fun PlaygroundScreenPreview() {
    PathwakeTheme {
        PlaygroundScreen(
            state = PlaygroundUiState(
                puzzle = Generator.generate(Difficulty.HARD, 3),
                generating = false,
                solvedCount = 2
            ),
            onBack = {},
            onSelectDifficulty = {},
            onNewPuzzle = {},
            onToggleSolution = {},
            onSolved = {}
        )
    }
}
