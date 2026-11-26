package io.github.emilmelgaard.pathwake.system

import android.content.Context
import io.github.emilmelgaard.pathwake.alarm.AlarmSound

/**
 * Plays an alarm's sound at an alarm volume level to try it, like the alarm will ring. That level is set on the
 * device's alarm volume while playing; [stop] puts the device's volume back.
 */
class VolumePreview(context: Context, private val library: SoundLibrary) {
    private val alarmVolume = AlarmVolume(context)
    private val player = AlarmPlayer(context, withWakeLock = false)
    private var levelToRestore: Int? = null

    fun play(sound: AlarmSound, level: Int) {
        if (levelToRestore == null) levelToRestore = alarmVolume.level
        alarmVolume.level = level
        // A silent alarm has no sound to try: the default sound shows how loud the level is.
        val source = library.sources(sound).all.firstOrNull() ?: library.sources(AlarmSound.Default).all.first()
        player.play(source, loop = true)
    }

    /** Changes the level while playing. */
    fun setLevel(level: Int) {
        if (levelToRestore != null) alarmVolume.level = level
    }

    fun stop() {
        player.stop()
        levelToRestore?.let { alarmVolume.level = it }
        levelToRestore = null
    }
}
