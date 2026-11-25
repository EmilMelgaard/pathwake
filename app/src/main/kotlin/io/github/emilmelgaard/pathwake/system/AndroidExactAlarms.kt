package io.github.emilmelgaard.pathwake.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.emilmelgaard.pathwake.MainActivity
import io.github.emilmelgaard.pathwake.alarm.ExactAlarms
import java.time.Instant

/**
 * Uses [AlarmManager.setAlarmClock]: it is exact, fires in Doze, shows the alarm icon in the status bar, and lets the
 * app start its ringing service from the background.
 */
class AndroidExactAlarms(private val context: Context) : ExactAlarms {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(at: Instant) = set(at, ringIntent(REQUEST_SCHEDULED, at))

    override fun cancel() = alarmManager.cancel(ringIntent(REQUEST_SCHEDULED, Instant.EPOCH))

    override fun ringLate(at: Instant) = set(Instant.now(), ringIntent(REQUEST_LATE, at))

    override fun resumeRinging() = set(Instant.now(), receiverIntent(REQUEST_RESUME, AlarmReceiver.ACTION_RESUME))

    /** False if the user revoked "Alarms & reminders" (Android 12 and 13 only; later versions always allow it). */
    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun set(at: Instant, operation: PendingIntent) {
        val showApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        if (canScheduleExact()) {
            try {
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(at.toEpochMilli(), showApp), operation)
                return
            } catch (_: SecurityException) {
                // The permission was revoked in the meantime: fall back to an inexact alarm below.
            }
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), operation)
    }

    private fun ringIntent(requestCode: Int, at: Instant) = receiverIntent(requestCode, AlarmReceiver.ACTION_RING) {
        putExtra(AlarmReceiver.EXTRA_AT, at.toEpochMilli())
    }

    private fun receiverIntent(requestCode: Int, action: String, extras: Intent.() -> Unit = {}): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, AlarmReceiver::class.java).setAction(action).apply(extras),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private companion object {
        const val REQUEST_SCHEDULED = 0
        const val REQUEST_LATE = 1
        const val REQUEST_RESUME = 2
    }
}
