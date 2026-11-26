package io.github.emilmelgaard.pathwake.system

import android.content.Context
import android.media.AudioManager
import kotlin.math.roundToInt

/** The device's alarm volume (the alarm stream), which Pathwake's alarms ring at. */
class AlarmVolume(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    val min: Int get() = audioManager.getStreamMinVolume(AudioManager.STREAM_ALARM)
    val max: Int get() = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)

    var level: Int
        get() = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        set(value) = runCatching {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, value.coerceIn(min, max), 0)
        }.let { }

    val isMuted: Boolean get() = level == 0

    /** The level for an alarm's own volume ([io.github.emilmelgaard.pathwake.alarm.Alarm.volume]). */
    fun levelOf(percent: Int): Int = levelOf(percent, min, max)

    fun percentOf(level: Int): Int = percentOf(level, min, max)

    companion object {
        // Alarms store percentages rather than levels, because devices have different numbers of volume steps.
        fun levelOf(percent: Int, min: Int, max: Int): Int =
            if (max <= min) min else min + (percent.coerceIn(0, 100) * (max - min) / 100f).roundToInt()

        fun percentOf(level: Int, min: Int, max: Int): Int =
            if (max <= min) 100 else ((level.coerceIn(min, max) - min) * 100f / (max - min)).roundToInt()
    }
}
