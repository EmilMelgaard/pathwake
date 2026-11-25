package io.github.emilmelgaard.pathwake.ui.puzzle

import androidx.compose.ui.test.TouchInjectionScope
import io.github.emilmelgaard.pathwake.puzzle.Pos
import io.github.emilmelgaard.pathwake.puzzle.Puzzle

/** Draws [path] on a [PuzzlePanel] that fills the node, ending in the exit at its last node. */
internal fun TouchInjectionScope.drag(puzzle: Puzzle, path: List<Pos>) {
    val geometry = PuzzleGeometry(puzzle, width.toFloat(), height.toFloat())
    down(geometry.toPx(path.first()))
    for (i in 1 until path.size) {
        val from = geometry.toPx(path[i - 1])
        val to = geometry.toPx(path[i])
        for (step in 1..5) moveTo(from + (to - from) * (step / 5f))
    }
    val exit = puzzle.exitAt(path.last())!!
    val end = geometry.exitEnd(exit.node, exit.direction)
    moveTo(end + (end - geometry.toPx(exit.node)) * 0.5f)
    up()
}
