package io.github.emilmelgaard.pathwake.ui.common

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Shows snackbars from an app-wide scope, so a message (and its undo action) survives the screen that triggered it,
 * e.g. an editor that closes right after saving.
 */
class Messenger(private val scope: CoroutineScope, val hostState: SnackbarHostState) {
    fun show(message: String, actionLabel: String? = null, onAction: suspend () -> Unit = {}) {
        scope.launch {
            hostState.currentSnackbarData?.dismiss()
            val result = hostState.showSnackbar(message, actionLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) onAction()
        }
    }
}

val LocalMessenger = staticCompositionLocalOf<Messenger> { error("No Messenger provided") }

/**
 * Keeps unsaved edits from being lost when another alarm is opened (tablets show the list next to the editor).
 * The editor reports whether it has changes; navigation that would drop them goes through [runOrConfirm].
 */
class UnsavedChangesGuard {
    var hasUnsavedChanges by mutableStateOf(false)

    /** Navigation waiting for the user to confirm discarding changes. */
    var pending by mutableStateOf<(() -> Unit)?>(null)
        private set

    fun runOrConfirm(action: () -> Unit) {
        if (hasUnsavedChanges) pending = action else action()
    }

    fun discardAndContinue() {
        val action = pending
        pending = null
        hasUnsavedChanges = false
        action?.invoke()
    }

    fun keepEditing() {
        pending = null
    }
}

val LocalUnsavedChangesGuard = staticCompositionLocalOf { UnsavedChangesGuard() }
