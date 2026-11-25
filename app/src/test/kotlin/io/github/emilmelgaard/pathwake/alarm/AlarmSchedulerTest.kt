package io.github.emilmelgaard.pathwake.alarm

import io.github.emilmelgaard.pathwake.data.FakeAlarmRepository
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmSchedulerTest {
    private val zone = ZoneId.of("Europe/Copenhagen")

    /** Wednesday 2026-11-25. */
    private fun at(date: String, time: String): ZonedDateTime =
        ZonedDateTime.of(LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time)), zone)

    private var now = at("2026-11-25", "06:00")
    private val exactAlarms = FakeExactAlarms()
    private val state = FakeSchedulerState()
    private val ringer = FakeRinger()

    private fun scheduler(repository: FakeAlarmRepository) =
        AlarmScheduler(repository, exactAlarms, state, ringer) { now }

    private val once = Alarm(id = 1, time = LocalTime.of(7, 0))
    private val daily = Alarm(id = 2, time = LocalTime.of(6, 30), repeatDays = DayOfWeek.entries.toSet())

    @Test
    fun reschedule_setsThePlatformAlarmForTheNextAlarm() = runTest {
        scheduler(FakeAlarmRepository(once, daily)).reschedule()

        assertEquals(at("2026-11-25", "06:30").toInstant(), exactAlarms.scheduled)
        assertEquals(exactAlarms.scheduled, state.scheduled)
    }

    @Test
    fun reschedule_cancelsWhenNoAlarmIsOn() = runTest {
        exactAlarms.scheduled = Instant.EPOCH
        scheduler(FakeAlarmRepository(once.copy(enabled = false))).reschedule()

        assertNull(exactAlarms.scheduled)
        assertNull(state.scheduled)
    }

    @Test
    fun alarmTime_ringsTheDueAlarmsAndSchedulesTheNext() = runTest {
        val repository = FakeAlarmRepository(once, daily, once.copy(id = 3, label = "Also 7"))
        now = at("2026-11-25", "07:00")

        scheduler(repository).onAlarmTime(now.toInstant())

        assertEquals(listOf(now to listOf(1L, 3L)), ringer.rung)
        assertFalse(repository.current.single { it.id == 1L }.enabled)
        assertTrue(repository.current.single { it.id == 2L }.enabled)
        assertEquals(at("2026-11-26", "06:30").toInstant(), exactAlarms.scheduled)
    }

    @Test
    fun alarmTime_ringsOnlyOnce() = runTest {
        val scheduler = scheduler(FakeAlarmRepository(daily))
        now = at("2026-11-25", "06:30")

        scheduler.onAlarmTime(now.toInstant())
        scheduler.onAlarmTime(now.toInstant())

        assertEquals(1, ringer.rung.size)
    }

    @Test
    fun sync_ringsAnAlarmThatIsOnlySecondsLate() = runTest {
        val scheduler = scheduler(FakeAlarmRepository(once))
        scheduler.reschedule()
        now = at("2026-11-25", "07:00").plusSeconds(10)

        scheduler.sync()

        assertEquals(listOf(at("2026-11-25", "07:00").toInstant()), exactAlarms.late)
        assertTrue(ringer.missed.isEmpty())
    }

    @Test
    fun sync_reportsAlarmsMissedWhileTheDeviceWasOff() = runTest {
        val repository = FakeAlarmRepository(once, daily)
        val scheduler = scheduler(repository)
        scheduler.reschedule()
        now = at("2026-11-25", "09:00")

        scheduler.sync()

        assertTrue(exactAlarms.late.isEmpty())
        assertEquals(
            listOf(at("2026-11-25", "06:30") to listOf(2L), at("2026-11-25", "07:00") to listOf(1L)),
            ringer.missed.map { it.at to it.alarms.map(Alarm::id) }
        )
        assertFalse(repository.current.single { it.id == 1L }.enabled)
        assertEquals(at("2026-11-26", "06:30").toInstant(), exactAlarms.scheduled)

        // A platform alarm for a reported time arriving afterwards (after a clock change) doesn't ring.
        scheduler.onAlarmTime(at("2026-11-25", "07:00").toInstant())
        assertTrue(ringer.rung.isEmpty())
    }

    @Test
    fun sync_ringsTheLastAlarmWhenEarlierOnesWereMissed() = runTest {
        val scheduler = scheduler(FakeAlarmRepository(daily, once.copy(time = LocalTime.of(8, 0))))
        scheduler.reschedule()
        now = at("2026-11-25", "08:00").plusSeconds(5)

        scheduler.sync()

        assertEquals(listOf(listOf(2L)), ringer.missed.map { it.alarms.map(Alarm::id) })
        assertEquals(listOf(at("2026-11-25", "08:00").toInstant()), exactAlarms.late)
    }

    @Test
    fun sync_beforeTheAlarmTimeOnlyReschedules() = runTest {
        val scheduler = scheduler(FakeAlarmRepository(once))
        scheduler.reschedule()
        now = at("2026-11-25", "06:59")

        scheduler.sync()

        assertTrue(exactAlarms.late.isEmpty() && ringer.missed.isEmpty())
        assertEquals(at("2026-11-25", "07:00").toInstant(), exactAlarms.scheduled)
    }

    @Test
    fun sync_resumesAnInterruptedAlarm() = runTest {
        ringer.interrupted = true
        scheduler(FakeAlarmRepository()).sync()
        assertEquals(1, exactAlarms.resumed)
    }

    private class FakeExactAlarms : ExactAlarms {
        var scheduled: Instant? = null
        val late = mutableListOf<Instant>()
        var resumed = 0

        override fun schedule(at: Instant) {
            scheduled = at
        }

        override fun cancel() {
            scheduled = null
        }

        override fun ringLate(at: Instant) {
            late += at
        }

        override fun resumeRinging() {
            resumed++
        }
    }

    private class FakeSchedulerState : SchedulerState {
        override var scheduled: Instant? = null
        override var handled: Instant? = null
    }

    private class FakeRinger : Ringer {
        val rung = mutableListOf<Pair<ZonedDateTime, List<Long>>>()
        val missed = mutableListOf<Occurrence>()
        var interrupted = false

        override fun ring(alarms: List<Alarm>, at: ZonedDateTime) {
            rung += at to alarms.map { it.id }
        }

        override fun isInterrupted(now: ZonedDateTime) = interrupted

        override fun reportMissed(occurrences: List<Occurrence>) {
            missed += occurrences
        }
    }
}
