package io.github.emilmelgaard.pathwake.system

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import io.github.emilmelgaard.pathwake.alarm.AlarmSound
import io.github.emilmelgaard.pathwake.alarm.AutoSilence
import io.github.emilmelgaard.pathwake.appContainer
import io.github.emilmelgaard.pathwake.ring.RingVolume
import io.github.emilmelgaard.pathwake.ring.RingingSession
import io.github.emilmelgaard.pathwake.settings.Settings
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Rings the alarm in [io.github.emilmelgaard.pathwake.ring.RingingController.session] (sound and vibration) until the
 * session ends: when its puzzles are solved, or when it silences itself.
 */
class RingingService : Service() {
    private val scope = MainScope()
    private lateinit var player: AlarmPlayer
    private lateinit var vibration: AlarmVibration
    private lateinit var audioManager: AudioManager
    private var ringing: Job? = null

    /** What is playing; ringing restarts when it changes (another alarm joined a silent one). */
    private data class Playback(val sound: AlarmSound, val vibrate: Boolean, val once: Boolean)

    private var playback: Playback? = null

    /** When this service started ringing; the volume ramp starts here (also after a reboot). */
    private var since: Instant = Instant.now()

    /** When the sound ended or, if it can't end by itself, when the alarm silences anyway. */
    private var soundEndedAt: Instant? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        player = AlarmPlayer(this)
        vibration = AlarmVibration(this)
        audioManager = getSystemService(AudioManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = appContainer
        val session = container.ringing.session.value
        // A service started with startForegroundService() must always go to the foreground, even to stop right away.
        ServiceCompat.startForeground(
            this,
            AlarmNotifications.RINGING_ID,
            container.notifications.ringing(session),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
            } else {
                0
            }
        )
        if (session == null) {
            stop()
            return START_NOT_STICKY
        }
        if (ringing?.isActive != true) ringing = scope.launch { ring() }
        return START_STICKY
    }

    private suspend fun ring() {
        val container = appContainer
        val settings = container.settings.get()
        val streamFloor = StreamFloor()
        var shown: RingingSession? = null
        var wasInCall = false
        since = Instant.now()
        while (true) {
            val session = container.ringing.session.value ?: break
            val wanted = Playback(session.sound, session.vibrate, session.stopWhenSoundEnds)
            if (wanted != playback) start(wanted)
            if (session != shown) {
                container.notifications.updateRinging(session)
                shown = session
            }
            val now = Instant.now()
            val silenceAt = listOfNotNull(session.silenceAt, soundEndedAt).minOrNull()
            if (silenceAt != null && !now.isBefore(silenceAt)) {
                container.notifications.showSilenced(session, Duration.between(session.startedAt, now))
                container.ringing.stop()
                break
            }
            val inCall = audioManager.mode in CALL_MODES
            // The call had audio focus, so music and other media may come back over the alarm.
            if (wasInCall && !inCall) player.regainFocus()
            wasInCall = inCall
            player.setVolume(volume(settings, now, inCall))
            if (settings.lockVolumeButtons) streamFloor.keep()
            delay(TICK_MILLIS)
        }
        stop()
    }

    private fun start(wanted: Playback) {
        player.stop()
        vibration.stop()
        playback = wanted
        soundEndedAt = null
        if (wanted.vibrate) vibration.start()
        val sources = appContainer.soundLibrary.sources(wanted.sound)
        // "When the sound ends" needs the chosen sound; fallback sounds loop until the default auto-silence time.
        if (wanted.once && sources.chosen == null) fallBackToDefaultSilence()
        play(wanted, sources.all, 0, once = wanted.once && sources.chosen != null)
    }

    private fun play(wanted: Playback, sources: List<SoundSource>, index: Int, once: Boolean) {
        if (index >= sources.size) return
        player.play(
            sources[index],
            loop = !once,
            onEnded = { if (playback == wanted) soundEndedAt = Instant.now() },
            onError = {
                if (playback == wanted) {
                    if (once) fallBackToDefaultSilence()
                    play(wanted, sources, index + 1, once = false)
                }
            }
        )
    }

    private fun fallBackToDefaultSilence() {
        val minutes = (AutoSilence.DEFAULT as AutoSilence.After).minutes.toLong()
        soundEndedAt = since.plus(Duration.ofMinutes(minutes))
    }

    private fun volume(settings: Settings, now: Instant, inCall: Boolean): Float = RingVolume.of(
        sinceStart = Duration.between(since, now),
        crescendo = Duration.ofSeconds(settings.crescendoSeconds.toLong()),
        solving = settings.solvingVolume,
        sinceInteraction = appContainer.ringing.lastInteraction.value?.let { Duration.between(it, now) },
        inCall = inCall
    )

    /** Keeps the alarm stream from being turned down while ringing (it may be turned up). */
    private inner class StreamFloor {
        private var floor = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)

        fun keep() {
            val level = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            if (level > floor) {
                floor = level
            } else if (level < floor) {
                runCatching { audioManager.setStreamVolume(AudioManager.STREAM_ALARM, floor, 0) }
            }
        }
    }

    private fun stop() {
        player.stop()
        vibration.stop()
        playback = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        isRunning = false
        player.stop()
        vibration.stop()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TICK_MILLIS = 200L

        /** Audio modes of an incoming, ongoing (phone) or VoIP call; none need a permission to read. */
        private val CALL_MODES = setOf(
            AudioManager.MODE_RINGTONE,
            AudioManager.MODE_IN_CALL,
            AudioManager.MODE_IN_COMMUNICATION
        )

        /** Whether the service runs in this process (false after a crash or reboot cut off a ringing alarm). */
        @Volatile
        var isRunning = false
            private set
    }
}
