package app.costream.data

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import app.costream.util.displayTitleFromFileName
import app.costream.util.formatDurationLabel
import app.costream.util.formatFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

class LocalMediaRepository(private val context: Context) {

    suspend fun import(uris: List<Uri>): List<MediaItem> = withContext(Dispatchers.IO) {
        uris.mapNotNull { uri -> runCatching { buildItem(uri) }.getOrNull() }
    }

    private fun buildItem(uri: Uri): MediaItem {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        val name = queryDisplayName(uri) ?: uri.lastPathSegment ?: "Video"
        val size = querySize(uri)
        val mime = context.contentResolver.getType(uri)
        val duration = readDuration(uri)
        val title = displayTitleFromFileName(name)

        val description = buildString {
            append("From your device")
            if (size > 0) append(" · ${formatFileSize(size)}")
            val durationLabel = formatDurationLabel(duration)
            if (durationLabel.isNotBlank()) append(" · $durationLabel")
        }

        return MediaItem(
            id = "local:$uri",
            title = title,
            description = description,
            year = null,
            runtime = formatDurationLabel(duration).ifBlank { null },
            genres = listOf("On this device"),
            sources = listOf(
                StreamSource(
                    url = uri.toString(),
                    label = "Original file",
                    quality = "Original",
                    mimeType = mime
                )
            ),
            provider = "This device",
            isLocal = true,
            badge = "LOCAL",
            accentIndex = abs(title.hashCode()) % 5,
            durationMs = duration
        )
    }

    private fun queryDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) return cursor.getString(index)
                }
            }
        return null
    }

    private fun querySize(uri: Uri): Long {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (index >= 0 && !cursor.isNull(index)) return cursor.getLong(index)
                }
            }
        return 0L
    }

    private fun readDuration(uri: Uri): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
