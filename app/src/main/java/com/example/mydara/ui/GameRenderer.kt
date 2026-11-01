package com.example.mydara.ui

import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.example.mydara.game.Fraction
import com.example.mydara.game.Planet
import com.example.mydara.game.PlanetType
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

// Deep Ocean palette.
internal val BackgroundColor = Color(0xFF0B2A3A)
private val DashedLink = Color(0xFF5F8496)
internal val InkColor = Color(0xFF0B2A3A)
internal val IconFill = Color(0xFFD4E6EE)
private val SparkColor = Color(0xFFCFE8F2)

/** Planets are drawn smaller than their (touch/spacing) radius. */
private const val VISUAL_RADIUS = 0.6

internal fun fractionColor(f: Fraction): Color = Color(f.argb)

/** Outline / defense-tick / link-edge color: the fraction color darkened. */
internal fun darkOf(c: Color): Color = Color(c.red * 0.45f, c.green * 0.45f, c.blue * 0.45f, 1f)

/** Draws the whole scene from session state. Only reads state; all game logic lives elsewhere. */
internal fun DrawScope.drawGame(s: GameSession, paint: Paint) {
    val cam = s.camera
    val model = s.model
    val scale = cam.scale.toFloat()
    val baseR = (Planet.RADIUS * VISUAL_RADIUS * scale).toFloat()
    val thick = max(0.0085f * scale, 1.5f)
    val fog = s.config.fogOfWar

    fun px(x: Double) = cam.toScreenX(x).toFloat()
    fun py(y: Double) = cam.toScreenY(y).toFloat()
    fun shown(p: Planet) = !fog || s.visible[p.id]

    drawRect(BackgroundColor)

    // faint "snow" specks
    for (st in s.stars.stars) {
        drawCircle(
            Color.White,
            radius = (0.01 * scale * st.scale).toFloat(),
            center = Offset(px(st.x), py(st.y)),
            alpha = (st.alpha * 0.15).toFloat()
        )
    }

    // links: dark tube with a light core when both ends match; thin dashed lavender line otherwise
    val dash = PathEffect.dashPathEffect(floatArrayOf(baseR * 0.55f, baseR * 0.45f))
    for (l in model.links) {
        val a = Offset(px(l.a.x), py(l.a.y))
        val b = Offset(px(l.b.x), py(l.b.y))
        val dim = if (fog && !s.visible[l.a.id] && !s.visible[l.b.id]) 0.5f else 1f
        if (l.isSolid) {
            val col = fractionColor(l.a.fraction)
            drawLine(darkOf(col), a, b, strokeWidth = thick, alpha = dim)
            drawLine(col, a, b, strokeWidth = thick * 0.45f, alpha = dim)
        } else {
            drawLine(
                DashedLink, a, b, strokeWidth = max(thick * 0.28f, 1.5f),
                alpha = 0.8f * dim, pathEffect = dash
            )
        }
    }

    // valid targets of the selected planet get a faint ring
    val sel = s.input.selected
    if (sel != null) {
        for (l in sel.links) {
            val t = l.other(sel)
            drawCircle(
                IconFill, baseR * 1.95f, Offset(px(t.x), py(t.y)),
                alpha = 0.35f, style = Stroke(width = max(thick * 0.5f, 1.5f))
            )
        }
    }

    // planets: soft glow, segmented shield ring (with empty slots), glossy disc
    for (p in model.planets) {
        val c = Offset(px(p.x), py(p.y))
        val r = baseR * p.popScale.toFloat()
        val vis = shown(p)
        val a = if (vis) 1f else 0.5f
        val col = fractionColor(p.fraction)
        val dark = darkOf(col)
        val light = lerp(col, Color.White, 0.45f)

        drawCircle(col, r * 1.9f, c, alpha = 0.10f * a)
        drawCircle(col, r * 1.45f, c, alpha = 0.14f * a)

        val maxDef = p.type.maxDefense
        if (vis && maxDef > 0) {
            val step = 360f / maxDef
            val sweep = step * 0.72f
            val ringR = r * 1.32f
            val width = if (p.type == PlanetType.DEFENSIVE) r * 0.3f else r * 0.24f
            val shield = lerp(col, Color.White, 0.3f)
            val topLeft = Offset(c.x - ringR, c.y - ringR)
            val ringSize = Size(2 * ringR, 2 * ringR)
            for (k in 0 until maxDef) {
                val start = -90f + k * step - sweep / 2f
                // faint track so missing shield points are visible
                drawArc(shield, start, sweep, false, topLeft, ringSize, alpha = 0.16f, style = Stroke(width = width))
                val fill = (p.defense - k).toFloat().coerceIn(0f, 1f)
                if (fill > 0f) {
                    drawArc(shield, start, sweep * fill, false, topLeft, ringSize, alpha = 0.28f, style = Stroke(width = width * 1.9f))
                    drawArc(shield, start, sweep * fill, false, topLeft, ringSize, style = Stroke(width = width))
                }
            }
        }

        val body = Brush.radialGradient(
            colors = listOf(light, col, lerp(col, Color.Black, 0.3f)),
            center = Offset(c.x - r * 0.35f, c.y - r * 0.4f),
            radius = r * 1.6f
        )
        drawCircle(body, r, c, alpha = a)
        drawCircle(dark, r, c, alpha = a, style = Stroke(width = max(r * 0.09f, 1.2f)))
        drawCircle(Color.White, r * 0.88f, c, alpha = 0.22f * a, style = Stroke(width = max(r * 0.05f, 1f)))
        drawCircle(Color.White, r * 0.17f, Offset(c.x - r * 0.4f, c.y - r * 0.44f), alpha = 0.4f * a)

        if (vis) {
            drawLabel(paint, p.wholeUnits.toString(), c.x, c.y - r * 0.05f, r * 1.0f, InkColor.toArgb())
            drawTypeGlyph(p.type, Offset(c.x, c.y + r * 0.5f), r * 0.17f, InkColor)
        }
    }

    // auto-target marker: small white wedge on the rim pointing toward the target
    for (p in model.planets) {
        val t = p.autoTarget ?: continue
        if (p.fraction.isNeutral || !shown(p)) continue
        val c = Offset(px(p.x), py(p.y))
        val r = baseR
        val ang = atan2(t.y - p.y, t.x - p.x).toFloat()
        val dx = cos(ang)
        val dy = sin(ang)
        val tip = Offset(c.x + dx * r * 2.2f, c.y + dy * r * 2.2f)
        val baseC = Offset(c.x + dx * r * 1.6f, c.y + dy * r * 1.6f)
        val nx = -dy * r * 0.34f
        val ny = dx * r * 0.34f
        val tri = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(baseC.x + nx, baseC.y + ny)
            lineTo(baseC.x - nx, baseC.y - ny)
            close()
        }
        drawPath(tri, Color.White)
        drawPath(tri, InkColor, alpha = 0.6f, style = Stroke(width = 1f, join = StrokeJoin.Round))
    }

    // units: small outlined dots; batches of more than 10 show their value (with a dark halo for legibility)
    val dotR = thick * 1.5f
    val unitLabelSize = max(thick * 2.8f, baseR * 0.6f)
    for (b in model.batches) {
        if (!s.isUnitVisible(b)) continue // fog of war: far-away enemy traffic stays hidden
        val c = Offset(px(b.x), py(b.y))
        val col = fractionColor(b.fraction)
        drawCircle(col, dotR, c)
        drawCircle(darkOf(col), dotR, c, style = Stroke(width = max(dotR * 0.25f, 1f)))
        if (UnitLabel.visible(b.value)) {
            drawLabel(
                paint, b.value.toString(), c.x, c.y - dotR - unitLabelSize * 0.75f,
                unitLabelSize, IconFill.toArgb(), halo = BackgroundColor.toArgb()
            )
        }
    }

    // selection / touch feedback
    if (sel != null) {
        val c = Offset(px(sel.x), py(sel.y))
        val effect = if (s.input.halved) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null
        drawCircle(
            IconFill, baseR * 1.8f, c,
            style = Stroke(width = max(thick * 0.6f, 2f), pathEffect = effect)
        )
        if (s.input.halved) drawLabel(paint, "1/2", c.x, c.y - baseR * 2.6f, baseR * 0.8f, IconFill.toArgb())
    }
    s.input.highlight?.let { h ->
        drawCircle(
            IconFill, baseR * 2.05f, Offset(px(h.x), py(h.y)),
            alpha = 0.55f, style = Stroke(width = max(thick * 0.5f, 1.5f))
        )
    }
    s.input.swipeFrom?.let { from ->
        drawLine(
            IconFill,
            Offset(px(from.x), py(from.y)),
            Offset(px(s.input.swipeX), py(s.input.swipeY)),
            strokeWidth = max(thick * 0.5f, 1.5f), alpha = 0.6f
        )
    }

    // particles: saturated dots for hits/deaths/captures, short light dashes for link recolors
    for (p in s.particles.particles) {
        val c = Offset(px(p.x), py(p.y))
        if (p.spark) {
            val half = (0.007 * scale).toFloat()
            val dx = cos(p.angle).toFloat() * half
            val dy = sin(p.angle).toFloat() * half
            drawLine(
                SparkColor, Offset(c.x - dx, c.y - dy), Offset(c.x + dx, c.y + dy),
                strokeWidth = max(thick * 0.3f, 1.5f), alpha = p.alpha.toFloat()
            )
        } else {
            drawCircle(
                Color(p.color), radius = max((p.radius * scale).toFloat(), 0.5f),
                center = c, alpha = p.alpha.toFloat()
            )
        }
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
internal fun DrawScope.drawLabel(
    paint: Paint, text: String, x: Float, y: Float, size: Float, argb: Int, halo: Int? = null
) {
    paint.textSize = size
    val canvas = drawContext.canvas.nativeCanvas
    if (halo != null) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size * 0.22f
        paint.color = halo
        canvas.drawText(text, x, y + size * 0.35f, paint)
        paint.style = Paint.Style.FILL
    }
    paint.color = argb
    canvas.drawText(text, x, y + size * 0.35f, paint)
}
