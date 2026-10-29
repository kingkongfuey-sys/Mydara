package com.example.mydara.ui

import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mydara.game.Fraction
import com.example.mydara.game.GameModel
import com.example.mydara.game.Outcome
import com.example.mydara.game.PlanetType
import kotlin.math.roundToInt

private val Panel = Color(0xFF222222)

/** Stage 3 game UI: canvas + gestures + HUD + result overlay. [onExit] is the Return-to-menu button. */
@Composable
fun GameScreen(config: GameConfig, onExit: () -> Unit) {
    val slop = LocalViewConfiguration.current.touchSlop
    val session = remember(config) { GameSession(config, slop.toDouble()) }
    var frameTick by remember(session) { mutableIntStateOf(0) }
    var hud by remember(session) { mutableStateOf(session.hudState()) }
    val paint = remember {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
    }

    // Game loop: one vsync-driven coroutine; the session runs fixed 60 Hz steps internally.
    LaunchedEffect(session) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    session.advance(((now - last) / 1_000_000_000.0).coerceAtMost(0.1), SystemClock.uptimeMillis())
                }
                last = now
                frameTick++
                hud = session.hudState()
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF111111))) {
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { session.camera.resize(it.width.toDouble(), it.height.toDouble()) }
                .pointerInput(session) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            for (c in event.changes) {
                                val id = c.id.value.toInt()
                                val x = c.position.x.toDouble()
                                val y = c.position.y.toDouble()
                                when {
                                    c.changedToDown() -> session.input.pointerDown(id, x, y, c.uptimeMillis)
                                    c.changedToUp() -> session.input.pointerUp(id, x, y, c.uptimeMillis)
                                    c.pressed -> session.input.pointerMove(id, x, y, c.uptimeMillis)
                                }
                                c.consume()
                            }
                        }
                    }
                }
        ) {
            frameTick // read so the canvas redraws every frame
            drawGame(session, paint)
        }

        // top-left: play/pause + velocity slider
        Row(
            Modifier.align(Alignment.TopStart).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlayPauseButton(paused = hud.paused, onClick = { session.togglePause() })
            Spacer(Modifier.width(10.dp))
            VelocitySlider(
                index = hud.velocityIndex,
                dimmed = hud.paused,
                onIndex = { session.setVelocityIndex(it) },
                modifier = Modifier.width(200.dp).height(44.dp)
            )
        }

        // top-right: seed
        Text(
            "seed ${session.seed}",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.TopEnd).padding(14.dp)
        )

        // bottom corners: upgrade buttons for the selected own planet
        val type = hud.selectedType
        if (type != null) {
            if (type != PlanetType.DEFENSIVE) {
                UpgradeButton(
                    PlanetType.DEFENSIVE, hud.canAffordUpgrade,
                    Modifier.align(Alignment.BottomStart).padding(28.dp)
                ) { session.upgradeSelected(PlanetType.DEFENSIVE) }
            }
            if (type != PlanetType.ECONOMIC) {
                UpgradeButton(
                    PlanetType.ECONOMIC, hud.canAffordUpgrade,
                    Modifier.align(Alignment.BottomEnd).padding(28.dp)
                ) { session.upgradeSelected(PlanetType.ECONOMIC) }
            }
        }

        if (hud.outcome != Outcome.PLAYING) ResultOverlay(hud.outcome, onExit)
    }
}

@Composable
private fun PlayPauseButton(paused: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(Panel).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(20.dp)) {
            if (paused) {
                val tri = Path().apply {
                    moveTo(size.width * 0.15f, 0f)
                    lineTo(size.width, size.height / 2f)
                    lineTo(size.width * 0.15f, size.height)
                    close()
                }
                drawPath(tri, Color.White)
            } else {
                val w = size.width * 0.3f
                drawRect(Color.White, Offset(size.width * 0.1f, 0f), androidx.compose.ui.geometry.Size(w, size.height))
                drawRect(Color.White, Offset(size.width * 0.6f, 0f), androidx.compose.ui.geometry.Size(w, size.height))
            }
        }
    }
}

/** Drag slider that snaps to the 13 velocity values; the knob shows the current multiplier. */
@Composable
private fun VelocitySlider(index: Int, dimmed: Boolean, onIndex: (Int) -> Unit, modifier: Modifier) {
    val steps = GameModel.VELOCITY_STEPS.size
    var widthPx by remember { mutableIntStateOf(1) }
    var heightPx by remember { mutableIntStateOf(1) }
    val latest by rememberUpdatedState(onIndex)
    val knobFraction by animateFloatAsState(index / (steps - 1).toFloat(), label = "velocityKnob")
    val textPaint = remember {
        Paint().apply { isAntiAlias = true; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    }

    fun indexAt(x: Float): Int {
        val r = heightPx * 0.45f
        val usable = (widthPx - 2 * r).coerceAtLeast(1f)
        return (((x - r) / usable).coerceIn(0f, 1f) * (steps - 1)).roundToInt()
    }

    Box(
        modifier
            .onSizeChanged { widthPx = it.width; heightPx = it.height }
            .pointerInput(Unit) { detectTapGestures { latest(indexAt(it.x)) } }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { latest(indexAt(it.x)) },
                    onDrag = { change, _ -> change.consume(); latest(indexAt(change.position.x)) }
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.height * 0.45f
            val alpha = if (dimmed) 0.4f else 1f
            val y = size.height / 2f
            drawLine(
                Color.White, Offset(r, y), Offset(size.width - r, y),
                strokeWidth = 4f, alpha = 0.35f * alpha
            )
            for (i in 0 until steps) {
                val x = r + (size.width - 2 * r) * i / (steps - 1)
                drawCircle(Color.White, 3f, Offset(x, y), alpha = 0.5f * alpha)
            }
            val kx = r + (size.width - 2 * r) * knobFraction
            drawCircle(Panel, r, Offset(kx, y), alpha = alpha)
            drawCircle(Color.White, r, Offset(kx, y), alpha = alpha, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
            val v = GameModel.VELOCITY_STEPS[index.coerceIn(0, steps - 1)]
            val label = (if (v == v.toInt().toDouble()) v.toInt().toString() else v.toString()) + "x"
            drawLabel(textPaint, label, kx, y, r * 0.8f, Color.White.toArgb())
        }
    }
}

@Composable
private fun UpgradeButton(type: PlanetType, affordable: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("10", color = Color.White.copy(alpha = if (affordable) 1f else 0.45f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Box(
            Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(if (type == PlanetType.ECONOMIC) Color(0xFF2E7D32) else Color(0xFF1565C0))
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(32.dp)) {
                drawTypeGlyph(
                    type, Offset(size.width / 2f, size.height / 2f), size.width * 0.4f,
                    Color.White, alpha = if (affordable) 1f else 0.45f
                )
            }
        }
    }
}

@Composable
private fun ResultOverlay(outcome: Outcome, onExit: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(
                if (outcome == Outcome.VICTORY) "Victory" else "Defeat",
                color = if (outcome == Outcome.VICTORY) Color(Fraction.PLAYER.argb) else Color(Fraction.RED.argb),
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold
            )
            Button(onClick = onExit) { Text("Return to menu") }
        }
    }
}
