package io.github.emilmelgaard.pathwake

import android.app.Application
import android.content.Context
import io.github.emilmelgaard.pathwake.data.AlarmRepository
import io.github.emilmelgaard.pathwake.data.PathwakeDatabase
import io.github.emilmelgaard.pathwake.data.RoomAlarmRepository
import java.time.ZonedDateTime

/** Manual dependency injection: app-wide singletons. */
class AppContainer(context: Context) {
    private val database by lazy { PathwakeDatabase.create(context) }

    val alarmRepository: AlarmRepository by lazy { RoomAlarmRepository(database.alarmDao()) }

    /** Current time in the current time zone (read on every call, so time zone changes are picked up). */
    val now: () -> ZonedDateTime = { ZonedDateTime.now() }
}

class PathwakeApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.appContainer: AppContainer get() = (applicationContext as PathwakeApplication).container
