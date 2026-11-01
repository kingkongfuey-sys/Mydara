package com.example.mydara.ui

import com.example.mydara.game.MapGenerator
import com.example.mydara.game.MapSize
import com.example.mydara.game.Planet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Map framing: centered, fully on screen below the HUD, and every planet reachable, at every size and shape of screen. */
class MapViewTest {

    // width, height in px
    private val screens = listOf(
        1080.0 to 2400.0, 1080.0 to 1920.0, 720.0 to 1280.0, 1440.0 to 3120.0, 800.0 to 1280.0, 1080.0 to 1080.0
    )
    private val top = 200.0
    private val bottom = 80.0

    private fun camera(size: MapSize, seed: Long, w: Double, h: Double): Pair<com.example.mydara.game.GameModel, Camera> {
        val m = MapGenerator.generate(seed, size, (seed % 3).toInt() + 1)
        val cam = Camera(m.mapWidth, m.mapHeight, Camera.maxZoomFor(size), MapGenerator.MAP_MARGIN)
        cam.resize(w, h, top, bottom)
        return m to cam
    }

    @Test
    fun mapStartsCenteredAndFullyInsideTheUsableArea() {
        for (size in MapSize.values()) for (seed in 1L..40L) for ((w, h) in screens) {
            val (m, cam) = camera(size, seed, w, h)
            val r = Planet.RADIUS * cam.scale
            var minX = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE
            var minY = Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
            for (p in m.planets) {
                val sx = cam.toScreenX(p.x); val sy = cam.toScreenY(p.y)
                val msg = "$size seed=$seed ${w.toInt()}x${h.toInt()}"
                assertTrue("$msg left", sx - r >= -0.5)
                assertTrue("$msg right", sx + r <= w + 0.5)
                assertTrue("$msg under HUD", sy - r >= top - 0.5)
                assertTrue("$msg bottom", sy + r <= h - bottom + 0.5)
                minX = minOf(minX, sx); maxX = maxOf(maxX, sx)
                minY = minOf(minY, sy); maxY = maxOf(maxY, sy)
            }
            assertEquals("centered x", w / 2, (minX + maxX) / 2, 0.5)
            assertEquals("centered y", top + (h - top - bottom) / 2, (minY + maxY) / 2, 0.5)
            assertTrue(cam.zoom in cam.minZoom..cam.maxZoomEffective)
        }
    }

    @Test
    fun playfieldIsCroppedToThePlanetsWithAnEvenMargin() {
        for (size in MapSize.values()) for (seed in 1L..40L) {
            val m = MapGenerator.generate(seed, size, 2)
            assertEquals(MapGenerator.MAP_MARGIN, m.planets.minOf { it.x }, 1e-9)
            assertEquals(MapGenerator.MAP_MARGIN, m.planets.minOf { it.y }, 1e-9)
            assertEquals(MapGenerator.MAP_MARGIN, m.mapWidth - m.planets.maxOf { it.x }, 1e-9)
            assertEquals(MapGenerator.MAP_MARGIN, m.mapHeight - m.planets.maxOf { it.y }, 1e-9)
        }
    }

    @Test
    fun anyPlanetCanBeCenteredAtAnyZoomEvenWhenTheWholeMapIsVisible() {
        for (size in MapSize.values()) for (seed in 1L..15L) for ((w, h) in screens) {
            val (m, cam) = camera(size, seed, w, h)
            val cxScreen = w / 2
            val cyScreen = top + (h - top - bottom) / 2
            for (zoomFactor in listOf(1.0, 1.5, 4.0, 1000.0)) { // 1.0 = whole map on screen
                cam.fit()
                cam.zoomAt(zoomFactor, cxScreen, cyScreen)
                for (p in m.planets) {
                    cam.pan(cxScreen - cam.toScreenX(p.x), cyScreen - cam.toScreenY(p.y))
                    val msg = "$size seed=$seed ${w.toInt()}x${h.toInt()} zoom x$zoomFactor"
                    assertEquals("$msg x", cxScreen, cam.toScreenX(p.x), 0.5)
                    assertEquals("$msg y", cyScreen, cam.toScreenY(p.y), 0.5)
                }
            }
        }
    }

    @Test
    fun fringePlanetsStopHalfwayAcrossTheScreen() {
        for (size in MapSize.values()) for (seed in 1L..20L) for ((w, h) in screens) {
            val (m, cam) = camera(size, seed, w, h)
            val cxScreen = w / 2
            val cyScreen = top + (h - top - bottom) / 2
            val msg = "$size seed=$seed ${w.toInt()}x${h.toInt()}"
            // drag the map as far right / down as possible: the leftmost / topmost planet stops at screen centre
            cam.pan(1e7, 1e7)
            assertEquals("$msg left", cxScreen, cam.toScreenX(m.planets.minOf { it.x }), 0.5)
            assertEquals("$msg top", cyScreen, cam.toScreenY(m.planets.minOf { it.y }), 0.5)
            // ...and as far left / up as possible: the rightmost / bottommost planet stops at screen centre
            cam.pan(-1e7, -1e7)
            assertEquals("$msg right", cxScreen, cam.toScreenX(m.planets.maxOf { it.x }), 0.5)
            assertEquals("$msg bottom", cyScreen, cam.toScreenY(m.planets.maxOf { it.y }), 0.5)
        }
    }
}
