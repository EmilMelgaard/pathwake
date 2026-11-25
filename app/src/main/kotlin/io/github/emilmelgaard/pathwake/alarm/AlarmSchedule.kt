package io.github.emilmelgaard.pathwake.alarm

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

/** When alarms ring. All functions are pure so they can be tested with fixed clocks and time zones. */
object AlarmSchedule {
    /** How far ahead to look for a day that isn't paused (pauses longer than this make the alarm never ring). */
    private const val MAX_DAYS_AHEAD = 5 * 366

    val WEEKDAYS: Set<DayOfWeek> = DayOfWeek.entries.toSet() - setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    val WEEKEND: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    /**
     * The next time [alarm] rings strictly after [now], in [now]'s time zone, or null if it never will (disabled, or
     * a one-time alarm whose date has passed).
     *
     * Times that don't exist because of a daylight saving gap ring right after the gap (02:30 becomes 03:30); times
     * that occur twice ring the first time.
     */
    fun nextOccurrence(alarm: Alarm, now: ZonedDateTime): ZonedDateTime? {
        if (!alarm.enabled) return null
        fun at(date: LocalDate) = ZonedDateTime.of(date, alarm.time, now.zone)

        if (!alarm.repeats) {
            alarm.date?.let { date -> return at(date).takeIf { it.isAfter(now) } }
            val today = at(now.toLocalDate())
            return if (today.isAfter(now)) today else at(now.toLocalDate().plusDays(1))
        }
        var date = now.toLocalDate()
        repeat(MAX_DAYS_AHEAD) {
            if (ringsOn(alarm, date)) {
                val occurrence = at(date)
                if (occurrence.isAfter(now)) return occurrence
            }
            date = date.plusDays(1)
        }
        return null
    }

    /** Whether a repeating [alarm] rings on [date] (ignoring whether that time has passed). */
    fun ringsOn(alarm: Alarm, date: LocalDate): Boolean =
        date.dayOfWeek in alarm.repeatDays && alarm.pauses.none { date in it } && date != alarm.skippedDate

    /** The alarm, set to skip its next occurrence. Only repeating alarms can skip. */
    fun skipNext(alarm: Alarm, now: ZonedDateTime): Alarm {
        if (!alarm.repeats) return alarm
        val next = nextOccurrence(alarm.copy(skippedDate = null), now) ?: return alarm
        return alarm.copy(skippedDate = next.toLocalDate())
    }

    /** Whether the alarm is currently skipping an upcoming occurrence. */
    fun isSkipping(alarm: Alarm, now: ZonedDateTime): Boolean {
        val skipped = alarm.skippedDate ?: return false
        return alarm.repeats && !ZonedDateTime.of(skipped, alarm.time, now.zone).isBefore(now)
    }

    /** Pauses that haven't ended yet, in date order. */
    fun currentPauses(alarm: Alarm, today: LocalDate): List<DateRange> =
        alarm.pauses.filter { !it.end.isBefore(today) }.sortedBy { it.start }

    /**
     * Removes information that no longer matters (ended pauses, a skip that already passed, dates on repeating
     * alarms), so stored alarms don't accumulate stale data.
     */
    fun normalize(alarm: Alarm, now: ZonedDateTime): Alarm {
        if (!alarm.repeats) return alarm.copy(pauses = emptyList(), skippedDate = null)
        return alarm.copy(
            date = null,
            pauses = currentPauses(alarm, now.toLocalDate()),
            skippedDate = alarm.skippedDate.takeIf { isSkipping(alarm, now) }
        )
    }

    /** The enabled alarm that rings first after [now], with its time. */
    fun upcoming(alarms: List<Alarm>, now: ZonedDateTime): Pair<Alarm, ZonedDateTime>? = alarms
        .mapNotNull { alarm -> nextOccurrence(alarm, now)?.let { alarm to it } }
        .minByOrNull { it.second }

    /** The alarms that ring at exactly [at]. */
    fun dueAt(alarms: List<Alarm>, at: ZonedDateTime): List<Alarm> =
        alarms.filter { nextOccurrence(it, at.minusSeconds(1))?.toInstant() == at.toInstant() }

    /** [alarm] after it rang (or should have rung) at [at]: one-time alarms turn off, repeating ones drop stale data. */
    fun afterRinging(alarm: Alarm, at: ZonedDateTime): Alarm =
        if (alarm.repeats) normalize(alarm, at) else alarm.copy(enabled = false)

    /**
     * The times from [from] to [until] (both inclusive) at which [alarms] ring, each with the alarms ringing then.
     * One-time alarms only ring once. Stops after [limit] occurrences.
     */
    fun occurrencesBetween(
        alarms: List<Alarm>,
        from: ZonedDateTime,
        until: ZonedDateTime,
        limit: Int = 100
    ): List<Occurrence> {
        val result = mutableListOf<Occurrence>()
        var current = alarms
        var cursor = from.minusSeconds(1)
        while (result.size < limit) {
            val at = upcoming(current, cursor)?.second ?: break
            if (at.isAfter(until)) break
            val due = dueAt(current, at)
            result += Occurrence(at, due)
            val dueIds = due.map { it.id }.toSet()
            current = current.map { if (it.id in dueIds) afterRinging(it, at) else it }
            cursor = at
        }
        return result
    }

    /** Time from [now] until [at], rounded up to whole minutes (an alarm 30 seconds away is "in 1 minute"). */
    fun timeUntil(at: ZonedDateTime, now: ZonedDateTime): Duration {
        val duration = Duration.between(now, at)
        val minutes = (duration.toMillis() + 59_999) / 60_000
        return Duration.ofMinutes(minutes.coerceAtLeast(0))
    }
}
