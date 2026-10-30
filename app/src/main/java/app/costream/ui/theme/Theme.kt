package app.costream.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.costream.R

val Ink = Color(0xFF06060A)
val InkDeep = Color(0xFF040407)
val Surface = Color(0xFF0E0E16)
val SurfaceHigh = Color(0xFF171722)
val SurfaceGlass = Color(0xFF1D1D2A)
val TextPrimary = Color(0xFFF7F8FC)
val TextSecondary = Color(0xFFA0A5BA)
val TextMuted = Color(0xFF6F7489)
val Teal = Color(0xFF6EE7D8)
val Mint = Color(0xFF9AF5C8)
val Blue = Color(0xFF73A5FF)
val Violet = Color(0xFF9B7CFF)
val Pink = Color(0xFFFF6FA8)
val Amber = Color(0xFFFFC46B)
val Red = Color(0xFFFF5265)
val Gold = Color(0xFFFFD700)
val Line = Color(0x1FFFFFFF)

val AccentSets: List<List<Color>> = listOf(
    listOf(Teal, Blue),
    listOf(Violet, Pink),
    listOf(Amber, Pink),
    listOf(Blue, Violet),
    listOf(Teal, Violet),
    listOf(Mint, Blue),
    listOf(Pink, Amber)
)

fun accentColors(index: Int): List<Color> = AccentSets[index.mod(AccentSets.size)]

fun accentBrush(index: Int, horizontal: Boolean = false): Brush = if (horizontal) {
    Brush.horizontalGradient(accentColors(index))
} else {
    Brush.linearGradient(accentColors(index))
}

private val MyDarkScheme = darkColorScheme(
    primary = Teal,
    onPrimary = Ink,
    primaryContainer = Color(0xFF163A3A),
    onPrimaryContainer = Teal,
    secondary = Violet,
    onSecondary = Color.White,
    tertiary = Pink,
    onTertiary = Ink,
    background = Ink,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = TextSecondary,
    outline = Line,
    outlineVariant = Color(0x14FFFFFF),
    error = Red,
    onError = Color.White
)

@Composable
fun CoStreamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MyDarkScheme,
        content = content
    )
}