package io.github.emilmelgaard.pathwake.puzzle

/**
 * Result of checking a line against a puzzle.
 *
 * @property violations dots and symbols whose rules are broken (the UI flashes them).
 * @property pathError set when the line itself is illegal (doesn't start at a start, crosses a gap, ...).
 */
data class ValidationResult(val isValid: Boolean, val violations: Set<Pos> = emptySet(), val pathError: String? = null)

object Validator {
    /**
     * Checks [path], a list of adjacent nodes from a start to an exit node. In symmetry puzzles, [path] may be either
     * of the two lines; the other one is its mirror image.
     */
    fun validate(puzzle: Puzzle, path: List<Pos>): ValidationResult {
        val board = Board(puzzle)
        val traced = board.trace(puzzle.asFirstLine(path))
        val occupied = traced.occupied ?: return ValidationResult(isValid = false, pathError = traced.error)
        val violations = mutableSetOf<Int>()
        val ok = board.check(occupied, traced.secondary, violations)
        return ValidationResult(ok, violations.map(board::pos).toSet())
    }
}
