package io.github.emilmelgaard.pathwake.puzzle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedRulesTest {
    // STAIRCASE splits the 2x2 grid into the bottom-right cell and an L of the other three; ALONG_BORDER leaves one
    // region of all four cells.
    private val lShape = Polyomino.parse("XX", "X.")
    private val square = Polyomino.parse("XX", "XX")
    private val monomino = Polyomino.parse("X")
    private val domino = Polyomino.parse("XX")

    private fun valid(symbols: Map<Pos, Symbol>, path: List<Pos>, dots: Set<Pos> = emptySet()) =
        Validator.validate(twoByTwo(symbols = symbols, dots = dots), path).isValid

    @Test
    fun polyomino_normalizesAndRotates() {
        assertEquals(Polyomino.parse("XX", "X."), Polyomino.of(listOf(Pos(5, 5), Pos(6, 5), Pos(5, 6))))
        assertEquals(1, square.rotations.size)
        assertEquals(2, Polyomino.parse("XXX").rotations.size)
        assertEquals(4, lShape.rotations.size)
        assertEquals(Polyomino.parse("XX", ".X"), lShape.rotated())
    }

    @Test
    fun tetris_mustExactlyTileTheRegion() {
        assertTrue(valid(mapOf(cell(0, 0) to Tetris(lShape)), STAIRCASE))
        assertFalse(valid(mapOf(cell(0, 0) to Tetris(lShape)), ALONG_BORDER))
        assertTrue(valid(mapOf(cell(0, 0) to Tetris(square)), ALONG_BORDER))
        assertTrue(valid(mapOf(cell(0, 0) to Tetris(domino), cell(1, 1) to Tetris(domino)), ALONG_BORDER))
    }

    @Test
    fun tetris_onlyRotatablePiecesMayTurn() {
        val turned = Polyomino.parse("X.", "XX")
        assertFalse(valid(mapOf(cell(0, 0) to Tetris(turned)), STAIRCASE))
        assertTrue(valid(mapOf(cell(0, 0) to Tetris(turned, rotatable = true)), STAIRCASE))
    }

    @Test
    fun negativeTetris_cancelsCellsOfPositivePieces() {
        val squareMinusOne = mapOf(cell(0, 0) to Tetris(square), cell(1, 0) to Tetris(monomino, negative = true))
        assertTrue(valid(squareMinusOne, STAIRCASE))
        assertFalse(valid(squareMinusOne, ALONG_BORDER))
    }

    @Test
    fun negativeTetris_equalAreasCancelCompletely() {
        val cancelling = mapOf(cell(0, 0) to Tetris(domino), cell(1, 0) to Tetris(domino, negative = true))
        assertTrue(valid(cancelling, STAIRCASE))
        assertTrue(valid(cancelling, ALONG_BORDER))
    }

    @Test
    fun elimination_erasesOneBrokenSymbol() {
        val mixed = mapOf(
            cell(0, 0) to Square(SymbolColor.BLACK),
            cell(1, 1) to Square(SymbolColor.WHITE),
            cell(1, 0) to Elimination()
        )
        assertTrue(valid(mixed, ALONG_BORDER))
    }

    @Test
    fun elimination_withNothingToFixIsAnError() {
        val separated = mapOf(
            cell(0, 0) to Square(SymbolColor.BLACK),
            cell(1, 1) to Square(SymbolColor.WHITE),
            cell(1, 0) to Elimination()
        )
        val result = Validator.validate(twoByTwo(symbols = separated), STAIRCASE)
        assertFalse(result.isValid)
        assertTrue(cell(1, 0) in result.violations)
    }

    @Test
    fun elimination_canEraseAnUncoveredDot() {
        val dot = setOf(node(1, 1))
        assertFalse(valid(emptyMap(), ALONG_BORDER, dot))
        assertTrue(valid(mapOf(cell(0, 0) to Elimination()), ALONG_BORDER, dot))
    }

    @Test
    fun elimination_fixesOnlyOneProblem() {
        val twoProblems = mapOf(
            cell(0, 0) to Square(SymbolColor.BLACK),
            cell(1, 1) to Square(SymbolColor.WHITE),
            cell(0, 1) to Triangles(3),
            cell(1, 0) to Elimination()
        )
        assertFalse(valid(twoProblems, ALONG_BORDER))
    }

    @Test
    fun elimination_marksDoNotCountForStars() {
        val starAndMark = mapOf(cell(0, 0) to Star(SymbolColor.WHITE), cell(1, 0) to Elimination())
        // The lone star is the only problem, and the mark erases it.
        assertTrue(valid(starAndMark, ALONG_BORDER))
    }

    // 4x2 cells, mirrored left-right. Starts at the bottom corners, exits pointing up at x = 1 and x = 3.
    private fun symmetric(dots: Set<Pos> = emptySet(), dotLines: Map<Pos, Int> = emptyMap()) = Puzzle(
        width = 4,
        height = 2,
        starts = setOf(node(0, 2), node(4, 2)),
        exits = listOf(Exit(node(1, 0), Direction.UP), Exit(node(3, 0), Direction.UP)),
        dots = dots,
        symmetry = Symmetry.HORIZONTAL,
        dotLines = dotLines
    )

    private val leftLine = line(0 to 2, 0 to 1, 0 to 0, 1 to 0)
    private val rightLine = line(4 to 2, 4 to 1, 4 to 0, 3 to 0)

    @Test
    fun symmetry_mirroredLineIsDrawnToo() {
        val dotOnRight = setOf(node(4, 1))
        assertTrue(Validator.validate(symmetric(dots = dotOnRight), leftLine).isValid)
        assertTrue(Validator.validate(symmetric(dots = dotOnRight), rightLine).isValid)
    }

    @Test
    fun symmetry_linesMayNotTouchTheAxis() {
        val result = Validator.validate(symmetric(), line(0 to 2, 1 to 2, 2 to 2, 2 to 1, 1 to 1, 1 to 0))
        assertFalse(result.isValid)
        assertNotNull(result.pathError)
    }

    @Test
    fun symmetry_coloredDotsNeedTheRightLine() {
        val dot = node(0, 1)
        val leftLineDot = symmetric(dots = setOf(dot), dotLines = mapOf(dot to 0))
        val rightLineDot = symmetric(dots = setOf(dot), dotLines = mapOf(dot to 1))
        // Line 0 starts at the start that comes first in reading order: (0, 2).
        assertTrue(Validator.validate(leftLineDot, leftLine).isValid)
        assertTrue(Validator.validate(leftLineDot, rightLine).isValid)
        assertFalse(Validator.validate(rightLineDot, leftLine).isValid)
        assertFalse(Validator.validate(rightLineDot, rightLine).isValid)
    }

    @Test
    fun symmetry_solverCountsEachLinePairOnce() {
        val result = Solver.solve(symmetric(dots = setOf(node(0, 1), node(1, 1))), maxSolutions = 100)
        assertTrue(result.exhaustive)
        assertTrue(result.solutions.isNotEmpty())
        result.solutions.forEach { solution ->
            assertEquals(node(0, 2), solution.first())
            assertTrue(Validator.validate(symmetric(dots = setOf(node(0, 1), node(1, 1))), solution).isValid)
        }
    }

    @Test
    fun symmetry_tracerStopsTheMirrorAtGaps() {
        val puzzle = symmetric().copy(gaps = setOf(Pos(8, 3)))
        val tracer = PathTracer(puzzle)
        assertTrue(tracer.begin(0f, 2f, 0.3f))
        tracer.drag(0f, 1f)
        assertEquals(line(0 to 2), tracer.path)
        assertTrue(tracer.snapshot().tipY > 1.5f)
    }
}
