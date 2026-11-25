package io.github.emilmelgaard.pathwake.system

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import io.github.emilmelgaard.pathwake.R

/**
 * Plays the system's default alarm sound in a loop on the alarm stream. Falls back to a bundled tone if that sound
 * can't be played (e.g. before the device is first unlocked after a reboot, when shared storage isn't available).
 */
class AlarmSound(private val context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null

    fun start() {
        if (player != null) return
        // Pauses music and other media while the alarm rings.
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { }
            .build()
            .also { audioManager.requestAudioFocus(it) }
        val defaultSound = runCatching {
            RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
        }
            .getOrNull()
        play(defaultSound)
    }

    fun stop() {
        player?.release()
        player = null
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    /** Plays [uri], or the bundled tone if it is null or fails. */
    private fun play(uri: Uri?) {
        val player = MediaPlayer()
        this.player = player
        try {
            player.setAudioAttributes(attributes)
            player.isLooping = true
            // Keeps the CPU awake while ringing, so auto-silence and vibration keep working with the screen off.
            player.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
            if (uri != null) {
                player.setDataSource(context, uri)
            } else {
                context.resources.openRawResourceFd(R.raw.alarm_fallback).use { player.setDataSource(it) }
            }
            player.setOnPreparedListener { it.start() }
            player.setOnErrorListener { _, what, extra ->
                Log.w(TAG, "Can't play $uri ($what, $extra)")
                if (uri != null && this.player === player) {
                    player.release()
                    play(null)
                }
                true
            }
            player.prepareAsync()
        } catch (e: Exception) {
            Log.w(TAG, "Can't play $uri", e)
            player.release()
            this.player = null
            if (uri != null) play(null)
        }
    }

    private companion object {
        const val TAG = "AlarmSound"
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
