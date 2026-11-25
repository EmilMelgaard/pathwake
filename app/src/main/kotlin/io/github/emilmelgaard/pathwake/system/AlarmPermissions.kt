package io.github.emilmelgaard.pathwake.system

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

/** Permissions and settings that alarms need to ring and show their puzzles. */
enum class AlarmPermission {
    /** Without notifications, a ringing alarm can't show its puzzle screen. */
    NOTIFICATIONS,

    /** "Alarms & reminders" (only revocable on Android 12 and 13): without it, alarms may ring late. */
    EXACT_ALARMS,

    /** Android 14+: without it, the puzzle screen doesn't open by itself when the device is locked. */
    FULL_SCREEN
}

object AlarmPermissions {
    fun missing(notifications: AlarmNotifications, exactAlarms: AndroidExactAlarms) = buildList {
        if (!notifications.canShowRinging()) add(AlarmPermission.NOTIFICATIONS)
        if (!exactAlarms.canScheduleExact()) add(AlarmPermission.EXACT_ALARMS)
        if (!notifications.canUseFullScreen()) add(AlarmPermission.FULL_SCREEN)
    }

    /** Whether the notification permission can be asked for in a dialog (instead of in system settings). */
    fun needsNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    /** Whether Android would still show the notification permission dialog after it was refused once. */
    fun shouldShowNotificationRationale(activity: Activity?): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true

    fun requestNotifications(launcher: ActivityResultLauncher<String>) {
        if (Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** The system settings screen where the user can grant [permission]. */
    fun settingsIntent(context: Context, permission: AlarmPermission): Intent {
        val packageUri = "package:${context.packageName}".toUri()
        if (permission == AlarmPermission.EXACT_ALARMS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri)
        }
        if (permission == AlarmPermission.FULL_SCREEN &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        ) {
            return Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri)
        }
        return Intent(
            Settings.ACTION_APP_NOTIFICATION_SETTINGS
        ).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }
}
