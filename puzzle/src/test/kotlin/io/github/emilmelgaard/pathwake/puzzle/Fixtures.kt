package io.github.emilmelgaard.pathwake.puzzle

/** Node at node coordinates ([i], [j]). */
fun node(i: Int, j: Int) = Pos(2 * i, 2 * j)

/** Cell at cell coordinates ([i], [j]). */
fun cell(i: Int, j: Int) = Pos(2 * i + 1, 2 * j + 1)

/** A line through the given node coordinates. */
fun line(vararg nodes: Pair<Int, Int>) = nodes.map { (i, j) -> node(i, j) }

/**
 * 2x2 cells. Start bottom-left, exit top-right (pointing right).
 *
 * The "staircase" line `(0,2) (1,2) (1,1) (2,1) (2,0)` cuts off the bottom-right cell; the line along the left and
 * top border leaves a single region.
 */
fun twoByTwo(gaps: Set<Pos> = emptySet(), dots: Set<Pos> = emptySet(), symbols: Map<Pos, Symbol> = emptyMap()) = Puzzle(
    width = 2,
    height = 2,
    starts = setOf(node(0, 2)),
    exits = listOf(Exit(node(2, 0), Direction.RIGHT)),
    gaps = gaps,
    dots = dots,
    symbols = symbols
)

val STAIRCASE = line(0 to 2, 1 to 2, 1 to 1, 2 to 1, 2 to 0)
val ALONG_BORDER = line(0 to 2, 0 to 1, 0 to 0, 1 to 0, 2 to 0)
