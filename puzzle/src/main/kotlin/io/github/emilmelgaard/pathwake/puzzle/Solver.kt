package io.github.emilmelgaard.pathwake.puzzle

import kotlin.random.Random

/**
 * @property solutions the solutions found, each a list of nodes from a start to an exit (for symmetry puzzles: the
 *   line from the canonical start of each mirrored start pair; the other line is its mirror image).
 * @property exhaustive true if the whole search space was explored, i.e. [solutions] contains every solution.
 * @property steps number of search steps used.
 */
data class SolveResult(val solutions: List<List<Pos>>, val exhaustive: Boolean, val steps: Long)

/**
 * Depth-first search over all lines. Prunes as soon as a region is closed off by the line, when a triangle cell gets
 * too many line edges, and when the remaining dots or every exit can no longer be reached.
 */
object Solver {
    const val DEFAULT_MAX_STEPS = 2_000_000L

    /**
     * @param random if set, neighbours are tried in a random order, which finds *some* solutions of large puzzles
     *   much faster than the fixed order (used by the generator to probe for alternative solutions).
     */
    fun solve(
        puzzle: Puzzle,
        maxSolutions: Int = 1,
        maxSteps: Long = DEFAULT_MAX_STEPS,
        random: Random? = null
    ): SolveResult = Search(Board(puzzle), maxSolutions, maxSteps, random).run()
}

private class Search(
    private val board: Board,
    private val maxSolutions: Int,
    private val maxSteps: Long,
    random: Random?
) {
    private val symmetric = board.symmetric
    private val mirror = board.mirror
    private val occupied = BooleanArray(board.size)
    private val secondary: BooleanArray? = if (symmetric) BooleanArray(board.size) else null
    private val path = IntArray(board.size)
    private var length = 0
    private var steps = 0L
    private var aborted = false
    private var stopped = false
    private val solutions = ArrayList<List<Pos>>()

    // Elimination marks can erase triangles and dots, so those shortcuts would wrongly prune.
    private val hasEliminators = board.puzzle.symbols.values.any { it is Elimination }

    private val regionOf = IntArray(board.size)
    private val reached = BooleanArray(board.size)
    private val queue = IntArray(board.size)

    private val links: Array<IntArray> = if (random == null) {
        board.links
    } else {
        Array(board.size) { i ->
            val pairs = board.links[i].toList().chunked(2).shuffled(random)
            pairs.flatten().toIntArray()
        }
    }

    // In symmetry puzzles each solution can be drawn from either start of a mirrored pair; search only one of them.
    private val starts: IntArray = board.starts
        .filter { !symmetric || it < mirror[it] }
        .let { if (random == null) it else it.shuffled(random) }
        .toIntArray()

    fun run(): SolveResult {
        for (start in starts) {
            path[0] = start
            length = 1
            occupy(start)
            dfs()
            release(start)
            length = 0
            if (aborted || stopped) break
        }
        return SolveResult(solutions, exhaustive = !aborted && !stopped, steps = steps)
    }

    private fun occupy(i: Int) {
        occupied[i] = true
        if (secondary != null) {
            occupied[mirror[i]] = true
            secondary[mirror[i]] = true
        }
    }

    private fun release(i: Int) {
        occupied[i] = false
        if (secondary != null) {
            occupied[mirror[i]] = false
            secondary[mirror[i]] = false
        }
    }

    private fun dfs() {
        if (++steps > maxSteps) {
            aborted = true
            return
        }
        val head = path[length - 1]
        if (length >= 2 && board.exitDirection[head] != null && board.check(occupied, secondary, null)) {
            solutions += List(length) { board.pos(path[it]) }
            if (solutions.size >= maxSolutions) {
                stopped = true
                return
            }
        }
        if (needsPruningCheck(head) && !promising(head)) return
        val links = links[head]
        var i = 0
        while (i < links.size) {
            val edge = links[i]
            val next = links[i + 1]
            i += 2
            if (occupied[next]) continue
            if (symmetric && (mirror[next] == next || occupied[mirror[next]] || board.gap[mirror[edge]])) continue
            occupy(edge)
            val overflows = board.overflowsTriangle(edge, occupied) ||
                (symmetric && board.overflowsTriangle(mirror[edge], occupied))
            if (hasEliminators || !overflows) {
                path[length++] = next
                occupy(next)
                dfs()
                release(next)
                length--
            }
            release(edge)
            if (aborted || stopped) return
        }
    }

    /** Regions can only close, and the head can only get trapped, when a line touches the border or a line. */
    private fun needsPruningCheck(head: Int): Boolean {
        if (touches(head, if (length >= 2) path[length - 2] else -1)) return true
        return symmetric && touches(mirror[head], if (length >= 2) mirror[path[length - 2]] else -1)
    }

    private fun touches(node: Int, previous: Int): Boolean {
        if (board.isBorderNode(node)) return true
        val links = board.links[node]
        var i = 1
        while (i < links.size) {
            if (links[i] != previous && occupied[links[i]]) return true
            i += 2
        }
        return false
    }

    private fun promising(head: Int): Boolean {
        val mirroredHead = mirror[head]
        if (board.isBorderNode(head) || board.isBorderNode(mirroredHead)) {
            val count = board.computeRegions(occupied, regionOf)
            // Regions that have a line's end as a corner can still be split further; all others are final.
            val open = openRegions(head) or (if (symmetric) openRegions(mirroredHead) else 0L)
            for (r in 0 until count) {
                if (r < 64 && open and (1L shl r) != 0L) continue
                if (!board.checkRegion(board.regionStart[r], board.regionStart[r + 1], occupied, null)) return false
            }
        }
        return canReachExitAndDots(head)
    }

    /** Bit set of the regions that have [node] as a corner. */
    private fun openRegions(node: Int): Long {
        val nx = board.x(node)
        val ny = board.y(node)
        val cols = board.cols
        var bits = 0L
        if (nx > 0 && ny > 0) bits = bits or bit(regionOf[node - cols - 1])
        if (nx < cols - 1 && ny > 0) bits = bits or bit(regionOf[node - cols + 1])
        if (nx > 0 && ny < board.rows - 1) bits = bits or bit(regionOf[node + cols - 1])
        if (nx < cols - 1 && ny < board.rows - 1) bits = bits or bit(regionOf[node + cols + 1])
        return bits
    }

    private fun bit(region: Int) = if (region in 0 until 64) 1L shl region else 0L

    private fun canReachExitAndDots(head: Int): Boolean {
        reached.fill(false)
        var qs = 0
        var qe = 0
        reached[head] = true
        queue[qe++] = head
        var exitReachable = false
        while (qs < qe) {
            val u = queue[qs++]
            val links = board.links[u]
            var i = 1
            while (i < links.size) {
                val n = links[i]
                if (!occupied[n] && !reached[n]) {
                    reached[n] = true
                    queue[qe++] = n
                    if (board.exitDirection[n] != null) exitReachable = true
                }
                i += 2
            }
        }
        if (!exitReachable) return false
        if (hasEliminators) return true
        for (d in board.dots) {
            if (occupied[d]) continue
            // In symmetry puzzles the mirrored line covers d if the drawn line can reach mirror(d).
            val ok = when (board.dotLine[d]) {
                0 -> reachable(d)
                1 -> reachable(mirror[d])
                else -> reachable(d) || (symmetric && reachable(mirror[d]))
            }
            if (!ok) return false
        }
        return true
    }

    private fun reachable(p: Int): Boolean {
        val px = board.x(p)
        val py = board.y(p)
        return when {
            px % 2 == 0 && py % 2 == 0 -> reached[p]
            px % 2 == 1 -> reached[p - 1] && reached[p + 1]
            else -> reached[p - board.cols] && reached[p + board.cols]
        }
    }
}
