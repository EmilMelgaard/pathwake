package io.github.emilmelgaard.pathwake.ui.sound

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.emilmelgaard.pathwake.R
import io.github.emilmelgaard.pathwake.alarm.AlarmSound
import io.github.emilmelgaard.pathwake.appContainer
import io.github.emilmelgaard.pathwake.system.AlarmPlayer
import io.github.emilmelgaard.pathwake.system.SoundLibrary
import io.github.emilmelgaard.pathwake.ui.common.soundTitle
import kotlinx.coroutines.launch

/**
 * Lets the user pick an alarm sound: the default, silent, a sound file they added (or add a new one) or one of the
 * device's alarm tones. Tapping a sound plays it once, at alarm volume.
 */
@Composable
fun SoundPickerDialog(current: AlarmSound, onDismiss: () -> Unit, onPick: (AlarmSound) -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val library = container.soundLibrary
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(current) }
    var error by remember { mutableStateOf<Int?>(null) }
    var adding by remember { mutableStateOf(false) }
    var customVersion by remember { mutableIntStateOf(0) }
    val custom by produceState(emptyList<AlarmSound.Custom>(), customVersion) { value = library.custom() }
    val tones by produceState<List<AlarmSound.Tone>?>(null) { value = library.deviceTones() }

    val player = remember { AlarmPlayer(context, withWakeLock = false) }
    DisposableEffect(player) { onDispose { player.stop() } }
    fun select(sound: AlarmSound) {
        selected = sound
        error = null
        val source = library.sources(sound).all.firstOrNull()
        if (source == null) player.stop() else player.play(source, loop = false)
    }

    val addFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        adding = true
        scope.launch {
            try {
                val added = library.add(uri)
                customVersion++
                select(added)
            } catch (_: SoundLibrary.TooLargeException) {
                error = R.string.sound_too_large
            } catch (_: Exception) {
                error = R.string.sound_add_failed
            } finally {
                adding = false
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sound)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 480.dp).testTag(SOUND_LIST_TAG)) {
                item { SoundRow(AlarmSound.Default, selected, ::select) }
                item { SoundRow(AlarmSound.Silent, selected, ::select) }
                item { Header(stringResource(R.string.your_sounds)) }
                items(custom, key = { it.fileName }) { sound ->
                    SoundRow(sound, selected, ::select) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    container.deleteSound(sound)
                                    if (selected == sound) selected = AlarmSound.Default
                                    customVersion++
                                }
                            }
                        ) {
                            Icon(Icons.Filled.Delete, stringResource(R.string.delete_sound, sound.title))
                        }
                    }
                }
                item {
                    TextButton(onClick = { addFile.launch(arrayOf("audio/*")) }, enabled = !adding) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(stringResource(R.string.add_sound_file), modifier = Modifier.padding(start = 8.dp))
                    }
                    if (adding) LinearProgressIndicator(Modifier.fillMaxWidth())
                    error?.let {
                        Text(
                            stringResource(it),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                item { Header(stringResource(R.string.device_tones)) }
                val deviceTones = tones
                if (deviceTones == null) {
                    item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp)) }
                } else {
                    items(deviceTones, key = { it.uri }) { SoundRow(it, selected, ::select) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(selected) }) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } }
    )
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp).semantics { heading() }
    )
}

@Composable
private fun SoundRow(
    sound: AlarmSound,
    selected: AlarmSound,
    onSelect: (AlarmSound) -> Unit,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = sound == selected, role = Role.RadioButton, onClick = { onSelect(sound) }),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = sound == selected, onClick = null, modifier = Modifier.padding(12.dp))
        Text(soundTitle(sound), modifier = Modifier.weight(1f))
        trailing()
    }
}

internal const val SOUND_LIST_TAG = "soundList"
