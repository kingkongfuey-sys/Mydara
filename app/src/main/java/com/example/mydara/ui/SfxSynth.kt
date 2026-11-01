package com.example.mydara.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedural sound effects, rendered to 16-bit mono PCM / WAV at startup. No audio asset files are needed.
 *
 * Instead of bare sine / triangle beeps, every effect is built from a few small "instruments" that imitate
 * real physical sounds, which is what keeps them from sounding like a generic synthesizer:
 *  - mallet: a struck wooden bar (marimba-like 1 : 4 : 10 overtones that die away faster than the fundamental)
 *  - bell: inharmonic partials plus a slightly detuned copy of the fundamental, so the note beats and shimmers
 *  - felt: a soft, slow-attack tone whose pitch sags a little as it fades (muffled piano / sad pad)
 *  - thud: a falling sine with a burst of low-passed noise (a body hitting something)
 *  - whoosh: noise through a sweeping resonant filter (air moving)
 * Melodies use open fifths and pentatonic steps rather than plain major triads, and a tiny room reflection
 * is added so nothing sounds dry. Everything is deterministic (fixed noise seeds).
 */
object SfxSynth {
    const val SAMPLE_RATE = 22_050

    /** Mono mix buffer with its own noise source. */
    private class Mix(totalMs: Int, seed: Int) {
        val buf = FloatArray(totalMs * SAMPLE_RATE / 1000)
        val rng = Random(seed)
        fun idx(ms: Int): Int = ms * SAMPLE_RATE / 1000
    }

    // ------------------------------------------------------------------ building blocks

    /** Sum of decaying sine partials. [sag] lowers the pitch by that fraction as the note fades. */
    private fun Mix.partials(
        startMs: Int, f: Double, vol: Double,
        ratios: DoubleArray, amps: DoubleArray, decaysSec: DoubleArray,
        attackSec: Double, sag: Double = 0.0
    ) {
        val start = idx(startMs)
        for (k in ratios.indices) {
            val base = f * ratios[k]
            if (base > 9_000.0) continue
            var phase = 0.0
            val len = min(buf.size - start, (decaysSec[k] * 7 * SAMPLE_RATE).toInt())
            for (i in 0 until len) {
                val t = i.toDouble() / SAMPLE_RATE
                val freq = base * (1.0 - sag * (1.0 - exp(-t * 6.0)))
                phase += 2 * PI * freq / SAMPLE_RATE
                val env = min(1.0, t / attackSec) * exp(-t / decaysSec[k])
                buf[start + i] += (sin(phase) * amps[k] * env * vol).toFloat()
            }
        }
    }

    /** A few milliseconds of low-passed noise: the "contact" of a strike. [smooth] 0..1, lower = duller. */
    private fun Mix.click(startMs: Int, vol: Double, smooth: Double) {
        val start = idx(startMs)
        var lp = 0.0
        for (i in 0 until idx(14)) {
            val s = start + i
            if (s >= buf.size) break
            lp += smooth * ((rng.nextDouble() * 2 - 1) - lp)
            buf[s] += (lp * vol * exp(-i / (SAMPLE_RATE * 0.003))).toFloat()
        }
    }

    private fun Mix.mallet(startMs: Int, f: Double, vol: Double, decayMs: Double) {
        val d = decayMs / 1000
        partials(
            startMs, f, vol,
            doubleArrayOf(1.0, 3.97, 9.2), doubleArrayOf(1.0, 0.30, 0.08),
            doubleArrayOf(d, d * 0.35, d * 0.15), attackSec = 0.002
        )
        click(startMs, vol * 0.25, 0.45)
    }

    private fun Mix.bell(startMs: Int, f: Double, vol: Double, decayMs: Double) {
        val d = decayMs / 1000
        partials(
            startMs, f, vol,
            doubleArrayOf(1.0, 2.0, 2.4, 3.0, 4.2, 5.4),
            doubleArrayOf(1.0, 0.55, 0.40, 0.30, 0.18, 0.10),
            doubleArrayOf(d, d * 0.8, d * 0.6, d * 0.5, d * 0.35, d * 0.25), attackSec = 0.003
        )
        // slightly detuned twin: the slow beating is what makes it feel like a real, imperfect bell
        partials(
            startMs, f * 1.004, vol * 0.45,
            doubleArrayOf(1.0), doubleArrayOf(1.0), doubleArrayOf(d * 0.9), attackSec = 0.003
        )
        click(startMs, vol * 0.15, 0.6)
    }

    private fun Mix.felt(startMs: Int, f: Double, vol: Double, decayMs: Double) {
        val d = decayMs / 1000
        partials(
            startMs, f, vol,
            doubleArrayOf(1.0, 2.0, 3.0), doubleArrayOf(1.0, 0.35, 0.10),
            doubleArrayOf(d, d * 0.5, d * 0.3), attackSec = 0.014, sag = 0.015
        )
        partials(
            startMs, f * 1.006, vol * 0.5,
            doubleArrayOf(1.0), doubleArrayOf(1.0), doubleArrayOf(d), attackSec = 0.014, sag = 0.015
        )
    }

    /** Falling sine plus a puff of low-passed noise. */
    private fun Mix.thud(startMs: Int, f0: Double, f1: Double, vol: Double, ms: Int, noiseVol: Double) {
        val start = idx(startMs)
        val tau = ms / 1000.0 / 4
        var phase = 0.0
        var lp = 0.0
        val len = min(buf.size - start, idx(ms))
        for (i in 0 until len) {
            val t = i.toDouble() / SAMPLE_RATE
            val freq = f1 + (f0 - f1) * exp(-t * 28.0)
            phase += 2 * PI * freq / SAMPLE_RATE
            val env = min(1.0, t / 0.002) * exp(-t / tau)
            lp += 0.12 * ((rng.nextDouble() * 2 - 1) - lp)
            buf[start + i] += ((sin(phase) * vol + lp * noiseVol * exp(-t / 0.04)) * env).toFloat()
        }
    }

    /** Noise through a resonant band-pass filter sweeping from [fromHz] to [toHz], with a soft bell envelope. */
    private fun Mix.whoosh(startMs: Int, ms: Int, vol: Double, fromHz: Double, toHz: Double) {
        val start = idx(startMs)
        val n = min(buf.size - start, idx(ms))
        var low = 0.0
        var band = 0.0
        for (i in 0 until n) {
            val u = i.toDouble() / n
            val fc = fromHz + (toHz - fromHz) * u
            val f = 2 * sin(PI * fc / SAMPLE_RATE)
            val x = rng.nextDouble() * 2 - 1
            low += f * band
            val high = x - low - 0.5 * band
            band += f * high
            val s = sin(PI * u)
            buf[start + i] += (band * s * s * vol).toFloat()
        }
    }

    /** A handful of soft, darkened early reflections: a small room instead of a dry beep. */
    private fun Mix.room(wet: Double) {
        val dry = buf.copyOf()
        var lp = 0f
        val soft = FloatArray(dry.size) { i -> lp += 0.45f * (dry[i] - lp); lp }
        for ((ms, gain) in listOf(19 to 0.30, 37 to 0.20, 61 to 0.12, 97 to 0.07)) {
            val d = idx(ms)
            for (i in d until buf.size) buf[i] += (soft[i - d] * gain * wet).toFloat()
        }
    }

    /** Fade the very end (no click), normalize the loudest sample to 60% of full scale, convert to 16 bit. */
    private fun Mix.finish(): ShortArray {
        val fade = min(idx(18), buf.size)
        for (i in 0 until fade) buf[buf.size - 1 - i] *= i.toFloat() / fade
        var peak = 0f
        for (v in buf) peak = maxOf(peak, abs(v))
        val gain = if (peak > 0f) 0.6f / peak else 1f
        return ShortArray(buf.size) { (buf[it] * gain * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort() }
    }

    // ------------------------------------------------------------------ the effects

    fun samples(sfx: Sfx): ShortArray = when (sfx) {
        // selecting: a light wooden tap
        Sfx.TICK -> Mix(140, 1).apply {
            mallet(0, 1180.0, 0.7, 38.0)
            room(0.12)
        }.finish()

        // long-press armed: a quick two-knock, up a fifth
        Sfx.ARM -> Mix(420, 2).apply {
            mallet(0, 392.0, 0.8, 90.0)
            mallet(75, 587.33, 0.8, 120.0)
            room(0.2)
        }.finish()

        // units leaving: a soft rush of air
        Sfx.SEND -> Mix(330, 3).apply {
            whoosh(0, 230, 0.9, 420.0, 2200.0)
            thud(0, 260.0, 160.0, 0.35, 90, 0.0)
            room(0.25)
        }.finish()

        // an attack landing on your planet: a dull, heavy impact
        Sfx.HIT -> Mix(520, 4).apply {
            thud(0, 150.0, 48.0, 1.0, 320, 0.35)
            click(0, 0.5, 0.25)
            room(0.2)
        }.finish()

        // you took a planet: three bells in open fifths that ring into each other
        Sfx.CAPTURE -> Mix(1100, 5).apply {
            bell(0, 392.0, 0.8, 520.0)
            bell(95, 587.33, 0.8, 560.0)
            bell(200, 880.0, 0.8, 700.0)
            room(0.3)
        }.finish()

        // you lost a planet: a muffled falling figure over a low thump
        Sfx.LOST -> Mix(1300, 6).apply {
            thud(0, 110.0, 50.0, 0.5, 300, 0.15)
            felt(0, 233.08, 0.8, 420.0)
            felt(190, 196.0, 0.8, 480.0)
            felt(400, 155.56, 0.9, 600.0)
            room(0.3)
        }.finish()

        // upgrade: a wooden note answered by a bell with a faint sparkle above it
        Sfx.UPGRADE -> Mix(800, 7).apply {
            mallet(0, 587.33, 0.8, 140.0)
            bell(85, 880.0, 0.7, 420.0)
            bell(85, 1760.0, 0.18, 260.0)
            room(0.25)
        }.finish()

        // victory: a short pentatonic phrase on bells, uneven in time like a played phrase
        Sfx.VICTORY -> Mix(1500, 8).apply {
            bell(0, 392.0, 0.7, 520.0)
            bell(150, 587.33, 0.7, 560.0)
            bell(290, 659.25, 0.7, 600.0)
            bell(460, 783.99, 0.9, 800.0)
            room(0.35)
        }.finish()

        // defeat: slow falling felt tones that settle on a low thud
        Sfx.DEFEAT -> Mix(1550, 9).apply {
            felt(0, 311.13, 0.8, 500.0)
            felt(280, 261.63, 0.8, 520.0)
            felt(560, 233.08, 0.8, 560.0)
            thud(820, 98.0, 44.0, 0.6, 380, 0.2)
            felt(820, 174.61, 0.9, 600.0)
            room(0.35)
        }.finish()
    }

    /** A complete mono 16-bit PCM WAV file. */
    fun wav(sfx: Sfx): ByteArray {
        val pcm = samples(sfx)
        val dataLen = pcm.size * 2
        val buf = java.nio.ByteBuffer.allocate(44 + dataLen).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray()).putInt(36 + dataLen).put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(SAMPLE_RATE).putInt(SAMPLE_RATE * 2).putShort(2).putShort(16)
        buf.put("data".toByteArray()).putInt(dataLen)
        for (s in pcm) buf.putShort(s)
        return buf.array()
    }

    fun all(): Map<Sfx, ByteArray> = Sfx.values().associateWith { wav(it) }
}
