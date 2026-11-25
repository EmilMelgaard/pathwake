package io.github.emilmelgaard.pathwake.puzzle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PathTracerTest {
    private val puzzle = Puzzle(2, 2, setOf(node(0, 0)), listOf(Exit(node(2, 2), Direction.RIGHT)))

    private fun tracer(p: Puzzle = puzzle) = PathTracer(p).also { assertTrue(it.begin(0.05f, 0.05f, 0.3f)) }

    @Test
    fun begin_onlyNearAStart() {
        assertFalse(PathTracer(puzzle).begin(1f, 1f, 0.3f))
        assertTrue(PathTracer(puzzle).begin(0.1f, -0.1f, 0.3f))
    }

    @Test
    fun drag_followsEdgesToTheExit() {
        val t = tracer()
        t.drag(1f, 0f)
        t.drag(2f, 0f)
        t.drag(2f, 1f)
        t.drag(2f, 2f)
        assertFalse(t.isAtExit)
        t.drag(2.4f, 2f)
        assertTrue(t.isAtExit)
        assertEquals(line(0 to 0, 1 to 0, 2 to 0, 2 to 1, 2 to 2), t.path)
    }

    @Test
    fun straightTrack_canOnlyBeDrawnStraight() {
        val track = Puzzle(3, 0, setOf(node(0, 0)), listOf(Exit(node(3, 0), Direction.RIGHT)))
        val t = tracer(track)
        t.drag(1f, 0.4f)
        t.drag(2f, -0.4f)
        t.drag(3.4f, 0f)
        assertTrue(t.isAtExit)
        assertEquals(line(0 to 0, 1 to 0, 2 to 0, 3 to 0), t.path)
        assertTrue(Validator.validate(track, t.path).isValid)
    }

    @Test
    fun drag_bigJumpsAreWalkedStepByStep() {
        val t = tracer()
        t.drag(2.5f, 2f)
        assertTrue(t.isAtExit)
        assertTrue(Validator.validate(puzzle, t.path).isValid)
    }

    @Test
    fun drag_backwardsRetractsTheLine() {
        val t = tracer()
        t.drag(2f, 0f)
        assertEquals(line(0 to 0, 1 to 0, 2 to 0), t.path)
        t.drag(1f, 0f)
        assertEquals(line(0 to 0, 1 to 0), t.path)
        t.drag(0.4f, 0f)
        assertEquals(line(0 to 0), t.path)
        assertEquals(0.4f, t.snapshot().tipX, 1e-4f)
    }

    @Test
    fun drag_stopsBeforeGaps() {
        val t = tracer(puzzle.copy(gaps = setOf(Pos(1, 0))))
        t.drag(1f, 0f)
        assertEquals(line(0 to 0), t.path)
        assertTrue(t.snapshot().tipX < 0.5f)
    }

    @Test
    fun drag_stopsBeforeRunningIntoItself() {
        val t = tracer()
        t.drag(1f, 0f)
        t.drag(1f, 1f)
        t.drag(0f, 1f)
        assertEquals(line(0 to 0, 1 to 0, 1 to 1, 0 to 1), t.path)
        t.drag(0f, 0f)
        assertEquals(line(0 to 0, 1 to 0, 1 to 1, 0 to 1), t.path)
        assertTrue(t.snapshot().tipY > 0.2f)
    }

    @Test
    fun drag_sidewaysNearANodeTurnsTheCorner() {
        val t = tracer()
        t.drag(0.7f, 0f)
        t.drag(0.8f, 0.5f)
        assertEquals(line(0 to 0, 1 to 0), t.path)
        assertEquals(1f, t.snapshot().tipX, 1e-4f)
        assertTrue(t.snapshot().tipY > 0.4f)
    }

    @Test
    fun begin_nearTipContinuesTheLine() {
        val t = tracer()
        t.drag(1f, 0f)
        assertTrue(t.begin(1.05f, 0.05f, 0.3f))
        assertEquals(line(0 to 0, 1 to 0), t.path)
        t.drag(1f, 1f)
        assertEquals(line(0 to 0, 1 to 0, 1 to 1), t.path)
    }
}
