package com.example.mydara.game

import kotlin.random.Random

enum class Outcome { PLAYING, VICTORY, DEFEAT }

/**
 * Pure-Kotlin deterministic skirmish simulation. No Android/Compose imports.
 * Map units: mapWidth = 1.0. One [step] = one 60 fps frame; all per-frame effects are scaled by [velocity].
 */
class GameModel(
    val seed: Long,
    val mapWidth: Double = 1.0,
    val mapHeight: Double = 1.6
) {
    val rng: Random = Random(seed)

    private val _planets = ArrayList<Planet>()
    private val _links = ArrayList<Link>()
    private val _batches = ArrayList<UnitBatch>()
    private val events = ArrayList<GameEvent>()

    val planets: List<Planet> get() = _planets
    val links: List<Link> get() = _links
    val batches: List<UnitBatch> get() = _batches

    /** Global speed multiplier (default 0.5). Use one of [VELOCITY_STEPS]. */
    var velocity: Double = DEFAULT_VELOCITY
    var paused: Boolean = false

    var frame: Long = 0
        private set

    var outcome: Outcome = Outcome.PLAYING
        private set
    var winner: Fraction? = null
        private set
    var isOver: Boolean = false
        private set

    private var participants: Set<Fraction>? = null
    private var accumulator = 0.0
    private var nextBatchId = 0

    // ---------------------------------------------------------------- construction

    fun addPlanet(
        x: Double,
        y: Double,
        fraction: Fraction = Fraction.NEUTRAL,
        type: PlanetType = PlanetType.NORMAL
    ): Planet {
        val p = Planet(_planets.size, x, y, fraction, type)
        _planets += p
        return p
    }

    fun addLink(a: Planet, b: Planet): Link {
        require(a !== b) { "Cannot link a planet to itself" }
        a.linkTo(b)?.let { return it }
        val l = Link(_links.size, a, b)
        l.styleFraction = l.solidFraction
        _links += l
        a.links += l
        b.links += l
        return l
    }

    // ---------------------------------------------------------------- commands

    /**
     * Sends units from [from] to a linked planet [to]. Full send = (int) units; half = (int) units / 2.
     * Creates exactly one batched unit. Returns false if illegal or nothing to send.
     */
    fun send(from: Planet, to: Planet, half: Boolean = false): Boolean = sendInternal(from, to, half, false)

    private fun sendInternal(from: Planet, to: Planet, half: Boolean, auto: Boolean): Boolean {
        if (isOver || from.fraction.isNeutral) return false
        val link = from.linkTo(to) ?: return false
        val whole = from.wholeUnits
        val value = if (half) whole / 2 else whole
        if (value < 1) return false
        from.units -= value
        _batches += UnitBatch(nextBatchId++, from.fraction, value, from, to, link)
        from.pop = 1.0
        emit(GameEvent.UnitsSent(from, to, from.fraction, value, auto))
        return true
    }

    /** Assigns an auto-target; the first dispatch happens on the next 9-frame check. */
    fun setAutoTarget(from: Planet, target: Planet): Boolean {
        if (isOver || from.fraction.isNeutral || !from.isLinkedTo(target)) return false
        from.autoTarget = target
        from.autoAccumulator = 1.0
        return true
    }

    fun clearAutoTarget(planet: Planet) {
        planet.autoTarget = null
        planet.autoAccumulator = 0.0
    }

    /** Converts an owned planet to ECONOMIC or DEFENSIVE for 10 units. */
    fun upgrade(planet: Planet, newType: PlanetType): Boolean {
        if (isOver || planet.fraction.isNeutral) return false
        if (newType == PlanetType.NORMAL || planet.type == newType) return false
        if (planet.wholeUnits < Planet.UPGRADE_COST) return false
        planet.units -= Planet.UPGRADE_COST
        planet.type = newType
        planet.defense = when (newType) {
            // +10 shield points, capped at the defensive maximum.
            PlanetType.DEFENSIVE -> minOf(planet.defense + 10.0, newType.maxDefense.toDouble())
            else -> 0.0
        }
        emit(GameEvent.Upgraded(planet, newType))
        return true
    }

    fun drainEvents(): List<GameEvent> {
        if (events.isEmpty()) return emptyList()
        val copy = ArrayList(events)
        events.clear()
        return copy
    }

    // ---------------------------------------------------------------- simulation

    /** Advances real time by [dtSeconds]; runs fixed 60 Hz steps (max 5 per call to avoid spirals). */
    fun tick(dtSeconds: Double) {
        accumulator += dtSeconds
        var steps = 0
        while (accumulator >= FRAME_DT && steps < 5) {
            accumulator -= FRAME_DT
            step()
            steps++
        }
        if (steps == 5 && accumulator > FRAME_DT) accumulator = 0.0
    }

    /** One 60 fps frame. No-op while paused or finished. */
    fun step() {
        ensureStarted()
        if (paused || isOver) return
        frame++
        val v = velocity

        for (p in _planets) if (p.pop > 0.0) p.pop = maxOf(0.0, p.pop - v / 30.0)

        if (frame % 6L == 0L) growAndRegen(v)
        if (frame % 9L == 0L) dispatchAutoTargets(v)

        var anyArrived = false
        for (b in _batches) {
            b.progress += SPEED_PER_FRAME * v / b.link.length
            if (b.progress >= 1.0) anyArrived = true
        }
        // Battles every 3 frames; also whenever something arrives so a crossing is never skipped.
        if (frame % 3L == 0L || anyArrived) resolveLinkBattles()
        if (anyArrived) processArrivals()
        _batches.removeAll { !it.alive }

        if (frame % 30L == 0L) checkVictory()
    }

    private fun ensureStarted() {
        if (participants == null) {
            participants = _planets.filter { !it.fraction.isNeutral }.map { it.fraction }.toSet()
        }
    }

    private fun growAndRegen(v: Double) {
        for (p in _planets) {
            if (p.fraction.isNeutral) continue // neutrals never grow or regenerate
            p.units = minOf(Planet.MAX_UNITS, p.units + 0.1 * v * p.type.growthMultiplier)
            val maxDef = p.type.maxDefense.toDouble()
            if (maxDef > 0.0 && p.type.defenseRegenMultiplier > 0.0) {
                p.defense = minOf(maxDef, p.defense + 0.1 * v * p.type.defenseRegenMultiplier)
            }
        }
    }

    private fun dispatchAutoTargets(v: Double) {
        for (p in _planets) {
            val target = p.autoTarget ?: continue
            if (p.fraction.isNeutral) { clearAutoTarget(p); continue }
            p.autoAccumulator += 0.1 * v
            if (p.autoAccumulator >= 1.0) {
                p.autoAccumulator = 0.0
                sendInternal(p, target, half = false, auto = true)
            }
        }
    }

    /** Opposing batches of different fractions on the same link annihilate pairwise by value. */
    private fun resolveLinkBattles() {
        if (_batches.size < 2) return
        val byLink = _batches.filter { it.alive }.groupBy { it.link }
        for ((link, onLink) in byLink) {
            val forward = onLink.filter { it.from === link.a }
            val backward = onLink.filter { it.from === link.b }
            if (forward.isEmpty() || backward.isEmpty()) continue
            for (x in forward) {
                for (y in backward) {
                    if (!x.alive) break
                    if (!y.alive || x.fraction == y.fraction) continue
                    // y.progress is measured from b, so its position along a->b is 1 - y.progress
                    if (x.progress < 1.0 - y.progress) continue
                    val t = ((x.progress + (1.0 - y.progress)) / 2.0).coerceIn(0.0, 1.0)
                    val mx = link.a.x + (link.b.x - link.a.x) * t
                    val my = link.a.y + (link.b.y - link.a.y) * t
                    when {
                        x.value == y.value -> {
                            x.alive = false; y.alive = false
                            emit(GameEvent.UnitDied(mx, my, x.fraction, link))
                            emit(GameEvent.UnitDied(mx, my, y.fraction, link))
                        }
                        x.value > y.value -> {
                            x.value -= y.value; y.alive = false
                            emit(GameEvent.UnitDied(mx, my, y.fraction, link))
                        }
                        else -> {
                            y.value -= x.value; x.alive = false
                            emit(GameEvent.UnitDied(mx, my, x.fraction, link))
                        }
                    }
                }
            }
        }
    }

    private fun processArrivals() {
        // Index loop: arrivals never add batches, but keep iteration order deterministic.
        for (b in _batches) {
            if (b.alive && b.progress >= 1.0) {
                b.alive = false
                arrive(b)
            }
        }
    }

    private fun arrive(b: UnitBatch) {
        val p = b.to
        val v = b.value
        if (p.fraction == b.fraction) {
            p.units = minOf(Planet.MAX_UNITS, p.units + v)
            p.pop = 1.0
            return
        }
        emit(GameEvent.PlanetHit(p, b.fraction))
        val d = p.wholeDefense
        when {
            v <= d -> p.defense -= v
            v < d + p.units -> {
                p.units -= (v - d)
                p.defense = 0.0
            }
            else -> capture(p, b.fraction, v - (d + p.units))
        }
    }

    private fun capture(p: Planet, newOwner: Fraction, remaining: Double) {
        val oldOwner = p.fraction
        p.fraction = newOwner
        p.type = PlanetType.NORMAL
        p.defense = PlanetType.NORMAL.maxDefense.toDouble()
        p.units = remaining.coerceIn(0.0, Planet.MAX_UNITS)
        p.autoTarget = null
        p.autoAccumulator = 0.0
        p.pop = 1.0
        emit(GameEvent.PlanetCaptured(p, newOwner, oldOwner))
        for (l in p.links) {
            val now = l.solidFraction
            if (now != l.styleFraction) {
                l.styleFraction = now
                emit(GameEvent.LinkRecolored(l))
            }
        }
    }

    private fun checkVictory() {
        val start = participants ?: return
        if (start.size < 2) return // a match needs at least two fractions to have a winner
        val alive = _planets.filter { !it.fraction.isNeutral }.map { it.fraction }.toSet()
        val eliminated = start - alive
        if (eliminated.isNotEmpty()) _batches.removeAll { it.fraction in eliminated }

        val playerPlaying = Fraction.PLAYER in start
        when {
            playerPlaying && Fraction.PLAYER !in alive -> {
                isOver = true
                outcome = Outcome.DEFEAT
                winner = alive.singleOrNull()
            }
            alive.size <= 1 -> {
                isOver = true
                winner = alive.singleOrNull()
                outcome = if (playerPlaying && winner == Fraction.PLAYER) Outcome.VICTORY else outcome
            }
        }
    }

    private fun emit(e: GameEvent) {
        if (events.size >= MAX_EVENTS) events.removeAt(0)
        events += e
    }

    // ---------------------------------------------------------------- queries

    fun planetsOf(fraction: Fraction): List<Planet> = _planets.filter { it.fraction == fraction }

    companion object {
        const val FRAME_DT = 1.0 / 60.0
        const val DEFAULT_VELOCITY = 0.5

        /** Travel speed in map units per frame at velocity 1 (= 0.009 * 0.2). Map unit = reference width 1.0, independent of playfield size. */
        const val SPEED_PER_FRAME = 0.0018

        const val MAX_EVENTS = 2000

        val VELOCITY_STEPS: List<Double> =
            listOf(0.1, 0.2, 0.3, 0.4, 0.5, 0.75, 1.0, 1.25, 1.5, 2.0, 2.5, 3.0, 4.0)
    }
}
