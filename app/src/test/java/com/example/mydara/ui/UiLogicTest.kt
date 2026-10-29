package com.example.mydara.ui

import com.example.mydara.game.Fraction
import com.example.mydara.game.GameModel
import com.example.mydara.game.MapSize
import com.example.mydara.game.Outcome
import com.example.mydara.game.Planet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UiLogicTest {

    /** A(green) - B(neutral) - C(red); map 1.0 x 1.6, view 1000 x 1600 (zoom 1.0, center 0.5,0.8). */
    private class Rig {
        val model = GameModel(1L)
        val a = model.addPlanet(0.3, 0.3, Fraction.GREEN)
        val b = model.addPlanet(0.5, 0.3)
        val c = model.addPlanet(0.7, 0.3, Fraction.RED)
        val camera = Camera(1.0, 1.6, 2.1)
        val input: InputController

        init {
            model.addLink(a, b); model.addLink(b, c)
            camera.resize(1000.0, 1600.0)
            input = InputController(model, camera, Fraction.GREEN, 12.0)
            a.units = 7.0
        }

        fun sx(p: Planet) = camera.toScreenX(p.x)
        fun sy(p: Planet) = camera.toScreenY(p.y)
        fun sxw(x: Double) = camera.toScreenX(x)
        fun syw(y: Double) = camera.toScreenY(y)

        fun tap(p: Planet, t: Long) {
            input.pointerDown(1, sx(p), sy(p), t)
            input.pointerUp(1, sx(p), sy(p), t + 60)
        }
    }

    // ------------------------------------------------------------------ camera

    @Test
    fun cameraFitsMapAndRoundTripsCoordinates() {
        val cam = Camera(1.0, 1.6, 2.1)
        cam.resize(1000.0, 2000.0)
        assertEquals(1.0, cam.zoom, 1e-9)
        assertEquals(0.5, cam.cx, 1e-9)
        assertEquals(0.37, cam.toWorldX(cam.toScreenX(0.37)), 1e-9)
        assertEquals(1.21, cam.toWorldY(cam.toScreenY(1.21)), 1e-9)
        val huge = Camera(2.0, 3.2, 6.5)
        huge.resize(1000.0, 2000.0)
        assertEquals(0.5, huge.zoom, 1e-9)
    }

    @Test
    fun zoomIsClampedAndPanStaysInsideMap() {
        val cam = Camera(1.0, 1.6, 2.1)
        cam.resize(1000.0, 1600.0)
        cam.zoomAt(100.0, 500.0, 800.0)
        assertEquals(2.1, cam.zoom, 1e-9)
        cam.zoomAt(0.0001, 500.0, 800.0)
        assertEquals(Camera.MIN_ZOOM, cam.zoom, 1e-9)
        cam.zoomAt(100.0, 500.0, 800.0)
        cam.pan(100000.0, 100000.0)
        val hw = cam.viewW / (2 * cam.scale)
        assertTrue(cam.cx >= hw - 1e-9)
        assertTrue(cam.cy >= cam.viewH / (2 * cam.scale) - 1e-9)
        cam.pan(-1e7, -1e7)
        assertTrue(cam.cx <= 1.0 - hw + 1e-9)
    }

    @Test
    fun zoomKeepsFocalPointFixed() {
        val cam = Camera(2.0, 3.2, 6.5)
        cam.resize(1000.0, 1600.0)
        cam.zoomAt(1.0, 500.0, 800.0)
        val before = cam.toWorldX(300.0)
        cam.zoomAt(2.0, 300.0, 700.0)
        assertEquals(before, cam.toWorldX(300.0), 1e-9)
    }

    @Test
    fun inertiaDecaysAndStopsWhileHeld() {
        val cam = Camera(2.0, 3.2, 6.5)
        cam.resize(1000.0, 1600.0)
        cam.zoomAt(4.0, 500.0, 800.0)
        cam.pan(30.0, 0.0)
        val x0 = cam.cx
        cam.holding = true
        cam.step()
        assertEquals(x0, cam.cx, 0.0)
        cam.holding = false
        repeat(200) { cam.step() }
        assertTrue(cam.cx < x0) // kept drifting
        val x1 = cam.cx
        repeat(50) { cam.step() }
        assertEquals(x1, cam.cx, 1e-3) // came to rest
    }

    // ------------------------------------------------------------------ taps and selection

    @Test
    fun tapSelectsThenDeselects() {
        val r = Rig()
        r.tap(r.a, 0)
        assertTrue(r.input.selected === r.a)
        r.tap(r.a, 200)
        assertNull(r.input.selected)
        r.tap(r.a, 400)
        r.input.pointerDown(1, r.sxw(0.1), r.syw(1.0), 600) // empty space
        r.input.pointerUp(1, r.sxw(0.1), r.syw(1.0), 650)
        assertNull(r.input.selected)
    }

    @Test
    fun tapLinkedPlanetSendsAllAndKeepsSelection() {
        val r = Rig()
        r.tap(r.a, 0)
        r.tap(r.b, 200)
        assertEquals(1, r.model.batches.size)
        assertEquals(7, r.model.batches[0].value)
        assertTrue(r.input.selected === r.a)
    }

    @Test
    fun tapUnlinkedEnemyDoesNothing() {
        val r = Rig()
        r.tap(r.a, 0)
        r.tap(r.c, 200) // A is not linked to C
        assertTrue(r.model.batches.isEmpty())
        assertTrue(r.input.selected === r.a)
    }

    @Test
    fun longPressArmsHalvedSendThenResets() {
        val r = Rig()
        r.input.pointerDown(1, r.sx(r.a), r.sy(r.a), 0)
        r.input.update(499)
        assertNull(r.input.selected)
        r.input.update(500)
        assertTrue(r.input.selected === r.a)
        assertTrue(r.input.halved)
        r.input.pointerUp(1, r.sx(r.a), r.sy(r.a), 650)
        assertTrue(r.input.halved) // release keeps the armed state
        r.tap(r.b, 800)
        assertEquals(3, r.model.batches.single().value) // 7 / 2
        assertFalse(r.input.halved)
    }

    @Test
    fun movingFingerCancelsLongPress() {
        val r = Rig()
        r.input.pointerDown(1, r.sx(r.a), r.sy(r.a), 0)
        r.input.pointerMove(1, r.sx(r.a) + 80, r.sy(r.a), 100) // > 0.05 world units
        r.input.update(600)
        assertNull(r.input.selected)
    }

    // ------------------------------------------------------------------ auto-target swipe

    @Test
    fun quickSwipeTowardLinkedPlanetSetsAutoTarget() {
        val r = Rig()
        r.input.pointerDown(1, r.sx(r.a), r.sy(r.a), 0)
        r.input.pointerMove(1, r.sxw(0.42), r.syw(0.3), 150)
        assertTrue(r.input.swipeFrom === r.a)
        r.input.pointerUp(1, r.sxw(0.42), r.syw(0.3), 300)
        assertTrue(r.a.autoTarget === r.b)
        assertNull(r.input.selected)
    }

    @Test
    fun slowOrOffAngleSwipeDoesNothing() {
        val slow = Rig()
        slow.input.pointerDown(1, slow.sx(slow.a), slow.sy(slow.a), 0)
        slow.input.pointerMove(1, slow.sxw(0.42), slow.syw(0.3), 500)
        slow.input.pointerUp(1, slow.sxw(0.42), slow.syw(0.3), 1000) // > 900 ms
        assertNull(slow.a.autoTarget)

        val off = Rig()
        off.input.pointerDown(1, off.sx(off.a), off.sy(off.a), 0)
        off.input.pointerMove(1, off.sxw(0.3), off.syw(0.45), 100) // straight down, link points right
        off.input.pointerUp(1, off.sxw(0.3), off.syw(0.45), 200)
        assertNull(off.a.autoTarget)
    }

    @Test
    fun tapAutoTargetingPlanetCancelsIt() {
        val r = Rig()
        r.model.setAutoTarget(r.a, r.b)
        r.tap(r.a, 0)
        assertNull(r.a.autoTarget)
        assertTrue(r.input.selected === r.a)
    }

    // ------------------------------------------------------------------ pan / pinch

    @Test
    fun dragOnEmptySpaceOrEnemyPlanetPansWithoutTapping() {
        val r = Rig()
        r.camera.zoomAt(2.0, 500.0, 800.0)
        r.tap(r.a, 0)
        assertTrue(r.input.selected === r.a)
        val cx0 = r.camera.cx
        // drag starting on the enemy planet C
        val x = r.sx(r.c); val y = r.sy(r.c)
        r.input.pointerDown(1, x, y, 300)
        assertTrue(r.input.highlight === r.c)
        r.input.pointerMove(1, x - 100, y, 350)
        r.input.pointerUp(1, x - 100, y, 400)
        assertTrue(r.camera.cx > cx0)
        assertTrue(r.input.selected === r.a) // no tap happened
        assertNull(r.input.highlight)
    }

    @Test
    fun smallWiggleOnEmptySpaceStillCountsAsTap() {
        val r = Rig()
        r.tap(r.a, 0)
        r.input.pointerDown(1, 100.0, 1400.0, 300)
        r.input.pointerMove(1, 104.0, 1402.0, 330) // inside touch slop
        r.input.pointerUp(1, 104.0, 1402.0, 360)
        assertNull(r.input.selected)
    }

    @Test
    fun twoFingerPinchZoomsAndBlocksTaps() {
        val r = Rig()
        val z0 = r.camera.zoom
        r.input.pointerDown(1, r.sx(r.a), r.sy(r.a), 0)       // lands on own planet
        r.input.pointerDown(2, 700.0, 900.0, 40)               // second finger
        assertNull(r.input.highlight)
        r.input.pointerMove(2, 900.0, 900.0, 80)               // spread
        assertTrue(r.camera.zoom > z0)
        r.input.update(1000)                                    // would be a long press otherwise
        assertNull(r.input.selected)
        r.input.pointerUp(2, 900.0, 900.0, 200)
        r.input.pointerUp(1, r.sx(r.a), r.sy(r.a), 220)
        assertNull(r.input.selected) // no tap after pinch
        assertTrue(r.model.batches.isEmpty())
        assertFalse(r.camera.holding)
    }

    @Test
    fun selectionDropsWhenPlanetIsLost() {
        val r = Rig()
        r.tap(r.a, 0)
        r.a.fraction = Fraction.RED
        r.input.update(100)
        assertNull(r.input.selected)
    }

    // ------------------------------------------------------------------ effects, fog, session

    @Test
    fun particlesAreCappedAndDieOut() {
        val ps = ParticleSystem(1L)
        repeat(30) { ps.burst(0.5, 0.5, Fraction.RED.argb) }
        assertEquals(ParticleSystem.MAX_PARTICLES, ps.particles.size)
        repeat(60) { ps.step() }
        assertTrue(ps.particles.isEmpty())
    }

    @Test
    fun starsSpawnByMapSizeAndExpire() {
        val counts = MapSize.values().map { size ->
            val sf = StarField(3L, size.width, size.height, size)
            for (f in 1L..600L) sf.step(f)
            sf.stars.size
        }
        assertTrue(counts[0] < counts[1] && counts[1] < counts[2] && counts[2] < counts[3])
        val sf = StarField(3L, 1.0, 1.6, MapSize.SMALL)
        for (f in 1L..600L) sf.step(f)
        assertTrue(sf.stars.all { it.alpha in 0.0..1.0 && it.scale in 0.0..1.0 })
    }

    @Test
    fun fogShowsOwnPlanetsAndNeighborsOnly() {
        val s = GameSession(GameConfig(size = MapSize.LARGE, fogOfWar = true, seed = 9L))
        s.advance(0.02, 0)
        val own = s.model.planetsOf(Fraction.PLAYER).single()
        assertTrue(s.visible[own.id])
        for (l in own.links) assertTrue(s.visible[l.other(own).id])
        val hidden = s.model.planets.count { !s.visible[it.id] }
        assertTrue(hidden > 0)
        val enemy = s.model.planetsOf(Fraction.RED).single()
        assertFalse(s.visible[enemy.id])
    }

    @Test
    fun sessionRunsFixedStepsAndFeedsParticles() {
        val s = GameSession(GameConfig(size = MapSize.SMALL, seed = 5L))
        s.advance(1.0, 0)
        assertEquals(5L, s.model.frame) // max 5 catch-up steps per call
        repeat(20) { s.advance(1.0 / 60.0, 0) }
        assertTrue(s.model.frame >= 25L)
        // paused: nothing advances
        s.togglePause()
        val f = s.model.frame
        repeat(30) { s.advance(1.0 / 60.0, 0) }
        assertEquals(f, s.model.frame)
        assertEquals(Outcome.PLAYING, s.model.outcome)
    }

    @Test
    fun velocityIndexMatchesSnapValues() {
        val s = GameSession(GameConfig(seed = 1L))
        for (i in GameModel.VELOCITY_STEPS.indices) {
            s.setVelocityIndex(i)
            assertEquals(i, s.velocityIndex())
        }
        s.setVelocityIndex(4)
        assertEquals(0.5, s.model.velocity, 1e-9)
        assertEquals(13, GameModel.VELOCITY_STEPS.size)
    }

    @Test
    fun upgradeButtonsActOnSelectedOwnPlanet() {
        val s = GameSession(GameConfig(size = MapSize.SMALL, seed = 5L))
        val own = s.model.planetsOf(Fraction.PLAYER).single()
        s.camera.resize(1000.0, 1600.0)
        own.units = 30.0
        s.input.pointerDown(1, s.camera.toScreenX(own.x), s.camera.toScreenY(own.y), 0)
        s.input.pointerUp(1, s.camera.toScreenX(own.x), s.camera.toScreenY(own.y), 50)
        assertTrue(s.hudState().canAffordUpgrade)
        s.upgradeSelected(com.example.mydara.game.PlanetType.DEFENSIVE)
        assertEquals(com.example.mydara.game.PlanetType.DEFENSIVE, own.type)
        assertEquals(com.example.mydara.game.PlanetType.DEFENSIVE, s.hudState().selectedType)
    }
}
