package io.github.emilmelgaard.pathwake.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import io.github.emilmelgaard.pathwake.R
import io.github.emilmelgaard.pathwake.puzzle.Difficulty

/** A read-only field that opens a menu of [choices]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ChoiceField(
    label: String,
    value: T,
    choices: List<T>,
    text: @Composable (T) -> String,
    onChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = text(value),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            supportingText = supportingText?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(text(choice)) },
                    onClick = {
                        expanded = false
                        onChange(choice)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

/** A row with a title, an optional description and a switch. The whole row toggles it. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = description?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier
            .bleed()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
    )
}

/** A row showing a value, which opens something to change it when [onClick] is given. */
@Composable
fun ValueRow(title: String, value: String, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(value) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.bleed().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    )
}

/** Moves a list item's own side padding out into the margin, so its text lines up with the content around it. */
private fun Modifier.bleed(): Modifier = layout { measurable, constraints ->
    val inset = LIST_ITEM_INSET.roundToPx()
    val placeable = measurable.measure(constraints.offset(horizontal = 2 * inset))
    layout(placeable.width - 2 * inset, placeable.height) { placeable.place(-inset, 0) }
}

private val LIST_ITEM_INSET = 16.dp

/** An alarm volume [level] on the device's alarm volume scale. */
data class VolumeState(val level: Int, val min: Int, val max: Int)

/** An alarm volume slider with a button that plays (or stops) a sound to try it. */
@Composable
fun VolumeSlider(
    volume: VolumeState,
    testing: Boolean,
    onLevelChange: (Int) -> Unit,
    onTest: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val description = stringResource(R.string.alarm_volume)
        Slider(
            value = volume.level.toFloat(),
            onValueChange = { onLevelChange(Math.round(it)) },
            valueRange = volume.min.toFloat()..volume.max.toFloat().coerceAtLeast(volume.min + 1f),
            steps = (volume.max - volume.min - 1).coerceAtLeast(0),
            modifier = Modifier.weight(1f).semantics { contentDescription = description }
        )
        TextButton(onClick = { onTest(!testing) }) {
            Text(stringResource(if (testing) R.string.stop_test else R.string.test_sound))
        }
    }
}

/** "Puzzles to solve  [−] 2 [+]". */
@Composable
fun PuzzleCountRow(count: Int, max: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.puzzles_to_solve),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
        )
        val fewer = stringResource(R.string.fewer_puzzles)
        FilledTonalIconButton(
            onClick = { onChange(count - 1) },
            enabled = count > 1,
            modifier = Modifier.semantics { contentDescription = fewer }
        ) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            count.toString(),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(48.dp).testTag(PUZZLE_COUNT_TAG)
        )
        FilledTonalIconButton(onClick = { onChange(count + 1) }, enabled = count < max) {
            Icon(Icons.Filled.Add, stringResource(R.string.more_puzzles))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DifficultyChips(selected: Difficulty, onSelect: (Difficulty) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Difficulty.entries.forEach { difficulty ->
            FilterChip(
                selected = difficulty == selected,
                onClick = { onSelect(difficulty) },
                label = { Text(difficultyName(difficulty)) }
            )
        }
    }
}

internal const val PUZZLE_COUNT_TAG = "puzzleCount"
