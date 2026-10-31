package com.example.mydara.ui

import com.example.mydara.game.Fraction
import com.example.mydara.game.GameEvent
import com.example.mydara.game.Outcome
import kotlin.random.Random

enum class Sfx { TICK, ARM, SEND, UNIT_POP, HIT, CAPTURE, LOST, UPGRADE, VICTORY, DEFEAT }

enum class Haptic { SELECT, ARM, SEND, HIT, CAPTURE, LOST, UPGRADE, VICTORY, DEFEAT }

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
 * Decides which sound / vibration each game event deserves. Pure logic: events involving the player are
 * loud, AI-vs-AI events are quiet or silent, and rapid repeats are throttled.
 */
class FeedbackDirector(private val player: Fraction = Fraction.PLAYER, seed: Long = 1L) {
    var sink: FeedbackSink = FeedbackSink.None

    /** Real time of the current frame in ms; set by the session before each batch of events. */
    var nowMs: Long = 0L

    private val rng = Random(seed)
    private val lastSfx = HashMap<Sfx, Long>()
    private val lastHaptic = HashMap<Haptic, Long>()

    fun onEvent(e: GameEvent) {
        when (e) {
            is GameEvent.UnitsSent ->
                if (e.fraction == player && !e.auto) { sfx(Sfx.SEND, 0.7f); haptic(Haptic.SEND) }
            is GameEvent.UnitDied ->
                sfx(Sfx.UNIT_POP, if (e.fraction == player) 0.5f else 0.18f, jitter = true)
            is GameEvent.PlanetHit -> when {
                e.planet.fraction == player -> { sfx(Sfx.HIT, 0.9f, jitter = true); haptic(Haptic.HIT) }
                e.attacker == player -> sfx(Sfx.HIT, 0.5f, jitter = true)
                else -> sfx(Sfx.HIT, 0.15f, jitter = true)
            }
            is GameEvent.PlanetCaptured -> when {
                e.newOwner == player -> { sfx(Sfx.CAPTURE, 1f); haptic(Haptic.CAPTURE) }
                e.oldOwner == player -> { sfx(Sfx.LOST, 1f); haptic(Haptic.LOST) }
                else -> sfx(Sfx.CAPTURE, 0.2f)
            }
            is GameEvent.Upgraded ->
                if (e.planet.fraction == player) { sfx(Sfx.UPGRADE, 0.9f); haptic(Haptic.UPGRADE) }
            is GameEvent.LinkRecolored -> Unit
        }
    }

    fun onSelect() {
        sfx(Sfx.TICK, 0.6f)
        haptic(Haptic.SELECT)
    }

    fun onArmed() {
        sfx(Sfx.ARM, 0.7f)
        haptic(Haptic.ARM)
    }

    fun onOutcome(outcome: Outcome) {
        when (outcome) {
            Outcome.VICTORY -> { sfx(Sfx.VICTORY, 1f); haptic(Haptic.VICTORY) }
            Outcome.DEFEAT -> { sfx(Sfx.DEFEAT, 1f); haptic(Haptic.DEFEAT) }
            Outcome.PLAYING -> Unit
        }
    }

    private fun sfx(s: Sfx, volume: Float, jitter: Boolean = false) {
        if (!FeedbackSettings.sound) return
        val last = lastSfx[s]
        if (last != null && nowMs - last < (SFX_MIN_INTERVAL_MS[s] ?: 0L)) return
        lastSfx[s] = nowMs
        val rate = if (jitter) 0.92f + rng.nextFloat() * 0.16f else 1f
        sink.play(s, volume, rate)
    }

    private fun haptic(h: Haptic) {
        if (!FeedbackSettings.haptics) return
        val last = lastHaptic[h]
        if (last != null && nowMs - last < HAPTIC_MIN_INTERVAL_MS) return
        lastHaptic[h] = nowMs
        sink.haptic(h)
    }

    companion object {
        private val SFX_MIN_INTERVAL_MS = mapOf(
            Sfx.TICK to 30L, Sfx.SEND to 50L, Sfx.UNIT_POP to 70L, Sfx.HIT to 100L
        )
        private const val HAPTIC_MIN_INTERVAL_MS = 60L
    }
}
