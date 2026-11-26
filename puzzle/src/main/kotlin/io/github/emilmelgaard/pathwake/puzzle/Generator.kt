package io.github.emilmelgaard.pathwake.puzzle

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.random.Random

enum class Mechanic {
    DOTS,
    GAPS,
    SQUARES,
    STARS,
    TRIANGLES,
    TETRIS,
    NEGATIVE_TETRIS,
    ELIMINATION,
    SYMMETRY,
    MULTIPLE_STARTS_AND_EXITS
}

/** Preset difficulties, easiest first. [TRIVIAL] is a straight line, like the door panels of *The Witness*. */
enum class Difficulty { TRIVIAL, EASY, MEDIUM, HARD, EXPERT, BRUTAL }

/**
 * Parameters for one generated puzzle. Symbol mechanics in [mechanics] are placed by the generator;
 * [Mechanic.SYMMETRY] is controlled by [symmetry] and [Mechanic.MULTIPLE_STARTS_AND_EXITS] adds decoy starts/exits.
 *
 * @property maxSolutions generation keeps adding symbols until the puzzle has at most this many solutions.
 * @property minPathFraction minimum share of the nodes the solution line visits (longer lines are more winding).
 * @property initialSymbols symbols placed at random before solver-guided refinement.
 * @property maxSymbols upper bound on dots + gaps + symbols.
 * @property maxDots upper bound on dots (too many dots just spell out the solution).
 * @property maxGaps upper bound on gaps.
 * @property rotatableChance probability that a tetris piece may be rotated.
 * @property maxTetrisRegion largest region (in cells) that gets tetris pieces.
 */
data class PuzzleSpec(
    val width: Int,
    val height: Int,
    val mechanics: Set<Mechanic>,
    val symmetry: Symmetry = Symmetry.NONE,
    val squareColors: Int = 2,
    val maxSolutions: Int = 1,
    val minPathFraction: Float = 0.4f,
    val initialSymbols: Int = 3,
    val maxSymbols: Int = 12,
    val maxDots: Int = 5,
    val maxGaps: Int = 5,
    val rotatableChance: Float = 0f,
    val maxTetrisRegion: Int = 10
)

/**
 * @property solution one valid solution (the line the puzzle was built around).
 * @property difficulty the preset used, or null when generated from a custom [PuzzleSpec].
 * @property solutionCount exact number of solutions, or null if the solver budget ran out.
 */
data class GeneratedPuzzle(
    val puzzle: Puzzle,
    val solution: List<Pos>,
    val difficulty: Difficulty?,
    val seed: Long,
    val solutionCount: Int?
) {
    val mechanics: Set<Mechanic> get() = puzzle.mechanics()
}

fun Puzzle.mechanics(): Set<Mechanic> = buildSet {
    if (dots.isNotEmpty()) add(Mechanic.DOTS)
    if (gaps.isNotEmpty()) add(Mechanic.GAPS)
    if (symbols.values.any { it is Square }) add(Mechanic.SQUARES)
    if (symbols.values.any { it is Star }) add(Mechanic.STARS)
    if (symbols.values.any { it is Triangles }) add(Mechanic.TRIANGLES)
    if (symbols.values.any { it is Tetris && !it.negative }) add(Mechanic.TETRIS)
    if (symbols.values.any { it is Tetris && it.negative }) add(Mechanic.NEGATIVE_TETRIS)
    if (symbols.values.any { it is Elimination }) add(Mechanic.ELIMINATION)
    if (symmetry != Symmetry.NONE) add(Mechanic.SYMMETRY)
    val pairs = if (symmetry == Symmetry.NONE) 1 else 2
    if (starts.size > pairs || exits.size > pairs) add(Mechanic.MULTIPLE_STARTS_AND_EXITS)
}

object Generator {
    private const val MAX_ATTEMPTS = 40

    /** Total solver steps one [generate] call may use. Counted in steps, not time, so results are reproducible. */
    private const val STEP_BUDGET = 2_500_000L
    private const val SOLVE_STEPS = 150_000L
    private const val PROBE_STEPS = 15_000L
    private const val PROBES = 3

    // Caps that keep puzzles readable.
    private const val MAX_STARS = 8
    private const val MAX_ELIMINATIONS = 2

    /** Mechanics that place symbols (as opposed to changing the layout). */
    private val SYMBOL_MECHANICS = setOf(
        Mechanic.DOTS,
        Mechanic.GAPS,
        Mechanic.SQUARES,
        Mechanic.STARS,
        Mechanic.TRIANGLES,
        Mechanic.TETRIS,
        Mechanic.NEGATIVE_TETRIS,
        Mechanic.ELIMINATION
    )

    /** Generates a puzzle for [difficulty]. The same seed always gives the same puzzle. */
    fun generate(difficulty: Difficulty, seed: Long = Random.nextLong()): GeneratedPuzzle {
        if (difficulty == Difficulty.TRIVIAL) return straightLine(seed)
        val random = Random(seed)
        val budget = Budget(STEP_BUDGET)
        var best: Candidate? = null
        for (attempt in 0 until MAX_ATTEMPTS) {
            if (budget.exhausted && best != null) break
            val candidate = Attempt(spec(difficulty, random), random, budget).run() ?: continue
            if (candidate.meetsTarget) return candidate.toGenerated(difficulty, seed)
            if (best == null || candidate.score < best.score) best = candidate
        }
        best?.let { return it.toGenerated(difficulty, seed) }
        // Practically unreachable. Never loop forever: a hanging generator would mean an alarm that can't be stopped.
        val easy = PuzzleSpec(3, 3, setOf(Mechanic.DOTS), maxSolutions = Int.MAX_VALUE)
        repeat(100) {
            Attempt(easy, random, Budget(STEP_BUDGET)).run()?.let { return it.toGenerated(difficulty, seed) }
        }
        return fallbackPuzzle(difficulty, seed)
    }

    /** A straight track from a start on the left to an exit on the right, without symbols. */
    private fun straightLine(seed: Long): GeneratedPuzzle {
        val length = 3
        val solution = (0..length).map { Pos(2 * it, 0) }
        val puzzle = Puzzle(length, 0, setOf(solution.first()), listOf(Exit(solution.last(), Direction.RIGHT)))
        return GeneratedPuzzle(puzzle, solution, Difficulty.TRIVIAL, seed, solutionCount = 1)
    }

    /** A fixed, always valid puzzle: one dot in the middle of a 3x3 grid. */
    private fun fallbackPuzzle(difficulty: Difficulty, seed: Long): GeneratedPuzzle {
        val solution = listOf(Pos(0, 6), Pos(0, 4), Pos(2, 4), Pos(2, 2), Pos(4, 2), Pos(4, 0), Pos(6, 0))
        val puzzle = Puzzle(3, 3, setOf(Pos(0, 6)), listOf(Exit(Pos(6, 0), Direction.RIGHT)), dots = setOf(Pos(3, 2)))
        return GeneratedPuzzle(puzzle, solution, difficulty, seed, solutionCount = null)
    }

    /** Generates a puzzle from an explicit spec; null if this random attempt didn't work out. */
    fun generate(spec: PuzzleSpec, seed: Long): GeneratedPuzzle? =
        Attempt(spec, Random(seed), Budget(STEP_BUDGET)).run()?.toGenerated(difficulty = null, seed = seed)

    internal fun spec(difficulty: Difficulty, random: Random): PuzzleSpec {
        val base = listOf(Mechanic.DOTS, Mechanic.SQUARES, Mechanic.STARS, Mechanic.TRIANGLES, Mechanic.TETRIS)
        val advanced = listOf(Mechanic.NEGATIVE_TETRIS, Mechanic.ELIMINATION)

        /** [triangleWeight]: how likely triangles are picked compared with each other base mechanic (0: never). */
        fun pick(
            baseCount: Int,
            advancedCount: Int,
            gapChance: Float,
            multiChance: Float,
            triangleWeight: Float = 1f
        ): Set<Mechanic> = buildSet {
            val options = base.associateWith { if (it == Mechanic.TRIANGLES) triangleWeight else 1f }
                .filterValues { it > 0f }
                .toMutableMap()
            repeat(minOf(baseCount, options.size)) {
                var r = random.nextFloat() * options.values.sum()
                val chosen = options.keys.firstOrNull {
                    r -= options.getValue(it)
                    r < 0f
                } ?: options.keys.last()
                add(chosen)
                options -= chosen
            }
            addAll(advanced.shuffled(random).take(advancedCount))
            if (Mechanic.NEGATIVE_TETRIS in this) add(Mechanic.TETRIS)
            if (random.nextFloat() < gapChance) add(Mechanic.GAPS)
            if (random.nextFloat() < multiChance) add(Mechanic.MULTIPLE_STARTS_AND_EXITS)
        }

        fun symmetry(chance: Float) = if (random.nextFloat() < chance) {
            listOf(Symmetry.HORIZONTAL, Symmetry.VERTICAL, Symmetry.ROTATIONAL).random(random)
        } else {
            Symmetry.NONE
        }
        return when (difficulty) {
            Difficulty.TRIVIAL -> error("Trivial puzzles aren't generated from a spec")

            Difficulty.EASY -> PuzzleSpec(
                width = 3,
                height = 3,
                mechanics = pick(1, 0, gapChance = 0.3f, multiChance = 0f, triangleWeight = 0f),
                maxSolutions = 3,
                minPathFraction = 0.3f,
                initialSymbols = 2,
                maxSymbols = 6,
                maxDots = 3,
                maxGaps = 2,
                maxTetrisRegion = 5
            )

            Difficulty.MEDIUM -> PuzzleSpec(
                width = 4,
                height = 4,
                mechanics = pick(2, 0, gapChance = 0.3f, multiChance = 0.2f, triangleWeight = 0.35f),
                symmetry = symmetry(0.15f),
                maxSolutions = 2,
                minPathFraction = 0.35f,
                initialSymbols = 4,
                maxSymbols = 10,
                maxDots = 4,
                maxGaps = 3,
                rotatableChance = 0.25f,
                maxTetrisRegion = 8
            )

            Difficulty.HARD -> PuzzleSpec(
                width = 5,
                height = if (random.nextBoolean()) 4 else 5,
                mechanics = pick(2, if (random.nextFloat() < 0.3f) 1 else 0, gapChance = 0.4f, multiChance = 0.3f),
                symmetry = symmetry(0.2f),
                squareColors = 2 + random.nextInt(2),
                maxSolutions = 1,
                minPathFraction = 0.4f,
                initialSymbols = 6,
                maxSymbols = 14,
                rotatableChance = 0.4f,
                maxTetrisRegion = 10
            )

            Difficulty.EXPERT -> PuzzleSpec(
                width = 5,
                height = 5,
                mechanics = pick(2, 1, gapChance = 0.4f, multiChance = 0.4f),
                symmetry = symmetry(0.25f),
                squareColors = 3,
                maxSolutions = 1,
                minPathFraction = 0.45f,
                initialSymbols = 8,
                maxSymbols = 18,
                rotatableChance = 0.5f,
                maxTetrisRegion = 12
            )

            Difficulty.BRUTAL -> PuzzleSpec(
                width = 6,
                height = 6,
                mechanics = pick(2 + random.nextInt(2), 1 + random.nextInt(2), gapChance = 0.5f, multiChance = 0.5f),
                symmetry = symmetry(0.3f),
                squareColors = 3,
                maxSolutions = 1,
                minPathFraction = 0.5f,
                initialSymbols = 10,
                maxSymbols = 24,
                maxDots = 6,
                rotatableChance = 0.6f,
                maxTetrisRegion = 14
            )
        }
    }

    private class Budget(var remaining: Long) {
        val exhausted: Boolean get() = remaining <= 0

        fun solve(puzzle: Puzzle, maxSolutions: Int, maxSteps: Long, random: Random? = null): SolveResult {
            val result = Solver.solve(puzzle, maxSolutions, minOf(maxSteps, maxOf(remaining, 1)), random)
            remaining -= result.steps
            return result
        }
    }

    private class Candidate(
        val puzzle: Puzzle,
        val solution: List<Pos>,
        val solutionCount: Int?,
        val meetsTarget: Boolean
    ) {
        val score: Int get() = solutionCount ?: Int.MAX_VALUE

        fun toGenerated(difficulty: Difficulty?, seed: Long) =
            GeneratedPuzzle(puzzle, solution, difficulty, seed, solutionCount)
    }

    /** A set of additions that is applied (and reverted) as a whole. */
    private data class Change(
        val dots: Set<Pos> = emptySet(),
        val gaps: Set<Pos> = emptySet(),
        val symbols: Map<Pos, Symbol> = emptyMap(),
        val dotLines: Map<Pos, Int> = emptyMap()
    )

    private class Attempt(private val spec: PuzzleSpec, private val random: Random, private val budget: Budget) {
        private val w = spec.width
        private val h = spec.height
        private val symmetry = spec.symmetry
        private val symmetric = symmetry != Symmetry.NONE
        private val starts = linkedSetOf<Pos>()
        private val exits = mutableListOf<Exit>()
        private val gaps = linkedSetOf<Pos>()
        private val dots = linkedSetOf<Pos>()
        private val dotLines = linkedMapOf<Pos, Int>()
        private val symbols = linkedMapOf<Pos, Symbol>()

        private lateinit var solution: List<Pos>

        /** Nodes and edges covered by the solution (both lines in symmetry puzzles). */
        private lateinit var occupied: Set<Pos>

        /** Nodes and edges covered by the mirrored line. */
        private var secondary: Set<Pos> = emptySet()
        private lateinit var regionOfCell: Map<Pos, Int>
        private val regionColor = mutableMapOf<Int, SymbolColor>()

        private val symbolMechanics = spec.mechanics intersect SYMBOL_MECHANICS
        private val allCells = (0 until h).flatMap { j -> (0 until w).map { i -> Pos(2 * i + 1, 2 * j + 1) } }
        private val allEdges = (0..2 * h).flatMap { y -> (0..2 * w).map { x -> Pos(x, y) } }.filter { it.isEdge }

        private fun mirror(p: Pos) = symmetry.mirror(p, w, h)

        private fun build() = Puzzle(
            w,
            h,
            starts.toSet(),
            exits.toList(),
            gaps.toSet(),
            dots.toSet(),
            symbols.toMap(),
            symmetry,
            dotLines.toMap()
        )

        private fun symbolCount() = dots.size + gaps.size + symbols.size

        fun run(): Candidate? {
            if (!layout()) return null
            if (!placeSolution()) return null
            repeat(spec.initialSymbols) { addRandom() }
            return refine()
        }

        private fun inGrid(p: Pos) = p.x in 0..2 * w && p.y in 0..2 * h

        private fun randomNode() = Pos(2 * random.nextInt(w + 1), 2 * random.nextInt(h + 1))

        private fun randomBorderExit(): Exit {
            val side = random.nextInt(4)
            return when (side) {
                0 -> Exit(Pos(2 * random.nextInt(w + 1), 0), Direction.UP)
                1 -> Exit(Pos(2 * random.nextInt(w + 1), 2 * h), Direction.DOWN)
                2 -> Exit(Pos(0, 2 * random.nextInt(h + 1)), Direction.LEFT)
                else -> Exit(Pos(2 * w, 2 * random.nextInt(h + 1)), Direction.RIGHT)
            }
        }

        private fun addStart(s: Pos) {
            starts += s
            starts += mirror(s)
        }

        private fun addExit(e: Exit) {
            exits += e
            if (symmetric) exits += Exit(mirror(e.node), symmetry.mirror(e.direction))
        }

        private fun usableStart(s: Pos) =
            (!symmetric || mirror(s) != s) && exits.none { it.node == s || it.node == mirror(s) }

        private fun usableExit(e: Exit) = (!symmetric || mirror(e.node) != e.node) &&
            e.node !in starts &&
            mirror(e.node) !in starts &&
            exits.none { it.node == e.node || it.node == mirror(e.node) }

        private fun layout(): Boolean {
            val start = (0 until 50).asSequence()
                .map {
                    if (random.nextBoolean()) {
                        Pos(if (random.nextBoolean()) 0 else 2 * w, if (random.nextBoolean()) 0 else 2 * h)
                    } else {
                        randomNode()
                    }
                }
                .firstOrNull { usableStart(it) } ?: return false
            addStart(start)
            val minDistance = (w + h) / (if (symmetric) 3 else 2)
            val exit = (0 until 50).asSequence()
                .map { randomBorderExit() }
                .firstOrNull {
                    usableExit(it) &&
                        (abs(it.node.x - start.x) + abs(it.node.y - start.y)) / 2 >= minDistance
                }
                ?: return false
            addExit(exit)
            if (Mechanic.MULTIPLE_STARTS_AND_EXITS in spec.mechanics) {
                repeat(1 + random.nextInt(2)) {
                    val s = randomNode()
                    if (usableStart(s)) addStart(s)
                }
                repeat(1 + random.nextInt(2)) {
                    val e = randomBorderExit()
                    if (usableExit(e)) addExit(e)
                }
            }
            return true
        }

        private fun placeSolution(): Boolean {
            val minNodes = maxOf(2, ceil(spec.minPathFraction * (w + 1) * (h + 1) / (if (symmetric) 2 else 1)).toInt())
            // A mirrored line can't cross the axis, so it may only reach one exit of each mirrored pair.
            val ends = listOf(exits[0].node, exits[minOf(1, exits.lastIndex)].node).distinct()
            repeat(30) {
                val drawn = randomPath(starts.first(), ends.random(random), minNodes) ?: return@repeat
                val board = Board(build())
                val path = board.puzzle.asFirstLine(drawn)
                val traced = board.trace(path)
                val occupiedArray = traced.occupied ?: return@repeat
                val regionOf = IntArray(board.size)
                val regions = board.regions(occupiedArray, regionOf)
                if (Mechanic.SQUARES in spec.mechanics && regions.size < 2) return@repeat
                solution = path
                occupied = (0 until board.size).filter { occupiedArray[it] }.map(board::pos).toSet()
                secondary = traced.secondary?.let { s -> (0 until board.size).filter { s[it] }.map(board::pos).toSet() }
                    ?: emptySet()
                regionOfCell = allCells.associateWith { regionOf[board.index(it)] }
                assignRegionColors(regions.size)
                return true
            }
            return false
        }

        private fun randomPath(start: Pos, end: Pos, minNodes: Int): List<Pos>? {
            val path = arrayListOf(start)
            val visited = hashSetOf(start, mirror(start))
            while (true) {
                val head = path[path.lastIndex]
                if (head == end) return path.takeIf { it.size >= minNodes }
                val options = Direction.entries
                    .map { head.step(it, 2) }
                    .filter {
                        inGrid(it) && it !in visited &&
                            (!symmetric || (mirror(it) != it && mirror(it) !in visited))
                    }
                    .filter { it != end || path.size + 1 >= minNodes }
                    .filter { it == end || canReach(it, end, visited) }
                if (options.isEmpty()) return null
                val next = if (end in options && random.nextFloat() < 0.35f) end else options.random(random)
                path += next
                visited += next
                visited += mirror(next)
            }
        }

        /** Whether the line could still get from [from] to [target] without crossing [visited] (or the axis). */
        private fun canReach(from: Pos, target: Pos, visited: Set<Pos>): Boolean {
            val blocked = visited + mirror(from)
            val seen = hashSetOf(from)
            val queue = ArrayDeque(listOf(from))
            while (queue.isNotEmpty()) {
                val p = queue.removeFirst()
                for (d in Direction.entries) {
                    val n = p.step(d, 2)
                    if (n == target) return true
                    if (!inGrid(n) || n in blocked || (symmetric && mirror(n) == n) || !seen.add(n)) continue
                    queue.addLast(n)
                }
            }
            return false
        }

        private fun assignRegionColors(regionCount: Int) {
            val palette = listOf(SymbolColor.BLACK, SymbolColor.WHITE, SymbolColor.CYAN, SymbolColor.MAGENTA)
                .take(spec.squareColors.coerceIn(2, 4))
            for (r in 0 until regionCount) regionColor[r] = palette.random(random)
            if (regionCount >= 2 && regionColor.values.toSet().size < 2) {
                val r = random.nextInt(regionCount)
                regionColor[r] = palette.first { it != regionColor[r] }
            }
        }

        private fun isValid(path: List<Pos>) = Validator.validate(build(), path).isValid

        private fun apply(change: Change) {
            dots += change.dots
            gaps += change.gaps
            symbols += change.symbols
            dotLines += change.dotLines
        }

        private fun revert(change: Change) {
            dots -= change.dots
            gaps -= change.gaps
            change.symbols.keys.forEach { symbols -= it }
            change.dotLines.keys.forEach { dotLines -= it }
        }

        /** Applies [change] if the intended solution stays valid and (optionally) [alternative] becomes invalid. */
        private fun tryApply(change: Change, alternative: List<Pos>? = null): Boolean {
            if (change.symbols.keys.any { it in symbols } || change.dots.any { it in dots || it in gaps }) return false
            apply(change)
            if (isValid(solution) && (alternative == null || !isValid(alternative))) return true
            revert(change)
            return false
        }

        private fun emptyCells() = allCells.filter { it !in symbols }

        private fun edgesUsedAround(cell: Pos, line: Set<Pos>) = Direction.entries.count { cell.step(it) in line }

        private fun cellsByRegion(): Map<Int, List<Pos>> = allCells.groupBy { regionOfCell.getValue(it) }

        private fun proposals(mechanic: Mechanic, alternative: Set<Pos>?): List<Change> {
            val empty = emptyCells()
            return when (mechanic) {
                Mechanic.DOTS ->
                    occupied
                        .filter { dots.size < spec.maxDots }
                        .filter { it !in starts && it !in dots && (alternative == null || it !in alternative) }
                        .map { p ->
                            val colored = symmetric && random.nextBoolean()
                            Change(dots = setOf(p), dotLines = if (colored) mapOf(p to lineOf(p)) else emptyMap())
                        }

                Mechanic.GAPS ->
                    allEdges
                        .filter { gaps.size < spec.maxGaps }
                        .filter { it !in occupied && it !in gaps && it !in dots }
                        .filter { alternative == null || it in alternative }
                        .map { Change(gaps = setOf(it)) }

                Mechanic.SQUARES -> empty.map { cell ->
                    Change(symbols = mapOf(cell to Square(regionColor.getValue(regionOfCell.getValue(cell)))))
                }

                Mechanic.TRIANGLES -> empty.mapNotNull { cell ->
                    val used = edgesUsedAround(cell, occupied)
                    val differs = alternative == null || edgesUsedAround(cell, alternative) != used
                    if (used in 1..3 && differs) Change(symbols = mapOf(cell to Triangles(used))) else null
                }

                Mechanic.STARS -> starProposals(empty)

                Mechanic.TETRIS -> tetrisProposals(negative = false)

                Mechanic.NEGATIVE_TETRIS -> tetrisProposals(negative = true)

                Mechanic.ELIMINATION -> eliminationProposals()

                Mechanic.SYMMETRY, Mechanic.MULTIPLE_STARTS_AND_EXITS -> emptyList()
            }.shuffled(random)
        }

        private fun lineOf(p: Pos) = if (p in secondary) 1 else 0

        private fun starProposals(empty: List<Pos>): List<Change> {
            if (symbols.values.count { it is Star } >= MAX_STARS) return emptyList()
            val colors = listOf(
                SymbolColor.BLACK,
                SymbolColor.WHITE,
                SymbolColor.MAGENTA,
                SymbolColor.GREEN,
                SymbolColor.CYAN,
                SymbolColor.ORANGE,
                SymbolColor.YELLOW
            )
            val byRegion = empty.groupBy { regionOfCell.getValue(it) }
            return (0 until 12).mapNotNull {
                val (region, cells) = byRegion.entries.randomOrNull(random)?.toPair() ?: return@mapNotNull null
                val color = colors.random(random)
                val existing = symbols.count { (p, s) ->
                    regionOfCell[p] == region && s.color == color &&
                        s !is Elimination
                }
                val picked = cells.shuffled(random)
                when {
                    existing == 0 && picked.size >= 2 -> Change(
                        symbols = mapOf(picked[0] to Star(color), picked[1] to Star(color))
                    )

                    existing == 1 -> Change(symbols = mapOf(picked[0] to Star(color)))

                    else -> null
                }
            }
        }

        /** Cell coordinates (column, row) of a cell position. */
        private fun cellXY(cell: Pos) = Pos(cell.x / 2, cell.y / 2)

        private fun neighbours(p: Pos) = Direction.entries.map { Pos(p.x + it.dx, p.y + it.dy) }

        /**
         * Pieces that exactly tile a region of the solution. With [negative], the region is first extended by a few
         * outside cells, which a subtractive piece then cancels.
         */
        private fun tetrisProposals(negative: Boolean): List<Change> {
            val regions = cellsByRegion().values.filter { cells ->
                cells.size <= spec.maxTetrisRegion && cells.none { symbols[it] is Tetris } &&
                    cells.any { it !in symbols }
            }
            if (regions.isEmpty()) return emptyList()
            return (0 until 8).mapNotNull {
                val cells = regions.random(random)
                val area = cells.map(::cellXY).toSet()
                val extension = if (negative) growExtension(area) ?: return@mapNotNull null else emptySet()
                val pieces = partition(area + extension) ?: return@mapNotNull null
                if (negative && pieces.any { it.all { cell -> cell in extension } }) return@mapNotNull null
                val shapes = pieces.map { Polyomino.of(it) to false } +
                    (if (negative) listOf(Polyomino.of(extension) to true) else emptyList())
                val slots = cells.filter { it !in symbols }.shuffled(random)
                if (shapes.size > slots.size || shapes.size > 4) return@mapNotNull null
                Change(
                    symbols = shapes.mapIndexed { i, (shape, isNegative) ->
                        val rotatable = random.nextFloat() < spec.rotatableChance
                        val shown = if (rotatable) shape.rotations.random(random) else shape
                        slots[i] to Tetris(shown, rotatable, isNegative)
                    }.toMap()
                )
            }
        }

        /** 1-2 connected cells next to [area] (cell coordinates) but outside it. */
        private fun growExtension(area: Set<Pos>): Set<Pos>? {
            fun inside(p: Pos) = p.x in 0 until w && p.y in 0 until h
            val border = area.flatMap(::neighbours).filter { inside(it) && it !in area }.distinct()
            val start = border.randomOrNull(random) ?: return null
            val extension = mutableSetOf(start)
            if (random.nextBoolean()) {
                neighbours(start).filter { inside(it) && it !in area }.randomOrNull(random)?.let { extension += it }
            }
            return extension
        }

        /** Splits [cells] into random connected pieces of 1-4 cells (mostly 3-4). */
        private fun partition(cells: Set<Pos>): List<Set<Pos>>? {
            val remaining = cells.toMutableSet()
            val pieces = mutableListOf<Set<Pos>>()
            while (remaining.isNotEmpty()) {
                // Start from a poorly connected cell so no single cells get stranded.
                val start = remaining.minBy { c -> neighbours(c).count { it in remaining } * 10 + random.nextInt(10) }
                val target = listOf(1, 2, 2, 3, 3, 3, 4, 4, 4, 4).random(random)
                val piece = mutableSetOf(start)
                while (piece.size < target) {
                    val frontier = piece.flatMap(::neighbours).filter { it in remaining && it !in piece }
                    if (frontier.isEmpty()) break
                    piece += frontier.random(random)
                }
                remaining -= piece
                pieces += piece
            }
            return pieces.takeIf { it.size <= 4 }
        }

        /** An elimination mark plus a symbol (or dot) that is wrong without it, in the same region. */
        private fun eliminationProposals(): List<Change> {
            if (symbols.values.count { it is Elimination } >= MAX_ELIMINATIONS) return emptyList()
            val byRegion = cellsByRegion()
            return (0 until 10).mapNotNull {
                val cells = byRegion.values.random(random)
                val empty = cells.filter { it !in symbols }.shuffled(random)
                if (empty.isEmpty()) return@mapNotNull null
                val mark = mapOf(empty[0] to Elimination())
                when (random.nextInt(4)) {
                    0 -> empty.getOrNull(1)?.let { cell ->
                        val color = listOf(SymbolColor.BLACK, SymbolColor.WHITE, SymbolColor.CYAN).random(random)
                        Change(symbols = mark + (cell to Square(color)))
                    }

                    1 -> empty.getOrNull(1)?.let { cell ->
                        Change(
                            symbols =
                                mark + (cell to Star(listOf(SymbolColor.MAGENTA, SymbolColor.GREEN).random(random)))
                        )
                    }

                    2 -> empty.getOrNull(1)?.let { cell ->
                        val wrong = (1..3).filter { it != edgesUsedAround(cell, occupied) }.random(random)
                        Change(symbols = mark + (cell to Triangles(wrong)))
                    }

                    else ->
                        cells
                            .flatMap { c -> Direction.entries.map { c.step(it) } }
                            .filter { it !in occupied && it !in gaps && it !in dots }
                            .randomOrNull(random)
                            ?.let { Change(dots = setOf(it), symbols = mark) }
                }
            }
        }

        private fun addRandom(): Boolean {
            for (mechanic in symbolMechanics.shuffled(random)) {
                for (change in proposals(mechanic, null).take(10)) {
                    if (tryApply(change)) return true
                }
            }
            return false
        }

        private fun addDistinguishing(alternative: List<Pos>): Boolean {
            val alternativeOccupied = Board(build()).let { board ->
                val occ = board.trace(alternative).occupied ?: return false
                (0 until board.size).filter { occ[it] }.map(board::pos).toSet()
            }
            for (mechanic in symbolMechanics.shuffled(random)) {
                for (change in proposals(mechanic, alternativeOccupied).take(15)) {
                    if (tryApply(change, alternative)) return true
                }
            }
            return false
        }

        /**
         * Adds symbols until the solver can show there are at most `maxSolutions` solutions. If the exhaustive search
         * runs out of steps without finding an alternative, randomized probes look for one; if they find none either,
         * the puzzle is accepted with an unknown (but evidently small) number of solutions.
         */
        private fun refine(): Candidate? {
            while (true) {
                if (!squaresHaveTwoColors()) return null
                val puzzle = build()
                val result = budget.solve(puzzle, maxSolutions = spec.maxSolutions + 1, maxSteps = SOLVE_STEPS)
                if (result.exhaustive) return Candidate(puzzle, solution, result.solutions.size, meetsTarget = true)
                var alternatives = result.solutions.filter { !sameSolution(it) }
                if (alternatives.isEmpty()) {
                    alternatives = probe(puzzle)
                    if (alternatives.isEmpty()) {
                        return Candidate(
                            puzzle,
                            solution,
                            null,
                            meetsTarget = !budget.exhausted
                        )
                    }
                }
                if (symbolCount() >= spec.maxSymbols || budget.exhausted) {
                    return Candidate(puzzle, solution, null, meetsTarget = false)
                }
                if (!addDistinguishing(alternatives.random(random)) && !addRandom()) {
                    return Candidate(puzzle, solution, null, meetsTarget = false)
                }
            }
        }

        /**
         * Squares of a single color don't constrain the line, so if all squares have the same color, this adds one
         * of another color. False if that isn't possible.
         */
        private fun squaresHaveTwoColors(): Boolean {
            val colors = symbols.values.filterIsInstance<Square>().map { it.color }.toSet()
            if (colors.size != 1) return true
            return emptyCells()
                .map { it to regionColor.getValue(regionOfCell.getValue(it)) }
                .filter { (_, color) -> color !in colors }
                .shuffled(random)
                .any { (cell, color) -> tryApply(Change(symbols = mapOf(cell to Square(color)))) }
        }

        private fun sameSolution(path: List<Pos>) = path == solution

        private fun probe(puzzle: Puzzle): List<List<Pos>> {
            repeat(PROBES) {
                val result = budget.solve(
                    puzzle,
                    maxSolutions = 2,
                    maxSteps = PROBE_STEPS,
                    random = Random(random.nextLong())
                )
                val alternatives = result.solutions.filter { !sameSolution(it) }
                if (alternatives.isNotEmpty()) return alternatives
            }
            return emptyList()
        }
    }
}
