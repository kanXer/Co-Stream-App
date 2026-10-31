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
                addAll(buildSectionItems(CURATED_HINDI_LATEST, latestHindiMovies, 10))
                addAll(latestCombined)
            }.let(::cleanList).take(8)

            // BUILD ALL ROWS (matching the web app categories)
            val rows = buildList {
                // 1. Latest Hindi Movies (top priority)
                val latestHindi = buildSectionItems(CURATED_HINDI_LATEST, latestHindiMovies, 20)
                if (latestHindi.isNotEmpty()) {
                    add(
                        HomeRow(
                            title = "🆕 Latest Hindi Movies",
                            subtitle = "Newest Bollywood & Indian releases",
                            items = latestHindi,
                            ranked = true,
                            category = HomeCategories.LATEST_MOVIES
                        )
                    )
                }

                // 2. This Year's Releases
                val thisYear = buildSectionItems(
                    CURATED_HINDI_LATEST.filter { (it.year.toIntOrNull() ?: 0) >= currentYear - 1 },
                    thisYearMovies,
                    15
                )
                if (thisYear.isNotEmpty()) {
                    add(
                        HomeRow(
                            title = "🎬 $currentYear Bollywood Hits",
                            subtitle = "Top Indian movies releasing in $currentYear",
                            items = thisYear,
                            ranked = true,
                            category = HomeCategories.HINDI_MOVIES
                        )
                    )
                }

                // 3. Platform Top 10s (Netflix, Prime, etc.) - CID strictly excluded
                selected.forEach { platform ->
                    val label = platformLabel(platform)
                    val rails = curatedByPlatform[platform].orEmpty()
                    val pLiveMovies = cleanList(rails.flatMap { it.second }.filter { it.mediaType == "movie" })
                    val pLiveSeries = cleanList(rails.flatMap { it.second }.filter { it.mediaType == "tv" })
                    val curatedMovies = when (platform) {
                        "Netflix" -> CURATED_NETFLIX_HINDI_MOVIES
                        "PrimeVideo" -> CURATED_PRIME_HINDI_MOVIES
                        else -> emptyList()
                    }
                    val curatedSeries = when (platform) {
                        "Netflix" -> CURATED_NETFLIX_HINDI_SERIES
                        "PrimeVideo" -> CURATED_PRIME_HINDI_SERIES
                        else -> emptyList()
                    }
                    val pMovies = buildSectionItems(curatedMovies, pLiveMovies, 10)
                    val pSeries = buildSectionItems(curatedSeries, pLiveSeries, 10, type = "tv")

                    if (pMovies.isNotEmpty()) {
                        add(
                            HomeRow(
                                title = "🏆 Top 10 Hindi Movies on $label",
                                subtitle = "Trending movies with Hindi audio",
                                items = pMovies,
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
                                items = pSeries,
                                ranked = true,
                                category = HomeCategories.HINDI_SERIES
                            )
                        )
                    }
                }

                // 4. Most Popular Hindi Movies
                val popularMovies = buildSectionItems(CURATED_HINDI_POPULAR, popularHindiMovies, 20)
                if (popularMovies.isNotEmpty()) {
                    add(
                        HomeRow(
                            title = "🔥 Most Popular Hindi Movies",
                            subtitle = "All-time Bollywood fan favourites",
                            items = popularMovies,
                            category = HomeCategories.HINDI_MOVIES
                        )
                    )
                }

                // 5. Top Rated Movies
                val topMovies = buildSectionItems(CURATED_HINDI_POPULAR.filter { it.rating >= 7.0 }, topRatedMovies, 15)
                if (topMovies.isNotEmpty()) {
                    add(
                        HomeRow(
                            title = "⭐ Top Rated Hindi Movies",
                            subtitle = "Highest rated Indian films",
                            items = topMovies,
                            category = HomeCategories.HINDI_MOVIES
                        )
                    )
                }

                // 6. Latest Hindi Web Series
                val latestSeries = buildSectionItems(CURATED_HINDI_SERIES_LIST, latestHindiSeries, 15, type = "tv")
                if (latestSeries.isNotEmpty()) {
                    add(
                        HomeRow(
                            title = "🆕 Latest Hindi Web Series",
                            subtitle = "Freshest Indian streaming shows",
                            items = latestSeries,
                            ranked = true,
                            category = HomeCategories.HINDI_SERIES
                        )
                    )
                }

                // 7. Popular Hindi Web Series
                val popularSeries = buildSectionItems(CURATED_HINDI_SERIES_LIST, popularHindiSeries, 15, type = "tv")
                if (popularSeries.isNotEmpty()) {
                    add(
                        HomeRow(
                            title = "🔥 Most Popular Hindi Web Series",
                            subtitle = "Binge-worthy Indian shows",
                            items = popularSeries,
                            category = HomeCategories.HINDI_SERIES
                        )
                    )
                }

                // 8. Top Rated Web Series
                val topSeries = buildSectionItems(CURATED_HINDI_SERIES_LIST.filter { it.rating >= 8.0 }, topRatedSeries, 15, type = "tv")
                if (topSeries.isNotEmpty()) {
                    add(
                        HomeRow(
                            title = "⭐ Top Rated Hindi Web Series",
                            subtitle = "Critically acclaimed Indian shows",
                            items = topSeries,
                            category = HomeCategories.HINDI_SERIES
                        )
                    )
                }

                // 9-18. Genres matching web app
                val finalActionMovies = buildSectionItems(CURATED_HINDI_ACTION, actionMovies, 15)
                val finalThrillerMovies = buildSectionItems(CURATED_HINDI_THRILLER, thrillerMovies, 15)
                val finalRomanceMovies = buildSectionItems(CURATED_HINDI_ROMANCE, romanceMovies, 15)
                val finalComedyMovies = buildSectionItems(CURATED_HINDI_COMEDY, comedyMovies, 15)
                val finalFamilyMovies = buildSectionItems(CURATED_HINDI_FAMILY, familyMovies, 15)
                val finalActionSeries = buildSectionItems(CURATED_HINDI_ACTION_SERIES, actionSeries, 15, type = "tv")
                val finalCrimeSeries = buildSectionItems(CURATED_HINDI_CRIME_SERIES, crimeSeries, 15, type = "tv")
                val finalHorror = buildSectionItems(CURATED_HINDI_HORROR, horrorItems, 15)
                val finalScifi = buildSectionItems(CURATED_HINDI_SCIFI, scifiMovies, 15)

                if (finalActionMovies.isNotEmpty()) add(HomeRow("💥 Hindi Action Movies", "High-octane Bollywood action", finalActionMovies, category = HomeCategories.ACTION_MOVIES))
                if (finalThrillerMovies.isNotEmpty()) add(HomeRow("🔪 Hindi Thrillers", "Edge-of-your-seat suspense films", finalThrillerMovies, category = HomeCategories.THRILLER_MOVIES))
                if (finalRomanceMovies.isNotEmpty()) add(HomeRow("💕 Hindi Romance Movies", "Bollywood love stories", finalRomanceMovies, category = HomeCategories.ROMANCE_MOVIES))
                if (finalComedyMovies.isNotEmpty()) add(HomeRow("😂 Hindi Comedy Movies", "Laugh-out-loud Bollywood picks", finalComedyMovies, category = HomeCategories.COMEDY_MOVIES))
                if (finalFamilyMovies.isNotEmpty()) add(HomeRow("👨‍👩‍👧 Hindi Family Movies", "Perfect for the whole family", finalFamilyMovies, category = HomeCategories.HINDI_MOVIES))
                if (finalActionSeries.isNotEmpty()) add(HomeRow("💥 Hindi Action Web Series", "Action-packed Indian shows", finalActionSeries, category = HomeCategories.HINDI_SERIES))
                if (finalCrimeSeries.isNotEmpty()) add(HomeRow("🕵️ Hindi Crime Series", "Crime dramas & investigations", finalCrimeSeries, category = HomeCategories.CRIME_SERIES))
                if (finalHorror.isNotEmpty()) add(HomeRow("👻 Hindi Horror", "Spine-chilling desi horror", finalHorror, category = HomeCategories.HORROR))
                if (finalScifi.isNotEmpty()) add(HomeRow("🚀 Hindi Sci-Fi & Fantasy", "Futuristic Indian blockbusters", finalScifi, category = HomeCategories.SCIFI))
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
            throw IOException("\"${item.title}\" is not yet available to stream on NetMirror.")
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
                if (file.contains("220884")) continue
                if (file.startsWith("/")) file = "https://net52.cc$file"

                // Check if this m3u8 playlist contains the 220884 dummy placeholder video
                val isDummy = runCatching {
                    val headReq = Request.Builder()
                        .url(file)
                        .header("Accept", "*/*")
                        .header("User-Agent", "okhttp/4.9.2 /OS.Gatu v3.0")
                        .header("Referer", "https://net52.cc/")
                        .build()
                    client.newCall(headReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string().orEmpty()
                            body.contains("220884")
                        } else false
                    }
                }.getOrDefault(false)
                if (isDummy) continue

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
            if (url.isBlank() || url.contains("220884")) continue
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

        data class HindiItemDef(
            val tmdbId: Int,
            val title: String,
            val year: String,
            val poster: String,
            val rating: Double = 7.0,
            val overview: String = "",
            val mediaType: String = "movie"
        ) {
            fun toMediaItem(): MediaItem = MediaItem(
                id = "nm:$mediaType:$tmdbId",
                tmdbId = tmdbId,
                mediaType = mediaType,
                title = title,
                description = overview.ifBlank { "Streaming in Hindi on Co-Stream." },
                posterUrl = poster,
                backdropUrl = poster.replace("/w185/", "/w780/"),
                year = year,
                provider = "Co-Stream",
                badge = if (mediaType == "tv") "🔥 LATEST SERIES" else "🎬 LATEST RELEASE",
                accentIndex = abs(tmdbId) % 5
            )
        }

        fun buildSectionItems(
            curatedDefs: List<HindiItemDef>,
            liveItems: List<MediaItem>,
            limit: Int = 20,
            type: String = "movie"
        ): List<MediaItem> {
            val seenIds = mutableSetOf<String>()
            val result = mutableListOf<MediaItem>()
            for (def in curatedDefs) {
                val key = "$type:${def.tmdbId}"
                if (!seenIds.contains(key)) {
                    seenIds.add(key)
                    result.add(def.toMediaItem())
                }
            }
            for (item in cleanList(liveItems)) {
                val key = "${item.mediaType}:${item.tmdbId ?: item.id}"
                if (!seenIds.contains(key) && !isBlockedMediaItem(item.tmdbId, item.title)) {
                    seenIds.add(key)
                    result.add(item)
                }
            }
            return result.take(limit)
        }

        val CURATED_HINDI_LATEST = listOf(
            HindiItemDef(1196943, "Chhaava", "2025", "https://image.tmdb.org/t/p/w185/ubRsrzb6NRW8YhVTJ6jG1kpNvCi.jpg", 7.2),
            HindiItemDef(1083241, "Baby John", "2024", "https://image.tmdb.org/t/p/w185/5Oqsh17dq0cIRFPynPNy5elmyxW.jpg", 6.5),
            HindiItemDef(1257960, "Sikandar", "2025", "https://image.tmdb.org/t/p/w185/41s42CRXafa3OuRGvCtfYPEBmse.jpg", 6.5),
            HindiItemDef(1234139, "Jailer 2", "2026", "https://image.tmdb.org/t/p/w780/zpHTOdRl7GEPqFsDGqsN6Hi9Ylt.jpg", 8.0),
            HindiItemDef(1213243, "Toxic", "2026", "https://image.tmdb.org/t/p/w780/6xK461P6yV8Xq50tK1B7z5wVf9V.jpg", 8.0),
            HindiItemDef(857598, "Pushpa 2 - The Rule", "2024", "https://image.tmdb.org/t/p/w185/1T21FblunT0y8fz7YaW8JMYgUKm.jpg", 6.8),
            HindiItemDef(1112426, "Stree 2", "2024", "https://image.tmdb.org/t/p/w185/nfnhwfUEFuSOxxf4jDdBlY6Lccw.jpg", 7.3),
            HindiItemDef(801688, "Kalki 2898-AD", "2024", "https://image.tmdb.org/t/p/w185/rstcAnBeCkxNQjNp3YXrF6IP1tW.jpg", 7.1),
            HindiItemDef(1014214, "Singham Again", "2024", "https://image.tmdb.org/t/p/w185/2JbNkHg8m7LaBy61LyrnnlenaxY.jpg", 6.2),
            HindiItemDef(980599, "Bhool Bhulaiyaa 3", "2024", "https://image.tmdb.org/t/p/w185/3AfHD1HoaQpQwKH8kxRdBKVmzeU.jpg", 6.0),
            HindiItemDef(984101, "Bad Newz", "2024", "https://image.tmdb.org/t/p/w185/qDlqxGATpYBWqGciPB4tmQm3bFT.jpg", 5.5),
            HindiItemDef(1233531, "Article 370", "2024", "https://image.tmdb.org/t/p/w185/9VTemjHMpyxzfC3JsG2aFy8Bf9Y.jpg", 7.2),
            HindiItemDef(1187058, "Munjya", "2024", "https://image.tmdb.org/t/p/w185/ylYdXu0SM79Qs7vOz6q7mBxanVD.jpg", 6.5),
            HindiItemDef(1187619, "Shaitaan", "2024", "https://image.tmdb.org/t/p/w185/oRvFzcagAcC6Q317xtV7QXzwBnj.jpg", 6.7),
            HindiItemDef(850165, "Fighter", "2024", "https://image.tmdb.org/t/p/w185/zDZowwb5qU8Q3Q9yJ2k5N4rB4l7.jpg", 6.8),
            HindiItemDef(1146225, "Chandu Champion", "2024", "https://image.tmdb.org/t/p/w185/d5bZ4uMkW3X3y0uQfU9Z8u1p7V9.jpg", 7.3),
            HindiItemDef(902897, "Yodha", "2024", "https://image.tmdb.org/t/p/w185/eQxU0jU7o8w7fC9B5b8X8m1P3l5.jpg", 6.1),
            HindiItemDef(618355, "Maidaan", "2024", "https://image.tmdb.org/t/p/w185/5vR5cW9q9V6d9l7M1k4B7a8N3b5.jpg", 7.4),
            HindiItemDef(896350, "Merry Christmas", "2024", "https://image.tmdb.org/t/p/w185/d2e1B4r8l8k3N5b7C9a1M4w7P2y.jpg", 7.0),
            HindiItemDef(1072790, "Teri Baaton Mein Aisa Uljha Jiya", "2024", "https://image.tmdb.org/t/p/w185/2gL8d3k5l8w4m1p7n2b9c5v8x4y.jpg", 6.2)
        )

        val CURATED_HINDI_POPULAR = listOf(
            HindiItemDef(781732, "Animal", "2023", "https://image.tmdb.org/t/p/w185/hr9rjR3J0xBBKmlJ4n3gHId9ccx.jpg", 6.8),
            HindiItemDef(872906, "Jawan", "2023", "https://image.tmdb.org/t/p/w185/jFt1gS4BGHlK8xt76Y81Alp4dbt.jpg", 7.1),
            HindiItemDef(864692, "Pathaan", "2023", "https://image.tmdb.org/t/p/w185/arf00BkwvXo0CFKbaD9OpqdE4Nu.jpg", 6.6),
            HindiItemDef(960876, "Dunki", "2023", "https://image.tmdb.org/t/p/w185/kPRb1mbVHGop0egQ7153y0lhzGL.jpg", 6.8),
            HindiItemDef(1163258, "12th Fail", "2023", "https://image.tmdb.org/t/p/w185/eebUPRI4Z5e1Z7Hev4JZAwMIFkX.jpg", 8.3),
            HindiItemDef(885331, "Gadar 2", "2023", "https://image.tmdb.org/t/p/w185/brCBYKGQaxZZcwmFF6OIxZLdKVU.jpg", 6.2),
            HindiItemDef(720557, "Tiger 3", "2023", "https://image.tmdb.org/t/p/w185/7wgED7Yx9VLcNWSO91VgwicHmMD.jpg", 6.0),
            HindiItemDef(1029827, "Drishyam 2", "2022", "https://image.tmdb.org/t/p/w185/wk8Vu0DI0MiNLaXXiVqAwjLRKL5.jpg", 8.1),
            HindiItemDef(579974, "RRR", "2022", "https://image.tmdb.org/t/p/w185/wE0I6efAW4cDDmZQWtwZMOW44EJ.jpg", 7.9),
            HindiItemDef(587412, "K.G.F: Chapter 2", "2022", "https://image.tmdb.org/t/p/w185/khNVygolU0TxLIDWff5tQlAhZ23.jpg", 7.7),
            HindiItemDef(496331, "Brahmāstra Part One: Shiva", "2022", "https://image.tmdb.org/t/p/w185/x61qdvHIsr9U53FwoLVDQqAGur0.jpg", 6.5),
            HindiItemDef(858485, "Kantara", "2022", "https://image.tmdb.org/t/p/w185/jIsKmkxMzdCZ0Ux1GVSnu8m6Na6.jpg", 7.4),
            HindiItemDef(599925, "Shershaah", "2021", "https://image.tmdb.org/t/p/w185/zGvFnwoXJKrYnKhoVPytqkqCJ8V.jpg", 7.8),
            HindiItemDef(632747, "Sooryavanshi", "2021", "https://image.tmdb.org/t/p/w185/632747.jpg", 6.5),
            HindiItemDef(580489, "Bhool Bhulaiyaa 2", "2022", "https://image.tmdb.org/t/p/w185/580489.jpg", 6.3),
            HindiItemDef(848116, "Rocky Aur Rani Kii Prem Kahaani", "2023", "https://image.tmdb.org/t/p/w185/vTQIqlxUkOuyf2UKhlM2OUaFGKz.jpg", 6.7),
            HindiItemDef(799177, "Bhediya", "2022", "https://image.tmdb.org/t/p/w185/iZtq44ZmxBW0E3tAeLG6UdJe9aj.jpg", 6.8),
            HindiItemDef(360814, "Dangal", "2016", "https://image.tmdb.org/t/p/w185/360814.jpg", 8.1),
            HindiItemDef(346648, "Bajrangi Bhaijaan", "2015", "https://image.tmdb.org/t/p/w185/346648.jpg", 7.9),
            HindiItemDef(585268, "War", "2019", "https://image.tmdb.org/t/p/w185/585268.jpg", 6.9)
        )

        val CURATED_HINDI_ACTION = listOf(
            HindiItemDef(872906, "Jawan", "2023", "https://image.tmdb.org/t/p/w185/jFt1gS4BGHlK8xt76Y81Alp4dbt.jpg", 7.1),
            HindiItemDef(864692, "Pathaan", "2023", "https://image.tmdb.org/t/p/w185/arf00BkwvXo0CFKbaD9OpqdE4Nu.jpg", 6.6),
            HindiItemDef(1014214, "Singham Again", "2024", "https://image.tmdb.org/t/p/w185/2JbNkHg8m7LaBy61LyrnnlenaxY.jpg", 6.2),
            HindiItemDef(720557, "Tiger 3", "2023", "https://image.tmdb.org/t/p/w185/7wgED7Yx9VLcNWSO91VgwicHmMD.jpg", 6.0),
            HindiItemDef(857598, "Pushpa 2 - The Rule", "2024", "https://image.tmdb.org/t/p/w185/1T21FblunT0y8fz7YaW8JMYgUKm.jpg", 6.8),
            HindiItemDef(801688, "Kalki 2898-AD", "2024", "https://image.tmdb.org/t/p/w185/rstcAnBeCkxNQjNp3YXrF6IP1tW.jpg", 7.1),
            HindiItemDef(781732, "Animal", "2023", "https://image.tmdb.org/t/p/w185/hr9rjR3J0xBBKmlJ4n3gHId9ccx.jpg", 6.8),
            HindiItemDef(885331, "Gadar 2", "2023", "https://image.tmdb.org/t/p/w185/brCBYKGQaxZZcwmFF6OIxZLdKVU.jpg", 6.2),
            HindiItemDef(579974, "RRR", "2022", "https://image.tmdb.org/t/p/w185/wE0I6efAW4cDDmZQWtwZMOW44EJ.jpg", 7.9),
            HindiItemDef(587412, "K.G.F: Chapter 2", "2022", "https://image.tmdb.org/t/p/w185/khNVygolU0TxLIDWff5tQlAhZ23.jpg", 7.7),
            HindiItemDef(1083241, "Baby John", "2024", "https://image.tmdb.org/t/p/w185/5Oqsh17dq0cIRFPynPNy5elmyxW.jpg", 6.5),
            HindiItemDef(1257960, "Sikandar", "2025", "https://image.tmdb.org/t/p/w185/41s42CRXafa3OuRGvCtfYPEBmse.jpg", 6.5),
            HindiItemDef(902897, "Yodha", "2024", "https://image.tmdb.org/t/p/w185/eQxU0jU7o8w7fC9B5b8X8m1P3l5.jpg", 6.1),
            HindiItemDef(599925, "Shershaah", "2021", "https://image.tmdb.org/t/p/w185/zGvFnwoXJKrYnKhoVPytqkqCJ8V.jpg", 7.8)
        )

        val CURATED_HINDI_COMEDY = listOf(
            HindiItemDef(1112426, "Stree 2", "2024", "https://image.tmdb.org/t/p/w185/nfnhwfUEFuSOxxf4jDdBlY6Lccw.jpg", 7.3),
            HindiItemDef(980599, "Bhool Bhulaiyaa 3", "2024", "https://image.tmdb.org/t/p/w185/3AfHD1HoaQpQwKH8kxRdBKVmzeU.jpg", 6.0),
            HindiItemDef(984101, "Bad Newz", "2024", "https://image.tmdb.org/t/p/w185/qDlqxGATpYBWqGciPB4tmQm3bFT.jpg", 5.5),
            HindiItemDef(1187058, "Munjya", "2024", "https://image.tmdb.org/t/p/w185/ylYdXu0SM79Qs7vOz6q7mBxanVD.jpg", 6.5),
            HindiItemDef(960876, "Dunki", "2023", "https://image.tmdb.org/t/p/w185/kPRb1mbVHGop0egQ7153y0lhzGL.jpg", 6.8),
            HindiItemDef(580489, "Bhool Bhulaiyaa 2", "2022", "https://image.tmdb.org/t/p/w185/580489.jpg", 6.3),
            HindiItemDef(848116, "Rocky Aur Rani Kii Prem Kahaani", "2023", "https://image.tmdb.org/t/p/w185/vTQIqlxUkOuyf2UKhlM2OUaFGKz.jpg", 6.7),
            HindiItemDef(20453, "3 Idiots", "2009", "https://image.tmdb.org/t/p/w185/20453.jpg", 8.4),
            HindiItemDef(297222, "PK", "2014", "https://image.tmdb.org/t/p/w185/297222.jpg", 7.7),
            HindiItemDef(592834, "Chhichhore", "2019", "https://image.tmdb.org/t/p/w185/592834.jpg", 7.5)
        )

        val CURATED_HINDI_THRILLER = listOf(
            HindiItemDef(1029827, "Drishyam 2", "2022", "https://image.tmdb.org/t/p/w185/wk8Vu0DI0MiNLaXXiVqAwjLRKL5.jpg", 8.1),
            HindiItemDef(1187619, "Shaitaan", "2024", "https://image.tmdb.org/t/p/w185/oRvFzcagAcC6Q317xtV7QXzwBnj.jpg", 6.7),
            HindiItemDef(1233531, "Article 370", "2024", "https://image.tmdb.org/t/p/w185/9VTemjHMpyxzfC3JsG2aFy8Bf9Y.jpg", 7.2),
            HindiItemDef(896350, "Merry Christmas", "2024", "https://image.tmdb.org/t/p/w185/d2e1B4r8l8k3N5b7C9a1M4w7P2y.jpg", 7.0),
            HindiItemDef(748783, "The Kashmir Files", "2022", "https://image.tmdb.org/t/p/w185/748783.jpg", 7.2),
            HindiItemDef(534780, "Andhadhun", "2018", "https://image.tmdb.org/t/p/w185/534780.jpg", 8.2),
            HindiItemDef(559907, "Badla", "2019", "https://image.tmdb.org/t/p/w185/559907.jpg", 7.1),
            HindiItemDef(341012, "Drishyam", "2015", "https://image.tmdb.org/t/p/w185/341012.jpg", 8.3),
            HindiItemDef(509967, "Raazi", "2018", "https://image.tmdb.org/t/p/w185/509967.jpg", 7.7)
        )

        val CURATED_HINDI_ROMANCE = listOf(
            HindiItemDef(848116, "Rocky Aur Rani Kii Prem Kahaani", "2023", "https://image.tmdb.org/t/p/w185/vTQIqlxUkOuyf2UKhlM2OUaFGKz.jpg", 6.7),
            HindiItemDef(1072790, "Teri Baaton Mein Aisa Uljha Jiya", "2024", "https://image.tmdb.org/t/p/w185/2gL8d3k5l8w4m1p7n2b9c5v8x4y.jpg", 6.2),
            HindiItemDef(828559, "Sita Ramam", "2022", "https://image.tmdb.org/t/p/w185/828559.jpg", 8.2),
            HindiItemDef(599925, "Shershaah", "2021", "https://image.tmdb.org/t/p/w185/zGvFnwoXJKrYnKhoVPytqkqCJ8V.jpg", 7.8),
            HindiItemDef(537915, "Kabir Singh", "2019", "https://image.tmdb.org/t/p/w185/537915.jpg", 6.9),
            HindiItemDef(184341, "Yeh Jawaani Hai Deewani", "2013", "https://image.tmdb.org/t/p/w185/184341.jpg", 7.2),
            HindiItemDef(14729, "Jab We Met", "2007", "https://image.tmdb.org/t/p/w185/14729.jpg", 7.8),
            HindiItemDef(206, "Dilwale Dulhania Le Jayenge", "1995", "https://image.tmdb.org/t/p/w185/206.jpg", 8.6)
        )

        val CURATED_HINDI_HORROR = listOf(
            HindiItemDef(1112426, "Stree 2", "2024", "https://image.tmdb.org/t/p/w185/nfnhwfUEFuSOxxf4jDdBlY6Lccw.jpg", 7.3),
            HindiItemDef(980599, "Bhool Bhulaiyaa 3", "2024", "https://image.tmdb.org/t/p/w185/3AfHD1HoaQpQwKH8kxRdBKVmzeU.jpg", 6.0),
            HindiItemDef(1187058, "Munjya", "2024", "https://image.tmdb.org/t/p/w185/ylYdXu0SM79Qs7vOz6q7mBxanVD.jpg", 6.5),
            HindiItemDef(1187619, "Shaitaan", "2024", "https://image.tmdb.org/t/p/w185/oRvFzcagAcC6Q317xtV7QXzwBnj.jpg", 6.7),
            HindiItemDef(580489, "Bhool Bhulaiyaa 2", "2022", "https://image.tmdb.org/t/p/w185/580489.jpg", 6.3),
            HindiItemDef(799177, "Bhediya", "2022", "https://image.tmdb.org/t/p/w185/iZtq44ZmxBW0E3tAeLG6UdJe9aj.jpg", 6.8),
            HindiItemDef(538858, "Tumbbad", "2018", "https://image.tmdb.org/t/p/w185/538858.jpg", 8.2),
            HindiItemDef(488661, "Pari", "2018", "https://image.tmdb.org/t/p/w185/488661.jpg", 6.6),
            HindiItemDef(14856, "1920", "2008", "https://image.tmdb.org/t/p/w185/14856.jpg", 6.5)
        )

        val CURATED_HINDI_SCIFI = listOf(
            HindiItemDef(801688, "Kalki 2898-AD", "2024", "https://image.tmdb.org/t/p/w185/rstcAnBeCkxNQjNp3YXrF6IP1tW.jpg", 7.1),
            HindiItemDef(496331, "Brahmāstra Part One: Shiva", "2022", "https://image.tmdb.org/t/p/w185/x61qdvHIsr9U53FwoLVDQqAGur0.jpg", 6.5),
            HindiItemDef(74534, "Ra.One", "2011", "https://image.tmdb.org/t/p/w185/74534.jpg", 5.5),
            HindiItemDef(200727, "Krrish 3", "2013", "https://image.tmdb.org/t/p/w185/200727.jpg", 5.8),
            HindiItemDef(25189, "Koi... Mil Gaya", "2003", "https://image.tmdb.org/t/p/w185/25189.jpg", 7.1),
            HindiItemDef(438674, "2.0", "2018", "https://image.tmdb.org/t/p/w185/438674.jpg", 6.1)
        )

        val CURATED_HINDI_FAMILY = listOf(
            HindiItemDef(20453, "3 Idiots", "2009", "https://image.tmdb.org/t/p/w185/20453.jpg", 8.4),
            HindiItemDef(346648, "Bajrangi Bhaijaan", "2015", "https://image.tmdb.org/t/p/w185/346648.jpg", 7.9),
            HindiItemDef(360814, "Dangal", "2016", "https://image.tmdb.org/t/p/w185/360814.jpg", 8.1),
            HindiItemDef(1163258, "12th Fail", "2023", "https://image.tmdb.org/t/p/w185/eebUPRI4Z5e1Z7Hev4JZAwMIFkX.jpg", 8.3),
            HindiItemDef(592834, "Chhichhore", "2019", "https://image.tmdb.org/t/p/w185/592834.jpg", 7.5),
            HindiItemDef(297222, "PK", "2014", "https://image.tmdb.org/t/p/w185/297222.jpg", 7.7)
        )

        val CURATED_HINDI_SERIES_LIST = listOf(
            HindiItemDef(79141, "Sacred Games", "2018", "https://image.tmdb.org/t/p/w185/79141.jpg", 8.1, mediaType = "tv"),
            HindiItemDef(84661, "Mirzapur", "2018", "https://image.tmdb.org/t/p/w185/84661.jpg", 8.4, mediaType = "tv"),
            HindiItemDef(93405, "The Family Man", "2019", "https://image.tmdb.org/t/p/w185/93405.jpg", 8.6, mediaType = "tv"),
            HindiItemDef(101344, "Panchayat", "2020", "https://image.tmdb.org/t/p/w185/101344.jpg", 8.7, mediaType = "tv"),
            HindiItemDef(103222, "Paatal Lok", "2020", "https://image.tmdb.org/t/p/w185/103222.jpg", 8.0, mediaType = "tv"),
            HindiItemDef(200843, "Farzi", "2023", "https://image.tmdb.org/t/p/w185/200843.jpg", 8.2, mediaType = "tv"),
            HindiItemDef(88040, "Delhi Crime", "2019", "https://image.tmdb.org/t/p/w185/88040.jpg", 8.4, mediaType = "tv"),
            HindiItemDef(88584, "Kota Factory", "2019", "https://image.tmdb.org/t/p/w185/88584.jpg", 8.5, mediaType = "tv"),
            HindiItemDef(112888, "Mismatched", "2020", "https://image.tmdb.org/t/p/w185/112888.jpg", 7.2, mediaType = "tv"),
            HindiItemDef(133701, "Rana Naidu", "2023", "https://image.tmdb.org/t/p/w185/133701.jpg", 6.5, mediaType = "tv"),
            HindiItemDef(97893, "Jamtara – Sabka Number Ayega", "2020", "https://image.tmdb.org/t/p/w185/97893.jpg", 7.6, mediaType = "tv"),
            HindiItemDef(238310, "The Railway Men", "2023", "https://image.tmdb.org/t/p/w185/238310.jpg", 8.1, mediaType = "tv"),
            HindiItemDef(247169, "Maamla Legal Hai", "2024", "https://image.tmdb.org/t/p/w185/247169.jpg", 7.5, mediaType = "tv"),
            HindiItemDef(99966, "Asur", "2020", "https://image.tmdb.org/t/p/w185/99966.jpg", 8.4, mediaType = "tv")
        )

        val CURATED_HINDI_ACTION_SERIES = listOf(
            HindiItemDef(79141, "Sacred Games", "2018", "https://image.tmdb.org/t/p/w185/79141.jpg", 8.1, mediaType = "tv"),
            HindiItemDef(84661, "Mirzapur", "2018", "https://image.tmdb.org/t/p/w185/84661.jpg", 8.4, mediaType = "tv"),
            HindiItemDef(93405, "The Family Man", "2019", "https://image.tmdb.org/t/p/w185/93405.jpg", 8.6, mediaType = "tv"),
            HindiItemDef(200843, "Farzi", "2023", "https://image.tmdb.org/t/p/w185/200843.jpg", 8.2, mediaType = "tv"),
            HindiItemDef(133701, "Rana Naidu", "2023", "https://image.tmdb.org/t/p/w185/133701.jpg", 6.5, mediaType = "tv")
        )

        val CURATED_HINDI_CRIME_SERIES = listOf(
            HindiItemDef(88040, "Delhi Crime", "2019", "https://image.tmdb.org/t/p/w185/88040.jpg", 8.4, mediaType = "tv"),
            HindiItemDef(103222, "Paatal Lok", "2020", "https://image.tmdb.org/t/p/w185/103222.jpg", 8.0, mediaType = "tv"),
            HindiItemDef(99966, "Asur", "2020", "https://image.tmdb.org/t/p/w185/99966.jpg", 8.4, mediaType = "tv"),
            HindiItemDef(97893, "Jamtara – Sabka Number Ayega", "2020", "https://image.tmdb.org/t/p/w185/97893.jpg", 7.6, mediaType = "tv"),
            HindiItemDef(200843, "Farzi", "2023", "https://image.tmdb.org/t/p/w185/200843.jpg", 8.2, mediaType = "tv")
        )

        val CURATED_NETFLIX_HINDI_MOVIES = listOf(
            HindiItemDef(1112426, "Stree 2", "2024", "https://image.tmdb.org/t/p/w185/nfnhwfUEFuSOxxf4jDdBlY6Lccw.jpg", 7.3),
            HindiItemDef(1014214, "Singham Again", "2024", "https://image.tmdb.org/t/p/w185/2JbNkHg8m7LaBy61LyrnnlenaxY.jpg", 6.2),
            HindiItemDef(984101, "Bad Newz", "2024", "https://image.tmdb.org/t/p/w185/qDlqxGATpYBWqGciPB4tmQm3bFT.jpg", 5.5),
            HindiItemDef(1233531, "Article 370", "2024", "https://image.tmdb.org/t/p/w185/9VTemjHMpyxzfC3JsG2aFy8Bf9Y.jpg", 7.2),
            HindiItemDef(872906, "Jawan", "2023", "https://image.tmdb.org/t/p/w185/jFt1gS4BGHlK8xt76Y81Alp4dbt.jpg", 7.1),
            HindiItemDef(960876, "Dunki", "2023", "https://image.tmdb.org/t/p/w185/kPRb1mbVHGop0egQ7153y0lhzGL.jpg", 6.8),
            HindiItemDef(781732, "Animal", "2023", "https://image.tmdb.org/t/p/w185/hr9rjR3J0xBBKmlJ4n3gHId9ccx.jpg", 6.8),
            HindiItemDef(1163258, "12th Fail", "2023", "https://image.tmdb.org/t/p/w185/eebUPRI4Z5e1Z7Hev4JZAwMIFkX.jpg", 8.3),
            HindiItemDef(580489, "Bhool Bhulaiyaa 2", "2022", "https://image.tmdb.org/t/p/w185/580489.jpg", 6.3)
        )

        val CURATED_NETFLIX_HINDI_SERIES = listOf(
            HindiItemDef(79141, "Sacred Games", "2018", "https://image.tmdb.org/t/p/w185/79141.jpg", 8.1, mediaType = "tv"),
            HindiItemDef(88040, "Delhi Crime", "2019", "https://image.tmdb.org/t/p/w185/88040.jpg", 8.4, mediaType = "tv"),
            HindiItemDef(238310, "The Railway Men", "2023", "https://image.tmdb.org/t/p/w185/238310.jpg", 8.1, mediaType = "tv"),
            HindiItemDef(88584, "Kota Factory", "2019", "https://image.tmdb.org/t/p/w185/88584.jpg", 8.5, mediaType = "tv"),
            HindiItemDef(247169, "Maamla Legal Hai", "2024", "https://image.tmdb.org/t/p/w185/247169.jpg", 7.5, mediaType = "tv"),
            HindiItemDef(112888, "Mismatched", "2020", "https://image.tmdb.org/t/p/w185/112888.jpg", 7.2, mediaType = "tv"),
            HindiItemDef(133701, "Rana Naidu", "2023", "https://image.tmdb.org/t/p/w185/133701.jpg", 6.5, mediaType = "tv"),
            HindiItemDef(97893, "Jamtara – Sabka Number Ayega", "2020", "https://image.tmdb.org/t/p/w185/97893.jpg", 7.6, mediaType = "tv")
        )

        val CURATED_PRIME_HINDI_MOVIES = listOf(
            HindiItemDef(801688, "Kalki 2898-AD", "2024", "https://image.tmdb.org/t/p/w185/rstcAnBeCkxNQjNp3YXrF6IP1tW.jpg", 7.1),
            HindiItemDef(857598, "Pushpa 2 - The Rule", "2024", "https://image.tmdb.org/t/p/w185/1T21FblunT0y8fz7YaW8JMYgUKm.jpg", 6.8),
            HindiItemDef(1112426, "Stree 2", "2024", "https://image.tmdb.org/t/p/w185/nfnhwfUEFuSOxxf4jDdBlY6Lccw.jpg", 7.3),
            HindiItemDef(1014214, "Singham Again", "2024", "https://image.tmdb.org/t/p/w185/2JbNkHg8m7LaBy61LyrnnlenaxY.jpg", 6.2),
            HindiItemDef(781732, "Animal", "2023", "https://image.tmdb.org/t/p/w185/hr9rjR3J0xBBKmlJ4n3gHId9ccx.jpg", 6.8),
            HindiItemDef(872906, "Jawan", "2023", "https://image.tmdb.org/t/p/w185/jFt1gS4BGHlK8xt76Y81Alp4dbt.jpg", 7.1),
            HindiItemDef(579974, "RRR", "2022", "https://image.tmdb.org/t/p/w185/wE0I6efAW4cDDmZQWtwZMOW44EJ.jpg", 7.9),
            HindiItemDef(587412, "K.G.F: Chapter 2", "2022", "https://image.tmdb.org/t/p/w185/khNVygolU0TxLIDWff5tQlAhZ23.jpg", 7.7),
            HindiItemDef(1029827, "Drishyam 2", "2022", "https://image.tmdb.org/t/p/w185/wk8Vu0DI0MiNLaXXiVqAwjLRKL5.jpg", 8.1)
        )

        val CURATED_PRIME_HINDI_SERIES = listOf(
            HindiItemDef(84661, "Mirzapur", "2018", "https://image.tmdb.org/t/p/w185/84661.jpg", 8.4, mediaType = "tv"),
            HindiItemDef(93405, "The Family Man", "2019", "https://image.tmdb.org/t/p/w185/93405.jpg", 8.6, mediaType = "tv"),
            HindiItemDef(101344, "Panchayat", "2020", "https://image.tmdb.org/t/p/w185/101344.jpg", 8.7, mediaType = "tv"),
            HindiItemDef(103222, "Paatal Lok", "2020", "https://image.tmdb.org/t/p/w185/103222.jpg", 8.0, mediaType = "tv"),
            HindiItemDef(200843, "Farzi", "2023", "https://image.tmdb.org/t/p/w185/200843.jpg", 8.2, mediaType = "tv")
        )

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
