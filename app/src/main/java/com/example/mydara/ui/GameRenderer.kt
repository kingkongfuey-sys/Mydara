package com.example.mydara.ui

import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.example.mydara.game.Fraction
import com.example.mydara.game.MapSize
import com.example.mydara.game.Planet
import com.example.mydara.game.PlanetType
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

private val BackgroundColor = Color(0xFF111111)
private val LinkGray = Color(0xFF444444)
private val InkColor = Color(0xFF111111)

internal fun fractionColor(f: Fraction): Color = Color(f.argb)

/** Draws the whole scene from session state. Only reads state; all game logic lives elsewhere. */
internal fun DrawScope.drawGame(s: GameSession, paint: Paint) {
    val cam = s.camera
    val model = s.model
    val scale = cam.scale.toFloat()
    val baseR = (Planet.RADIUS * scale).toFloat()
    val thick = max(0.008f * scale, 1.5f)
    val fog = s.config.fogOfWar

    fun px(x: Double) = cam.toScreenX(x).toFloat()
    fun py(y: Double) = cam.toScreenY(y).toFloat()
    fun shown(p: Planet) = !fog || s.visible[p.id]

    drawRect(BackgroundColor)

    // faint playfield border
    drawRect(
        Color.White.copy(alpha = 0.06f),
        topLeft = Offset(px(0.0), py(0.0)),
        size = Size((model.mapWidth * scale).toFloat(), (model.mapHeight * scale).toFloat()),
        style = Stroke(width = 2f)
    )

    // stars
    for (st in s.stars.stars) {
        drawCircle(
            Color.White,
            radius = (0.01 * scale * st.scale).toFloat(),
            center = Offset(px(st.x), py(st.y)),
            alpha = (st.alpha * 0.6).toFloat()
        )
    }

    // links: dotted gray between different fractions, solid fraction color when both ends match
    for (l in model.links) {
        val a = Offset(px(l.a.x), py(l.a.y))
        val b = Offset(px(l.b.x), py(l.b.y))
        val dim = if (fog && !s.visible[l.a.id] && !s.visible[l.b.id]) 0.5f else 1f
        if (l.isSolid) {
            val col = fractionColor(l.a.fraction)
            drawLine(
                Color.Black, a + Offset(0f, thick * 0.6f), b + Offset(0f, thick * 0.6f),
                strokeWidth = thick, alpha = 0.5f * dim
            )
            drawLine(col, a, b, strokeWidth = thick, alpha = dim)
        } else {
            val len = hypot(b.x - a.x, b.y - a.y)
            val gap = max(0.7f * baseR, 2f)
            val dotR = max(0.3f * baseR, 1f)
            var d = 0f
            while (len > 0f && d <= len) {
                drawCircle(
                    LinkGray, dotR,
                    Offset(a.x + (b.x - a.x) * d / len, a.y + (b.y - a.y) * d / len),
                    alpha = dim
                )
                d += gap
            }
        }
    }

    // valid targets of the selected planet get a faint ring
    val sel = s.input.selected
    if (sel != null) {
        for (l in sel.links) {
            val t = l.other(sel)
            drawCircle(
                Color.White, baseR * 1.3f, Offset(px(t.x), py(t.y)),
                alpha = 0.25f, style = Stroke(width = thick)
            )
        }
    }

    // planets
    for (p in model.planets) {
        val c = Offset(px(p.x), py(p.y))
        val r = baseR * p.popScale.toFloat()
        val vis = shown(p)
        val a = if (vis) 1f else 0.5f
        val col = fractionColor(p.fraction)

        // defense arcs sit under the body; the half outside the circumference stays visible
        val maxDef = p.type.maxDefense
        if (vis && maxDef > 0) {
            val step = 360f / maxDef
            val sweep = step * 0.7f
            val width = if (p.type == PlanetType.DEFENSIVE) r * 0.34f else r * 0.22f
            for (k in 0 until maxDef) {
                if (k < p.defense) {
                    drawArc(
                        col, -90f + k * step - sweep / 2f, sweep, false,
                        Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(width = width)
                    )
                }
            }
        }
        drawCircle(col, r, c, alpha = a)
        drawCircle(lerp(col, Color.Black, 0.4f), r, c, alpha = a, style = Stroke(width = r * 0.1f))

        if (vis) {
            val txt = p.wholeUnits.toString()
            drawLabel(paint, txt, c.x, c.y - r * 0.1f, r * 0.85f, InkColor.toArgb())
            drawTypeGlyph(p.type, Offset(c.x, c.y + r * 0.55f), r * 0.2f, InkColor)
        }
    }

    // auto-target markers (player's planets): rotating ring + pointer toward the target
    for (p in model.planets) {
        val t = p.autoTarget ?: continue
        if (p.fraction != Fraction.PLAYER) continue
        val c = Offset(px(p.x), py(p.y))
        val rr = baseR * 1.2f
        val rot = (model.frame * 3 % 360).toFloat()
        val col = fractionColor(p.fraction)
        for (i in 0 until 6) {
            drawArc(
                col, rot + i * 60f, 30f, false,
                Offset(c.x - rr, c.y - rr), Size(2 * rr, 2 * rr), style = Stroke(width = thick * 1.3f)
            )
        }
        val ang = atan2(t.y - p.y, t.x - p.x).toFloat()
        val tip = Offset(c.x + cos(ang) * rr * 1.5f, c.y + sin(ang) * rr * 1.5f)
        val baseC = Offset(c.x + cos(ang) * rr * 1.08f, c.y + sin(ang) * rr * 1.08f)
        val nx = -sin(ang) * rr * 0.22f
        val ny = cos(ang) * rr * 0.22f
        val tri = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(baseC.x + nx, baseC.y + ny)
            lineTo(baseC.x - nx, baseC.y - ny)
            close()
        }
        drawPath(tri, col)
    }

    // units: dot radius 2 x link thickness, value label above (not on Medium maps)
    val dotR = 2f * thick
    for (b in model.batches) {
        val c = Offset(px(b.x), py(b.y))
        drawCircle(fractionColor(b.fraction), dotR, c)
        if (s.config.size != MapSize.MEDIUM) {
            drawLabel(paint, b.value.toString(), c.x, c.y - 1.8f * dotR - dotR * 0.8f, dotR * 1.6f, Color.White.toArgb())
        }
    }

    // selection / touch feedback
    if (sel != null) {
        val c = Offset(px(sel.x), py(sel.y))
        val effect = if (s.input.halved) PathEffect.dashPathEffect(floatArrayOf(14f, 10f)) else null
        drawCircle(Color.White, baseR * 1.35f, c, style = Stroke(width = thick * 1.2f, pathEffect = effect))
        if (s.input.halved) drawLabel(paint, "1/2", c.x, c.y - baseR * 1.8f, baseR * 0.7f, Color.White.toArgb())
    }
    s.input.highlight?.let { h ->
        drawCircle(
            Color.White, baseR * 1.5f, Offset(px(h.x), py(h.y)),
            alpha = 0.55f, style = Stroke(width = thick)
        )
    }
    s.input.swipeFrom?.let { from ->
        drawLine(
            Color.White,
            Offset(px(from.x), py(from.y)),
            Offset(px(s.input.swipeX), py(s.input.swipeY)),
            strokeWidth = thick, alpha = 0.5f
        )
    }

    // particles
    for (p in s.particles.particles) {
        drawCircle(
            Color(p.color), radius = max((p.radius * scale).toFloat(), 0.5f),
            center = Offset(px(p.x), py(p.y)), alpha = p.alpha.toFloat()
        )
    }
}

/** Planet type indicator: economic = plus, defensive = diamond outline. Also used on upgrade buttons. */
internal fun DrawScope.drawTypeGlyph(type: PlanetType, c: Offset, size: Float, color: Color, alpha: Float = 1f) {
    when (type) {
        PlanetType.ECONOMIC -> {
            drawLine(color, Offset(c.x - size, c.y), Offset(c.x + size, c.y), strokeWidth = size * 0.5f, alpha = alpha)
            drawLine(color, Offset(c.x, c.y - size), Offset(c.x, c.y + size), strokeWidth = size * 0.5f, alpha = alpha)
        }
        PlanetType.DEFENSIVE -> {
            val path = Path().apply {
                moveTo(c.x, c.y - size)
                lineTo(c.x + size, c.y)
                lineTo(c.x, c.y + size)
                lineTo(c.x - size, c.y)
                close()
            }
            drawPath(path, color, alpha = alpha, style = Stroke(width = size * 0.45f))
        }
        PlanetType.NORMAL -> Unit
    }
}

/** Centered text at (x, y) using a shared Paint. */
internal fun DrawScope.drawLabel(paint: Paint, text: String, x: Float, y: Float, size: Float, argb: Int) {
    paint.textSize = size
    paint.color = argb
    drawContext.canvas.nativeCanvas.drawText(text, x, y + size * 0.35f, paint)
}
