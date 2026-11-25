package io.github.emilmelgaard.pathwake.ui.settings

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.emilmelgaard.pathwake.R
import io.github.emilmelgaard.pathwake.alarm.Alarm
import io.github.emilmelgaard.pathwake.alarm.AlarmSound
import io.github.emilmelgaard.pathwake.alarm.AutoSilence
import io.github.emilmelgaard.pathwake.appContainer
import io.github.emilmelgaard.pathwake.settings.AlarmDefaults
import io.github.emilmelgaard.pathwake.settings.Settings
import io.github.emilmelgaard.pathwake.settings.SolvingVolume
import io.github.emilmelgaard.pathwake.settings.ThemeMode
import io.github.emilmelgaard.pathwake.system.AlarmPlayer
import io.github.emilmelgaard.pathwake.system.AlarmVolume
import io.github.emilmelgaard.pathwake.ui.common.ChoiceField
import io.github.emilmelgaard.pathwake.ui.common.DifficultyChips
import io.github.emilmelgaard.pathwake.ui.common.PuzzleCountRow
import io.github.emilmelgaard.pathwake.ui.common.SwitchRow
import io.github.emilmelgaard.pathwake.ui.common.ValueRow
import io.github.emilmelgaard.pathwake.ui.common.autoSilenceText
import io.github.emilmelgaard.pathwake.ui.common.crescendoText
import io.github.emilmelgaard.pathwake.ui.common.currentLocale
import io.github.emilmelgaard.pathwake.ui.common.soundTitle
import io.github.emilmelgaard.pathwake.ui.puzzle.PuzzleLook
import io.github.emilmelgaard.pathwake.ui.sound.SoundPickerDialog
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import kotlinx.coroutines.launch

/** The device's alarm volume as shown in the settings. */
data class VolumeState(val level: Int, val min: Int, val max: Int)

/** Callbacks of the settings screen. */
data class SettingsActions(
    val onBack: () -> Unit = {},
    val onUpdate: ((Settings) -> Settings) -> Unit = {},
    val onVolumeChange: (Int) -> Unit = {},
    val onTestSound: (Boolean) -> Unit = {},
    val onOpenSourceCode: () -> Unit = {}
)

@Composable
fun SettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val container = context.appContainer
    val settings by container.settings.current.collectAsStateWithLifecycle()
    val alarmVolume = remember { AlarmVolume(context) }
    var volume by remember { mutableIntStateOf(alarmVolume.level) }
    LifecycleResumeEffect(Unit) {
        volume = alarmVolume.level
        onPauseOrDispose { }
    }
    var testing by remember { mutableStateOf(false) }
    val player = remember { AlarmPlayer(context, withWakeLock = false) }
    DisposableEffect(player) { onDispose { player.stop() } }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    }

    SettingsContent(
        settings = settings,
        volume = VolumeState(volume, alarmVolume.min, alarmVolume.max),
        testing = testing,
        version = version,
        actions = SettingsActions(
            onBack = onBack,
            onUpdate = { transform -> container.scope.launch { container.settings.update(transform) } },
            onVolumeChange = {
                alarmVolume.level = it
                volume = alarmVolume.level
            },
            onTestSound = { test ->
                testing = test
                if (test) {
                    val source = container.soundLibrary.sources(settings.alarmDefaults.sound).all.firstOrNull()
                        ?: container.soundLibrary.sources(AlarmSound.Default).all.first()
                    player.play(source, loop = true)
                } else {
                    player.stop()
                }
            },
            onOpenSourceCode = {
                context.startActivity(Intent(Intent.ACTION_VIEW, SOURCE_CODE_URL.toUri()))
            }
        ),
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    settings: Settings,
    volume: VolumeState,
    testing: Boolean,
    version: String?,
    actions: SettingsActions,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag(SETTINGS_TAG),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RingingSettings(settings, volume, testing, actions)
                HorizontalDivider()
                NewAlarmSettings(settings.alarmDefaults) { change ->
                    actions.onUpdate { it.copy(alarmDefaults = change(it.alarmDefaults)) }
                }
                HorizontalDivider()
                AppearanceSettings(settings, actions)
                HorizontalDivider()
                About(version, actions)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp).semantics {
            heading()
        }
    )
}

@Composable
private fun RingingSettings(settings: Settings, volume: VolumeState, testing: Boolean, actions: SettingsActions) {
    SectionTitle(stringResource(R.string.ringing_title))
    Column {
        Text(stringResource(R.string.alarm_volume), style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            val description = stringResource(R.string.alarm_volume)
            Slider(
                value = volume.level.toFloat(),
                onValueChange = { actions.onVolumeChange(Math.round(it)) },
                valueRange = volume.min.toFloat()..volume.max.toFloat().coerceAtLeast(volume.min + 1f),
                steps = (volume.max - volume.min - 1).coerceAtLeast(0),
                modifier = Modifier.weight(1f).semantics { contentDescription = description }
            )
            TextButton(onClick = { actions.onTestSound(!testing) }) {
                Text(stringResource(if (testing) R.string.stop_test else R.string.test_sound))
            }
        }
        Text(
            stringResource(R.string.alarm_volume_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    ChoiceField(
        label = stringResource(R.string.crescendo),
        value = settings.crescendoSeconds,
        choices = Settings.CRESCENDO_CHOICES,
        text = { crescendoText(it) },
        onChange = { seconds -> actions.onUpdate { it.copy(crescendoSeconds = seconds) } },
        modifier = Modifier.fillMaxWidth()
    )
    ChoiceField(
        label = stringResource(R.string.solving_volume),
        value = settings.solvingVolume,
        choices = SolvingVolume.entries,
        text = { solvingVolumeText(it) },
        onChange = { mode -> actions.onUpdate { it.copy(solvingVolume = mode) } },
        supportingText = stringResource(R.string.solving_volume_hint),
        modifier = Modifier.fillMaxWidth()
    )
    SwitchRow(
        title = stringResource(R.string.lock_volume_buttons),
        description = stringResource(R.string.lock_volume_buttons_hint),
        checked = settings.lockVolumeButtons,
        onCheckedChange = { on -> actions.onUpdate { it.copy(lockVolumeButtons = on) } }
    )
}

@Composable
private fun NewAlarmSettings(defaults: AlarmDefaults, onChange: ((AlarmDefaults) -> AlarmDefaults) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    SectionTitle(stringResource(R.string.new_alarms))
    Text(stringResource(R.string.difficulty), style = MaterialTheme.typography.titleSmall)
    DifficultyChips(defaults.difficulty, { difficulty -> onChange { it.copy(difficulty = difficulty) } })
    PuzzleCountRow(
        defaults.puzzleCount,
        Alarm.MAX_PUZZLES,
        { count -> onChange { it.copy(puzzleCount = count.coerceIn(1, Alarm.MAX_PUZZLES)) } }
    )
    ValueRow(stringResource(R.string.sound), soundTitle(defaults.sound), onClick = { picking = true })
    SwitchRow(stringResource(R.string.vibrate), defaults.vibrate, { on -> onChange { it.copy(vibrate = on) } })
    ChoiceField(
        label = stringResource(R.string.auto_silence),
        value = defaults.autoSilence,
        choices = AutoSilence.CHOICES,
        text = { autoSilenceText(it) },
        onChange = { silence -> onChange { it.copy(autoSilence = silence) } },
        modifier = Modifier.fillMaxWidth()
    )
    if (picking) {
        SoundPickerDialog(
            current = defaults.sound,
            onDismiss = { picking = false },
            onPick = { sound ->
                picking = false
                onChange { it.copy(sound = sound) }
            }
        )
    }
}

@Composable
private fun AppearanceSettings(settings: Settings, actions: SettingsActions) {
    SectionTitle(stringResource(R.string.appearance))
    ChoiceField(
        label = stringResource(R.string.theme),
        value = settings.theme,
        choices = ThemeMode.entries,
        text = { themeText(it) },
        onChange = { theme -> actions.onUpdate { it.copy(theme = theme) } },
        modifier = Modifier.fillMaxWidth()
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        SwitchRow(
            title = stringResource(R.string.dynamic_color),
            description = stringResource(R.string.dynamic_color_hint),
            checked = settings.dynamicColor,
            onCheckedChange = { on -> actions.onUpdate { it.copy(dynamicColor = on) } }
        )
    }
    ChoiceField(
        label = stringResource(R.string.puzzle_look_setting),
        value = settings.puzzleLook,
        choices = PuzzleLook.entries,
        text = { lookText(it) },
        onChange = { look -> actions.onUpdate { it.copy(puzzleLook = look) } },
        modifier = Modifier.fillMaxWidth()
    )
    SwitchRow(
        title = stringResource(R.string.color_marks),
        description = stringResource(R.string.color_marks_hint),
        checked = settings.colorMarks,
        onCheckedChange = { on -> actions.onUpdate { it.copy(colorMarks = on) } }
    )
    val localeFirstDay = WeekFields.of(currentLocale()).firstDayOfWeek
    ChoiceField(
        label = stringResource(R.string.first_day_of_week),
        value = settings.firstDayOfWeek,
        choices = listOf(null, DayOfWeek.MONDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
        text = { day ->
            if (day == null) stringResource(R.string.first_day_locale, dayName(localeFirstDay)) else dayName(day)
        },
        onChange = { day -> actions.onUpdate { it.copy(firstDayOfWeek = day) } },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun About(version: String?, actions: SettingsActions) {
    SectionTitle(stringResource(R.string.about))
    ValueRow(stringResource(R.string.app_name), stringResource(R.string.version, version.orEmpty()), onClick = null)
    ValueRow(stringResource(R.string.source_code), SOURCE_CODE_URL, onClick = actions.onOpenSourceCode)
    Text(
        stringResource(R.string.about_text),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 24.dp)
    )
}

@Composable
private fun dayName(day: DayOfWeek) = day.getDisplayName(TextStyle.FULL, currentLocale())

@Composable
private fun solvingVolumeText(mode: SolvingVolume) = stringResource(
    when (mode) {
        SolvingVolume.FULL -> R.string.solving_volume_full
        SolvingVolume.LOWER -> R.string.solving_volume_lower
        SolvingVolume.MUTE -> R.string.solving_volume_mute
    }
)

@Composable
private fun themeText(theme: ThemeMode) = stringResource(
    when (theme) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    }
)

@Composable
private fun lookText(look: PuzzleLook) = stringResource(
    when (look) {
        PuzzleLook.PANEL -> R.string.look_panel
        PuzzleLook.MATERIAL -> R.string.look_material
    }
)

private const val SOURCE_CODE_URL = "https://github.com/EmilMelgaard/pathwake"
internal const val SETTINGS_TAG = "settings"
