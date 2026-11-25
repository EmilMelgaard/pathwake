package io.github.emilmelgaard.pathwake.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.emilmelgaard.pathwake.appContainer
import java.time.Instant
import kotlinx.coroutines.launch

/** Receives the platform alarms set by [AndroidExactAlarms]. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = context.appContainer
        val result = goAsync()
        container.scope.launch {
            try {
                when (intent.action) {
                    ACTION_RING -> container.scheduler.onAlarmTime(
                        Instant.ofEpochMilli(intent.getLongExtra(EXTRA_AT, 0))
                    )

                    ACTION_RESUME -> container.ringer.resume()
                }
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        const val ACTION_RING = "io.github.emilmelgaard.pathwake.action.RING"
        const val ACTION_RESUME = "io.github.emilmelgaard.pathwake.action.RESUME"
        const val EXTRA_AT = "at"
    }
}

/**
 * Reschedules after events that clear or shift platform alarms: (locked) boot, clock and time zone changes, app
 * updates and the exact-alarm permission being granted. Also catches up on alarms missed in the meantime.
 */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val container = context.appContainer
        val result = goAsync()
        container.scope.launch {
            try {
                container.scheduler.sync()
            } finally {
                result.finish()
            }
        }
    }

    private companion object {
        val ACTIONS = setOf(
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            // AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED (API 31; only sent from then on).
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        )
    }
}
