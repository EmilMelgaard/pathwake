package io.github.emilmelgaard.pathwake.settings

import androidx.datastore.core.okio.OkioStorage
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
import io.github.emilmelgaard.pathwake.alarm.AlarmSound
import io.github.emilmelgaard.pathwake.alarm.AutoSilence
import io.github.emilmelgaard.pathwake.puzzle.Difficulty
import io.github.emilmelgaard.pathwake.ui.puzzle.PuzzleLook
import java.io.File
import java.time.DayOfWeek
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    // Okio storage: the default file storage can't replace files on Windows (where tests may run).
    private fun TestScope.repository(file: File = File(folder.root, "settings.preferences_pb")) = SettingsRepository(
        PreferenceDataStoreFactory.create(
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { file.toOkioPath() },
            scope = backgroundScope
        ),
        backgroundScope
    )

    @Test
    fun startsWithTheDefaults() = runTest {
        assertEquals(Settings(), repository().get())
    }

    @Test
    fun storesEverySetting() = runTest {
        val changed = Settings(
            theme = ThemeMode.DARK,
            dynamicColor = true,
            puzzleLook = PuzzleLook.MATERIAL,
            colorMarks = true,
            firstDayOfWeek = DayOfWeek.SUNDAY,
            crescendoSeconds = 60,
            solvingVolume = SolvingVolume.MUTE,
            lockVolumeButtons = false,
            alarmDefaults = AlarmDefaults(
                difficulty = Difficulty.BRUTAL,
                puzzleCount = 4,
                autoSilence = AutoSilence.WhenSoundEnds,
                sound = AlarmSound.Custom("1_Birds.mp3", "Birds"),
                vibrate = false
            )
        )
        val repository = repository()
        repository.update { changed }
        assertEquals(changed, repository.get())

        repository.update {
            it.copy(firstDayOfWeek = null, alarmDefaults = it.alarmDefaults.copy(autoSilence = AutoSilence.Never))
        }
        assertEquals(null, repository.get().firstDayOfWeek)
        assertEquals(AutoSilence.Never, repository.get().alarmDefaults.autoSilence)
    }
}
