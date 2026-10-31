package app.costream.ui

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.costream.CoStreamApp
import app.costream.data.AudioVariant
import app.costream.data.AuthUser
import app.costream.data.HomeFeed
import app.costream.data.MediaItem
import app.costream.data.NetMirrorRepository
import app.costream.data.PlayRequest
import app.costream.data.StreamSource
import app.costream.data.WatchEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Login : Screen
    data object Platforms : Screen
    data object Home : Screen
    data object Search : Screen
    data object MyList : Screen
    data class Detail(val item: MediaItem) : Screen
    data class Player(val request: PlayRequest) : Screen
}

sealed interface HomeUiState {
    data object Idle : HomeUiState
    data object Loading : HomeUiState
    data class Ready(val feed: HomeFeed) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Ready(val query: String, val items: List<MediaItem>) : SearchUiState
    data class Error(val query: String, val message: String) : SearchUiState
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as CoStreamApp
    private val store = app.store
    private val repository = app.netMirrorRepository
    private val authRepository = app.authRepository

    var authUser by mutableStateOf(store.authUser())
        private set
    var authBusy by mutableStateOf(false)
        private set
    var authError by mutableStateOf<String?>(null)
        private set
    val googleConfigured: Boolean get() = authRepository.googleAvailable

    var selectedPlatforms by mutableStateOf(store.platforms().ifEmpty { NetMirrorRepository.DEFAULT_PLATFORMS })
        private set

    val screens = mutableStateListOf<Screen>().apply {
        add(
            when {
                authUser == null -> Screen.Login
                store.platforms().isEmpty() -> Screen.Platforms
                else -> Screen.Home
            }
        )
    }

    var homeState by mutableStateOf<HomeUiState>(HomeUiState.Ready(repository.instantHomeFeed(selectedPlatforms)))
        private set
    var searchState by mutableStateOf<SearchUiState>(SearchUiState.Idle)
        private set
    var searchQuery by mutableStateOf("")
        private set
    var detailItem by mutableStateOf<MediaItem?>(null)
        private set
    var detailLoading by mutableStateOf(false)
        private set
    var detailError by mutableStateOf<String?>(null)
        private set
    var playPreparing by mutableStateOf(false)
        private set

    var myList by mutableStateOf(store.myList())
        private set
    var continueWatching by mutableStateOf(store.continueWatching())
        private set
    var message by mutableStateOf<String?>(null)
        private set

    private var homeJob: Job? = null
    private var searchJob: Job? = null
    private var detailJob: Job? = null

    val currentScreen: Screen get() = screens.lastOrNull() ?: Screen.Login

    init {
        if (currentScreen == Screen.Home) loadHome()
    }

    fun continueAsGuest(name: String) {
        val user = AuthUser(
            id = "guest-${System.currentTimeMillis()}",
            name = name.trim().ifBlank { "Guest" },
            guest = true
        )
        finishLogin(user)
    }

    fun signInGoogle(activityContext: Context) {
        if (authBusy) return
        authBusy = true
        authError = null
        viewModelScope.launch {
            runCatching { authRepository.signInWithGoogle(activityContext) }
                .onSuccess(::finishLogin)
                .onFailure { authError = it.message ?: "Google sign-in failed." }
            authBusy = false
        }
    }

    private fun finishLogin(user: AuthUser) {
        authUser = user
        store.setAuthUser(user)
        screens.clear()
        screens.add(if (store.platforms().isEmpty()) Screen.Platforms else Screen.Home)
        if (currentScreen == Screen.Home) loadHome()
    }

    fun logout() {
        viewModelScope.launch { authRepository.signOut() }
        authUser = null
        store.setAuthUser(null)
        screens.clear()
        screens.add(Screen.Login)
        homeState = HomeUiState.Idle
        searchState = SearchUiState.Idle
    }

    fun savePlatforms(platforms: List<String>) {
        val valid = platforms.filter { it in NetMirrorRepository.PLATFORMS }.distinct()
        if (valid.isEmpty()) return
        selectedPlatforms = valid
        store.setPlatforms(valid)
        screens.clear()
        screens.add(Screen.Home)
        loadHome(force = true)
    }

    fun editPlatforms() {
        push(Screen.Platforms)
    }

    fun loadHome(force: Boolean = false) {
        homeJob?.cancel()
        if (homeState !is HomeUiState.Ready) {
            homeState = HomeUiState.Ready(repository.instantHomeFeed(selectedPlatforms))
        }
        homeJob = viewModelScope.launch {
            runCatching { repository.homeFeed(selectedPlatforms, force) }
                .onSuccess { homeState = HomeUiState.Ready(it) }
                .onFailure {
                    if (homeState !is HomeUiState.Ready) {
                        homeState = HomeUiState.Error(it.message ?: "Couldn't load the catalog.")
                    }
                }
        }
    }

    fun refreshHome() = loadHome(force = true)

    fun openSearch() {
        if (currentScreen !is Screen.Search) push(Screen.Search)
    }

    fun openMyList() {
        if (currentScreen !is Screen.MyList) push(Screen.MyList)
    }

    fun openDetail(item: MediaItem) {
        push(Screen.Detail(item))
        loadDetail(item)
    }

    fun loadDetail(item: MediaItem, season: Int = item.selectedSeason, episode: Int = item.selectedEpisode) {
        detailItem = item
        detailError = null
        detailLoading = true
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            runCatching { repository.details(item, season, episode) }
                .onSuccess { detailItem = it }
                .onFailure { detailError = it.message ?: "Couldn't load this title." }
            detailLoading = false
        }
    }

    fun selectSeason(season: Int) {
        val current = detailItem ?: return
        val tmdbId = current.tmdbId ?: return
        detailLoading = true
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            runCatching { repository.seasonEpisodes(tmdbId, season) }
                .onSuccess { detailItem = current.copy(selectedSeason = season, selectedEpisode = 1, episodes = it) }
                .onFailure { detailError = it.message ?: "Couldn't load this season." }
            detailLoading = false
        }
    }

    fun retryDetail() {
        detailItem?.let { loadDetail(it) }
    }

    fun playItem(item: MediaItem, source: StreamSource? = null) {
        if (playPreparing) return
        val start = store.progressFor(item.id)?.positionMs ?: 0L
        val ready = source ?: item.sources.firstOrNull()
        if (ready != null) {
            push(Screen.Player(PlayRequest(item, ready, start)))
            return
        }
        playPreparing = true
        viewModelScope.launch {
            runCatching { repository.details(item) }
                .onSuccess { full ->
                    detailItem = full
                    full.sources.firstOrNull()?.let { push(Screen.Player(PlayRequest(full, it, start))) }
                        ?: showMessage("No playable source is available.")
                }
                .onFailure { showMessage(it.message ?: "Couldn't prepare this stream.") }
            playPreparing = false
        }
    }

    /** Play the next episode of the currently playing series request. */
    fun playNextEpisode(current: PlayRequest) {
        val item = current.item
        if (!item.isSeries) return
        val nextEp = item.selectedEpisode + 1
        val season = item.selectedSeason
        val episodeCount = item.seasons.firstOrNull { it.number == season }?.episodeCount
            ?: item.episodes.size
        if (episodeCount > 0 && nextEp > episodeCount) {
            val nextSeason = season + 1
            if (item.seasons.any { it.number == nextSeason }) {
                playEpisode(item, nextSeason, 1)
            } else {
                showMessage("This was the last episode.")
            }
        } else {
            playEpisode(item, season, nextEp)
        }
    }

    fun playEpisode(item: MediaItem, season: Int, episode: Int, audio: AudioVariant? = null) {
        if (playPreparing) return
        playPreparing = true
        viewModelScope.launch {
            runCatching { repository.details(item, season, episode, audio) }
                .onSuccess { full ->
                    detailItem = full
                    val source = full.sources.firstOrNull()
                    if (source == null) showMessage("No playable source is available for this episode.")
                    else push(Screen.Player(PlayRequest(full, source, 0L, "S$season · E$episode")))
                }
                .onFailure { showMessage(it.message ?: "Couldn't prepare this episode.") }
            playPreparing = false
        }
    }

    fun resume(entry: WatchEntry) {
        val source = entry.item.sources.firstOrNull { it.url == entry.sourceUrl }
            ?: StreamSource(entry.sourceUrl, label = "Resume")
        push(Screen.Player(PlayRequest(entry.item, source, entry.positionMs, "Continue watching")))
    }

    fun onSearchQueryChanged(rawQuery: String) {
        searchQuery = rawQuery
        searchJob?.cancel()
        val query = rawQuery.trim()
        if (query.isBlank()) {
            searchState = SearchUiState.Idle
            return
        }
        searchJob = viewModelScope.launch {
            delay(320)
            searchNow(query)
        }
    }

    fun search(rawQuery: String) {
        searchQuery = rawQuery
        searchJob?.cancel()
        val query = rawQuery.trim()
        if (query.isBlank()) {
            searchState = SearchUiState.Idle
            return
        }
        searchJob = viewModelScope.launch { searchNow(query) }
    }

    private suspend fun searchNow(query: String) {
        searchState = SearchUiState.Loading
        runCatching { repository.search(query) }
            .onSuccess { searchState = SearchUiState.Ready(query, it) }
            .onFailure { searchState = SearchUiState.Error(query, it.message ?: "Search is unavailable.") }
    }

    fun clearSearch() {
        searchJob?.cancel()
        searchQuery = ""
        searchState = SearchUiState.Idle
    }

    fun toggleMyList(item: MediaItem) {
        val saved = store.toggleMyList(item)
        myList = store.myList()
        showMessage(if (saved) "Added to My List" else "Removed from My List")
    }

    fun removeFromMyList(item: MediaItem) {
        store.removeFromMyList(item.id)
        myList = store.myList()
    }

    fun downloadItem(item: MediaItem, source: StreamSource) {
        val context = getApplication<Application>().applicationContext
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val fileName = "${item.title}-${System.currentTimeMillis()}.mp4"
        val request = DownloadManager.Request(Uri.parse(source.url)).apply {
            setTitle(item.title)
            setDescription("Downloading ${item.title}")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, "CoStream/$fileName")
            setMimeType(source.mimeType ?: "video/mp4")
        }
        dm.enqueue(request)
        store.addLocalVideos(listOf(item.copy()))
        showMessage("Download started for ${item.title}")
    }

    fun isSaved(itemId: String): Boolean = myList.any { it.id == itemId }
    fun progressFor(itemId: String): WatchEntry? = store.progressFor(itemId)

    fun saveProgress(item: MediaItem, sourceUrl: String, positionMs: Long, durationMs: Long) {
        store.recordWatch(item, sourceUrl, positionMs, durationMs)
    }

    fun onPlayerClosed() {
        continueWatching = store.continueWatching()
        myList = store.myList()
    }

    fun clearHistory() {
        store.clearHistory()
        continueWatching = emptyList()
    }

    fun isPlayerOnTop(): Boolean = currentScreen is Screen.Player

    fun returnHomeIfIdle() {
        if (authUser != null && currentScreen !is Screen.Player) goHome()
    }

    fun back() {
        if (currentScreen is Screen.Player) {
            // Directly exit player back to whatever screen was beneath it (e.g. Detail or Home),
            // even if multiple consecutive episodes/videos were watched.
            while (screens.size > 1 && screens.last() is Screen.Player) {
                screens.removeAt(screens.lastIndex)
            }
        } else if (screens.size > 1) {
            screens.removeAt(screens.lastIndex)
        }
    }

    fun goHome() {
        screens.clear()
        screens.add(Screen.Home)
        if (homeState is HomeUiState.Idle) loadHome()
    }

    fun showMessage(value: String) {
        message = value
    }

    fun clearMessage() {
        message = null
    }

    private fun push(screen: Screen) {
        // When already playing a video and opening another video / next episode,
        // replace the current player screen instead of stacking them up.
        if (screen is Screen.Player && currentScreen is Screen.Player) {
            screens[screens.lastIndex] = screen
        } else {
            screens.add(screen)
        }
    }
}
