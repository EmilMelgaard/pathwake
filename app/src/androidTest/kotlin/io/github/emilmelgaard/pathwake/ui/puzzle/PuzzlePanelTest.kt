package io.github.emilmelgaard.pathwake.ui.puzzle

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import io.github.emilmelgaard.pathwake.puzzle.Direction
import io.github.emilmelgaard.pathwake.puzzle.Exit
import io.github.emilmelgaard.pathwake.puzzle.Pos
import io.github.emilmelgaard.pathwake.puzzle.Puzzle
import io.github.emilmelgaard.pathwake.puzzle.Square
import io.github.emilmelgaard.pathwake.puzzle.SymbolColor
import io.github.emilmelgaard.pathwake.puzzle.Symmetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class PuzzlePanelTest {
    @get:Rule
    val rule = createComposeRule()

    // 2x2 cells, start bottom-left, exit top-right. Only the "staircase" separates the two squares.
    private val puzzle = Puzzle(
        width = 2,
        height = 2,
        starts = setOf(Pos(0, 4)),
        exits = listOf(Exit(Pos(4, 0), Direction.RIGHT)),
        symbols = mapOf(Pos(1, 1) to Square(SymbolColor.BLACK), Pos(3, 3) to Square(SymbolColor.WHITE))
    )
    private val staircase = listOf(Pos(0, 4), Pos(2, 4), Pos(2, 2), Pos(4, 2), Pos(4, 0))
    private val alongBorder = listOf(Pos(0, 4), Pos(0, 2), Pos(0, 0), Pos(2, 0), Pos(4, 0))

    // 4x2 cells mirrored left-right: starts at the bottom corners, exits pointing up at x = 1 and x = 3 (node units).
    private val symmetric = Puzzle(
        width = 4,
        height = 2,
        starts = setOf(Pos(0, 4), Pos(8, 4)),
        exits = listOf(Exit(Pos(2, 0), Direction.UP), Exit(Pos(6, 0), Direction.UP)),
        dots = setOf(Pos(8, 2)),
        symmetry = Symmetry.HORIZONTAL
    )
    private val leftLine = listOf(Pos(0, 4), Pos(0, 2), Pos(0, 0), Pos(2, 0))
    private val rightLine = listOf(Pos(8, 4), Pos(8, 2), Pos(8, 0), Pos(6, 0))

    private fun drawLine(path: List<Pos>, onPuzzle: Puzzle = puzzle): List<Pos>? {
        var solved: List<Pos>? = null
        rule.setContent {
            PuzzlePanel(onPuzzle, onSolved = { solved = it }, modifier = Modifier.size(320.dp).testTag("panel"))
        }
        rule.onNodeWithTag("panel").performTouchInput { drag(onPuzzle, path) }
        rule.waitForIdle()
        return solved
    }

    @Test
    fun drawingTheSolution_solvesThePuzzle() {
        assertEquals(staircase, drawLine(staircase))
    }

    @Test
    fun drawingAWrongLine_doesNotSolve() {
        assertNull(drawLine(alongBorder))
    }

    @Test
    fun symmetryPuzzle_solvedFromTheLeftStart() {
        assertEquals(leftLine, drawLine(leftLine, symmetric))
    }

    @Test
    fun symmetryPuzzle_solvedFromTheRightStart() {
        assertEquals(rightLine, drawLine(rightLine, symmetric))
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun straightTrack_canBeDrawnWithAScreenReaderAction() {
        val track =
            Puzzle(width = 3, height = 0, starts = setOf(Pos(0, 0)), exits = listOf(Exit(Pos(6, 0), Direction.RIGHT)))
        var solved: List<Pos>? = null
        rule.setContent { PuzzlePanel(track, onSolved = { solved = it }, modifier = Modifier.testTag("panel")) }

        rule.onNodeWithTag("panel").performCustomAccessibilityActionWithLabel("Draw the line")
        assertEquals(listOf(Pos(0, 0), Pos(2, 0), Pos(4, 0), Pos(6, 0)), solved)
    }

    @Test
    fun gridPuzzle_hasNoScreenReaderShortcut() {
        rule.setContent { PuzzlePanel(puzzle, onSolved = {}, modifier = Modifier.testTag("panel")) }
        rule.onNodeWithTag("panel").assert(
            SemanticsMatcher.keyNotDefined(SemanticsActions.CustomActions)
        )
    }
}
