package com.example.mydara.ui

import com.example.mydara.game.Fraction
import com.example.mydara.game.GameModel
import com.example.mydara.game.Planet
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

/**
 * Gesture state machine (no Compose imports). The UI forwards raw pointer events; all disambiguation of
 * tap / long-press / auto-target swipe / pan / pinch lives here.
 *
 * Rules: tap own planet = select; tap linked planet with a selection = send all; long-press own planet
 * (500 ms, < 0.05 movement) = halved mode, then tap linked = send half; swipe from own planet toward a
 * linked planet (> 2 radii, released within 900 ms, within 45 degrees) = auto-target; drag from empty
 * space / enemy / neutral planet = pan; two fingers = pinch zoom and block taps and swipes.
 */
class InputController(
    private val model: GameModel,
    private val camera: Camera,
    private val player: Fraction = Fraction.PLAYER,
    private val slopPx: Double = 12.0
) {
    var selected: Planet? = null
        private set
    var halved: Boolean = false
        private set

    /** Planet under the finger (highlight ring at 1.5 radii while down). */
    var highlight: Planet? = null
        private set

    /** Swipe preview: from this planet to the finger (world coordinates). */
    var swipeFrom: Planet? = null
        private set
    var swipeX: Double = 0.0
        private set
    var swipeY: Double = 0.0
        private set

    private enum class Mode { NONE, OWN, PAN, PINCH }

    private var mode = Mode.NONE
    private val pointers = LinkedHashMap<Int, DoubleArray>()
    private var downId = -1
    private var downPx = 0.0
    private var downPy = 0.0
    private var downWx = 0.0
    private var downWy = 0.0
    private var downT = 0L
    private var downPlanet: Planet? = null
    private var moved = false
    private var longFired = false
    private var swiping = false
    private var maxMove = 0.0
    private var pinchDist = 0.0

    // ------------------------------------------------------------------ pointer events

    fun pointerDown(id: Int, x: Double, y: Double, timeMs: Long) {
        pointers[id] = doubleArrayOf(x, y)
        camera.holding = true
        camera.stopInertia()
        if (pointers.size >= 2) {
            enterPinch()
            return
        }
        val wx = camera.toWorldX(x)
        val wy = camera.toWorldY(y)
        val p = hitPlanet(wx, wy)
        downId = id
        downPx = x; downPy = y
        downWx = wx; downWy = wy
        downT = timeMs
        downPlanet = p
        moved = false
        longFired = false
        swiping = false
        maxMove = 0.0
        highlight = p
        mode = if (p != null && p.fraction == player && !model.isOver) Mode.OWN else Mode.PAN
    }

    fun pointerMove(id: Int, x: Double, y: Double, timeMs: Long) {
        val last = pointers[id] ?: return
        val dx = x - last[0]
        val dy = y - last[1]
        last[0] = x
        last[1] = y
        when (mode) {
            Mode.PINCH -> pinchMove(id, dx, dy)
            Mode.OWN -> if (id == downId) ownMove(x, y)
            Mode.PAN -> if (id == downId) panMove(x, y, dx, dy)
            Mode.NONE -> Unit
        }
    }

    fun pointerUp(id: Int, x: Double, y: Double, timeMs: Long) {
        if (pointers.remove(id) == null) return
        when (mode) {
            Mode.PINCH -> {
                when {
                    pointers.isEmpty() -> endGesture()
                    pointers.size == 1 -> {
                        // Keep panning with the remaining finger; never produces a tap.
                        val (rid, pos) = pointers.entries.first()
                        mode = Mode.PAN
                        downId = rid
                        downPx = pos[0]; downPy = pos[1]
                        downPlanet = null
                        moved = true
                        camera.stopInertia()
                    }
                    else -> pinchDist = currentPinchDist()
                }
            }
            Mode.OWN -> if (id == downId) {
                finishOwn(x, y, timeMs)
                endGesture()
            }
            Mode.PAN -> if (id == downId) {
                if (!moved) tapAt(downPlanet)
                endGesture()
            }
            Mode.NONE -> endGesture()
        }
    }

    /** Called every frame: long-press detection and selection validity. */
    fun update(nowMs: Long) {
        val sel = selected
        if (sel != null && sel.fraction != player) deselect()
        if (mode == Mode.OWN && !longFired && !swiping && maxMove < LONG_PRESS_MAX_MOVE &&
            nowMs - downT >= LONG_PRESS_MS && !model.isOver
        ) {
            val p = downPlanet ?: return
            longFired = true
            selected = p
            halved = true
        }
    }

    // ------------------------------------------------------------------ gesture handling

    private fun enterPinch() {
        mode = Mode.PINCH
        highlight = null
        swipeFrom = null
        longFired = false
        swiping = false
        pinchDist = currentPinchDist()
    }

    private fun currentPinchDist(): Double {
        val it = pointers.values.iterator()
        val a = it.next()
        val b = it.next()
        return hypot(a[0] - b[0], a[1] - b[1])
    }

    private fun pinchMove(id: Int, dx: Double, dy: Double) {
        if (pointers.size < 2) return
        val keys = pointers.keys.toList()
        if (id != keys[0] && id != keys[1]) return
        val a = pointers.getValue(keys[0])
        val b = pointers.getValue(keys[1])
        val dist = hypot(a[0] - b[0], a[1] - b[1])
        if (pinchDist > 1e-6 && dist > 1e-6) {
            camera.zoomAt(dist / pinchDist, (a[0] + b[0]) / 2, (a[1] + b[1]) / 2)
        }
        pinchDist = dist
        camera.pan(dx / 2, dy / 2) // midpoint follows the fingers
    }

    private fun ownMove(x: Double, y: Double) {
        val p = downPlanet ?: return
        val wx = camera.toWorldX(x)
        val wy = camera.toWorldY(y)
        maxMove = max(maxMove, hypot(wx - downWx, wy - downWy))
        if (longFired) return
        if (!swiping && hypot(wx - p.x, wy - p.y) > 2 * p.radius) swiping = true
        if (swiping) {
            swipeFrom = p
            swipeX = wx
            swipeY = wy
        }
    }

    private fun panMove(x: Double, y: Double, dx: Double, dy: Double) {
        if (!moved && hypot(x - downPx, y - downPy) > slopPx) {
            moved = true
            highlight = null
        }
        if (moved) camera.pan(dx, dy)
    }

    private fun finishOwn(x: Double, y: Double, timeMs: Long) {
        val p = downPlanet ?: return
        when {
            longFired -> Unit // halved mode already armed
            swiping -> if (timeMs - downT <= SWIPE_MAX_MS) {
                trySwipeAutoTarget(p, camera.toWorldX(x), camera.toWorldY(y))
            }
            else -> tapAt(p)
        }
    }

    private fun endGesture() {
        mode = Mode.NONE
        highlight = null
        swipeFrom = null
        downPlanet = null
        if (pointers.isEmpty()) camera.holding = false
    }

    private fun trySwipeAutoTarget(from: Planet, wx: Double, wy: Double) {
        val swipeAngle = atan2(wy - from.y, wx - from.x)
        var best: Planet? = null
        var bestDiff = SWIPE_ANGLE_TOLERANCE + 1e-9
        for (link in from.links) {
            val t = link.other(from)
            var diff = abs(atan2(t.y - from.y, t.x - from.x) - swipeAngle)
            if (diff > PI) diff = 2 * PI - diff
            if (diff < bestDiff) { bestDiff = diff; best = t }
        }
        best?.let { model.setAutoTarget(from, it) }
    }

    private fun tapAt(p: Planet?) {
        val sel = selected
        if (p == null) {
            deselect()
            return
        }
        if (sel != null && p !== sel && sel.isLinkedTo(p)) {
            if (model.send(sel, p, halved)) halved = false
            return
        }
        if (p.fraction == player) {
            when {
                p.autoTarget != null -> { model.clearAutoTarget(p); select(p) }
                sel === p -> deselect()
                else -> select(p)
            }
        }
        // Tapping an unlinked enemy/neutral planet does nothing.
    }

    private fun select(p: Planet) {
        selected = p
        halved = false
    }

    private fun deselect() {
        selected = null
        halved = false
    }

    private fun hitPlanet(wx: Double, wy: Double): Planet? {
        var best: Planet? = null
        var bestD = Double.MAX_VALUE
        for (p in model.planets) {
            val d = hypot(wx - p.x, wy - p.y)
            if (d <= HIT_FACTOR * p.radius && d < bestD) { bestD = d; best = p }
        }
        return best
    }

    companion object {
        const val LONG_PRESS_MS = 500L
        const val LONG_PRESS_MAX_MOVE = 0.05
        const val SWIPE_MAX_MS = 900L
        const val SWIPE_ANGLE_TOLERANCE = PI / 4
        const val HIT_FACTOR = 1.3
    }
}
