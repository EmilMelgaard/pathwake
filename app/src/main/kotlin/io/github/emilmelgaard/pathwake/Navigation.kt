package io.github.emilmelgaard.pathwake

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import io.github.emilmelgaard.pathwake.ui.playground.PlaygroundScreen

@Composable
fun MainNavigation() {
    val backStack = rememberNavBackStack(Playground)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<Playground> { PlaygroundScreen() }
        }
    )
}
