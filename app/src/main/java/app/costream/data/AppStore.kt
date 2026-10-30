package app.costream.data

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class AppStore(context: Context) {
    private val prefs = context.getSharedPreferences("costream_store", Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val itemListSerializer = ListSerializer(MediaItem.serializer())
    private val watchListSerializer = ListSerializer(WatchEntry.serializer())
    private val platformListSerializer = ListSerializer(String.serializer())

    @Synchronized
    fun myList(): List<MediaItem> = decodeItems(prefs.getString(KEY_MY_LIST, null))

    @Synchronized
    fun authUser(): AuthUser? = prefs.getString(KEY_AUTH_USER, null)?.let { raw ->
        runCatching { json.decodeFromString(AuthUser.serializer(), raw) }.getOrNull()
    }

    @Synchronized
    fun setAuthUser(user: AuthUser?) {
        val edit = prefs.edit()
        if (user == null) edit.remove(KEY_AUTH_USER)
        else edit.putString(KEY_AUTH_USER, json.encodeToString(AuthUser.serializer(), user))
        edit.apply()
    }

    @Synchronized
    fun platforms(): List<String> = prefs.getString(KEY_PLATFORMS, null)?.let { raw ->
        runCatching { json.decodeFromString(platformListSerializer, raw) }.getOrDefault(emptyList())
    } ?: emptyList()

    @Synchronized
    fun setPlatforms(platforms: List<String>) {
        prefs.edit().putString(KEY_PLATFORMS, json.encodeToString(platformListSerializer, platforms)).apply()
    }

    @Synchronized
    fun toggleMyList(item: MediaItem): Boolean {
        val current = myList().toMutableList()
        val existing = current.indexOfFirst { it.id == item.id }
        val saved = if (existing >= 0) {
            current.removeAt(existing)
            false
        } else {
            current.add(0, item)
            true
        }
        prefs.edit().putString(KEY_MY_LIST, json.encodeToString(itemListSerializer, current)).apply()
        return saved
    }

    @Synchronized
    fun isInMyList(id: String): Boolean = myList().any { it.id == id }

    @Synchronized
    fun removeFromMyList(id: String) {
        val current = myList().filterNot { it.id == id }
        prefs.edit().putString(KEY_MY_LIST, json.encodeToString(itemListSerializer, current)).apply()
    }

    @Synchronized
    fun history(): List<WatchEntry> = decodeWatches(prefs.getString(KEY_HISTORY, null))

    @Synchronized
    fun recordWatch(item: MediaItem, sourceUrl: String, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0L) return
        val current = history().filterNot { it.item.id == item.id }.toMutableList()
        val safePosition = positionMs.coerceIn(0L, durationMs)
        current.add(
            0,
            WatchEntry(
                item = item,
                sourceUrl = sourceUrl,
                positionMs = safePosition,
                durationMs = durationMs,
                updatedAt = System.currentTimeMillis()
            )
        )
        val trimmed = current.take(40)
        prefs.edit().putString(KEY_HISTORY, json.encodeToString(watchListSerializer, trimmed)).apply()
    }

    @Synchronized
    fun progressFor(itemId: String): WatchEntry? = history().firstOrNull { it.item.id == itemId }

    fun continueWatching(limit: Int = 8): List<WatchEntry> =
        history()
            .filter { it.durationMs > 0 && it.positionMs > 4_000 && it.progress < 0.96f }
            .take(limit)

    @Synchronized
    fun clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    @Synchronized
    fun localVideos(): List<MediaItem> = decodeItems(prefs.getString(KEY_LOCAL, null))

    @Synchronized
    fun setLocalVideos(items: List<MediaItem>) {
        prefs.edit().putString(KEY_LOCAL, json.encodeToString(itemListSerializer, items)).apply()
    }

    @Synchronized
    fun addLocalVideos(items: List<MediaItem>) {
        val merged = (items + localVideos()).distinctBy { it.id }
        setLocalVideos(merged)
    }

    @Synchronized
    fun removeLocalVideo(id: String) {
        setLocalVideos(localVideos().filterNot { it.id == id })
    }

    @Synchronized
    fun webUrl(): String? = prefs.getString(KEY_WEB_URL, null)

    @Synchronized
    fun setWebUrl(url: String) {
        prefs.edit().putString(KEY_WEB_URL, url).apply()
    }

    private fun decodeItems(raw: String?): List<MediaItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(itemListSerializer, raw) }.getOrDefault(emptyList())
    }

    private fun decodeWatches(raw: String?): List<WatchEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(watchListSerializer, raw) }.getOrDefault(emptyList())
    }

    private companion object {
        const val KEY_MY_LIST = "my_list"
        const val KEY_HISTORY = "history"
        const val KEY_LOCAL = "local_videos"
        const val KEY_WEB_URL = "web_url"
        const val KEY_AUTH_USER = "auth_user"
        const val KEY_PLATFORMS = "platforms"
    }
}
