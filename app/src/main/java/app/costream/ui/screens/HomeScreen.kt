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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import app.costream.data.HomeCategories
import app.costream.data.HomeRow
import app.costream.data.MediaItem
import app.costream.data.WatchEntry
import app.costream.ui.HomeUiState
import app.costream.ui.components.CoStreamMark
import app.costream.ui.components.EmptyState
import app.costream.ui.components.GenreChip
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
    var selectedTab by remember { mutableStateOf(CatalogTab.HOME) }
    var selectedCategory by remember(selectedTab) { mutableStateOf("All") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(selectedTab) {
        listState.scrollToItem(0)
    }
    LaunchedEffect(selectedCategory) {
        listState.scrollToItem(0)
    }

    val movieCategories = remember {
        listOf(
            "All",
            "🆕 Latest Hindi",
            "🎬 2026 Bollywood",
            "🔥 Most Popular",
            "⭐ Top Rated",
            "💥 Action",
            "🔪 Thrillers",
            "💕 Romance",
            "😂 Comedy",
            "👨‍👩‍👧 Family",
            "🚀 Sci-Fi"
        )
    }
    val seriesCategories = remember {
        listOf(
            "All",
            "🆕 Latest Hindi",
            "🔥 Most Popular",
            "⭐ Top Rated",
            "💥 Action",
            "🕵️ Crime",
            "👻 Horror",
            "🌸 K-Drama",
            "⚔️ Anime",
            "🌍 Hollywood"
        )
    }

    Box(Modifier.fillMaxSize().background(Ink)) {
        when (state) {
            HomeUiState.Idle, HomeUiState.Loading -> LoadingHome(myList.size, onSearch, onMyList, onProfile, photoUrl, userName)
            is HomeUiState.Error -> Column(Modifier.fillMaxSize()) {
                HomeTopBar(myList.size, onSearch, onMyList, onProfile, photoUrl, userName)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(Icons.Rounded.CloudOff, "Lost the signal", state.message, actionLabel = "Try again", onAction = onRefresh)
                }
            }
            is HomeUiState.Ready -> {
                val currentFeatured = remember(selectedTab, state.feed) {
                    when (selectedTab) {
                        CatalogTab.HOME -> state.feed.featured
                        CatalogTab.MOVIES -> state.feed.featured.filterNot { it.isSeries }.ifEmpty {
                            state.feed.rows.flatMap { it.items }.filterNot { it.isSeries }.distinctBy { it.id }.take(8)
                        }
                        CatalogTab.SERIES -> state.feed.rows
                            .filter { it.category in listOf(HomeCategories.TRENDING_SERIES, HomeCategories.HINDI_SERIES, HomeCategories.THRILLER_SERIES) }
                            .flatMap { it.items }
                            .filter { it.isSeries }
                            .distinctBy { it.id }
                            .take(8)
                            .ifEmpty { state.feed.featured.filter { it.isSeries } }
                    }
                }

                val currentRows = remember(selectedTab, selectedCategory, state.feed.rows) {
                    when (selectedTab) {
                        CatalogTab.HOME -> state.feed.rows
                        CatalogTab.MOVIES -> {
                            val movieRows = state.feed.rows
                                .map { it.copy(items = it.items.filterNot { item -> item.isSeries }) }
                                .filter { it.items.isNotEmpty() }
                            when (selectedCategory) {
                                "🆕 Latest Hindi" -> movieRows.filter { it.title.contains("Latest Hindi", ignoreCase = true) || it.category == HomeCategories.LATEST_MOVIES }
                                "🎬 2026 Bollywood" -> movieRows.filter { it.title.contains("Bollywood", ignoreCase = true) || it.title.contains("2026", ignoreCase = true) }
                                "🔥 Most Popular" -> movieRows.filter { it.title.contains("Popular", ignoreCase = true) }
                                "⭐ Top Rated" -> movieRows.filter { it.title.contains("Top Rated", ignoreCase = true) }
                                "💥 Action" -> movieRows.filter { it.category == HomeCategories.ACTION_MOVIES || it.title.contains("Action", ignoreCase = true) }
                                "🔪 Thrillers" -> movieRows.filter { it.category == HomeCategories.THRILLER_MOVIES || it.title.contains("Thriller", ignoreCase = true) }
                                "💕 Romance" -> movieRows.filter { it.category == HomeCategories.ROMANCE_MOVIES || it.title.contains("Romance", ignoreCase = true) }
                                "😂 Comedy" -> movieRows.filter { it.category == HomeCategories.COMEDY_MOVIES || it.title.contains("Comedy", ignoreCase = true) }
                                "👨‍👩‍👧 Family" -> movieRows.filter { it.title.contains("Family", ignoreCase = true) }
                                "🚀 Sci-Fi" -> movieRows.filter { it.category == HomeCategories.SCIFI || it.title.contains("Sci-Fi", ignoreCase = true) }
                                else -> movieRows
                            }
                        }
                        CatalogTab.SERIES -> {
                            val seriesRows = state.feed.rows
                                .map { it.copy(items = it.items.filter { item -> item.isSeries }) }
                                .filter { it.items.isNotEmpty() }
                            when (selectedCategory) {
                                "🆕 Latest Hindi" -> seriesRows.filter { it.title.contains("Latest Hindi", ignoreCase = true) || it.title.contains("Fresh", ignoreCase = true) }
                                "🔥 Most Popular" -> seriesRows.filter { it.title.contains("Popular", ignoreCase = true) }
                                "⭐ Top Rated" -> seriesRows.filter { it.title.contains("Top Rated", ignoreCase = true) }
                                "💥 Action" -> seriesRows.filter { it.title.contains("Action", ignoreCase = true) }
                                "🕵️ Crime" -> seriesRows.filter { it.category == HomeCategories.CRIME_SERIES || it.title.contains("Crime", ignoreCase = true) }
                                "👻 Horror" -> seriesRows.filter { it.category == HomeCategories.HORROR || it.title.contains("Horror", ignoreCase = true) }
                                "🌸 K-Drama" -> seriesRows.filter { it.category == HomeCategories.KDRAMA_SERIES || it.title.contains("K-Drama", ignoreCase = true) }
                                "⚔️ Anime" -> seriesRows.filter { it.category == HomeCategories.ANIME_SERIES || it.title.contains("Anime", ignoreCase = true) }
                                "🌍 Hollywood" -> seriesRows.filter { it.category == HomeCategories.GLOBAL_SERIES || it.title.contains("Hollywood", ignoreCase = true) || it.title.contains("Global", ignoreCase = true) }
                                else -> seriesRows
                            }
                        }
                    }
                }

                val categoryChips = when (selectedTab) {
                    CatalogTab.HOME -> emptyList()
                    CatalogTab.MOVIES -> movieCategories
                    CatalogTab.SERIES -> seriesCategories
                }

                CatalogHome(
                    listState = listState,
                    featured = currentFeatured,
                    rows = currentRows,
                    categoryChips = categoryChips,
                    selectedCategory = selectedCategory,
                    onSelectCategory = { selectedCategory = it },
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
        }
        CatalogBottomBar(
            selected = selectedTab,
            onSelect = { selectedTab = it },
            onScrollToTop = { scope.launch { listState.animateScrollToItem(0) } },
            onMyList = onMyList,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun CatalogHome(
    listState: LazyListState,
    featured: List<MediaItem>,
    rows: List<HomeRow>,
    categoryChips: List<String>,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
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
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 72.dp, bottom = 104.dp)) {
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
            if (categoryChips.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(18.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(categoryChips) { chip ->
                            GenreChip(
                                text = chip,
                                selected = selectedCategory == chip,
                                onClick = { onSelectCategory(chip) }
                            )
                        }
                    }
                }
            }
            if (continueWatching.isNotEmpty()) {
                item { Spacer(Modifier.height(28.dp)); SectionHeader("Continue watching", subtitle = "Jump back in", trailingText = "${continueWatching.size} in progress") }
                item {
                    Spacer(Modifier.height(16.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(continueWatching, key = { it.item.id }) { entry -> WideCard(entry.item, entry.progress, onClick = { onResume(entry) }) }
                    }
                }
            }
            items(rows) { row ->
                Spacer(Modifier.height(34.dp))
                SectionHeader(row.title, subtitle = row.subtitle, trailingText = if (row.ranked) "TOP 10" else null)
                Spacer(Modifier.height(16.dp))
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
    onScrollToTop: () -> Unit,
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
        BottomItem("Home", Icons.Rounded.Home, selected == CatalogTab.HOME) {
            if (selected == CatalogTab.HOME) onScrollToTop() else onSelect(CatalogTab.HOME)
        }
        BottomItem("Movies", Icons.Rounded.Movie, selected == CatalogTab.MOVIES) {
            if (selected == CatalogTab.MOVIES) onScrollToTop() else onSelect(CatalogTab.MOVIES)
        }
        BottomItem("Web Series", Icons.Rounded.Tv, selected == CatalogTab.SERIES) {
            if (selected == CatalogTab.SERIES) onScrollToTop() else onSelect(CatalogTab.SERIES)
        }
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
