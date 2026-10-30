package app.costream.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.costream.data.MediaItem
import app.costream.ui.SearchUiState
import app.costream.ui.components.EmptyState
import app.costream.ui.components.GenreChip
import app.costream.ui.components.GlassIconButton
import app.costream.ui.components.PosterCard
import app.costream.ui.components.ShimmerBox
import app.costream.ui.theme.Ink
import app.costream.ui.theme.Teal
import app.costream.ui.theme.TextMuted
import app.costream.ui.theme.TextPrimary
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    query: String,
    state: SearchUiState,
    savedIds: Set<String>,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenDetails: (MediaItem) -> Unit
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(160); runCatching { focusRequester.requestFocus() }; keyboard?.show() }
    BackHandler(enabled = query.isNotEmpty()) { onQueryChange(""); keyboard?.hide() }

    Column(Modifier.fillMaxSize().background(Ink).statusBarsPadding().imePadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.Rounded.ArrowBackIosNew, "Back", size = 38.dp, onClick = onBack)
            Spacer(Modifier.width(10.dp))
            SearchField(query, focusRequester, onQueryChange, { onQueryChange("") }, Modifier.weight(1f))
        }
        when {
            query.isBlank() -> SearchStart(onQueryChange)
            state is SearchUiState.Loading -> SearchLoading(query)
            state is SearchUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Rounded.WifiOff, "Search took a wrong turn", state.message, actionLabel = "Try again", onAction = onRetry)
            }
            state is SearchUiState.Ready && state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Rounded.Search, "No matching titles", "Try a shorter title, actor, genre or language.", actionLabel = "Clear search", onAction = { onQueryChange("") })
            }
            state is SearchUiState.Ready -> SearchResults(state, savedIds, onOpenDetails)
            else -> SearchStart(onQueryChange)
        }
    }
}

@Composable
private fun SearchField(query: String, focusRequester: FocusRequester, onQueryChange: (String) -> Unit, onClear: () -> Unit, modifier: Modifier) {
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.height(46.dp).background(Color.White.copy(alpha = .065f), RoundedCornerShape(15.dp)).border(1.dp, Color.White.copy(alpha = .09f), RoundedCornerShape(15.dp)).focusRequester(focusRequester),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary, fontSize = 14.sp),
        cursorBrush = SolidColor(Teal),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        decorationBox = { field ->
            Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, null, tint = TextMuted, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) { if (query.isEmpty()) Text("Search movies and web series…", color = TextMuted, fontSize = 13.sp); field() }
                if (query.isNotEmpty()) Icon(Icons.Rounded.Close, "Clear", tint = TextMuted, modifier = Modifier.size(26.dp).clickable(onClick = onClear).padding(5.dp))
            }
        }
    )
}

@Composable
private fun SearchStart(onSuggestion: (String) -> Unit) {
    val suggestions = listOf("Hindi", "Action", "Comedy", "Thriller", "Romance", "Animation", "Crime", "Family")
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 18.dp)) {
        Text("Find your next story", color = TextPrimary, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(7.dp))
        Text("Search the complete movie and web-series catalog.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.forEach { GenreChip(text = it, selected = false, onClick = { onSuggestion(it) }) }
        }
    }
}

@Composable
private fun SearchResults(state: SearchUiState.Ready, savedIds: Set<String>, onOpenDetails: (MediaItem) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(span = { GridItemSpan(3) }) { Text("${state.items.size} results for “${state.query}”", color = TextPrimary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(4.dp)) }
        items(state.items, key = { it.id }) { item -> PosterCard(item, Modifier.fillMaxWidth(), width = null, saved = item.id in savedIds) { onOpenDetails(item) } }
    }
}

@Composable
private fun SearchLoading(query: String) {
    Column(Modifier.fillMaxSize()) {
        Text("Searching for “$query”…", color = TextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(12) { Column { ShimmerBox(Modifier.fillMaxWidth().height(160.dp), RoundedCornerShape(14.dp)); Spacer(Modifier.height(6.dp)); ShimmerBox(Modifier.fillMaxWidth(.7f).height(9.dp)) } }
        }
    }
}
