package app.costream.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.costream.ui.theme.Blue
import app.costream.ui.theme.Ink
import app.costream.ui.theme.Mint
import app.costream.ui.theme.Surface
import app.costream.ui.theme.SurfaceHigh
import app.costream.ui.theme.Teal
import app.costream.ui.theme.TextMuted
import app.costream.ui.theme.TextPrimary
import app.costream.ui.theme.TextSecondary
import app.costream.ui.theme.Violet

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = Icons.Rounded.PlayArrow,
    loading: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.975f else 1f, label = "primary_press")
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(Teal, Mint, Blue)))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled && !loading,
                onClick = onClick
            )
            .heightIn(min = 54.dp)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Ink,
                    strokeWidth = 2.dp
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Ink,
                    modifier = Modifier.size(21.dp)
                )
            }
            if (loading || icon != null) Spacer(Modifier.width(9.dp))
            Text(
                text = text,
                color = Ink,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    letterSpacing = 0.1.sp
                )
            )
        }
    }
}

@Composable
fun GhostButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.975f else 1f, label = "ghost_press")
    Row(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.075f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .heightIn(min = 54.dp)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            color = TextPrimary,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tint: Color = TextPrimary,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.32f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(size * 0.46f))
    }
}

@Composable
fun ProfileAvatar(
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    onClick: (() -> Unit)? = null,
    photoUrl: String? = null,
    name: String? = null
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Violet, Blue, Teal)))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (!photoUrl.isNullOrBlank()) {
            coil.compose.AsyncImage(
                model = photoUrl,
                contentDescription = name ?: "Profile",
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        } else {
            Text(
                text = name?.firstOrNull()?.uppercase() ?: "C",
                color = Ink,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = (size.value * 0.42f).sp
                )
            )
        }
    }
}

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TextPrimary,
    background: Color = Color.Black.copy(alpha = 0.48f),
    borderColor: Color = Color.White.copy(alpha = 0.16f)
) {
    Text(
        text = text.uppercase(),
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        color = color,
        style = MaterialTheme.typography.labelSmall.copy(
            letterSpacing = 0.8.sp,
            fontWeight = FontWeight.ExtraBold
        ),
        maxLines = 1
    )
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailingText: String? = null,
    onTrailingClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp
                )
            )
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailingText != null) {
            Text(
                text = trailingText,
                color = if (onTrailingClick != null) Teal else TextMuted,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp),
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .then(
                        if (onTrailingClick != null) {
                            Modifier.clickable(onClick = onTrailingClick)
                        } else Modifier
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun GenreChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) Teal else Color.White.copy(alpha = 0.06f)
    val fg = if (selected) Ink else TextSecondary
    Text(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(
                1.dp,
                if (selected) Color.Transparent else Color.White.copy(alpha = 0.10f),
                RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        color = fg,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    )
}

@Composable
fun NoticeBanner(
    text: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceHigh)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        if (onRetry != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onRetry)
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, tint = Teal, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("Retry", color = Teal, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(74.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.035f))
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Teal, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = title,
            color = TextPrimary,
            style = MaterialTheme.typography.titleLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            PrimaryButton(text = actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp)
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.32f,
        targetValue = 0.78f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(SurfaceHigh.copy(alpha = alpha))
    )
}

@Composable
fun StatLine(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            color = TextMuted,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp)
        )
        Spacer(Modifier.height(4.dp))
        Text(text = value, color = TextPrimary, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.07f))
    )
}
