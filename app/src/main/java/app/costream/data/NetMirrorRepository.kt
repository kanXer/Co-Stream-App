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
import java.net.URLEncoder
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
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)

            // Curated rails for selected platforms + ensure Netflix & PrimeVideo are included
            val platformsToFetch = (selected + listOf("Netflix", "PrimeVideo")).distinct()
            val curatedDeferred = platformsToFetch.associateWith { platform ->
                async { runCatching { curated(platform) }.getOrDefault(emptyList()) }
            }

            // High-priority Hindi and global discoveries in parallel
            val latestHindiMoviesDeferred = async {
                runCatching {
                    discover(null, "movie", country = "IN", sort = "release", yearFrom = currentYear - 1, yearTo = currentYear + 1)
                }.getOrDefault(emptyList())
            }
            val thisYearMoviesDeferred = async {
                runCatching {
                    discover(null, "movie", sort = "release", yearFrom = currentYear, yearTo = currentYear + 1)
                }.getOrDefault(emptyList())
            }
            val popularHindiMoviesDeferred = async {
                runCatching {
                    discover(null, "movie", country = "IN", sort = "popularity")
                }.getOrDefault(emptyList())
            }
            val topRatedMoviesDeferred = async {
                runCatching {
                    discover(null, "movie", sort = "rating", yearFrom = currentYear - 2, yearTo = currentYear + 1)
                }.getOrDefault(emptyList())
            }
            val latestHindiSeriesDeferred = async {
                runCatching {
                    discover(null, "tv", country = "IN", sort = "release", yearFrom = currentYear - 1, yearTo = currentYear + 1)
                }.getOrDefault(emptyList())
            }
            val popularHindiSeriesDeferred = async {
                runCatching {
                    discover(null, "tv", country = "IN", sort = "popularity")
                }.getOrDefault(emptyList())
            }
            val topRatedSeriesDeferred = async {
                runCatching {
                    discover(null, "tv", sort = "rating", yearFrom = currentYear - 2, yearTo = currentYear + 1)
                }.getOrDefault(emptyList())
            }

            // Genre searches matching web app
            val actionMoviesDeferred = async { runCatching { search("Hindi action movie").filter { it.mediaType == "movie" } }.getOrDefault(emptyList()) }
            val thrillerMoviesDeferred = async { runCatching { search("Hindi thriller movie").filter { it.mediaType == "movie" } }.getOrDefault(emptyList()) }
            val romanceMoviesDeferred = async { runCatching { search("Hindi romance movie").filter { it.mediaType == "movie" } }.getOrDefault(emptyList()) }
            val comedyMoviesDeferred = async { runCatching { search("Hindi comedy movie").filter { it.mediaType == "movie" } }.getOrDefault(emptyList()) }
            val familyMoviesDeferred = async { runCatching { search("Hindi family movie").filter { it.mediaType == "movie" } }.getOrDefault(emptyList()) }
            val actionSeriesDeferred = async { runCatching { search("Hindi action web series").filter { it.mediaType == "tv" } }.getOrDefault(emptyList()) }
            val crimeSeriesDeferred = async { runCatching { search("Hindi crime web series").filter { it.mediaType == "tv" } }.getOrDefault(emptyList()) }
            val horrorDeferred = async { runCatching { search("Hindi horror") }.getOrDefault(emptyList()) }
            val scifiDeferred = async { runCatching { search("Hindi sci-fi fantasy movie").filter { it.mediaType == "movie" } }.getOrDefault(emptyList()) }

            // Global, K-Drama and Anime
            val globalSeriesDeferred = async { runCatching { discover("Netflix", "tv", country = "US") }.getOrDefault(emptyList()) }
            val kdramaSeriesDeferred = async { runCatching { discover("Netflix", "tv", country = "KR") }.getOrDefault(emptyList()) }
            val animeCuratedDeferred = async { runCatching { curated("Crunchyroll") }.getOrDefault(emptyList()) }

            // Await all
            val curatedByPlatform = curatedDeferred.mapValues { it.value.await() }
            val latestHindiMovies = cleanList(latestHindiMoviesDeferred.await())
            val thisYearMovies = cleanList(thisYearMoviesDeferred.await())
            val popularHindiMovies = cleanList(popularHindiMoviesDeferred.await())
            val topRatedMovies = cleanList(topRatedMoviesDeferred.await())
            val latestHindiSeries = cleanList(latestHindiSeriesDeferred.await())
            val popularHindiSeries = cleanList(popularHindiSeriesDeferred.await())
            val topRatedSeries = cleanList(topRatedSeriesDeferred.await())

            val actionMovies = cleanList(actionMoviesDeferred.await())
            val thrillerMovies = cleanList(thrillerMoviesDeferred.await())
            val romanceMovies = cleanList(romanceMoviesDeferred.await())
            val comedyMovies = cleanList(comedyMoviesDeferred.await())
            val familyMovies = cleanList(familyMoviesDeferred.await())
            val actionSeries = cleanList(actionSeriesDeferred.await())
            val crimeSeries = cleanList(crimeSeriesDeferred.await())
            val horrorItems = cleanList(horrorDeferred.await())
            val scifiMovies = cleanList(scifiDeferred.await())
            val globalSeries = cleanList(globalSeriesDeferred.await())
            val kdramaSeries = cleanList(kdramaSeriesDeferred.await())
            val animeSeries = cleanList(animeCuratedDeferred.await().flatMap { it.second }.filter { it.mediaType == "tv" })

            // Curated rails extracted
            val allCuratedRails = curatedByPlatform.values.flatten()
            val hindiMovieCurated = allCuratedRails
                .filter { rail -> HINDI_RAIL.containsMatchIn(rail.first) }
                .flatMap { it.second }
                .filter { it.mediaType == "movie" }
                .let(::cleanList)

            // Combined latest movies
            val latestCombined = (latestHindiMovies + hindiMovieCurated + thisYearMovies)
                .distinctBy { it.id }
                .sortedWith(compareByDescending<MediaItem> { it.year?.toIntOrNull() ?: 0 }.thenBy { it.title })

            // Featured banner: Curated Bollywood blockbusters (including Jailer 2) at top + latest releases
            val featuredList = buildList {
                addAll(CURATED_HINDI_SPOTLIGHT)
                addAll(latestHindiMovies)
                addAll(latestCombined)
            }.let(::cleanList).take(8)

            // BUILD ALL ROWS (matching the web app categories)
            val rows = buildList {
                // 1. Latest Hindi Movies (top priority)
                if (latestHindiMovies.isNotEmpty()) {
                    add(
                        HomeRow(
                            title = "🆕 Latest Hindi Movies",
                            subtitle = "Newest Bollywood & Indian releases",
                            items = latestHindiMovies.take(20),
                            ranked = true,
                            category = HomeCategories.LATEST_MOVIES
                        )
                    )
                }

                // 2. This Year's Releases
                if (thisYearMovies.isNotEmpty()) {
                    add(
                        HomeRow(
                            title = "🎬 $currentYear Bollywood Hits",
                            subtitle = "Top Indian movies releasing in $currentYear",
                            items = thisYearMovies.take(15),
                            ranked = true,
                            category = HomeCategories.HINDI_MOVIES
                        )
                    )
                }

                // 3. Platform Top 10s (Netflix, Prime, etc.) - CID strictly excluded
                selected.forEach { platform ->
                    val label = platformLabel(platform)
                    val rails = curatedByPlatform[platform].orEmpty()
                    val pMovies = cleanList(rails.flatMap { it.second }.filter { it.mediaType == "movie" })
                    val pSeries = cleanList(rails.flatMap { it.second }.filter { it.mediaType == "tv" })
                    if (pMovies.isNotEmpty()) {
                        add(
                            HomeRow(
                                title = "🏆 Top 10 Hindi Movies on $label",
                                subtitle = "Trending movies with Hindi audio",
                                items = pMovies.take(10),
                                ranked = true,
                                category = HomeCategories.HINDI_MOVIES
                            )
                        )
                    }
                    if (pSeries.isNotEmpty()) {
                        add(
                            HomeRow(
                                title = "📺 Top 10 Hindi Web Series on $label",
                                subtitle = "Trending Indian and Hindi series",
                                items = pSeries.take(10),
                                ranked = true,
                                category = HomeCategories.HINDI_SERIES
                            )
                        )
                    }
                }

                // 4. Most Popular Hindi Movies
                if (popularHindiMovies.isNotEmpty()) {
                    add(
                        HomeRow(
                            title = "🔥 Most Popular Hindi Movies",
                            subtitle = "All-time Bollywood fan favourites",
                            items = popularHindiMovies.take(20),
                            category = HomeCategories.HINDI_MOVIES
                        )
                    )
                }

                // 5. Top Rated Movies
                if (topRatedMovies.isNotEmpty()) {
                    add(
                        HomeRow(
                            title = "⭐ Top Rated Hindi Movies",
                            subtitle = "Highest rated Indian films",
                            items = topRatedMovies.take(15),
                            category = HomeCategories.HINDI_MOVIES
                        )
                    )
                }

                // 6. Latest Hindi Web Series
                if (latestHindiSeries.isNotEmpty()) {
                    add(
                        HomeRow(
                            title = "🆕 Latest Hindi Web Series",
                            subtitle = "Freshest Indian streaming shows",
                            items = latestHindiSeries.take(15),
                            ranked = true,
                            category = HomeCategories.HINDI_SERIES
                        )
                    )
                }

                // 7. Popular Hindi Web Series
                if (popularHindiSeries.isNotEmpty()) {
                    add(
                        HomeRow(
                            title = "🔥 Most Popular Hindi Web Series",
                            subtitle = "Binge-worthy Indian shows",
                            items = popularHindiSeries.take(15),
                            category = HomeCategories.HINDI_SERIES
                        )
                    )
                }

                // 8. Top Rated Web Series
                if (topRatedSeries.isNotEmpty()) {
                    add(
                        HomeRow(
                            title = "⭐ Top Rated Hindi Web Series",
                            subtitle = "Critically acclaimed Indian shows",
                            items = topRatedSeries.take(15),
                            category = HomeCategories.HINDI_SERIES
                        )
                    )
                }

                // 9-18. Genres matching web app
                if (actionMovies.isNotEmpty()) add(HomeRow("💥 Hindi Action Movies", "High-octane Bollywood action", actionMovies.take(15), category = HomeCategories.ACTION_MOVIES))
                if (thrillerMovies.isNotEmpty()) add(HomeRow("🔪 Hindi Thrillers", "Edge-of-your-seat suspense films", thrillerMovies.take(15), category = HomeCategories.THRILLER_MOVIES))
                if (romanceMovies.isNotEmpty()) add(HomeRow("💕 Hindi Romance Movies", "Bollywood love stories", romanceMovies.take(15), category = HomeCategories.ROMANCE_MOVIES))
                if (comedyMovies.isNotEmpty()) add(HomeRow("😂 Hindi Comedy Movies", "Laugh-out-loud Bollywood picks", comedyMovies.take(15), category = HomeCategories.COMEDY_MOVIES))
                if (familyMovies.isNotEmpty()) add(HomeRow("👨‍👩‍👧 Hindi Family Movies", "Perfect for the whole family", familyMovies.take(15), category = HomeCategories.HINDI_MOVIES))
                if (actionSeries.isNotEmpty()) add(HomeRow("💥 Hindi Action Web Series", "Action-packed Indian shows", actionSeries.take(15), category = HomeCategories.HINDI_SERIES))
                if (crimeSeries.isNotEmpty()) add(HomeRow("🕵️ Hindi Crime Series", "Crime dramas & investigations", crimeSeries.take(15), category = HomeCategories.CRIME_SERIES))
                if (horrorItems.isNotEmpty()) add(HomeRow("👻 Hindi Horror", "Spine-chilling desi horror", horrorItems.take(15), category = HomeCategories.HORROR))
                if (scifiMovies.isNotEmpty()) add(HomeRow("🚀 Hindi Sci-Fi & Fantasy", "Futuristic Indian blockbusters", scifiMovies.take(15), category = HomeCategories.SCIFI))
                if (kdramaSeries.isNotEmpty()) add(HomeRow("🌸 K-Drama & Asian Series", "Popular Korean drama & Asian hits", kdramaSeries.take(15), category = HomeCategories.KDRAMA_SERIES))
                if (animeSeries.isNotEmpty()) add(HomeRow("⚔️ Anime & Animation Series", "Top Japanese anime & action series", animeSeries.take(15), category = HomeCategories.ANIME_SERIES))
                if (globalSeries.isNotEmpty()) add(HomeRow("🌍 Hollywood & Global Series", "Acclaimed international shows", globalSeries.take(15), category = HomeCategories.GLOBAL_SERIES))
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
        cleanList(parseCatalogItems(root.optJSONArray("items")))
    }

    suspend fun details(
        item: MediaItem,
        season: Int = item.selectedSeason,
        episode: Int = item.selectedEpisode,
        audio: AudioVariant? = null
    ): MediaItem = coroutineScope {
        val tmdbId = item.tmdbId ?: return@coroutineScope item
        val metadataJob = async { runCatching { titleMetadata(tmdbId, item.mediaType) }.getOrNull() }
        val variantsJob = async { runCatching { variants(tmdbId, item.mediaType, season, episode) }.getOrNull() }
        val variantInfo = variantsJob.await()
        val embedJob = async {
            runCatching {
                embed(
                    tmdbId = tmdbId,
                    type = item.mediaType,
                    season = season,
                    episode = episode,
                    audio = audio,
                    variantRoot = variantInfo
                )
            }.getOrNull()
        }
        val metadata = metadataJob.await() ?: JSONObject()
        val embed = embedJob.await()
        val rawEpisodes = if (item.mediaType == "tv") {
            if (metadata.optInt("initialSeason", 1) == season) {
                metadata.optJSONArray("initialEpisodes")
            } else {
                runCatching { season(tmdbId, season).optJSONArray("episodes") }.getOrNull()
            }
        } else null
        val episodes = parseEpisodes(rawEpisodes)
        val selectedEpisodeInfo = episodes.firstOrNull { it.number == episode }

        // Primary: sources from embed API
        var sources = if (embed != null) parseEmbedSources(embed) else emptyList()

        // Fallback: If embed API returns no stream (e.g. Jailer 2), resolve classic NetMirror playlist
        if (sources.isEmpty()) {
            val classic = resolveClassicPlaylist(item.title, metadata.optString("imdb_id").takeIf { it.isNotBlank() })
            if (classic.isNotEmpty()) {
                sources = classic
            }
        }

        if (sources.isEmpty()) {
            throw IOException("No playable stream is currently available for \"${item.title}\".")
        }

        val audioVariants = parseAudioVariants(variantInfo)

        item.copy(
            title = embed?.optString("title")?.takeIf { it.isNotBlank() }
                ?: metadata.optString("title").ifBlank { item.title },
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
            badge = if (item.mediaType == "tv") "🔥 LATEST SERIES" else "🎬 LATEST RELEASE"
        )
    }

    suspend fun seasonEpisodes(tmdbId: Int, season: Int): List<EpisodeInfo> =
        parseEpisodes(season(tmdbId, season).optJSONArray("episodes"))

    private suspend fun discover(
        platform: String?,
        type: String,
        country: String? = null,
        sort: String = "popularity",
        region: String = "IN",
        yearFrom: Int? = null,
        yearTo: Int? = null
    ): List<MediaItem> {
        val builder = "$baseUrl/api/catalog/discover".toHttpUrl().newBuilder()
            .addQueryParameter("type", type)
            .addQueryParameter("region", region)
            .addQueryParameter("sort", sort)
        if (!platform.isNullOrBlank()) builder.addQueryParameter("platform", platform)
        if (!country.isNullOrBlank()) builder.addQueryParameter("country", country)
        if (yearFrom != null) builder.addQueryParameter("year_from", yearFrom.toString())
        if (yearTo != null) builder.addQueryParameter("year_to", yearTo.toString())
        val json = runCatching { JSONObject(execute(builder.build().toString())) }.getOrNull()
        return cleanList(parseCatalogItems(json?.optJSONArray("items")))
    }

    private suspend fun curated(platform: String): List<Pair<String, List<MediaItem>>> {
        val root = JSONObject(execute("$baseUrl/api/catalog/curated/$platform"))
        val rails = root.optJSONArray("rails") ?: return emptyList()
        return buildList {
            for (index in 0 until rails.length()) {
                val rail = rails.optJSONObject(index) ?: continue
                val items = cleanList(parseCatalogItems(rail.optJSONArray("items")))
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
                val items = cleanList(parseCatalogItems(rail.optJSONArray("items")))
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

    /**
     * Resolves streams from classic NetMirror CDN (net52.cc) for titles that have no stream in net27 embed API (e.g. Jailer 2).
     */
    private suspend fun resolveClassicPlaylist(title: String, imdbId: String?): List<StreamSource> = withContext(Dispatchers.IO) {
        val candidateIds = mutableListOf<String>()
        if (!imdbId.isNullOrBlank() && imdbId.startsWith("tt", ignoreCase = true)) {
            candidateIds.add(imdbId)
        }

        // Search net52.cc
        val searchUrl = "https://net52.cc/search.php?s=${URLEncoder.encode(title, "UTF-8")}&t=${System.currentTimeMillis() / 1000}"
        val searchRequest = Request.Builder()
            .url(searchUrl)
            .header("Accept", "application/json")
            .header("User-Agent", "okhttp/4.9.2 /OS.Gatu v3.0")
            .header("Referer", "https://net52.cc/")
            .header("X-Requested-With", "XMLHttpRequest")
            .build()

        val searchJson = runCatching {
            client.newCall(searchRequest).execute().use { resp ->
                if (resp.isSuccessful) JSONObject(resp.body?.string().orEmpty()) else null
            }
        }.getOrNull()

        val results = searchJson?.optJSONArray("searchResult")
        if (results != null) {
            val cleanTitle = title.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
            for (i in 0 until results.length()) {
                val entry = results.optJSONObject(i) ?: continue
                val t = entry.optString("t")
                val id = entry.optString("id")
                if (id.isNotBlank()) {
                    val cleanCandidate = t.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
                    if (cleanCandidate == cleanTitle || cleanCandidate.contains(cleanTitle) || cleanTitle.contains(cleanCandidate)) {
                        candidateIds.add(0, id)
                        break
                    }
                }
            }
            if (candidateIds.isEmpty() && results.length() > 0) {
                results.optJSONObject(0)?.optString("id")?.takeIf { it.isNotBlank() }?.let { candidateIds.add(it) }
            }
        }

        // Fetch playlist from net52.cc
        for (targetId in candidateIds.distinct()) {
            val playlistUrl = "https://net52.cc/playlist.php?id=$targetId&t=Video&tm=${System.currentTimeMillis()}"
            val playRequest = Request.Builder()
                .url(playlistUrl)
                .header("Accept", "application/json")
                .header("User-Agent", "okhttp/4.9.2 /OS.Gatu v3.0")
                .header("Referer", "https://net52.cc/")
                .build()

            val playJson = runCatching {
                client.newCall(playRequest).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string().orEmpty() else null
                }
            }.getOrNull() ?: continue

            val array = runCatching { JSONArray(playJson) }.getOrNull() ?: continue
            val first = array.optJSONObject(0) ?: continue
            val sources = first.optJSONArray("sources") ?: continue
            val list = mutableListOf<StreamSource>()
            for (i in 0 until sources.length()) {
                val s = sources.optJSONObject(i) ?: continue
                var file = s.optString("file")
                if (file.isBlank()) continue
                if (file.startsWith("/")) file = "https://net52.cc$file"
                val label = s.optString("label")
                val quality = when {
                    label.contains("full", true) -> "1080p"
                    label.contains("mid", true) -> "720p"
                    label.contains("low", true) -> "480p"
                    label.filter(Char::isDigit).isNotEmpty() -> "${label.filter(Char::isDigit)}p"
                    else -> label.ifBlank { "Auto" }
                }
                list.add(
                    StreamSource(
                        url = file,
                        label = quality,
                        quality = quality,
                        mimeType = if (file.contains(".m3u8", true)) "application/x-mpegURL" else "video/mp4",
                        headers = mapOf("Referer" to "https://net52.cc/", "User-Agent" to "okhttp/4.9.2 /OS.Gatu v3.0")
                    )
                )
            }
            if (list.isNotEmpty()) {
                return@withContext list.sortedByDescending { it.quality?.filter(Char::isDigit)?.toIntOrNull() ?: 0 }
            }
        }
        emptyList()
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
                if (isBlockedMediaItem(tmdbId, title)) continue
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
                        badge = if (type == "tv") "🔥 LATEST SERIES" else "🎬 LATEST RELEASE",
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
            host.contains("net52.cc") || host.contains("freecdn") || host.endsWith(".top") -> "https://net52.cc/"
            else -> "https://net27.cc/"
        }
        val ua = if (host.contains("net52.cc") || host.contains("freecdn") || host.endsWith(".top")) {
            "okhttp/4.9.2 /OS.Gatu v3.0"
        } else {
            USER_AGENT
        }
        return mapOf("Referer" to referer, "User-Agent" to ua)
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

        private val NON_STREAMABLE_TMDB_IDS = setOf(15226) // C.I.D. (1998)

        fun isBlockedMediaItem(tmdbId: Int?, title: String?): Boolean {
            if (tmdbId != null && tmdbId in NON_STREAMABLE_TMDB_IDS) return true
            if (title.isNullOrBlank()) return false
            val t = title.trim().lowercase()
            val clean = t.replace(Regex("[^a-z0-9]"), "")
            if (clean == "cid" || clean.startsWith("cid")) return true
            if (Regex("\\b(c\\.?\\s*i\\.?\\s*d\\.?)\\b", RegexOption.IGNORE_CASE).containsMatchIn(t)) return true
            if (t.contains("crime investigation department", ignoreCase = true)) return true
            return false
        }

        fun cleanList(items: List<MediaItem>): List<MediaItem> {
            return items.filterNot { isBlockedMediaItem(it.tmdbId, it.title) }.distinctBy { it.id }
        }

        fun platformLabel(id: String): String = when (id) {
            "PrimeVideo" -> "Amazon Prime"
            "JioHotstar" -> "JioHotstar"
            "SonyLIV" -> "SonyLIV"
            "AppleTV" -> "Apple TV+"
            "Crunchyroll" -> "Crunchyroll"
            "MX" -> "Amazon MX Player"
            else -> id
        }

        val CURATED_HINDI_SPOTLIGHT = listOf(
            MediaItem(
                id = "nm:movie:1234139",
                tmdbId = 1234139,
                mediaType = "movie",
                title = "Jailer 2",
                description = "When a dangerous syndicate expands its reach to threaten his family's peace, retired jailer Tiger Muthuvel Pandian goes undercover as a driver. Operating from the shadows, he takes on ruthless new enemies to protect his home.",
                posterUrl = "https://image.tmdb.org/t/p/w780/upwdEyhhYZ7pT2hD535fr2MGfpx.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/zpHTOdRl7GEPqFsDGqsN6Hi9Ylt.jpg",
                year = "2026",
                badge = "🎬 LATEST RELEASE",
                provider = "Co-Stream"
            ),
            MediaItem(
                id = "nm:movie:1213243",
                tmdbId = 1213243,
                mediaType = "movie",
                title = "Toxic",
                description = "A fairy tale for grown-ups set against the backdrop of the drug mafia.",
                posterUrl = "https://image.tmdb.org/t/p/w780/z0G78R6q4kX88g7bA7z8a2uN4j2.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/z0G78R6q4kX88g7bA7z8a2uN4j2.jpg",
                year = "2026",
                badge = "🎬 LATEST RELEASE",
                provider = "Co-Stream"
            ),
            MediaItem(
                id = "nm:movie:1109086",
                tmdbId = 1109086,
                mediaType = "movie",
                title = "War 2",
                description = "Major Kabir Dhaliwal faces an unprecedented challenge when a rogue operative with unmatched tactical skills threatens national security.",
                posterUrl = "https://image.tmdb.org/t/p/w780/j8C8mE43X5g2sY7a6a4z5a8c2d1.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/j8C8mE43X5g2sY7a6a4z5a8c2d1.jpg",
                year = "2025",
                badge = "🎬 LATEST RELEASE",
                provider = "Co-Stream"
            ),
            MediaItem(
                id = "nm:movie:857598",
                tmdbId = 857598,
                mediaType = "movie",
                title = "Pushpa 2 - The Rule",
                description = "Pushpa Raj continues to reign supreme over the red sanders syndicate, taking on ruthless foes and law enforcement alike.",
                posterUrl = "https://image.tmdb.org/t/p/w780/vG2stbB5rMR371aTzN4A3R1a8.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/vG2stbB5rMR371aTzN4A3R1a8.jpg",
                year = "2024",
                badge = "🎬 LATEST RELEASE",
                provider = "Co-Stream"
            ),
            MediaItem(
                id = "nm:movie:1112426",
                tmdbId = 1112426,
                mediaType = "movie",
                title = "Stree 2",
                description = "The town of Chanderi is haunted once again, this time by a headless entity named Sarkata abducting progressive women.",
                posterUrl = "https://image.tmdb.org/t/p/w780/m9EtB2Zc2yL4u4O9h5D3s4b8.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/original/m9EtB2Zc2yL4u4O9h5D3s4b8.jpg",
                year = "2024",
                badge = "🎬 LATEST RELEASE",
                provider = "Co-Stream"
            )
        )
    }
}
