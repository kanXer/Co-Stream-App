package app.costream.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.util.Rational
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import app.costream.data.MediaItem as CoMediaItem
import app.costream.data.PlayRequest
import app.costream.ui.components.GhostButton
import app.costream.ui.components.GlassIconButton
import app.costream.ui.components.Pill
import app.costream.ui.components.PrimaryButton
import app.costream.ui.theme.Blue
import app.costream.ui.theme.Ink
import app.costream.ui.theme.Pink
import app.costream.ui.theme.Red
import app.costream.ui.theme.Surface
import app.costream.ui.theme.SurfaceHigh
import app.costream.ui.theme.Teal
import app.costream.ui.theme.TextMuted
import app.costream.ui.theme.TextPrimary
import app.costream.ui.theme.TextSecondary
import app.costream.ui.theme.Violet
import app.costream.util.formatClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    request: PlayRequest,
    isPipMode: Boolean,
    saved: Boolean,
    onBack: () -> Unit,
    onSaveProgress: (CoMediaItem, String, Long, Long) -> Unit,
    onPlayerClosed: () -> Unit,
    onToggleSaved: (CoMediaItem) -> Unit,
    onNextEpisode: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val initialWindowBrightness = remember {
        activity?.window?.attributes?.screenBrightness ?: -1f
    }
    val systemBrightness = remember {
        Settings.System.getInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS,
            128
        ) / 255f
    }
    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val pipSupported = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    var playbackState by remember { mutableIntStateOf(Player.STATE_IDLE) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(request.startPositionMs.coerceAtLeast(0L)) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var bufferedMs by remember { mutableLongStateOf(0L) }
    var trackState by remember { mutableStateOf<Tracks?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }

    var controlsVisible by remember { mutableStateOf(true) }
    var locked by remember { mutableStateOf(false) }
    var showUnlock by remember { mutableStateOf(false) }
    var sheetOpen by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var qualityAuto by remember { mutableStateOf(true) }
    var audioAuto by remember { mutableStateOf(true) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    var sliderSeekMs by remember { mutableStateOf<Long?>(null) }
    var gestureSeekMs by remember { mutableStateOf<Long?>(null) }
    var hudVolume by remember { mutableStateOf<Float?>(null) }
    var hudBrightness by remember { mutableStateOf<Float?>(null) }
    var brightnessLevel by remember {
        mutableFloatStateOf(
            if (initialWindowBrightness >= 0f) initialWindowBrightness else systemBrightness
        )
    }
    var flashSide by remember { mutableIntStateOf(0) }
    var flashToken by remember { mutableLongStateOf(0L) }
    var landscape by remember { mutableStateOf(true) }
    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }

    val exoPlayer = remember {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(app.costream.data.NetMirrorRepository.USER_AGENT)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(35_000)
            .apply {
                if (request.source.headers.isNotEmpty()) {
                    setDefaultRequestProperties(request.source.headers)
                }
            }
        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(20_000, 90_000, 1_500, 3_000)
            .build()
        ExoPlayer.Builder(context, DefaultMediaSourceFactory(dataSourceFactory))
            .setLoadControl(loadControl)
            .build()
            .apply {
                setAudioAttributes(
                    androidx.media3.common.AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true
                )
                val mediaItem = MediaItem.Builder()
                    .setUri(request.source.url)
                    .apply {
                        request.source.mimeType?.takeIf { it.isNotBlank() }?.let { setMimeType(it) }
                        if (request.source.subtitles.isNotEmpty()) {
                            setSubtitleConfigurations(
                                request.source.subtitles.map { track ->
                                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(track.url))
                                        .setMimeType(
                                            when {
                                                track.mimeType.contains("vtt", true) -> MimeTypes.TEXT_VTT
                                                else -> MimeTypes.APPLICATION_SUBRIP
                                            }
                                        )
                                        .setLanguage(track.language)
                                        .setLabel(track.label)
                                        .build()
                                }
                            )
                        }
                    }
                    .build()
                setMediaItem(mediaItem, request.startPositionMs.coerceAtLeast(0L))
                prepare()
                playWhenReady = true
            }
    }

    val livePlayback = exoPlayer.isCurrentMediaItemLive ||
        (request.item.isLive && playbackState != Player.STATE_READY)

    val latestSaveProgress by rememberUpdatedState(onSaveProgress)
    val latestPlayerClosed by rememberUpdatedState(onPlayerClosed)
    val latestPipMode by rememberUpdatedState(isPipMode)

    fun saveNow() {
        val player = exoPlayer
        if (player.isCurrentMediaItemLive) return
        val duration = player.duration
        if (duration != C.TIME_UNSET && duration > 0L) {
            val position = player.currentPosition.coerceIn(0L, duration)
            latestSaveProgress(request.item, request.source.url, position, duration)
        }
    }

    fun closePlayer() {
        saveNow()
        onBack()
    }

    fun togglePlayback() {
        if (exoPlayer.playbackState == Player.STATE_ENDED) exoPlayer.seekTo(0L)
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
        controlsVisible = true
    }

    fun setWindowBrightness(value: Float) {
        val window = activity?.window ?: return
        val attributes = window.attributes
        attributes.screenBrightness = value.coerceIn(0.04f, 1f)
        window.attributes = attributes
    }

    fun enterPip() {
        val act = activity ?: return
        if (!pipSupported) return
        val ratio = if (videoWidth > 0 && videoHeight > 0) {
            Rational(videoWidth, videoHeight)
        } else {
            Rational(16, 9)
        }
        runCatching {
            act.enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(ratio)
                    .build()
            )
        }
        controlsVisible = false
    }

    fun toggleOrientation() {
        val act = activity ?: return
        landscape = !landscape
        runCatching {
            act.requestedOrientation = if (landscape) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }

    BackHandler { closePlayer() }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                if (state == Player.STATE_READY) {
                    errorText = null
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                errorText = friendlyPlayerError(error)
                controlsVisible = true
                sheetOpen = false
            }

            override fun onTracksChanged(tracks: Tracks) {
                trackState = tracks
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                videoWidth = videoSize.width
                videoHeight = videoSize.height
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            val duration = exoPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0L }
            if (!exoPlayer.isCurrentMediaItemLive && duration != null) {
                latestSaveProgress(
                    request.item,
                    request.source.url,
                    exoPlayer.currentPosition.coerceIn(0L, duration),
                    duration
                )
            }
            exoPlayer.release()
            latestPlayerClosed()
        }
    }

    DisposableEffect(activity) {
        val act = activity ?: return@DisposableEffect onDispose { }
        val window = act.window
        // Enable layout behind the display cutout (notch) on all edges so the
        // video surface fills edge-to-edge including any camera cutout area.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val params = window.attributes
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = params
        }
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        // Ensure the Compose layout tree is allowed to draw into the inset areas.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val previousOrientation = act.requestedOrientation
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        runCatching {
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            // Restore cutout mode to the default (never, which is 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val params = window.attributes
                params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                window.attributes = params
            }
            controller.show(WindowInsetsCompat.Type.systemBars())
            runCatching { act.requestedOrientation = previousOrientation }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val window = activity?.window ?: return@onDispose
            val attributes = window.attributes
            attributes.screenBrightness = initialWindowBrightness
            window.attributes = attributes
        }
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> saveNow()
                Lifecycle.Event.ON_STOP -> {
                    saveNow()
                    if (!latestPipMode) exoPlayer.pause()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(exoPlayer) {
        while (isActive) {
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            bufferedMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)
            val duration = exoPlayer.duration
            if (duration != C.TIME_UNSET && duration > 0L) durationMs = duration
            delay(450)
        }
    }

    LaunchedEffect(exoPlayer) {
        while (isActive) {
            delay(12_000)
            saveNow()
        }
    }

    LaunchedEffect(
        controlsVisible,
        isPlaying,
        locked,
        sheetOpen,
        errorText,
        isPipMode,
        sliderSeekMs,
        gestureSeekMs,
        hudVolume,
        hudBrightness
    ) {
        if (controlsVisible && isPlaying && !locked && !sheetOpen && errorText == null && !isPipMode) {
            delay(3_700)
            controlsVisible = false
        }
    }

    LaunchedEffect(isPipMode) {
        if (isPipMode) {
            controlsVisible = false
            sheetOpen = false
        } else {
            controlsVisible = true
        }
    }

    val shownPosition = sliderSeekMs ?: gestureSeekMs ?: positionMs

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Stable accumulated pinch scale (survives recomposition)
        val pinchScaleRef = remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
        var pinchHudLabel by remember { mutableStateOf<String?>(null) }

        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                    setBackgroundColor(android.graphics.Color.BLACK)
                    subtitleView?.setStyle(
                        CaptionStyleCompat(
                            android.graphics.Color.WHITE,
                            android.graphics.Color.argb(170, 0, 0, 0),
                            android.graphics.Color.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_NONE,
                            android.graphics.Color.WHITE,
                            null
                        )
                    )
                    subtitleView?.setFractionalTextSize(0.055f)
                    player = exoPlayer
                }
            },
            update = { view ->
                view.player = exoPlayer
                view.resizeMode = resizeMode
            },
            modifier = Modifier.fillMaxSize()
        )

        if (!isPipMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        // ── Pinch-to-zoom: changes ExoPlayer resizeMode in real time ──
                        detectTransformGestures { _, _, zoomDelta, _ ->
                            if (zoomDelta == 1f) return@detectTransformGestures
                            val next = (pinchScaleRef.floatValue * zoomDelta).coerceIn(0.5f, 3f)
                            pinchScaleRef.floatValue = next
                            val newMode = when {
                                next > 1.2f -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                next < 0.85f -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                else -> resizeMode
                            }
                            if (newMode != resizeMode) {
                                resizeMode = newMode
                                pinchHudLabel = if (newMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) "Fill" else "Fit"
                                scope.launch { delay(1200); pinchHudLabel = null }
                            }
                        }
                    }
                    .pointerInput(locked) {
                        if (locked) {
                            detectTapGestures {
                                showUnlock = !showUnlock
                            }
                            return@pointerInput
                        }

                        var lastTapTime = 0L
                        var lastTapPoint = Offset.Zero

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val downX = down.position.x
                            val downY = down.position.y
                            val width = size.width.toFloat()
                            val height = size.height.toFloat()
                            var dragging = false
                            var mode = 0
                            var targetSeek = exoPlayer.currentPosition
                            val startPosition = targetSeek
                            val startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            val maxVolume = audioManager
                                .getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                .coerceAtLeast(1)
                            var targetBrightness = brightnessLevel
                            var targetVolume = startVolume
                            var released = false

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    released = true
                                    break
                                }
                                val dx = change.position.x - downX
                                val dy = change.position.y - downY
                                if (!dragging && abs(dy) > viewConfiguration.touchSlop && abs(dy) > abs(dx)) {
                                    dragging = true
                                    mode = if (downX < width / 2f) 2 else 3
                                }
                                if (!dragging) continue
                                change.consume()
                                when (mode) {
                                    2 -> {
                                        targetBrightness =
                                            (brightnessLevel - dy / height * 1.15f).coerceIn(0.04f, 1f)
                                        brightnessLevel = targetBrightness
                                        setWindowBrightness(targetBrightness)
                                        hudBrightness = targetBrightness
                                        hudVolume = null
                                    }
                                    3 -> {
                                        targetVolume = (startVolume - dy / height * maxVolume * 1.15f)
                                            .roundToInt()
                                            .coerceIn(0, maxVolume)
                                        audioManager.setStreamVolume(
                                            AudioManager.STREAM_MUSIC,
                                            targetVolume,
                                            0
                                        )
                                        if (muted) {
                                            muted = false
                                            exoPlayer.volume = 1f
                                        }
                                        hudVolume = targetVolume.toFloat() / maxVolume
                                        hudBrightness = null
                                    }
                                }
                            }

                            if (dragging) {
                                when (mode) {
                                    2 -> {
                                        brightnessLevel = targetBrightness
                                        scope.launch {
                                            delay(850)
                                            hudBrightness = null
                                        }
                                    }
                                    3 -> {
                                        scope.launch {
                                            delay(850)
                                            hudVolume = null
                                        }
                                    }
                                }
                            } else if (released) {
                                val now = System.currentTimeMillis()
                                val isDoubleTap = now - lastTapTime < 300L &&
                                    (down.position - lastTapPoint).getDistance() < 90.dp.toPx()

                                if (isDoubleTap) {
                                    lastTapTime = 0L
                                    val max = exoPlayer.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                                    val liveNow = exoPlayer.isCurrentMediaItemLive ||
                                        (request.item.isLive && exoPlayer.playbackState != Player.STATE_READY)
                                    when {
                                        liveNow -> togglePlayback()
                                        downX < width * 0.40f -> {
                                            exoPlayer.seekTo((exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L))
                                            flashSide = -1
                                            val token = System.currentTimeMillis()
                                            flashToken = token
                                            scope.launch {
                                                delay(600)
                                                if (flashToken == token) flashSide = 0
                                            }
                                        }
                                        downX > width * 0.60f -> {
                                            exoPlayer.seekTo((exoPlayer.currentPosition + 10_000L).coerceAtMost(max))
                                            flashSide = 1
                                            val token = System.currentTimeMillis()
                                            flashToken = token
                                            scope.launch {
                                                delay(600)
                                                if (flashToken == token) flashSide = 0
                                            }
                                        }
                                        else -> togglePlayback()
                                    }
                                } else {
                                    lastTapTime = now
                                    lastTapPoint = down.position
                                    scope.launch {
                                        delay(290)
                                        if (lastTapTime == now) {
                                            controlsVisible = !controlsVisible
                                            if (controlsVisible) showUnlock = false
                                        }
                                    }
                                }
                            }
                        }
                    }
            )

            if (errorText != null) {
                PlaybackErrorPanel(
                    message = errorText.orEmpty(),
                    onRetry = {
                        errorText = null
                        exoPlayer.prepare()
                        exoPlayer.play()
                    },
                    onBack = { closePlayer() }
                )
            }

            if (playbackState == Player.STATE_BUFFERING && errorText == null) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(46.dp),
                    color = Teal,
                    strokeWidth = 3.dp
                )
            }

            if (controlsVisible && !locked && !isPipMode && errorText == null) {
                // ── Top bar (back + title on left; pip + bookmark on right) ──────
                PlayerTopBar(
                    title = request.item.title,
                    subtitle = buildString {
                        if (request.contextLabel.isNotBlank()) {
                            append(request.contextLabel).append(" · ")
                        }
                        append(request.item.provider)
                        append(" · ")
                        append(request.source.label)
                    },
                    live = livePlayback,
                    saved = saved,
                    pipSupported = pipSupported,
                    isSeries = request.item.isSeries,
                    onBack = { closePlayer() },
                    onToggleSaved = { onToggleSaved(request.item) },
                    onPip = { enterPip() },
                    onNextEpisode = onNextEpisode,
                    speed = speed,
                    onSpeedClick = { sheetOpen = true },
                    onSettings = { sheetOpen = true },
                    onRotate = { toggleOrientation() }
                )

                // ── Center playback controls (seek ±10 + play/pause) ─────────────
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .displayCutoutPadding(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    GlassIconButton(
                        icon = Icons.Rounded.Replay10,
                        contentDescription = "Seek back 10s",
                        size = 52.dp,
                        tint = TextPrimary,
                        onClick = {
                            val target = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                            exoPlayer.seekTo(target)
                            flashSide = -1
                            val token = System.currentTimeMillis()
                            flashToken = token
                            scope.launch { delay(600); if (flashToken == token) flashSide = 0 }
                        }
                    )
                    GlassIconButton(
                        icon = when {
                            playbackState == Player.STATE_ENDED -> Icons.Rounded.Replay
                            isPlaying -> Icons.Rounded.Pause
                            else -> Icons.Rounded.PlayArrow
                        },
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        size = 64.dp,
                        tint = TextPrimary,
                        onClick = { togglePlayback() }
                    )
                    GlassIconButton(
                        icon = Icons.Rounded.Forward10,
                        contentDescription = "Seek forward 10s",
                        size = 52.dp,
                        tint = TextPrimary,
                        onClick = {
                            val max = exoPlayer.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                            exoPlayer.seekTo((exoPlayer.currentPosition + 10_000L).coerceAtMost(max))
                            flashSide = 1
                            val token = System.currentTimeMillis()
                            flashToken = token
                            scope.launch { delay(600); if (flashToken == token) flashSide = 0 }
                        }
                    )
                }

                // ── Lock button (CenterStart) ────────────────────────────────────
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .displayCutoutPadding()
                        .padding(start = 16.dp)
                ) {
                    GlassIconButton(
                        icon = Icons.Rounded.Lock,
                        contentDescription = "Lock controls",
                        size = 38.dp,
                        tint = TextPrimary.copy(alpha = 0.82f),
                        onClick = {
                            locked = true
                            controlsVisible = false
                            showUnlock = false
                        }
                    )
                }
            }

            if (locked && showUnlock && !isPipMode && errorText == null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .displayCutoutPadding()
                        .padding(start = 24.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.62f))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
                        .clickable {
                            locked = false
                            controlsVisible = true
                            showUnlock = false
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = Teal,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Controls locked · tap to unlock",
                        color = TextPrimary,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.5.sp)
                    )
                }
            }

            // ── Bottom panel: seekbar + timestamps + footer ──────────────────────────
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                AnimatedVisibility(
                    visible = controlsVisible && !locked && !isPipMode && errorText == null,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(180))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                                )
                            )
                            .navigationBarsPadding()
                            .displayCutoutPadding()
                            .padding(horizontal = 18.dp)
                            .padding(top = 28.dp, bottom = 12.dp)
                    ) {
                        if (!livePlayback) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formatClock(shownPosition),
                                    color = if (sliderSeekMs != null || gestureSeekMs != null) Teal else TextPrimary,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = if (durationMs > 0L) formatClock(durationMs) else "—:—",
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.5.sp)
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            CinemaScrubber(
                                positionMs = shownPosition,
                                durationMs = durationMs,
                                bufferedMs = bufferedMs,
                                enabled = durationMs > 0L,
                                onPreview = {
                                    sliderSeekMs = it
                                    controlsVisible = true
                                },
                                onCommit = {
                                    exoPlayer.seekTo(it)
                                    sliderSeekMs = null
                                    controlsVisible = true
                                }
                            )
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(Red)
                                )
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    text = "LIVE",
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.2.sp,
                                        fontSize = 11.sp
                                    )
                                )
                                Spacer(Modifier.width(9.dp))
                                Text(
                                    text = request.item.provider,
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (exoPlayer.isCurrentMediaItemSeekable) {
                                    Text(
                                        text = "GO LIVE",
                                        color = Teal,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            letterSpacing = 0.9.sp,
                                            fontSize = 9.sp
                                        ),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(Teal.copy(alpha = 0.10f))
                                            .border(1.dp, Teal.copy(alpha = 0.22f), RoundedCornerShape(50))
                                            .clickable { exoPlayer.seekToDefaultPosition() }
                                            .padding(horizontal = 11.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = request.item.title,
                                color = TextMuted,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 8.5.sp,
                                    letterSpacing = 0.4.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "CO-STREAM PLAYER",
                                color = TextMuted.copy(alpha = 0.65f),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 8.sp,
                                    letterSpacing = 1.2.sp
                                )
                            )
                        }
                    }
                }
            } // BottomCenter Box

            flashSide.takeIf { it != 0 }?.let { side ->
                Row(
                    modifier = Modifier
                        .align(if (side < 0) Alignment.CenterStart else Alignment.CenterEnd)
                        .padding(horizontal = 54.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (side < 0) {
                        Icon(
                            Icons.Rounded.Replay10,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            "10 sec",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp)
                        )
                    } else {
                        Text(
                            "10 sec",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp)
                        )
                        Spacer(Modifier.width(7.dp))
                        Icon(
                            Icons.Rounded.Forward10,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            gestureSeekMs?.let { target ->
                SeekHud(
                    targetMs = target,
                    deltaMs = target - positionMs,
                    durationMs = durationMs,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            hudBrightness?.let { value ->
                GestureHud(
                    icon = Icons.Rounded.Brightness6,
                    label = "Brightness",
                    value = value,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(bottom = 120.dp)
                )
            }

            hudVolume?.let { value ->
                GestureHud(
                    icon = if (value <= 0.01f) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                    label = "Volume",
                    value = value,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(bottom = 120.dp)
                )
            }

            // ── Pinch-to-zoom HUD ─────────────────────────────────────────────
            pinchHudLabel?.let { label ->
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.70f))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (label == "Fill") Icons.Rounded.Fullscreen else Icons.Rounded.FullscreenExit,
                            contentDescription = null,
                            tint = Teal,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Zoom: $label",
                            color = TextPrimary,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }

        if (sheetOpen && !isPipMode) {
            PlayerSettingsSheet(
                speed = speed,
                tracks = trackState,
                qualityAuto = qualityAuto,
                audioAuto = audioAuto,
                resizeMode = resizeMode,
                onSpeed = {
                    speed = it
                    exoPlayer.setPlaybackSpeed(it)
                },
                onResizeMode = { resizeMode = it },
                onVideoAuto = {
                    qualityAuto = true
                    exoPlayer.trackSelectionParameters =
                        exoPlayer.trackSelectionParameters.buildUpon()
                            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                            .build()
                },
                onVideoTrack = { group, index ->
                    qualityAuto = false
                    exoPlayer.trackSelectionParameters =
                        exoPlayer.trackSelectionParameters.buildUpon()
                            .setOverrideForType(
                                TrackSelectionOverride(group.mediaTrackGroup, index)
                            )
                            .build()
                },
                onAudioAuto = {
                    audioAuto = true
                    exoPlayer.trackSelectionParameters =
                        exoPlayer.trackSelectionParameters.buildUpon()
                            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                            .build()
                },
                onAudioTrack = { group, index ->
                    audioAuto = false
                    exoPlayer.trackSelectionParameters =
                        exoPlayer.trackSelectionParameters.buildUpon()
                            .setOverrideForType(
                                TrackSelectionOverride(group.mediaTrackGroup, index)
                            )
                            .build()
                },
                onSubtitlesOff = {
                    exoPlayer.trackSelectionParameters =
                        exoPlayer.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                            .build()
                },
                onSubtitleTrack = { group, index ->
                    exoPlayer.trackSelectionParameters =
                        exoPlayer.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setOverrideForType(
                                TrackSelectionOverride(group.mediaTrackGroup, index)
                            )
                            .build()
                },
                onDismiss = { sheetOpen = false }
            )
        }
    }
}

@Composable
private fun PlayerTopBar(
    title: String,
    subtitle: String,
    live: Boolean,
    saved: Boolean,
    pipSupported: Boolean,
    isSeries: Boolean,
    speed: Float,
    onBack: () -> Unit,
    onToggleSaved: () -> Unit,
    onPip: () -> Unit,
    onNextEpisode: (() -> Unit)?,
    onSpeedClick: () -> Unit,
    onSettings: () -> Unit,
    onRotate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.88f),
                        Color.Black.copy(alpha = 0.50f),
                        Color.Transparent
                    )
                )
            )
            .statusBarsPadding()
            .displayCutoutPadding()
            .padding(horizontal = 14.dp)
            .padding(top = 8.dp, bottom = 24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // ── Left: Back + title ────────────────────────────────────────────
            GlassIconButton(
                icon = Icons.Rounded.ArrowBackIosNew,
                contentDescription = "Back",
                size = 40.dp,
                onClick = onBack
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (live) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Red)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "LIVE",
                            color = Red,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.5.sp,
                                letterSpacing = 0.8.sp
                            )
                        )
                        Spacer(Modifier.width(7.dp))
                    }
                    Text(
                        text = subtitle,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            // ── Right: action buttons ─────────────────────────────────────────
            // Speed badge
            SpeedBadge(speed, onClick = onSpeedClick)
            Spacer(Modifier.width(4.dp))
            // Next episode (series only)
            if (isSeries && onNextEpisode != null) {
                GlassIconButton(
                    icon = Icons.Rounded.SkipNext,
                    contentDescription = "Next episode",
                    size = 40.dp,
                    tint = Teal,
                    onClick = onNextEpisode
                )
                Spacer(Modifier.width(4.dp))
            }
            // Bookmark
            GlassIconButton(
                icon = if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                contentDescription = if (saved) "Remove from My List" else "Add to My List",
                size = 40.dp,
                tint = if (saved) Teal else TextPrimary,
                onClick = onToggleSaved
            )
            // PiP
            if (pipSupported) {
                Spacer(Modifier.width(4.dp))
                GlassIconButton(
                    icon = Icons.Rounded.PictureInPictureAlt,
                    contentDescription = "Picture in picture",
                    size = 40.dp,
                    onClick = onPip
                )
            }
            Spacer(Modifier.width(4.dp))
            // Settings
            PlayerIconButton(
                icon = Icons.Rounded.Tune,
                contentDescription = "Playback settings",
                onClick = onSettings
            )
            // Rotate
            PlayerIconButton(
                icon = Icons.Rounded.ScreenRotation,
                contentDescription = "Rotate",
                onClick = onRotate
            )
        }
    }
}

@Composable
private fun PlayerIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = TextPrimary.copy(alpha = 0.92f),
            modifier = Modifier.size(23.dp)
        )
    }
}

@Composable
private fun SpeedBadge(speed: Float, onClick: () -> Unit) {
    val label = if (speed == speed.roundToInt().toFloat()) {
        "${speed.roundToInt()}×"
    } else {
        "${speed}×"
    }
    Text(
        text = label,
        color = TextPrimary,
        style = MaterialTheme.typography.labelLarge.copy(
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        ),
        modifier = Modifier
            .widthIn(min = 42.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.09f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}

@Composable
private fun CinemaScrubber(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    enabled: Boolean,
    onPreview: (Long) -> Unit,
    onCommit: (Long) -> Unit
) {
    val progress = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    val buffered = if (durationMs > 0L) {
        (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .pointerInput(enabled, durationMs) {
                if (!enabled || durationMs <= 0L) return@pointerInput
                // Mirror the thumb-inset used by the Canvas so touch hits map exactly
                // to the drawn track (thumb radius = 5.dp on each side).
                val thumbPx = 5.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val trackW = (size.width.toFloat() - thumbPx * 2f).coerceAtLeast(1f)
                    var fraction = ((down.position.x - thumbPx) / trackW).coerceIn(0f, 1f)
                    onPreview((durationMs * fraction).toLong())
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        fraction = ((change.position.x - thumbPx) / trackW).coerceIn(0f, 1f)
                        onPreview((durationMs * fraction).toLong())
                        change.consume()
                        if (!change.pressed) break
                    }
                    onCommit((durationMs * fraction).toLong())
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val centerY = size.height / 2f
            val trackHeight = 3.dp.toPx()
            val corner = CornerRadius(trackHeight / 2f, trackHeight / 2f)
            val thumbRadius = 5.dp.toPx()
            // Clamp the drawable track width so the thumb never overflows the canvas.
            val trackWidth = (size.width - thumbRadius * 2f).coerceAtLeast(0f)
            val trackStart = thumbRadius

            drawRoundRect(
                color = Color.White.copy(alpha = 0.18f),
                topLeft = Offset(trackStart, centerY - trackHeight / 2f),
                size = Size(trackWidth, trackHeight),
                cornerRadius = corner
            )
            if (buffered > 0f) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.30f),
                    topLeft = Offset(trackStart, centerY - trackHeight / 2f),
                    size = Size(trackWidth * buffered, trackHeight),
                    cornerRadius = corner
                )
            }
            if (progress > 0f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        listOf(Teal, Blue),
                        startX = trackStart,
                        endX = trackStart + trackWidth
                    ),
                    topLeft = Offset(trackStart, centerY - trackHeight / 2f),
                    size = Size(trackWidth * progress, trackHeight),
                    cornerRadius = corner
                )
            }
            val thumbX = (trackStart + trackWidth * progress).coerceIn(trackStart, trackStart + trackWidth)
            drawCircle(
                color = Teal.copy(alpha = 0.18f),
                radius = 10.dp.toPx(),
                center = Offset(thumbX, centerY)
            )
            drawCircle(
                color = Color.White,
                radius = thumbRadius,
                center = Offset(thumbX, centerY)
            )
        }
    }
}

@Composable
private fun SeekHud(
    targetMs: Long,
    deltaMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    val sign = if (deltaMs >= 0L) "+" else "−"
    val delta = formatClock(abs(deltaMs))
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.66f))
            .border(1.dp, Color.White.copy(alpha = 0.13f), RoundedCornerShape(18.dp))
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "$sign $delta",
            color = Teal,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = formatClock(targetMs),
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold
            )
        )
        if (durationMs > 0L) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "of ${formatClock(durationMs)}",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp)
            )
        }
    }
}

@Composable
private fun GestureHud(
    icon: ImageVector,
    label: String,
    value: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(172.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.68f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
            .padding(horizontal = 15.dp, vertical = 13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Teal, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                color = TextPrimary,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.5.sp)
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${(value * 100).roundToInt()}%",
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.16f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(value.coerceIn(0f, 1f))
                    .height(4.dp)
                    .background(Brush.horizontalGradient(listOf(Teal, Blue)))
            )
        }
    }
}

@Composable
private fun PlaybackErrorPanel(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 430.dp)
                .padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Pink.copy(alpha = 0.13f))
                    .border(1.dp, Pink.copy(alpha = 0.24f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Replay,
                    contentDescription = null,
                    tint = Pink,
                    modifier = Modifier.size(29.dp)
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = "The stream dropped",
                color = TextPrimary,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(22.dp))
            PrimaryButton(
                text = "Try again",
                icon = Icons.Rounded.Replay,
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            GhostButton(
                text = "Back to browse",
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSettingsSheet(
    speed: Float,
    tracks: Tracks?,
    qualityAuto: Boolean,
    audioAuto: Boolean,
    resizeMode: Int,
    onSpeed: (Float) -> Unit,
    onResizeMode: (Int) -> Unit,
    onVideoAuto: () -> Unit,
    onVideoTrack: (Tracks.Group, Int) -> Unit,
    onAudioAuto: () -> Unit,
    onAudioTrack: (Tracks.Group, Int) -> Unit,
    onSubtitlesOff: () -> Unit,
    onSubtitleTrack: (Tracks.Group, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val videoGroups = tracks?.groups?.filter { it.type == C.TRACK_TYPE_VIDEO }.orEmpty()
    val audioGroups = tracks?.groups?.filter { it.type == C.TRACK_TYPE_AUDIO }.orEmpty()
    val textGroups = tracks?.groups?.filter { it.type == C.TRACK_TYPE_TEXT }.orEmpty()
    val subtitlesSelected = textGroups.any { group ->
        (0 until group.length).any { group.isTrackSelected(it) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Surface,
        contentColor = TextPrimary,
        scrimColor = Color.Black.copy(alpha = 0.58f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 34.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.22f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Playback settings",
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "Speed, picture and tracks for this stream",
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                GlassIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = "Close settings",
                    size = 34.dp,
                    onClick = onDismiss
                )
            }

            Spacer(Modifier.height(22.dp))
            SettingsLabel("PLAYBACK SPEED")
            Spacer(Modifier.height(9.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { option ->
                    SettingChip(
                        text = if (option == option.roundToInt().toFloat()) {
                            "${option.roundToInt()}×"
                        } else {
                            "${option}×"
                        },
                        selected = speed == option,
                        onClick = { onSpeed(option) }
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            SettingsLabel("PICTURE")
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingChip(
                    text = "Fit",
                    selected = resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT,
                    onClick = { onResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
                )
                SettingChip(
                    text = "Fill",
                    selected = resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                    onClick = { onResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM) }
                )
                SettingChip(
                    text = "Stretch",
                    selected = resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FILL,
                    onClick = { onResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL) }
                )
            }

            Spacer(Modifier.height(22.dp))
            SettingsLabel("QUALITY")
            Spacer(Modifier.height(9.dp))
            TrackOption(
                label = "Auto · adaptive",
                selected = qualityAuto,
                onClick = onVideoAuto
            )
            videoGroups.forEach { group ->
                for (index in 0 until group.length) {
                    if (!group.isTrackSupported(index)) continue
                    TrackOption(
                        label = videoTrackLabel(group, index),
                        selected = !qualityAuto && group.isTrackSelected(index),
                        onClick = { onVideoTrack(group, index) }
                    )
                }
            }
            if (videoGroups.isEmpty()) {
                SettingsHint("This stream exposes a single adaptive video track.")
            }

            Spacer(Modifier.height(22.dp))
            SettingsLabel("AUDIO")
            Spacer(Modifier.height(9.dp))
            TrackOption(
                label = "Auto · stream default",
                selected = audioAuto,
                onClick = onAudioAuto
            )
            audioGroups.forEach { group ->
                for (index in 0 until group.length) {
                    if (!group.isTrackSupported(index)) continue
                    TrackOption(
                        label = audioTrackLabel(group, index),
                        selected = !audioAuto && group.isTrackSelected(index),
                        onClick = { onAudioTrack(group, index) }
                    )
                }
            }
            if (audioGroups.isEmpty()) {
                SettingsHint("No alternate audio tracks in this stream.")
            }

            Spacer(Modifier.height(22.dp))
            SettingsLabel("SUBTITLES")
            Spacer(Modifier.height(9.dp))
            TrackOption(
                label = "Off",
                selected = !subtitlesSelected,
                onClick = onSubtitlesOff
            )
            textGroups.forEach { group ->
                for (index in 0 until group.length) {
                    if (!group.isTrackSupported(index)) continue
                    TrackOption(
                        label = textTrackLabel(group, index),
                        selected = group.isTrackSelected(index),
                        onClick = { onSubtitleTrack(group, index) }
                    )
                }
            }
            if (textGroups.isEmpty()) {
                SettingsHint("No subtitle tracks published for this stream.")
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = "Options appear when the stream publishes multiple tracks. HLS manifests can change mid-playback.",
                color = TextMuted.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp, lineHeight = 15.sp)
            )
        }
    }
}

@Composable
private fun SettingsLabel(text: String) {
    Text(
        text = text,
        color = TextMuted,
        style = MaterialTheme.typography.labelSmall.copy(
            letterSpacing = 1.4.sp,
            fontSize = 8.5.sp
        )
    )
}

@Composable
private fun SettingsHint(text: String) {
    Text(
        text = text,
        color = TextMuted,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
        modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp)
    )
}

@Composable
private fun SettingChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (selected) Ink else TextSecondary,
        style = MaterialTheme.typography.labelLarge.copy(
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Teal else Color.White.copy(alpha = 0.06f))
            .border(
                1.dp,
                if (selected) Color.Transparent else Color.White.copy(alpha = 0.09f),
                RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 9.dp)
    )
}

@Composable
private fun TrackOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Teal.copy(alpha = 0.09f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (selected) Teal else TextPrimary.copy(alpha = 0.88f),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = Teal,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

private fun videoTrackLabel(group: Tracks.Group, index: Int): String {
    val format = group.getTrackFormat(index)
    val height = format.height
    val quality = when {
        height >= 2160 -> "4K"
        height > 0 -> "${height}p"
        else -> format.label ?: "Track ${index + 1}"
    }
    val bitrate = format.bitrate
    return if (bitrate > 0) {
        "$quality · ${String.format(Locale.US, "%.1f", bitrate / 1_000_000.0)} Mbps"
    } else {
        quality
    }
}

private fun audioTrackLabel(group: Tracks.Group, index: Int): String {
    val format = group.getTrackFormat(index)
    val language = format.language?.takeIf { it.isNotBlank() }?.let { code ->
        runCatching { Locale.forLanguageTag(code).displayLanguage }.getOrNull()
            ?.takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) }
    }
    val label = format.label?.takeIf { it.isNotBlank() }
    return label ?: language ?: "Audio ${index + 1}"
}

private fun textTrackLabel(group: Tracks.Group, index: Int): String {
    val format = group.getTrackFormat(index)
    val language = format.language?.takeIf { it.isNotBlank() }?.let { code ->
        runCatching { Locale.forLanguageTag(code).displayLanguage }.getOrNull()
            ?.takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) }
    }
    val label = format.label?.takeIf { it.isNotBlank() }
    return label ?: language ?: "Subtitle ${index + 1}"
}

private fun friendlyPlayerError(error: PlaybackException): String {
    return when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            "Couldn't reach this stream. Check your connection and try again."

        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
            "The stream returned an error. It may have moved, expired or gone offline."

        PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED ->
            "This link uses unencrypted HTTP, which this app doesn't allow."

        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED ->
            "This format isn't supported by the player. Try HLS, MP4 or WebM."

        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED ->
            "Your device couldn't decode this video stream."

        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ->
            "This stream looks malformed or incomplete."

        else -> "This stream stopped unexpectedly (${error.errorCodeName})."
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
