package app.costream

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.costream.ui.AppViewModel
import app.costream.ui.CoStreamRoot
import app.costream.ui.theme.CoStreamTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()
    private var pipMode by mutableStateOf(false)
    private var backgroundedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            CoStreamTheme {
                CoStreamRoot(
                    viewModel = viewModel,
                    pipMode = pipMode
                )
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipMode = isInPictureInPictureMode
    }

    override fun onStop() {
        super.onStop()
        if (!viewModel.isPlayerOnTop()) {
            backgroundedAt = System.currentTimeMillis()
        }
    }

    override fun onStart() {
        super.onStart()
        val since = backgroundedAt
        backgroundedAt = 0L
        if (since > 0L &&
            System.currentTimeMillis() - since > BACKGROUND_RESET_MS &&
            !viewModel.isPlayerOnTop()
        ) {
            viewModel.returnHomeIfIdle()
        }
    }

    private companion object {
        const val BACKGROUND_RESET_MS = 30 * 60 * 1000L
    }
}
