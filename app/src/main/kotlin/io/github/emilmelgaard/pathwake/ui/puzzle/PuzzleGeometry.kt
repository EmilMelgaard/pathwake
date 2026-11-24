package io.github.emilmelgaard.pathwake.ui.puzzle

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import io.github.emilmelgaard.pathwake.puzzle.Direction
import io.github.emilmelgaard.pathwake.puzzle.PathTracer
import io.github.emilmelgaard.pathwake.puzzle.Pos
import io.github.emilmelgaard.pathwake.puzzle.Puzzle
import kotlin.math.min

/**
 * Maps between pixels and puzzle node units (adjacent nodes are 1 unit apart) for a puzzle centered in a
 * [width] x [height] pixel area.
 */
internal class PuzzleGeometry(puzzle: Puzzle, width: Float, height: Float) {
    /** Pixels per node unit. */
    val unit: Float = min(width / (puzzle.width + 2 * MARGIN), height / (puzzle.height + 2 * MARGIN))
    val panelSize = Size(unit * (puzzle.width + 2 * MARGIN), unit * (puzzle.height + 2 * MARGIN))
    val panelTopLeft = Offset((width - panelSize.width) / 2, (height - panelSize.height) / 2)
    private val originX = panelTopLeft.x + MARGIN * unit
    private val originY = panelTopLeft.y + MARGIN * unit

    fun toPx(x: Float, y: Float) = Offset(originX + x * unit, originY + y * unit)

    fun toPx(p: Pos) = toPx(p.x / 2f, p.y / 2f)

    /** End of an exit stub leaving [node] in [direction]. */
    fun exitEnd(node: Pos, direction: Direction) =
        toPx(node.x / 2f + direction.dx * EXIT_LENGTH, node.y / 2f + direction.dy * EXIT_LENGTH)

    fun toNodeUnits(px: Offset) = Offset((px.x - originX) / unit, (px.y - originY) / unit)

    companion object {
        /** Panel padding around the grid, in node units. */
        const val MARGIN = 0.75f
        const val LINE_WIDTH = 0.24f
        const val START_RADIUS = 0.28f
        const val EXIT_LENGTH = 0.3f
        const val GAP_WIDTH = 0.3f

        val tracerConfig = PathTracer.Config(lineWidth = LINE_WIDTH, gapWidth = GAP_WIDTH, exitLength = EXIT_LENGTH)
    }
}
