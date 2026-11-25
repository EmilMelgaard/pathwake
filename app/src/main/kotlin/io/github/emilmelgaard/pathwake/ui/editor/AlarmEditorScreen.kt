package io.github.emilmelgaard.pathwake.ui.editor

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TimePickerDialogDefaults
import androidx.compose.material3.TimePickerDisplayMode
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.emilmelgaard.pathwake.R
import io.github.emilmelgaard.pathwake.alarm.Alarm
import io.github.emilmelgaard.pathwake.alarm.DateRange
import io.github.emilmelgaard.pathwake.appContainer
import io.github.emilmelgaard.pathwake.puzzle.Difficulty
import io.github.emilmelgaard.pathwake.system.AlarmPermissions
import io.github.emilmelgaard.pathwake.ui.common.LocalMessenger
import io.github.emilmelgaard.pathwake.ui.common.LocalUnsavedChangesGuard
import io.github.emilmelgaard.pathwake.ui.common.countdown
import io.github.emilmelgaard.pathwake.ui.common.currentLocale
import io.github.emilmelgaard.pathwake.ui.common.difficultyName
import io.github.emilmelgaard.pathwake.ui.common.firstDayOfWeek
import io.github.emilmelgaard.pathwake.ui.common.formatCountdown
import io.github.emilmelgaard.pathwake.ui.common.formatDate
import io.github.emilmelgaard.pathwake.ui.common.formatDayName
import io.github.emilmelgaard.pathwake.ui.common.formatTime
import io.github.emilmelgaard.pathwake.ui.common.formatWhen
import io.github.emilmelgaard.pathwake.ui.common.orderedWeek
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.TextStyle

/** Callbacks of the alarm editor. */
data class AlarmEditorActions(
    val onClose: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onTryPuzzle: (Difficulty) -> Unit = {},
    val onTimeChange: (LocalTime) -> Unit = {},
    val onLabelChange: (String) -> Unit = {},
    val onToggleDay: (DayOfWeek) -> Unit = {},
    val onDateChange: (LocalDate?) -> Unit = {},
    val onAddPause: (DateRange) -> Unit = {},
    val onRemovePause: (DateRange) -> Unit = {},
    val onSkipNextChange: (Boolean) -> Unit = {},
    val onDifficultyChange: (Difficulty) -> Unit = {},
    val onPuzzleCountChange: (Int) -> Unit = {},
    val onAutoSilenceChange: (Int?) -> Unit = {}
)

@Composable
fun AlarmEditorScreen(
    alarmId: Long,
    onClose: () -> Unit,
    onTryPuzzle: (Difficulty) -> Unit,
    modifier: Modifier = Modifier
) {
    val container = LocalContext.current.appContainer
    val viewModel: AlarmEditorViewModel = viewModel {
        AlarmEditorViewModel(alarmId, container.alarmRepository, container.now)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val messenger = LocalMessenger.current
    val guard = LocalUnsavedChangesGuard.current
    val resources = LocalResources.current

    LaunchedEffect(state.isDirty) { guard.hasUnsavedChanges = state.isDirty }
    DisposableEffect(guard) { onDispose { guard.hasUnsavedChanges = false } }
    LaunchedEffect(state.missing) { if (state.missing) onClose() }
    BackHandler(enabled = state.isDirty) { guard.runOrConfirm(onClose) }

    fun save() = viewModel.save { saved ->
        guard.hasUnsavedChanges = false
        messenger.show(
            saved.timeUntil?.let { resources.getString(R.string.alarm_set, resources.countdown(it)) }
                ?: resources.getString(R.string.alarm_saved)
        )
        onClose()
    }
    val context = LocalContext.current
    // Saves whether or not notifications are allowed; the alarm list explains what is missing.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        save()
    }

    AlarmEditorContent(
        state = state,
        actions = AlarmEditorActions(
            onClose = { guard.runOrConfirm(onClose) },
            onSave = {
                // A ringing alarm needs notifications to show its puzzles: ask once, when the first alarm is saved.
                if (AlarmPermissions.needsNotificationPermission(context) &&
                    !container.preferences.notificationPermissionRequested
                ) {
                    container.preferences.notificationPermissionRequested = true
                    AlarmPermissions.requestNotifications(notificationPermission)
                } else {
                    save()
                }
            },
            onDelete = {
                viewModel.delete { alarm ->
                    guard.hasUnsavedChanges = false
                    messenger.show(resources.getString(R.string.alarm_deleted), resources.getString(R.string.undo)) {
                        container.alarmRepository.restore(alarm)
                    }
                    onClose()
                }
            },
            onTryPuzzle = onTryPuzzle,
            onTimeChange = viewModel::setTime,
            onLabelChange = viewModel::setLabel,
            onToggleDay = viewModel::toggleDay,
            onDateChange = viewModel::setDate,
            onAddPause = viewModel::addPause,
            onRemovePause = viewModel::removePause,
            onSkipNextChange = viewModel::setSkipNext,
            onDifficultyChange = viewModel::setDifficulty,
            onPuzzleCountChange = viewModel::setPuzzleCount,
            onAutoSilenceChange = viewModel::setAutoSilence
        ),
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditorContent(state: AlarmEditorUiState, actions: AlarmEditorActions, modifier: Modifier = Modifier) {
    val inputModeManager = LocalInputModeManager.current
    Scaffold(
        // The list-detail scaffold moves focus into the detail pane when its layout changes (e.g. when a phone is
        // rotated). On touch screens the editor itself takes that focus; otherwise the label would get it and pop up
        // the keyboard. With a keyboard, focus passes on to the first control as usual.
        modifier = modifier
            .focusProperties { canFocus = inputModeManager.inputMode == InputMode.Touch }
            .focusTarget(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isNew) R.string.new_alarm else R.string.edit_alarm)) },
                navigationIcon = {
                    IconButton(onClick = actions.onClose) {
                        Icon(Icons.Filled.Close, stringResource(R.string.close))
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = actions.onDelete) {
                            Icon(Icons.Filled.Delete, stringResource(R.string.delete_alarm))
                        }
                    }
                    TextButton(onClick = actions.onSave, enabled = !state.loading) {
                        Text(stringResource(R.string.save))
                    }
                }
            )
        }
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag(ALARM_EDITOR_TAG),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TimeHeader(state, actions.onTimeChange)
                OutlinedTextField(
                    value = state.alarm.label,
                    onValueChange = { actions.onLabelChange(it.take(MAX_LABEL_LENGTH)) },
                    label = { Text(stringResource(R.string.label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                HorizontalDivider()
                RepeatSection(state, actions)
                HorizontalDivider()
                PuzzleSection(state.alarm, actions)
                HorizontalDivider()
                RingingSection(state.alarm, actions)
                Spacer(Modifier.padding(bottom = 16.dp))
            }
        }
    }
}

@Composable
private fun TimeHeader(state: AlarmEditorUiState, onTimeChange: (LocalTime) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val time = formatTime(state.alarm.time)
    val changeTime = stringResource(R.string.change_time, time)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = { picking = true },
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.semantics { contentDescription = changeTime }
        ) {
            Text(
                time,
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp)
            )
        }
        val next = state.next
        val timeUntil = state.timeUntil
        val today = state.today
        Text(
            if (next != null && timeUntil != null && today != null) {
                stringResource(R.string.rings_at, formatWhen(next, today), formatCountdown(timeUntil))
            } else {
                stringResource(R.string.wont_ring)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = if (next == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
    if (picking) {
        TimeDialog(
            initial = state.alarm.time,
            onConfirm = {
                picking = false
                onTimeChange(it)
            },
            onDismiss = { picking = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(initial: LocalTime, onConfirm: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initial.hour, initial.minute, DateFormat.is24HourFormat(LocalContext.current))
    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
    var displayMode by remember {
        mutableStateOf(
            if (windowHeight < TimePickerDialogDefaults.MinHeightForTimePicker) {
                TimePickerDisplayMode.Input
            } else {
                TimePickerDisplayMode.Picker
            }
        )
    }
    TimePickerDialog(
        onDismissRequest = onDismiss,
        title = { TimePickerDialogDefaults.Title(displayMode) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
        modeToggleButton = {
            TimePickerDialogDefaults.DisplayModeToggle(
                onDisplayModeChange = {
                    displayMode = if (displayMode == TimePickerDisplayMode.Picker) {
                        TimePickerDisplayMode.Input
                    } else {
                        TimePickerDisplayMode.Picker
                    }
                },
                displayMode = displayMode
            )
        }
    ) {
        if (displayMode == TimePickerDisplayMode.Picker) TimePicker(state) else TimeInput(state)
    }
}

@Composable
private fun RepeatSection(state: AlarmEditorUiState, actions: AlarmEditorActions) {
    val alarm = state.alarm
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.repeat), style = MaterialTheme.typography.titleMedium)
        DayToggles(alarm.repeatDays, actions.onToggleDay)
        if (alarm.repeats) {
            PausesRow(alarm.pauses, state.today, actions)
            SkipNextRow(state.skippedDate, actions.onSkipNextChange)
        } else {
            DateRow(alarm.date, state.today, state.next?.toLocalDate(), actions.onDateChange)
        }
    }
}

@Composable
private fun DayToggles(selected: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit) {
    val locale = currentLocale()
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        orderedWeek(firstDayOfWeek()).forEach { day ->
            val checked = day in selected
            val name = day.getDisplayName(TextStyle.FULL, locale)
            Surface(
                checked = checked,
                onCheckedChange = { onToggle(day) },
                shape = CircleShape,
                color = if (checked) colors.primary else colors.surfaceContainerHighest,
                contentColor = if (checked) colors.onPrimary else colors.onSurfaceVariant,
                modifier = Modifier.size(40.dp).semantics { contentDescription = name }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(formatDayName(day), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRow(date: LocalDate?, today: LocalDate?, nextDate: LocalDate?, onDateChange: (LocalDate?) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { picking = true }) {
            Icon(Icons.Filled.DateRange, contentDescription = null)
            Text(
                if (date == null) stringResource(R.string.pick_date) else formatDate(date),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        if (date != null) {
            IconButton(onClick = { onDateChange(null) }) {
                Icon(Icons.Filled.Clear, stringResource(R.string.clear_date))
            }
        }
    }
    if (picking && today != null) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (date ?: nextDate ?: today).toUtcMillis(),
            selectableDates = fromDay(today)
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        picking = false
                        onDateChange(state.selectedDateMillis?.toLocalDate())
                    }
                ) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { picking = false }) { Text(stringResource(android.R.string.cancel)) }
            }
        ) {
            DatePicker(state)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PausesRow(pauses: List<DateRange>, today: LocalDate?, actions: AlarmEditorActions) {
    var picking by rememberSaveable { mutableStateOf(false) }
    Column {
        Text(stringResource(R.string.pauses), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.pauses_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pauses.forEach { pause ->
                val text = stringResource(R.string.date_range, formatDate(pause.start), formatDate(pause.end))
                val remove = stringResource(R.string.remove_pause, text)
                InputChip(
                    selected = false,
                    onClick = { actions.onRemovePause(pause) },
                    label = { Text(text) },
                    trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null, Modifier.size(18.dp)) },
                    modifier = Modifier.semantics { contentDescription = remove }
                )
            }
            TextButton(onClick = { picking = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.add_pause), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
    if (picking && today != null) {
        val state = rememberDateRangePickerState(selectableDates = fromDay(today))
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        picking = false
                        val start = state.selectedStartDateMillis?.toLocalDate()
                        if (start != null) {
                            actions.onAddPause(DateRange(start, state.selectedEndDateMillis?.toLocalDate() ?: start))
                        }
                    },
                    enabled = state.selectedStartDateMillis != null
                ) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { picking = false }) { Text(stringResource(android.R.string.cancel)) }
            }
        ) {
            DateRangePicker(
                state,
                title = { Text(stringResource(R.string.add_pause), Modifier.padding(start = 24.dp, top = 16.dp)) },
                showModeToggle = false,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SkipNextRow(skippedDate: LocalDate?, onSkipNextChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.skip_next), style = MaterialTheme.typography.titleSmall)
            if (skippedDate != null) {
                Text(
                    stringResource(R.string.skipping_on, formatDate(skippedDate)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        val description = stringResource(R.string.skip_next)
        Switch(
            checked = skippedDate != null,
            onCheckedChange = onSkipNextChange,
            modifier = Modifier.semantics { contentDescription = description }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PuzzleSection(alarm: Alarm, actions: AlarmEditorActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.puzzles_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.difficulty), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Difficulty.entries.forEach { difficulty ->
                FilterChip(
                    selected = difficulty == alarm.difficulty,
                    onClick = { actions.onDifficultyChange(difficulty) },
                    label = { Text(difficultyName(difficulty)) }
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.puzzles_to_solve),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            val fewer = stringResource(R.string.fewer_puzzles)
            FilledTonalIconButton(
                onClick = { actions.onPuzzleCountChange(alarm.puzzleCount - 1) },
                enabled = alarm.puzzleCount > 1,
                modifier = Modifier.semantics { contentDescription = fewer }
            ) {
                Text("−", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                alarm.puzzleCount.toString(),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(48.dp).testTag(PUZZLE_COUNT_TAG)
            )
            FilledTonalIconButton(
                onClick = { actions.onPuzzleCountChange(alarm.puzzleCount + 1) },
                enabled = alarm.puzzleCount < Alarm.MAX_PUZZLES
            ) {
                Icon(Icons.Filled.Add, stringResource(R.string.more_puzzles))
            }
        }
        OutlinedButton(onClick = { actions.onTryPuzzle(alarm.difficulty) }) {
            Icon(painterResource(R.drawable.ic_puzzle), contentDescription = null, Modifier.size(18.dp))
            Text(stringResource(R.string.try_puzzle), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RingingSection(alarm: Alarm, actions: AlarmEditorActions) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.ringing_title), style = MaterialTheme.typography.titleMedium)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = autoSilenceText(alarm.autoSilenceMinutes),
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                label = { Text(stringResource(R.string.auto_silence)) },
                supportingText = { Text(stringResource(R.string.auto_silence_hint)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .testTag(AUTO_SILENCE_TAG)
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                Alarm.AUTO_SILENCE_CHOICES.forEach { minutes ->
                    DropdownMenuItem(
                        text = { Text(autoSilenceText(minutes)) },
                        onClick = {
                            expanded = false
                            actions.onAutoSilenceChange(minutes)
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
    }
}

@Composable
private fun autoSilenceText(minutes: Int?): String = if (minutes == null) {
    stringResource(R.string.auto_silence_never)
} else {
    pluralStringResource(R.plurals.auto_silence_after, minutes, minutes)
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** Date pickers work with UTC midnight; only [today] and later can be picked. */
@OptIn(ExperimentalMaterial3Api::class)
private fun fromDay(today: LocalDate) = object : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long) = !utcTimeMillis.toLocalDate().isBefore(today)

    override fun isSelectableYear(year: Int) = year >= today.year
}

private const val MAX_LABEL_LENGTH = 60
internal const val ALARM_EDITOR_TAG = "alarmEditor"
internal const val PUZZLE_COUNT_TAG = "puzzleCount"
internal const val AUTO_SILENCE_TAG = "autoSilence"
