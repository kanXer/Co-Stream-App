package app.costream.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.costream.ui.theme.Blue
import app.costream.ui.theme.Pink
import app.costream.ui.theme.Teal
import app.costream.ui.theme.TextPrimary
import app.costream.ui.theme.Violet
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CoStreamMark(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    glowing: Boolean = true
) {
    val transition = rememberInfiniteTransition(label = "logo_glow")
    val pulse by transition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_pulse"
    )
    val sweep = Brush.sweepGradient(listOf(Teal, Blue, Violet, Pink, Teal))

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.085f
        val inset = w * 0.17f
        val arcSize = Size(w - inset * 2f, h - inset * 2f)
        val arcTopLeft = Offset(inset, inset)

        if (glowing) {
            drawArc(
                brush = sweep,
                startAngle = 40f,
                sweepAngle = 280f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = stroke * 2.5f, cap = StrokeCap.Round),
                alpha = 0.10f * pulse
            )
        }
        drawArc(
            brush = sweep,
            startAngle = 40f,
            sweepAngle = 280f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )

        val triangle = Path().apply {
            moveTo(w * 0.455f, h * 0.395f)
            lineTo(w * 0.455f, h * 0.605f)
            lineTo(w * 0.625f, h * 0.50f)
            close()
        }
        drawPath(triangle, Color.White)
        drawCircle(
            color = Pink,
            radius = w * 0.036f,
            center = Offset(w * 0.785f, h * 0.34f)
        )
    }
}

@Composable
fun CoStreamWordmark(
    modifier: Modifier = Modifier,
    markSize: Dp = 26.dp,
    textSize: Int = 13
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        CoStreamMark(size = markSize, glowing = false)
        Spacer(Modifier.width(9.dp))
        Text(
            text = "CO-STREAM",
            color = TextPrimary,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 2.4.sp,
                fontSize = textSize.sp
            )
        )
    }
}

@Composable
fun LogoLockup(modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            CoStreamMark(size = 86.dp)
        }
        Spacer(Modifier.size(18.dp))
        Text(
            text = "CO-STREAM",
            color = TextPrimary,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 7.sp
            )
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = "CINEMA, MINUS THE NOISE",
            color = TextPrimary.copy(alpha = 0.42f),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.4.sp)
        )
    }
}
