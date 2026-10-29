package com.example.mydara.game

enum class Difficulty { EASY, NORMAL, HARD }

/**
 * One AI opponent (mirrors AbstractAiEntity / AiCustomEntity, simplified). Pure model code.
 * Call [update] after every [GameModel.step]. All commands go through the public GameModel API, so the
 * AI obeys the same link rules as the player and always launches a planet's full garrison.
 *
 * Cadence: perform() every 6 frames; budget of [ACTIONS_PER_SECOND] actions refilled every 60 frames;
 * every send / upgrade / auto-target costs 1 action (cancelling an auto-target is free).
 *
 * Difficulty gates behaviors: EASY = attacks only; NORMAL = + auto-targets + economic upgrades;
 * HARD = + defensive upgrades + coordinated attacks.
 */
class AiController(
    val difficulty: Difficulty,
    val fraction: Fraction,
    private val model: GameModel
) {
    private class Info {
        var safe = true
        var threat = 0
        var potentialThreat = 0
        var adjacentEnemies = 0
        var requestPriority = 0
    }

    private var infos: Array<Info> = emptyArray()
    private var lastFrame = -1L

    var budget: Int = ACTIONS_PER_SECOND
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
        if (f % 60L == 0L) budget = ACTIONS_PER_SECOND

        val perform = f % 6L == 0L
        val slow = f % 10L == 0L
        if (!perform && !slow) return

        val mine = model.planetsOf(fraction)
        if (mine.isEmpty()) return
        updateData(mine)

        if (perform) {
            checkForAttack(mine)
            if (difficulty != Difficulty.EASY) checkToUpgrade(mine)
        }
        if (slow && difficulty != Difficulty.EASY) {
            if (difficulty == Difficulty.HARD) checkForCoordinatedAttack()
            updateSafeAutoTargets(mine)
        }
    }

    // ------------------------------------------------------------------ data

    private fun isEnemy(p: Planet) = !p.fraction.isNeutral && p.fraction != fraction

    private fun info(p: Planet): Info = infos[p.id]

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

    /** checkForAttack: attack the weakest adjacent non-own planet we can beat. */
    private fun checkForAttack(mine: List<Planet>) {
        for (p in mine) {
            if (budget <= 0) return
            if (info(p).threat > p.fullHp / 2.0) continue
            val own = p.wholeUnits
            if (own < 1) continue
            var best: Planet? = null
            for (link in p.links) {
                val t = link.other(p)
                if (t.fraction == fraction) continue
                val need = if (t.fraction.isNeutral) t.fullHp else t.fullHp + 5
                if (own >= need && (best == null || t.fullHp < best.fullHp)) best = t
            }
            val choice = best
            if (choice != null) spend { model.send(p, choice) }
        }
    }

    private fun checkToUpgrade(mine: List<Planet>) {
        val maxEconomic = minOf(2, mine.size / 6)
        var economic = mine.count { it.type == PlanetType.ECONOMIC }
        for (p in mine) {
            if (budget <= 0) return
            if (p.type != PlanetType.NORMAL || p.wholeUnits < Planet.UPGRADE_COST) continue
            val d = info(p)
            if (d.safe && economic < maxEconomic) {
                if (spend { model.upgrade(p, PlanetType.ECONOMIC) }) { economic++; upgradesPerformed++ }
            } else if (difficulty == Difficulty.HARD && !d.safe && d.adjacentEnemies >= 2 &&
                d.potentialThreat >= 0.7 * p.fullHp
            ) {
                if (spend { model.upgrade(p, PlanetType.DEFENSIVE) }) upgradesPerformed++
            }
        }
    }

    /** Hard only: combine adjacent AI planets to take an enemy planet no single planet could. */
    private fun checkForCoordinatedAttack() {
        for (t in model.planets) {
            if (!isEnemy(t)) continue
            val adjacent = t.links.map { it.other(t) }
                .filter { it.fraction == fraction && it.wholeUnits >= 1 }
                .sortedByDescending { it.wholeUnits }
            if (adjacent.size < 2) continue
            val need = t.fullHp + 8
            if (adjacent.sumOf { it.wholeUnits } < need) continue
            // Smallest set of strongest planets that reaches the threshold.
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
        val maxEconomic = minOf(2, mine.size / 6)
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
        const val ACTIONS_PER_SECOND = 2

        /** One controller per non-player fraction present on the map. */
        fun forOpponents(model: GameModel, difficulty: Difficulty): List<AiController> =
            Fraction.AI_ORDER
                .filter { f -> model.planets.any { it.fraction == f } }
                .map { AiController(difficulty, it, model) }
    }
}
