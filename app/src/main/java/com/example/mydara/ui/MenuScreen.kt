package com.example.mydara.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.mydara.game.Fraction
import kotlin.math.cos
import kotlin.math.sin

/**
 * Main menu: logo, big round Play button and a round settings button on the left. There is deliberately no
 * quit button. Layout is proportional to the screen (logo ~50% wide).
 */
@Composable
fun MenuScreen(onPlay: () -> Unit, onSettings: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        AnimatedGround()
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = maxWidth
            val h = maxHeight
            val logoW = w * 0.5f
            val logoH = logoW / 1.1f
            val playD = w * 0.32f
            val settingsD = w * 0.16f
            MydaraLogo(
                Modifier.width(logoW).height(logoH).align(Alignment.TopCenter)
                    .offset(y = h * 0.36f - logoH / 2)
            )
            PlayButton(
                Modifier.size(playD).align(Alignment.TopCenter).offset(y = h * 0.62f - playD / 2),
                onPlay
            )
            SettingsButton(
                Modifier.size(settingsD).align(Alignment.TopStart)
                    .offset(x = w * 0.07f, y = h * 0.62f - settingsD / 2),
                onSettings
            )
        }
    }
}

@Composable
private fun MydaraLogo(modifier: Modifier) {
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.width * 0.17f
            val c = Offset(size.width / 2f, size.height * 0.38f)
            val col = Color(Fraction.PLAYER.argb)
            drawCircle(col, r * 1.9f, c, alpha = 0.10f)
            drawCircle(col, r * 1.45f, c, alpha = 0.14f)
            val step = 360f / 7
            val sweep = step * 0.72f
            val ringR = r * 1.32f
            val shield = lerp(col, Color.White, 0.3f)
            for (k in 0 until 7) {
                drawArc(
                    shield, -90f + k * step - sweep / 2f, sweep, false,
                    Offset(c.x - ringR, c.y - ringR), Size(2 * ringR, 2 * ringR),
                    alpha = if (k < 5) 1f else 0.16f, style = Stroke(width = r * 0.24f)
                )
            }
            drawCircle(
                Brush.radialGradient(
                    colors = listOf(lerp(col, Color.White, 0.45f), col, lerp(col, Color.Black, 0.3f)),
                    center = Offset(c.x - r * 0.35f, c.y - r * 0.4f),
                    radius = r * 1.6f
                ),
                r, c
            )
            drawCircle(darkOf(col), r, c, style = Stroke(width = r * 0.09f))
            drawCircle(Color.White, r * 0.17f, Offset(c.x - r * 0.4f, c.y - r * 0.44f), alpha = 0.4f)
        }
        Text(
            "MYDARA",
            modifier = Modifier.align(Alignment.BottomCenter),
            color = IconFill,
            fontSize = 26.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 4.sp
        )
    }
}

@Composable
private fun PlayButton(modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.clip(CircleShape).clickable { onClick() }) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f * 0.86f
            val c = center
            val col = Color(Fraction.PLAYER.argb)
            drawCircle(col, size.minDimension / 2f, c, alpha = 0.18f)
            drawCircle(
                Brush.radialGradient(
                    colors = listOf(lerp(col, Color.White, 0.45f), col, lerp(col, Color.Black, 0.3f)),
                    center = Offset(c.x - r * 0.35f, c.y - r * 0.4f),
                    radius = r * 1.6f
                ),
                r, c
            )
            drawCircle(darkOf(col), r, c, style = Stroke(width = r * 0.05f))
            val tri = Path().apply {
                moveTo(c.x - r * 0.2f, c.y - r * 0.38f)
                lineTo(c.x + r * 0.42f, c.y)
                lineTo(c.x - r * 0.2f, c.y + r * 0.38f)
                close()
            }
            drawPath(tri, InkColor)
        }
    }
}

@Composable
private fun SettingsButton(modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.clip(CircleShape).background(PanelColor.copy(alpha = 0.85f)).clickable { onClick() }) {
        Canvas(Modifier.fillMaxSize()) {
            val m = size.minDimension
            val c = center
            // gear: ring + eight teeth + hub
            drawCircle(IconFill, m * 0.22f, c, style = Stroke(width = m * 0.08f))
            for (i in 0 until 8) {
                val a = i * (Math.PI / 4).toFloat()
                drawLine(
                    IconFill,
                    Offset(c.x + cos(a) * m * 0.27f, c.y + sin(a) * m * 0.27f),
                    Offset(c.x + cos(a) * m * 0.37f, c.y + sin(a) * m * 0.37f),
                    strokeWidth = m * 0.09f, cap = StrokeCap.Round
                )
            }
            drawCircle(IconFill, m * 0.07f, c)
        }
    }
}
