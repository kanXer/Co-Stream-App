package app.costream.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.costream.ui.theme.Ink
import app.costream.ui.theme.Teal
import app.costream.ui.theme.TextPrimary

@Composable
fun HomeTopBar(
    savedCount: Int,
    onSearch: () -> Unit,
    onMyList: () -> Unit,
    onProfile: () -> Unit,
    photoUrl: String? = null,
    userName: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Ink.copy(alpha = 0.92f),
                        Ink.copy(alpha = 0.55f),
                        Color.Transparent
                    )
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoStreamWordmark(markSize = 26.dp, textSize = 11)
        Spacer(Modifier.weight(1f))
        GlassIconButton(
            icon = Icons.Rounded.Search,
            contentDescription = "Search",
            size = 38.dp,
            onClick = onSearch
        )
        Spacer(Modifier.width(8.dp))
        Box {
            GlassIconButton(
                icon = Icons.Rounded.Bookmark,
                contentDescription = "My List",
                size = 38.dp,
                onClick = onMyList
            )
            if (savedCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(Teal)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        ProfileAvatar(size = 36.dp, onClick = onProfile, photoUrl = photoUrl, name = userName)
    }
}

@Composable
fun SubScreenTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Ink.copy(alpha = 0.94f))
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassIconButton(
            icon = Icons.Rounded.ArrowBackIosNew,
            contentDescription = "Back",
            size = 38.dp,
            onClick = onBack
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            color = TextPrimary,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}
