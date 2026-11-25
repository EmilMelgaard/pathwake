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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.emilmelgaard.pathwake.R
import io.github.emilmelgaard.pathwake.puzzle.Difficulty
import io.github.emilmelgaard.pathwake.puzzle.Generator
import io.github.emilmelgaard.pathwake.puzzle.Mechanic
import io.github.emilmelgaard.pathwake.ui.common.difficultyName
import io.github.emilmelgaard.pathwake.ui.puzzle.PuzzleLook
import io.github.emilmelgaard.pathwake.ui.puzzle.PuzzlePanel
import io.github.emilmelgaard.pathwake.ui.theme.PathwakeTheme

@Composable
fun PlaygroundScreen(
    initialDifficulty: Difficulty,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaygroundViewModel = viewModel { PlaygroundViewModel(initialDifficulty) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PlaygroundScreen(
        state = state,
        onBack = onBack,
        onSelectDifficulty = viewModel::selectDifficulty,
        onSelectLook = viewModel::selectLook,
        onNewPuzzle = viewModel::newPuzzle,
        onToggleSolution = viewModel::toggleSolution,
        onSolved = viewModel::onSolved,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaygroundScreen(
    state: PlaygroundUiState,
    onBack: () -> Unit,
    onSelectDifficulty: (Difficulty) -> Unit,
    onSelectLook: (PuzzleLook) -> Unit,
    onNewPuzzle: () -> Unit,
    onToggleSolution: () -> Unit,
    onSolved: () -> Unit,
    modifier: Modifier = Modifier
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
                    onSelectLook,
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
                    PuzzleArea(state, onSolved, Modifier.weight(1f).fillMaxHeight().padding(16.dp))
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    controls(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
                    PuzzleArea(state, onSolved, Modifier.weight(1f).fillMaxWidth().padding(8.dp))
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
    onSelectLook: (PuzzleLook) -> Unit,
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
        Text(stringResource(R.string.puzzle_look), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PuzzleLook.entries.forEach { look ->
                FilterChip(
                    selected = look == state.look,
                    onClick = { onSelectLook(look) },
                    label = { Text(stringResource(look.label)) }
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
    }
}

@Composable
private fun PuzzleArea(state: PlaygroundUiState, onSolved: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        val generated = state.puzzle
        if (generated != null) {
            key(generated) {
                PuzzlePanel(
                    puzzle = generated.puzzle,
                    onSolved = { onSolved() },
                    look = state.look,
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

@get:StringRes
private val PuzzleLook.label: Int
    get() = when (this) {
        PuzzleLook.PANEL -> R.string.look_panel
        PuzzleLook.MATERIAL -> R.string.look_material
    }

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
            onSelectLook = {},
            onNewPuzzle = {},
            onToggleSolution = {},
            onSolved = {}
        )
    }
}
