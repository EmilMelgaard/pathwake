package io.github.emilmelgaard.pathwake.alarm

import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmScheduleTest {
    private val copenhagen = ZoneId.of("Europe/Copenhagen")

    /** Wednesday 2026-11-25. */
    private fun at(date: String, time: String, zone: ZoneId = copenhagen) =
        ZonedDateTime.of(LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time)), zone)

    private fun next(alarm: Alarm, now: ZonedDateTime) = AlarmSchedule.nextOccurrence(alarm, now)

    private val seven = Alarm(time = LocalTime.of(7, 0))

    @Test
    fun oneTime_ringsTodayIfStillAheadOtherwiseTomorrow() {
        assertEquals(at("2026-11-25", "07:00"), next(seven, at("2026-11-25", "06:00")))
        assertEquals(at("2026-11-26", "07:00"), next(seven, at("2026-11-25", "07:00")))
        assertEquals(at("2026-11-26", "07:00"), next(seven, at("2026-11-25", "22:00")))
    }

    @Test
    fun disabled_neverRings() {
        assertNull(next(seven.copy(enabled = false), at("2026-11-25", "06:00")))
    }

    @Test
    fun specificDate_ringsOnThatDateOnly() {
        val christmas = seven.copy(date = LocalDate.parse("2026-12-24"))
        assertEquals(at("2026-12-24", "07:00"), next(christmas, at("2026-11-25", "12:00")))
        assertNull(next(christmas, at("2026-12-24", "07:00")))
    }

    @Test
    fun repeating_findsTheNextSelectedDay() {
        val weekdays = seven.copy(repeatDays = AlarmSchedule.WEEKDAYS)
        // Friday evening -> Monday morning.
        assertEquals(at("2026-11-30", "07:00"), next(weekdays, at("2026-11-27", "20:00")))
        val weekend = seven.copy(repeatDays = setOf(SATURDAY, SUNDAY))
        assertEquals(at("2026-11-28", "07:00"), next(weekend, at("2026-11-25", "12:00")))
        assertEquals(at("2026-11-29", "07:00"), next(weekend, at("2026-11-28", "07:00")))
    }

    @Test
    fun repeating_skipsPausedDays() {
        val weekdays = seven.copy(
            repeatDays = AlarmSchedule.WEEKDAYS,
            pauses = listOf(DateRange(LocalDate.parse("2026-12-21"), LocalDate.parse("2027-01-03")))
        )
        assertEquals(at("2027-01-04", "07:00"), next(weekdays, at("2026-12-19", "12:00")))
        assertEquals(at("2026-12-18", "07:00"), next(weekdays, at("2026-12-17", "12:00")))
    }

    @Test
    fun repeating_pausedForeverNeverRings() {
        val paused = seven.copy(
            repeatDays = setOf(MONDAY),
            pauses = listOf(DateRange(LocalDate.parse("2026-01-01"), LocalDate.parse("2040-01-01")))
        )
        assertNull(next(paused, at("2026-11-25", "12:00")))
    }

    @Test
    fun skipNext_skipsExactlyOneOccurrence() {
        val now = at("2026-11-25", "20:00")
        val daily = seven.copy(repeatDays = DayOfWeek.entries.toSet())
        val skipping = AlarmSchedule.skipNext(daily, now)
        assertEquals(LocalDate.parse("2026-11-26"), skipping.skippedDate)
        assertTrue(AlarmSchedule.isSkipping(skipping, now))
        assertEquals(at("2026-11-27", "07:00"), next(skipping, now))
        // Once the skipped day has passed, the skip no longer applies.
        assertFalse(AlarmSchedule.isSkipping(skipping, at("2026-11-26", "07:01")))
        assertEquals(seven, AlarmSchedule.skipNext(seven, now))
    }

    @Test
    fun daylightSaving_gapShiftsLaterAndOverlapRingsOnce() {
        // Copenhagen springs forward on 2027-03-28 02:00 -> 03:00.
        val gap = Alarm(time = LocalTime.of(2, 30), repeatDays = setOf(SUNDAY))
        val springNext = next(gap, at("2027-03-27", "12:00"))!!
        assertEquals(LocalTime.of(3, 30), springNext.toLocalTime())
        assertEquals(LocalDate.parse("2027-03-28"), springNext.toLocalDate())

        // ... and falls back on 2027-10-31 03:00 -> 02:00; 02:30 happens twice, the alarm rings the first time.
        val fallNext = next(gap, at("2027-10-30", "12:00"))!!
        assertEquals(
            ZonedDateTime.of(LocalDateTime.parse("2027-10-31T02:30"), copenhagen).withEarlierOffsetAtOverlap(),
            fallNext
        )
        val afterFirst = fallNext.plusMinutes(1)
        assertEquals(LocalDate.parse("2027-11-07"), next(gap, afterFirst)!!.toLocalDate())
    }

    @Test
    fun timeZone_usesWallClockTimeOfTheCurrentZone() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        assertEquals(at("2026-11-26", "07:00", tokyo), next(seven, at("2026-11-25", "22:00", tokyo)))
    }

    @Test
    fun upcoming_picksTheEarliestEnabledAlarm() {
        val now = at("2026-11-25", "12:00")
        val alarms = listOf(
            seven.copy(id = 1),
            seven.copy(id = 2, time = LocalTime.of(13, 0)),
            seven.copy(id = 3, time = LocalTime.of(12, 30), enabled = false)
        )
        assertEquals(2L, AlarmSchedule.upcoming(alarms, now)?.first?.id)
        assertNull(AlarmSchedule.upcoming(emptyList(), now))
    }

    @Test
    fun normalize_dropsStaleData() {
        val now = at("2026-11-25", "12:00")
        val old = DateRange(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-05"))
        val future = DateRange(LocalDate.parse("2026-12-20"), LocalDate.parse("2026-12-31"))
        val alarm = seven.copy(
            repeatDays = setOf(FRIDAY),
            date = LocalDate.parse("2026-12-01"),
            pauses = listOf(future, old),
            skippedDate = LocalDate.parse("2026-11-20")
        )
        assertEquals(
            alarm.copy(date = null, pauses = listOf(future), skippedDate = null),
            AlarmSchedule.normalize(alarm, now)
        )
        val once = seven.copy(pauses = listOf(future), skippedDate = LocalDate.parse("2026-11-26"))
        assertEquals(seven, AlarmSchedule.normalize(once, now))
    }

    @Test
    fun timeUntil_roundsUpToWholeMinutes() {
        val now = at("2026-11-25", "06:59:30")
        assertEquals(Duration.ofMinutes(1), AlarmSchedule.timeUntil(at("2026-11-25", "07:00"), now))
        assertEquals(Duration.ofHours(24), AlarmSchedule.timeUntil(at("2026-11-26", "06:59:30"), now))
    }

    @Test
    fun dueAt_findsEveryAlarmRingingAtThatTime() {
        val work = seven.copy(id = 1, repeatDays = AlarmSchedule.WEEKDAYS)
        val once = seven.copy(id = 2)
        val skipping = seven.copy(
            id = 3,
            repeatDays = AlarmSchedule.WEEKDAYS,
            skippedDate = LocalDate.parse("2026-11-25")
        )
        val later = seven.copy(id = 4, time = LocalTime.of(7, 30))
        val off = seven.copy(id = 5, enabled = false)

        val due = AlarmSchedule.dueAt(listOf(work, once, skipping, later, off), at("2026-11-25", "07:00"))

        assertEquals(listOf(1L, 2L), due.map { it.id })
    }

    @Test
    fun afterRinging_turnsOneTimeAlarmsOffAndKeepsRepeatingOnes() {
        val now = at("2026-11-25", "07:00")
        assertFalse(AlarmSchedule.afterRinging(seven, now).enabled)

        val weekdays = seven.copy(
            repeatDays = AlarmSchedule.WEEKDAYS,
            pauses = listOf(DateRange(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-20")))
        )
        val after = AlarmSchedule.afterRinging(weekdays, now)
        assertTrue(after.enabled)
        assertTrue(after.pauses.isEmpty())
        assertEquals(at("2026-11-26", "07:00"), next(after, now))
    }

    @Test
    fun occurrencesBetween_listsEachRingOnceInOrder() {
        val once = seven.copy(id = 1)
        val daily = seven.copy(id = 2, time = LocalTime.of(6, 30), repeatDays = DayOfWeek.entries.toSet())

        val occurrences = AlarmSchedule.occurrencesBetween(
            listOf(once, daily),
            from = at("2026-11-25", "06:30"),
            until = at("2026-11-26", "08:00")
        )

        assertEquals(
            listOf(
                at("2026-11-25", "06:30") to listOf(2L),
                at("2026-11-25", "07:00") to listOf(1L),
                at("2026-11-26", "06:30") to listOf(2L)
            ),
            occurrences.map { it.at to it.alarms.map(Alarm::id) }
        )
    }

    @Test
    fun occurrencesBetween_isEmptyWhenNothingRings() {
        val occurrences = AlarmSchedule.occurrencesBetween(
            listOf(seven),
            from = at("2026-11-25", "07:01"),
            until = at("2026-11-25", "08:00")
        )
        assertTrue(occurrences.isEmpty())
    }
}
