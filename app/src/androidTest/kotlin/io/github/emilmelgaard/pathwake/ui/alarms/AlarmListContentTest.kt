package io.github.emilmelgaard.pathwake.ui.alarms

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.emilmelgaard.pathwake.alarm.Alarm
import io.github.emilmelgaard.pathwake.alarm.AlarmSchedule
import io.github.emilmelgaard.pathwake.puzzle.Difficulty
import io.github.emilmelgaard.pathwake.ui.theme.PathwakeTheme
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AlarmListContentTest {
    @get:Rule
    val rule = createComposeRule()

    /** Wednesday 2026-11-25 06:00. */
    private val now = ZonedDateTime.of(2026, 11, 25, 6, 0, 0, 0, ZoneId.of("Europe/Copenhagen"))

    private fun show(alarms: List<Alarm>, actions: AlarmListActions = AlarmListActions()) {
        val items = alarms.map { AlarmListItem(it, AlarmSchedule.nextOccurrence(it, now), null, emptyList()) }
        val upcoming = items.firstOrNull { it.next != null }
            ?.let { UpcomingAlarm(it.alarm, it.next!!, AlarmSchedule.timeUntil(it.next, now)) }
        rule.setContent {
            PathwakeTheme {
                AlarmListContent(
                    state = AlarmListUiState(loading = false, items = items, upcoming = upcoming, now = now),
                    selectedAlarmId = null,
                    actions = actions
                )
            }
        }
    }

    @Test
    fun withoutAlarms_explainsWhatToDo() {
        show(emptyList())
        rule.onNodeWithText("No alarms yet").assertExists()
        rule.onNodeWithText("Add alarm", useUnmergedTree = true).assertExists()
    }

    @Test
    fun showsTheAlarmsAndTheNextOne() {
        show(
            listOf(
                Alarm(
                    id = 1,
                    time = LocalTime.of(7, 30),
                    label = "Work",
                    repeatDays = AlarmSchedule.WEEKDAYS,
                    difficulty = Difficulty.HARD,
                    puzzleCount = 2
                )
            )
        )
        rule.onNodeWithText("Work").assertExists()
        rule.onNodeWithText("Weekdays · Hard · 2 puzzles").assertExists()
        rule.onNodeWithText("Today", substring = true).assertExists()
        rule.onNodeWithText("in 1 h 30 min").assertExists()
    }

    @Test
    fun togglesAndOpensAlarms() {
        val alarm = Alarm(id = 3, time = LocalTime.of(8, 0), enabled = false)
        var toggled: Pair<Alarm, Boolean>? = null
        var opened: Alarm? = null
        show(listOf(alarm), AlarmListActions(onToggle = { a, on -> toggled = a to on }, onOpen = { opened = it }))

        rule.onNodeWithText("No alarms on").assertExists()
        rule.onNodeWithContentDescription("Alarm at", substring = true).assertIsOff().performClick()
        assertEquals(alarm to true, toggled)

        rule.onNodeWithText("Once · Medium · 1 puzzle").performClick()
        assertEquals(alarm, opened)
    }
}
