package io.github.emilmelgaard.pathwake.ring

import io.github.emilmelgaard.pathwake.settings.SolvingVolume
import java.time.Duration
import kotlin.math.min

/** The volume of a ringing alarm, relative to the alarm stream volume. */
object RingVolume {
    /** After this long without touching the puzzle, the alarm is back at full volume. */
    val SOLVING_TIMEOUT: Duration = Duration.ofSeconds(10)

    private const val LOWER = 0.25f

    /** During a phone call the alarm plays quietly, like the stock clock's in-call alarm. */
    private const val IN_CALL = 0.125f

    /**
     * @param sinceStart how long the alarm has been ringing.
     * @param crescendo how long it takes to reach full volume (zero: start at full volume).
     * @param sinceInteraction how long ago the puzzle was last touched; null if not yet.
     * @param inCall whether a phone call is ringing or active.
     */
    fun of(
        sinceStart: Duration,
        crescendo: Duration,
        solving: SolvingVolume,
        sinceInteraction: Duration?,
        inCall: Boolean = false
    ): Float {
        val ramp = if (crescendo.isZero || sinceStart >= crescendo) {
            1f
        } else {
            val progress = sinceStart.toMillis().coerceAtLeast(0).toFloat() / crescendo.toMillis()
            // Squared, so the volume rises evenly to the ear (loudness isn't linear in amplitude).
            progress * progress
        }
        val solvingNow = sinceInteraction != null && sinceInteraction < SOLVING_TIMEOUT
        val factor = when {
            !solvingNow -> 1f
            solving == SolvingVolume.FULL -> 1f
            solving == SolvingVolume.LOWER -> LOWER
            else -> 0f
        }
        val volume = ramp * factor
        return if (inCall) min(volume, IN_CALL) else volume
    }

    /** How long going from full volume to silence takes: quick, so touching the puzzle quiets the alarm at once. */
    val FADE_DOWN: Duration = Duration.ofMillis(300)

    /** How long going from silence to full volume takes: slow, so the alarm doesn't blast back. */
    val FADE_UP: Duration = Duration.ofSeconds(3)

    /** Moves the volume from [current] towards [target] for [elapsed] at the fade speeds. */
    fun fade(current: Float, target: Float, elapsed: Duration): Float {
        val millis = elapsed.toMillis().coerceAtLeast(0).toFloat()
        return if (target < current) {
            maxOf(target, current - millis / FADE_DOWN.toMillis())
        } else {
            minOf(target, current + millis / FADE_UP.toMillis())
        }
    }
}
