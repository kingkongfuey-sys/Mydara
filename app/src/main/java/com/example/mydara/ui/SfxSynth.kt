package com.example.mydara.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedural sound effects: every effect is a short sequence of synthesized notes, rendered to 16-bit
 * mono PCM / WAV at startup. No audio asset files are needed.
 */
object SfxSynth {
    const val SAMPLE_RATE = 22_050

    private enum class Shape { SINE, TRI }

    /** A tone gliding from [f0] to [f1] Hz over [ms]; [noise] adds a burst of noise (thuds, pops). */
    private class Note(
        val f0: Double,
        val f1: Double,
        val ms: Int,
        val vol: Double,
        val shape: Shape = Shape.SINE,
        val noise: Double = 0.0
    )

    private fun n(f: Double, ms: Int, vol: Double, shape: Shape = Shape.SINE) = Note(f, f, ms, vol, shape)

    private fun notes(sfx: Sfx): List<Note> = when (sfx) {
        Sfx.TICK -> listOf(Note(1400.0, 1000.0, 30, 0.6))
        Sfx.ARM -> listOf(n(700.0, 40, 0.5), n(1050.0, 70, 0.6))
        Sfx.SEND -> listOf(Note(520.0, 920.0, 100, 0.6))
        Sfx.UNIT_POP -> listOf(Note(760.0, 280.0, 80, 0.5, Shape.TRI, noise = 0.15))
        Sfx.HIT -> listOf(Note(170.0, 60.0, 170, 0.9, Shape.SINE, noise = 0.25))
        Sfx.CAPTURE -> listOf(n(523.25, 90, 0.6), n(659.25, 90, 0.6), n(783.99, 240, 0.6))
        Sfx.LOST -> listOf(n(392.0, 130, 0.5, Shape.TRI), n(329.63, 130, 0.5, Shape.TRI), n(261.63, 320, 0.5, Shape.TRI))
        Sfx.UPGRADE -> listOf(n(880.0, 60, 0.45), n(1318.5, 60, 0.45), n(1760.0, 160, 0.45))
        Sfx.VICTORY -> listOf(n(523.25, 140, 0.6), n(659.25, 140, 0.6), n(783.99, 140, 0.6), n(1046.5, 420, 0.6))
        Sfx.DEFEAT -> listOf(
            n(261.63, 200, 0.5, Shape.TRI), n(233.08, 200, 0.5, Shape.TRI),
            n(207.65, 200, 0.5, Shape.TRI), n(196.0, 520, 0.5, Shape.TRI)
        )
    }

    fun samples(sfx: Sfx): ShortArray {
        val rng = Random(7)
        val out = ArrayList<Float>()
        for (note in notes(sfx)) {
            val count = note.ms * SAMPLE_RATE / 1000
            var phase = 0.0
            for (i in 0 until count) {
                val t = i.toDouble() / count
                val freq = note.f0 + (note.f1 - note.f0) * t
                phase += 2 * PI * freq / SAMPLE_RATE
                val wave = when (note.shape) {
                    Shape.SINE -> sin(phase)
                    Shape.TRI -> 2.0 / PI * kotlin.math.asin(sin(phase))
                }
                val attack = minOf(1.0, i / (SAMPLE_RATE * 0.003))
                val env = attack * exp(-4.0 * t)
                val noise = note.noise * (rng.nextDouble() * 2 - 1) * exp(-9.0 * t)
                out += ((wave * note.vol + noise) * env).toFloat()
            }
        }
        // Normalize the loudest sample to 90% of full scale, never clipping.
        var peak = 0f
        for (v in out) peak = maxOf(peak, abs(v))
        val gain = if (peak > 0f) 0.9f / peak else 1f
        return ShortArray(out.size) { (out[it] * gain * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort() }
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
