package io.github.emilmelgaard.pathwake.system

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import io.github.emilmelgaard.pathwake.alarm.AlarmSound
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The sounds alarms can play. Sound files the user adds are copied into device-protected storage, so they keep
 * working if the original is moved or deleted, and play before the device is first unlocked after a reboot.
 */
class SoundLibrary(private val context: Context) {
    private val directory = File(context.createDeviceProtectedStorageContext().filesDir, "sounds")

    class TooLargeException : IOException("Sound files can be at most ${MAX_BYTES / 1_000_000} MB")

    /** Copies the audio file at [uri] (from the system file picker) into the library. */
    suspend fun add(uri: Uri): AlarmSound.Custom = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var name = "Sound"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use {
            if (it.moveToFirst()) {
                if (!it.isNull(0)) name = it.getString(0)
                if (!it.isNull(1)) size = it.getLong(1)
            }
        }
        if (size > MAX_BYTES) throw TooLargeException()
        val title = name.substringBeforeLast('.').ifBlank { name }
        val extension = name.substringAfterLast('.', "").takeIf { it.length in 1..5 && it.all(Char::isLetterOrDigit) }
        directory.mkdirs()
        val file = File(directory, "${System.currentTimeMillis()}_${safe(title)}${extension?.let { ".$it" }.orEmpty()}")
        try {
            val input = resolver.openInputStream(uri) ?: throw IOException("Can't open $uri")
            input.use { source ->
                file.outputStream().use { target ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        copied += read
                        if (copied > MAX_BYTES) throw TooLargeException()
                        target.write(buffer, 0, read)
                    }
                }
            }
        } catch (e: IOException) {
            file.delete()
            throw e
        }
        AlarmSound.Custom(file.name, title)
    }

    /** The sound files the user added, by title. */
    suspend fun custom(): List<AlarmSound.Custom> = withContext(Dispatchers.IO) {
        directory.listFiles().orEmpty()
            .filter { it.isFile }
            .map { AlarmSound.Custom(it.name, titleOf(it.name)) }
            .sortedBy { it.title.lowercase() }
    }

    suspend fun delete(sound: AlarmSound.Custom) = withContext(Dispatchers.IO) { file(sound)?.delete() }

    /** The library's copy of [sound], if it is still there. */
    fun file(sound: AlarmSound.Custom): File? = File(directory, sound.fileName).takeIf { it.isFile }

    /** The alarm tones that come with the device. */
    suspend fun deviceTones(): List<AlarmSound.Tone> = withContext(Dispatchers.IO) {
        runCatching {
            val manager = RingtoneManager(context).apply { setType(RingtoneManager.TYPE_ALARM) }
            val cursor = manager.cursor
            buildList {
                while (cursor.moveToNext()) {
                    val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
                    add(AlarmSound.Tone(manager.getRingtoneUri(cursor.position).toString(), title))
                }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Where [sound] is played from.
     *
     * @property chosen the sound itself; null if it isn't available (e.g. a deleted file) or silent.
     * @property all [chosen] followed by fallbacks, in the order to try them; empty for a silent sound.
     */
    data class Sources(val chosen: SoundSource?, val all: List<SoundSource>)

    fun sources(sound: AlarmSound): Sources {
        if (sound == AlarmSound.Silent) return Sources(null, emptyList())
        val default = defaultUri()?.let(SoundSource::Content)
        val chosen = when (sound) {
            AlarmSound.Silent, AlarmSound.Default -> default
            is AlarmSound.Tone -> SoundSource.Content(sound.uri.toUri())
            is AlarmSound.Custom -> file(sound)?.let(SoundSource::LocalFile)
        }
        return Sources(chosen, listOfNotNull(chosen, default, SoundSource.Bundled).distinct())
    }

    private fun defaultUri(): Uri? =
        runCatching { RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM) }.getOrNull()

    companion object {
        const val MAX_BYTES = 30_000_000L

        private fun safe(title: String) = title.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").take(60)

        /** File names are `<timestamp>_<title>.<extension>`. */
        fun titleOf(fileName: String) = fileName.substringAfter('_').substringBeforeLast('.')
    }
}
