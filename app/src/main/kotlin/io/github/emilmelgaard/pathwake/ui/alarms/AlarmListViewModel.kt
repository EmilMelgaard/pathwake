package io.github.emilmelgaard.pathwake.ui.alarms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.emilmelgaard.pathwake.alarm.Alarm
import io.github.emilmelgaard.pathwake.alarm.AlarmSchedule
import io.github.emilmelgaard.pathwake.alarm.DateRange
import io.github.emilmelgaard.pathwake.alarm.minuteTicker
import io.github.emilmelgaard.pathwake.data.AlarmRepository
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * @property next when the alarm rings next (null: it won't).
 * @property skippedDate set if the alarm skips its next occurrence on this day.
 * @property pauses pauses that haven't ended yet.
 */
data class AlarmListItem(
    val alarm: Alarm,
    val next: ZonedDateTime?,
    val skippedDate: LocalDate?,
    val pauses: List<DateRange>
)

data class UpcomingAlarm(val alarm: Alarm, val at: ZonedDateTime, val timeUntil: Duration)

data class AlarmListUiState(
    val loading: Boolean = true,
    val items: List<AlarmListItem> = emptyList(),
    val upcoming: UpcomingAlarm? = null,
    val now: ZonedDateTime? = null
)

class AlarmListViewModel(
    private val repository: AlarmRepository,
    private val now: () -> ZonedDateTime,
    ticker: Flow<ZonedDateTime> = minuteTicker(now)
) : ViewModel() {
    val uiState: StateFlow<AlarmListUiState> = combine(repository.alarms, ticker) { alarms, time ->
        val items = alarms.map { alarm ->
            AlarmListItem(
                alarm = alarm,
                next = AlarmSchedule.nextOccurrence(alarm, time),
                skippedDate = alarm.skippedDate.takeIf { AlarmSchedule.isSkipping(alarm, time) },
                pauses = if (alarm.repeats) AlarmSchedule.currentPauses(alarm, time.toLocalDate()) else emptyList()
            )
        }
        val upcoming = items
            .filter { it.next != null }
            .minByOrNull { it.next!! }
            ?.let { UpcomingAlarm(it.alarm, it.next!!, AlarmSchedule.timeUntil(it.next, time)) }
        AlarmListUiState(loading = false, items = items, upcoming = upcoming, now = time)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlarmListUiState())

    fun setEnabled(alarm: Alarm, enabled: Boolean) = save {
        val time = now()
        var updated = alarm.copy(enabled = enabled)
        // Turning on a one-time alarm whose date has passed makes it ring at the next occurrence instead.
        if (enabled && !alarm.repeats && AlarmSchedule.nextOccurrence(updated, time) == null) {
            updated = updated.copy(date = null)
        }
        AlarmSchedule.normalize(updated, time)
    }

    fun skipNext(alarm: Alarm) = save { AlarmSchedule.skipNext(alarm, now()) }

    fun cancelSkip(alarm: Alarm) = save { alarm.copy(skippedDate = null) }

    /** Copies the alarm, except for a skipped day. */
    fun duplicate(alarm: Alarm) = save { alarm.copy(id = 0, skippedDate = null) }

    fun delete(alarm: Alarm) {
        viewModelScope.launch { repository.delete(alarm.id) }
    }

    private fun save(transform: () -> Alarm) {
        viewModelScope.launch { repository.save(transform()) }
    }
}
