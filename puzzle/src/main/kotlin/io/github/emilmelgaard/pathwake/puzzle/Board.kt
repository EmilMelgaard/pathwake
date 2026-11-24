package io.github.emilmelgaard.pathwake.puzzle

/**
 * Index-based view of a [Puzzle], shared by [Validator], [Solver] and [Generator] so all of them apply exactly the
 * same rules. A position `Pos(x, y)` has index `y * cols + x`. An `occupied` array marks the nodes and edges covered by
 * the line(s); for symmetry puzzles a `secondary` array marks those covered by the mirrored line.
 *
 * Uses internal scratch buffers to avoid allocations in the solver's hot loop, so it is not thread-safe.
 */
internal class Board(val puzzle: Puzzle) {
    val cols = puzzle.cols
    val rows = puzzle.rows
    val size = cols * rows
    val symmetric = puzzle.symmetry != Symmetry.NONE

    val gap = BooleanArray(size)
    val dot = BooleanArray(size)
    val symbol = arrayOfNulls<Symbol>(size)
    val isStart = BooleanArray(size)
    val exitDirection = arrayOfNulls<Direction>(size)

    /** Mirror image of each index (identity for non-symmetry puzzles). */
    val mirror = IntArray(size) { index(puzzle.mirror(pos(it))) }

    /** Line that must cover the dot at an index (-1: either line). */
    val dotLine = IntArray(size) { -1 }

    /** Triangle count per cell (0 = no triangles). */
    private val triangles = IntArray(size)

    val cells: IntArray
    val dots: IntArray
    val starts: IntArray
    private val coloredDots: IntArray

    /** For each node: flattened `(edge, neighbour)` pairs for every adjacent node not separated by a gap. */
    val links: Array<IntArray>

    /** Cells of region r are `regionCells[regionStart[r] until regionStart[r + 1]]` after [computeRegions]. */
    val regionCells: IntArray
    val regionStart: IntArray

    private val regionStack: IntArray
    private val colorCounts = IntArray(SymbolColor.entries.size)
    private val scratchRegionOf = IntArray(size)
    private val aroundCell = intArrayOf(-1, 1, -cols, cols, -cols - 1, -cols + 1, cols - 1, cols + 1)

    // Scratch state for region evaluation.
    private val items = IntArray(size)
    private val eliminators = IntArray(size)
    private val uncovered = IntArray(size)
    private val stamp = IntArray(size)
    private var stampValue = 0
    private val erased = BooleanArray(size)
    private val candidates = IntArray(size)

    // Scratch state for fitting tetris pieces, in cell coordinates (k = j * width + i).
    private val w = puzzle.width
    private val h = puzzle.height
    private val need = IntArray(w * h)
    private val orientations = arrayOfNulls<List<Polyomino>>(size)
    private val positives = IntArray(size)
    private val negatives = IntArray(size)
    private var positiveCount = 0
    private var negativeCount = 0
    private val used = BooleanArray(size)

    init {
        puzzle.gaps.forEach { gap[index(it)] = true }
        puzzle.dots.forEach { dot[index(it)] = true }
        puzzle.dotLines.forEach { (p, line) -> dotLine[index(p)] = line }
        puzzle.symbols.forEach { (p, s) ->
            val i = index(p)
            symbol[i] = s
            if (s is Triangles) triangles[i] = s.count
            if (s is Tetris) orientations[i] = if (s.rotatable) s.shape.rotations else listOf(s.shape)
        }
        puzzle.starts.forEach { isStart[index(it)] = true }
        puzzle.exits.forEach { exitDirection[index(it.node)] = it.direction }
        cells = (0 until size).filter { x(it) % 2 == 1 && y(it) % 2 == 1 }.toIntArray()
        dots = puzzle.dots.map(::index).toIntArray()
        coloredDots = puzzle.dotLines.keys.map(::index).toIntArray()
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

    /** Marks [path] (and its mirror image in symmetry puzzles), or explains why it isn't a legal line. */
    fun trace(path: List<Pos>): Traced {
        if (path.size < 2) return Traced.error("Path is too short")
        val occupied = BooleanArray(size)
        for ((i, p) in path.withIndex()) {
            if (p !in puzzle || !p.isNode) return Traced.error("Not a node: $p")
            val idx = index(p)
            if (occupied[idx]) return Traced.error("Path revisits $p")
            if (i == 0 && !isStart[idx]) return Traced.error("Path must begin at a start, not $p")
            if (i > 0) {
                val prev = path[i - 1]
                val d = Direction.between(prev, p) ?: return Traced.error("Not adjacent: $prev -> $p")
                val edge = index(prev.step(d))
                if (gap[edge]) return Traced.error("Path crosses a gap at ${pos(edge)}")
                occupied[edge] = true
            }
            occupied[idx] = true
        }
        if (exitDirection[index(path.last())] == null) return Traced.error("Path must end at an exit")
        if (!symmetric) return Traced(occupied, null, null)

        val secondary = BooleanArray(size)
        for ((i, p) in path.withIndex()) {
            val m = mirror[index(p)]
            if (occupied[m]) return Traced.error("The lines collide at ${pos(m)}")
            if (i > 0) {
                val edge = mirror[index(path[i - 1].step(Direction.between(path[i - 1], p)!!))]
                if (gap[edge]) return Traced.error("The mirrored line crosses a gap at ${pos(edge)}")
                if (occupied[edge]) return Traced.error("The lines collide at ${pos(edge)}")
                occupied[edge] = true
                secondary[edge] = true
            }
            occupied[m] = true
            secondary[m] = true
        }
        return Traced(occupied, secondary, null)
    }

    /**
     * Checks all rules for a complete line. Returns true if solved. Violating positions are added to [violations]
     * when it is non-null; otherwise the check stops at the first violation.
     */
    fun check(occupied: BooleanArray, secondary: BooleanArray?, violations: MutableCollection<Int>?): Boolean {
        var ok = true
        for (d in coloredDots) {
            // A dot covered by the wrong line is an error; uncovered dots are handled per region.
            if (occupied[d] && (secondary?.get(d) == true) != (dotLine[d] == 1)) {
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

    /**
     * True if occupying [edge] gave a neighbouring triangle cell more used edges than its count allows. Only valid as
     * a pruning shortcut when the puzzle has no elimination marks (which could erase the triangle).
     */
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
     * Checks the rules for the region `regionCells[from until to]`, including dots on its cells' corners and edges
     * and elimination marks (see [check] for how [violations] is used).
     */
    fun checkRegion(from: Int, to: Int, occupied: BooleanArray, violations: MutableCollection<Int>?): Boolean {
        stampValue++
        var itemCount = 0
        var eliminatorCount = 0
        var uncoveredCount = 0
        for (i in from until to) {
            val c = regionCells[i]
            when (symbol[c]) {
                null -> Unit
                is Elimination -> eliminators[eliminatorCount++] = c
                else -> items[itemCount++] = c
            }
            if (dots.isNotEmpty()) {
                for (o in aroundCell) {
                    val p = c + o
                    if (dot[p] && !occupied[p] && stamp[p] != stampValue) {
                        stamp[p] = stampValue
                        uncovered[uncoveredCount++] = p
                    }
                }
            }
        }
        if (eliminatorCount == 0) return evaluate(from, to, itemCount, uncoveredCount, occupied, violations)

        // Elimination marks only make sense if the region is broken without them.
        if (evaluate(from, to, itemCount, uncoveredCount, occupied, null)) {
            if (violations != null) for (e in 0 until eliminatorCount) violations += eliminators[e]
            return false
        }
        var candidateCount = 0
        for (i in 0 until itemCount) candidates[candidateCount++] = items[i]
        for (i in 0 until uncoveredCount) candidates[candidateCount++] = uncovered[i]
        if (candidateCount >= eliminatorCount &&
            eraseAndCheck(0, eliminatorCount, candidateCount, from, to, itemCount, uncoveredCount, occupied)
        ) {
            return true
        }
        if (violations != null) {
            evaluate(from, to, itemCount, uncoveredCount, occupied, violations)
            for (e in 0 until eliminatorCount) violations += eliminators[e]
        }
        return false
    }

    /** Tries every way of erasing [remaining] more candidates (from index [first] on). */
    private fun eraseAndCheck(
        first: Int,
        remaining: Int,
        candidateCount: Int,
        from: Int,
        to: Int,
        itemCount: Int,
        uncoveredCount: Int,
        occupied: BooleanArray
    ): Boolean {
        if (remaining == 0) return evaluate(from, to, itemCount, uncoveredCount, occupied, null)
        for (i in first..candidateCount - remaining) {
            val c = candidates[i]
            erased[c] = true
            val ok = eraseAndCheck(i + 1, remaining - 1, candidateCount, from, to, itemCount, uncoveredCount, occupied)
            erased[c] = false
            if (ok) return true
        }
        return false
    }

    /** Checks the region's non-erased symbols and dots, ignoring elimination marks. */
    private fun evaluate(
        from: Int,
        to: Int,
        itemCount: Int,
        uncoveredCount: Int,
        occupied: BooleanArray,
        violations: MutableCollection<Int>?
    ): Boolean {
        var ok = true
        for (i in 0 until uncoveredCount) {
            val d = uncovered[i]
            if (erased[d]) continue
            ok = false
            if (violations == null) return false
            violations += d
        }
        colorCounts.fill(0)
        var squareColor = -1
        var mixedSquares = false
        var hasStar = false
        positiveCount = 0
        negativeCount = 0
        for (i in 0 until itemCount) {
            val c = items[i]
            if (erased[c]) continue
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

                is Tetris -> if (s.negative) negatives[negativeCount++] = c else positives[positiveCount++] = c

                is Elimination -> Unit
            }
        }
        if (mixedSquares) {
            if (violations == null) return false
            ok = false
            for (i in 0 until itemCount) if (!erased[items[i]] && symbol[items[i]] is Square) violations += items[i]
        }
        if (hasStar) {
            for (i in 0 until itemCount) {
                val c = items[i]
                val s = symbol[c]
                if (!erased[c] && s is Star && colorCounts[s.color.ordinal] != 2) {
                    ok = false
                    if (violations == null) return false
                    violations += c
                }
            }
        }
        if ((positiveCount > 0 || negativeCount > 0) && !tetrisFits(from, to)) {
            if (violations == null) return false
            ok = false
            for (i in 0 until itemCount) if (!erased[items[i]] && symbol[items[i]] is Tetris) violations += items[i]
        }
        return ok
    }

    private fun tetrisFits(from: Int, to: Int): Boolean {
        var positiveArea = 0
        var negativeArea = 0
        for (i in 0 until positiveCount) positiveArea += (symbol[positives[i]] as Tetris).shape.size
        for (i in 0 until negativeCount) negativeArea += (symbol[negatives[i]] as Tetris).shape.size
        if (negativeCount > 0 && positiveArea == negativeArea) return true
        if (positiveArea - negativeArea != to - from) return false
        need.fill(0)
        for (i in from until to) {
            val c = regionCells[i]
            need[(y(c) / 2) * w + x(c) / 2] = 1
        }
        used.fill(false, 0, positiveCount)
        return placeNegatives(0)
    }

    /** Places negative pieces anywhere on the grid; each covered cell then needs one more positive block. */
    private fun placeNegatives(index: Int): Boolean {
        if (index == negativeCount) return coverWithPositives(positiveCount)
        for (shape in orientations[negatives[index]]!!) {
            for (oy in 0..h - shape.height) {
                for (ox in 0..w - shape.width) {
                    for (b in shape.blocks) need[(oy + b.y) * w + ox + b.x]++
                    if (placeNegatives(index + 1)) return true
                    for (b in shape.blocks) need[(oy + b.y) * w + ox + b.x]--
                }
            }
        }
        return false
    }

    /** Exact cover of [need] with the remaining positive pieces, always filling the first cell still needing one. */
    private fun coverWithPositives(remaining: Int): Boolean {
        var first = -1
        for (k in need.indices) {
            if (need[k] > 0) {
                first = k
                break
            }
        }
        if (first == -1) return remaining == 0
        if (remaining == 0) return false
        val fx = first % w
        val fy = first / w
        for (j in 0 until positiveCount) {
            if (used[j] || isDuplicateOfEarlierUnused(j)) continue
            for (shape in orientations[positives[j]]!!) {
                val anchor = shape.blocks[0]
                val ox = fx - anchor.x
                val oy = fy - anchor.y
                if (ox < 0 || oy < 0 || ox + shape.width > w || oy + shape.height > h) continue
                if (shape.blocks.any { need[(oy + it.y) * w + ox + it.x] <= 0 }) continue
                for (b in shape.blocks) need[(oy + b.y) * w + ox + b.x]--
                used[j] = true
                if (coverWithPositives(remaining - 1)) return true
                used[j] = false
                for (b in shape.blocks) need[(oy + b.y) * w + ox + b.x]++
            }
        }
        return false
    }

    private fun isDuplicateOfEarlierUnused(j: Int): Boolean {
        for (k in 0 until j) if (!used[k] && symbol[positives[k]] == symbol[positives[j]]) return true
        return false
    }

    class Traced(val occupied: BooleanArray?, val secondary: BooleanArray?, val error: String?) {
        companion object {
            fun error(message: String) = Traced(null, null, message)
        }
    }
}
