package io.github.emilmelgaard.pathwake.ui.ringing

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.emilmelgaard.pathwake.appContainer
import io.github.emilmelgaard.pathwake.ui.theme.PathwakeTheme

/**
 * The puzzles that stop a ringing alarm. Shows over the lock screen and turns the screen on (see the manifest), and
 * works before the device is first unlocked after a reboot.
 */
class RingingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The ringing screen is always dark, so the system bars need light icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Back doesn't stop the alarm; only solving the puzzles does.
        onBackPressedDispatcher.addCallback(this) { }
        setContent {
            PathwakeTheme(darkTheme = true) {
                RingingScreen(onFinished = ::finish)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Turning the volume down is blocked while ringing (if set); the ringing service also undoes it from elsewhere.
        val locked = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE
        if (locked && appContainer.settings.current.value.lockVolumeButtons) return true
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, RingingActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
    }
}
