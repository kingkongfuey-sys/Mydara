package com.example.mydara.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Plays the synthesized effects through SoundPool and maps haptic cues onto the system haptic constants
 * (no VIBRATE permission needed). WAV files are generated once into the app cache.
 */
class AndroidFeedback(context: Context, private val view: View) : FeedbackSink {

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val ids = HashMap<Sfx, Int>()
    private val ready: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status -> if (status == 0) ready.add(sampleId) }
        for ((sfx, wav) in SfxSynth.all()) {
            val file = File(context.cacheDir, "sfx_${sfx.name.lowercase()}.wav")
            if (!file.exists() || file.length() != wav.size.toLong()) file.writeBytes(wav)
            ids[sfx] = pool.load(file.absolutePath, 1)
        }
    }

    override fun play(sfx: Sfx, volume: Float, rate: Float) {
        val id = ids[sfx] ?: return
        if (id !in ready) return
        val v = volume.coerceIn(0f, 1f)
        pool.play(id, v, v, 1, 0, rate.coerceIn(0.5f, 2f))
    }

    override fun haptic(haptic: Haptic) {
        val api30 = Build.VERSION.SDK_INT >= 30
        val constant = when (haptic) {
            Haptic.SELECT -> HapticFeedbackConstants.CLOCK_TICK
            Haptic.ARM -> HapticFeedbackConstants.LONG_PRESS
            Haptic.CAPTURE, Haptic.UPGRADE, Haptic.VICTORY ->
                if (api30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
            Haptic.LOST, Haptic.DEFEAT ->
                if (api30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
        }
        view.performHapticFeedback(constant)
    }

    fun release() {
        pool.release()
    }
}

/** Persists the sound / haptics switches. */
object FeedbackPrefs {
    private const val FILE = "mydara_settings"

    fun load(context: Context) {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        FeedbackSettings.sound = p.getBoolean("sound", true)
        FeedbackSettings.haptics = p.getBoolean("haptics", true)
    }

    fun save(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean("sound", FeedbackSettings.sound)
            .putBoolean("haptics", FeedbackSettings.haptics)
            .apply()
    }
}
