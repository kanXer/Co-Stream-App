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

        val feed = coroutineScope {
            // Curated rails for selected platforms + ensure Netflix & PrimeVideo are included for Hindi & English curated rails
            val platformsToFetch = (selected + listOf("Netflix", "PrimeVideo")).distinct()
            val curatedDeferred = platformsToFetch.associateWith { platform ->
                async { runCatching { curated(platform) }.getOrDefault(emptyList()) }
            }

            // Anime curated from Crunchyroll
            val animeDeferred = async { runCatching { curated("Crunchyroll") }.getOrDefault(emptyList()) }

            // Web series discovery jobs
            val hindiSeriesDeferred = async { runCatching { discover("Netflix", "tv", country = "IN") }.getOrDefault(emptyList()) }
            val globalSeriesDeferred = async { runCatching { discover("Netflix", "tv", country = "US") }.getOrDefault(emptyList()) }
            val kdramaSeriesDeferred = async { runCatching { discover("Netflix", "tv", country = "KR") }.getOrDefault(emptyList()) }

            // Movie discovery job
            val discoverMoviesDeferred = async { runCatching { discover("Netflix", "movie") }.getOrDefault(emptyList()) }

            // Await all
            val curatedByPlatform = curatedDeferred.mapValues { it.value.await() }
            val animeCurated = animeDeferred.await()
            val hindiSeriesDiscover = hindiSeriesDeferred.await()
            val globalSeriesDiscover = globalSeriesDeferred.await()
            val kdramaSeriesDiscover = kdramaSeriesDeferred.await()
            val discoverMovies = discoverMoviesDeferred.await()

            // 1. EXTRACT MOVIES (Latest, Hindi, English)
            val allCuratedRails = curatedByPlatform.values.flatten()

            val hindiMovieCandidates = allCuratedRails
                .filter { rail -> HINDI_RAIL.containsMatchIn(rail.first) }
                .flatMap { it.second }
                .filter { it.mediaType == "movie" }
                .distinctBy { it.id }
                .sortedWith(compareByDescending<MediaItem> { it.year?.toIntOrNull() ?: 0 }.thenBy { it.title })

            val hindiIds = hindiMovieCandidates.mapNotNull { it.tmdbId }.toSet()

            val englishMovieCandidates = (allCuratedRails
                .filter { rail -> ENGLISH_RAIL.containsMatchIn(rail.first) && !HINDI_RAIL.containsMatchIn(rail.first) }
                .flatMap { it.second }
                .filter { it.mediaType == "movie" && it.tmdbId !in hindiIds } +
                discoverMovies.filter { it.mediaType == "movie" && it.tmdbId !in hindiIds })
                .distinctBy { it.id }
                .sortedWith(compareByDescending<MediaItem> { it.year?.toIntOrNull() ?: 0 }.thenBy { it.title })

            // Latest movies strictly prioritizing latest years (2026, 2025, 2024...) in Hindi and English
            val latestCombined = (hindiMovieCandidates + englishMovieCandidates)
                .distinctBy { it.id }
                .sortedWith(compareByDescending<MediaItem> { it.year?.toIntOrNull() ?: 0 }.thenBy { it.title })

            // Featured carousel: Interleave the newest latest Hindi and English movies so both appear at the top!
            val featuredList = buildList {
                val hIter = hindiMovieCandidates.iterator()
                val eIter = englishMovieCandidates.iterator()
                while (size < 10 && (hIter.hasNext() || eIter.hasNext())) {
                    if (hIter.hasNext()) add(hIter.next())
                    if (size < 10 && eIter.hasNext()) add(eIter.next())
                }
            }.ifEmpty { latestCombined.take(10) }

            // 2. EXTRACT WEB SERIES CATEGORIES
            val trendingSeriesCandidates = (allCuratedRails
                .filter { rail -> SERIES_RAIL.containsMatchIn(rail.first) }
                .flatMap { it.second }
                .filter { it.mediaType == "tv" } + globalSeriesDiscover)
                .distinctBy { it.id }

            val hindiSeriesCandidates = (hindiSeriesDiscover + allCuratedRails
                .filter { rail -> HINDI_RAIL.containsMatchIn(rail.first) }
                .flatMap { it.second }
                .filter { it.mediaType == "tv" })
                .distinctBy { it.id }

            val thrillerSeriesCandidates = allCuratedRails
                .filter { rail -> THRILLER_RAIL.containsMatchIn(rail.first) }
                .flatMap { it.second }
                .filter { it.mediaType == "tv" }
                .distinctBy { it.id }

            val globalSeriesCandidates = globalSeriesDiscover
                .filter { it.mediaType == "tv" }
                .distinctBy { it.id }

            val kdramaSeriesCandidates = kdramaSeriesDiscover
                .filter { it.mediaType == "tv" }
                .distinctBy { it.id }

            val animeSeriesCandidates = (animeCurated
                .flatMap { it.second }
                .filter { it.mediaType == "tv" } +
                allCuratedRails.filter { it.first.contains("Anime", ignoreCase = true) }.flatMap { it.second })
                .distinctBy { it.id }

            // 3. BUILD ALL ROWS
            val rows = buildList {
                // Latest movies (Hindi & English) at the top
                if (latestCombined.isNotEmpty()) {
                    add(
                        HomeRow(
                            title = "🔥 Latest Movies (Hindi & English)",
                            subtitle = "Fresh releases & blockbusters in Hindi & English",
                            items = latestCombined.take(15),
                            ranked = true,
                            category = HomeCategories.LATEST_MOVIES
                        )
                    )
                }
                if (hindiMovieCandidates.isNotEmpty()) {
                    add(
                        HomeRow(
                            title = "🎬 Latest Hindi Blockbusters",
                            subtitle = "Top Bollywood & Indian cinema in Hindi",
                            items = hindiMovieCandidates.take(15),
                            ranked = true,
                            category = HomeCategories.HINDI_MOVIES
                        )
                    )
                }
                if (englishMovieCandidates.isNotEmpty()) {
                    add(
                        HomeRow(
                            title = "🍿 Latest Hollywood & Global Hits",
                            subtitle = "Top English releases & worldwide favorites",
                            items = englishMovieCandidates.take(15),
                            ranked = true,
                            category = HomeCategories.ENGLISH_MOVIES
                        )
                    )
                }

                // WEB SERIES CATEGORIES
                if (trendingSeriesCandidates.isNotEmpty()) {
                    add(
                        HomeRow(
                            title = "🔥 Trending Web Series",
                            subtitle = "Most watched shows & originals",
                            items = trendingSeriesCandidates.take(15),
                            ranked = true,
                            category = HomeCategories.TRENDING_SERIES
                        )
                    )
                }
                if (hindiSeriesCandidates.isNotEmpty()) {
                    add(
                        HomeRow(
                            title = "🇮🇳 Top Hindi Web Series",
                            subtitle = "Popular Indian web series & drama",
                            items = hindiSeriesCandidates.take(15),
                            ranked = true,
                            category = HomeCategories.HINDI_SERIES
                        )
                    )
                }
                if (thrillerSeriesCandidates.isNotEmpty()) {
                    add(
                        HomeRow(
                            title = "⚡ Thriller & Mystery Series",
                            subtitle = "High stakes, gripping twists & suspense",
                            items = thrillerSeriesCandidates.take(15),
                            category = HomeCategories.THRILLER_SERIES
                        )
                    )
                }
                if (globalSeriesCandidates.isNotEmpty()) {
                    add(
                        HomeRow(
                            title = "🌍 Hollywood & Global Series",
                            subtitle = "Acclaimed international shows",
                            items = globalSeriesCandidates.take(15),
                            category = HomeCategories.GLOBAL_SERIES
                        )
                    )
                }
                if (kdramaSeriesCandidates.isNotEmpty()) {
                    add(
                        HomeRow(
                            title = "🌸 K-Drama & Asian Series",
                            subtitle = "Popular Korean drama & Asian hits",
                            items = kdramaSeriesCandidates.take(15),
                            category = HomeCategories.KDRAMA_SERIES
                        )
                    )
                }
                if (animeSeriesCandidates.isNotEmpty()) {
                    add(
                        HomeRow(
                            title = "⚔️ Anime & Animation Series",
                            subtitle = "Top Japanese anime & action series",
                            items = animeSeriesCandidates.take(15),
                            category = HomeCategories.ANIME_SERIES
                        )
                    )
                }

                // Platform specific rails
                selected.forEach { platform ->
                    val label = platformLabel(platform)
                    val rails = curatedByPlatform[platform].orEmpty()
                    val pMovies = rails.flatMap { it.second }.filter { it.mediaType == "movie" }.distinctBy { it.id }
                    val pSeries = rails.flatMap { it.second }.filter { it.mediaType == "tv" }.distinctBy { it.id }
                    if (pMovies.isNotEmpty()) {
                        add(HomeRow("Top Movies on $label", "Curated for $label", pMovies.take(12), category = HomeCategories.ENGLISH_MOVIES))
                    }
                    if (pSeries.isNotEmpty()) {
                        add(HomeRow("Top Web Series on $label", "Originals on $label", pSeries.take(12), category = HomeCategories.TRENDING_SERIES))
                    }
                }
            }

            val fallback = if (rows.isEmpty()) currentHomeFallback() else emptyList()
            val finalRows = rows.ifEmpty { fallback }
            HomeFeed(
                featured = featuredList.ifEmpty { finalRows.flatMap { it.items }.distinctBy { it.id }.take(8) },
                rows = finalRows
            )
        }

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
        private val ENGLISH_RAIL = Regex("hits|blockbuster|hollywood|releases|2025|2024|popular", RegexOption.IGNORE_CASE)
        private val SERIES_RAIL = Regex("series|popular", RegexOption.IGNORE_CASE)
        private val THRILLER_RAIL = Regex("thriller|mystery|crime", RegexOption.IGNORE_CASE)
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
