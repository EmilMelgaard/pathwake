package io.github.emilmelgaard.pathwake.puzzle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratorTest {
    private fun seeds(difficulty: Difficulty) = when (difficulty) {
        Difficulty.EASY, Difficulty.MEDIUM -> 1L..40L
        Difficulty.HARD, Difficulty.EXPERT -> 1L..20L
        Difficulty.FIENDISH -> 1L..8L
    }

    @Test
    fun generatedPuzzlesAreSolvableAndMeetTheirSolutionTarget() {
        val stats = StringBuilder()
        for (difficulty in Difficulty.entries) {
            var totalMillis = 0L
            var maxMillis = 0L
            var unknownCounts = 0
            for (seed in seeds(difficulty)) {
                val started = System.nanoTime()
                val generated = Generator.generate(difficulty, seed)
                val millis = (System.nanoTime() - started) / 1_000_000
                totalMillis += millis
                maxMillis = maxOf(maxMillis, millis)

                val puzzle = generated.puzzle
                val validation = Validator.validate(puzzle, generated.solution)
                assertTrue("$difficulty seed $seed: $validation", validation.isValid)
                assertTrue("$difficulty seed $seed has no symbols", puzzle.mechanics().isNotEmpty())
                val count = generated.solutionCount
                if (count == null) {
                    unknownCounts++
                } else {
                    val result = Solver.solve(puzzle, maxSolutions = 1000)
                    assertEquals("$difficulty seed $seed", count, result.solutions.size)
                    assertTrue(generated.solution in result.solutions)
                }
            }
            val n = seeds(difficulty).count()
            stats.appendLine(
                "$difficulty: avg ${totalMillis / n} ms, max $maxMillis ms, unknown solution count $unknownCounts/$n"
            )
        }
        println(stats)
    }

    private fun assertGeneratesWith(mechanic: Mechanic, spec: PuzzleSpec) {
        var found = 0
        for (seed in 1L..15L) {
            val generated = Generator.generate(spec, seed) ?: continue
            assertTrue("seed $seed", Validator.validate(generated.puzzle, generated.solution).isValid)
            if (mechanic in generated.mechanics) found++
        }
        assertTrue("$mechanic appeared in only $found of 15 puzzles", found >= 5)
    }

    @Test
    fun generatesTetrisPieces() = assertGeneratesWith(
        Mechanic.TETRIS,
        PuzzleSpec(4, 4, setOf(Mechanic.TETRIS), maxSolutions = 2, rotatableChance = 0.5f)
    )

    @Test
    fun generatesNegativeTetrisPieces() = assertGeneratesWith(
        Mechanic.NEGATIVE_TETRIS,
        PuzzleSpec(4, 4, setOf(Mechanic.TETRIS, Mechanic.NEGATIVE_TETRIS), maxSolutions = 2, initialSymbols = 4)
    )

    @Test
    fun generatesEliminationMarks() = assertGeneratesWith(
        Mechanic.ELIMINATION,
        PuzzleSpec(4, 4, setOf(Mechanic.SQUARES, Mechanic.ELIMINATION), maxSolutions = 2, initialSymbols = 4)
    )

    @Test
    fun generatesSymmetryPuzzles() {
        for (symmetry in listOf(Symmetry.HORIZONTAL, Symmetry.VERTICAL, Symmetry.ROTATIONAL)) {
            assertGeneratesWith(
                Mechanic.SYMMETRY,
                PuzzleSpec(4, 4, setOf(Mechanic.DOTS, Mechanic.SQUARES), symmetry = symmetry, maxSolutions = 2)
            )
        }
    }

    @Test
    fun sameSeedGivesSamePuzzle() {
        for (difficulty in Difficulty.entries) {
            assertEquals(Generator.generate(difficulty, 42), Generator.generate(difficulty, 42))
        }
    }

    @Test
    fun presetsUseTheirGridSizes() {
        assertEquals(3, Generator.generate(Difficulty.EASY, 7).puzzle.width)
        assertEquals(6, Generator.generate(Difficulty.FIENDISH, 7).puzzle.width)
    }
}
