package com.example.mydara.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameModelTest {

    /** Two planets 0.2 mapWidths apart, linked. velocity 1 for easy math. */
    private fun pair(
        fa: Fraction = Fraction.GREEN,
        fb: Fraction = Fraction.NEUTRAL,
        tb: PlanetType = PlanetType.NORMAL
    ): Triple<GameModel, Planet, Planet> {
        val m = GameModel(seed = 1L)
        m.velocity = 1.0
        val a = m.addPlanet(0.3, 0.3, fa)
        val b = m.addPlanet(0.5, 0.3, fb, tb)
        m.addLink(a, b)
        return Triple(m, a, b)
    }

    private fun run(m: GameModel, frames: Int) = repeat(frames) { m.step() }

    private fun runUntilIdle(m: GameModel, maxFrames: Int = 1000) {
        var n = 0
        while (m.batches.isNotEmpty() && n++ < maxFrames) m.step()
    }

    @Test
    fun startsWithThreeUnitsAndFullDefense() {
        val m = GameModel(1L)
        val own = m.addPlanet(0.1, 0.1, Fraction.GREEN)
        val neutral = m.addPlanet(0.3, 0.1)
        val def = m.addPlanet(0.5, 0.1, Fraction.NEUTRAL, PlanetType.DEFENSIVE)
        val eco = m.addPlanet(0.7, 0.1, Fraction.NEUTRAL, PlanetType.ECONOMIC)
        assertEquals(3.0, own.units, 0.0)
        assertEquals(3.0, neutral.units, 0.0)
        assertEquals(7.0, own.defense, 0.0)
        assertEquals(14.0, def.defense, 0.0)
        assertEquals(0.0, eco.defense, 0.0)
    }

    @Test
    fun captureMathWithDefense() {
        // value <= defense: only defense drops (7 defense, 3 garrison = 10 to take)
        var (m, a, b) = pair()
        a.units = 5.0
        m.send(a, b); runUntilIdle(m)
        assertEquals(2.0, b.defense, 1e-9)
        assertEquals(3.0, b.units, 1e-9)
        assertEquals(Fraction.NEUTRAL, b.fraction)

        // defense < value < defense + garrison: defense -> 0, garrison reduced by the rest
        val t2 = pair(); m = t2.first; a = t2.second; b = t2.third
        a.units = 8.0
        m.send(a, b); runUntilIdle(m)
        assertEquals(0.0, b.defense, 1e-9)
        assertEquals(2.0, b.units, 1e-9)
        assertEquals(Fraction.NEUTRAL, b.fraction)

        // value >= defense + garrison (10): capture, garrison = value - 10, defense resets to 7
        val t3 = pair(); m = t3.first; a = t3.second; b = t3.third
        a.units = 12.0
        m.send(a, b); runUntilIdle(m)
        assertEquals(Fraction.GREEN, b.fraction)
        assertEquals(PlanetType.NORMAL, b.type)
        assertEquals(2.0, b.units, 1e-9)
        assertEquals(7.0, b.defense, 1e-9)
    }

    @Test
    fun exactlyTenUnitsTakeAFreshNeutralPlanet() {
        val (m, a, b) = pair()
        a.units = 10.0
        m.send(a, b); runUntilIdle(m)
        assertEquals(Fraction.GREEN, b.fraction)
        assertEquals(0.0, b.units, 1e-9)

        val (m2, a2, b2) = pair()
        a2.units = 9.0
        m2.send(a2, b2); runUntilIdle(m2)
        assertEquals(Fraction.NEUTRAL, b2.fraction) // 9 is one short
    }

    @Test
    fun captureResetsTypeAndExactTieLeavesZero() {
        val (m, a, b) = pair(tb = PlanetType.DEFENSIVE) // defense 14 + garrison 3 = 17
        a.units = 17.0
        m.send(a, b); runUntilIdle(m)
        assertEquals(Fraction.GREEN, b.fraction)
        assertEquals(PlanetType.NORMAL, b.type)
        assertEquals(0.0, b.units, 1e-9)
    }

    @Test
    fun equalBatchesAnnihilate() {
        val (m, a, b) = pair(Fraction.GREEN, Fraction.RED)
        a.units = 6.0; b.units = 6.0
        assertTrue(m.send(a, b)); assertTrue(m.send(b, a))
        assertEquals(2, m.batches.size)
        runUntilIdle(m)
        assertTrue(m.batches.isEmpty())
        assertTrue(a.units < 2.0) // nothing was delivered, only a little growth
        assertTrue(b.units < 2.0)
        assertEquals(Fraction.GREEN, a.fraction)
        assertEquals(Fraction.RED, b.fraction)
    }

    @Test
    fun largerBatchSurvivesWithDifference() {
        val (m, a, b) = pair(Fraction.GREEN, Fraction.RED)
        a.units = 9.0; b.units = 4.0
        m.send(a, b); m.send(b, a)
        // run until the red batch is gone, green survivor should carry 5
        var n = 0
        while (m.batches.size > 1 && n++ < 500) m.step()
        assertEquals(1, m.batches.size)
        assertEquals(Fraction.GREEN, m.batches[0].fraction)
        assertEquals(5, m.batches[0].value)
    }

    @Test
    fun friendlyOppositeBatchesDoNotFight() {
        val (m, a, b) = pair(Fraction.GREEN, Fraction.GREEN)
        a.units = 4.0; b.units = 4.0
        m.send(a, b); m.send(b, a)
        runUntilIdle(m)
        assertTrue(a.units in 4.0..7.0) // received the 4 sent by b (+ a little growth)
        assertTrue(b.units in 4.0..7.0)
    }

    @Test
    fun economicGrowsTwiceAsFast() {
        val m = GameModel(1L); m.velocity = 1.0
        val normal = m.addPlanet(0.1, 0.1, Fraction.GREEN)
        val eco = m.addPlanet(0.5, 0.1, Fraction.RED, PlanetType.ECONOMIC)
        run(m, 600) // 10 seconds
        assertEquals(13.0, normal.units, 1e-6)
        assertEquals(23.0, eco.units, 1e-6)
    }

    @Test
    fun neutralNeverGrows() {
        val m = GameModel(1L); m.velocity = 1.0
        m.addPlanet(0.1, 0.1, Fraction.GREEN)
        val n = m.addPlanet(0.5, 0.1)
        run(m, 600)
        assertEquals(3.0, n.units, 0.0)
        assertEquals(7.0, n.defense, 0.0)
    }

    @Test
    fun halvedSendRoundsDown() {
        val (m, a, b) = pair(Fraction.GREEN, Fraction.GREEN)
        a.units = 7.9
        assertTrue(m.send(a, b, half = true))
        assertEquals(3, m.batches.single().value)
        assertEquals(4.9, a.units, 1e-9)

        a.units = 1.0
        assertFalse(m.send(a, b, half = true)) // half of 1 = 0
    }

    @Test
    fun fullSendUsesWholeUnitsAndKeepsFraction() {
        val (m, a, b) = pair(Fraction.GREEN, Fraction.GREEN)
        a.units = 7.9
        m.send(a, b)
        assertEquals(7, m.batches.single().value)
        assertEquals(0.9, a.units, 1e-9)
    }

    @Test
    fun cannotSendWithoutLinkOrFromNeutral() {
        val m = GameModel(1L)
        val a = m.addPlanet(0.1, 0.1, Fraction.GREEN)
        val c = m.addPlanet(0.9, 0.1)
        assertFalse(m.send(a, c))
        m.addLink(a, c)
        assertFalse(m.send(c, a)) // neutral source
        assertTrue(m.send(a, c))
    }

    @Test
    fun travelTimeMatchesSpec() {
        val (m, a, b) = pair(Fraction.GREEN, Fraction.GREEN)
        a.units = 5.0
        m.send(a, b)
        var frames = 0
        while (m.batches.isNotEmpty()) { m.step(); frames++ }
        assertTrue("took $frames frames", frames in 110..113) // ~1.85s at 60 fps
    }

    @Test
    fun upgradesCostTenAndRules() {
        val m = GameModel(1L)
        val p = m.addPlanet(0.1, 0.1, Fraction.GREEN)
        p.units = 9.0
        assertFalse(m.upgrade(p, PlanetType.ECONOMIC))
        p.units = 25.0
        assertTrue(m.upgrade(p, PlanetType.DEFENSIVE))
        assertEquals(15.0, p.units, 1e-9)
        assertEquals(14.0, p.defense, 1e-9) // 7 + 10 = 17, capped at the defensive max of 14
        assertFalse(m.upgrade(p, PlanetType.DEFENSIVE)) // already that type
        assertTrue(m.upgrade(p, PlanetType.ECONOMIC))
        assertEquals(5.0, p.units, 1e-9)
    }

    @Test
    fun defenseRegenerates() {
        val (m, _, b) = pair(Fraction.GREEN, Fraction.RED)
        b.defense = 0.0
        run(m, 60) // 1s at velocity 1
        assertEquals(1.0, b.defense, 1e-6)
        val d = m.addPlanet(0.9, 0.9, Fraction.RED, PlanetType.DEFENSIVE)
        d.defense = 0.0
        run(m, 60)
        assertEquals(3.0, d.defense, 1e-6)
    }

    @Test
    fun autoTargetResendsFullGarrison() {
        val (m, a, b) = pair(Fraction.GREEN, Fraction.GREEN)
        a.units = 6.0
        assertTrue(m.setAutoTarget(a, b))
        run(m, 9)
        assertEquals(1, m.batches.size)
        assertEquals(6, m.batches[0].value)
        m.clearAutoTarget(a)
        assertNull(a.autoTarget)
    }

    @Test
    fun captureClearsAutoTarget() {
        val (m, a, b) = pair(Fraction.GREEN, Fraction.RED)
        b.units = 3.0
        m.setAutoTarget(b, a)
        a.units = 30.0
        m.send(a, b); runUntilIdle(m)
        assertEquals(Fraction.GREEN, b.fraction)
        assertNull(b.autoTarget)
    }

    @Test
    fun victoryAndDefeat() {
        val (m, a, b) = pair(Fraction.GREEN, Fraction.RED)
        a.units = 40.0
        m.send(a, b); runUntilIdle(m)
        run(m, 60)
        assertTrue(m.isOver)
        assertEquals(Outcome.VICTORY, m.outcome)
        assertEquals(Fraction.GREEN, m.winner)

        val (m2, a2, b2) = pair(Fraction.GREEN, Fraction.RED)
        b2.units = 40.0
        m2.send(b2, a2); runUntilIdle(m2)
        run(m2, 60)
        assertTrue(m2.isOver)
        assertEquals(Outcome.DEFEAT, m2.outcome)
    }

    @Test
    fun pausedModelDoesNotAdvance() {
        val m = GameModel(1L)
        val p = m.addPlanet(0.1, 0.1, Fraction.GREEN)
        m.paused = true
        run(m, 600)
        assertEquals(3.0, p.units, 0.0)
        assertEquals(0L, m.frame)
    }

    @Test
    fun velocityScalesGrowth() {
        val m = GameModel(1L) // default 0.5
        val p = m.addPlanet(0.1, 0.1, Fraction.GREEN)
        run(m, 600)
        assertEquals(8.0, p.units, 1e-6) // 3 + 0.5/s * 10s
    }

    @Test
    fun linkBecomesSolidWhenBothEndsMatch() {
        val (m, a, b) = pair(Fraction.GREEN, Fraction.NEUTRAL)
        val l = a.links.single()
        assertFalse(l.isSolid)
        a.units = 16.0
        m.send(a, b); runUntilIdle(m)
        assertTrue(l.isSolid)
        assertEquals(Fraction.GREEN, l.solidFraction)
        assertTrue(m.drainEvents().any { it is GameEvent.LinkRecolored })
    }

    @Test
    fun shieldRegenerationIsCappedPerType() {
        val m = GameModel(1L); m.velocity = 1.0
        val normal = m.addPlanet(0.1, 0.1, Fraction.GREEN)
        val defensive = m.addPlanet(0.5, 0.1, Fraction.GREEN, PlanetType.DEFENSIVE)
        val eco = m.addPlanet(0.9, 0.1, Fraction.GREEN, PlanetType.ECONOMIC)
        normal.defense = 6.5; defensive.defense = 12.0
        run(m, 600)
        assertEquals(7.0, normal.defense, 1e-9)
        assertEquals(14.0, defensive.defense, 1e-9)
        assertEquals(0.0, eco.defense, 0.0) // economic planets never get a shield
        // defensive regenerates three times as fast
        normal.defense = 0.0; defensive.defense = 0.0
        run(m, 60)
        assertEquals(1.0, normal.defense, 1e-6)
        assertEquals(3.0, defensive.defense, 1e-6)
    }

    @Test
    fun sendUpgradeAndCaptureEmitEvents() {
        val (m, a, b) = pair(Fraction.GREEN, Fraction.RED)
        a.units = 30.0
        m.upgrade(a, PlanetType.ECONOMIC) // costs 10, leaving 20 to send
        m.send(a, b)
        runUntilIdle(m)
        val ev = m.drainEvents()
        val sent = ev.filterIsInstance<GameEvent.UnitsSent>().single()
        assertEquals(Fraction.GREEN, sent.fraction)
        assertFalse(sent.auto)
        assertTrue(ev.any { it is GameEvent.Upgraded && it.type == PlanetType.ECONOMIC })
        val cap = ev.filterIsInstance<GameEvent.PlanetCaptured>().single()
        assertEquals(Fraction.GREEN, cap.newOwner)
        assertEquals(Fraction.RED, cap.oldOwner)
    }

    @Test
    fun autoTargetDispatchIsFlaggedAuto() {
        val (m, a, b) = pair(Fraction.GREEN, Fraction.GREEN)
        a.units = 6.0
        m.setAutoTarget(a, b)
        run(m, 9)
        val sent = m.drainEvents().filterIsInstance<GameEvent.UnitsSent>().single()
        assertTrue(sent.auto)
    }
}
