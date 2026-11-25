package io.github.emilmelgaard.pathwake

import android.app.Application
import android.content.Context
import io.github.emilmelgaard.pathwake.alarm.AlarmScheduler
import io.github.emilmelgaard.pathwake.data.AlarmRepository
import io.github.emilmelgaard.pathwake.data.PathwakeDatabase
import io.github.emilmelgaard.pathwake.data.RoomAlarmRepository
import io.github.emilmelgaard.pathwake.data.SchedulingAlarmRepository
import io.github.emilmelgaard.pathwake.ring.RingingController
import io.github.emilmelgaard.pathwake.system.AlarmNotifications
import io.github.emilmelgaard.pathwake.system.AndroidExactAlarms
import io.github.emilmelgaard.pathwake.system.AndroidRinger
import io.github.emilmelgaard.pathwake.system.AppPreferences
import io.github.emilmelgaard.pathwake.system.PreferencesRingingSessionStore
import io.github.emilmelgaard.pathwake.system.PreferencesSchedulerState
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manual dependency injection: app-wide singletons. Everything here must work before the device is first unlocked
 * after a reboot (direct boot), so it only uses device-protected storage.
 */
class AppContainer(context: Context) {
    private val database by lazy { PathwakeDatabase.create(context) }
    private val storedAlarms by lazy { RoomAlarmRepository(database.alarmDao()) }

    /** Current time in the current time zone (read on every call, so time zone changes are picked up). */
    val now: () -> ZonedDateTime = { ZonedDateTime.now() }

    /** For work that must outlive screens, e.g. finishing a broadcast. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val preferences by lazy { AppPreferences(context) }
    val notifications by lazy { AlarmNotifications(context) }
    val exactAlarms by lazy { AndroidExactAlarms(context) }
    val ringing by lazy { RingingController(PreferencesRingingSessionStore(context)) }
    val ringer by lazy { AndroidRinger(context, ringing, notifications) }
    val scheduler by lazy { AlarmScheduler(storedAlarms, exactAlarms, PreferencesSchedulerState(context), ringer, now) }

    /** Alarms for the UI. Changes reschedule the platform alarm. */
    val alarmRepository: AlarmRepository by lazy { SchedulingAlarmRepository(storedAlarms, scheduler) }
}

class PathwakeApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifications.createChannels()
        // Catch up if the platform alarm was lost, e.g. because the app was force-stopped.
        container.scope.launch { container.scheduler.sync() }
    }
}

val Context.appContainer: AppContainer get() = (applicationContext as PathwakeApplication).container
