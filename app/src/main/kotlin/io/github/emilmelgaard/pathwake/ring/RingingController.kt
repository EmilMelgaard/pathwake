package io.github.emilmelgaard.pathwake.ring

import io.github.emilmelgaard.pathwake.alarm.Alarm
import java.time.Instant
import java.time.ZonedDateTime
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Persists the ringing session, so a ringing alarm survives the app being killed or the device restarting. */
interface RingingSessionStore {
    fun load(): RingingSession?

    fun save(session: RingingSession?)
}

/** Holds the alarm that is ringing, if any. The ringing service and screen observe [session]. */
class RingingController(
    private val store: RingingSessionStore,
    private val newSeed: () -> Long = { Random.nextLong() }
) {
    private val _session = MutableStateFlow(store.load())
    val session: StateFlow<RingingSession?> = _session.asStateFlow()

    private val _lastInteraction = MutableStateFlow<Instant?>(null)

    /** When the puzzles were last touched (to lower the volume while solving); null if not yet. */
    val lastInteraction: StateFlow<Instant?> = _lastInteraction.asStateFlow()

    fun interacted(at: Instant = Instant.now()) {
        _lastInteraction.value = at
    }

    /** Starts ringing [alarms], or adds them to the alarm that is already ringing. They replace a test. */
    fun ring(alarms: List<Alarm>, at: ZonedDateTime) = update { current ->
        current?.takeUnless { it.test }?.join(alarms, at) ?: RingingSession.start(alarms, at, newSeed())
    }

    /** Rings [alarm] as a test (replacing another test), unless a real alarm is ringing. Returns whether it does. */
    fun test(alarm: Alarm, at: ZonedDateTime): Boolean {
        var started = false
        update { current ->
            if (current != null && !current.test) {
                current
            } else {
                started = true
                RingingSession.start(listOf(alarm), at, newSeed()).copy(test = true)
            }
        }
        return started
    }

    /** Stops a test; real alarms only stop when solved or silenced. */
    fun cancelTest() = update { current -> current?.takeUnless { it.test } }

    /**
     * Puzzle [puzzleNumber] was solved: moves on to the next puzzle, or stops ringing after the last one.
     * Ignored if that puzzle isn't the current one anymore.
     */
    fun puzzleSolved(puzzleNumber: Int) = update { current ->
        when {
            current == null || current.puzzleNumber != puzzleNumber -> current
            current.solved + 1 >= current.puzzleCount -> null
            else -> current.copy(solved = current.solved + 1, puzzleNumber = puzzleNumber + 1)
        }
    }

    /** Replaces puzzle [puzzleNumber] with a new one of the same difficulty. */
    fun newPuzzle(puzzleNumber: Int) = update { current ->
        if (current?.puzzleNumber == puzzleNumber) current.copy(puzzleNumber = puzzleNumber + 1) else current
    }

    /** Stops ringing without the puzzles being solved (auto-silence). */
    fun stop() = update { null }

    @Synchronized
    private fun update(transform: (RingingSession?) -> RingingSession?) {
        val current = _session.value
        val updated = transform(current)
        if (updated == current) return
        if (updated == null) _lastInteraction.value = null
        store.save(updated)
        _session.value = updated
    }
}
