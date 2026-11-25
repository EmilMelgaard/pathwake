package io.github.emilmelgaard.pathwake

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.emilmelgaard.pathwake.settings.ThemeMode
import io.github.emilmelgaard.pathwake.ui.common.LocalFirstDayOfWeek
import io.github.emilmelgaard.pathwake.ui.common.drawAfterLoading
import io.github.emilmelgaard.pathwake.ui.theme.PathwakeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        val settingsRepository = appContainer.settings
        drawAfterLoading(settingsRepository)
        setContent {
            val settings by settingsRepository.current.collectAsStateWithLifecycle()
            val dark = when (settings.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // The system bar icons follow the app's theme, which may differ from the system's.
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            PathwakeTheme(darkTheme = dark, dynamicColor = settings.dynamicColor) {
                CompositionLocalProvider(LocalFirstDayOfWeek provides settings.firstDayOfWeek) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        MainNavigation()
                    }
                }
            }
        }
    }
}
