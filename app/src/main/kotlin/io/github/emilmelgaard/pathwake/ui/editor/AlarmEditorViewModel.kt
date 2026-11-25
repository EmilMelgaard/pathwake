package io.github.emilmelgaard.pathwake.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.emilmelgaard.pathwake.alarm.Alarm
import io.github.emilmelgaard.pathwake.alarm.AlarmSchedule
import io.github.emilmelgaard.pathwake.alarm.DateRange
import io.github.emilmelgaard.pathwake.alarm.minuteTicker
import io.github.emilmelgaard.pathwake.data.AlarmRepository
import io.github.emilmelgaard.pathwake.puzzle.Difficulty
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * @property alarm the alarm being edited (not saved yet).
 * @property next when [alarm] would ring next; null if it wouldn't.
 * @property skippedDate the day that "skip next" skips, if it is on.
 * @property missing the alarm to edit doesn't exist (anymore).
 */
data class AlarmEditorUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val alarm: Alarm = Alarm(),
    val isDirty: Boolean = false,
    val missing: Boolean = false,
    val next: ZonedDateTime? = null,
    val timeUntil: Duration? = null,
    val skippedDate: LocalDate? = null,
    val today: LocalDate? = null
)

/** Result of saving: the alarm's id and when it rings next (null: it won't). */
data class SavedAlarm(val id: Long, val next: ZonedDateTime?, val timeUntil: Duration?)

class AlarmEditorViewModel(
    private val alarmId: Long,
    private val repository: AlarmRepository,
    private val now: () -> ZonedDateTime,
    ticker: Flow<ZonedDateTime> = minuteTicker(now)
) : ViewModel() {
    private data class Draft(val initial: Alarm, val alarm: Alarm, val isNew: Boolean, val missing: Boolean)

    private val draft = MutableStateFlow<Draft?>(null)

    init {
        viewModelScope.launch {
            val existing = if (alarmId == 0L) null else repository.get(alarmId)
            val alarm = existing ?: Alarm()
            draft.value = Draft(alarm, alarm, isNew = existing == null, missing = alarmId != 0L && existing == null)
        }
    }

    val uiState: StateFlow<AlarmEditorUiState> = combine(draft, ticker) { draft, time ->
        if (draft == null) return@combine AlarmEditorUiState()
        // A saved alarm is enabled, so preview it that way.
        val preview = draft.alarm.copy(enabled = true)
        val next = AlarmSchedule.nextOccurrence(preview, time)
        AlarmEditorUiState(
            loading = false,
            isNew = draft.isNew,
            alarm = draft.alarm,
            isDirty = draft.alarm != draft.initial,
            missing = draft.missing,
            next = next,
            timeUntil = next?.let { AlarmSchedule.timeUntil(it, time) },
            skippedDate = preview.skippedDate.takeIf { AlarmSchedule.isSkipping(preview, time) },
            today = time.toLocalDate()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlarmEditorUiState())

    private fun edit(transform: (Alarm) -> Alarm) = draft.update { it?.copy(alarm = transform(it.alarm)) }

    fun setTime(time: LocalTime) = edit { it.copy(time = time) }

    fun setLabel(label: String) = edit { it.copy(label = label) }

    /** Adding a repeat day turns a one-time alarm into a repeating one (and drops its date). */
    fun toggleDay(day: DayOfWeek) = edit {
        val days = if (day in it.repeatDays) it.repeatDays - day else it.repeatDays + day
        it.copy(repeatDays = days, date = if (days.isEmpty()) it.date else null)
    }

    fun setRepeatDays(days: Set<DayOfWeek>) = edit {
        it.copy(repeatDays = days, date = if (days.isEmpty()) it.date else null)
    }

    fun setDate(date: LocalDate?) = edit {
        it.copy(
            date = date,
            repeatDays = if (date !=
                null
            ) {
                emptySet()
            } else {
                it.repeatDays
            }
        )
    }

    fun addPause(range: DateRange) = edit { it.copy(pauses = (it.pauses + range).distinct().sortedBy { r -> r.start }) }

    fun removePause(range: DateRange) = edit { it.copy(pauses = it.pauses - range) }

    fun setSkipNext(skip: Boolean) = edit {
        if (skip) {
            AlarmSchedule.skipNext(
                it.copy(enabled = true),
                now()
            ).copy(enabled = it.enabled)
        } else {
            it.copy(skippedDate = null)
        }
    }

    fun setDifficulty(difficulty: Difficulty) = edit { it.copy(difficulty = difficulty) }

    fun setPuzzleCount(count: Int) = edit { it.copy(puzzleCount = count.coerceIn(1, Alarm.MAX_PUZZLES)) }

    /** Saves (and enables) the alarm. */
    fun save(onSaved: (SavedAlarm) -> Unit) {
        val current = draft.value ?: return
        viewModelScope.launch {
            val time = now()
            val alarm = AlarmSchedule.normalize(current.alarm.copy(enabled = true), time)
            val id = repository.save(alarm)
            draft.value = current.copy(initial = alarm.copy(id = id), alarm = alarm.copy(id = id), isNew = false)
            val next = AlarmSchedule.nextOccurrence(alarm, time)
            onSaved(SavedAlarm(id, next, next?.let { AlarmSchedule.timeUntil(it, time) }))
        }
    }

    /** Deletes the alarm and hands it back so the deletion can be undone. */
    fun delete(onDeleted: (Alarm) -> Unit) {
        val current = draft.value ?: return
        if (current.isNew) return
        viewModelScope.launch {
            val stored = repository.get(current.initial.id) ?: current.initial
            repository.delete(stored.id)
            onDeleted(stored)
        }
    }
}
