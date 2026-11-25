package io.github.emilmelgaard.pathwake.ui.common

import android.content.Context
import android.content.res.Resources
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.emilmelgaard.pathwake.R
import io.github.emilmelgaard.pathwake.alarm.Alarm
import io.github.emilmelgaard.pathwake.alarm.AlarmSchedule
import io.github.emilmelgaard.pathwake.alarm.AlarmSound
import io.github.emilmelgaard.pathwake.alarm.AutoSilence
import io.github.emilmelgaard.pathwake.puzzle.Difficulty
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** How an alarm's repetition is described. */
sealed interface RepeatSummary {
    data object EveryDay : RepeatSummary

    data object Weekdays : RepeatSummary

    data object Weekends : RepeatSummary

    data object Once : RepeatSummary

    data class Days(val days: List<DayOfWeek>) : RepeatSummary

    data class OnDate(val date: LocalDate) : RepeatSummary
}

/** The days of the week starting with the locale's first day. */
fun orderedWeek(firstDay: DayOfWeek): List<DayOfWeek> = List(7) { firstDay.plus(it.toLong()) }

fun repeatSummary(alarm: Alarm, firstDay: DayOfWeek): RepeatSummary = when {
    !alarm.repeats -> alarm.date?.let { RepeatSummary.OnDate(it) } ?: RepeatSummary.Once
    alarm.repeatDays.size == 7 -> RepeatSummary.EveryDay
    alarm.repeatDays == AlarmSchedule.WEEKDAYS -> RepeatSummary.Weekdays
    alarm.repeatDays == AlarmSchedule.WEEKEND -> RepeatSummary.Weekends
    else -> RepeatSummary.Days(orderedWeek(firstDay).filter { it in alarm.repeatDays })
}

@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/** The first day of the week chosen in the settings; null: the locale's. */
val LocalFirstDayOfWeek = staticCompositionLocalOf<DayOfWeek?> { null }

@Composable
@ReadOnlyComposable
fun firstDayOfWeek(): DayOfWeek = LocalFirstDayOfWeek.current ?: WeekFields.of(currentLocale()).firstDayOfWeek

/** The time in the user's 12/24-hour format, e.g. "07:00" or "7:00 AM". */
fun Context.formatTime(time: LocalTime): String {
    val locale = resources.configuration.locales[0]
    val pattern = DateFormat.getBestDateTimePattern(locale, if (DateFormat.is24HourFormat(this)) "Hm" else "hm")
    return time.format(DateTimeFormatter.ofPattern(pattern, locale))
}

@Composable
fun formatTime(time: LocalTime): String {
    // Read the configuration so the text updates when the locale changes.
    currentLocale()
    return LocalContext.current.formatTime(time)
}

@Composable
fun formatDate(date: LocalDate): String {
    val locale = currentLocale()
    return date.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale))
}

@Composable
fun formatDayName(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, currentLocale())

/** "Today 07:00", "Tomorrow 07:00", "Fri 07:00" or "Fri, 12 Dec 07:00". */
@Composable
fun formatWhen(at: ZonedDateTime, today: LocalDate): String {
    val date = at.toLocalDate()
    val day = when {
        date == today -> stringResource(R.string.today)
        date == today.plusDays(1) -> stringResource(R.string.tomorrow)
        date.isBefore(today.plusDays(7)) -> formatDayName(date.dayOfWeek)
        else -> formatDate(date)
    }
    return "$day ${formatTime(at.toLocalTime())}"
}

/** "in 2 days 3 h", "in 7 h 30 min", "in less than a minute". Minutes are left out from a day on. */
fun Resources.countdown(duration: Duration): String {
    if (duration < Duration.ofMinutes(1)) return getString(R.string.in_less_than_a_minute)
    // Duration.toHoursPart() and toMinutesPart() need API 31.
    val totalMinutes = duration.toMinutes()
    val days = (totalMinutes / (24 * 60)).toInt()
    val hours = (totalMinutes / 60 % 24).toInt()
    val minutes = (totalMinutes % 60).toInt()
    val parts = buildList {
        if (days > 0) add(getQuantityString(R.plurals.days_count, days, days))
        if (hours > 0) add(getString(R.string.hours_short, hours))
        if (minutes > 0 && days == 0) add(getString(R.string.minutes_short, minutes))
    }
    return getString(R.string.in_time, parts.joinToString(" "))
}

@Composable
@ReadOnlyComposable
fun formatCountdown(duration: Duration): String = LocalResources.current.countdown(duration)

@Composable
fun repeatText(alarm: Alarm): String = when (val summary = repeatSummary(alarm, firstDayOfWeek())) {
    RepeatSummary.EveryDay -> stringResource(R.string.every_day)
    RepeatSummary.Weekdays -> stringResource(R.string.weekdays)
    RepeatSummary.Weekends -> stringResource(R.string.weekends)
    RepeatSummary.Once -> stringResource(R.string.once)
    is RepeatSummary.OnDate -> stringResource(R.string.once_on, formatDate(summary.date))
    is RepeatSummary.Days -> summary.days.map { formatDayName(it) }.joinToString(", ")
}

@Composable
fun difficultyName(difficulty: Difficulty): String = stringResource(
    when (difficulty) {
        Difficulty.TRIVIAL -> R.string.difficulty_trivial
        Difficulty.EASY -> R.string.difficulty_easy
        Difficulty.MEDIUM -> R.string.difficulty_medium
        Difficulty.HARD -> R.string.difficulty_hard
        Difficulty.EXPERT -> R.string.difficulty_expert
        Difficulty.BRUTAL -> R.string.difficulty_brutal
    }
)

/** "Hard · 2 puzzles". */
@Composable
fun puzzleText(alarm: Alarm): String = stringResource(
    R.string.puzzle_summary,
    difficultyName(alarm.difficulty),
    pluralStringResource(R.plurals.puzzles, alarm.puzzleCount, alarm.puzzleCount)
)

@Composable
fun autoSilenceText(autoSilence: AutoSilence): String = when (autoSilence) {
    AutoSilence.Never -> stringResource(R.string.auto_silence_never)
    AutoSilence.WhenSoundEnds -> stringResource(R.string.auto_silence_sound_ends)
    is AutoSilence.After -> pluralStringResource(R.plurals.auto_silence_after, autoSilence.minutes, autoSilence.minutes)
}

@Composable
fun soundTitle(sound: AlarmSound): String = when (sound) {
    AlarmSound.Default -> stringResource(R.string.sound_default)
    AlarmSound.Silent -> stringResource(R.string.sound_silent)
    is AlarmSound.Tone -> sound.title
    is AlarmSound.Custom -> sound.title
}

/** "Off", "15 seconds", "1 minute". */
@Composable
fun crescendoText(seconds: Int): String = when {
    seconds <= 0 -> stringResource(R.string.off)
    seconds < 60 -> pluralStringResource(R.plurals.seconds, seconds, seconds)
    else -> pluralStringResource(R.plurals.minutes, seconds / 60, seconds / 60)
}
