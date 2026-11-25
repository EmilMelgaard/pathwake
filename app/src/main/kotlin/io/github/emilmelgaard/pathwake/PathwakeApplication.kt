package io.github.emilmelgaard.pathwake

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import io.github.emilmelgaard.pathwake.alarm.Alarm
import io.github.emilmelgaard.pathwake.alarm.AlarmScheduler
import io.github.emilmelgaard.pathwake.alarm.AlarmSound
import io.github.emilmelgaard.pathwake.data.AlarmRepository
import io.github.emilmelgaard.pathwake.data.PathwakeDatabase
import io.github.emilmelgaard.pathwake.data.RoomAlarmRepository
import io.github.emilmelgaard.pathwake.data.SchedulingAlarmRepository
import io.github.emilmelgaard.pathwake.ring.RingingController
import io.github.emilmelgaard.pathwake.settings.SettingsRepository
import io.github.emilmelgaard.pathwake.system.AlarmNotifications
import io.github.emilmelgaard.pathwake.system.AndroidExactAlarms
import io.github.emilmelgaard.pathwake.system.AndroidRinger
import io.github.emilmelgaard.pathwake.system.AppPreferences
import io.github.emilmelgaard.pathwake.system.PreferencesRingingSessionStore
import io.github.emilmelgaard.pathwake.system.PreferencesSchedulerState
import io.github.emilmelgaard.pathwake.system.SoundLibrary
import java.io.File
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
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
    val settings by lazy {
        // Not preferencesDataStoreFile(): it resolves against the application context, which isn't device protected.
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(context.createDeviceProtectedStorageContext().filesDir, "datastore/settings.preferences_pb")
        }
        SettingsRepository(dataStore, scope)
    }
    val soundLibrary by lazy { SoundLibrary(context) }
    val notifications by lazy { AlarmNotifications(context) }
    val exactAlarms by lazy { AndroidExactAlarms(context) }
    val ringing by lazy { RingingController(PreferencesRingingSessionStore(context)) }
    val ringer by lazy { AndroidRinger(context, ringing, notifications) }
    val scheduler by lazy { AlarmScheduler(storedAlarms, exactAlarms, PreferencesSchedulerState(context), ringer, now) }

    /** Alarms for the UI. Changes reschedule the platform alarm. */
    val alarmRepository: AlarmRepository by lazy { SchedulingAlarmRepository(storedAlarms, scheduler) }

    /** Rings [alarm] now to try it out, unless a real alarm is ringing; open the ringing screen after this. */
    fun testAlarm(alarm: Alarm): Boolean = ringer.test(alarm, now())

    /** Removes a sound file from the library; alarms (and the default for new alarms) that used it play the default. */
    suspend fun deleteSound(sound: AlarmSound.Custom) {
        soundLibrary.delete(sound)
        useDefaultSoundFor { it == sound }
    }

    /** Alarms restored from a backup may use sound files that weren't in it; they play the default sound instead. */
    suspend fun forgetMissingSounds() = useDefaultSoundFor { it is AlarmSound.Custom && soundLibrary.file(it) == null }

    private suspend fun useDefaultSoundFor(replace: (AlarmSound) -> Boolean) {
        alarmRepository.alarms.first().filter { replace(it.sound) }.forEach {
            alarmRepository.save(it.copy(sound = AlarmSound.Default))
        }
        settings.update { s ->
            val defaults = s.alarmDefaults
            if (replace(defaults.sound)) s.copy(alarmDefaults = defaults.copy(sound = AlarmSound.Default)) else s
        }
    }
}

class PathwakeApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifications.createChannels()
        // Load the settings right away, so the first screen has the right theme.
        container.settings.current
        // Catch up if the platform alarm was lost, e.g. because the app was force-stopped.
        container.scope.launch { container.scheduler.sync() }
    }
}

val Context.appContainer: AppContainer get() = (applicationContext as PathwakeApplication).container
