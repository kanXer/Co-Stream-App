package app.costream.data

import kotlinx.serialization.Serializable

@Serializable
data class SubtitleTrack(
    val id: String,
    val label: String,
    val language: String,
    val url: String,
    val mimeType: String = "application/x-subrip"
)

@Serializable
data class AudioVariant(
    val id: String,
    val label: String,
    val detailPath: String
)

@Serializable
data class StreamSource(
    val url: String,
    val label: String = "Auto",
    val quality: String? = null,
    val mimeType: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<SubtitleTrack> = emptyList()
)

@Serializable
data class SeasonInfo(
    val number: Int,
    val name: String,
    val episodeCount: Int
)

@Serializable
data class EpisodeInfo(
    val number: Int,
    val name: String,
    val description: String = "",
    val stillUrl: String? = null,
    val runtimeMinutes: Int = 0,
    val airDate: String = ""
)

@Serializable
data class MediaItem(
    val id: String,
    val title: String,
    val description: String = "",
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val year: String? = null,
    val runtime: String? = null,
    val genres: List<String> = emptyList(),
    val sources: List<StreamSource> = emptyList(),
    val archiveId: String? = null,
    val provider: String = "Co-Stream",
    val isLive: Boolean = false,
    val isLocal: Boolean = false,
    val badge: String? = null,
    val accentIndex: Int = 0,
    val durationMs: Long? = null,
    val tmdbId: Int? = null,
    val mediaType: String = "movie",
    val seasons: List<SeasonInfo> = emptyList(),
    val episodes: List<EpisodeInfo> = emptyList(),
    val selectedSeason: Int = 1,
    val selectedEpisode: Int = 1,
    val audioVariants: List<AudioVariant> = emptyList(),
    val selectedAudioId: String? = null
) {
    val playable: Boolean get() = sources.isNotEmpty()
    val isSeries: Boolean get() = mediaType == "tv"
    val isArchive: Boolean get() = archiveId != null
}

data class HomeRow(
    val title: String,
    val subtitle: String? = null,
    val items: List<MediaItem>,
    val ranked: Boolean = false
)

data class HomeFeed(
    val featured: List<MediaItem>,
    val rows: List<HomeRow>
)

@Serializable
data class WatchEntry(
    val item: MediaItem,
    val sourceUrl: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long
) {
    val progress: Float
        get() = if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

@Serializable
data class AuthUser(
    val id: String,
    val name: String,
    val email: String? = null,
    val photoUrl: String? = null,
    val guest: Boolean = false
)

data class PlayRequest(
    val item: MediaItem,
    val source: StreamSource,
    val startPositionMs: Long = 0L,
    val contextLabel: String = ""
)
