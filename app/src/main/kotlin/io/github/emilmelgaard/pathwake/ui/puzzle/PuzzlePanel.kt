package io.github.emilmelgaard.pathwake.ui.puzzle

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.emilmelgaard.pathwake.R
import io.github.emilmelgaard.pathwake.puzzle.Elimination
import io.github.emilmelgaard.pathwake.puzzle.PathTracer
import io.github.emilmelgaard.pathwake.puzzle.Pos
import io.github.emilmelgaard.pathwake.puzzle.Puzzle
import io.github.emilmelgaard.pathwake.puzzle.Square
import io.github.emilmelgaard.pathwake.puzzle.Star
import io.github.emilmelgaard.pathwake.puzzle.SymbolColor
import io.github.emilmelgaard.pathwake.puzzle.Symmetry
import io.github.emilmelgaard.pathwake.puzzle.Tetris
import io.github.emilmelgaard.pathwake.puzzle.TraceSnapshot
import io.github.emilmelgaard.pathwake.puzzle.Triangles
import io.github.emilmelgaard.pathwake.puzzle.Validator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay

private sealed interface Feedback {
    val path: List<Pos>

    data class Solved(override val path: List<Pos>) : Feedback

    data class Failed(override val path: List<Pos>, val violations: Set<Pos>) : Feedback
}

/**
 * An interactive puzzle. Press the start circle and drag the line to an exit; releasing at the exit checks the line.
 * Broken rules blink and the line fades; a correct line stays lit and [onSolved] is called with it.
 *
 * @param colorMarks marks colored squares and stars with a shape per color, for color-blind players.
 * @param solutionHint if set, drawn faintly behind the line (debug/"show solution").
 */
@Composable
fun PuzzlePanel(
    puzzle: Puzzle,
    onSolved: (List<Pos>) -> Unit,
    modifier: Modifier = Modifier,
    colorMarks: Boolean = false,
    solutionHint: List<Pos>? = null,
    enabled: Boolean = true
) {
    val colors = PuzzleColors.Panel
    val tracer = remember(puzzle) { PathTracer(puzzle, PuzzleGeometry.tracerConfig) }
    var trace by remember(puzzle) { mutableStateOf(TraceSnapshot.EMPTY) }
    var feedback by remember(puzzle) { mutableStateOf<Feedback?>(null) }
    val blink = remember { Animatable(0f) }
    val haptics = LocalHapticFeedback.current
    val currentOnSolved by rememberUpdatedState(onSolved)

    LaunchedEffect(feedback) {
        if (feedback is Feedback.Failed) {
            repeat(3) {
                blink.animateTo(1f, tween(140))
                blink.animateTo(0f, tween(140))
            }
            delay(500)
            feedback = null
        }
    }

    // A straight track can be drawn without seeing it, e.g. with a screen reader.
    val trackLine = remember(puzzle) { puzzle.trackLine()?.takeIf { Validator.validate(puzzle, it).isValid } }
    val description = if (trackLine != null) {
        stringResource(R.string.puzzle_description_track)
    } else {
        stringResource(R.string.puzzle_description, puzzle.width, puzzle.height)
    }
    val drawLine = stringResource(R.string.draw_line)
    Canvas(
        modifier
            .systemGestureExclusion()
            .semantics {
                contentDescription = description
                if (enabled && trackLine != null) {
                    customActions = listOf(
                        CustomAccessibilityAction(drawLine) {
                            feedback = Feedback.Solved(trackLine)
                            currentOnSolved(trackLine)
                            true
                        }
                    )
                }
            }
            .pointerInput(puzzle, enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val geometry = PuzzleGeometry(puzzle, size.width.toFloat(), size.height.toFloat())
                    val at = geometry.toNodeUnits(down.position)
                    val touchRadius = max(0.45f, 32.dp.toPx() / geometry.unit)
                    if (!tracer.begin(at.x, at.y, touchRadius)) {
                        if (tracer.isActive) {
                            tracer.clear()
                            trace = tracer.snapshot()
                        }
                        return@awaitEachGesture
                    }
                    feedback = null
                    down.consume()
                    trace = tracer.snapshot()
                    haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    var nodeCount = tracer.path.size
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val p = geometry.toNodeUnits(change.position)
                        tracer.drag(p.x, p.y)
                        change.consume()
                        if (tracer.path.size != nodeCount) {
                            nodeCount = tracer.path.size
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                        }
                        trace = tracer.snapshot()
                    }
                    if (tracer.isAtExit) {
                        val path = tracer.path.toList()
                        val result = Validator.validate(puzzle, path)
                        tracer.clear()
                        trace = tracer.snapshot()
                        if (result.isValid) {
                            feedback = Feedback.Solved(path)
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            currentOnSolved(path)
                        } else {
                            feedback = Feedback.Failed(path, result.violations)
                            haptics.performHapticFeedback(HapticFeedbackType.Reject)
                        }
                    }
                }
            }
    ) {
        val geometry = PuzzleGeometry(puzzle, size.width, size.height)
        drawPuzzle(geometry, puzzle, colors, colorMarks, trace, feedback, blink.value, solutionHint)
    }
}

private fun DrawScope.drawPuzzle(
    g: PuzzleGeometry,
    puzzle: Puzzle,
    colors: PuzzleColors,
    colorMarks: Boolean,
    trace: TraceSnapshot,
    feedback: Feedback?,
    blink: Float,
    solutionHint: List<Pos>?
) {
    val u = g.unit
    val lineWidth = PuzzleGeometry.LINE_WIDTH * u
    drawRoundRect(colors.panel, g.panelTopLeft, g.panelSize, CornerRadius(0.3f * u))

    // Track: edges (split around gaps), round joints at nodes, exits and starts.
    for (y in 0 until puzzle.rows) {
        for (x in 0 until puzzle.cols) {
            val p = Pos(x, y)
            when {
                p.isNode -> drawCircle(colors.track, lineWidth / 2, g.toPx(p))
                p.isEdge -> drawEdge(g, p, p in puzzle.gaps, colors.track, lineWidth)
            }
        }
    }
    for (exit in puzzle.exits) {
        drawLine(colors.track, g.toPx(exit.node), g.exitEnd(exit.node, exit.direction), lineWidth, StrokeCap.Round)
    }
    for (start in puzzle.starts) drawCircle(colors.track, PuzzleGeometry.START_RADIUS * u, g.toPx(start))

    val violations = (feedback as? Feedback.Failed)?.violations.orEmpty()
    for ((cell, symbol) in puzzle.symbols) {
        val base = colors.symbol(symbol.color)
        val color = if (cell in violations) lerp(base, colors.error, blink) else base
        val center = g.toPx(cell)
        when (symbol) {
            is Square -> {
                drawSquare(center, u, color)
                if (colorMarks) drawColorMark(center, u, symbol.color)
            }

            is Star -> {
                drawStar(center, u, color)
                if (colorMarks) drawColorMark(center, u, symbol.color)
            }

            is Triangles -> drawTriangles(center, u, symbol.count, color)

            is Tetris -> drawTetris(center, u, symbol, color)

            is Elimination -> drawElimination(center, u, color)
        }
    }

    // Dots lie under the line, which hides them.
    for (dot in puzzle.dots) drawDot(g, puzzle, colors, dot, dotColor(puzzle, colors, dot))

    val symmetric = puzzle.symmetry != Symmetry.NONE
    solutionHint?.let { hint ->
        for (line in if (symmetric) listOf(hint, hint.map(puzzle::mirror)) else listOf(hint)) {
            drawPath(
                linePath(g, line, exitTip(g, puzzle, line)),
                colors.line.copy(alpha = 0.35f),
                style = lineStroke(lineWidth * 0.4f)
            )
        }
    }

    // The line: the one being drawn, or the last submitted one while showing feedback.
    val lineNodes: List<Pos>
    val tip: Offset?
    when {
        trace.nodes.isNotEmpty() -> {
            lineNodes = trace.nodes
            tip = Offset(trace.tipX, trace.tipY)
        }

        feedback != null -> {
            lineNodes = feedback.path
            tip = exitTipUnits(puzzle, feedback.path)
        }

        else -> {
            lineNodes = emptyList()
            tip = null
        }
    }
    val lines = mutableListOf<Pair<List<Pos>, Offset?>>()
    if (lineNodes.isNotEmpty()) {
        lines += lineNodes to tip
        if (symmetric) lines += lineNodes.map(puzzle::mirror) to tip?.let { mirrorUnits(puzzle, it) }
    }
    for ((nodes, end) in lines) {
        val color = when {
            trace.nodes.isEmpty() && feedback is Feedback.Failed -> lerp(colors.line, colors.error, 0.6f + 0.4f * blink)
            trace.nodes.isEmpty() && feedback is Feedback.Solved && !symmetric -> colors.solvedLine
            symmetric -> colors.symmetryLine(if (puzzle.isFirstLineStart(nodes.first())) 0 else 1)
            else -> colors.line
        }
        val path = linePath(g, nodes, end?.let { g.toPx(it.x, it.y) })
        val start = g.toPx(nodes.first())
        val glowColor = if (trace.nodes.isEmpty() && feedback is Feedback.Failed) color else colors.lineGlow
        drawGlow(path, start, glowColor.copy(alpha = if (feedback is Feedback.Solved) 0.9f else 0.6f), lineWidth, u)
        drawPath(path, color, style = lineStroke(lineWidth))
        drawCircle(color, PuzzleGeometry.START_RADIUS * u, start)
    }

    // Broken dots blink on top of the line, so a dot covered by the wrong line (symmetry) is visible too.
    for (dot in puzzle.dots) {
        if (dot in violations) drawDot(g, puzzle, colors, dot, lerp(dotColor(puzzle, colors, dot), colors.error, blink))
    }
}

/** Dots that belong to one line of a symmetry puzzle take that line's color. */
private fun dotColor(puzzle: Puzzle, colors: PuzzleColors, dot: Pos): Color =
    puzzle.dotLines[dot]?.let(colors::symmetryLine) ?: colors.dot

private fun DrawScope.drawDot(g: PuzzleGeometry, puzzle: Puzzle, colors: PuzzleColors, dot: Pos, color: Color) =
    drawPath(hexagonPath(g.toPx(dot), 0.11f * g.unit), color)

/** Mirror image of a point in node units. */
private fun mirrorUnits(puzzle: Puzzle, p: Offset): Offset = when (puzzle.symmetry) {
    Symmetry.NONE -> p
    Symmetry.HORIZONTAL -> Offset(puzzle.width - p.x, p.y)
    Symmetry.VERTICAL -> Offset(p.x, puzzle.height - p.y)
    Symmetry.ROTATIONAL -> Offset(puzzle.width - p.x, puzzle.height - p.y)
}

/** End of the exit stub at the end of [path], in node units. */
private fun exitTipUnits(puzzle: Puzzle, path: List<Pos>): Offset? {
    val last = path.lastOrNull() ?: return null
    val exit = puzzle.exitAt(last) ?: return null
    return Offset(
        last.x / 2f + exit.direction.dx * PuzzleGeometry.EXIT_LENGTH,
        last.y / 2f + exit.direction.dy * PuzzleGeometry.EXIT_LENGTH
    )
}
private fun lineStroke(width: Float) = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)

/** Soft blurred halo behind the line. */
private fun DrawScope.drawGlow(path: Path, start: Offset, color: Color, lineWidth: Float, u: Float) {
    drawIntoCanvas { canvas ->
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            this.color = color.toArgb()
            maskFilter = BlurMaskFilter(lineWidth * 0.8f, BlurMaskFilter.Blur.NORMAL)
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = lineWidth * 1.5f
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
        }
        canvas.nativeCanvas.drawPath(path.asAndroidPath(), paint)
        paint.style = android.graphics.Paint.Style.FILL
        canvas.nativeCanvas.drawCircle(start.x, start.y, PuzzleGeometry.START_RADIUS * u * 1.2f, paint)
    }
}

private fun DrawScope.drawEdge(g: PuzzleGeometry, edge: Pos, isGap: Boolean, color: Color, width: Float) {
    val horizontal = edge.x % 2 == 1
    val a = if (horizontal) Pos(edge.x - 1, edge.y) else Pos(edge.x, edge.y - 1)
    val b = if (horizontal) Pos(edge.x + 1, edge.y) else Pos(edge.x, edge.y + 1)
    val pa = g.toPx(a)
    val pb = g.toPx(b)
    if (!isGap) {
        drawLine(color, pa, pb, width)
        return
    }
    val part = 0.5f - PuzzleGeometry.GAP_WIDTH / 2
    drawLine(color, pa, pa + (pb - pa) * part, width)
    drawLine(color, pb, pb + (pa - pb) * part, width)
}

private fun exitTip(g: PuzzleGeometry, puzzle: Puzzle, path: List<Pos>): Offset? {
    val last = path.lastOrNull() ?: return null
    val exit = puzzle.exitAt(last) ?: return null
    return g.exitEnd(exit.node, exit.direction)
}

private fun linePath(g: PuzzleGeometry, nodes: List<Pos>, tip: Offset?) = Path().apply {
    val first = g.toPx(nodes.first())
    moveTo(first.x, first.y)
    for (i in 1 until nodes.size) {
        val p = g.toPx(nodes[i])
        lineTo(p.x, p.y)
    }
    tip?.let { lineTo(it.x, it.y) }
}

private fun DrawScope.drawSquare(center: Offset, u: Float, color: Color) {
    val side = 0.34f * u
    val topLeft = center - Offset(side / 2, side / 2)
    val radius = CornerRadius(0.09f * u)
    drawRoundRect(color, topLeft, Size(side, side), radius)
}

private fun DrawScope.drawStar(center: Offset, u: Float, color: Color) {
    // Outline of two overlapping squares, one rotated by 45 degrees.
    val outer = 0.23f * u
    val inner = outer * 0.7654f
    val path = Path()
    for (i in 0 until 16) {
        val angle = PI / 8 * i - PI / 2
        val r = if (i % 2 == 0) outer else inner
        val x = center.x + (r * cos(angle)).toFloat()
        val y = center.y + (r * sin(angle)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}

private fun DrawScope.drawTriangles(center: Offset, u: Float, count: Int, color: Color) {
    val side = 0.17f * u
    val height = side * 0.866f
    val spacing = 0.21f * u
    for (k in 0 until count) {
        val cx = center.x + (k - (count - 1) / 2f) * spacing
        val path = Path().apply {
            moveTo(cx, center.y - height * 2 / 3)
            lineTo(cx + side / 2, center.y + height / 3)
            lineTo(cx - side / 2, center.y + height / 3)
            close()
        }
        drawPath(path, color)
    }
}

/**
 * Tetris piece as small blocks; subtractive (negative) ones are hollow. Rotatable pieces are tilted, unless rotating
 * doesn't change them (single blocks, squares).
 */
private fun DrawScope.drawTetris(center: Offset, u: Float, tetris: Tetris, color: Color) {
    val shape = tetris.shape
    val cellSize = min(0.17f * u, 0.6f * u / max(shape.width, shape.height))
    val gap = cellSize * 0.2f
    val left = center.x - shape.width * cellSize / 2
    val top = center.y - shape.height * cellSize / 2
    val draw: DrawScope.() -> Unit = {
        for (b in shape.blocks) {
            val topLeft = Offset(left + b.x * cellSize + gap / 2, top + b.y * cellSize + gap / 2)
            val blockSize = Size(cellSize - gap, cellSize - gap)
            if (tetris.negative) {
                val stroke = cellSize * 0.2f
                drawRect(
                    color,
                    topLeft + Offset(stroke / 2, stroke / 2),
                    Size(blockSize.width - stroke, blockSize.height - stroke),
                    style = Stroke(stroke)
                )
            } else {
                drawRect(color, topLeft, blockSize)
            }
        }
    }
    if (tetris.rotatable && tetris.shape.rotations.size > 1) rotate(15f, center, draw) else draw()
}

/**
 * A dark mark inside a colored square or star that tells its color without color vision. Black and white stand apart
 * by brightness and get none.
 */
private fun DrawScope.drawColorMark(center: Offset, u: Float, color: SymbolColor) {
    val mark = Color(0xFF101820)
    val half = 0.075f * u
    val stroke = 0.045f * u
    fun line(dx1: Float, dy1: Float, dx2: Float, dy2: Float) =
        drawLine(mark, center + Offset(dx1, dy1), center + Offset(dx2, dy2), stroke, StrokeCap.Round)
    when (color) {
        SymbolColor.BLACK, SymbolColor.WHITE -> Unit

        SymbolColor.ORANGE -> line(-half, 0f, half, 0f)

        SymbolColor.GREEN -> line(0f, -half, 0f, half)

        SymbolColor.MAGENTA -> drawCircle(mark, half * 0.8f, center, style = Stroke(stroke))

        SymbolColor.CYAN -> drawCircle(mark, half * 0.6f, center)

        SymbolColor.YELLOW -> {
            line(-half, -half, half, half)
            line(-half, half, half, -half)
        }

        SymbolColor.BLUE -> {
            line(-half, 0f, half, 0f)
            line(0f, -half, 0f, half)
        }
    }
}

/** Elimination mark: a "Y" with three arms 120 degrees apart. */
private fun DrawScope.drawElimination(center: Offset, u: Float, color: Color) {
    val length = 0.16f * u
    for (degrees in listOf(90.0, 210.0, 330.0)) {
        val angle = Math.toRadians(degrees)
        val end = center + Offset((cos(angle) * length).toFloat(), (sin(angle) * length).toFloat())
        drawLine(color, center, end, 0.07f * u, StrokeCap.Round)
    }
}

private fun hexagonPath(center: Offset, radius: Float) = Path().apply {
    for (i in 0 until 6) {
        val angle = PI / 3 * i
        val x = center.x + (radius * cos(angle)).toFloat()
        val y = center.y + (radius * sin(angle)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}
