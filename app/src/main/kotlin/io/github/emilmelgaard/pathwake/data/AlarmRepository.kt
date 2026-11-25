package io.github.emilmelgaard.pathwake.data

import io.github.emilmelgaard.pathwake.alarm.Alarm
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface AlarmRepository {
    /** All alarms, ordered by time of day. */
    val alarms: Flow<List<Alarm>>

    suspend fun get(id: Long): Alarm?

    /** Inserts the alarm if its id is 0, otherwise updates it. Returns the id. */
    suspend fun save(alarm: Alarm): Long

    suspend fun delete(id: Long)

    /** Re-inserts a deleted alarm with its original id (undo). */
    suspend fun restore(alarm: Alarm)
}

class RoomAlarmRepository(private val dao: AlarmDao) : AlarmRepository {
    override val alarms: Flow<List<Alarm>> = dao.observeAll().map { rows -> rows.map { it.toAlarm() } }

    override suspend fun get(id: Long): Alarm? = dao.get(id)?.toAlarm()

    override suspend fun save(alarm: Alarm): Long {
        if (alarm.id == 0L) return dao.insert(alarm.toEntity())
        dao.update(alarm.toEntity())
        return alarm.id
    }

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun restore(alarm: Alarm) {
        dao.insert(alarm.toEntity())
    }
}
