package io.github.emilmelgaard.pathwake.puzzle

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/** Immutable snapshot of a [PathTracer] for drawing. Coordinates are in node units. */
data class TraceSnapshot(val nodes: List<Pos>, val tipX: Float, val tipY: Float, val atExit: Boolean) {
    companion object {
        val EMPTY = TraceSnapshot(emptyList(), 0f, 0f, false)
    }
}

/**
 * Turns pointer movement into a line on the grid: the line follows the finger along edges, backs up when the finger
 * goes back, stops in front of gaps and of itself, turns corners when the finger moves sideways, and can be pushed
 * into an exit.
 *
 * Coordinates are in node units: node `Pos(2i, 2j)` is at `(i, j)`, so adjacent nodes are 1 unit apart.
 */
class PathTracer(private val puzzle: Puzzle, private val config: Config = Config()) {
    /** Sizes in node units. */
    data class Config(
        val lineWidth: Float = 0.24f,
        val gapWidth: Float = 0.3f,
        val exitLength: Float = 0.3f,
        val snapDistance: Float = 0.3f,
        val deadZone: Float = 0.05f
    )

    private val nodes = ArrayList<Pos>()
    private val visited = HashSet<Pos>()
    private var direction: Direction? = null
    private var intoExit = false
    private var progress = 0f

    val path: List<Pos> get() = nodes
    val isActive: Boolean get() = nodes.isNotEmpty()
    val isAtExit: Boolean get() = intoExit && progress >= 1f

    fun snapshot(): TraceSnapshot {
        if (nodes.isEmpty()) return TraceSnapshot.EMPTY
        val (x, y) = tip()
        return TraceSnapshot(nodes.toList(), x, y, isAtExit)
    }

    fun clear() {
        nodes.clear()
        visited.clear()
        direction = null
        intoExit = false
        progress = 0f
    }

    /**
     * Starts drawing at ([x], [y]). Continues the current line if the touch is near its tip, otherwise starts a new
     * line at the nearest start within [touchRadius]. Returns false if the touch should be ignored.
     */
    fun begin(x: Float, y: Float, touchRadius: Float): Boolean {
        if (nodes.isNotEmpty() && !isAtExit) {
            val (tx, ty) = tip()
            if (hypot(x - tx, y - ty) <= touchRadius) return true
        }
        val start = puzzle.starts.minByOrNull { hypot(x - it.x / 2f, y - it.y / 2f) } ?: return false
        if (hypot(x - start.x / 2f, y - start.y / 2f) > touchRadius) return false
        clear()
        nodes += start
        visited += start
        return true
    }

    /** Moves the line towards the finger at ([x], [y]). */
    fun drag(x: Float, y: Float) {
        if (nodes.isEmpty()) return
        repeat(nodes.size * 4 + 64) {
            if (!step(x, y)) return
        }
    }

    /** One movement step; returns true if another step is needed to catch up with the finger. */
    private fun step(x: Float, y: Float): Boolean {
        val head = nodes[nodes.lastIndex]
        val dx = x - head.x / 2f
        val dy = y - head.y / 2f
        val dir = direction
        if (dir == null) {
            val best = bestDirection(head, dx, dy) ?: return false
            val previous = nodes.getOrNull(nodes.size - 2)
            if (previous != null && head.step(best, 2) == previous) {
                // Back up: the line now ends somewhere on the edge between previous and head.
                nodes.removeAt(nodes.lastIndex)
                visited -= head
                direction = best.opposite
                intoExit = false
                progress = 1f - (dx * best.dx + dy * best.dy)
                if (progress > 0f) return false
                direction = null
                progress = 0f
                return true
            }
            direction = best
            intoExit = isExit(head, best)
            progress = 0f
            return true
        }

        val length = if (intoExit) config.exitLength else 1f
        val along = (dx * dir.dx + dy * dir.dy) / length
        val across = dx * dir.dy - dy * dir.dx
        if (along <= 0f) {
            direction = null
            intoExit = false
            progress = 0f
            return true
        }
        val limit = limit(head, dir)
        if (!intoExit && abs(across) > config.snapDistance) {
            val sideways = perpendicular(dir, across)
            if (along < 0.5f && abs(across) > along && canMove(head, sideways)) {
                direction = sideways
                intoExit = isExit(head, sideways)
                progress = 0f
                return true
            }
            if (along >= 0.5f && limit >= 1f && abs(across) > 1f - along && canMove(head.step(dir, 2), sideways)) {
                commit(head.step(dir, 2))
                return true
            }
        }
        if (along >= 1f && limit >= 1f) {
            if (intoExit) {
                progress = 1f
                return false
            }
            commit(head.step(dir, 2))
            return true
        }
        progress = min(along, limit)
        return false
    }

    private fun commit(node: Pos) {
        nodes += node
        visited += node
        direction = null
        intoExit = false
        progress = 0f
    }

    private fun tip(): Pair<Float, Float> {
        val head = nodes[nodes.lastIndex]
        val dir = direction ?: return head.x / 2f to head.y / 2f
        val length = progress * if (intoExit) config.exitLength else 1f
        return head.x / 2f + dir.dx * length to head.y / 2f + dir.dy * length
    }

    private fun isExit(node: Pos, dir: Direction) = puzzle.exitAt(node)?.direction == dir

    private fun bestDirection(head: Pos, dx: Float, dy: Float): Direction? = Direction.entries
        .filter { isExit(head, it) || head.step(it, 2) in puzzle }
        .map { it to dx * it.dx + dy * it.dy }
        .filter { it.second > config.deadZone }
        .maxByOrNull { it.second }
        ?.first

    /** How far (0..1) the line may go from [node] in [dir] before hitting a gap or itself. */
    private fun limit(node: Pos, dir: Direction): Float {
        if (isExit(node, dir)) return 1f
        if (node.step(dir) in puzzle.gaps) return 0.5f - config.gapWidth / 2 - config.lineWidth / 2
        if (node.step(dir, 2) in visited) return 1f - config.lineWidth
        return 1f
    }

    private fun canMove(node: Pos, dir: Direction): Boolean {
        if (isExit(node, dir)) return true
        val next = node.step(dir, 2)
        return next in puzzle && node.step(dir) !in puzzle.gaps && next !in visited
    }

    private fun perpendicular(dir: Direction, across: Float): Direction = when (dir) {
        Direction.UP, Direction.DOWN -> if (across * dir.dy > 0) Direction.RIGHT else Direction.LEFT
        Direction.LEFT, Direction.RIGHT -> if (across * dir.dx < 0) Direction.DOWN else Direction.UP
    }
}
