package io.github.emilmelgaard.pathwake.system

import androidx.test.platform.app.InstrumentationRegistry
import io.github.emilmelgaard.pathwake.puzzle.Difficulty
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PuzzleProgressTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun clear() {
        context.createDeviceProtectedStorageContext().deleteSharedPreferences("progress")
    }

    @Test
    fun warnsOncePerDifficulty_untilAPuzzleOfItIsSolved() {
        val progress = PuzzleProgress(context)
        assertFalse(progress.needsWarning(Difficulty.TRIVIAL))
        assertTrue(progress.needsWarning(Difficulty.HARD))

        progress.warned(Difficulty.HARD)
        progress.solved(Difficulty.EASY)

        val reloaded = PuzzleProgress(context)
        assertFalse(reloaded.needsWarning(Difficulty.HARD))
        assertFalse(reloaded.needsWarning(Difficulty.EASY))
        assertTrue(reloaded.needsWarning(Difficulty.MEDIUM))
    }
}
