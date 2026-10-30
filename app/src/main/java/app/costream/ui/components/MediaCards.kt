package app.costream.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.costream.data.MediaItem
import app.costream.ui.theme.Ink
import app.costream.ui.theme.Pink
import app.costream.ui.theme.Red
import app.costream.ui.theme.SurfaceHigh
import app.costream.ui.theme.Teal
import app.costream.ui.theme.TextMuted
import app.costream.ui.theme.TextPrimary
import app.costream.ui.theme.TextSecondary
import app.costream.ui.theme.accentColors
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlin.math.abs

fun mediaMeta(item: MediaItem): String {
    return buildList {
        if (item.isLive) add("LIVE")
        if (!item.isLive) item.year?.takeIf { it.isNotBlank() }?.let { add(it) }
        item.runtime?.takeIf { it.isNotBlank() }?.let { add(it) }
        val genre = item.genres.take(2).joinToString(" · ")
        if (genre.isNotBlank()) add(genre)
    }.joinToString("  ·  ")
}

@Composable
fun GeneratedArtwork(
    item: MediaItem,
    modifier: Modifier = Modifier,
    vivid: Boolean = false
) {
    val colors = accentColors(item.accentIndex)
    val background = if (vivid) {
        Brush.linearGradient(
            listOf(
                colors[0].copy(alpha = 0.95f),
                colors[1].copy(alpha = 0.92f),
                Ink.copy(alpha = 0.95f)
            ),
            start = Offset.Zero,
            end = Offset(1_000f, 1_200f)
        )
    } else {
        Brush.linearGradient(
            listOf(
                colors[0].copy(alpha = 0.78f),
                colors[1].copy(alpha = 0.86f),
                Color(0xFF0C0B13)
            ),
            start = Offset.Zero,
            end = Offset(800f, 1_100f)
        )
    }

    Box(
        modifier = modifier
            .background(background)
            .clip(RoundedCornerShape(0.dp))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawCircle(
                color = Color.White.copy(alpha = 0.10f),
                radius = w * 0.55f,
                center = Offset(w * 0.92f, h * 0.10f)
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.12f),
                radius = w * 0.62f,
                center = Offset(w * 0.05f, h * 0.92f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.20f), Color.Transparent),
                    center = Offset(w * 0.28f, h * 0.32f),
                    radius = w * 0.7f
                ),
                radius = w * 0.7f,
                center = Offset(w * 0.28f, h * 0.32f)
            )
            val step = w / 7f
            var x = -h
            while (x < w + h) {
                drawLine(
                    color = Color.White.copy(alpha = 0.035f),
                    start = Offset(x, 0f),
                    end = Offset(x + h, h),
                    strokeWidth = 1.2f
                )
                x += step
            }
        }

        Text(
            text = "CO-STREAM",
            color = Color.White.copy(alpha = 0.46f),
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 1.4.sp,
                fontSize = 7.sp
            ),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(9.dp)
        )

        if (item.isLocal) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.24f))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Movie,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(22.dp)
                )
            }
        } else {
            Text(
                text = item.title.take(1).uppercase(),
                color = Color.White.copy(alpha = if (vivid) 0.26f else 0.14f),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = if (vivid) 116.sp else 92.sp,
                    lineHeight = 100.sp,
                    fontWeight = FontWeight.Black
                ),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 10.dp, bottom = 2.dp)
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            Ink.copy(alpha = 0.55f)
                        )
                    )
                )
        )
    }
}

@Composable
fun MediaArtwork(
    item: MediaItem,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center
) {
    val context = LocalContext.current
    val imageUrl = item.backdropUrl ?: item.posterUrl
    val request = remember(imageUrl) {
        if (imageUrl.isNullOrBlank()) null else {
            ImageRequest.Builder(context)
                .data(imageUrl)
                .crossfade(260)
                .build()
        }
    }

    Box(modifier = modifier) {
        GeneratedArtwork(item, Modifier.fillMaxSize(), vivid = item.isLive)
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                alignment = alignment
            )
        }
    }
}

@Composable
fun PosterCard(
    item: MediaItem,
    modifier: Modifier = Modifier,
    width: Dp? = 132.dp,
    saved: Boolean = false,
    progress: Float? = null,
    onClick: () -> Unit
) {
    val sized = if (width != null) modifier.width(width) else modifier.fillMaxWidth()
    Box(
        modifier = sized
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceHigh)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(16.dp))
        ) {
            MediaArtwork(item, Modifier.fillMaxSize())
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.42f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.92f)
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (item.isLive) {
                    Pill(
                        text = item.badge ?: "LIVE",
                        color = Color.White,
                        background = if (item.badge == "LIVE") Red else Color.Black.copy(alpha = 0.55f),
                        borderColor = Color.Transparent
                    )
                } else if (!item.badge.isNullOrBlank()) {
                    Pill(text = item.badge)
                }
            }

            if (saved) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Bookmark,
                        contentDescription = null,
                        tint = Teal,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 9.dp, vertical = 8.dp)
            ) {
                Text(
                    text = item.title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = 11.5.sp,
                        lineHeight = 13.5.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = when {
                    item.isLive -> item.genres.firstOrNull() ?: "Live"
                    item.isLocal -> item.runtime ?: "On device"
                    else -> item.year ?: item.genres.firstOrNull()
                }
                if (!meta.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = meta,
                        color = TextSecondary.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.5.sp,
                            letterSpacing = 0.2.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (progress != null && progress > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.18f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(Teal)
                    )
                }
            }
        }
    }
}

@Composable
fun RankedPosterCard(
    rank: Int,
    item: MediaItem,
    saved: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(174.dp)
            .height(202.dp)
    ) {
        Text(
            text = rank.toString(),
            color = Color.White.copy(alpha = 0.40f),
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 96.sp,
                lineHeight = 100.sp,
                fontWeight = FontWeight.Black,
                drawStyle = Stroke(width = 2.4f)
            ),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 0.dp, bottom = 2.dp)
        )
        PosterCard(
            item = item,
            width = 122.dp,
            saved = saved,
            onClick = onClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 4.dp)
        )
    }
}

@Composable
fun WideCard(
    item: MediaItem,
    progress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(252.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceHigh)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(18.dp))
        ) {
            MediaArtwork(item, Modifier.fillMaxSize())
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.25f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.90f)
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.42f))
                    .border(1.dp, Color.White.copy(alpha = 0.24f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(11.dp)
            ) {
                Text(
                    text = item.title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Resume · ${(progress * 100).toInt()}%",
                    color = Teal,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        letterSpacing = 0.3.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.20f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(Teal)
                )
            }
        }
    }
}

@Composable
fun LiveChannelCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(292.dp)
            .height(168.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        GeneratedArtwork(item, Modifier.fillMaxSize(), vivid = true)
        Canvas(Modifier.fillMaxSize()) {
            var y = 0f
            while (y < size.height) {
                drawLine(
                    color = Color.Black.copy(alpha = 0.06f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
                y += 4.dp.toPx()
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.12f),
                            Color.Black.copy(alpha = 0.06f),
                            Color.Black.copy(alpha = 0.72f)
                        )
                    )
                )
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Pill(
                text = if (item.badge == "LIVE") "LIVE" else item.badge ?: "LIVE",
                color = Color.White,
                background = if (item.badge == "LIVE") Red else Color.Black.copy(alpha = 0.45f),
                borderColor = Color.Transparent
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(30.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (item.isLive) Icons.Rounded.Sensors else Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(17.dp)
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, end = 50.dp, bottom = 13.dp)
        ) {
            Text(
                text = item.title,
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = item.provider,
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.5.sp,
                    letterSpacing = 0.4.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.16f))
                .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun HeroBanner(
    item: MediaItem,
    saved: Boolean,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
    onToggleSaved: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(566.dp)
    ) {
        MediaArtwork(
            item = item,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Ink.copy(alpha = 0.70f),
                        0.22f to Ink.copy(alpha = 0.12f),
                        0.48f to Color.Transparent,
                        0.72f to Ink.copy(alpha = 0.62f),
                        1f to Ink
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(Ink.copy(alpha = 0.60f), Color.Transparent),
                        startX = 0f,
                        endX = 900f
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (item.isLive) {
                    Pill(
                        text = if (item.badge == "LIVE") "LIVE NOW" else item.badge ?: "LIVE",
                        color = Color.White,
                        background = if (item.badge == "LIVE") Red else Color.Black.copy(alpha = 0.50f),
                        borderColor = Color.Transparent
                    )
                } else {
                    Pill(
                        text = item.badge ?: "FEATURED",
                        color = Ink,
                        background = Teal,
                        borderColor = Color.Transparent
                    )
                }
                Text(
                    text = item.provider,
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 0.6.sp,
                        fontSize = 9.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = item.title,
                color = TextPrimary,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontSize = 38.sp,
                    lineHeight = 40.sp
                ),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = mediaMeta(item),
                color = TextSecondary,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.5.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.description.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = item.description,
                    color = TextPrimary.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PrimaryButton(
                    text = if (item.isLive) "Watch live" else "Play",
                    modifier = Modifier.weight(1f),
                    onClick = onPlay
                )
                GlassIconButton(
                    icon = if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                    contentDescription = if (saved) "Remove from My List" else "Add to My List",
                    size = 54.dp,
                    tint = if (saved) Teal else TextPrimary,
                    onClick = onToggleSaved
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "More info  ›",
                color = TextSecondary,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onDetails)
                    .padding(vertical = 5.dp)
            )
        }
    }
}
