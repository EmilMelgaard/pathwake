package io.github.emilmelgaard.pathwake.puzzle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SolverTest {
    private val oneByOne = Puzzle(1, 1, setOf(node(0, 0)), listOf(Exit(node(1, 1), Direction.RIGHT)))

    @Test
    fun findsAllSolutionsOfOpenGrid() {
        val result = Solver.solve(oneByOne, maxSolutions = 10)
        assertTrue(result.exhaustive)
        assertEquals(2, result.solutions.size)
    }

    @Test
    fun countsCornerToCornerPathsOn3x3() {
        // Known count of self-avoiding corner-to-corner paths on a 4x4 node grid.
        val puzzle = Puzzle(3, 3, setOf(node(0, 0)), listOf(Exit(node(3, 3), Direction.RIGHT)))
        val result = Solver.solve(puzzle, maxSolutions = 1000)
        assertTrue(result.exhaustive)
        assertEquals(184, result.solutions.size)
    }

    @Test
    fun dotsRestrictSolutions() {
        val result = Solver.solve(oneByOne.copy(dots = setOf(Pos(1, 0))), maxSolutions = 10)
        assertEquals(listOf(line(0 to 0, 1 to 0, 1 to 1)), result.solutions)
    }

    @Test
    fun everySolutionIsValid() {
        val puzzle = twoByTwo(
            dots = setOf(node(1, 1)),
            symbols = mapOf(cell(0, 0) to Square(SymbolColor.BLACK), cell(1, 1) to Square(SymbolColor.WHITE))
        )
        val result = Solver.solve(puzzle, maxSolutions = 100)
        assertTrue(result.exhaustive)
        assertTrue(STAIRCASE in result.solutions)
        result.solutions.forEach { assertTrue(Validator.validate(puzzle, it).isValid) }
    }

    @Test
    fun stopsAtMaxSolutions() {
        val puzzle = Puzzle(3, 3, setOf(node(0, 0)), listOf(Exit(node(3, 3), Direction.RIGHT)))
        val result = Solver.solve(puzzle, maxSolutions = 5)
        assertEquals(5, result.solutions.size)
        assertFalse(result.exhaustive)
    }

    @Test
    fun unsolvablePuzzleHasNoSolutions() {
        val puzzle = oneByOne.copy(gaps = setOf(Pos(1, 0), Pos(0, 1)))
        val result = Solver.solve(puzzle, maxSolutions = 10)
        assertTrue(result.exhaustive)
        assertTrue(result.solutions.isEmpty())
    }
}
