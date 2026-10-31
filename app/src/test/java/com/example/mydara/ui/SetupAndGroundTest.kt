package com.example.mydara.ui

import com.example.mydara.game.Difficulty
import com.example.mydara.game.MapSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupAndGroundTest {

    @Test
    fun setupDefaultsMatchTheSpec() {
        val s = SetupConfig()
        assertFalse(s.fogOfWar)           // fog of war defaults to off
        assertEquals(1, s.opponents)
        assertNull(s.seed())              // empty seed = random
        assertEquals(MapSize.MEDIUM, s.size)
        assertEquals(Difficulty.NORMAL, s.difficulty)
    }

    @Test
    fun seedFieldKeepsDigitsOnlyAndLimitsLength() {
        assertEquals("1234", SetupConfig.sanitizeSeed("12a3-4"))
        assertEquals("", SetupConfig.sanitizeSeed("abc"))
        assertEquals(SetupConfig.MAX_SEED_DIGITS, SetupConfig.sanitizeSeed("1234567890123").length)
        assertEquals(42L, SetupConfig(seedText = "42").seed())
        assertNull(SetupConfig(seedText = "").seed())
    }

    @Test
    fun setupBecomesGameConfig() {
        val g = SetupConfig(MapSize.HUGE, 3, Difficulty.HARD, true, "777").toGameConfig()
        assertEquals(MapSize.HUGE, g.size)
        assertEquals(3, g.opponents)
        assertEquals(Difficulty.HARD, g.difficulty)
        assertTrue(g.fogOfWar)
        assertEquals(777L, g.seed)
        assertEquals(3, SetupConfig(opponents = 9).toGameConfig().opponents)
        assertEquals(1, SetupConfig(opponents = 0).toGameConfig().opponents)
    }

    @Test
    fun sameSeedFromSetupGivesSameMatch() {
        val cfg = SetupConfig(MapSize.LARGE, 2, Difficulty.NORMAL, false, "4242").toGameConfig()
        val a = GameSession(cfg)
        val b = GameSession(cfg)
        assertEquals(4242L, a.seed)
        assertEquals(a.model.planets.map { it.x }, b.model.planets.map { it.x })
        assertEquals(a.model.planets.map { it.type }, b.model.planets.map { it.type })
        assertEquals(22, a.model.planets.size)
    }

    @Test
    fun groundCellsDriftUpwardAndRespawnForever() {
        val f = GroundField(seed = 3L, count = 14)
        assertEquals(14, f.cells.size)
        val before = f.cells.map { it.y }
        f.step(1.0)
        // every cell moved up (none can respawn within one second from the initial range)
        assertTrue(f.cells.indices.all { f.cells[it].y < before[it] })
        repeat(3000) { f.step(0.1) } // five minutes of drifting
        assertEquals(14, f.cells.size)
        for (c in f.cells) {
            assertTrue("y=${c.y}", c.y in GroundField.DESPAWN_Y..1.25)
            assertTrue(c.size in GroundField.MIN_SIZE..GroundField.MAX_SIZE)
            assertTrue(c.alpha in 0.05f..0.3f)
            assertTrue(c.shade in 0..2)
        }
    }

    @Test
    fun groundIsDeterministicPerSeed() {
        val a = GroundField(seed = 9L); val b = GroundField(seed = 9L)
        repeat(100) { a.step(0.1); b.step(0.1) }
        assertEquals(a.cells.map { it.y }, b.cells.map { it.y })
        assertEquals(a.cells.map { it.x }, b.cells.map { it.x })
    }
}
