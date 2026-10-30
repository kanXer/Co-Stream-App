package app.costream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.costream.data.NetMirrorRepository
import app.costream.ui.components.CoStreamWordmark
import app.costream.ui.theme.Blue
import app.costream.ui.theme.Ink
import app.costream.ui.theme.Pink
import app.costream.ui.theme.Teal
import app.costream.ui.theme.TextMuted
import app.costream.ui.theme.TextPrimary
import app.costream.ui.theme.TextSecondary
import app.costream.ui.theme.Violet

private val platformColors = mapOf(
    "Netflix" to Color(0xFFE50914),
    "PrimeVideo" to Color(0xFF00A8E1),
    "JioHotstar" to Blue,
    "SonyLIV" to Violet,
    "AppleTV" to Color.White,
    "Crunchyroll" to Color(0xFFF47521),
    "MX" to Color(0xFF0085F6)
)

@Composable
fun PlatformSelectionScreen(
    initial: List<String>,
    onContinue: (List<String>) -> Unit
) {
    var selected by remember(initial) { mutableStateOf(initial.toSet()) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(14.dp))
        CoStreamWordmark(markSize = 30.dp, textSize = 12)
        Spacer(Modifier.height(32.dp))
        Text("Choose your platforms", color = TextPrimary, style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text("Your Home feed will be built around these services.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(22.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(NetMirrorRepository.PLATFORMS) { platform ->
                val active = platform in selected
                val color = platformColors[platform] ?: Teal
                Box(
                    modifier = Modifier
                        .height(112.dp)
                        .background(if (active) color.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.04f), RoundedCornerShape(20.dp))
                        .border(1.dp, if (active) color.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                        .clickable {
                            selected = if (active) selected - platform else selected + platform
                        }
                        .padding(14.dp)
                ) {
                    Column(Modifier.align(Alignment.BottomStart)) {
                        Text(NetMirrorRepository.platformLabel(platform), color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(if (active) "Selected" else "Tap to select", color = if (active) color else TextMuted, fontSize = 10.sp)
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(23.dp)
                            .background(if (active) color else Color.Transparent, CircleShape)
                            .border(1.dp, if (active) color else Color.White.copy(alpha = 0.18f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (active) Icon(Icons.Rounded.Check, contentDescription = null, tint = Ink, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .background(if (selected.isEmpty()) Color.White.copy(alpha = 0.08f) else Teal, RoundedCornerShape(18.dp))
                .clickable(enabled = selected.isNotEmpty()) { onContinue(selected.toList()) }
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Continue", color = if (selected.isEmpty()) TextMuted else Ink, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ArrowForward, contentDescription = null, tint = if (selected.isEmpty()) TextMuted else Ink)
        }
        Spacer(Modifier.height(16.dp))
    }
}
