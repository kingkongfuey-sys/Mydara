package com.example.mydara.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiControllerTest {

    private fun stepWithAi(m: GameModel, ais: List<AiController>, frames: Int) {
        repeat(frames) { m.step(); ais.forEach { it.update() } }
    }

    /** Chain of n linked GREEN planets plus an unlinked RED planet (so the match has two fractions). */
    private fun greenChain(n: Int): Pair<GameModel, List<Planet>> {
        val m = GameModel(1L)
        val ps = (0 until n).map { m.addPlanet(0.2 * it, 0.2, Fraction.GREEN) }
        for (i in 0 until n - 1) m.addLink(ps[i], ps[i + 1])
        m.addPlanet(5.0, 5.0, Fraction.RED)
        return m to ps
    }

    @Test
    fun attacksNeutralOnlyWhenAffordable() {
        for ((units, expectAttack) in listOf(9.0 to false, 10.0 to true)) {
            val m = GameModel(1L)
            val a = m.addPlanet(0.2, 0.2, Fraction.GREEN)
            val n = m.addPlanet(0.4, 0.2) // fullHp 10
            m.addLink(a, n)
            m.addPlanet(5.0, 5.0, Fraction.RED)
            a.units = units
            val ai = AiController(Difficulty.EASY, Fraction.GREEN, m)
            stepWithAi(m, listOf(ai), 12)
            assertEquals("units=$units", expectAttack, m.batches.isNotEmpty())
        }
    }

    @Test
    fun enemyAttackNeedsFiveExtra() {
        for ((units, expectAttack) in listOf(14.0 to false, 15.0 to true)) {
            val m = GameModel(1L)
            val a = m.addPlanet(0.2, 0.2, Fraction.GREEN)
            val e = m.addPlanet(0.4, 0.2, Fraction.RED) // 3 + 7 = 10, need 15
            m.addLink(a, e)
            a.units = units
            val ai = AiController(Difficulty.EASY, Fraction.GREEN, m)
            stepWithAi(m, listOf(ai), 12)
            assertEquals("units=$units", expectAttack, m.batches.isNotEmpty())
        }
    }

    @Test
    fun easyNeverUpgradesButNormalAndHardDo() {
        for ((diff, expected) in listOf(Difficulty.EASY to 0, Difficulty.NORMAL to 1, Difficulty.HARD to 1)) {
            val (m, ps) = greenChain(6)
            ps[2].units = 20.0
            ps[3].units = 20.0
            val ai = AiController(diff, Fraction.GREEN, m)
            stepWithAi(m, listOf(ai), 60)
            // cap = min(2, 6/6) = 1 economic planet
            assertEquals("$diff", expected, ps.count { it.type == PlanetType.ECONOMIC })
            assertEquals(expected, ai.upgradesPerformed)
        }
    }

    @Test
    fun hardBuysDefensiveOnThreatenedPlanetNormalDoesNot() {
        for ((diff, expected) in listOf(Difficulty.NORMAL to PlanetType.NORMAL, Difficulty.HARD to PlanetType.DEFENSIVE)) {
            val m = GameModel(1L)
            val a = m.addPlanet(0.4, 0.4, Fraction.GREEN)
            val e1 = m.addPlanet(0.6, 0.4, Fraction.RED)
            val e2 = m.addPlanet(0.2, 0.4, Fraction.RED)
            m.addLink(a, e1); m.addLink(a, e2)
            a.units = 20.0; e1.units = 15.0; e2.units = 15.0
            val ai = AiController(diff, Fraction.GREEN, m)
            stepWithAi(m, listOf(ai), 12)
            assertEquals("$diff", expected, a.type)
        }
    }

    @Test
    fun coordinatedAttackOnlyOnHard() {
        for ((diff, expectedBatches) in listOf(Difficulty.NORMAL to 0, Difficulty.HARD to 2)) {
            val m = GameModel(1L)
            val a = m.addPlanet(0.2, 0.2, Fraction.GREEN)
            val b = m.addPlanet(0.6, 0.2, Fraction.GREEN)
            val t = m.addPlanet(0.4, 0.2, Fraction.RED) // fullHp 10: single needs 18, combined needs 21
            m.addLink(a, t); m.addLink(b, t)
            a.units = 10.0; b.units = 10.0
            val ai = AiController(diff, Fraction.GREEN, m)
            stepWithAi(m, listOf(ai), 10)
            assertEquals("$diff", expectedBatches, m.batches.size)
        }
    }

    @Test
    fun safePlanetFeedsFrontlineViaAutoTarget() {
        val m = GameModel(1L)
        val back = m.addPlanet(0.2, 0.2, Fraction.GREEN)
        val front = m.addPlanet(0.4, 0.2, Fraction.GREEN)
        val enemy = m.addPlanet(0.6, 0.2, Fraction.RED)
        m.addLink(back, front); m.addLink(front, enemy)
        enemy.units = 30.0
        val easy = AiController(Difficulty.EASY, Fraction.GREEN, m)
        stepWithAi(m, listOf(easy), 20)
        assertTrue(back.autoTarget == null) // Easy never auto-targets

        val m2 = GameModel(1L)
        val b2 = m2.addPlanet(0.2, 0.2, Fraction.GREEN)
        val f2 = m2.addPlanet(0.4, 0.2, Fraction.GREEN)
        val e2 = m2.addPlanet(0.6, 0.2, Fraction.RED)
        m2.addLink(b2, f2); m2.addLink(f2, e2)
        e2.units = 30.0
        val normal = AiController(Difficulty.NORMAL, Fraction.GREEN, m2)
        stepWithAi(m2, listOf(normal), 20)
        assertTrue(b2.autoTarget === f2)
    }

    @Test
    fun fullMatchObeysLegalSendsAndActionBudget() {
        val setups = listOf(
            Triple(Difficulty.HARD, Difficulty.NORMAL, 11L),
            Triple(Difficulty.NORMAL, Difficulty.EASY, 22L),
            Triple(Difficulty.HARD, Difficulty.HARD, 33L)
        )
        for ((d1, d2, seed) in setups) {
            val m = MapGenerator.generate(seed, MapSize.MEDIUM, 1)
            m.velocity = 2.0
            val ais = listOf(
                AiController(d1, Fraction.GREEN, m),
                AiController(d2, Fraction.RED, m)
            )
            val seen = HashSet<Int>()
            var frames = 0
            while (!m.isOver && frames < 9000) {
                m.step(); ais.forEach { it.update() }; frames++
                for (b in m.batches) if (seen.add(b.id)) {
                    assertTrue(b.value >= 1)
                    assertEquals(b.from.fraction, b.fraction)
                    assertTrue("send without link", b.from.isLinkedTo(b.to))
                }
                for (p in m.planets) assertTrue(p.units >= -1e-9 && p.defense >= -1e-9)
            }
            assertTrue(seen.isNotEmpty())
            for (ai in ais) {
                val perWindow = ai.actionFrames.groupingBy { it / 60 }.eachCount()
                assertTrue("budget exceeded: ${perWindow.values.maxOrNull()}", perWindow.values.all { it <= 2 })
            }
        }
    }

    @Test
    fun easyAiNeverOwnsUpgradedPlanets() {
        val m = MapGenerator.generate(5L, MapSize.LARGE, 1)
        m.velocity = 2.0
        val easy = AiController(Difficulty.EASY, Fraction.GREEN, m)
        val hard = AiController(Difficulty.HARD, Fraction.RED, m)
        var frames = 0
        while (!m.isOver && frames < 9000) {
            m.step(); easy.update(); hard.update(); frames++
            assertTrue(m.planetsOf(Fraction.GREEN).all { it.type == PlanetType.NORMAL })
        }
        assertEquals(0, easy.upgradesPerformed)
    }

    @Test
    fun forOpponentsCreatesOneControllerPerAiFraction() {
        val m = MapGenerator.generate(3L, MapSize.LARGE, 3)
        val ais = AiController.forOpponents(m, Difficulty.NORMAL)
        assertEquals(listOf(Fraction.RED, Fraction.YELLOW, Fraction.CYAN), ais.map { it.fraction })
        assertFalse(ais.any { it.fraction == Fraction.PLAYER })
    }
}
