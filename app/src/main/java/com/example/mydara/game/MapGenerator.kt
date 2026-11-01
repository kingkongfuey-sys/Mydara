package com.example.mydara.game

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Map-unit area of the growth region per planet (hex-packed planets need ~0.035). */
private const val AREA_PER_PLANET = 0.085

enum class MapSize(val planetCount: Int) {
    SMALL(10), MEDIUM(20), LARGE(30), HUGE(50);

    /** Growth region scales with planet count so planet density is the same for every size. */
    private val scale: Double get() = sqrt(planetCount * AREA_PER_PLANET / 1.6)
    /** Size of the region the layout grows in; the final playfield is cropped to the planets (see MapGenerator). */
    val width: Double get() = scale
    val height: Double get() = 1.6 * scale
}

/** Seeded random map generation. Same seed + size + opponents => identical map. */
object MapGenerator {

    /**
     * Minimum center-to-center distance (3.5 planet radii). The prompt asks for 3.5 radii "between edges"
     * (= 0.275 center to center), which is impossible together with the 0.22 link cap, so spacing is
     * measured center to center here.
     */
    const val MIN_CENTER_DISTANCE = 3.5 * Planet.RADIUS
    const val MAX_LINK_DISTANCE = 0.22
    const val MAX_LINKS = 4

    /** Empty border (map units) around the outermost planets in the cropped playfield. */
    const val MAP_MARGIN = 0.1

    private const val EDGE_MARGIN = 0.03
    private const val CANDIDATES = 80
    private const val MAX_PLACEMENT_FAILS = 300
    private const val MAX_ATTEMPTS = 200
    private const val SPECIAL_SHARE = 0.15

    /** When false (current design) every neutral planet is NORMAL; true restores ~15% economic + 15% defensive. */
    const val SPECIAL_NEUTRALS = false

    fun generate(seed: Long, size: MapSize, opponents: Int): GameModel {
        require(opponents in 1..3) { "opponents must be 1..3" }
        for (attempt in 0 until MAX_ATTEMPTS) {
            val rng = Random(mix(seed, size.ordinal.toLong() * 1_000L + attempt))
            val layout = buildLayout(rng, size) ?: continue
            return assemble(seed, size, opponents, layout, rng) ?: continue
        }
        error("Map generation failed for seed=$seed size=$size")
    }

    // ------------------------------------------------------------------ layout

    private class Layout(
        val xs: DoubleArray,
        val ys: DoubleArray,
        val edges: List<IntArray>,
        val width: Double,
        val height: Double
    )

    private fun buildLayout(rng: Random, size: MapSize): Layout? {
        val n = size.planetCount
        val w = size.width
        val h = size.height
        val lo = Planet.RADIUS + EDGE_MARGIN
        val xs = DoubleArray(n)
        val ys = DoubleArray(n)
        xs[0] = lo + rng.nextDouble() * (w - 2 * lo)
        ys[0] = lo + rng.nextDouble() * (h - 2 * lo)

        // Grow outward: each new planet sits 3.5..~4.4 radii from an existing one (so the graph is
        // connected by construction), preferring spots toward a random target to fill the playfield.
        var count = 1
        var fails = 0
        while (count < n) {
            val tx = lo + rng.nextDouble() * (w - 2 * lo)
            val ty = lo + rng.nextDouble() * (h - 2 * lo)
            var bx = 0.0
            var by = 0.0
            var bestScore = -Double.MAX_VALUE
            var found = false
            repeat(CANDIDATES) { i ->
                var cx: Double
                var cy: Double
                if (i % 2 == 0) {
                    // a site at distance 3.5..4.4 radii from one existing planet
                    val a = rng.nextInt(count)
                    val ang = rng.nextDouble() * 2 * PI
                    val dist = MIN_CENTER_DISTANCE + (MAX_LINK_DISTANCE - MIN_CENTER_DISTANCE) * rng.nextDouble()
                    cx = xs[a] + cos(ang) * dist
                    cy = ys[a] + sin(ang) * dist
                } else {
                    // a triangle apex: in link range of TWO nearby planets, which closes loops
                    val a = rng.nextInt(count)
                    val b = rng.nextInt(count)
                    if (a == b) return@repeat
                    val dx = xs[b] - xs[a]
                    val dy = ys[b] - ys[a]
                    val d = hypot(dx, dy)
                    if (d < 0.01 || d > 2 * MAX_LINK_DISTANCE) return@repeat
                    val r1 = MIN_CENTER_DISTANCE + (MAX_LINK_DISTANCE - MIN_CENTER_DISTANCE) * rng.nextDouble()
                    val r2 = MIN_CENTER_DISTANCE + (MAX_LINK_DISTANCE - MIN_CENTER_DISTANCE) * rng.nextDouble()
                    if (d > r1 + r2 || d < kotlin.math.abs(r1 - r2)) return@repeat
                    val along = (r1 * r1 - r2 * r2 + d * d) / (2 * d)
                    val off = sqrt(maxOf(0.0, r1 * r1 - along * along))
                    val side = if (rng.nextBoolean()) 1.0 else -1.0
                    cx = xs[a] + along * dx / d - side * off * dy / d
                    cy = ys[a] + along * dy / d + side * off * dx / d
                }
                if (cx < lo || cx > w - lo || cy < lo || cy > h - lo) return@repeat
                var neighbors = 0
                for (k in 0 until count) {
                    val dk = hypot(cx - xs[k], cy - ys[k])
                    if (dk < MIN_CENTER_DISTANCE) return@repeat
                    if (dk <= MAX_LINK_DISTANCE) neighbors++
                }
                // closer to the growth target is better; sites with 2-3 neighbors (lattice-like) are better still
                val score = -hypot(cx - tx, cy - ty) + 0.1 * minOf(neighbors, 3)
                if (score > bestScore) { bestScore = score; bx = cx; by = cy; found = true }
            }
            if (!found) {
                if (++fails > MAX_PLACEMENT_FAILS) return null
                continue
            }
            xs[count] = bx
            ys[count] = by
            count++
        }

        // Gabriel graph limited by the link distance cap: planar, and connected because the
        // growth tree above is a subgraph of the cap-limited graph.
        val cap2 = MAX_LINK_DISTANCE * MAX_LINK_DISTANCE
        val edges = ArrayList<IntArray>()
        for (i in 0 until n) for (j in i + 1 until n) {
            val d2 = sq(xs[i] - xs[j]) + sq(ys[i] - ys[j])
            if (d2 > cap2) continue
            var blocked = false
            for (k in 0 until n) {
                if (k == i || k == j) continue
                val dik = sq(xs[i] - xs[k]) + sq(ys[i] - ys[k])
                val djk = sq(xs[j] - xs[k]) + sq(ys[j] - ys[k])
                if (dik + djk < d2 - 1e-12) { blocked = true; break }
            }
            if (!blocked) edges += intArrayOf(i, j)
        }
        if (!isConnected(n, edges, null)) return null

        // Enforce <= MAX_LINKS per planet by dropping the longest edges that are not bridges.
        val deg = IntArray(n)
        for (e in edges) { deg[e[0]]++; deg[e[1]]++ }
        val active = BooleanArray(edges.size) { true }
        val order = edges.indices.sortedWith(
            compareByDescending<Int> { edgeLen(xs, ys, edges[it]) }.thenBy { it }
        )
        for (idx in order) {
            val e = edges[idx]
            if (deg[e[0]] <= MAX_LINKS && deg[e[1]] <= MAX_LINKS) continue
            active[idx] = false
            if (isConnected(n, edges, active)) {
                deg[e[0]]--; deg[e[1]]--
            } else {
                active[idx] = true
            }
        }
        if (deg.any { it > MAX_LINKS }) return null
        val kept = edges.filterIndexed { i, _ -> active[i] }

        // Crop the playfield to the planets with an even margin and move everything so the map is centered
        // in its own bounds (the camera then centers and fits exactly what exists).
        var minX = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE
        var minY = Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
        for (i in 0 until n) {
            minX = minOf(minX, xs[i]); maxX = maxOf(maxX, xs[i])
            minY = minOf(minY, ys[i]); maxY = maxOf(maxY, ys[i])
        }
        val dx = MAP_MARGIN - minX
        val dy = MAP_MARGIN - minY
        for (i in 0 until n) { xs[i] += dx; ys[i] += dy }
        return Layout(xs, ys, kept, (maxX - minX) + 2 * MAP_MARGIN, (maxY - minY) + 2 * MAP_MARGIN)
    }

    private fun isConnected(n: Int, edges: List<IntArray>, active: BooleanArray?): Boolean {
        val adj = Array(n) { ArrayList<Int>() }
        edges.forEachIndexed { i, e ->
            if (active == null || active[i]) { adj[e[0]] += e[1]; adj[e[1]] += e[0] }
        }
        val seen = BooleanArray(n)
        val stack = ArrayDeque<Int>()
        stack.addLast(0); seen[0] = true
        var visited = 1
        while (stack.isNotEmpty()) {
            val c = stack.removeLast()
            for (m in adj[c]) if (!seen[m]) { seen[m] = true; visited++; stack.addLast(m) }
        }
        return visited == n
    }

    // ------------------------------------------------------------------ assembly

    private fun assemble(seed: Long, size: MapSize, opponents: Int, layout: Layout, rng: Random): GameModel? {
        val n = size.planetCount
        val xs = layout.xs
        val ys = layout.ys

        val adjacent = Array(n) { BooleanArray(n) }
        for (e in layout.edges) { adjacent[e[0]][e[1]] = true; adjacent[e[1]][e[0]] = true }

        // No set of pairwise non-adjacent starts on this layout => reject it and try the next attempt.
        val starts = pickStarts(xs, ys, adjacent, opponents + 1) ?: return null
        val fractions = (listOf(Fraction.PLAYER) + Fraction.AI_ORDER.take(opponents)).shuffled(rng)

        val types = HashMap<Int, PlanetType>()
        if (SPECIAL_NEUTRALS) {
            val neutrals = (0 until n).filter { it !in starts }.shuffled(rng)
            val special = (neutrals.size * SPECIAL_SHARE).roundToInt()
            for (k in 0 until special) types[neutrals[k]] = PlanetType.ECONOMIC
            for (k in special until minOf(2 * special, neutrals.size)) types[neutrals[k]] = PlanetType.DEFENSIVE
        }

        val model = GameModel(seed, layout.width, layout.height)
        for (i in 0 until n) {
            val owner = starts.indexOf(i).let { if (it >= 0) fractions[it] else Fraction.NEUTRAL }
            model.addPlanet(xs[i], ys[i], owner, types[i] ?: PlanetType.NORMAL)
        }
        for (e in layout.edges.sortedWith(compareBy({ it[0] }, { it[1] }))) {
            model.addLink(model.planets[e[0]], model.planets[e[1]])
        }
        return model
    }

    /** Greedy farthest-point picking; first start is the planet farthest from the centroid. */
    private fun pickStarts(xs: DoubleArray, ys: DoubleArray, adjacent: Array<BooleanArray>, count: Int): List<Int>? {
        val n = xs.size
        val cx = xs.average()
        val cy = ys.average()
        var first = 0
        var best = -1.0
        for (i in 0 until n) {
            val d = hypot(xs[i] - cx, ys[i] - cy)
            if (d > best + 1e-12) { best = d; first = i }
        }
        val chosen = arrayListOf(first)
        var relaxed = false
        while (chosen.size < count) {
            var pick = -1
            var pickScore = -1.0
            for (relax in 0..1) {
                for (i in 0 until n) {
                    if (i in chosen) continue
                    if (relax == 0 && chosen.any { adjacent[it][i] }) continue
                    val score = chosen.minOf { hypot(xs[i] - xs[it], ys[i] - ys[it]) }
                    if (score > pickScore + 1e-12) { pickScore = score; pick = i }
                }
                if (pick >= 0) { relaxed = relax == 1; break }
            }
            chosen += pick
            if (relaxed) break
        }
        if (!relaxed) return chosen
        // Greedy got stuck next to an existing start: search for a non-adjacent set instead.
        return bestIndependentSet(xs, ys, adjacent, count)
    }

    /** Pairwise non-adjacent set of [count] planets with the largest minimum pairwise distance, or null. */
    private fun bestIndependentSet(xs: DoubleArray, ys: DoubleArray, adjacent: Array<BooleanArray>, count: Int): List<Int>? {
        var best: List<Int>? = null
        var bestScore = -1.0
        val current = ArrayList<Int>()
        fun rec(from: Int, minDist: Double) {
            if (current.size == count) {
                if (minDist > bestScore + 1e-12) { bestScore = minDist; best = ArrayList(current) }
                return
            }
            for (i in from until xs.size) {
                if (current.any { adjacent[it][i] }) continue
                var m = minDist
                for (c in current) m = minOf(m, hypot(xs[i] - xs[c], ys[i] - ys[c]))
                current += i
                rec(i + 1, m)
                current.removeAt(current.size - 1)
            }
        }
        rec(0, Double.MAX_VALUE)
        return best
    }

    private fun edgeLen(xs: DoubleArray, ys: DoubleArray, e: IntArray) =
        hypot(xs[e[0]] - xs[e[1]], ys[e[0]] - ys[e[1]])

    private fun sq(v: Double) = v * v

    /** SplitMix64-style mixing so nearby seeds give unrelated maps. */
    private fun mix(a: Long, b: Long): Long {
        var z = a + (b + 1) * -7046029254386353131L
        z = (z xor (z ushr 30)) * -4658895280553007687L
        z = (z xor (z ushr 27)) * -7723592293110705685L
        return z xor (z ushr 31)
    }
}
