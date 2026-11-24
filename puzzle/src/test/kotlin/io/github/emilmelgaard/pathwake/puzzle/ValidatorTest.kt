package io.github.emilmelgaard.pathwake.puzzle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatorTest {
    private fun assertPathError(puzzle: Puzzle, path: List<Pos>) {
        val result = Validator.validate(puzzle, path)
        assertFalse(result.isValid)
        assertNotNull(result.pathError)
    }

    @Test
    fun emptyPuzzle_anyLegalLineIsValid() {
        assertTrue(Validator.validate(twoByTwo(), STAIRCASE).isValid)
        assertTrue(Validator.validate(twoByTwo(), ALONG_BORDER).isValid)
    }

    @Test
    fun line_mustStartAtStartAndEndAtExit() {
        assertPathError(twoByTwo(), line(1 to 2, 1 to 1, 2 to 1, 2 to 0))
        assertPathError(twoByTwo(), line(0 to 2, 1 to 2, 1 to 1))
    }

    @Test
    fun line_mustBeContinuousAndNotRevisit() {
        assertPathError(twoByTwo(), line(0 to 2, 1 to 1, 2 to 0))
        assertPathError(twoByTwo(), line(0 to 2, 1 to 2, 1 to 1, 0 to 1, 0 to 2, 0 to 1))
    }

    @Test
    fun line_cannotCrossGap() {
        val puzzle = twoByTwo(gaps = setOf(STAIRCASE[0].step(Direction.RIGHT)))
        assertPathError(puzzle, STAIRCASE)
        assertTrue(Validator.validate(puzzle, ALONG_BORDER).isValid)
    }

    @Test
    fun dots_mustBeCovered() {
        val dot = node(1, 1)
        val puzzle = twoByTwo(dots = setOf(dot))
        assertTrue(Validator.validate(puzzle, STAIRCASE).isValid)
        val result = Validator.validate(puzzle, ALONG_BORDER)
        assertFalse(result.isValid)
        assertEquals(setOf(dot), result.violations)
    }

    @Test
    fun squares_ofDifferentColorsMustBeSeparated() {
        val puzzle = twoByTwo(
            symbols = mapOf(cell(0, 0) to Square(SymbolColor.BLACK), cell(1, 1) to Square(SymbolColor.WHITE))
        )
        assertTrue(Validator.validate(puzzle, STAIRCASE).isValid)
        val result = Validator.validate(puzzle, ALONG_BORDER)
        assertFalse(result.isValid)
        assertEquals(setOf(cell(0, 0), cell(1, 1)), result.violations)
    }

    @Test
    fun stars_needExactlyOneSameColoredPartner() {
        val pair = mapOf(cell(0, 0) to Star(SymbolColor.MAGENTA), cell(1, 0) to Star(SymbolColor.MAGENTA))
        assertTrue(Validator.validate(twoByTwo(symbols = pair), STAIRCASE).isValid)

        val starAndSquare = mapOf(cell(0, 0) to Star(SymbolColor.BLACK), cell(1, 0) to Square(SymbolColor.BLACK))
        assertTrue(Validator.validate(twoByTwo(symbols = starAndSquare), STAIRCASE).isValid)

        val three = pair + (cell(0, 1) to Square(SymbolColor.MAGENTA))
        val result = Validator.validate(twoByTwo(symbols = three), STAIRCASE)
        assertFalse(result.isValid)
        assertEquals(setOf(cell(0, 0), cell(1, 0)), result.violations)

        val lonely = mapOf(cell(1, 1) to Star(SymbolColor.GREEN), cell(0, 0) to Star(SymbolColor.GREEN))
        assertFalse(Validator.validate(twoByTwo(symbols = lonely), STAIRCASE).isValid)
        assertTrue(Validator.validate(twoByTwo(symbols = lonely), ALONG_BORDER).isValid)
    }

    @Test
    fun triangles_countUsedEdgesOfTheirCell() {
        assertTrue(Validator.validate(twoByTwo(symbols = mapOf(cell(1, 1) to Triangles(2))), STAIRCASE).isValid)
        val result = Validator.validate(twoByTwo(symbols = mapOf(cell(1, 1) to Triangles(1))), STAIRCASE)
        assertFalse(result.isValid)
        assertEquals(setOf(cell(1, 1)), result.violations)
        assertTrue(Validator.validate(twoByTwo(symbols = mapOf(cell(0, 0) to Triangles(2))), ALONG_BORDER).isValid)
    }

    @Test
    fun orangeStar_countsTriangles() {
        val symbols = mapOf(cell(0, 0) to Triangles(2), cell(1, 1) to Star(SymbolColor.ORANGE))
        assertTrue(Validator.validate(twoByTwo(symbols = symbols), ALONG_BORDER).isValid)
        assertFalse(Validator.validate(twoByTwo(symbols = symbols), STAIRCASE).isValid)
    }
}
