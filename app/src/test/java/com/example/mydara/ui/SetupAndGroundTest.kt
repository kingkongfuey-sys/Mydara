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
        assertEquals(MapSize.MEDIUM, s.size)
        assertEquals(Difficulty.NORMAL, s.difficulty)
    }

    @Test
    fun setupBecomesGameConfigWithARandomSeed() {
        val g = SetupConfig(MapSize.HUGE, 3, Difficulty.HARD, true).toGameConfig()
        assertEquals(MapSize.HUGE, g.size)
        assertEquals(3, g.opponents)
        assertEquals(Difficulty.HARD, g.difficulty)
        assertTrue(g.fogOfWar)
        assertNull(g.seed) // chosen when the match is created, never typed in
        assertEquals(3, SetupConfig(opponents = 9).toGameConfig().opponents)
        assertEquals(1, SetupConfig(opponents = 0).toGameConfig().opponents)
    }

    @Test
    fun matchesGetDifferentRandomMapsUnlessRestarted() {
        val cfg = SetupConfig(MapSize.LARGE, 2, Difficulty.NORMAL, false).toGameConfig()
        val seeds = (1..5).map { GameSession(cfg).seed }.toSet()
        assertTrue(seeds.size > 1)
        // Restart = same settings and the same seed: identical map
        val a = GameSession(cfg)
        val restarted = GameSession(cfg.copy(seed = a.seed))
        assertEquals(a.model.planets.map { it.x }, restarted.model.planets.map { it.x })
        assertEquals(a.model.planets.map { it.fraction }, restarted.model.planets.map { it.fraction })
        assertEquals(30, a.model.planets.size)
        // Regenerate = same settings, new seed: a different map
        val regenerated = GameSession(cfg.copy(seed = null))
        assertTrue(regenerated.seed != a.seed || regenerated.model.planets.map { it.x } != a.model.planets.map { it.x })
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
