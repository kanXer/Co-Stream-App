package app.costream.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

class NetMirrorRepository(private val client: OkHttpClient) {

    private val baseUrl = "https://net27.cc"
    private val metadataCache = ConcurrentHashMap<String, Pair<Long, JSONObject>>()

    @Volatile
    private var homeCache: Triple<Long, String, HomeFeed>? = null

    suspend fun homeFeed(platforms: List<String>, force: Boolean = false): HomeFeed {
        val selected = platforms.ifEmpty { DEFAULT_PLATFORMS }
        val key = selected.joinToString(",")
        homeCache?.let { cached ->
            if (!force && cached.second == key && System.currentTimeMillis() - cached.first < HOME_CACHE_MS) {
                return cached.third
            }
        }

        val rows = coroutineScope {
            selected.map { platform ->
                async {
                    val label = platformLabel(platform)
                    val curated = runCatching { curated(platform) }.getOrDefault(emptyList())
                    val movieCandidates = curated
                        .filter { rail -> HINDI_RAIL.containsMatchIn(rail.first) }
                        .flatMap { it.second }
                        .filter { it.mediaType == "movie" }
                        .distinctBy { it.id }
                    val movies = if (movieCandidates.isNotEmpty()) {
                        movieCandidates.take(10)
                    } else {
                        runCatching { discover(platform, "movie") }.getOrDefault(emptyList()).take(10)
                    }
                    val series = runCatching { discover(platform, "tv", country = "IN") }
                        .getOrDefault(emptyList())
                        .take(10)
                    buildList {
                        if (movies.isNotEmpty()) add(
                            HomeRow("Top 10 Hindi Movies on $label", "Trending with Hindi audio", movies, ranked = true)
                        )
                        if (series.isNotEmpty()) add(
                            HomeRow("Top 10 Hindi Web Series on $label", "Popular Indian series", series, ranked = true)
                        )
                    }
                }
            }.awaitAll().flatten()
        }

        val fallback = if (rows.isEmpty()) currentHomeFallback() else emptyList()
        val finalRows = rows.ifEmpty { fallback }
        val feed = HomeFeed(
            featured = finalRows.flatMap { it.items }.distinctBy { it.id }.take(8),
            rows = finalRows
        )
        homeCache = Triple(System.currentTimeMillis(), key, feed)
        return feed
    }

    suspend fun search(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isBlank()) return@withContext emptyList()
        val url = "$baseUrl/api/catalog/search-hybrid".toHttpUrl().newBuilder()
            .addQueryParameter("q", clean)
            .build()
        val root = JSONObject(execute(url.toString()))
        parseCatalogItems(root.optJSONArray("items"))
    }

    suspend fun details(
        item: MediaItem,
        season: Int = item.selectedSeason,
        episode: Int = item.selectedEpisode,
        audio: AudioVariant? = null
    ): MediaItem = coroutineScope {
        val tmdbId = item.tmdbId ?: return@coroutineScope item
        val metadataJob = async { titleMetadata(tmdbId, item.mediaType) }
        val variantsJob = async { runCatching { variants(tmdbId, item.mediaType, season, episode) }.getOrNull() }
        val variantInfo = variantsJob.await()
        val embedJob = async {
            embed(
                tmdbId = tmdbId,
                type = item.mediaType,
                season = season,
                episode = episode,
                audio = audio,
                variantRoot = variantInfo
            )
        }
        val metadata = metadataJob.await()
        val embed = embedJob.await()
        val rawEpisodes = if (item.mediaType == "tv") {
            if (metadata.optInt("initialSeason", 1) == season) {
                metadata.optJSONArray("initialEpisodes")
            } else {
                season(tmdbId, season).optJSONArray("episodes")
            }
        } else null
        val episodes = parseEpisodes(rawEpisodes)
        val selectedEpisodeInfo = episodes.firstOrNull { it.number == episode }
        val sources = parseEmbedSources(embed)
        val audioVariants = parseAudioVariants(variantInfo)

        item.copy(
            title = embed.optString("title").ifBlank { metadata.optString("title").ifBlank { item.title } },
            description = selectedEpisodeInfo?.description?.takeIf { it.isNotBlank() }
                ?: metadata.optString("overview").ifBlank { item.description },
            posterUrl = metadata.optString("poster").takeIf { it.isNotBlank() } ?: item.posterUrl,
            backdropUrl = metadata.optString("backdrop").takeIf { it.isNotBlank() } ?: item.backdropUrl,
            year = metadata.optString("year").takeIf { it.isNotBlank() } ?: item.year,
            runtime = selectedEpisodeInfo?.runtimeMinutes?.takeIf { it > 0 }?.let { "${it}m" } ?: item.runtime,
            genres = parseGenres(metadata.optJSONArray("genres")).ifEmpty { item.genres },
            sources = sources,
            seasons = parseSeasons(metadata.optJSONArray("seasons")),
            episodes = episodes,
            selectedSeason = season,
            selectedEpisode = episode,
            audioVariants = audioVariants,
            selectedAudioId = audio?.id,
            provider = "Co-Stream",
            badge = if (item.mediaType == "tv") "SERIES" else "MOVIE"
        )
    }

    suspend fun seasonEpisodes(tmdbId: Int, season: Int): List<EpisodeInfo> =
        parseEpisodes(season(tmdbId, season).optJSONArray("episodes"))

    private suspend fun discover(platform: String, type: String, country: String? = null): List<MediaItem> {
        val builder = "$baseUrl/api/catalog/discover".toHttpUrl().newBuilder()
            .addQueryParameter("platform", platform)
            .addQueryParameter("type", type)
            .addQueryParameter("region", "IN")
            .addQueryParameter("sort", "popularity")
        country?.let { builder.addQueryParameter("country", it) }
        return parseCatalogItems(JSONObject(execute(builder.build().toString())).optJSONArray("items"))
    }

    private suspend fun curated(platform: String): List<Pair<String, List<MediaItem>>> {
        val root = JSONObject(execute("$baseUrl/api/catalog/curated/$platform"))
        val rails = root.optJSONArray("rails") ?: return emptyList()
        return buildList {
            for (index in 0 until rails.length()) {
                val rail = rails.optJSONObject(index) ?: continue
                val items = parseCatalogItems(rail.optJSONArray("items"))
                if (items.isNotEmpty()) add(rail.optString("title") to items)
            }
        }
    }

    private suspend fun currentHomeFallback(): List<HomeRow> {
        val root = JSONObject(execute("$baseUrl/api/catalog/aoneroom-home"))
        val rails = root.optJSONArray("rails") ?: return emptyList()
        return buildList {
            for (index in 0 until minOf(rails.length(), 5)) {
                val rail = rails.optJSONObject(index) ?: continue
                val items = parseCatalogItems(rail.optJSONArray("items"))
                if (items.isNotEmpty()) add(HomeRow(rail.optString("title"), "Recently updated", items.take(16)))
            }
        }
    }

    private suspend fun titleMetadata(tmdbId: Int, type: String): JSONObject {
        val key = "$type:$tmdbId"
        metadataCache[key]?.let { cached ->
            if (System.currentTimeMillis() - cached.first < METADATA_CACHE_MS) return cached.second
        }
        val value = JSONObject(execute("$baseUrl/api/catalog/title/$type/$tmdbId"))
        metadataCache[key] = System.currentTimeMillis() to value
        return value
    }

    private suspend fun season(tmdbId: Int, season: Int): JSONObject =
        JSONObject(execute("$baseUrl/api/catalog/season/$tmdbId/$season"))

    private suspend fun variants(tmdbId: Int, type: String, season: Int, episode: Int): JSONObject {
        val suffix = if (type == "tv") "?se=$season&ep=$episode" else ""
        return JSONObject(execute("$baseUrl/api/variants-tmdb/$type/$tmdbId$suffix"))
    }

    private suspend fun embed(
        tmdbId: Int,
        type: String,
        season: Int,
        episode: Int,
        audio: AudioVariant?,
        variantRoot: JSONObject?
    ): JSONObject {
        val builder = "$baseUrl/api/embed-tmdb/$tmdbId".toHttpUrl().newBuilder()
            .addQueryParameter("type", type)
            .addQueryParameter("se", season.toString())
            .addQueryParameter("ep", episode.toString())
        if (audio != null && variantRoot != null) {
            val defaultId = variantRoot.optString("defaultSubjectId")
            val defaultPath = variantRoot.optString("defaultDetailPath")
            if (defaultId.isNotBlank() && defaultPath.isNotBlank()) {
                builder.addQueryParameter("dub", audio.id)
                    .addQueryParameter("dubdp", audio.detailPath)
                    .addQueryParameter("sid", defaultId)
                    .addQueryParameter("dp", defaultPath)
            }
        }
        val root = JSONObject(execute(builder.build().toString()))
        if (!root.optBoolean("ok") || (root.optJSONArray("streams")?.length() ?: 0) == 0) {
            throw IOException(root.optString("error").ifBlank { "No playable stream is available." })
        }
        return root
    }

    private fun parseCatalogItems(array: JSONArray?): List<MediaItem> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optJSONObject(index) ?: continue
                val title = value.optString("title")
                val type = value.optString("type").ifBlank { "movie" }
                val tmdbId = value.optInt("tmdbId")
                if (title.isBlank()) continue
                val subjectId = value.optString("subjectId")
                if (tmdbId <= 0 && subjectId.isBlank()) continue
                add(
                    MediaItem(
                        id = if (tmdbId > 0) "nm:$type:$tmdbId" else "nm:subject:$subjectId",
                        tmdbId = tmdbId.takeIf { it > 0 },
                        mediaType = type,
                        title = title,
                        description = value.optString("overview"),
                        posterUrl = value.optString("poster").takeIf { it.isNotBlank() },
                        backdropUrl = value.optString("backdrop").takeIf { it.isNotBlank() },
                        year = value.optString("year").takeIf { it.isNotBlank() },
                        provider = "Co-Stream",
                        badge = if (type == "tv") "SERIES" else "MOVIE",
                        accentIndex = abs((if (tmdbId > 0) tmdbId else subjectId.hashCode())) % 5
                    )
                )
            }
        }
    }

    private fun parseEmbedSources(root: JSONObject): List<StreamSource> {
        val subtitles = parseSubtitles(root.optJSONArray("captions"))
        val streams = root.optJSONArray("streams") ?: JSONArray()
        val result = mutableListOf<StreamSource>()
        for (index in 0 until streams.length()) {
            val stream = streams.optJSONObject(index) ?: continue
            val url = stream.optString("url")
            if (url.isBlank()) continue
            val resolution = stream.optInt("resolution")
            result += StreamSource(
                url = url,
                label = if (resolution > 0) "${resolution}p" else "Auto",
                quality = if (resolution > 0) "${resolution}p" else "Auto",
                mimeType = if (url.contains(".m3u8", true)) "application/x-mpegURL" else "video/mp4",
                headers = mediaHeaders(url),
                subtitles = subtitles
            )
        }
        return result.sortedByDescending { it.quality?.filter(Char::isDigit)?.toIntOrNull() ?: 0 }
    }

    private fun parseSubtitles(array: JSONArray?): List<SubtitleTrack> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val caption = array.optJSONObject(index) ?: continue
                var url = caption.optString("url")
                if (url.isBlank()) continue
                if (url.startsWith('/')) url = "$baseUrl$url"
                add(
                    SubtitleTrack(
                        id = "${caption.optString("lang").ifBlank { "und" }}-$index",
                        label = caption.optString("name").ifBlank { caption.optString("lang").ifBlank { "Subtitle ${index + 1}" } },
                        language = caption.optString("lang").ifBlank { "und" },
                        url = url
                    )
                )
            }
        }
    }

    private fun parseSeasons(array: JSONArray?): List<SeasonInfo> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val season = array.optJSONObject(index) ?: continue
                val number = season.optInt("season_number")
                if (number <= 0) continue
                add(SeasonInfo(number, season.optString("name").ifBlank { "Season $number" }, season.optInt("episode_count")))
            }
        }
    }

    private fun parseEpisodes(array: JSONArray?): List<EpisodeInfo> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val episode = array.optJSONObject(index) ?: continue
                val number = episode.optInt("episode")
                if (number <= 0) continue
                add(
                    EpisodeInfo(
                        number = number,
                        name = episode.optString("name").ifBlank { "Episode $number" },
                        description = episode.optString("overview"),
                        stillUrl = episode.optString("still").takeIf { it.isNotBlank() },
                        runtimeMinutes = episode.optInt("runtime"),
                        airDate = episode.optString("airDate")
                    )
                )
            }
        }
    }

    private fun parseGenres(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.optString("name")?.takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    private fun parseAudioVariants(root: JSONObject?): List<AudioVariant> {
        val array = root?.optJSONArray("variants") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val variant = array.optJSONObject(index) ?: continue
                val id = variant.optString("dubSubjectId")
                val path = variant.optString("detailPath")
                val raw = variant.optString("language")
                if (id.isBlank() || path.isBlank()) continue
                if (raw != "Default" && !raw.contains("dub", true) && !variant.optBoolean("isOriginal")) continue
                val label = if (raw == "Default" || raw.isBlank()) "Original" else raw.replace(Regex("\\s+dub$", RegexOption.IGNORE_CASE), "")
                add(AudioVariant(id, label, path))
            }
        }
    }

    private fun mediaHeaders(url: String): Map<String, String> {
        val host = runCatching { url.toHttpUrl().host }.getOrDefault("")
        val referer = when {
            host.endsWith("hakunaymatata.com") -> "https://moviebox.ph/"
            host.endsWith("mxcontent.net") -> "https://mixdrop.top/"
            host.endsWith("messycloud.ink") -> "https://netmirror.hair/"
            host.endsWith("juicybits.io") -> "https://www.messycloud.ink/"
            host.endsWith("aoneroom.com") -> "https://videodownloader.site/"
            else -> "https://net27.cc/"
        }
        return mapOf("Referer" to referer, "User-Agent" to USER_AGENT)
    }

    private suspend fun execute(url: String): String = withContext(Dispatchers.IO) {
        var failure: Throwable? = null
        repeat(3) { attempt ->
            try {
                val request = Request.Builder().url(url).header("Accept", "application/json").build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) return@withContext response.body?.string().orEmpty()
                    failure = IOException("Request failed (${response.code})")
                    if (response.code != 429 && response.code < 500) throw failure!!
                }
            } catch (error: Throwable) {
                failure = error
            }
            if (attempt < 2) delay(350L * (attempt + 1))
        }
        throw failure ?: IOException("Request failed")
    }

    companion object {
        val DEFAULT_PLATFORMS = listOf("Netflix", "PrimeVideo")
        val PLATFORMS = listOf("Netflix", "PrimeVideo", "JioHotstar", "SonyLIV", "AppleTV", "Crunchyroll", "MX")
        private val HINDI_RAIL = Regex("hindi|bollywood|indian", RegexOption.IGNORE_CASE)
        private const val HOME_CACHE_MS = 5 * 60 * 1000L
        private const val METADATA_CACHE_MS = 10 * 60 * 1000L
        private const val USER_AGENT = "Co-Stream/2.0 (Android)"

        fun platformLabel(id: String): String = when (id) {
            "PrimeVideo" -> "Amazon Prime"
            "JioHotstar" -> "JioHotstar"
            "SonyLIV" -> "SonyLIV"
            "AppleTV" -> "Apple TV+"
            "Crunchyroll" -> "Crunchyroll"
            "MX" -> "Amazon MX Player"
            else -> id
        }
    }
}
