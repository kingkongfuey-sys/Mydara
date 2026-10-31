package com.example.mydara.ui

import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mydara.game.Fraction
import com.example.mydara.game.GameModel
import com.example.mydara.game.Outcome
import com.example.mydara.game.PlanetType
import kotlin.math.roundToInt

private val KnobText = Color(0xFF0B2A3A)
private val DotsFill = Color(0xFF123A4E)

/** Game UI: canvas + gestures + minimal HUD. [onExit] is the Return-to-menu button on the result overlay. */
@Composable
fun GameScreen(config: GameConfig, onExit: () -> Unit) {
    val slop = LocalViewConfiguration.current.touchSlop
    val session = remember(config) { GameSession(config, slop.toDouble()) }
    var frameTick by remember(session) { mutableIntStateOf(0) }
    var hud by remember(session) { mutableStateOf(session.hudState()) }
    var speedOpen by remember(session) { mutableStateOf(false) }
    var infoOpen by remember(session) { mutableStateOf(false) }
    val paint = remember {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        }
    }

    // Sound + haptics: synthesized effects, switches persisted in SharedPreferences.
    val context = LocalContext.current
    val view = LocalView.current
    val feedback = remember { AndroidFeedback(context, view) }
    var soundOn by remember { mutableStateOf(FeedbackSettings.sound) }
    var hapticsOn by remember { mutableStateOf(FeedbackSettings.haptics) }
    DisposableEffect(session, feedback) {
        session.feedback.sink = feedback
        onDispose {
            session.feedback.sink = FeedbackSink.None
        }
    }
    DisposableEffect(feedback) { onDispose { feedback.release() } }
    LaunchedEffect(Unit) {
        FeedbackPrefs.load(context)
        soundOn = FeedbackSettings.sound
        hapticsOn = FeedbackSettings.haptics
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

    Box(Modifier.fillMaxSize().background(BackgroundColor)) {
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

        // top-left: pause + fast-forward (opens the velocity slider)
        Column(Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PauseIcon(paused = hud.paused, onClick = { session.togglePause() })
                Spacer(Modifier.width(10.dp))
                FastForwardIcon(
                    label = velocityLabel(GameModel.VELOCITY_STEPS[hud.velocityIndex]),
                    onClick = { speedOpen = !speedOpen }
                )
            }
            if (speedOpen) {
                VelocitySlider(
                    index = hud.velocityIndex,
                    onIndex = { session.setVelocityIndex(it) },
                    modifier = Modifier.padding(start = 8.dp, top = 2.dp).width(230.dp).height(44.dp)
                )
            }
        }

        // top-right: three-dot icon; tap for seed + sound / haptics switches
        Column(
            Modifier.align(Alignment.TopEnd).padding(end = 6.dp, top = 10.dp),
            horizontalAlignment = Alignment.End
        ) {
            MenuDotsIcon(onClick = { infoOpen = !infoOpen })
            if (infoOpen) {
                Text(
                    "seed ${session.seed}",
                    color = IconFill.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(end = 10.dp)
                )
                MenuToggle("Sound", soundOn) {
                    soundOn = !soundOn
                    FeedbackSettings.sound = soundOn
                    FeedbackPrefs.save(context)
                }
                MenuToggle("Haptics", hapticsOn) {
                    hapticsOn = !hapticsOn
                    FeedbackSettings.haptics = hapticsOn
                    FeedbackPrefs.save(context)
                }
            }
        }

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
private fun MenuToggle(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        "$label  ${if (on) "on" else "off"}",
        color = IconFill.copy(alpha = if (on) 1f else 0.5f),
        fontSize = 14.sp,
        modifier = Modifier.clickable { onClick() }.padding(horizontal = 10.dp, vertical = 8.dp)
    )
}

private fun velocityLabel(v: Double): String =
    (if (v == v.toInt().toDouble()) v.toInt().toString() else v.toString()) + "x"

// ---------------------------------------------------------------------- icons (light gray, dark outline)

@Composable
private fun PauseIcon(paused: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(24.dp)) {
            val w = size.width
            val h = size.height
            val sw = w * 0.08f
            if (paused) {
                val tri = Path().apply {
                    moveTo(w * 0.2f, h * 0.05f)
                    lineTo(w * 0.95f, h * 0.5f)
                    lineTo(w * 0.2f, h * 0.95f)
                    close()
                }
                drawPath(tri, IconFill)
                drawPath(tri, InkColor, style = Stroke(width = sw, join = StrokeJoin.Round))
            } else {
                for (x in listOf(0.12f, 0.58f)) {
                    drawRoundRect(IconFill, Offset(w * x, h * 0.05f), Size(w * 0.3f, h * 0.9f), CornerRadius(w * 0.06f))
                    drawRoundRect(
                        InkColor, Offset(w * x, h * 0.05f), Size(w * 0.3f, h * 0.9f), CornerRadius(w * 0.06f),
                        style = Stroke(width = sw)
                    )
                }
            }
        }
    }
}

@Composable
private fun FastForwardIcon(label: String, onClick: () -> Unit) {
    Row(Modifier.clickable { onClick() }.padding(end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(28.dp)) {
                val w = size.width
                val h = size.height
                val sw = w * 0.07f
                for (x0 in listOf(0.04f, 0.5f)) {
                    val t = Path().apply {
                        moveTo(w * x0, h * 0.12f)
                        lineTo(w * (x0 + 0.46f), h * 0.5f)
                        lineTo(w * x0, h * 0.88f)
                        close()
                    }
                    drawPath(t, IconFill)
                    drawPath(t, InkColor, style = Stroke(width = sw, join = StrokeJoin.Round))
                }
            }
        }
        Text(label, color = IconFill.copy(alpha = 0.8f), fontSize = 11.sp)
    }
}

@Composable
private fun MenuDotsIcon(onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.width(12.dp).height(30.dp)) {
            val r = size.width * 0.42f
            for (f in listOf(0.14f, 0.5f, 0.86f)) {
                val c = Offset(size.width / 2f, size.height * f)
                drawCircle(DotsFill, r, c)
                drawCircle(IconFill, r, c, style = Stroke(width = size.width * 0.14f))
            }
        }
    }
}

/** Drag slider that snaps to the 13 velocity values; the knob shows the current multiplier. */
@Composable
private fun VelocitySlider(index: Int, onIndex: (Int) -> Unit, modifier: Modifier) {
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
            val y = size.height / 2f
            drawLine(IconFill, Offset(r, y), Offset(size.width - r, y), strokeWidth = 4f, alpha = 0.4f)
            for (i in 0 until steps) {
                val x = r + (size.width - 2 * r) * i / (steps - 1)
                drawCircle(IconFill, 3f, Offset(x, y), alpha = 0.6f)
            }
            val kx = r + (size.width - 2 * r) * knobFraction
            drawCircle(IconFill, r, Offset(kx, y))
            drawCircle(InkColor, r, Offset(kx, y), style = Stroke(width = 3f))
            val label = velocityLabel(GameModel.VELOCITY_STEPS[index.coerceIn(0, steps - 1)])
            drawLabel(textPaint, label, kx, y, r * 0.8f, KnobText.toArgb())
        }
    }
}

@Composable
private fun UpgradeButton(type: PlanetType, affordable: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val a = if (affordable) 1f else 0.5f
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("10", color = IconFill.copy(alpha = a), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Box(
            Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(IconFill.copy(alpha = a))
                .border(2.dp, InkColor, CircleShape)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(30.dp)) {
                drawTypeGlyph(type, Offset(size.width / 2f, size.height / 2f), size.width * 0.38f, InkColor)
            }
        }
    }
}

@Composable
private fun ResultOverlay(outcome: Outcome, onExit: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(BackgroundColor.copy(alpha = 0.8f))
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
                fontWeight = FontWeight.Light
            )
            Button(onClick = onExit) { Text("Back to setup") }
        }
    }
}
