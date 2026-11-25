package io.github.emilmelgaard.pathwake.system

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import io.github.emilmelgaard.pathwake.R
import java.io.File

/** Something [AlarmPlayer] can play. */
sealed interface SoundSource {
    data class Content(val uri: Uri) : SoundSource

    data class LocalFile(val file: File) : SoundSource

    /** The tone bundled with the app; it can always be played, even before the first unlock after a reboot. */
    data object Bundled : SoundSource
}

/** Plays alarm sounds on the alarm stream, with audio focus so music and other media pause. */
class AlarmPlayer(private val context: Context, private val withWakeLock: Boolean = true) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private var hasFocus = false
    private var volume = 1f

    /**
     * Plays [source], looping it if [loop]. [onEnded] is called when a sound that doesn't loop has played once,
     * [onError] if it can't be played. Both run on the main thread; neither is called after [stop].
     */
    fun play(source: SoundSource, loop: Boolean, onEnded: () -> Unit = {}, onError: () -> Unit = {}) {
        stopPlayer()
        if (focusRequest == null) {
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(ATTRIBUTES)
                .setOnAudioFocusChangeListener { change -> hasFocus = change == AudioManager.AUDIOFOCUS_GAIN }
                .build()
            requestFocus()
        }
        val player = MediaPlayer()
        this.player = player
        try {
            player.setAudioAttributes(ATTRIBUTES)
            player.isLooping = loop
            // Keeps the CPU awake while ringing, so auto-silence and vibration keep working with the screen off.
            if (withWakeLock) player.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            when (source) {
                is SoundSource.Content -> player.setDataSource(context, source.uri)

                is SoundSource.LocalFile -> player.setDataSource(source.file.path)

                SoundSource.Bundled -> context.resources.openRawResourceFd(R.raw.alarm_fallback).use {
                    player.setDataSource(it)
                }
            }
            player.setVolume(volume, volume)
            player.setOnPreparedListener { it.start() }
            player.setOnCompletionListener { if (this.player === player) onEnded() }
            player.setOnErrorListener { _, what, extra ->
                Log.w(TAG, "Can't play $source ($what, $extra)")
                if (this.player === player) {
                    stopPlayer()
                    onError()
                }
                true
            }
            player.prepareAsync()
        } catch (e: Exception) {
            Log.w(TAG, "Can't play $source", e)
            stopPlayer()
            onError()
        }
    }

    /** Sets the volume relative to the alarm stream volume (0 to 1). */
    fun setVolume(volume: Float) {
        if (volume == this.volume) return
        this.volume = volume
        player?.setVolume(volume, volume)
    }

    /** Asks for audio focus again if it was refused or lost, e.g. because a phone call had it. */
    fun regainFocus() {
        if (!hasFocus) requestFocus()
    }

    private fun requestFocus() {
        focusRequest?.let { hasFocus = audioManager.requestAudioFocus(it) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED }
    }

    fun stop() {
        stopPlayer()
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
        hasFocus = false
    }

    private fun stopPlayer() {
        player?.release()
        player = null
    }

    private companion object {
        const val TAG = "AlarmPlayer"
        val ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }
}

/** Vibrates in a repeating pattern, as an alarm (so it works in Do Not Disturb modes that allow alarms). */
class AlarmVibration(context: Context) {
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        context.getSystemService(Vibrator::class.java)
    }

    fun start() {
        if (!vibrator.hasVibrator()) return
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 700, 700), 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION") // The replacement (VibrationAttributes) needs API 33.
            vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
    }

    fun stop() = vibrator.cancel()
}
