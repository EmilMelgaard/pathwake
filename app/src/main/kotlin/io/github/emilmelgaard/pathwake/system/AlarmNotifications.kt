package io.github.emilmelgaard.pathwake.system

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.emilmelgaard.pathwake.MainActivity
import io.github.emilmelgaard.pathwake.R
import io.github.emilmelgaard.pathwake.alarm.Occurrence
import io.github.emilmelgaard.pathwake.ring.RingingSession
import io.github.emilmelgaard.pathwake.ui.common.formatTime
import io.github.emilmelgaard.pathwake.ui.ringing.RingingActivity
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.format.TextStyle

class AlarmNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannels() {
        // The ringing service plays the sound and vibrates itself, so the channel stays silent.
        val ringing = NotificationChannel(
            CHANNEL_RINGING,
            context.getString(R.string.channel_ringing),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val missed = NotificationChannel(
            CHANNEL_MISSED,
            context.getString(R.string.channel_missed),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannels(listOf(ringing, missed))
    }

    /** Whether the ringing alarm can show its notification (and, through it, the puzzle screen). */
    fun canShowRinging(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        manager.getNotificationChannel(CHANNEL_RINGING)?.importance != NotificationManager.IMPORTANCE_NONE

    /** Whether the puzzle screen can open by itself over the lock screen (Android 14+ can revoke this). */
    fun canUseFullScreen(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || manager.canUseFullScreenIntent()

    /** The ringing service's notification. It opens the puzzle screen, full screen if the device is locked. */
    fun ringing(session: RingingSession?): Notification {
        val openPuzzles = PendingIntent.getActivity(
            context,
            0,
            RingingActivity.intent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = when {
            session == null -> null

            session.solved == 0 -> context.resources.getQuantityString(
                R.plurals.ringing_solve_to_stop,
                session.puzzleCount,
                session.puzzleCount
            )

            else -> context.getString(R.string.ringing_progress, session.solved + 1, session.puzzleCount)
        }
        return NotificationCompat.Builder(context, CHANNEL_RINGING)
            .setSmallIcon(R.drawable.ic_puzzle)
            .setContentTitle(session?.let { title(it.alarmTime, it.labels) } ?: context.getString(R.string.alarm))
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            // Progress updates must not pop up again over the puzzle.
            .setOnlyAlertOnce(true)
            .setContentIntent(openPuzzles)
            .setFullScreenIntent(openPuzzles, true)
            .addAction(0, context.getString(R.string.solve_puzzles), openPuzzles)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun updateRinging(session: RingingSession) = notify(RINGING_ID, ringing(session))

    fun showMissed(occurrences: List<Occurrence>) = occurrences.forEach { occurrence ->
        val day = occurrence.at.dayOfWeek.getDisplayName(TextStyle.SHORT, context.resources.configuration.locales[0])
        val labels = occurrence.alarms.map { it.label.trim() }.filter { it.isNotEmpty() }
        val text = "$day ${title(occurrence.at.toLocalTime(), labels)}"
        notify(missedId(occurrence.at.toInstant()), missed(text))
    }

    /** [session] stopped ringing by itself after ringing for [duration]. */
    fun showSilenced(session: RingingSession, duration: Duration) {
        // Short sounds that only play once stop within a minute.
        val seconds = duration.seconds.coerceAtLeast(1).toInt()
        val (plural, count) = if (seconds < 60) {
            R.plurals.missed_silenced_seconds to seconds
        } else {
            R.plurals.missed_silenced to (seconds + 30) / 60
        }
        val text = context.resources.getQuantityString(plural, count, title(session.alarmTime, session.labels), count)
        notify(missedId(session.silenceAt ?: Instant.now()), missed(text))
    }

    private fun missed(text: String): Notification = NotificationCompat.Builder(context, CHANNEL_MISSED)
        .setSmallIcon(R.drawable.ic_puzzle)
        .setContentTitle(context.getString(R.string.missed_alarm))
        .setContentText(text)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setAutoCancel(true)
        .setContentIntent(
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        )
        .build()

    /** "07:00 · Work". */
    private fun title(time: LocalTime, labels: List<String>): String {
        val timeText = context.formatTime(time)
        return if (labels.isEmpty()) {
            timeText
        } else {
            context.getString(
                R.string.time_and_label,
                timeText,
                labels.joinToString()
            )
        }
    }

    private fun notify(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun missedId(at: Instant) = MISSED_ID_BASE + (at.epochSecond / 60 % 1_000_000).toInt()

    companion object {
        const val CHANNEL_RINGING = "ringing"
        const val CHANNEL_MISSED = "missed"
        const val RINGING_ID = 1
        private const val MISSED_ID_BASE = 1000
    }
}
