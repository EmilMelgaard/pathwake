package io.github.emilmelgaard.pathwake.ui.puzzle

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import io.github.emilmelgaard.pathwake.puzzle.Direction
import io.github.emilmelgaard.pathwake.puzzle.Exit
import io.github.emilmelgaard.pathwake.puzzle.Pos
import io.github.emilmelgaard.pathwake.puzzle.Puzzle
import io.github.emilmelgaard.pathwake.puzzle.Square
import io.github.emilmelgaard.pathwake.puzzle.SymbolColor
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

    private fun drawLine(path: List<Pos>): List<Pos>? {
        var solved: List<Pos>? = null
        rule.setContent {
            PuzzlePanel(puzzle, onSolved = { solved = it }, modifier = Modifier.size(320.dp).testTag("panel"))
        }
        rule.onNodeWithTag("panel").performTouchInput { drag(path) }
        rule.waitForIdle()
        return solved
    }

    private fun TouchInjectionScope.drag(path: List<Pos>) {
        val geometry = PuzzleGeometry(puzzle, width.toFloat(), height.toFloat())
        down(geometry.toPx(path.first()))
        for (i in 1 until path.size) {
            val from = geometry.toPx(path[i - 1])
            val to = geometry.toPx(path[i])
            for (step in 1..5) moveTo(from + (to - from) * (step / 5f))
        }
        moveTo(geometry.exitEnd(path.last(), Direction.RIGHT) + androidx.compose.ui.geometry.Offset(10f, 0f))
        up()
    }

    @Test
    fun drawingTheSolution_solvesThePuzzle() {
        assertEquals(staircase, drawLine(staircase))
    }

    @Test
    fun drawingAWrongLine_doesNotSolve() {
        assertNull(drawLine(alongBorder))
    }
}
