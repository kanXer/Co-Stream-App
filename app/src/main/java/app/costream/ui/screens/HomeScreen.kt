package app.costream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.costream.data.MediaItem
import app.costream.data.WatchEntry
import app.costream.ui.HomeUiState
import app.costream.ui.components.CoStreamMark
import app.costream.ui.components.EmptyState
import app.costream.ui.components.HeroBanner
import app.costream.ui.components.HomeTopBar
import app.costream.ui.components.Pill
import app.costream.ui.components.PosterCard
import app.costream.ui.components.RankedPosterCard
import app.costream.ui.components.SectionHeader
import app.costream.ui.components.ShimmerBox
import app.costream.ui.components.WideCard
import app.costream.ui.theme.Ink
import app.costream.ui.theme.Surface
import app.costream.ui.theme.Teal
import app.costream.ui.theme.TextMuted
import app.costream.ui.theme.TextPrimary
import kotlinx.coroutines.delay

private enum class CatalogTab { HOME, MOVIES, SERIES }

@Composable
fun HomeScreen(
    state: HomeUiState,
    myList: List<MediaItem>,
    continueWatching: List<WatchEntry>,
    onSearch: () -> Unit,
    onMyList: () -> Unit,
    onProfile: () -> Unit,
    onPlay: (MediaItem) -> Unit,
    onDetails: (MediaItem) -> Unit,
    onToggleSaved: (MediaItem) -> Unit,
    onResume: (WatchEntry) -> Unit,
    onRefresh: () -> Unit,
    photoUrl: String? = null,
    userName: String? = null
) {
    val savedIds = remember(myList) { myList.map { it.id }.toSet() }
    var selectedTab by remember { androidx.compose.runtime.mutableStateOf(CatalogTab.HOME) }
    Box(Modifier.fillMaxSize().background(Ink)) {
        when (state) {
            HomeUiState.Idle, HomeUiState.Loading -> LoadingHome(myList.size, onSearch, onMyList, onProfile, photoUrl, userName)
            is HomeUiState.Error -> Column(Modifier.fillMaxSize()) {
                HomeTopBar(myList.size, onSearch, onMyList, onProfile, photoUrl, userName)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(Icons.Rounded.CloudOff, "Lost the signal", state.message, actionLabel = "Try again", onAction = onRefresh)
                }
            }
            is HomeUiState.Ready -> CatalogHome(
                feed = when (selectedTab) {
                    CatalogTab.HOME -> state.feed
                    CatalogTab.MOVIES -> state.feed.copy(
                        featured = state.feed.featured.filterNot { it.isSeries },
                        rows = state.feed.rows.map { it.copy(items = it.items.filterNot { item -> item.isSeries }) }.filter { it.items.isNotEmpty() }
                    )
                    CatalogTab.SERIES -> state.feed.copy(
                        featured = state.feed.featured.filter { it.isSeries },
                        rows = state.feed.rows.map { it.copy(items = it.items.filter { item -> item.isSeries }) }.filter { it.items.isNotEmpty() }
                    )
                },
                savedIds = savedIds,
                continueWatching = continueWatching,
                onSearch = onSearch,
                onMyList = onMyList,
                onProfile = onProfile,
                onPlay = onPlay,
                onDetails = onDetails,
                onToggleSaved = onToggleSaved,
                onResume = onResume,
                photoUrl = photoUrl,
                userName = userName
            )
        }
        CatalogBottomBar(
            selected = selectedTab,
            onSelect = { selectedTab = it },
            onMyList = onMyList,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun CatalogHome(
    feed: app.costream.data.HomeFeed,
    savedIds: Set<String>,
    continueWatching: List<WatchEntry>,
    onSearch: () -> Unit,
    onMyList: () -> Unit,
    onProfile: () -> Unit,
    onPlay: (MediaItem) -> Unit,
    onDetails: (MediaItem) -> Unit,
    onToggleSaved: (MediaItem) -> Unit,
    onResume: (WatchEntry) -> Unit,
    photoUrl: String? = null,
    userName: String? = null
) {
    val featured = feed.featured
    val pagerState = rememberPagerState(pageCount = { featured.size.coerceAtLeast(1) })
    var currentPage by remember(featured) { mutableIntStateOf(0) }
    LaunchedEffect(pagerState.currentPage) { currentPage = pagerState.currentPage }
    LaunchedEffect(pagerState, featured.size) {
        if (featured.size <= 1) return@LaunchedEffect
        while (true) {
            delay(8_500)
            if (!pagerState.isScrollInProgress) pagerState.animateScrollToPage((pagerState.currentPage + 1) % featured.size)
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 72.dp, bottom = 104.dp)) {
            item {
                if (featured.isNotEmpty()) Box {
                    HorizontalPager(state = pagerState, key = { featured.getOrNull(it)?.id ?: it }) { page ->
                        featured.getOrNull(page)?.let { item ->
                            HeroBanner(
                                item = item,
                                saved = item.id in savedIds,
                                onPlay = { onPlay(item) },
                                onDetails = { onDetails(item) },
                                onToggleSaved = { onToggleSaved(item) }
                            )
                        }
                    }
                    if (featured.size > 1) Pill(
                        text = "${currentPage + 1} / ${featured.size}",
                        color = TextPrimary,
                        background = Color.Black.copy(alpha = .4f),
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 82.dp, end = 20.dp)
                    )
                }
            }
            if (continueWatching.isNotEmpty()) {
                item { Spacer(Modifier.height(24.dp)); SectionHeader("Continue watching", subtitle = "Jump back in", trailingText = "${continueWatching.size} in progress") }
                item {
                    Spacer(Modifier.height(12.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(continueWatching, key = { it.item.id }) { entry -> WideCard(entry.item, entry.progress, onClick = { onResume(entry) }) }
                    }
                }
            }
            items(feed.rows) { row ->
                Spacer(Modifier.height(34.dp))
                SectionHeader(row.title, subtitle = row.subtitle, trailingText = if (row.ranked) "TOP 10" else null)
                Spacer(Modifier.height(14.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(if (row.ranked) 2.dp else 12.dp)) {
                    itemsIndexed(row.items, key = { _, item -> "${row.title}:${item.id}" }) { index, item ->
                        if (row.ranked) RankedPosterCard(index + 1, item, item.id in savedIds) { onDetails(item) }
                        else PosterCard(item, saved = item.id in savedIds) { onDetails(item) }
                    }
                }
            }
            item { Spacer(Modifier.height(44.dp)); CatalogFooter(); Spacer(Modifier.height(20.dp)) }
        }
        HomeTopBar(savedIds.size, onSearch, onMyList, onProfile, photoUrl, userName)
    }
}

@Composable
private fun CatalogBottomBar(
    selected: CatalogTab,
    onSelect: (CatalogTab) -> Unit,
    onMyList: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Surface.copy(alpha = .97f))
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        BottomItem("Home", Icons.Rounded.Home, selected == CatalogTab.HOME) { onSelect(CatalogTab.HOME) }
        BottomItem("Movies", Icons.Rounded.Movie, selected == CatalogTab.MOVIES) { onSelect(CatalogTab.MOVIES) }
        BottomItem("Web Series", Icons.Rounded.Tv, selected == CatalogTab.SERIES) { onSelect(CatalogTab.SERIES) }
        BottomItem("Offline", Icons.Rounded.CloudOff, false, onMyList)
    }
}

@Composable
private fun BottomItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = if (selected) Teal else TextMuted, modifier = Modifier.size(21.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, color = if (selected) Teal else TextMuted, fontSize = 8.5.sp, maxLines = 1)
    }
}

@Composable
private fun LoadingHome(savedCount: Int, onSearch: () -> Unit, onMyList: () -> Unit, onProfile: () -> Unit, photoUrl: String? = null, userName: String? = null) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item { ShimmerBox(Modifier.fillMaxWidth().height(566.dp), RoundedCornerShape(0.dp)) }
            items(2) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                    ShimmerBox(Modifier.fillMaxWidth(.55f).height(22.dp))
                    Spacer(Modifier.height(14.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(3) { ShimmerBox(Modifier.fillParentMaxWidth(.34f).height(198.dp)) }
                    }
                }
            }
        }
        HomeTopBar(savedCount, onSearch, onMyList, onProfile, photoUrl, userName)
    }
}

@Composable
private fun CatalogFooter() {
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CoStreamMark(size = 34.dp, glowing = false)
        Spacer(Modifier.height(9.dp))
        Text("CO-STREAM", color = TextMuted, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp))
        Spacer(Modifier.height(5.dp))
        Text("Movies, web series and every episode in one place.", color = TextMuted.copy(alpha = .7f), fontSize = 10.5.sp, textAlign = TextAlign.Center)
    }
}
