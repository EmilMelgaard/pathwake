package io.github.emilmelgaard.pathwake.ring

import io.github.emilmelgaard.pathwake.settings.SolvingVolume
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Test

class RingVolumeTest {
    private val none = Duration.ZERO
    private fun seconds(s: Long) = Duration.ofSeconds(s)

    @Test
    fun withoutCrescendo_startsAtFullVolume() {
        assertEquals(1f, RingVolume.of(none, none, SolvingVolume.LOWER, null))
    }

    @Test
    fun crescendo_risesToFullVolume() {
        val crescendo = seconds(60)
        assertEquals(0f, RingVolume.of(none, crescendo, SolvingVolume.FULL, null))
        assertEquals(0.25f, RingVolume.of(seconds(30), crescendo, SolvingVolume.FULL, null), 0.001f)
        assertEquals(1f, RingVolume.of(seconds(60), crescendo, SolvingVolume.FULL, null))
        assertEquals(1f, RingVolume.of(seconds(600), crescendo, SolvingVolume.FULL, null))
    }

    @Test
    fun whileSolving_isLowerOrMutedUntilTenSecondsWithoutTouching() {
        assertEquals(0.25f, RingVolume.of(seconds(100), none, SolvingVolume.LOWER, seconds(2)))
        assertEquals(0f, RingVolume.of(seconds(100), none, SolvingVolume.MUTE, seconds(9)))
        assertEquals(1f, RingVolume.of(seconds(100), none, SolvingVolume.FULL, seconds(2)))
        assertEquals(1f, RingVolume.of(seconds(100), none, SolvingVolume.MUTE, seconds(10)))
    }

    @Test
    fun duringACall_isQuietButNeverLouderThanOtherwise() {
        assertEquals(0.125f, RingVolume.of(seconds(100), none, SolvingVolume.LOWER, null, inCall = true))
        assertEquals(0f, RingVolume.of(seconds(100), none, SolvingVolume.MUTE, seconds(2), inCall = true))
        assertEquals(0f, RingVolume.of(none, seconds(60), SolvingVolume.FULL, null, inCall = true))
    }
}
