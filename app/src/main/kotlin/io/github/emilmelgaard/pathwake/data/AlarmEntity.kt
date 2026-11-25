package io.github.emilmelgaard.pathwake.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.github.emilmelgaard.pathwake.alarm.Alarm
import io.github.emilmelgaard.pathwake.alarm.AlarmSound
import io.github.emilmelgaard.pathwake.alarm.AutoSilence
import io.github.emilmelgaard.pathwake.alarm.DateRange
import io.github.emilmelgaard.pathwake.puzzle.Difficulty
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Database row for an [Alarm]. Dates are stored as epoch days, repeat days as a bit mask (bit 0 = Monday). */
@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val hour: Int,
    val minute: Int,
    val label: String,
    val enabled: Boolean,
    @ColumnInfo(name = "repeat_days") val repeatDays: Int,
    @ColumnInfo(name = "date_epoch_day") val dateEpochDay: Long?,
    /** Pause ranges as `start..end` ISO dates separated by `;`. */
    val pauses: String,
    @ColumnInfo(name = "skipped_epoch_day") val skippedEpochDay: Long?,
    val difficulty: String,
    @ColumnInfo(name = "puzzle_count") val puzzleCount: Int,
    /** Null: never; 0: when the sound ends; otherwise minutes. */
    @ColumnInfo(name = "auto_silence_minutes", defaultValue = "10") val autoSilenceMinutes: Int?,
    /** See [AlarmSound.key]. */
    @ColumnInfo(defaultValue = "default") val sound: String,
    @ColumnInfo(name = "sound_title") val soundTitle: String?,
    @ColumnInfo(defaultValue = "1") val vibrate: Boolean
)

fun Alarm.toEntity() = AlarmEntity(
    id = id,
    hour = time.hour,
    minute = time.minute,
    label = label,
    enabled = enabled,
    repeatDays = repeatDays.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) },
    dateEpochDay = date?.toEpochDay(),
    pauses = pauses.joinToString(";") { "${it.start}..${it.end}" },
    skippedEpochDay = skippedDate?.toEpochDay(),
    difficulty = difficulty.name,
    puzzleCount = puzzleCount,
    autoSilenceMinutes = when (val silence = autoSilence) {
        AutoSilence.Never -> null
        AutoSilence.WhenSoundEnds -> 0
        is AutoSilence.After -> silence.minutes
    },
    sound = sound.key,
    soundTitle = sound.title,
    vibrate = vibrate
)

fun AlarmEntity.toAlarm() = Alarm(
    id = id,
    time = LocalTime.of(hour, minute),
    label = label,
    enabled = enabled,
    repeatDays = DayOfWeek.entries.filter { repeatDays and (1 shl (it.value - 1)) != 0 }.toSet(),
    date = dateEpochDay?.let(LocalDate::ofEpochDay),
    pauses = pauses.split(';').filter { it.isNotBlank() }.mapNotNull(::parseRange),
    skippedDate = skippedEpochDay?.let(LocalDate::ofEpochDay),
    difficulty = difficultyOf(difficulty) ?: Difficulty.MEDIUM,
    puzzleCount = puzzleCount.coerceIn(1, Alarm.MAX_PUZZLES),
    autoSilence = when {
        autoSilenceMinutes == null -> AutoSilence.Never
        autoSilenceMinutes <= 0 -> AutoSilence.WhenSoundEnds
        else -> AutoSilence.After(autoSilenceMinutes)
    },
    sound = AlarmSound.parse(sound, soundTitle),
    vibrate = vibrate
)

private fun parseRange(text: String): DateRange? = runCatching {
    val (start, end) = text.split("..")
    DateRange(LocalDate.parse(start), LocalDate.parse(end))
}.getOrNull()

/** Difficulties are stored by name. Names of renamed difficulties keep working. */
fun difficultyOf(name: String?): Difficulty? =
    Difficulty.entries.firstOrNull { it.name == name } ?: LEGACY_DIFFICULTIES[name]

private val LEGACY_DIFFICULTIES = mapOf("FIENDISH" to Difficulty.BRUTAL)
