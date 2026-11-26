package io.github.emilmelgaard.pathwake.ui.puzzle

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import io.github.emilmelgaard.pathwake.puzzle.SymbolColor

/** Colors of the puzzle panel: original art inspired by the game, with a slate-blue panel and a warm glowing line. */
@Immutable
data class PuzzleColors(
    val panel: Color,
    val track: Color,
    val line: Color,
    val lineGlow: Color,
    val solvedLine: Color,
    val dot: Color,
    val error: Color,
    val symbols: Map<SymbolColor, Color>,
    /** Colors of line 0 and line 1 in symmetry puzzles (also used for dots that belong to one of them). */
    val symmetryLines: List<Color>
) {
    fun symbol(color: SymbolColor): Color = symbols.getValue(color)

    fun symmetryLine(line: Int): Color = symmetryLines[line]

    companion object {
        val Panel = PuzzleColors(
            panel = Color(0xFF2E4B63),
            track = Color(0xFF93AFC4),
            line = Color(0xFFFFF6DC),
            lineGlow = Color(0xFFFFD66B),
            solvedLine = Color(0xFFFFFFFF),
            dot = Color(0xFF101820),
            error = Color(0xFFFF4F4F),
            symbols = mapOf(
                SymbolColor.BLACK to Color(0xFF101820),
                SymbolColor.WHITE to Color(0xFFF7F5EE),
                SymbolColor.ORANGE to Color(0xFFFF9F1C),
                SymbolColor.MAGENTA to Color(0xFFE548E0),
                SymbolColor.GREEN to Color(0xFF2FD98A),
                SymbolColor.CYAN to Color(0xFF3CC8F5),
                SymbolColor.YELLOW to Color(0xFFFFD23F),
                SymbolColor.BLUE to Color(0xFF4C8DFF)
            ),
            symmetryLines = listOf(Color(0xFF8CDCFF), Color(0xFFFFE27A))
        )
    }
}
