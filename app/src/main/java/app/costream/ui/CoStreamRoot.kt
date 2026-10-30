package app.costream.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.costream.ui.components.CoStreamWordmark
import app.costream.ui.components.ProfileAvatar
import app.costream.ui.screens.DetailScreen
import app.costream.ui.screens.HomeScreen
import app.costream.ui.screens.LoginScreen
import app.costream.ui.screens.MyListScreen
import app.costream.ui.screens.PlatformSelectionScreen
import app.costream.ui.screens.PlayerScreen
import app.costream.ui.screens.SearchScreen
import app.costream.ui.theme.Ink
import app.costream.ui.theme.Surface
import app.costream.ui.theme.SurfaceHigh
import app.costream.ui.theme.Teal
import app.costream.ui.theme.TextMuted
import app.costream.ui.theme.TextPrimary
import app.costream.ui.theme.TextSecondary

@Composable
fun CoStreamRoot(viewModel: AppViewModel, pipMode: Boolean) {
    val snackbarHostState = remember { SnackbarHostState() }
    var profileOpen by remember { mutableStateOf(false) }
    val screen = viewModel.currentScreen

    BackHandler(enabled = screen !is Screen.Login && screen !is Screen.Home && screen !is Screen.Player) {
        viewModel.back()
    }
    LaunchedEffect(viewModel.message) {
        val message = viewModel.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        if (viewModel.message == message) viewModel.clearMessage()
    }

    Box(Modifier.fillMaxSize().background(Ink)) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
            label = "co_stream_navigation",
            modifier = Modifier.fillMaxSize()
        ) { current ->
            when (current) {
                Screen.Login -> LoginScreen(
                    googleConfigured = viewModel.googleConfigured,
                    busy = viewModel.authBusy,
                    error = viewModel.authError,
                    onGoogle = viewModel::signInGoogle,
                    onGuest = viewModel::continueAsGuest
                )
                Screen.Platforms -> PlatformSelectionScreen(viewModel.selectedPlatforms, viewModel::savePlatforms)
                Screen.Home -> HomeScreen(
                    state = viewModel.homeState,
                    myList = viewModel.myList,
                    continueWatching = viewModel.continueWatching,
                    onSearch = viewModel::openSearch,
                    onMyList = viewModel::openMyList,
                    onProfile = { profileOpen = true },
                    onPlay = viewModel::playItem,
                    onDetails = viewModel::openDetail,
                    onToggleSaved = viewModel::toggleMyList,
                    onResume = viewModel::resume,
                    onRefresh = viewModel::refreshHome,
                    photoUrl = viewModel.authUser?.photoUrl,
                    userName = viewModel.authUser?.name
                )
                Screen.Search -> SearchScreen(
                    query = viewModel.searchQuery,
                    state = viewModel.searchState,
                    savedIds = viewModel.myList.map { it.id }.toSet(),
                    onBack = viewModel::back,
                    onQueryChange = viewModel::onSearchQueryChanged,
                    onRetry = { viewModel.search(viewModel.searchQuery) },
                    onOpenDetails = viewModel::openDetail
                )
                Screen.MyList -> MyListScreen(
                    items = viewModel.myList,
                    onBack = viewModel::back,
                    onOpenDetails = viewModel::openDetail,
                    onPlay = viewModel::playItem,
                    onRemove = viewModel::removeFromMyList,
                    onExplore = viewModel::goHome
                )
                is Screen.Detail -> DetailScreen(
                    original = current.item,
                    loaded = viewModel.detailItem?.takeIf { it.id == current.item.id },
                    loading = viewModel.detailLoading,
                    error = viewModel.detailError,
                    saved = viewModel.isSaved(current.item.id),
                    playPreparing = viewModel.playPreparing,
                    progress = viewModel.progressFor(current.item.id),
                    onBack = viewModel::back,
                    onPlay = viewModel::playItem,
                    onPlayEpisode = viewModel::playEpisode,
                    onSelectSeason = viewModel::selectSeason,
                    onToggleSaved = viewModel::toggleMyList,
                    onRetry = viewModel::retryDetail
                )
                is Screen.Player -> PlayerScreen(
                    request = current.request,
                    isPipMode = pipMode,
                    saved = viewModel.isSaved(current.request.item.id),
                    onBack = viewModel::back,
                    onSaveProgress = viewModel::saveProgress,
                    onPlayerClosed = viewModel::onPlayerClosed,
                    onToggleSaved = viewModel::toggleMyList
                )
            }
        }

        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(18.dp))
    }

    if (profileOpen) {
        ProfileSheet(
            name = viewModel.authUser?.name ?: "Guest",
            email = viewModel.authUser?.email,
            savedCount = viewModel.myList.size,
            onDismiss = { profileOpen = false },
            onPlatforms = { profileOpen = false; viewModel.editPlatforms() },
            onLogout = { profileOpen = false; viewModel.logout() }
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ProfileSheet(
    name: String,
    email: String?,
    savedCount: Int,
    onDismiss: () -> Unit,
    onPlatforms: () -> Unit,
    onLogout: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Surface,
        contentColor = TextPrimary,
        scrimColor = Color.Black.copy(alpha = .65f)
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileAvatar(size = 54.dp); Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    CoStreamWordmark(markSize = 22.dp, textSize = 10)
                    Text(name, color = TextPrimary, fontWeight = FontWeight.Bold)
                    email?.let { Text(it, color = TextMuted, fontSize = 10.sp) }
                }
                Text("$savedCount saved", color = Teal, fontSize = 10.sp)
            }
            Spacer(Modifier.size(20.dp))
            ProfileAction(Icons.Rounded.GridView, "Streaming platforms", "Choose what appears on Home", onPlatforms)
            ProfileAction(Icons.Rounded.Logout, "Sign out", "Return to the login screen", onLogout)
        }
    }
}

@Composable
private fun ProfileAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).background(Teal.copy(alpha = .11f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Teal) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text(title, color = TextPrimary, style = MaterialTheme.typography.titleSmall); Text(subtitle, color = TextSecondary, fontSize = 10.5.sp) }
        Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, null, tint = TextMuted, modifier = Modifier.size(13.dp))
    }
}
