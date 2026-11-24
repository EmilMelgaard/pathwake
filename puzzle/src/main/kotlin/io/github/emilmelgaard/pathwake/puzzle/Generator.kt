package io.github.emilmelgaard.pathwake.puzzle

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.random.Random

enum class Mechanic { DOTS, GAPS, SQUARES, STARS, TRIANGLES, MULTIPLE_STARTS_AND_EXITS }

enum class Difficulty { EASY, MEDIUM, HARD, EXPERT, FIENDISH }

/**
 * Parameters for one generated puzzle.
 *
 * @property maxSolutions generation keeps adding symbols until the puzzle has at most this many solutions.
 * @property minPathFraction minimum share of the nodes the solution line visits (longer lines are more winding).
 * @property initialSymbols symbols placed at random before solver-guided refinement.
 * @property maxSymbols upper bound on dots + gaps + symbols.
 * @property maxDots upper bound on dots (too many dots just spell out the solution).
 * @property maxGaps upper bound on gaps.
 */
data class PuzzleSpec(
    val width: Int,
    val height: Int,
    val mechanics: Set<Mechanic>,
    val squareColors: Int = 2,
    val maxSolutions: Int = 1,
    val minPathFraction: Float = 0.4f,
    val initialSymbols: Int = 3,
    val maxSymbols: Int = 12,
    val maxDots: Int = 5,
    val maxGaps: Int = 5
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
    if (starts.size > 1 || exits.size > 1) add(Mechanic.MULTIPLE_STARTS_AND_EXITS)
}

object Generator {
    private const val MAX_ATTEMPTS = 40

    /** Total solver steps one [generate] call may use. Counted in steps, not time, so results are reproducible. */
    private const val STEP_BUDGET = 2_500_000L
    private const val SOLVE_STEPS = 150_000L
    private const val PROBE_STEPS = 15_000L
    private const val PROBES = 3

    /** Generates a puzzle for [difficulty]. The same seed always gives the same puzzle. */
    fun generate(difficulty: Difficulty, seed: Long = Random.nextLong()): GeneratedPuzzle {
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
        // Practically unreachable; fall back to a puzzle that always generates.
        val easy = PuzzleSpec(3, 3, setOf(Mechanic.DOTS), maxSolutions = Int.MAX_VALUE)
        while (true) {
            Attempt(easy, random, Budget(STEP_BUDGET)).run()?.let { return it.toGenerated(difficulty, seed) }
        }
    }

    /** Generates a puzzle from an explicit spec; null if this random attempt didn't work out. */
    fun generate(spec: PuzzleSpec, seed: Long): GeneratedPuzzle? =
        Attempt(spec, Random(seed), Budget(STEP_BUDGET)).run()?.toGenerated(difficulty = null, seed = seed)

    private class Budget(var remaining: Long) {
        val exhausted: Boolean get() = remaining <= 0

        fun solve(puzzle: Puzzle, maxSolutions: Int, maxSteps: Long, random: Random? = null): SolveResult {
            val result = Solver.solve(puzzle, maxSolutions, minOf(maxSteps, maxOf(remaining, 1)), random)
            remaining -= result.steps
            return result
        }
    }

    internal fun spec(difficulty: Difficulty, random: Random): PuzzleSpec {
        val symbolMechanics = listOf(Mechanic.DOTS, Mechanic.SQUARES, Mechanic.STARS, Mechanic.TRIANGLES)

        fun pick(count: Int, gapChance: Float, multiChance: Float): Set<Mechanic> = buildSet {
            addAll(symbolMechanics.shuffled(random).take(count))
            if (random.nextFloat() < gapChance) add(Mechanic.GAPS)
            if (random.nextFloat() < multiChance) add(Mechanic.MULTIPLE_STARTS_AND_EXITS)
        }
        return when (difficulty) {
            Difficulty.EASY -> PuzzleSpec(
                width = 3,
                height = 3,
                mechanics = pick(1, gapChance = 0.3f, multiChance = 0f),
                maxSolutions = 3,
                minPathFraction = 0.3f,
                initialSymbols = 2,
                maxSymbols = 6,
                maxDots = 3,
                maxGaps = 2
            )

            Difficulty.MEDIUM -> PuzzleSpec(
                width = 4,
                height = 4,
                mechanics = pick(2, gapChance = 0.3f, multiChance = 0.2f),
                maxSolutions = 2,
                minPathFraction = 0.35f,
                initialSymbols = 4,
                maxSymbols = 10,
                maxDots = 4,
                maxGaps = 3
            )

            Difficulty.HARD -> PuzzleSpec(
                width = 5,
                height = if (random.nextBoolean()) 4 else 5,
                mechanics = pick(2, gapChance = 0.4f, multiChance = 0.4f),
                squareColors = 2 + random.nextInt(2),
                maxSolutions = 1,
                minPathFraction = 0.4f,
                initialSymbols = 6,
                maxSymbols = 14
            )

            Difficulty.EXPERT -> PuzzleSpec(
                width = 5,
                height = 5,
                mechanics = pick(3, gapChance = 0.4f, multiChance = 0.5f),
                squareColors = 3,
                maxSolutions = 1,
                minPathFraction = 0.45f,
                initialSymbols = 8,
                maxSymbols = 18
            )

            Difficulty.FIENDISH -> PuzzleSpec(
                width = 6,
                height = 6,
                mechanics = pick(3 + random.nextInt(2), gapChance = 0.5f, multiChance = 0.6f),
                squareColors = 3,
                maxSolutions = 1,
                minPathFraction = 0.5f,
                initialSymbols = 10,
                maxSymbols = 24,
                maxDots = 6
            )
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

    private sealed interface Change {
        data class AddDot(val pos: Pos) : Change

        data class AddGap(val pos: Pos) : Change

        data class AddSymbols(val symbols: Map<Pos, Symbol>) : Change
    }

    private class Attempt(private val spec: PuzzleSpec, private val random: Random, private val budget: Budget) {
        private val w = spec.width
        private val h = spec.height
        private val starts = linkedSetOf<Pos>()
        private val exits = mutableListOf<Exit>()
        private val gaps = linkedSetOf<Pos>()
        private val dots = linkedSetOf<Pos>()
        private val symbols = linkedMapOf<Pos, Symbol>()

        private lateinit var solution: List<Pos>
        private lateinit var occupied: Set<Pos>
        private lateinit var regionOfCell: Map<Pos, Int>
        private val regionColor = mutableMapOf<Int, SymbolColor>()

        private val symbolMechanics = spec.mechanics - Mechanic.MULTIPLE_STARTS_AND_EXITS
        private val allCells = (0 until h).flatMap { j -> (0 until w).map { i -> Pos(2 * i + 1, 2 * j + 1) } }
        private val allEdges = (0..2 * h).flatMap { y -> (0..2 * w).map { x -> Pos(x, y) } }.filter { it.isEdge }

        private fun build() = Puzzle(w, h, starts.toSet(), exits.toList(), gaps.toSet(), dots.toSet(), symbols.toMap())

        private fun symbolCount() = dots.size + gaps.size + symbols.size

        fun run(): Candidate? {
            if (!layout()) return null
            if (!placeSolution()) return null
            repeat(spec.initialSymbols) { addRandom() }
            return refine()
        }

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

        private fun layout(): Boolean {
            val start = if (random.nextBoolean()) {
                Pos(if (random.nextBoolean()) 0 else 2 * w, if (random.nextBoolean()) 0 else 2 * h)
            } else {
                randomNode()
            }
            starts += start
            val minDistance = (w + h) / 2
            val exit = (0 until 50).asSequence()
                .map { randomBorderExit() }
                .firstOrNull { (abs(it.node.x - start.x) + abs(it.node.y - start.y)) / 2 >= minDistance }
                ?: return false
            exits += exit
            if (Mechanic.MULTIPLE_STARTS_AND_EXITS in spec.mechanics) {
                repeat(1 + random.nextInt(2)) {
                    val s = randomNode()
                    if (exits.none { it.node == s }) starts += s
                }
                repeat(1 + random.nextInt(2)) {
                    val e = randomBorderExit()
                    if (e.node !in starts && exits.none { it.node == e.node }) exits += e
                }
            }
            return true
        }

        private fun placeSolution(): Boolean {
            val minNodes = maxOf(2, ceil(spec.minPathFraction * (w + 1) * (h + 1)).toInt())
            repeat(30) {
                val path = randomPath(starts.first(), exits.first().node, minNodes) ?: return@repeat
                val board = Board(build())
                val occupiedArray = board.trace(path).occupied ?: return@repeat
                val regionOf = IntArray(board.size)
                val regions = board.regions(occupiedArray, regionOf)
                if (Mechanic.SQUARES in spec.mechanics && regions.size < 2) return@repeat
                solution = path
                occupied = (0 until board.size).filter { occupiedArray[it] }.map(board::pos).toSet()
                regionOfCell = allCells.associateWith { regionOf[board.index(it)] }
                assignRegionColors(regions.size)
                return true
            }
            return false
        }

        private fun randomPath(start: Pos, end: Pos, minNodes: Int): List<Pos>? {
            val path = arrayListOf(start)
            val visited = hashSetOf(start)
            while (true) {
                val head = path[path.lastIndex]
                if (head == end) return path.takeIf { it.size >= minNodes }
                val options = Direction.entries
                    .map { head.step(it, 2) }
                    .filter { it.x in 0..2 * w && it.y in 0..2 * h && it !in visited }
                    .filter { it != end || path.size + 1 >= minNodes }
                    .filter { it == end || canReach(it, end, visited) }
                if (options.isEmpty()) return null
                val next = if (end in options && random.nextFloat() < 0.35f) end else options.random(random)
                path += next
                visited += next
            }
        }

        private fun canReach(from: Pos, target: Pos, visited: Set<Pos>): Boolean {
            val seen = hashSetOf(from)
            val queue = ArrayDeque(listOf(from))
            while (queue.isNotEmpty()) {
                val p = queue.removeFirst()
                for (d in Direction.entries) {
                    val n = p.step(d, 2)
                    if (n == target) return true
                    if (n.x !in 0..2 * w || n.y !in 0..2 * h || n in visited || !seen.add(n)) continue
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
            when (change) {
                is Change.AddDot -> dots += change.pos
                is Change.AddGap -> gaps += change.pos
                is Change.AddSymbols -> symbols += change.symbols
            }
        }

        private fun revert(change: Change) {
            when (change) {
                is Change.AddDot -> dots -= change.pos
                is Change.AddGap -> gaps -= change.pos
                is Change.AddSymbols -> change.symbols.keys.forEach { symbols -= it }
            }
        }

        /** Applies [change] if the intended solution stays valid and (optionally) [alternative] becomes invalid. */
        private fun tryApply(change: Change, alternative: List<Pos>? = null): Boolean {
            apply(change)
            if (isValid(solution) && (alternative == null || !isValid(alternative))) return true
            revert(change)
            return false
        }

        private fun emptyCells() = allCells.filter { it !in symbols }

        private fun edgesUsedAround(cell: Pos, line: Set<Pos>) = Direction.entries.count { cell.step(it) in line }

        private fun proposals(mechanic: Mechanic, alternative: Set<Pos>?): List<Change> {
            val empty = emptyCells()
            return when (mechanic) {
                Mechanic.DOTS ->
                    occupied
                        .filter { dots.size < spec.maxDots }
                        .filter { it !in starts && it !in dots && (alternative == null || it !in alternative) }
                        .map { Change.AddDot(it) }

                Mechanic.GAPS ->
                    allEdges
                        .filter { gaps.size < spec.maxGaps }
                        .filter { it !in occupied && it !in gaps && it !in dots }
                        .filter { alternative == null || it in alternative }
                        .map { Change.AddGap(it) }

                Mechanic.SQUARES -> empty.map { cell ->
                    Change.AddSymbols(mapOf(cell to Square(regionColor.getValue(regionOfCell.getValue(cell)))))
                }

                Mechanic.TRIANGLES -> empty.mapNotNull { cell ->
                    val used = edgesUsedAround(cell, occupied)
                    val differs = alternative == null || edgesUsedAround(cell, alternative) != used
                    if (used in 1..3 && differs) Change.AddSymbols(mapOf(cell to Triangles(used))) else null
                }

                Mechanic.STARS -> starProposals(empty)

                Mechanic.MULTIPLE_STARTS_AND_EXITS -> emptyList()
            }.shuffled(random)
        }

        private fun starProposals(empty: List<Pos>): List<Change> {
            val colors = listOf(
                SymbolColor.BLACK,
                SymbolColor.WHITE,
                SymbolColor.MAGENTA,
                SymbolColor.GREEN,
                SymbolColor.CYAN,
                SymbolColor.ORANGE
            )
            val byRegion = empty.groupBy { regionOfCell.getValue(it) }
            return (0 until 12).mapNotNull {
                val (region, cells) = byRegion.entries.randomOrNull(random)?.toPair() ?: return@mapNotNull null
                val color = colors.random(random)
                val existing = symbols.count { (p, s) -> regionOfCell[p] == region && s.color == color }
                val picked = cells.shuffled(random)
                when {
                    existing == 0 && picked.size >= 2 -> Change.AddSymbols(
                        mapOf(picked[0] to Star(color), picked[1] to Star(color))
                    )

                    existing == 1 -> Change.AddSymbols(mapOf(picked[0] to Star(color)))

                    else -> null
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
        private fun refine(): Candidate {
            while (true) {
                val puzzle = build()
                val result = budget.solve(puzzle, maxSolutions = spec.maxSolutions + 1, maxSteps = SOLVE_STEPS)
                if (result.exhaustive) return Candidate(puzzle, solution, result.solutions.size, meetsTarget = true)
                var alternatives = result.solutions.filter { it != solution }
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

        private fun probe(puzzle: Puzzle): List<List<Pos>> {
            repeat(PROBES) {
                val result = budget.solve(
                    puzzle,
                    maxSolutions = 2,
                    maxSteps = PROBE_STEPS,
                    random = Random(random.nextLong())
                )
                val alternatives = result.solutions.filter { it != solution }
                if (alternatives.isNotEmpty()) return alternatives
            }
            return emptyList()
        }
    }
}
