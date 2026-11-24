package io.github.emilmelgaard.pathwake.puzzle

/**
 * Index-based view of a [Puzzle], shared by [Validator], [Solver] and [Generator] so all of them apply exactly the
 * same rules. A position `Pos(x, y)` has index `y * cols + x`. An `occupied` array marks the nodes and edges the line
 * covers.
 *
 * Uses internal scratch buffers to avoid allocations in the solver's hot loop, so it is not thread-safe.
 */
internal class Board(val puzzle: Puzzle) {
    val cols = puzzle.cols
    val rows = puzzle.rows
    val size = cols * rows

    val gap = BooleanArray(size)
    val dot = BooleanArray(size)
    val symbol = arrayOfNulls<Symbol>(size)
    val isStart = BooleanArray(size)
    val exitDirection = arrayOfNulls<Direction>(size)

    /** Triangle count per cell (0 = no triangles). */
    private val triangles = IntArray(size)

    val cells: IntArray
    val dots: IntArray
    val starts: IntArray

    /** For each node: flattened `(edge, neighbour)` pairs for every adjacent node not separated by a gap. */
    val links: Array<IntArray>

    /** Cells of region r are `regionCells[regionStart[r] until regionStart[r + 1]]` after [computeRegions]. */
    val regionCells: IntArray
    val regionStart: IntArray

    private val regionStack: IntArray
    private val colorCounts = IntArray(SymbolColor.entries.size)
    private val scratchRegionOf = IntArray(size)
    private val aroundCell = intArrayOf(-1, 1, -cols, cols, -cols - 1, -cols + 1, cols - 1, cols + 1)

    init {
        puzzle.gaps.forEach { gap[index(it)] = true }
        puzzle.dots.forEach { dot[index(it)] = true }
        puzzle.symbols.forEach { (p, s) ->
            symbol[index(p)] = s
            if (s is Triangles) triangles[index(p)] = s.count
        }
        puzzle.starts.forEach { isStart[index(it)] = true }
        puzzle.exits.forEach { exitDirection[index(it.node)] = it.direction }
        cells = (0 until size).filter { x(it) % 2 == 1 && y(it) % 2 == 1 }.toIntArray()
        dots = puzzle.dots.map(::index).toIntArray()
        starts = puzzle.starts.map(::index).sorted().toIntArray()
        links = Array(size) { i ->
            if (x(i) % 2 != 0 || y(i) % 2 != 0) return@Array IntArray(0)
            val list = ArrayList<Int>(8)
            for (d in Direction.entries) {
                val nx = x(i) + 2 * d.dx
                val ny = y(i) + 2 * d.dy
                if (nx !in 0 until cols || ny !in 0 until rows) continue
                val edge = i + d.dx + d.dy * cols
                if (gap[edge]) continue
                list += edge
                list += i + 2 * (d.dx + d.dy * cols)
            }
            list.toIntArray()
        }
        regionCells = IntArray(cells.size)
        regionStart = IntArray(cells.size + 1)
        regionStack = IntArray(cells.size)
    }

    fun index(p: Pos) = p.y * cols + p.x

    fun pos(i: Int) = Pos(x(i), y(i))

    fun x(i: Int) = i % cols

    fun y(i: Int) = i / cols

    fun isBorderNode(i: Int) = x(i) == 0 || y(i) == 0 || x(i) == cols - 1 || y(i) == rows - 1

    /** Marks [path] in a fresh occupied array, or explains why it isn't a legal line. */
    fun trace(path: List<Pos>): Traced {
        if (path.size < 2) return Traced(null, "Path is too short")
        val occupied = BooleanArray(size)
        for ((i, p) in path.withIndex()) {
            if (p !in puzzle || !p.isNode) return Traced(null, "Not a node: $p")
            val idx = index(p)
            if (occupied[idx]) return Traced(null, "Path revisits $p")
            if (i == 0 && !isStart[idx]) return Traced(null, "Path must begin at a start, not $p")
            if (i > 0) {
                val prev = path[i - 1]
                val d = Direction.between(prev, p) ?: return Traced(null, "Not adjacent: $prev -> $p")
                val edge = index(prev.step(d))
                if (gap[edge]) return Traced(null, "Path crosses a gap at ${pos(edge)}")
                occupied[edge] = true
            }
            occupied[idx] = true
        }
        if (exitDirection[index(path.last())] == null) return Traced(null, "Path must end at an exit")
        return Traced(occupied, null)
    }

    /**
     * Checks all rules for a complete line. Returns true if solved. Violating positions are added to [violations]
     * when it is non-null; otherwise the check stops at the first violation.
     */
    fun check(occupied: BooleanArray, violations: MutableCollection<Int>?): Boolean {
        var ok = true
        for (d in dots) {
            if (!occupied[d]) {
                ok = false
                if (violations == null) return false
                violations += d
            }
        }
        val count = computeRegions(occupied, scratchRegionOf)
        for (r in 0 until count) {
            if (!checkRegion(regionStart[r], regionStart[r + 1], occupied, violations)) {
                ok = false
                if (violations == null) return false
            }
        }
        return ok
    }

    /**
     * Splits the cells into regions separated by the line, filling [regionOf] with each cell's region index and
     * [regionCells]/[regionStart] with the cells per region. Returns the number of regions.
     */
    fun computeRegions(occupied: BooleanArray, regionOf: IntArray): Int {
        for (c in cells) regionOf[c] = -1
        var count = 0
        var n = 0
        for (start in cells) {
            if (regionOf[start] >= 0) continue
            val id = count++
            regionStart[id] = n
            var sp = 0
            regionStack[sp++] = start
            regionOf[start] = id
            while (sp > 0) {
                val c = regionStack[--sp]
                regionCells[n++] = c
                val cx = x(c)
                val cy = y(c)
                if (cx > 1 && !occupied[c - 1] && regionOf[c - 2] < 0) {
                    regionOf[c - 2] = id
                    regionStack[sp++] = c - 2
                }
                if (cx < cols - 2 && !occupied[c + 1] && regionOf[c + 2] < 0) {
                    regionOf[c + 2] = id
                    regionStack[sp++] = c + 2
                }
                if (cy > 1 && !occupied[c - cols] && regionOf[c - 2 * cols] < 0) {
                    regionOf[c - 2 * cols] = id
                    regionStack[sp++] = c - 2 * cols
                }
                if (cy < rows - 2 && !occupied[c + cols] && regionOf[c + 2 * cols] < 0) {
                    regionOf[c + 2 * cols] = id
                    regionStack[sp++] = c + 2 * cols
                }
            }
        }
        regionStart[count] = n
        return count
    }

    /** Regions as lists of cell indices (allocating; for the generator). */
    fun regions(occupied: BooleanArray, regionOf: IntArray): List<IntArray> {
        val count = computeRegions(occupied, regionOf)
        return List(count) { r -> regionCells.copyOfRange(regionStart[r], regionStart[r + 1]) }
    }

    /** Number of the four edges around [cell] that the line uses. */
    fun edgesUsedAround(cell: Int, occupied: BooleanArray): Int {
        var n = 0
        if (occupied[cell - 1]) n++
        if (occupied[cell + 1]) n++
        if (occupied[cell - cols]) n++
        if (occupied[cell + cols]) n++
        return n
    }

    /** True if occupying [edge] gave a neighbouring triangle cell more used edges than its count allows. */
    fun overflowsTriangle(edge: Int, occupied: BooleanArray): Boolean {
        val ex = x(edge)
        val ey = y(edge)
        if (ex % 2 == 1) {
            if (ey > 0 && triangles[edge - cols] in 1 until edgesUsedAround(edge - cols, occupied)) return true
            if (ey < rows - 1 && triangles[edge + cols] in 1 until edgesUsedAround(edge + cols, occupied)) return true
        } else {
            if (ex > 0 && triangles[edge - 1] in 1 until edgesUsedAround(edge - 1, occupied)) return true
            if (ex < cols - 1 && triangles[edge + 1] in 1 until edgesUsedAround(edge + 1, occupied)) return true
        }
        return false
    }

    /**
     * Checks the symbol rules inside the region `regionCells[from until to]` (see [check] for how [violations] is
     * used).
     */
    fun checkRegion(from: Int, to: Int, occupied: BooleanArray, violations: MutableCollection<Int>?): Boolean {
        colorCounts.fill(0)
        var squareColor = -1
        var mixedSquares = false
        var hasStar = false
        var ok = true
        for (i in from until to) {
            val c = regionCells[i]
            val s = symbol[c] ?: continue
            colorCounts[s.color.ordinal]++
            when (s) {
                is Square -> {
                    if (squareColor == -1) {
                        squareColor = s.color.ordinal
                    } else if (squareColor != s.color.ordinal) {
                        mixedSquares = true
                    }
                }

                is Triangles -> {
                    if (edgesUsedAround(c, occupied) != s.count) {
                        ok = false
                        if (violations == null) return false
                        violations += c
                    }
                }

                is Star -> hasStar = true
            }
        }
        if (mixedSquares) {
            if (violations == null) return false
            ok = false
            for (i in from until to) if (symbol[regionCells[i]] is Square) violations += regionCells[i]
        }
        if (hasStar) {
            for (i in from until to) {
                val s = symbol[regionCells[i]]
                if (s is Star && colorCounts[s.color.ordinal] != 2) {
                    ok = false
                    if (violations == null) return false
                    violations += regionCells[i]
                }
            }
        }
        return ok
    }

    /** True if a dot on a corner or edge of a cell in `regionCells[from until to]` isn't covered by the line. */
    fun hasUncoveredDot(from: Int, to: Int, occupied: BooleanArray): Boolean {
        if (dots.isEmpty()) return false
        for (i in from until to) {
            val c = regionCells[i]
            for (o in aroundCell) {
                val p = c + o
                if (dot[p] && !occupied[p]) return true
            }
        }
        return false
    }

    class Traced(val occupied: BooleanArray?, val error: String?)
}
