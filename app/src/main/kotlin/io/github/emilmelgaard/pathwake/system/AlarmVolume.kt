package io.github.emilmelgaard.pathwake.system

import android.content.Context
import android.media.AudioManager

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
}
