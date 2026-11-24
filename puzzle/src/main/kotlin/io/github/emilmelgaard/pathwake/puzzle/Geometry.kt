package io.github.emilmelgaard.pathwake.puzzle

/**
 * A position in the doubled puzzle grid. For a puzzle of `w x h` cells, x runs `0..2w` and y runs `0..2h`:
 * even/even positions are nodes (where the line can turn), odd/odd positions are cells (where symbols live),
 * and the remaining positions are the edges between two nodes.
 */
data class Pos(val x: Int, val y: Int) {
    val isNode: Boolean get() = x % 2 == 0 && y % 2 == 0
    val isCell: Boolean get() = x % 2 == 1 && y % 2 == 1
    val isEdge: Boolean get() = (x + y) % 2 == 1

    fun step(direction: Direction, distance: Int = 1) = Pos(x + direction.dx * distance, y + direction.dy * distance)

    override fun toString() = "($x,$y)"
}

enum class Direction(val dx: Int, val dy: Int) {
    UP(0, -1),
    DOWN(0, 1),
    LEFT(-1, 0),
    RIGHT(1, 0)
    ;

    val opposite: Direction
        get() = when (this) {
            UP -> DOWN
            DOWN -> UP
            LEFT -> RIGHT
            RIGHT -> LEFT
        }

    companion object {
        /** Direction from node [from] to the adjacent node [to], or null if they are not adjacent. */
        fun between(from: Pos, to: Pos): Direction? {
            val dx = to.x - from.x
            val dy = to.y - from.y
            return entries.firstOrNull { it.dx * 2 == dx && it.dy * 2 == dy }
        }
    }
}
