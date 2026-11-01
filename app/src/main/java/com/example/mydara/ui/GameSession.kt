package com.example.mydara.ui

import com.example.mydara.game.AiController
import com.example.mydara.game.Difficulty
import com.example.mydara.game.Fraction
import com.example.mydara.game.GameEvent
import com.example.mydara.game.GameModel
import com.example.mydara.game.Link
import com.example.mydara.game.MapGenerator
import com.example.mydara.game.MapSize
import com.example.mydara.game.Outcome
import com.example.mydara.game.Planet
import com.example.mydara.game.PlanetType
import com.example.mydara.game.UnitBatch
import kotlin.random.Random

data class GameConfig(
    val size: MapSize = MapSize.MEDIUM,
    val opponents: Int = 1,
    val difficulty: Difficulty = Difficulty.NORMAL,
    val fogOfWar: Boolean = false,
    val seed: Long? = null
)

/** In-memory settings that survive between matches (not persisted to disk). */
object UiState {
    var velocity: Double = GameModel.DEFAULT_VELOCITY
}

/** Snapshot of everything the HUD needs; equal snapshots do not trigger recomposition. */
data class HudState(
    val selectedType: PlanetType?,
    val canAffordUpgrade: Boolean,
    val halved: Boolean,
    val paused: Boolean,
    val velocityIndex: Int,
    val outcome: Outcome
)

/**
 * Owns one match: model, AI controllers, camera, input and visual effects. Pure Kotlin so the
 * whole loop (fixed 60 Hz steps, AI, events -> particles) is unit-testable.
 */
class GameSession(val config: GameConfig, touchSlopPx: Double = 12.0) {
    val seed: Long = config.seed ?: Random.nextInt(1, 1_000_000_000).toLong()
    val model: GameModel = MapGenerator.generate(seed, config.size, config.opponents)
        .also { it.velocity = UiState.velocity }
    private val ais: List<AiController> = AiController.forOpponents(model, config.difficulty)

    val camera = Camera(model.mapWidth, model.mapHeight, Camera.maxZoomFor(config.size), MapGenerator.MAP_MARGIN)
    val input = InputController(model, camera, Fraction.PLAYER, touchSlopPx)
    val particles = ParticleSystem(seed xor 0x5DEECE66DL)
    val stars = StarField(seed xor 0x2545F491L, model.mapWidth, model.mapHeight, config.size)

    val feedback = FeedbackDirector(Fraction.PLAYER)

    /** Fog of war: planets the player can currently see (own planets and planets one link away). */
    val visible = BooleanArray(model.planets.size) { true }

    /** Links from the nearest player planet (0 = own); [UNREACHED] if none. */
    private val hops = IntArray(model.planets.size) { UNREACHED }

    private var accumulator = 0.0
    private var lastSelectedId = -1
    private var lastHalved = false
    private var lastOutcome = Outcome.PLAYING

    init {
        refreshFog()
    }

    // ------------------------------------------------------------------ commands from the HUD

    fun togglePause() {
        model.paused = !model.paused
    }

    fun setVelocityIndex(index: Int) {
        val v = GameModel.VELOCITY_STEPS[index.coerceIn(0, GameModel.VELOCITY_STEPS.size - 1)]
        model.velocity = v
        UiState.velocity = v
    }

    fun velocityIndex(): Int {
        var best = 0
        for (i in GameModel.VELOCITY_STEPS.indices) {
            if (kotlin.math.abs(GameModel.VELOCITY_STEPS[i] - model.velocity) <
                kotlin.math.abs(GameModel.VELOCITY_STEPS[best] - model.velocity)
            ) best = i
        }
        return best
    }

    fun upgradeSelected(type: PlanetType) {
        val p = input.selected ?: return
        if (p.fraction == Fraction.PLAYER) model.upgrade(p, type)
    }

    fun hudState(): HudState {
        val sel: Planet? = input.selected?.takeIf { it.fraction == Fraction.PLAYER }
        return HudState(
            selectedType = sel?.type,
            canAffordUpgrade = sel != null && sel.wholeUnits >= Planet.UPGRADE_COST,
            halved = input.halved,
            paused = model.paused,
            velocityIndex = velocityIndex(),
            outcome = model.outcome
        )
    }

    // ------------------------------------------------------------------ loop

    /** Advances by real time; runs fixed 60 Hz simulation steps (max 5 per call). */
    fun advance(dtSeconds: Double, nowMs: Long) {
        feedback.nowMs = nowMs
        accumulator += dtSeconds
        var steps = 0
        while (accumulator >= GameModel.FRAME_DT && steps < 5) {
            accumulator -= GameModel.FRAME_DT
            stepOnce()
            steps++
        }
        if (steps == 5) accumulator = 0.0
        input.update(nowMs)
        trackSelectionFeedback()
        camera.step()
        refreshFog()
    }

    internal fun stepOnce() {
        model.step()
        for (ai in ais) ai.update()
        val events = model.drainEvents()
        for (e in events) feedback.onEvent(e)
        for (e in events) when (e) {
            is GameEvent.UnitDied ->
                if (isLinkVisible(e.link)) particles.burst(e.x, e.y, e.fraction.particleArgb)
            is GameEvent.PlanetHit ->
                if (isPlanetVisible(e.planet)) particles.burst(e.planet.x, e.planet.y, e.attacker.particleArgb, scale = 2.0)
            is GameEvent.PlanetCaptured ->
                if (isPlanetVisible(e.planet)) particles.burst(e.planet.x, e.planet.y, e.newOwner.particleArgb, scale = 2.0)
            is GameEvent.LinkRecolored ->
                if (isLinkVisible(e.link)) particles.alongLink(e.link, (e.link.solidFraction ?: Fraction.NEUTRAL).particleArgb)
            is GameEvent.UnitsSent, is GameEvent.Upgraded -> Unit
        }
        if (model.outcome != lastOutcome) {
            lastOutcome = model.outcome
            feedback.onOutcome(model.outcome)
        }
        if (!model.paused) {
            particles.step()
            stars.step(model.frame)
        }
    }

    /** Tick when a planet gets selected; a distinct cue when long-press arms the half-send mode. */
    private fun trackSelectionFeedback() {
        val id = input.selected?.id ?: -1
        val armedNow = input.halved && !lastHalved
        if (id != lastSelectedId && id != -1 && !armedNow) feedback.onSelect()
        if (armedNow) feedback.onArmed()
        lastSelectedId = id
        lastHalved = input.halved
    }

    // ------------------------------------------------------------------ fog of war

    /** Planets show their stats within one link of a player planet. */
    fun isPlanetVisible(p: Planet): Boolean = !config.fogOfWar || hops[p.id] <= PLANET_VISION_HOPS

    /** Enemy traffic is only shown while it is within two links of a player planet. */
    fun isLinkVisible(link: Link): Boolean =
        !config.fogOfWar || minOf(hops[link.a.id], hops[link.b.id]) <= UNIT_VISION_HOPS

    fun isUnitVisible(b: UnitBatch): Boolean =
        !config.fogOfWar || b.fraction == Fraction.PLAYER || isLinkVisible(b.link)

    /** Recomputes link distances from the player's planets (breadth-first, all own planets at distance 0). */
    internal fun refreshFog() {
        if (!config.fogOfWar) return
        java.util.Arrays.fill(hops, UNREACHED)
        val queue = ArrayDeque<Planet>()
        for (p in model.planets) {
            if (p.fraction == Fraction.PLAYER) { hops[p.id] = 0; queue.addLast(p) }
        }
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            for (l in cur.links) {
                val n = l.other(cur)
                if (hops[n.id] == UNREACHED) { hops[n.id] = hops[cur.id] + 1; queue.addLast(n) }
            }
        }
        for (p in model.planets) visible[p.id] = hops[p.id] <= PLANET_VISION_HOPS
    }

    companion object {
        const val PLANET_VISION_HOPS = 1
        const val UNIT_VISION_HOPS = 2
        private const val UNREACHED = Int.MAX_VALUE / 2
    }
}
