package com.example.mydara.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class MapGeneratorTest {

    private fun starts(m: GameModel) = m.planets.filter { !it.fraction.isNeutral }

    @Test
    fun sameSeedGivesIdenticalMap() {
        for (size in MapSize.values()) {
            val a = MapGenerator.generate(12345L, size, 2)
            val b = MapGenerator.generate(12345L, size, 2)
            assertEquals(a.planets.size, b.planets.size)
            for (i in a.planets.indices) {
                val p = a.planets[i]; val q = b.planets[i]
                assertEquals(p.x, q.x, 0.0); assertEquals(p.y, q.y, 0.0)
                assertEquals(p.fraction, q.fraction); assertEquals(p.type, q.type)
            }
            assertEquals(
                a.links.map { it.a.id to it.b.id },
                b.links.map { it.a.id to it.b.id }
            )
        }
    }

    @Test
    fun differentSeedsGiveDifferentMaps() {
        val a = MapGenerator.generate(1L, MapSize.MEDIUM, 1)
        val b = MapGenerator.generate(2L, MapSize.MEDIUM, 1)
        assertTrue(a.planets.indices.any { a.planets[it].x != b.planets[it].x })
    }

    @Test
    fun planetCountsMatchSizes() {
        assertEquals(10, MapGenerator.generate(1L, MapSize.SMALL, 1).planets.size)
        assertEquals(20, MapGenerator.generate(1L, MapSize.MEDIUM, 1).planets.size)
        assertEquals(30, MapGenerator.generate(1L, MapSize.LARGE, 1).planets.size)
        assertEquals(50, MapGenerator.generate(1L, MapSize.HUGE, 1).planets.size)
    }

    @Test
    fun fullyConnectedForHundredSeedsPerSize() {
        for (size in MapSize.values()) {
            for (seed in 1L..100L) {
                val m = MapGenerator.generate(seed, size, (seed % 3).toInt() + 1)
                assertTrue("not connected: $size seed=$seed", PathFinder.isConnected(m.planets))
            }
        }
    }

    @Test
    fun geometryRulesHold() {
        for (size in MapSize.values()) {
            for (seed in 1L..100L) {
                val m = MapGenerator.generate(seed, size, 3)
                for (p in m.planets) {
                    assertTrue(p.x >= 0 && p.x <= m.mapWidth && p.y >= 0 && p.y <= m.mapHeight)
                    assertTrue("max links: $size seed=$seed", p.links.size in 1..MapGenerator.MAX_LINKS)
                }
                for (i in m.planets.indices) for (j in i + 1 until m.planets.size) {
                    val d = hypot(m.planets[i].x - m.planets[j].x, m.planets[i].y - m.planets[j].y)
                    assertTrue("spacing $d", d >= MapGenerator.MIN_CENTER_DISTANCE - 1e-9)
                }
                for (l in m.links) assertTrue(l.length <= MapGenerator.MAX_LINK_DISTANCE + 1e-9)
            }
        }
    }

    @Test
    fun startsAreNonAdjacentAndSpreadOut() {
        for (size in MapSize.values()) {
            for (seed in 1L..100L) {
                for (opp in 1..3) {
                    val m = MapGenerator.generate(seed, size, opp)
                    val s = starts(m)
                    assertEquals(opp + 1, s.size)
                    assertEquals(opp + 1, s.map { it.fraction }.toSet().size)
                    assertTrue(s.all { it.type == PlanetType.NORMAL && it.units == 3.0 })
                    for (i in s.indices) for (j in i + 1 until s.size) {
                        assertFalse("adjacent starts $size seed=$seed opp=$opp", s[i].isLinkedTo(s[j]))
                    }
                    if (opp == 1) {
                        var diameter = 0.0
                        for (a in m.planets) for (b in m.planets) diameter = maxOf(diameter, hypot(a.x - b.x, a.y - b.y))
                        val d = hypot(s[0].x - s[1].x, s[0].y - s[1].y)
                        assertTrue("starts too close: $d vs $diameter", d >= 0.5 * diameter - 1e-9)
                    }
                }
            }
        }
    }

    @Test
    fun allNeutralPlanetsAreNormalWithFullShield() {
        assertFalse(MapGenerator.SPECIAL_NEUTRALS)
        for (size in MapSize.values()) {
            for (seed in 1L..100L) {
                val m = MapGenerator.generate(seed, size, (seed % 3).toInt() + 1)
                val neutrals = m.planets.filter { it.fraction.isNeutral }
                assertEquals(size.planetCount - ((seed % 3).toInt() + 2), neutrals.size)
                assertTrue("special neutral: $size seed=$seed", neutrals.all { it.type == PlanetType.NORMAL })
                assertTrue(neutrals.all { it.units == 3.0 && it.defense == 7.0 })
            }
        }
    }

    @Test
    fun pathFinderFindsShortestPath() {
        val m = GameModel(1L)
        val p = (0..4).map { m.addPlanet(0.2 * it, 0.1) }
        for (i in 0 until 4) m.addLink(p[i], p[i + 1])
        m.addLink(p[0], p[3])
        val path = PathFinder.findPath(p[0], p[4])!!
        assertEquals(listOf(p[3], p[4]), path)
        assertTrue(PathFinder.findPath(p[2], p[2])!!.isEmpty())
        val iso = m.addPlanet(0.9, 0.9)
        assertEquals(null, PathFinder.findPath(p[0], iso))
        // passable filter blocks intermediates: p4 is only reachable through p3
        assertEquals(null, PathFinder.findPath(p[0], p[4]) { it !== p[3] })
        assertEquals(listOf(p[1], p[2]), PathFinder.findPath(p[0], p[2]) { it !== p[3] })
    }

    @Test
    fun linkLengthsAreVaried() {
        val pooled = ArrayList<Double>()
        for (size in MapSize.values()) {
            for (seed in 1L..100L) {
                val m = MapGenerator.generate(seed, size, 1)
                val lens = m.links.map { it.length }
                assertTrue(
                    "links too uniform: $size seed=$seed",
                    lens.max() / lens.min() >= 1.25
                )
                if (size != MapSize.SMALL) pooled += lens
            }
        }
        val mean = pooled.average()
        val sd = kotlin.math.sqrt(pooled.sumOf { (it - mean) * (it - mean) } / pooled.size)
        assertTrue("spread too small: ${sd / mean}", sd / mean >= 0.15)
        assertTrue("no short links", pooled.min() < 0.16)
        assertTrue("no long links", pooled.max() > 0.28)
    }
}
