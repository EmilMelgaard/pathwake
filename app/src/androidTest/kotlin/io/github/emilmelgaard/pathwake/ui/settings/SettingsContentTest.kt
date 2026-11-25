package io.github.emilmelgaard.pathwake.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import io.github.emilmelgaard.pathwake.puzzle.Difficulty
import io.github.emilmelgaard.pathwake.settings.Settings
import io.github.emilmelgaard.pathwake.settings.SolvingVolume
import io.github.emilmelgaard.pathwake.settings.ThemeMode
import io.github.emilmelgaard.pathwake.ui.theme.PathwakeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class SettingsContentTest {
    @get:Rule
    val rule = createComposeRule()

    private var settings by mutableStateOf(Settings())

    /** Shows the settings and applies every update to [settings], like the repository does. */
    private fun show(onTestSound: (Boolean) -> Unit = {}) = rule.setContent {
        PathwakeTheme {
            SettingsContent(
                settings = settings,
                volume = VolumeState(level = 5, min = 1, max = 7),
                testing = false,
                version = "1.0",
                actions = SettingsActions(onUpdate = { settings = it(settings) }, onTestSound = onTestSound)
            )
        }
    }

    @Test
    fun choosingFromAMenu_updatesTheSetting() {
        show()

        rule.onNodeWithText("Volume while solving").performClick()
        rule.onNodeWithText("Mute while drawing").performClick()
        rule.onNodeWithText("Theme").performScrollTo().performClick()
        rule.onNodeWithText("Dark").performClick()

        assertEquals(SolvingVolume.MUTE, settings.solvingVolume)
        assertEquals(ThemeMode.DARK, settings.theme)
    }

    @Test
    fun switchesAndChips_updateTheSettings() {
        show()

        val lockButtons = rule.onNode(isToggleable() and hasText("Keep ringing alarms loud"))
        lockButtons.assertIsOn().performClick()
        lockButtons.assertIsOff()
        rule.onNodeWithText("Hard").performScrollTo().performClick()

        assertFalse(settings.lockVolumeButtons)
        assertEquals(Difficulty.HARD, settings.alarmDefaults.difficulty)
    }

    @Test
    fun testButton_startsTheTestSound() {
        val tests = mutableListOf<Boolean>()
        show(onTestSound = { tests += it })

        rule.onNodeWithText("Test").performClick()
        assertEquals(listOf(true), tests)
    }
}
