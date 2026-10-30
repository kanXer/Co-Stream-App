package app.costream.util

import java.util.Locale

fun formatClock(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

fun formatDurationLabel(ms: Long?): String {
    if (ms == null || ms <= 0L) return ""
    val totalMinutes = ms / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        totalMinutes > 0 -> "${minutes}m"
        else -> "<1m"
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return ""
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.0f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", kb)
    }
}

fun displayTitleFromFileName(fileName: String): String {
    val trimmed = fileName.trim()
    if (trimmed.isEmpty()) return "Untitled video"
    val dot = trimmed.lastIndexOf('.')
    val withoutExtension = if (dot > 0 && trimmed.length - dot <= 6) trimmed.substring(0, dot) else trimmed
    return withoutExtension.replace('_', ' ').replace('.', ' ').replace(Regex("\\s+"), " ").trim()
}
