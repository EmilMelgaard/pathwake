package io.github.emilmelgaard.pathwake.puzzle

import kotlin.math.abs
import kotlin.math.sign

/** Logical colors of symbols. The UI maps them to concrete colors per theme. */
enum class SymbolColor { BLACK, WHITE, ORANGE, MAGENTA, GREEN, CYAN, YELLOW, BLUE }

/** A symbol placed in a cell. */
sealed interface Symbol {
    val color: SymbolColor
}

/** All squares in a region must have the same color. */
data class Square(override val color: SymbolColor) : Symbol

/**
 * A star must share its region with exactly one other symbol of the same color. Elimination marks don't count, but
 * tetris pieces (yellow, or blue when subtractive) and triangles (orange) do.
 */
data class Star(override val color: SymbolColor) : Symbol

/** The line must use exactly [count] of the four edges around the cell. */
data class Triangles(val count: Int, override val color: SymbolColor = SymbolColor.ORANGE) : Symbol {
    init {
        require(count in 1..3) { "Triangle count must be 1-3, was $count" }
    }
}

/**
 * A tetris piece. The region containing it must be exactly the union of all its pieces, placed without overlap;
 * [rotatable] pieces may be rotated. [negative] (subtractive) pieces cancel out cells of positive pieces, so positives
 * may overlap or stick out of the region where negatives cover them. If positive and negative areas are equal, the
 * pieces cancel completely and the region may have any shape.
 */
data class Tetris(val shape: Polyomino, val rotatable: Boolean = false, val negative: Boolean = false) : Symbol {
    override val color: SymbolColor get() = if (negative) SymbolColor.BLUE else SymbolColor.YELLOW
}

/**
 * Elimination mark: erases exactly one other symbol or uncovered dot in its region. The region must be broken
 * without the erasure; an elimination mark with nothing to fix is itself an error.
 */
data class Elimination(override val color: SymbolColor = SymbolColor.WHITE) : Symbol

/** Symmetry puzzles have two lines: the one you draw and its mirror image. */
enum class Symmetry {
    NONE,

    /** Mirrored left-right. */
    HORIZONTAL,

    /** Mirrored top-bottom. */
    VERTICAL,

    /** Rotated 180 degrees around the center. */
    ROTATIONAL
    ;

    /** Mirror image of [p] in a puzzle of [width] x [height] cells. */
    fun mirror(p: Pos, width: Int, height: Int): Pos = when (this) {
        NONE -> p
        HORIZONTAL -> Pos(2 * width - p.x, p.y)
        VERTICAL -> Pos(p.x, 2 * height - p.y)
        ROTATIONAL -> Pos(2 * width - p.x, 2 * height - p.y)
    }

    fun mirror(d: Direction): Direction = when (this) {
        NONE -> d
        HORIZONTAL -> if (d.dx != 0) d.opposite else d
        VERTICAL -> if (d.dy != 0) d.opposite else d
        ROTATIONAL -> d.opposite
    }
}

/** An exit: the line may end at [node] (on the border) by leaving the grid in [direction]. */
data class Exit(val node: Pos, val direction: Direction)

/**
 * A puzzle on a grid of [width] x [height] cells. One of them may be 0: then the grid is a single straight track.
 *
 * @property dotLines for symmetry puzzles: dots that must be covered by a specific line (0 = the drawn line, 1 = its
 *   mirror image). Other dots may be covered by either line.
 */
data class Puzzle(
    val width: Int,
    val height: Int,
    val starts: Set<Pos>,
    val exits: List<Exit>,
    val gaps: Set<Pos> = emptySet(),
    val dots: Set<Pos> = emptySet(),
    val symbols: Map<Pos, Symbol> = emptyMap(),
    val symmetry: Symmetry = Symmetry.NONE,
    val dotLines: Map<Pos, Int> = emptyMap()
) {
    val cols: Int get() = 2 * width + 1
    val rows: Int get() = 2 * height + 1

    init {
        require(width in 0..MAX_SIZE && height in 0..MAX_SIZE && width + height > 0) {
            "Size must be 0..$MAX_SIZE (not both 0), was ${width}x$height"
        }
        require(starts.isNotEmpty() && starts.all { it.isNode && contains(it) }) { "Invalid starts: $starts" }
        require(exits.isNotEmpty()) { "At least one exit is required" }
        require(exits.map { it.node }.toSet().size == exits.size) { "At most one exit per node" }
        exits.forEach { exit ->
            require(exit.node.isNode && contains(exit.node) && !contains(exit.node.step(exit.direction))) {
                "Exit $exit must be on the border and point outwards"
            }
        }
        require(gaps.all { it.isEdge && contains(it) }) { "Gaps must be edges: $gaps" }
        require(dots.all { !it.isCell && contains(it) }) { "Dots must be on nodes or edges: $dots" }
        require(symbols.keys.all { it.isCell && contains(it) }) { "Symbols must be in cells: ${symbols.keys}" }
        require(dots.none { it in gaps }) { "Dots can't be on gaps" }
        require(dotLines.keys.all { it in dots } && dotLines.values.all { it == 0 || it == 1 }) { "Invalid dotLines" }
        require(dotLines.isEmpty() || symmetry != Symmetry.NONE) { "Line-colored dots need a symmetry puzzle" }
        if (symmetry != Symmetry.NONE) {
            starts.forEach { require(mirror(it) in starts && mirror(it) != it) { "Starts must be symmetric: $it" } }
            exits.forEach { exit ->
                require(mirror(exit.node) != exit.node && Exit(mirror(exit.node), mirror(exit.direction)) in exits) {
                    "Exits must be symmetric: $exit"
                }
            }
        }
    }

    operator fun contains(p: Pos): Boolean = p.x in 0 until cols && p.y in 0 until rows

    fun exitAt(node: Pos): Exit? = exits.firstOrNull { it.node == node }

    /**
     * For a single straight track with one start and one exit: the line from the start to the exit, which may still
     * break rules (gaps, symbols). Null for other puzzles.
     */
    fun trackLine(): List<Pos>? {
        if (width != 0 && height != 0) return null
        val start = starts.singleOrNull() ?: return null
        val end = exits.singleOrNull()?.node ?: return null
        val dx = (end.x - start.x).sign
        val dy = (end.y - start.y).sign
        val steps = (abs(end.x - start.x) + abs(end.y - start.y)) / 2
        return (0..steps).map { Pos(start.x + 2 * dx * it, start.y + 2 * dy * it) }
    }

    /** The mirror image of [p] under [symmetry] (identity for non-symmetry puzzles). */
    fun mirror(p: Pos): Pos = symmetry.mirror(p, width, height)

    fun mirror(d: Direction): Direction = symmetry.mirror(d)

    /**
     * In symmetry puzzles the two lines are told apart by their starts: line 0 starts at the start that comes first
     * in reading order. Returns [path] if it is line 0, otherwise its mirror image (so line 0 can be validated no
     * matter which of the two lines was dragged).
     */
    fun asFirstLine(path: List<Pos>): List<Pos> {
        val start = path.firstOrNull() ?: return path
        if (symmetry == Symmetry.NONE || isFirstLineStart(start)) return path
        return path.map(::mirror)
    }

    /** Whether a line starting at [start] is line 0 (see [asFirstLine]). */
    fun isFirstLineStart(start: Pos): Boolean {
        val m = mirror(start)
        return start.y < m.y || (start.y == m.y && start.x < m.x)
    }

    companion object {
        const val MAX_SIZE = 8
    }
}

/**
 * A polyomino: connected cells given as `(x, y)` offsets, normalized so the smallest x and y are 0 and sorted by
 * row, then column.
 */
class Polyomino private constructor(val blocks: List<Pos>) {
    val size: Int get() = blocks.size
    val width: Int get() = blocks.maxOf { it.x } + 1
    val height: Int get() = blocks.maxOf { it.y } + 1

    /** Rotated 90 degrees clockwise (y axis pointing down). */
    fun rotated(): Polyomino = of(blocks.map { Pos(-it.y, it.x) })

    /** All distinct rotations, starting with this one. */
    val rotations: List<Polyomino> by lazy {
        generateSequence(this) { it.rotated() }.take(4).distinct().toList()
    }

    override fun equals(other: Any?) = other is Polyomino && other.blocks == blocks

    override fun hashCode() = blocks.hashCode()

    override fun toString() = "Polyomino$blocks"

    companion object {
        fun of(blocks: Collection<Pos>): Polyomino {
            require(blocks.isNotEmpty() && blocks.toSet().size == blocks.size) { "Invalid blocks: $blocks" }
            val minX = blocks.minOf { it.x }
            val minY = blocks.minOf { it.y }
            val normalized = blocks.map { Pos(it.x - minX, it.y - minY) }.sortedWith(compareBy({ it.y }, { it.x }))
            require(isConnected(normalized)) { "Polyomino must be connected: $blocks" }
            return Polyomino(normalized)
        }

        /** Parses rows like `"XX", ".X"` where `X` marks a block. */
        fun parse(vararg rows: String): Polyomino = of(
            rows.flatMapIndexed { y, row -> row.mapIndexedNotNull { x, c -> if (c == 'X') Pos(x, y) else null } }
        )

        private fun isConnected(blocks: List<Pos>): Boolean {
            val set = blocks.toSet()
            val seen = hashSetOf(blocks.first())
            val queue = ArrayDeque(listOf(blocks.first()))
            while (queue.isNotEmpty()) {
                val p = queue.removeFirst()
                for (d in Direction.entries) {
                    val n = Pos(p.x + d.dx, p.y + d.dy)
                    if (n in set && seen.add(n)) queue.addLast(n)
                }
            }
            return seen.size == set.size
        }
    }
}
