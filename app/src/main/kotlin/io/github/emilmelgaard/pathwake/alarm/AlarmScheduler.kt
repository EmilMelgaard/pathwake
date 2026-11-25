package io.github.emilmelgaard.pathwake.alarm

import io.github.emilmelgaard.pathwake.data.AlarmRepository
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The platform alarm that wakes the app. */
interface ExactAlarms {
    /** Wakes the app at [at] to ring the alarms due then. Replaces the previous [schedule]. */
    fun schedule(at: Instant)

    fun cancel()

    /** Wakes the app right away to ring the alarms that were due at [at]. */
    fun ringLate(at: Instant)

    /** Wakes the app right away to resume an alarm that was cut off while ringing. */
    fun resumeRinging()
}

/** What the scheduler remembers between runs of the app. */
interface SchedulerState {
    /** The alarm time the platform alarm is set for. */
    var scheduled: Instant?

    /** The latest alarm time that has rung or was reported missed. */
    var handled: Instant?
}

/** Rings alarms and tells the user about missed ones. */
interface Ringer {
    /** Starts ringing [alarms], due at [at], or adds them to the alarm that is already ringing. */
    fun ring(alarms: List<Alarm>, at: ZonedDateTime)

    /**
     * Whether an alarm was cut off while ringing (by a reboot or a crash) and should ring again. One whose
     * auto-silence time has passed is reported missed instead.
     */
    fun isInterrupted(now: ZonedDateTime): Boolean

    fun reportMissed(occurrences: List<Occurrence>)
}

/**
 * Keeps the platform alarm set for the next alarm time, rings alarms when it fires and catches up after times the
 * app couldn't ring (device off, clock changed, app stopped). Like the stock Android clock, an alarm that is more
 * than [LATE_LIMIT] late when the app catches up is reported missed instead of ringing.
 */
class AlarmScheduler(
    private val repository: AlarmRepository,
    private val exactAlarms: ExactAlarms,
    private val state: SchedulerState,
    private val ringer: Ringer,
    private val now: () -> ZonedDateTime
) {
    private val mutex = Mutex()

    /** Sets the platform alarm for the next alarm time. Call after alarms change. */
    suspend fun reschedule() = mutex.withLock { rescheduleLocked(now()) }

    /** The platform alarm fired for alarm time [at]: rings the alarms due then, unless that was already handled. */
    suspend fun onAlarmTime(at: Instant) = mutex.withLock {
        val now = now()
        if (!isHandled(at)) {
            state.handled = at
            val time = at.atZone(now.zone)
            val due = AlarmSchedule.dueAt(repository.alarms.first(), time)
            if (due.isNotEmpty()) ringer.ring(due, time)
            due.forEach { repository.save(AlarmSchedule.afterRinging(it, time)) }
        }
        rescheduleLocked(now)
    }

    /**
     * Catches up after the app may have missed alarm times: after a reboot, a clock or time zone change, an app
     * update, or when the app starts. Also resumes an alarm that was cut off while ringing.
     */
    suspend fun sync() = mutex.withLock {
        val now = now()
        val scheduled = state.scheduled
        if (scheduled != null && !scheduled.isAfter(now.toInstant()) && !isHandled(scheduled)) {
            catchUp(scheduled.atZone(now.zone), now)
        }
        if (ringer.isInterrupted(now)) exactAlarms.resumeRinging()
        rescheduleLocked(now)
    }

    private suspend fun catchUp(from: ZonedDateTime, now: ZonedDateTime) {
        val alarms = repository.alarms.first()
        val occurrences = AlarmSchedule.occurrencesBetween(alarms, from, now)
        val ringNow = occurrences.lastOrNull()?.takeIf { Duration.between(it.at, now) <= LATE_LIMIT }
        val missed = if (ringNow == null) occurrences else occurrences.dropLast(1)
        if (missed.isNotEmpty()) {
            ringer.reportMissed(missed.filter { Duration.between(it.at, now) <= MISSED_REPORT_LIMIT })
            val updated = alarms.associateBy { it.id }.toMutableMap()
            for (occurrence in missed) {
                for (alarm in occurrence.alarms) {
                    updated[alarm.id] = AlarmSchedule.afterRinging(updated.getValue(alarm.id), occurrence.at)
                }
            }
            alarms.forEach { if (updated[it.id] != it) repository.save(updated.getValue(it.id)) }
            state.handled = missed.last().at.toInstant()
        }
        // Ringing goes through the platform alarm, which allows starting the ringing service from the background.
        if (ringNow != null) exactAlarms.ringLate(ringNow.at.toInstant())
    }

    private suspend fun rescheduleLocked(now: ZonedDateTime) {
        val next = AlarmSchedule.upcoming(repository.alarms.first(), now)?.second?.toInstant()
        if (next == null) exactAlarms.cancel() else exactAlarms.schedule(next)
        state.scheduled = next
    }

    private fun isHandled(at: Instant) = state.handled?.let { !at.isAfter(it) } ?: false

    companion object {
        /** Alarms that are at most this late when the app catches up still ring (the stock clock's buffer). */
        val LATE_LIMIT: Duration = Duration.ofSeconds(15)

        /** Missed alarms older than this aren't reported. */
        val MISSED_REPORT_LIMIT: Duration = Duration.ofHours(12)
    }
}
