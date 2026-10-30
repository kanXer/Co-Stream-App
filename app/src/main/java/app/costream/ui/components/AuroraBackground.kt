package app.costream.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import app.costream.ui.theme.Blue
import app.costream.ui.theme.Ink
import app.costream.ui.theme.InkDeep
import app.costream.ui.theme.Pink
import app.costream.ui.theme.Teal
import app.costream.ui.theme.Violet
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
    animate: Boolean = true
) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(30_000, easing = LinearEasing)
        ),
        label = "aurora_phase"
    )
    val p = if (animate) phase else 0f

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            Brush.verticalGradient(
                colors = listOf(
                    InkDeep,
                    Ink,
                    Color(0xFF0A0811),
                    InkDeep
                )
            )
        )

        val minSide = minOf(size.width, size.height)
        val center1 = Offset(
            size.width * (0.14f + 0.06f * cos(p)),
            size.height * (0.18f + 0.04f * sin(p * 0.8f))
        )
        val radius1 = minSide * 0.95f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Teal.copy(alpha = 0.20f * intensity),
                    Teal.copy(alpha = 0.055f * intensity),
                    Color.Transparent
                ),
                center = center1,
                radius = radius1
            ),
            radius = radius1,
            center = center1
        )

        val center2 = Offset(
            size.width * (0.92f + 0.05f * sin(p * 0.7f)),
            size.height * (0.32f + 0.06f * cos(p))
        )
        val radius2 = minSide * 0.85f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Violet.copy(alpha = 0.24f * intensity),
                    Violet.copy(alpha = 0.06f * intensity),
                    Color.Transparent
                ),
                center = center2,
                radius = radius2
            ),
            radius = radius2,
            center = center2
        )

        val center3 = Offset(
            size.width * (0.58f + 0.08f * sin(p * 1.1f)),
            size.height * (0.92f + 0.03f * cos(p * 1.3f))
        )
        val radius3 = minSide * 0.75f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Pink.copy(alpha = 0.13f * intensity),
                    Blue.copy(alpha = 0.035f * intensity),
                    Color.Transparent
                ),
                center = center3,
                radius = radius3
            ),
            radius = radius3,
            center = center3
        )

        val random = Random(7)
        repeat(54) {
            val x = random.nextFloat() * size.width
            val y = random.nextFloat() * size.height
            val radius = 0.4f + random.nextFloat() * 1.1f
            drawCircle(
                color = Color.White.copy(alpha = 0.06f + random.nextFloat() * 0.11f),
                radius = radius,
                center = Offset(x, y)
            )
        }

        drawRect(
            Brush.verticalGradient(
                colors = listOf(Color.Transparent, Ink.copy(alpha = 0.72f)),
                startY = size.height * 0.45f,
                endY = size.height
            )
        )
    }
}
