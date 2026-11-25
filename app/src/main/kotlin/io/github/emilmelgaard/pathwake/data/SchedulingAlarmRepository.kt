package io.github.emilmelgaard.pathwake.data

import io.github.emilmelgaard.pathwake.alarm.Alarm
import io.github.emilmelgaard.pathwake.alarm.AlarmScheduler

/** Updates the platform alarm after every change, so the next alarm always rings. */
class SchedulingAlarmRepository(private val delegate: AlarmRepository, private val scheduler: AlarmScheduler) :
    AlarmRepository by delegate {
    override suspend fun save(alarm: Alarm): Long = delegate.save(alarm).also { scheduler.reschedule() }

    override suspend fun delete(id: Long) {
        delegate.delete(id)
        scheduler.reschedule()
    }

    override suspend fun restore(alarm: Alarm) {
        delegate.restore(alarm)
        scheduler.reschedule()
    }
}
