package io.github.emilmelgaard.pathwake.ui.ringing

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import io.github.emilmelgaard.pathwake.puzzle.Difficulty
import io.github.emilmelgaard.pathwake.puzzle.Generator
import io.github.emilmelgaard.pathwake.ring.RingingSession
import io.github.emilmelgaard.pathwake.ui.puzzle.drag
import io.github.emilmelgaard.pathwake.ui.theme.PathwakeTheme
import java.time.Instant
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RingingContentTest {
    @get:Rule
    val rule = createComposeRule()

    private val generated = Generator.generate(Difficulty.EASY, 7)
    private val session = RingingSession(
        alarmIds = listOf(1),
        labels = listOf("Work"),
        startedAt = Instant.EPOCH,
        alarmTime = LocalTime.of(7, 0),
        silenceAt = null,
        difficulty = Difficulty.EASY,
        puzzleCount = 3,
        solved = 1,
        seed = 7
    )

    private fun show(state: RingingUiState, onSolved: () -> Unit = {}, onNewPuzzle: () -> Unit = {}) = rule.setContent {
        PathwakeTheme(darkTheme = true) { RingingContent(state, LocalTime.of(7, 1), onSolved, onNewPuzzle) }
    }

    @Test
    fun showsTheAlarmAndProgress() {
        show(RingingUiState(session, generated))
        rule.onNodeWithText("Work").assertExists()
        rule.onNodeWithText("Puzzle 2 of 3").assertExists()
    }

    @Test
    fun drawingTheSolution_reportsItSolved() {
        var solved = 0
        show(RingingUiState(session, generated), onSolved = { solved++ })

        rule.onNodeWithTag(RINGING_PUZZLE_TAG).performTouchInput { drag(generated.puzzle, generated.solution) }
        rule.waitForIdle()

        assertEquals(1, solved)
    }

    @Test
    fun newPuzzle_isOfferedUntilThePuzzleIsSolved() {
        var newPuzzles = 0
        show(RingingUiState(session, generated), onNewPuzzle = { newPuzzles++ })
        rule.onNodeWithText("New puzzle").performClick()
        assertEquals(1, newPuzzles)
    }

    @Test
    fun solvedPuzzle_canNotBeReplaced() {
        show(RingingUiState(session, generated, solved = true))
        rule.onNodeWithText("Solved!").assertExists()
        rule.onNodeWithText("New puzzle").assertIsNotEnabled()
    }

    @Test
    fun test_canBeCancelled_butARealAlarmCanNot() {
        var cancelled = 0
        rule.setContent {
            PathwakeTheme(darkTheme = true) {
                RingingContent(
                    RingingUiState(session.copy(test = true), generated),
                    LocalTime.of(7, 1),
                    onSolved = {},
                    onNewPuzzle = {},
                    onCancelTest = { cancelled++ }
                )
            }
        }
        rule.onNodeWithText("Test").assertExists()
        rule.onNodeWithText("Cancel").performClick()
        assertEquals(1, cancelled)
    }

    @Test
    fun realAlarm_hasNoCancelButton() {
        show(RingingUiState(session, generated))
        rule.onNodeWithText("Cancel").assertDoesNotExist()
    }
}
