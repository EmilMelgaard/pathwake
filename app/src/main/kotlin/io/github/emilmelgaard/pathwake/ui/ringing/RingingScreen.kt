package io.github.emilmelgaard.pathwake.ui.ringing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.emilmelgaard.pathwake.R
import io.github.emilmelgaard.pathwake.alarm.minuteTicker
import io.github.emilmelgaard.pathwake.appContainer
import io.github.emilmelgaard.pathwake.ui.common.formatTime
import io.github.emilmelgaard.pathwake.ui.puzzle.PuzzleLook
import io.github.emilmelgaard.pathwake.ui.puzzle.PuzzlePanel
import java.time.LocalTime

@Composable
fun RingingScreen(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val container = LocalContext.current.appContainer
    val viewModel: RingingViewModel = viewModel { RingingViewModel(container.ringing) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val time by produceState(container.now().toLocalTime()) {
        minuteTicker(container.now).collect { value = it.toLocalTime() }
    }
    val currentOnFinished by rememberUpdatedState(onFinished)
    LaunchedEffect(state.finished) { if (state.finished) currentOnFinished() }
    RingingContent(state, time, viewModel::onSolved, viewModel::newPuzzle, modifier)
}

/** The clock, what is ringing, and the puzzle that stops it. */
@Composable
fun RingingContent(
    state: RingingUiState,
    time: LocalTime,
    onSolved: () -> Unit,
    onNewPuzzle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
            if (maxWidth > maxHeight) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(
                        Modifier.width(320.dp).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Header(state, time)
                        NewPuzzleButton(state, onNewPuzzle)
                    }
                    PuzzleArea(state, onSolved, Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Header(state, time, Modifier.padding(top = 16.dp))
                    PuzzleArea(state, onSolved, Modifier.weight(1f).fillMaxWidth())
                    NewPuzzleButton(state, onNewPuzzle)
                }
            }
        }
    }
}

@Composable
private fun Header(state: RingingUiState, time: LocalTime, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(formatTime(time), style = MaterialTheme.typography.displayLarge)
        val session = state.session ?: return@Column
        if (session.labels.isNotEmpty()) {
            Text(
                session.labels.joinToString(),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
        }
        Text(
            if (session.puzzleCount == 1) {
                pluralStringResource(R.plurals.ringing_solve_to_stop, 1, 1)
            } else {
                stringResource(R.string.ringing_progress, session.solved + 1, session.puzzleCount)
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun PuzzleArea(state: RingingUiState, onSolved: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        val generated = state.puzzle
        if (generated == null) {
            CircularProgressIndicator()
        } else {
            key(generated) {
                PuzzlePanel(
                    puzzle = generated.puzzle,
                    onSolved = { onSolved() },
                    look = PuzzleLook.PANEL,
                    enabled = !state.solved,
                    modifier = Modifier.fillMaxSize().testTag(RINGING_PUZZLE_TAG)
                )
            }
        }
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

@Composable
private fun NewPuzzleButton(state: RingingUiState, onNewPuzzle: () -> Unit) {
    OutlinedButton(onClick = onNewPuzzle, enabled = state.puzzle != null && !state.solved) {
        Text(stringResource(R.string.new_puzzle))
    }
}

internal const val RINGING_PUZZLE_TAG = "ringingPuzzle"
