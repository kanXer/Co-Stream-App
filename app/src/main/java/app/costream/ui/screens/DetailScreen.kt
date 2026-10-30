package app.costream.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.costream.data.AudioVariant
import app.costream.data.EpisodeInfo
import app.costream.data.MediaItem
import app.costream.data.StreamSource
import app.costream.data.WatchEntry
import app.costream.ui.components.GenreChip
import app.costream.ui.components.GlassIconButton
import app.costream.ui.components.MediaArtwork
import app.costream.ui.components.NoticeBanner
import app.costream.ui.components.Pill
import app.costream.ui.components.PrimaryButton
import app.costream.ui.components.ShimmerBox
import app.costream.ui.components.mediaMeta
import app.costream.ui.theme.Ink
import app.costream.ui.theme.Teal
import app.costream.ui.theme.TextMuted
import app.costream.ui.theme.TextPrimary
import app.costream.ui.theme.TextSecondary
import coil.compose.AsyncImage

@Composable
fun DetailScreen(
    original: MediaItem,
    loaded: MediaItem?,
    loading: Boolean,
    error: String?,
    saved: Boolean,
    playPreparing: Boolean,
    progress: WatchEntry?,
    onBack: () -> Unit,
    onPlay: (MediaItem, StreamSource?) -> Unit,
    onPlayEpisode: (MediaItem, Int, Int, AudioVariant?) -> Unit,
    onSelectSeason: (Int) -> Unit,
    onToggleSaved: (MediaItem) -> Unit,
    onRetry: () -> Unit
) {
    val item = loaded ?: original
    val context = LocalContext.current
    var selectedSource by remember(item.id, item.sources.size) { mutableIntStateOf(0) }
    var selectedAudio by remember(item.id, item.audioVariants) { mutableStateOf<AudioVariant?>(null) }
    LaunchedEffect(item.sources.size) { if (selectedSource > item.sources.lastIndex) selectedSource = 0 }
    val source = item.sources.getOrNull(selectedSource)

    Box(Modifier.fillMaxSize().background(Ink)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 36.dp)) {
            item {
                Box(Modifier.fillMaxWidth().height(440.dp)) {
                    MediaArtwork(item, Modifier.fillMaxSize(), alignment = Alignment.TopCenter)
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Ink.copy(.45f), Color.Transparent, Ink.copy(.72f), Ink))))
                    Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 22.dp)) {
                        Pill(if (item.isSeries) "WEB SERIES" else "MOVIE", color = Ink, background = Teal, borderColor = Color.Transparent)
                        Spacer(Modifier.height(12.dp))
                        Text(item.title, color = TextPrimary, style = MaterialTheme.typography.headlineLarge.copy(fontSize = 36.sp, lineHeight = 39.sp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(9.dp)); Text(mediaMeta(item), color = TextSecondary, fontSize = 11.5.sp)
                    }
                    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        GlassIconButton(Icons.Rounded.ArrowBackIosNew, "Back", onClick = onBack)
                        Spacer(Modifier.weight(1f))
                        GlassIconButton(Icons.Rounded.Share, "Share", onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, item.title) }
                            context.startActivity(Intent.createChooser(send, "Share ${item.title}"))
                        })
                        Spacer(Modifier.width(8.dp))
                        GlassIconButton(if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, "My List", tint = if (saved) Teal else TextPrimary) { onToggleSaved(item) }
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    PrimaryButton(
                        text = if (playPreparing) "Preparing…" else if ((progress?.positionMs ?: 0) > 4_000) "Resume" else "Play",
                        loading = playPreparing,
                        enabled = !loading || item.sources.isNotEmpty(),
                        onClick = {
                            if (item.isSeries) onPlayEpisode(item, item.selectedSeason, item.selectedEpisode, selectedAudio)
                            else if (selectedAudio != null) onPlayEpisode(item, 1, 1, selectedAudio)
                            else onPlay(item, source)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (item.genres.isNotEmpty()) {
                        Spacer(Modifier.height(18.dp)); Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { item.genres.forEach { GenreChip(text = it, selected = false, onClick = {}) } }
                    }
                    if (item.sources.size > 1) {
                        Spacer(Modifier.height(22.dp)); Text("QUALITY", color = TextMuted, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp)); Spacer(Modifier.height(8.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { item.sources.forEachIndexed { index, value -> ChoiceChip(value.label, index == selectedSource) { selectedSource = index } } }
                    }
                    if (item.audioVariants.size > 1) {
                        Spacer(Modifier.height(20.dp)); Text("AUDIO LANGUAGE", color = TextMuted, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp)); Spacer(Modifier.height(8.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item.audioVariants.forEach { variant -> ChoiceChip(variant.label, selectedAudio?.id == variant.id || (selectedAudio == null && variant.label == "Original")) { selectedAudio = variant } }
                        }
                    }
                    if (loading) { Spacer(Modifier.height(20.dp)); ShimmerBox(Modifier.fillMaxWidth().height(12.dp)) }
                    if (error != null) { Spacer(Modifier.height(18.dp)); NoticeBanner(error, onRetry = onRetry) }
                    Spacer(Modifier.height(26.dp)); Text("The story", color = TextPrimary, style = MaterialTheme.typography.titleLarge); Spacer(Modifier.height(9.dp))
                    Text(item.description.ifBlank { "No description available yet." }, color = TextSecondary, style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp))
                }
            }

            if (item.isSeries && item.seasons.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(30.dp)); Text("Seasons", color = TextPrimary, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp)); Spacer(Modifier.height(10.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(item.seasons, key = { it.number }) { season -> ChoiceChip(season.name, season.number == item.selectedSeason) { onSelectSeason(season.number) } }
                    }
                }
                item {
                    Spacer(Modifier.height(22.dp)); Text("Episodes", color = TextPrimary, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp)); Spacer(Modifier.height(10.dp))
                }
                items(item.episodes, key = { "${item.selectedSeason}:${it.number}" }) { episode ->
                    EpisodeRow(episode) { onPlayEpisode(item, item.selectedSeason, episode.number, selectedAudio) }
                }
            }
        }
    }
}

@Composable
private fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (selected) Teal else Color.White.copy(.055f)).border(1.dp, if (selected) Color.Transparent else Color.White.copy(.1f), RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        color = if (selected) Ink else TextSecondary,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp
    )
}

@Composable
private fun EpisodeRow(episode: EpisodeInfo, onPlay: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(width = 132.dp, height = 76.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(.05f))) {
            AsyncImage(episode.stillUrl, null, Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.Center).size(36.dp).background(Color.Black.copy(.58f), RoundedCornerShape(50)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PlayArrow, null, tint = Color.White) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${episode.number}. ${episode.name}", color = TextPrimary, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(buildString { if (episode.runtimeMinutes > 0) append("${episode.runtimeMinutes}m"); if (episode.airDate.isNotBlank()) { if (isNotEmpty()) append(" · "); append(episode.airDate) } }, color = TextMuted, fontSize = 10.sp)
        }
    }
}
