package io.github.emilmelgaard.pathwake.system

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import io.github.emilmelgaard.pathwake.alarm.Alarm
import io.github.emilmelgaard.pathwake.alarm.Occurrence
import io.github.emilmelgaard.pathwake.alarm.Ringer
import io.github.emilmelgaard.pathwake.ring.RingingController
import java.time.Duration
import java.time.ZonedDateTime

class AndroidRinger(
    private val context: Context,
    private val controller: RingingController,
    private val notifications: AlarmNotifications
) : Ringer {
    override fun ring(alarms: List<Alarm>, at: ZonedDateTime) {
        controller.ring(alarms, at)
        startService()
    }

    /** Rings the stored session again, e.g. after a reboot. Must run from a platform alarm (see [ExactAlarms]). */
    fun resume() {
        if (controller.session.value != null) startService()
    }

    /**
     * Rings [alarm] as a test, unless a real alarm is ringing. Must run while the app is in the foreground, which
     * also opens the puzzles (the notification only pops up over an unlocked screen). Returns whether it rings.
     */
    fun test(alarm: Alarm, at: ZonedDateTime): Boolean {
        if (!controller.test(alarm, at)) return false
        startService()
        return true
    }

    override fun isInterrupted(now: ZonedDateTime): Boolean {
        val session = controller.session.value ?: return false
        if (RingingService.isRunning) return false
        // A test cut off by a restart is over.
        if (session.test) {
            controller.cancelTest()
            return false
        }
        val silenceAt = session.silenceAt
        if (silenceAt != null && !silenceAt.isAfter(now.toInstant())) {
            notifications.showSilenced(session, Duration.between(session.startedAt, silenceAt))
            controller.stop()
            return false
        }
        return true
    }

    override fun reportMissed(occurrences: List<Occurrence>) = notifications.showMissed(occurrences)

    private fun startService() =
        ContextCompat.startForegroundService(context, Intent(context, RingingService::class.java))
}
