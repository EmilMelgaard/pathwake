package io.github.emilmelgaard.pathwake

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.emilmelgaard.pathwake.ui.alarms.AlarmDetailPlaceholder
import io.github.emilmelgaard.pathwake.ui.alarms.AlarmListScreen
import io.github.emilmelgaard.pathwake.ui.common.LocalMessenger
import io.github.emilmelgaard.pathwake.ui.common.LocalUnsavedChangesGuard
import io.github.emilmelgaard.pathwake.ui.common.Messenger
import io.github.emilmelgaard.pathwake.ui.common.UnsavedChangesGuard
import io.github.emilmelgaard.pathwake.ui.editor.AlarmEditorScreen
import io.github.emilmelgaard.pathwake.ui.playground.PlaygroundScreen
import io.github.emilmelgaard.pathwake.ui.settings.SettingsScreen

/**
 * The alarm list with the editor as its detail: side by side on large screens, one at a time on small ones.
 * The puzzle playground opens full screen on top.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun MainNavigation() {
    val backStack = rememberNavBackStack(AlarmList)
    val scope = rememberCoroutineScope()
    val messenger = remember { Messenger(scope, SnackbarHostState()) }
    val guard = remember { UnsavedChangesGuard() }

    // Only one editor at a time: opening another alarm replaces it (after confirming unsaved changes).
    fun openEditor(alarmId: Long) {
        val key = AlarmEditor(alarmId)
        if (backStack.lastOrNull() == key) return
        guard.runOrConfirm {
            backStack.removeAll { it is AlarmEditor }
            backStack.add(key)
        }
    }

    fun closeEditor(key: AlarmEditor) {
        guard.hasUnsavedChanges = false
        backStack.removeAll { it == key }
    }

    CompositionLocalProvider(LocalMessenger provides messenger, LocalUnsavedChangesGuard provides guard) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator()
            ),
            sceneStrategies = listOf(rememberListDetailSceneStrategy<NavKey>()),
            entryProvider = entryProvider {
                entry<AlarmList>(
                    metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { AlarmDetailPlaceholder() })
                ) {
                    AlarmListScreen(
                        selectedAlarmId = (backStack.lastOrNull() as? AlarmEditor)?.alarmId,
                        onOpenAlarm = ::openEditor,
                        onAddAlarm = { openEditor(AlarmEditor.NEW) },
                        onOpenPlayground = { backStack.add(Playground()) },
                        onOpenSettings = { backStack.add(Settings) },
                        onAlarmDeleted = { closeEditor(AlarmEditor(it)) }
                    )
                }
                entry<AlarmEditor>(metadata = ListDetailSceneStrategy.detailPane()) { key ->
                    AlarmEditorScreen(
                        alarmId = key.alarmId,
                        onClose = { closeEditor(key) },
                        onTryPuzzle = { backStack.add(Playground(it)) }
                    )
                }
                entry<Playground> { key ->
                    PlaygroundScreen(initialDifficulty = key.difficulty, onBack = { backStack.remove(key) })
                }
                entry<Settings> { SettingsScreen(onBack = { backStack.remove(Settings) }) }
            }
        )
        if (guard.pending != null) {
            DiscardChangesDialog(onDiscard = guard::discardAndContinue, onKeepEditing = guard::keepEditing)
        }
    }
}

@Composable
private fun DiscardChangesDialog(onDiscard: () -> Unit, onKeepEditing: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = { Text(stringResource(R.string.discard_changes_title)) },
        text = { Text(stringResource(R.string.discard_changes_body)) },
        confirmButton = { TextButton(onClick = onDiscard) { Text(stringResource(R.string.discard)) } },
        dismissButton = { TextButton(onClick = onKeepEditing) { Text(stringResource(R.string.keep_editing)) } }
    )
}
