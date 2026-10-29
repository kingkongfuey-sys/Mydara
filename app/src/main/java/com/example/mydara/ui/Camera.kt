package com.example.mydara.ui

import kotlin.math.abs
import kotlin.math.min

/**
 * Pan/zoom camera. Zoom 1.0 = one map unit spans the screen width. Pure math, no Android imports.
 */
class Camera(val mapWidth: Double, val mapHeight: Double, val maxZoom: Double) {
    var viewW: Double = 1.0
        private set
    var viewH: Double = 1.0
        private set
    var zoom: Double = 1.0
        private set
    var cx: Double = mapWidth / 2
        private set
    var cy: Double = mapHeight / 2
        private set

    /** Drag inertia in world units per frame. */
    var velX: Double = 0.0
    var velY: Double = 0.0

    /** True while a finger is down; inertia is suspended. */
    var holding: Boolean = false

    private var sized = false

    /** Pixels per world unit. */
    val scale: Double get() = viewW * zoom

    fun resize(w: Double, h: Double) {
        viewW = w
        viewH = h
        if (!sized) {
            sized = true
            fit()
        } else {
            zoom = zoom.coerceIn(MIN_ZOOM, maxZoom)
            clamp()
        }
    }

    /** Fit the whole map on screen (clamped to the zoom limits). */
    fun fit() {
        val s = min(viewW / mapWidth, viewH / mapHeight)
        zoom = (s / viewW).coerceIn(MIN_ZOOM, maxZoom)
        cx = mapWidth / 2
        cy = mapHeight / 2
        clamp()
    }

    fun toScreenX(wx: Double): Double = (wx - cx) * scale + viewW / 2
    fun toScreenY(wy: Double): Double = (wy - cy) * scale + viewH / 2
    fun toWorldX(sx: Double): Double = cx + (sx - viewW / 2) / scale
    fun toWorldY(sy: Double): Double = cy + (sy - viewH / 2) / scale

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
        zoom = (zoom * factor).coerceIn(MIN_ZOOM, maxZoom)
        cx = wx - (focalX - viewW / 2) / scale
        cy = wy - (focalY - viewH / 2) / scale
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

    /** Keeps the view inside the map; a map smaller than the view is centered. */
    private fun clamp() {
        val hw = viewW / (2 * scale)
        val hh = viewH / (2 * scale)
        cx = if (2 * hw >= mapWidth) mapWidth / 2 else cx.coerceIn(hw, mapWidth - hw)
        cy = if (2 * hh >= mapHeight) mapHeight / 2 else cy.coerceIn(hh, mapHeight - hh)
    }

    companion object {
        const val MIN_ZOOM = 0.3
        private const val INERTIA_DECAY = 0.92

        /** Per-size zoom limits mirroring the original: Small 1.7 / Medium 2.1 / Large 6.5 / Huge 6.5. */
        fun maxZoomFor(size: com.example.mydara.game.MapSize): Double = when (size) {
            com.example.mydara.game.MapSize.SMALL -> 1.7
            com.example.mydara.game.MapSize.MEDIUM -> 2.1
            com.example.mydara.game.MapSize.LARGE -> 6.5
            com.example.mydara.game.MapSize.HUGE -> 6.5
        }
    }
}
