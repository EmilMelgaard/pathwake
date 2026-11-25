package io.github.emilmelgaard.pathwake.system

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.os.ParcelFileDescriptor
import android.util.Log
import io.github.emilmelgaard.pathwake.AppContainer
import kotlinx.coroutines.runBlocking

/**
 * Auto Backup (rules in `res/xml`) with one addition: restored alarms are set right away, so they ring even if the
 * app isn't opened on the new device. The app runs in a restricted mode during a restore (without its Application),
 * so this uses its own [AppContainer].
 */
class RestoreAgent : BackupAgent() {
    // Only full (Auto) backups are used; these are for the older key-value backups.
    override fun onBackup(oldState: ParcelFileDescriptor?, data: BackupDataOutput?, newState: ParcelFileDescriptor?) =
        Unit

    override fun onRestore(data: BackupDataInput?, appVersionCode: Int, newState: ParcelFileDescriptor?) = Unit

    override fun onRestoreFinished() {
        try {
            val container = AppContainer(this)
            runBlocking {
                container.forgetMissingSounds()
                container.scheduler.sync()
            }
        } catch (e: Exception) {
            // The alarms are set anyway the next time the app starts.
            Log.w("RestoreAgent", "Can't set the restored alarms", e)
        }
    }
}
