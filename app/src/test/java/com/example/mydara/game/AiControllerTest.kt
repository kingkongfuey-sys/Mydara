package com.example.mydara.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    // ------------------------------------------------------------------ tactics differ, planet types do not

    @Test
    fun difficultiesDifferOnlyInTactics() {
        val t = Difficulty.values().map { it.tactics }
        assertEquals(1, Difficulty.EASY.tactics.actionsPerSecond)
        assertEquals(2, Difficulty.NORMAL.tactics.actionsPerSecond)
        assertEquals(2, Difficulty.HARD.tactics.actionsPerSecond)
        assertTrue(Difficulty.EASY.tactics.attackExtra > Difficulty.NORMAL.tactics.attackExtra)
        assertFalse(Difficulty.EASY.tactics.reinforce)
        assertTrue(Difficulty.NORMAL.tactics.reinforce && Difficulty.HARD.tactics.reinforce)
        assertTrue(Difficulty.HARD.tactics.coordinated && !Difficulty.NORMAL.tactics.coordinated)
        assertTrue(t.map { it.economicCap }.toSet().size == 3)
    }

    @Test
    fun everyDifficultyBuildsEconomicPlanets() {
        for (diff in Difficulty.values()) {
            val (m, ps) = greenChain(6)
            ps[2].units = 20.0
            val ai = AiController(diff, Fraction.GREEN, m)
            stepWithAi(m, listOf(ai), 60)
            assertEquals("$diff", 1, ps.count { it.type == PlanetType.ECONOMIC }) // cap min(cap, 6/6) = 1
            assertEquals(1, ai.upgradesPerformed)
        }
    }

    @Test
    fun everyDifficultyBuildsDefensivePlanetsWhenThreatened() {
        for (diff in Difficulty.values()) {
            val m = GameModel(1L)
            val a = m.addPlanet(0.4, 0.4, Fraction.GREEN)
            val e1 = m.addPlanet(0.6, 0.4, Fraction.RED)
            val e2 = m.addPlanet(0.2, 0.4, Fraction.RED)
            m.addLink(a, e1); m.addLink(a, e2)
            a.units = 20.0; e1.units = 15.0; e2.units = 15.0
            val ai = AiController(diff, Fraction.GREEN, m)
            stepWithAi(m, listOf(ai), 12)
            assertEquals("$diff", PlanetType.DEFENSIVE, a.type)
        }
    }

    @Test
    fun defenseThresholdDiffersByDifficulty() {
        // one weak, heavily shielded enemy neighbor: only the Easy AI wastes a defensive upgrade on it
        for ((diff, expected) in listOf(
            Difficulty.EASY to PlanetType.DEFENSIVE,
            Difficulty.NORMAL to PlanetType.NORMAL,
            Difficulty.HARD to PlanetType.NORMAL
        )) {
            val m = GameModel(1L)
            val a = m.addPlanet(0.4, 0.4, Fraction.GREEN)
            val e = m.addPlanet(0.6, 0.4, Fraction.RED, PlanetType.DEFENSIVE) // fullHp 17: out of reach
            m.addLink(a, e)
            a.units = 20.0
            val ai = AiController(diff, Fraction.GREEN, m)
            stepWithAi(m, listOf(ai), 12)
            assertEquals("$diff", expected, a.type)
        }
    }

    // ------------------------------------------------------------------ attacking

    @Test
    fun attacksNeutralOnlyWhenAffordable() {
        for ((units, expectAttack) in listOf(9.0 to false, 10.0 to true)) {
            val m = GameModel(1L)
            val a = m.addPlanet(0.2, 0.2, Fraction.GREEN)
            val n = m.addPlanet(0.4, 0.2) // fullHp 10
            m.addLink(a, n)
            m.addPlanet(5.0, 5.0, Fraction.RED)
            a.units = units
            val ai = AiController(Difficulty.NORMAL, Fraction.GREEN, m)
            stepWithAi(m, listOf(ai), 12)
            assertEquals("units=$units", expectAttack, m.batches.isNotEmpty())
        }
    }

    @Test
    fun easyDemandsAMarginBeforeAttacking() {
        for ((units, expectAttack) in listOf(12.0 to false, 13.0 to true)) {
            val m = GameModel(1L)
            val a = m.addPlanet(0.2, 0.2, Fraction.GREEN)
            val n = m.addPlanet(0.4, 0.2) // fullHp 10 + 3 extra
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
            val ai = AiController(Difficulty.NORMAL, Fraction.GREEN, m)
            stepWithAi(m, listOf(ai), 12)
            assertEquals("units=$units", expectAttack, m.batches.isNotEmpty())
        }
    }

    @Test
    fun hardPrefersEnemiesWhileNormalTakesTheCheapestTarget() {
        for ((diff, expectEnemy) in listOf(Difficulty.NORMAL to false, Difficulty.HARD to true)) {
            val m = GameModel(1L)
            val a = m.addPlanet(0.4, 0.4, Fraction.GREEN)
            val n = m.addPlanet(0.6, 0.4)                 // neutral, fullHp 10 (lower id: wins the tie)
            val e = m.addPlanet(0.2, 0.4, Fraction.RED)   // enemy, fullHp 10, needs 15
            m.addLink(a, n); m.addLink(a, e)
            a.units = 20.0
            val ai = AiController(diff, Fraction.GREEN, m)
            stepWithAi(m, listOf(ai), 12)
            assertEquals("$diff", if (expectEnemy) e else n, m.batches.single().to)
        }
    }

    @Test
    fun coordinatedAttackOnlyOnHard() {
        for ((diff, expectedBatches) in listOf(Difficulty.EASY to 0, Difficulty.NORMAL to 0, Difficulty.HARD to 2)) {
            val m = GameModel(1L)
            val a = m.addPlanet(0.2, 0.2, Fraction.GREEN)
            val b = m.addPlanet(0.6, 0.2, Fraction.GREEN)
            val t = m.addPlanet(0.4, 0.2, Fraction.RED) // fullHp 10: single needs 15, combined needs 18
            m.addLink(a, t); m.addLink(b, t)
            a.units = 10.0; b.units = 10.0
            val ai = AiController(diff, Fraction.GREEN, m)
            stepWithAi(m, listOf(ai), 10)
            assertEquals("$diff", expectedBatches, m.batches.size)
        }
    }

    @Test
    fun safePlanetFeedsFrontlineViaAutoTargetExceptOnEasy() {
        fun scenario(): Triple<GameModel, Planet, Planet> {
            val m = GameModel(1L)
            val back = m.addPlanet(0.2, 0.2, Fraction.GREEN)
            val front = m.addPlanet(0.4, 0.2, Fraction.GREEN)
            val enemy = m.addPlanet(0.6, 0.2, Fraction.RED)
            m.addLink(back, front); m.addLink(front, enemy)
            enemy.units = 30.0
            return Triple(m, back, front)
        }
        val (m1, back1, _) = scenario()
        stepWithAi(m1, listOf(AiController(Difficulty.EASY, Fraction.GREEN, m1)), 20)
        assertNull(back1.autoTarget)

        val (m2, back2, front2) = scenario()
        stepWithAi(m2, listOf(AiController(Difficulty.NORMAL, Fraction.GREEN, m2)), 20)
        assertTrue(back2.autoTarget === front2)
    }

    // ------------------------------------------------------------------ whole matches

    @Test
    fun fullMatchObeysLegalSendsAndEachDifficultysActionBudget() {
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
                val limit = ai.difficulty.tactics.actionsPerSecond
                assertTrue("budget exceeded: ${perWindow.values.maxOrNull()} > $limit", perWindow.values.all { it <= limit })
            }
        }
    }

    @Test
    fun forOpponentsCreatesOneControllerPerAiFraction() {
        val m = MapGenerator.generate(3L, MapSize.LARGE, 3)
        val ais = AiController.forOpponents(m, Difficulty.NORMAL)
        assertEquals(listOf(Fraction.RED, Fraction.YELLOW, Fraction.CYAN), ais.map { it.fraction })
        assertFalse(ais.any { it.fraction == Fraction.PLAYER })
    }

    // ------------------------------------------------------------------ fairness

    /**
     * GREEN is the AI. RED (the "player") sits two links beyond GREEN's frontier. The two worlds differ only in
     * things the AI must not be able to see: RED's garrisons and RED's auto-target. The AI has to behave
     * identically until it actually takes the neutral planet that links the two sides.
     */
    private fun fairnessWorld(farUnits: Double, withAutoTarget: Boolean): Pair<GameModel, AiController> {
        val m = GameModel(1L)
        m.velocity = 2.0
        val g = (0 until 4).map { m.addPlanet(0.2 * it, 0.2, Fraction.GREEN) }
        for (i in 0 until 3) m.addLink(g[i], g[i + 1])
        val n = m.addPlanet(0.8, 0.2)
        m.addLink(g[3], n)
        val r1 = m.addPlanet(1.0, 0.2, Fraction.RED)
        val r2 = m.addPlanet(1.2, 0.2, Fraction.RED)
        m.addLink(n, r1); m.addLink(r1, r2)
        r1.units = farUnits; r2.units = farUnits
        if (withAutoTarget) m.setAutoTarget(r1, r2) // RED shuffles units around out of the AI's sight
        return m to AiController(Difficulty.HARD, Fraction.GREEN, m)
    }

    @Test
    fun aiDoesNotReactToThingsOutsideItsView() {
        val (mA, aiA) = fairnessWorld(5.0, withAutoTarget = false)
        val (mB, aiB) = fairnessWorld(90.0, withAutoTarget = true)
        val neutralA = mA.planets[4]
        val neutralB = mB.planets[4]
        var frames = 0
        while (neutralA.fraction.isNeutral && neutralB.fraction.isNeutral && frames < 6000) {
            mA.step(); aiA.update(); mB.step(); aiB.update(); frames++
        }
        assertTrue("AI never expanded", frames < 6000)
        assertEquals(neutralA.fraction, neutralB.fraction) // took the neutral planet on the same frame
        assertEquals(aiA.actionFrames, aiB.actionFrames)
        for (i in 0 until 4) assertEquals(mA.planets[i].units, mB.planets[i].units, 1e-9)
    }
}
