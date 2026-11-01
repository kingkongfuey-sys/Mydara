package com.example.mydara.game

/**
 * What separates the difficulties. Every difficulty can build both economic and defensive planets;
 * they differ only in how well they play.
 *
 * @param actionsPerSecond command budget (each send / upgrade / auto-target costs 1), refilled every 60 frames
 * @param attackExtra extra units demanded beyond what an attack strictly needs (cautious = slower expansion)
 * @param pickWeakest attack the cheapest affordable target (false = first affordable link)
 * @param preferEnemies take enemy planets before neutral ones when both are affordable
 * @param reinforce supply the front line: safe planets auto-target planets in danger / expansion fronts
 * @param coordinated combine several planets to take an enemy planet none could take alone
 * @param economicCap most economic planets the AI keeps (also limited to one per six owned planets)
 * @param defenseThreat build a defensive planet on the front line once adjacent enemy garrisons add up to
 *   this share of the planet's own strength (0 = any frontline planet with spare units)
 */
class Tactics(
    val actionsPerSecond: Int,
    val attackExtra: Int,
    val pickWeakest: Boolean,
    val preferEnemies: Boolean,
    val reinforce: Boolean,
    val coordinated: Boolean,
    val economicCap: Int,
    val defenseThreat: Double
)

enum class Difficulty(val tactics: Tactics) {
    EASY(Tactics(1, 3, false, false, false, false, 1, 0.0)),
    NORMAL(Tactics(2, 0, true, false, true, false, 2, 0.5)),
    HARD(Tactics(2, 0, true, true, true, true, 3, 0.6))
}

/**
 * One AI opponent. Pure model code; call [update] after every [GameModel.step]. All commands go through the
 * public GameModel API, so the AI obeys the same link rules as the player and always launches a planet's
 * full garrison.
 *
 * Fair play: the AI only looks at what a player could see under fog of war - its own planets, planets
 * directly linked to them, and enemy batches travelling toward its own planets (those are always on a link
 * next to one of its planets). It never reads other planets' auto-targets or batches elsewhere on the map.
 *
 * Cadence: decisions every 6 frames; the action budget refills every 60 frames.
 */
class AiController(
    val difficulty: Difficulty,
    val fraction: Fraction,
    private val model: GameModel
) {
    private val tactics = difficulty.tactics

    private class Info {
        var safe = true
        var threat = 0
        var potentialThreat = 0
        var adjacentEnemies = 0
        var requestPriority = 0
    }

    private var infos: Array<Info> = emptyArray()
    private var lastFrame = -1L

    var budget: Int = tactics.actionsPerSecond
        private set
    var totalActions: Int = 0
        private set
    var upgradesPerformed: Int = 0
        private set

    private val _actionFrames = ArrayList<Long>()

    /** Model frame of every action taken (for tests / debugging). */
    val actionFrames: List<Long> get() = _actionFrames

    fun update() {
        if (model.isOver || model.paused) return
        val f = model.frame
        if (f == lastFrame) return
        lastFrame = f
        if (f % 60L == 0L) budget = tactics.actionsPerSecond

        val perform = f % 6L == 0L
        val slow = f % 10L == 0L
        if (!perform && !slow) return

        val mine = model.planetsOf(fraction)
        if (mine.isEmpty()) return
        updateData(mine)

        if (perform) {
            checkForAttack(mine)
            checkToUpgrade(mine)
        }
        if (slow) {
            if (tactics.coordinated) checkForCoordinatedAttack(mine)
            if (tactics.reinforce) updateSafeAutoTargets(mine)
        }
    }

    // ------------------------------------------------------------------ data

    private fun isEnemy(p: Planet) = !p.fraction.isNeutral && p.fraction != fraction

    private fun info(p: Planet): Info = infos[p.id]

    private fun maxEconomic(mine: List<Planet>) = minOf(tactics.economicCap, mine.size / 6)

    private fun updateData(mine: List<Planet>) {
        if (infos.size != model.planets.size) infos = Array(model.planets.size) { Info() }
        for (p in mine) {
            val d = infos[p.id]
            d.threat = 0
            d.potentialThreat = 0
            d.adjacentEnemies = 0
            var neutralNeighbors = 0
            for (link in p.links) {
                val o = link.other(p)
                if (isEnemy(o)) { d.adjacentEnemies++; d.potentialThreat += o.wholeUnits }
                else if (o.fraction.isNeutral) neutralNeighbors++
            }
            d.safe = d.adjacentEnemies == 0
            d.requestPriority = if (p.type == PlanetType.NORMAL) neutralNeighbors else 0
        }
        // Only batches heading into one of our planets; they travel on a link next to that planet.
        for (b in model.batches) {
            if (b.alive && b.fraction != fraction && b.to.fraction == fraction) infos[b.to.id].threat += b.value
        }
    }

    private fun inDanger(p: Planet): Boolean {
        val d = info(p)
        return d.threat > 0 || (!d.safe && d.potentialThreat > p.fullHp)
    }

    // ------------------------------------------------------------------ budget

    private fun spend(action: () -> Boolean): Boolean {
        if (budget <= 0) return false
        if (!action()) return false
        budget--
        totalActions++
        _actionFrames += model.frame
        return true
    }

    // ------------------------------------------------------------------ behaviors

    private fun checkForAttack(mine: List<Planet>) {
        for (p in mine) {
            if (budget <= 0) return
            if (info(p).threat > p.fullHp / 2.0) continue
            val own = p.wholeUnits
            if (own < 1) continue
            val target = pickTarget(p, own) ?: continue
            spend { model.send(p, target) }
        }
    }

    private fun pickTarget(p: Planet, own: Int): Planet? {
        val affordable = ArrayList<Planet>()
        for (link in p.links) {
            val t = link.other(p)
            if (t.fraction == fraction) continue
            val need = (if (t.fraction.isNeutral) t.fullHp else t.fullHp + 5) + tactics.attackExtra
            if (own >= need) affordable += t
        }
        if (affordable.isEmpty()) return null
        if (!tactics.pickWeakest) return affordable.first()
        val pool = if (tactics.preferEnemies && affordable.any { isEnemy(it) }) {
            affordable.filter { isEnemy(it) }
        } else {
            affordable
        }
        return pool.minWithOrNull(compareBy<Planet>({ it.fullHp }, { it.id }))
    }

    /** Every difficulty builds both planet types; the thresholds differ (see [Tactics]). */
    private fun checkToUpgrade(mine: List<Planet>) {
        val maxEconomic = maxEconomic(mine)
        var economic = mine.count { it.type == PlanetType.ECONOMIC }
        for (p in mine) {
            if (budget <= 0) return
            if (p.type != PlanetType.NORMAL || p.wholeUnits < Planet.UPGRADE_COST) continue
            val d = info(p)
            if (d.safe && economic < maxEconomic) {
                if (spend { model.upgrade(p, PlanetType.ECONOMIC) }) { economic++; upgradesPerformed++ }
            } else if (!d.safe && d.adjacentEnemies >= 1 &&
                d.potentialThreat >= tactics.defenseThreat * p.fullHp
            ) {
                if (spend { model.upgrade(p, PlanetType.DEFENSIVE) }) upgradesPerformed++
            }
        }
    }

    /** Combine adjacent planets to take an enemy planet no single planet could (only planets next to ours). */
    private fun checkForCoordinatedAttack(mine: List<Planet>) {
        val targets = mine.flatMap { p -> p.links.map { it.other(p) } }
            .filter { isEnemy(it) }
            .distinct()
            .sortedBy { it.id }
        for (t in targets) {
            val adjacent = t.links.map { it.other(t) }
                .filter { it.fraction == fraction && it.wholeUnits >= 1 }
                .sortedByDescending { it.wholeUnits }
            if (adjacent.size < 2) continue
            val need = t.fullHp + 8
            if (adjacent.sumOf { it.wholeUnits } < need) continue
            val group = ArrayList<Planet>()
            var sum = 0
            for (a in adjacent) {
                group += a
                sum += a.wholeUnits
                if (sum >= need) break
            }
            // Every send costs one action, so the group must fit in the remaining budget.
            if (group.size > budget) continue
            for (a in group) spend { model.send(a, t) }
            return
        }
    }

    /** Safe, non-defensive planets feed planets in danger, or allies with a higher request priority. */
    private fun updateSafeAutoTargets(mine: List<Planet>) {
        val maxEconomic = maxEconomic(mine)
        val economic = mine.count { it.type == PlanetType.ECONOMIC }
        val danger = mine.filter { inDanger(it) }

        for (p in mine) {
            val d = info(p)
            val current = p.autoTarget
            if (current != null && (!d.safe || current.fraction != fraction || p.type == PlanetType.DEFENSIVE)) {
                model.clearAutoTarget(p) // free
            }
            if (!d.safe || p.type == PlanetType.DEFENSIVE) continue
            if (p.type == PlanetType.NORMAL && economic < maxEconomic) {
                // Keep stockpiling so this planet can afford the economic upgrade.
                model.clearAutoTarget(p)
                continue
            }
            if (budget <= 0) continue

            var target: Planet? = null
            if (danger.isNotEmpty()) target = firstHopToNearest(p, danger)
            if (target == null && d.requestPriority == 0) {
                for (link in p.links) {
                    val o = link.other(p)
                    if (o.fraction != fraction) continue
                    val prio = info(o).requestPriority
                    if (prio > 0 && (target == null || prio > info(target).requestPriority)) target = o
                }
            }
            val chosen = target
            if (chosen != null && p.autoTarget !== chosen) spend { model.setAutoTarget(p, chosen) }
        }
    }

    private fun firstHopToNearest(from: Planet, goals: List<Planet>): Planet? {
        var best: List<Planet>? = null
        for (g in goals) {
            if (g === from) continue
            val path = PathFinder.findPath(from, g) { it.fraction == fraction } ?: continue
            if (path.isEmpty()) continue
            if (best == null || path.size < best.size) best = path
        }
        return best?.first()
    }

    companion object {
        /** One controller per non-player fraction present on the map. */
        fun forOpponents(model: GameModel, difficulty: Difficulty): List<AiController> =
            Fraction.AI_ORDER
                .filter { f -> model.planets.any { it.fraction == f } }
                .map { AiController(difficulty, it, model) }
    }
}
