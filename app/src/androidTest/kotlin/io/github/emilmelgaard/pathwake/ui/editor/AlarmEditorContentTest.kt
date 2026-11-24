package io.github.emilmelgaard.pathwake.ui.editor

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.emilmelgaard.pathwake.alarm.Alarm
import io.github.emilmelgaard.pathwake.ui.theme.PathwakeTheme
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AlarmEditorContentTest {
    @get:Rule
    val rule = createComposeRule()

    private fun show(state: AlarmEditorUiState, actions: AlarmEditorActions) = rule.setContent {
        PathwakeTheme { AlarmEditorContent(state, actions) }
    }

    @Test
    fun newAlarm_canBeSavedButNotDeleted() {
        var saved = false
        show(AlarmEditorUiState(loading = false), AlarmEditorActions(onSave = { saved = true }))

        rule.onNodeWithText("New alarm").assertExists()
        rule.onNodeWithContentDescription("Delete alarm").assertDoesNotExist()
        rule.onNodeWithText("Save").performClick()
        assertTrue(saved)
    }

    @Test
    fun togglesRepeatDays() {
        val toggled = mutableListOf<DayOfWeek>()
        show(AlarmEditorUiState(loading = false), AlarmEditorActions(onToggleDay = { toggled += it }))

        rule.onNodeWithContentDescription("Monday").performClick()
        rule.onNodeWithContentDescription("Saturday").performClick()
        assertEquals(listOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY), toggled)
    }

    @Test
    fun puzzleCountStopsAtItsLimits() {
        var count: Int? = null
        val actions = AlarmEditorActions(onPuzzleCountChange = { count = it })
        show(AlarmEditorUiState(loading = false, isNew = false, alarm = Alarm(id = 1, puzzleCount = 1)), actions)

        rule.onNodeWithTag(PUZZLE_COUNT_TAG).performScrollTo().assertTextEquals("1")
        rule.onNodeWithContentDescription("Fewer puzzles").assertIsNotEnabled()
        rule.onNodeWithContentDescription("More puzzles").assertIsEnabled().performClick()
        assertEquals(2, count)
    }

    @Test
    fun repeatingAlarm_offersPausesAndSkipping() {
        show(
            AlarmEditorUiState(loading = false, alarm = Alarm(repeatDays = setOf(DayOfWeek.FRIDAY))),
            AlarmEditorActions()
        )
        rule.onNodeWithText("Add pause").assertExists()
        rule.onNodeWithContentDescription("Skip next alarm").assertExists()
        rule.onNodeWithText("Pick a date").assertDoesNotExist()
    }
}
