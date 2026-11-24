package io.github.emilmelgaard.pathwake.puzzle

import kotlin.random.Random

/**
 * @property solutions the solutions found, each a list of nodes from a start to an exit.
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
    private val occupied = BooleanArray(board.size)
    private val path = IntArray(board.size)
    private var length = 0
    private var steps = 0L
    private var aborted = false
    private var stopped = false
    private val solutions = ArrayList<List<Pos>>()

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
    private val starts: IntArray = if (random ==
        null
    ) {
        board.starts
    } else {
        board.starts.toList().shuffled(random).toIntArray()
    }

    fun run(): SolveResult {
        for (start in starts) {
            push(start)
            dfs()
            pop()
            if (aborted || stopped) break
        }
        return SolveResult(solutions, exhaustive = !aborted && !stopped, steps = steps)
    }

    private fun push(node: Int) {
        path[length++] = node
        occupied[node] = true
    }

    private fun pop() {
        occupied[path[--length]] = false
    }

    private fun dfs() {
        if (++steps > maxSteps) {
            aborted = true
            return
        }
        val head = path[length - 1]
        if (length >= 2 && board.exitDirection[head] != null && board.check(occupied, null)) {
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
            occupied[edge] = true
            if (!board.overflowsTriangle(edge, occupied)) {
                push(next)
                dfs()
                pop()
            }
            occupied[edge] = false
            if (aborted || stopped) return
        }
    }

    /** Regions can only close, and the head can only get trapped, when it touches the border or the line. */
    private fun needsPruningCheck(head: Int): Boolean {
        if (board.isBorderNode(head)) return true
        val links = board.links[head]
        val previous = if (length >= 2) path[length - 2] else -1
        var i = 1
        while (i < links.size) {
            if (links[i] != previous && occupied[links[i]]) return true
            i += 2
        }
        return false
    }

    private fun promising(head: Int): Boolean {
        if (board.isBorderNode(head)) {
            val count = board.computeRegions(occupied, regionOf)
            // Regions that have the head as a corner can still be split further; all others are final.
            val hx = board.x(head)
            val hy = board.y(head)
            val cols = board.cols
            val open1 = if (hx > 0 && hy > 0) regionOf[head - cols - 1] else -1
            val open2 = if (hx < cols - 1 && hy > 0) regionOf[head - cols + 1] else -1
            val open3 = if (hx > 0 && hy < board.rows - 1) regionOf[head + cols - 1] else -1
            val open4 = if (hx < cols - 1 && hy < board.rows - 1) regionOf[head + cols + 1] else -1
            for (r in 0 until count) {
                if (r == open1 || r == open2 || r == open3 || r == open4) continue
                val from = board.regionStart[r]
                val to = board.regionStart[r + 1]
                if (!board.checkRegion(from, to, occupied, null) || board.hasUncoveredDot(from, to, occupied)) {
                    return false
                }
            }
        }
        return canReachExitAndDots(head)
    }

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
        for (d in board.dots) {
            if (occupied[d]) continue
            val dx = board.x(d)
            val dy = board.y(d)
            val ok = when {
                dx % 2 == 0 && dy % 2 == 0 -> reached[d]
                dx % 2 == 1 -> reached[d - 1] && reached[d + 1]
                else -> reached[d - board.cols] && reached[d + board.cols]
            }
            if (!ok) return false
        }
        return true
    }
}
