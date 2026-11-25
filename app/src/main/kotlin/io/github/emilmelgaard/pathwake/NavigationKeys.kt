package io.github.emilmelgaard.pathwake

import androidx.navigation3.runtime.NavKey
import io.github.emilmelgaard.pathwake.puzzle.Difficulty
import kotlinx.serialization.Serializable

@Serializable
data object AlarmList : NavKey

/** Edits the alarm with [alarmId], or a new alarm if it is [NEW]. */
@Serializable
data class AlarmEditor(val alarmId: Long = NEW) : NavKey {
    companion object {
        const val NEW = 0L
    }
}

@Serializable
data class Playground(val difficulty: Difficulty = Difficulty.MEDIUM) : NavKey

@Serializable
data object Settings : NavKey
