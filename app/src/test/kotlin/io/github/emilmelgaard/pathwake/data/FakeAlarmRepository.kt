package io.github.emilmelgaard.pathwake.data

import io.github.emilmelgaard.pathwake.alarm.Alarm
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeAlarmRepository(vararg initial: Alarm) : AlarmRepository {
    private val state = MutableStateFlow(initial.toList().sortedBy { it.time })
    private var lastId = initial.maxOfOrNull { it.id } ?: 0

    val current: List<Alarm> get() = state.value

    override val alarms: Flow<List<Alarm>> = state

    override suspend fun get(id: Long): Alarm? = state.value.find { it.id == id }

    override suspend fun save(alarm: Alarm): Long {
        val stored = if (alarm.id == 0L) alarm.copy(id = ++lastId) else alarm
        put(stored)
        return stored.id
    }

    override suspend fun delete(id: Long) = state.update { alarms -> alarms.filter { it.id != id } }

    override suspend fun restore(alarm: Alarm) = put(alarm)

    private fun put(alarm: Alarm) =
        state.update { alarms -> (alarms.filter { it.id != alarm.id } + alarm).sortedBy { it.time } }
}
