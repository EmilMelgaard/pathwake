package io.github.emilmelgaard.pathwake.puzzle

/** Logical colors of symbols. The UI maps them to concrete colors per theme. */
enum class SymbolColor { BLACK, WHITE, ORANGE, MAGENTA, GREEN, CYAN }

/** A symbol placed in a cell. */
sealed interface Symbol {
    val color: SymbolColor
}

/** All squares in a region must have the same color. */
data class Square(override val color: SymbolColor) : Symbol

/** A star must share its region with exactly one other symbol of the same color. */
data class Star(override val color: SymbolColor) : Symbol

/** The line must use exactly [count] of the four edges around the cell. */
data class Triangles(val count: Int, override val color: SymbolColor = SymbolColor.ORANGE) : Symbol {
    init {
        require(count in 1..3) { "Triangle count must be 1-3, was $count" }
    }
}

/** An exit: the line may end at [node] (on the border) by leaving the grid in [direction]. */
data class Exit(val node: Pos, val direction: Direction)

data class Puzzle(
    val width: Int,
    val height: Int,
    val starts: Set<Pos>,
    val exits: List<Exit>,
    val gaps: Set<Pos> = emptySet(),
    val dots: Set<Pos> = emptySet(),
    val symbols: Map<Pos, Symbol> = emptyMap()
) {
    val cols: Int get() = 2 * width + 1
    val rows: Int get() = 2 * height + 1

    init {
        require(width in 1..MAX_SIZE && height in 1..MAX_SIZE) { "Size must be 1..$MAX_SIZE, was ${width}x$height" }
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
    }

    operator fun contains(p: Pos): Boolean = p.x in 0 until cols && p.y in 0 until rows

    fun exitAt(node: Pos): Exit? = exits.firstOrNull { it.node == node }

    companion object {
        const val MAX_SIZE = 8
    }
}
