package app.costream.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.costream.ui.components.AuroraBackground
import app.costream.ui.components.LogoLockup
import app.costream.ui.theme.Ink
import app.costream.ui.theme.Teal
import app.costream.ui.theme.TextMuted
import app.costream.ui.theme.TextPrimary
import app.costream.ui.theme.TextSecondary

@Composable
fun LoginScreen(
    googleConfigured: Boolean,
    busy: Boolean,
    error: String?,
    onGoogle: (android.content.Context) -> Unit,
    onGuest: (String) -> Unit
) {
    val context = LocalContext.current
    var guestName by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize().background(Ink)) {
        AuroraBackground(intensity = 1.08f)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(0.7f))
            LogoLockup()
            Spacer(Modifier.height(28.dp))
            Text(
                text = "Movies, series and every episode — together.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                modifier = Modifier.fillMaxWidth(0.82f)
            )
            Spacer(Modifier.height(34.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.055f), RoundedCornerShape(28.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(28.dp))
                    .padding(18.dp)
            ) {
                Text("Start watching", color = TextPrimary, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(5.dp))
                Text("Sign in, or keep everything private in guest mode.", color = TextMuted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .clickable(enabled = googleConfigured && !busy) { onGoogle(context) }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (busy) CircularProgressIndicator(color = Ink, strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.AccountCircle, contentDescription = null, tint = Ink)
                    Spacer(Modifier.padding(5.dp))
                    Text(
                        if (googleConfigured) "Continue with Google" else "Add creds.json for Google login",
                        color = if (googleConfigured) Ink else Ink.copy(alpha = 0.45f),
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(15.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = 0.10f)))
                    Text("  OR  ", color = TextMuted, fontSize = 9.sp)
                    Box(Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = 0.10f)))
                }
                Spacer(Modifier.height(15.dp))
                BasicTextField(
                    value = guestName,
                    onValueChange = { guestName = it.take(40) },
                    singleLine = true,
                    cursorBrush = SolidColor(Teal),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(Color.White.copy(alpha = 0.055f), RoundedCornerShape(15.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(15.dp)),
                    decorationBox = { field ->
                        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Person, contentDescription = null, tint = TextMuted)
                            Spacer(Modifier.padding(5.dp))
                            Box(Modifier.weight(1f)) {
                                if (guestName.isBlank()) Text("Your name (optional)", color = TextMuted, fontSize = 13.sp)
                                field()
                            }
                        }
                    }
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(Teal, RoundedCornerShape(15.dp))
                        .clickable(enabled = !busy) { onGuest(guestName) }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Browse as guest", color = Ink, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.ArrowForward, contentDescription = null, tint = Ink)
                }
                if (!error.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(error, color = Color(0xFFFF9AAB), style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.weight(1f))
            Text("Your watch history stays on this device.", color = TextMuted, fontSize = 10.sp)
            Spacer(Modifier.height(16.dp))
        }
    }
}
