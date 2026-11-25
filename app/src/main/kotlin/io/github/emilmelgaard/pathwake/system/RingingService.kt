package io.github.emilmelgaard.pathwake.system

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import io.github.emilmelgaard.pathwake.appContainer
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Rings the alarm in [io.github.emilmelgaard.pathwake.ring.RingingController.session] (sound and vibration) until the
 * session ends: when its puzzles are solved, or at its auto-silence time.
 */
class RingingService : Service() {
    private val scope = MainScope()
    private lateinit var sound: AlarmSound
    private lateinit var vibration: AlarmVibration
    private var ringing = false

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        sound = AlarmSound(this)
        vibration = AlarmVibration(this)
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
        if (!ringing) {
            ringing = true
            sound.start()
            vibration.start()
            scope.launch { followSession() }
        }
        return START_STICKY
    }

    private suspend fun followSession() {
        val container = appContainer
        container.ringing.session.collectLatest { session ->
            if (session == null) {
                stop()
                return@collectLatest
            }
            container.notifications.updateRinging(session)
            val silenceAt = session.silenceAt ?: awaitCancellation()
            delay(Duration.between(Instant.now(), silenceAt).toMillis().coerceAtLeast(0))
            container.notifications.showSilenced(session)
            container.ringing.stop()
        }
    }

    private fun stop() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        isRunning = false
        sound.stop()
        vibration.stop()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        /** Whether the service runs in this process (false after a crash or reboot cut off a ringing alarm). */
        @Volatile
        var isRunning = false
            private set
    }
}
