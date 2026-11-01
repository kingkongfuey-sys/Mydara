package com.example.mydara.ui

import com.example.mydara.game.Fraction
import com.example.mydara.game.GameEvent
import com.example.mydara.game.Outcome

enum class Sfx { TICK, ARM, SEND, HIT, CAPTURE, LOST, UPGRADE, VICTORY, DEFEAT }

enum class Haptic { SELECT, ARM, CAPTURE, LOST, UPGRADE, VICTORY, DEFEAT }

/** Where sounds and vibrations end up. The Android implementation is in AndroidFeedback.kt. */
interface FeedbackSink {
    fun play(sfx: Sfx, volume: Float, rate: Float)
    fun haptic(haptic: Haptic)

    object None : FeedbackSink {
        override fun play(sfx: Sfx, volume: Float, rate: Float) = Unit
        override fun haptic(haptic: Haptic) = Unit
    }
}

/** Global on/off switches (persisted by FeedbackPrefs on Android). */
object FeedbackSettings {
    @Volatile var sound: Boolean = true
    @Volatile var haptics: Boolean = true
}

/**
 * Decides which sound / vibration each game event deserves. Sound is meant to sit quietly in the
 * background: only things that happen to YOU make a sound (AI-vs-AI fights, AI grabbing neutral planets
 * and unit collisions are silent), volumes are low, and sounds are rate-limited globally.
 */
class FeedbackDirector(private val player: Fraction = Fraction.PLAYER) {
    var sink: FeedbackSink = FeedbackSink.None

    /** Real time of the current frame in ms; set by the session before each batch of events. */
    var nowMs: Long = 0L

    private var lastAnySfx: Long? = null
    private val lastSfx = HashMap<Sfx, Long>()
    private val lastHaptic = HashMap<Haptic, Long>()

    fun onEvent(e: GameEvent) {
        when (e) {
            is GameEvent.UnitsSent ->
                if (e.fraction == player && !e.auto) sfx(Sfx.SEND, VOL_SEND)
            is GameEvent.PlanetHit ->
                if (e.planet.fraction == player) sfx(Sfx.HIT, VOL_HIT)
            is GameEvent.PlanetCaptured -> when {
                e.newOwner == player -> { sfx(Sfx.CAPTURE, VOL_CAPTURE); haptic(Haptic.CAPTURE) }
                e.oldOwner == player -> { sfx(Sfx.LOST, VOL_LOST); haptic(Haptic.LOST) }
                else -> Unit // AI takes a neutral or fights another AI: silent
            }
            is GameEvent.Upgraded ->
                if (e.planet.fraction == player) { sfx(Sfx.UPGRADE, VOL_UPGRADE); haptic(Haptic.UPGRADE) }
            is GameEvent.UnitDied, is GameEvent.LinkRecolored -> Unit
        }
    }

    fun onSelect() {
        sfx(Sfx.TICK, VOL_TICK)
        haptic(Haptic.SELECT)
    }

    fun onArmed() {
        sfx(Sfx.ARM, VOL_ARM)
        haptic(Haptic.ARM)
    }

    fun onOutcome(outcome: Outcome) {
        when (outcome) {
            Outcome.VICTORY -> { sfx(Sfx.VICTORY, VOL_RESULT, force = true); haptic(Haptic.VICTORY) }
            Outcome.DEFEAT -> { sfx(Sfx.DEFEAT, VOL_RESULT, force = true); haptic(Haptic.DEFEAT) }
            Outcome.PLAYING -> Unit
        }
    }

    private fun sfx(s: Sfx, volume: Float, force: Boolean = false) {
        if (!FeedbackSettings.sound) return
        val last = lastSfx[s]
        if (!force && last != null && nowMs - last < (SFX_MIN_INTERVAL_MS[s] ?: 0L)) return
        val any = lastAnySfx
        if (!force && any != null && nowMs - any < GLOBAL_MIN_INTERVAL_MS) return
        lastSfx[s] = nowMs
        lastAnySfx = nowMs
        sink.play(s, volume, 1f)
    }

    private fun haptic(h: Haptic) {
        if (!FeedbackSettings.haptics) return
        val last = lastHaptic[h]
        if (last != null && nowMs - last < HAPTIC_MIN_INTERVAL_MS) return
        lastHaptic[h] = nowMs
        sink.haptic(h)
    }

    companion object {
        // Quiet, background-level volumes.
        const val VOL_TICK = 0.14f
        const val VOL_ARM = 0.18f
        const val VOL_SEND = 0.16f
        const val VOL_HIT = 0.22f
        const val VOL_CAPTURE = 0.28f
        const val VOL_LOST = 0.35f
        const val VOL_UPGRADE = 0.22f
        const val VOL_RESULT = 0.40f

        /** No sound at all within this window of the previous one (keeps busy moments from piling up). */
        const val GLOBAL_MIN_INTERVAL_MS = 150L

        private val SFX_MIN_INTERVAL_MS = mapOf(
            Sfx.TICK to 60L, Sfx.SEND to 200L, Sfx.HIT to 700L
        )
        private const val HAPTIC_MIN_INTERVAL_MS = 80L
    }
}
