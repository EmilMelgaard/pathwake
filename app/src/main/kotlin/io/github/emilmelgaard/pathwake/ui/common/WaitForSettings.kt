package io.github.emilmelgaard.pathwake.ui.common

import android.app.Activity
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import io.github.emilmelgaard.pathwake.settings.SettingsRepository

private const val MAX_WAIT_MILLIS = 1000L

/**
 * Holds back the activity's first frame (the splash screen stays up) until [settings] are read, so the first screen
 * already has the right theme and puzzle look. Gives up after a second.
 */
fun Activity.drawAfterLoading(settings: SettingsRepository) {
    if (settings.isLoaded) return
    val content = findViewById<View>(android.R.id.content)
    val start = SystemClock.uptimeMillis()
    content.viewTreeObserver.addOnPreDrawListener(
        object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                val ready = settings.isLoaded || SystemClock.uptimeMillis() - start > MAX_WAIT_MILLIS
                if (ready) content.viewTreeObserver.removeOnPreDrawListener(this)
                return ready
            }
        }
    )
}
