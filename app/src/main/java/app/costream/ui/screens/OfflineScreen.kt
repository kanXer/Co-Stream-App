package app.costream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.costream.ui.components.EmptyState
import app.costream.ui.theme.Ink
import app.costream.ui.theme.TextMuted

@Composable
fun OfflineScreen(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Ink).statusBarsPadding()) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            EmptyState(
                Icons.Rounded.CloudOff,
                "Offline library",
                "Download movies and episodes to watch without internet.",
                actionLabel = "Browse catalog",
                onAction = { /* noop */ }
            )
            Text("No offline content yet", color = TextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
