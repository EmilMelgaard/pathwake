package io.github.emilmelgaard.pathwake.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.emilmelgaard.pathwake.alarm.Alarm
import io.github.emilmelgaard.pathwake.alarm.DateRange
import io.github.emilmelgaard.pathwake.puzzle.Difficulty
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AlarmDaoTest {
    private lateinit var database: PathwakeDatabase
    private lateinit var repository: RoomAlarmRepository

    @Before
    fun setUp() {
        database =
            Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PathwakeDatabase::class.java)
                .build()
        repository = RoomAlarmRepository(database.alarmDao())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun storesEveryField() = runBlocking {
        val alarm = Alarm(
            time = LocalTime.of(6, 45),
            label = "Work, \"early\" shift",
            enabled = false,
            repeatDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SUNDAY),
            pauses = listOf(
                DateRange(LocalDate.of(2026, 12, 20), LocalDate.of(2027, 1, 2)),
                DateRange(LocalDate.of(2027, 2, 9), LocalDate.of(2027, 2, 9))
            ),
            skippedDate = LocalDate.of(2026, 11, 30),
            difficulty = Difficulty.FIENDISH,
            puzzleCount = 3
        )
        val oneTime = Alarm(time = LocalTime.of(23, 59), date = LocalDate.of(2027, 3, 1))

        val id = repository.save(alarm)
        val oneTimeId = repository.save(oneTime)

        assertEquals(alarm.copy(id = id), repository.get(id))
        assertEquals(oneTime.copy(id = oneTimeId), repository.get(oneTimeId))
    }

    @Test
    fun listsAlarmsByTimeOfDay() = runBlocking {
        repository.save(Alarm(time = LocalTime.of(9, 0)))
        repository.save(Alarm(time = LocalTime.of(6, 30)))
        repository.save(Alarm(time = LocalTime.of(6, 5)))

        assertEquals(
            listOf(LocalTime.of(6, 5), LocalTime.of(6, 30), LocalTime.of(9, 0)),
            repository.alarms.first().map { it.time }
        )
    }

    @Test
    fun updatesDeletesAndRestores() = runBlocking {
        val id = repository.save(Alarm(label = "Before"))
        val updated = repository.get(id)!!.copy(label = "After")

        assertEquals(id, repository.save(updated))
        assertEquals("After", repository.get(id)?.label)

        repository.delete(id)
        assertNull(repository.get(id))

        repository.restore(updated)
        assertEquals(updated, repository.get(id))
    }
}
