package dev.ezaz.manazone.feedback

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** A sound the user added. [id] is the value stored as the selected sound, e.g. `custom:3f2a…`. */
data class CustomSound(val id: String, val name: String)

sealed interface ImportResult {
    data class Added(val sound: CustomSound) : ImportResult
    data class Rejected(val message: String) : ImportResult
}

/**
 * User-chosen completion sounds. Picked files are copied into app storage so they keep working if
 * the original is moved or its permission is lost; nothing is read from outside the app at playback.
 */
class CustomSoundStore(context: Context) {
    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, "custom_sounds")

    private val _sounds = MutableStateFlow(load())
    val sounds: StateFlow<List<CustomSound>> = _sounds.asStateFlow()

    /** The stored audio for [soundId], or null if it is not a custom sound or the file is gone. */
    fun fileFor(soundId: String): File? {
        if (!isCustomId(soundId)) return null
        val id = soundId.removePrefix(PREFIX)
        if (!ID_PATTERN.matches(id)) return null
        return File(dir, "$id.audio").takeIf { it.isFile }
    }

    suspend fun import(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        if (_sounds.value.size >= MAX_SOUNDS) return@withContext ImportResult.Rejected("You can keep up to $MAX_SOUNDS custom sounds. Remove one first.")
        val id = UUID.randomUUID().toString()
        val target = File(dir, "$id.audio")
        try {
            dir.mkdirs()
            val input = appContext.contentResolver.openInputStream(uri)
                ?: return@withContext ImportResult.Rejected("Couldn't open that file.")
            var copied = 0L
            input.use { src ->
                target.outputStream().use { out ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val n = src.read(buffer)
                        if (n < 0) break
                        copied += n
                        if (copied > MAX_BYTES) return@withContext reject(target, "That file is too large. Use one under ${MAX_BYTES / (1024 * 1024)} MB.")
                        out.write(buffer, 0, n)
                    }
                }
            }
            val seconds = durationSeconds(target)
                ?: return@withContext reject(target, "That doesn't look like a playable audio file.")
            if (seconds > MAX_SECONDS) return@withContext reject(target, "That sound is too long. Use one under $MAX_SECONDS seconds.")

            val name = displayName(uri) ?: "Custom sound"
            File(dir, "$id.name").writeText(name)
            val sound = CustomSound(PREFIX + id, name)
            _sounds.value = _sounds.value + sound
            ImportResult.Added(sound)
        } catch (e: java.io.IOException) {
            reject(target, "Couldn't read that file.")
        } catch (e: SecurityException) {
            reject(target, "Couldn't read that file.")
        }
    }

    suspend fun remove(soundId: String) = withContext(Dispatchers.IO) {
        if (!isCustomId(soundId)) return@withContext
        val id = soundId.removePrefix(PREFIX)
        if (!ID_PATTERN.matches(id)) return@withContext
        File(dir, "$id.audio").delete()
        File(dir, "$id.name").delete()
        _sounds.value = _sounds.value.filterNot { it.id == soundId }
    }

    private fun reject(target: File, message: String): ImportResult {
        target.delete()
        return ImportResult.Rejected(message)
    }

    private fun durationSeconds(file: File): Int? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            if (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) != "yes") return null
            val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: return null
            ((ms + 999) / 1000).toInt()
        } catch (e: RuntimeException) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()?.substringBeforeLast('.')?.trim()?.takeIf { it.isNotEmpty() }?.take(48)

    private fun load(): List<CustomSound> =
        dir.listFiles { f -> f.extension == "audio" && ID_PATTERN.matches(f.nameWithoutExtension) }
            .orEmpty()
            .sortedBy { it.lastModified() }
            .map { f ->
                val name = runCatching { File(dir, "${f.nameWithoutExtension}.name").readText() }.getOrNull()
                    ?.takeIf { it.isNotBlank() } ?: "Custom sound"
                CustomSound(PREFIX + f.nameWithoutExtension, name)
            }

    companion object {
        const val PREFIX = "custom:"
        const val MAX_SOUNDS = 8
        const val MAX_SECONDS = 30
        private const val MAX_BYTES = 8L * 1024 * 1024
        private val ID_PATTERN = Regex("[0-9a-fA-F-]{36}")

        fun isCustomId(soundId: String?): Boolean = soundId?.startsWith(PREFIX) == true
    }
}
