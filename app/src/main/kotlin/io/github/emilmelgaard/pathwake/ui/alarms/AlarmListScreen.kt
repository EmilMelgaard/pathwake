package io.github.emilmelgaard.pathwake.ui.alarms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.emilmelgaard.pathwake.R
import io.github.emilmelgaard.pathwake.alarm.Alarm
import io.github.emilmelgaard.pathwake.appContainer
import io.github.emilmelgaard.pathwake.ui.common.LocalMessenger
import io.github.emilmelgaard.pathwake.ui.common.formatCountdown
import io.github.emilmelgaard.pathwake.ui.common.formatDate
import io.github.emilmelgaard.pathwake.ui.common.formatTime
import io.github.emilmelgaard.pathwake.ui.common.formatWhen
import io.github.emilmelgaard.pathwake.ui.common.puzzleText
import io.github.emilmelgaard.pathwake.ui.common.repeatText

/** Callbacks of the alarm list. */
data class AlarmListActions(
    val onOpen: (Alarm) -> Unit = {},
    val onAdd: () -> Unit = {},
    val onOpenPlayground: () -> Unit = {},
    val onToggle: (Alarm, Boolean) -> Unit = { _, _ -> },
    val onSkipNext: (Alarm) -> Unit = {},
    val onCancelSkip: (Alarm) -> Unit = {},
    val onDuplicate: (Alarm) -> Unit = {},
    val onDelete: (Alarm) -> Unit = {}
)

@Composable
fun AlarmListScreen(
    selectedAlarmId: Long?,
    onOpenAlarm: (Long) -> Unit,
    onAddAlarm: () -> Unit,
    onOpenPlayground: () -> Unit,
    onAlarmDeleted: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val container = LocalContext.current.appContainer
    val viewModel: AlarmListViewModel = viewModel { AlarmListViewModel(container.alarmRepository, container.now) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val messenger = LocalMessenger.current
    val deleted = stringResource(R.string.alarm_deleted)
    val undo = stringResource(R.string.undo)
    AlarmListContent(
        state = state,
        selectedAlarmId = selectedAlarmId,
        actions = AlarmListActions(
            onOpen = { onOpenAlarm(it.id) },
            onAdd = onAddAlarm,
            onOpenPlayground = onOpenPlayground,
            onToggle = viewModel::setEnabled,
            onSkipNext = viewModel::skipNext,
            onCancelSkip = viewModel::cancelSkip,
            onDuplicate = viewModel::duplicate,
            onDelete = { alarm ->
                viewModel.delete(alarm)
                onAlarmDeleted(alarm.id)
                messenger.show(deleted, undo) { container.alarmRepository.restore(alarm) }
            }
        ),
        snackbarHostState = messenger.hostState,
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmListContent(
    state: AlarmListUiState,
    selectedAlarmId: Long?,
    actions: AlarmListActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = actions.onOpenPlayground) {
                        Icon(painterResource(R.drawable.ic_puzzle), stringResource(R.string.try_puzzles))
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = actions.onAdd,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_alarm)) }
            )
        }
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator()
            }

            state.items.isEmpty() -> EmptyAlarms(Modifier.fillMaxSize().padding(padding))

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().testTag(ALARM_LIST_TAG),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { NextAlarmHeader(state) }
                items(state.items, key = { it.alarm.id }) { item ->
                    AlarmCard(item, selected = item.alarm.id == selectedAlarmId, actions = actions)
                }
            }
        }
    }
}

@Composable
private fun NextAlarmHeader(state: AlarmListUiState) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        val upcoming = state.upcoming
        val today = state.now?.toLocalDate()
        if (upcoming == null || today == null) {
            Text(stringResource(R.string.no_alarms_on), style = MaterialTheme.typography.titleMedium)
        } else {
            Text(stringResource(R.string.next_alarm), style = MaterialTheme.typography.labelLarge)
            Text(formatWhen(upcoming.at, today), style = MaterialTheme.typography.headlineSmall)
            Text(formatCountdown(upcoming.timeUntil), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AlarmCard(item: AlarmListItem, selected: Boolean, actions: AlarmListActions) {
    val alarm = item.alarm
    val colors = if (selected) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    } else {
        CardDefaults.cardColors()
    }
    Card(
        onClick = { actions.onOpen(alarm) },
        colors = colors,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Row(
            Modifier.padding(start = 20.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                val dim = if (alarm.enabled) 1f else 0.6f
                val contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = dim)
                Text(formatTime(alarm.time), style = MaterialTheme.typography.displaySmall, color = contentColor)
                if (alarm.label.isNotBlank()) {
                    Text(alarm.label, style = MaterialTheme.typography.titleMedium, color = contentColor)
                }
                Text(
                    "${repeatText(alarm)} · ${puzzleText(alarm)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AlarmStatus(item)
            }
            val timeText = formatTime(alarm.time)
            val switchDescription = stringResource(R.string.alarm_switch, timeText)
            Switch(
                checked = alarm.enabled,
                onCheckedChange = { actions.onToggle(alarm, it) },
                modifier = Modifier.semantics { contentDescription = switchDescription }
            )
            AlarmMenu(item, actions)
        }
    }
}

@Composable
private fun AlarmStatus(item: AlarmListItem) {
    val alarm = item.alarm
    val lines = buildList {
        item.skippedDate?.let { add(stringResource(R.string.skipping_on, formatDate(it))) }
        item.pauses.firstOrNull()?.let {
            add(stringResource(R.string.paused_range, formatDate(it.start), formatDate(it.end)))
        }
        if (alarm.enabled && item.next == null) add(stringResource(R.string.wont_ring))
    }
    lines.forEach {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
    }
}

@Composable
private fun AlarmMenu(item: AlarmListItem, actions: AlarmListActions) {
    var expanded by remember { mutableStateOf(false) }
    val alarm = item.alarm
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (alarm.repeats && alarm.enabled) {
                if (item.skippedDate == null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.skip_next)) },
                        onClick = {
                            expanded = false
                            actions.onSkipNext(alarm)
                        }
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.cancel_skip)) },
                        onClick = {
                            expanded = false
                            actions.onCancelSkip(alarm)
                        }
                    )
                }
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.duplicate)) },
                onClick = {
                    expanded = false
                    actions.onDuplicate(alarm)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete)) },
                onClick = {
                    expanded = false
                    actions.onDelete(alarm)
                }
            )
        }
    }
}

@Composable
private fun EmptyAlarms(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                // Keeps the text clear of the floating action button on short screens.
                .padding(start = 32.dp, top = 32.dp, end = 32.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            EmptyAlarmsText()
        }
    }
}

@Composable
private fun EmptyAlarmsText() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            painterResource(R.drawable.ic_puzzle),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            stringResource(R.string.no_alarms_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            stringResource(R.string.no_alarms_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/** Shown in the detail pane on large screens when no alarm is open. */
@Composable
fun AlarmDetailPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.select_alarm),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

internal const val ALARM_LIST_TAG = "alarmList"
