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
