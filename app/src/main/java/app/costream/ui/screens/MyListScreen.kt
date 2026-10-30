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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.costream.data.MediaItem
import app.costream.ui.components.EmptyState
import app.costream.ui.components.PosterCard
import app.costream.ui.components.SubScreenTopBar
import app.costream.ui.theme.Ink
import app.costream.ui.theme.Teal
import app.costream.ui.theme.TextMuted
import app.costream.ui.theme.TextPrimary
import app.costream.ui.theme.TextSecondary

@Composable
fun MyListScreen(
    items: List<MediaItem>,
    onBack: () -> Unit,
    onOpenDetails: (MediaItem) -> Unit,
    onPlay: (MediaItem) -> Unit,
    onRemove: (MediaItem) -> Unit,
    onExplore: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
    ) {
        SubScreenTopBar(title = "My List", onBack = onBack)

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Rounded.BookmarkBorder,
                    title = "Save something for later",
                    body = "Tap the bookmark on any film or channel. Your list lives on this device — no account needed.",
                    actionLabel = "Explore catalog",
                    onAction = onExplore
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalArrangement = Arrangement.spacedBy(15.dp)
            ) {
                item(span = { GridItemSpan(3) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "${items.size} saved",
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Ready whenever you are",
                                color = TextMuted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            text = "ON THIS DEVICE",
                            color = Teal,
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.sp,
                                fontSize = 8.sp
                            )
                        )
                    }
                }

                items(items, key = { it.id }) { item ->
                    Box {
                        PosterCard(
                            item = item,
                            modifier = Modifier.fillMaxWidth(),
                            width = null,
                            saved = false,
                            onClick = {
                                if (item.isLocal) onPlay(item) else onOpenDetails(item)
                            }
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 6.dp, end = 6.dp)
                                .size(25.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.58f))
                                .clickable { onRemove(item) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Remove ${item.title}",
                                tint = Color.White.copy(alpha = 0.82f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                item(span = { GridItemSpan(3) }) {
                    Text(
                        text = "Saved locally. Nothing is synced to a server.",
                        color = TextMuted.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(top = 14.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}
