package io.github.emilmelgaard.pathwake.ring

import io.github.emilmelgaard.pathwake.settings.SolvingVolume
import java.time.Duration

/** The volume of a ringing alarm, relative to the alarm stream volume. */
object RingVolume {
    /** After this long without touching the puzzle, the alarm is back at full volume. */
    val SOLVING_TIMEOUT: Duration = Duration.ofSeconds(10)

    private const val LOWER = 0.25f

    /**
     * @param sinceStart how long the alarm has been ringing.
     * @param crescendo how long it takes to reach full volume (zero: start at full volume).
     * @param sinceInteraction how long ago the puzzle was last touched; null if not yet.
     */
    fun of(sinceStart: Duration, crescendo: Duration, solving: SolvingVolume, sinceInteraction: Duration?): Float {
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
        return ramp * factor
    }
}
