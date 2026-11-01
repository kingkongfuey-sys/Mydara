package com.example.mydara.ui

import com.example.mydara.game.MapSize
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Pan/zoom camera. Zoom 1.0 = one map unit spans the screen width. Pure math, no Android imports.
 *
 * The map is shown inside the *usable area*: the view minus [insetTop] (HUD) and [insetBottom]. The map starts
 * fitted and centered in it.
 *
 * Panning is free at every zoom level, including when the whole map is visible: the camera centre may move
 * anywhere between the outermost planets. So any planet, edge planets included, can be brought to the middle
 * of the usable area, but the map can't be dragged further than that (a fringe planet stops halfway across
 * the screen). [panInset] is the distance from the map border to the outermost planet centres.
 */
class Camera(
    val mapWidth: Double,
    val mapHeight: Double,
    val maxZoom: Double,
    val panInset: Double = 0.0
) {
    var viewW: Double = 1.0
        private set
    var viewH: Double = 1.0
        private set
    var insetTop: Double = 0.0
        private set
    var insetBottom: Double = 0.0
        private set
    var zoom: Double = 1.0
        private set
    var cx: Double = mapWidth / 2
        private set
    var cy: Double = mapHeight / 2
        private set

    /** Zoom that fits the whole map into the usable area. */
    var fitZoom: Double = 1.0
        private set

    /** Zoom-out limit: 0.3, or less if the map needs it to fit. */
    var minZoom: Double = MIN_ZOOM
        private set

    /** Zoom-in limit: the per-size limit, but never less than twice the fit zoom. */
    var maxZoomEffective: Double = maxZoom
        private set

    /** Drag inertia in world units per frame. */
    var velX: Double = 0.0
    var velY: Double = 0.0

    /** True while a finger is down; inertia is suspended. */
    var holding: Boolean = false

    private var sized = false

    /** Pixels per world unit. */
    val scale: Double get() = viewW * zoom

    private val usableH: Double get() = max(1.0, viewH - insetTop - insetBottom)
    private val centerSx: Double get() = viewW / 2
    private val centerSy: Double get() = insetTop + usableH / 2

    fun resize(w: Double, h: Double, topInsetPx: Double = 0.0, bottomInsetPx: Double = 0.0) {
        viewW = w
        viewH = h
        insetTop = topInsetPx
        insetBottom = bottomInsetPx
        fitZoom = min(viewW / mapWidth, usableH / mapHeight) / viewW
        minZoom = min(MIN_ZOOM, fitZoom)
        maxZoomEffective = max(maxZoom, fitZoom * 2)
        if (!sized) {
            sized = true
            fit()
        } else {
            zoom = zoom.coerceIn(minZoom, maxZoomEffective)
            clamp()
        }
    }

    /** Fit the whole map into the usable area, centered. */
    fun fit() {
        zoom = fitZoom.coerceIn(minZoom, maxZoomEffective)
        cx = mapWidth / 2
        cy = mapHeight / 2
        clamp()
    }

    fun toScreenX(wx: Double): Double = (wx - cx) * scale + centerSx
    fun toScreenY(wy: Double): Double = (wy - cy) * scale + centerSy
    fun toWorldX(sx: Double): Double = cx + (sx - centerSx) / scale
    fun toWorldY(sy: Double): Double = cy + (sy - centerSy) / scale

    /** Drags the map by a pixel delta and records the velocity for inertia. */
    fun pan(dxPx: Double, dyPx: Double) {
        val dx = -dxPx / scale
        val dy = -dyPx / scale
        cx += dx
        cy += dy
        velX = 0.6 * dx + 0.4 * velX
        velY = 0.6 * dy + 0.4 * velY
        clamp()
    }

    fun zoomAt(factor: Double, focalX: Double, focalY: Double) {
        val wx = toWorldX(focalX)
        val wy = toWorldY(focalY)
        zoom = (zoom * factor).coerceIn(minZoom, maxZoomEffective)
        cx = wx - (focalX - centerSx) / scale
        cy = wy - (focalY - centerSy) / scale
        clamp()
    }

    fun stopInertia() {
        velX = 0.0
        velY = 0.0
    }

    /** One frame of drag inertia. */
    fun step() {
        if (holding) return
        if (abs(velX) + abs(velY) < 1e-6) { velX = 0.0; velY = 0.0; return }
        val ox = cx
        val oy = cy
        cx += velX
        cy += velY
        clamp()
        if (cx == ox) velX = 0.0
        if (cy == oy) velY = 0.0
        velX *= INERTIA_DECAY
        velY *= INERTIA_DECAY
    }

    /** Free panning: the centre stays between the outermost planets (a fringe planet can reach screen centre). */
    private fun clamp() {
        cx = clampAxis(cx, mapWidth)
        cy = clampAxis(cy, mapHeight)
    }

    private fun clampAxis(v: Double, extent: Double): Double {
        val lo = panInset
        val hi = extent - panInset
        return if (lo >= hi) extent / 2 else v.coerceIn(lo, hi)
    }

    companion object {
        const val MIN_ZOOM = 0.3
        private const val INERTIA_DECAY = 0.92

        /** Per-size zoom-in limits: Small 1.7 / Medium 2.1 / Large 6.5 / Huge 6.5. */
        fun maxZoomFor(size: MapSize): Double = when (size) {
            MapSize.SMALL -> 1.7
            MapSize.MEDIUM -> 2.1
            MapSize.LARGE -> 6.5
            MapSize.HUGE -> 6.5
        }
    }
}
