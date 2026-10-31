package com.example.mydara.ui

import com.example.mydara.game.Fraction
import com.example.mydara.game.GameEvent
import com.example.mydara.game.GameModel
import com.example.mydara.game.MapSize
import com.example.mydara.game.Outcome
import com.example.mydara.game.Planet
import com.example.mydara.game.PlanetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackTest {

    private class Recorder : FeedbackSink {
        val sounds = ArrayList<Triple<Sfx, Float, Float>>()
        val haptics = ArrayList<Haptic>()
        override fun play(sfx: Sfx, volume: Float, rate: Float) { sounds += Triple(sfx, volume, rate) }
        override fun haptic(haptic: Haptic) { haptics += haptic }
        fun sfx() = sounds.map { it.first }
    }

    private class Rig {
        val rec = Recorder()
        val director = FeedbackDirector(Fraction.GREEN, 1L).also { it.sink = rec }
        val model = GameModel(1L)
        val mine: Planet = model.addPlanet(0.3, 0.3, Fraction.GREEN)
        val enemy: Planet = model.addPlanet(0.5, 0.3, Fraction.RED)
        val enemy2: Planet = model.addPlanet(0.7, 0.3, Fraction.YELLOW)

        init {
            FeedbackSettings.sound = true
            FeedbackSettings.haptics = true
        }
    }

    // ------------------------------------------------------------------ synth

    @Test
    fun everyEffectIsAudibleShortAndNeverClips() {
        for (sfx in Sfx.values()) {
            val pcm = SfxSynth.samples(sfx)
            assertTrue("$sfx empty", pcm.isNotEmpty())
            val seconds = pcm.size.toDouble() / SfxSynth.SAMPLE_RATE
            assertTrue("$sfx too long: $seconds", seconds in 0.02..1.5)
            val peak = pcm.maxOf { Math.abs(it.toInt()) }
            assertTrue("$sfx peak $peak", peak in 20_000..32_767)
        }
    }

    @Test
    fun wavFilesHaveValidHeaders() {
        for ((sfx, wav) in SfxSynth.all()) {
            assertEquals("RIFF", String(wav, 0, 4))
            assertEquals("WAVE", String(wav, 8, 4))
            assertEquals("data", String(wav, 36, 4))
            val dataLen = (wav[40].toInt() and 0xFF) or ((wav[41].toInt() and 0xFF) shl 8) or
                ((wav[42].toInt() and 0xFF) shl 16) or ((wav[43].toInt() and 0xFF) shl 24)
            assertEquals("$sfx", wav.size - 44, dataLen)
            val rate = (wav[24].toInt() and 0xFF) or ((wav[25].toInt() and 0xFF) shl 8)
            assertEquals(SfxSynth.SAMPLE_RATE, rate)
        }
    }

    // ------------------------------------------------------------------ routing

    @Test
    fun playerSendsMakeSoundButAutoAndAiSendsDoNot() {
        val r = Rig()
        r.director.onEvent(GameEvent.UnitsSent(r.mine, r.enemy, Fraction.GREEN, 5, auto = false))
        assertEquals(listOf(Sfx.SEND), r.rec.sfx())
        assertEquals(listOf(Haptic.SEND), r.rec.haptics)
        r.director.nowMs = 1000
        r.director.onEvent(GameEvent.UnitsSent(r.mine, r.enemy, Fraction.GREEN, 5, auto = true))
        r.director.onEvent(GameEvent.UnitsSent(r.enemy, r.mine, Fraction.RED, 5, auto = false))
        assertEquals(1, r.rec.sounds.size)
    }

    @Test
    fun captureAndLossHaveDistinctCuesAndAiFightsAreQuiet() {
        val r = Rig()
        r.director.onEvent(GameEvent.PlanetCaptured(r.enemy, Fraction.GREEN, Fraction.RED))
        r.director.nowMs = 1000
        r.director.onEvent(GameEvent.PlanetCaptured(r.mine, Fraction.RED, Fraction.GREEN))
        r.director.nowMs = 2000
        r.director.onEvent(GameEvent.PlanetCaptured(r.enemy2, Fraction.RED, Fraction.YELLOW))
        assertEquals(listOf(Sfx.CAPTURE, Sfx.LOST, Sfx.CAPTURE), r.rec.sfx())
        assertEquals(listOf(Haptic.CAPTURE, Haptic.LOST), r.rec.haptics) // AI vs AI: no vibration
        assertTrue(r.rec.sounds[2].second < 0.3f)
    }

    @Test
    fun hitsAreLoudestWhenThePlayerIsInvolved() {
        val r = Rig()
        r.director.onEvent(GameEvent.PlanetHit(r.mine, Fraction.RED))
        r.director.nowMs = 500
        r.director.onEvent(GameEvent.PlanetHit(r.enemy, Fraction.GREEN))
        r.director.nowMs = 1000
        r.director.onEvent(GameEvent.PlanetHit(r.enemy2, Fraction.RED))
        val v = r.rec.sounds.map { it.second }
        assertTrue(v[0] > v[1] && v[1] > v[2])
        assertEquals(listOf(Haptic.HIT), r.rec.haptics)
    }

    @Test
    fun rapidRepeatsAreThrottled() {
        val r = Rig()
        repeat(10) { r.director.onEvent(GameEvent.UnitDied(0.5, 0.5, Fraction.RED)) } // same instant
        assertEquals(1, r.rec.sounds.size)
        r.director.nowMs = 100
        r.director.onEvent(GameEvent.UnitDied(0.5, 0.5, Fraction.RED))
        assertEquals(2, r.rec.sounds.size)
        assertTrue(r.rec.sounds.all { it.third in 0.9f..1.1f }) // pitch jitter stays small
    }

    @Test
    fun switchesSilenceEverything() {
        val r = Rig()
        FeedbackSettings.sound = false
        r.director.onEvent(GameEvent.PlanetCaptured(r.enemy, Fraction.GREEN, Fraction.RED))
        assertTrue(r.rec.sounds.isEmpty())
        assertEquals(listOf(Haptic.CAPTURE), r.rec.haptics) // haptics have their own switch
        FeedbackSettings.sound = true
        FeedbackSettings.haptics = false
        r.director.nowMs = 1000
        r.director.onEvent(GameEvent.PlanetCaptured(r.enemy, Fraction.GREEN, Fraction.RED))
        assertEquals(1, r.rec.sounds.size)
        assertEquals(1, r.rec.haptics.size)
        FeedbackSettings.haptics = true
    }

    @Test
    fun outcomeJinglesAndUpgradeCue() {
        val r = Rig()
        r.director.onOutcome(Outcome.VICTORY)
        r.director.onOutcome(Outcome.PLAYING)
        r.director.nowMs = 1000
        r.director.onOutcome(Outcome.DEFEAT)
        r.director.onEvent(GameEvent.Upgraded(r.mine, PlanetType.DEFENSIVE))
        r.director.onEvent(GameEvent.Upgraded(r.enemy, PlanetType.DEFENSIVE)) // not the player's
        assertEquals(listOf(Sfx.VICTORY, Sfx.DEFEAT, Sfx.UPGRADE), r.rec.sfx())
    }

    // ------------------------------------------------------------------ session wiring

    @Test
    fun selectingPlanetTicksAndLongPressArmsWithoutDoubleCue() {
        FeedbackSettings.sound = true; FeedbackSettings.haptics = true
        val s = GameSession(GameConfig(size = MapSize.SMALL, seed = 5L))
        val rec = Recorder()
        s.feedback.sink = rec
        s.camera.resize(1000.0, 1600.0)
        val own = s.model.planetsOf(Fraction.PLAYER).single()
        val x = s.camera.toScreenX(own.x); val y = s.camera.toScreenY(own.y)

        s.input.pointerDown(1, x, y, 0); s.input.pointerUp(1, x, y, 50)
        s.advance(0.02, 100)
        assertEquals(listOf(Sfx.TICK), rec.sfx())

        // deselect, then long-press: only the arm cue, no extra tick
        s.input.pointerDown(1, x, y, 1000); s.input.pointerUp(1, x, y, 1050)
        s.advance(0.02, 1100)
        rec.sounds.clear(); rec.haptics.clear()
        s.input.pointerDown(1, x, y, 2000)
        s.advance(0.02, 2600)
        assertEquals(listOf(Sfx.ARM), rec.sfx())
        assertEquals(listOf(Haptic.ARM), rec.haptics)
    }

    @Test
    fun fullMatchProducesSaneFeedbackStream() {
        FeedbackSettings.sound = true; FeedbackSettings.haptics = true
        val s = GameSession(GameConfig(size = MapSize.SMALL, opponents = 1, seed = 11L))
        val rec = Recorder()
        s.feedback.sink = rec
        s.camera.resize(1000.0, 1600.0)
        val own = s.model.planetsOf(Fraction.PLAYER).single()
        own.units = 40.0
        s.model.velocity = 2.0
        var now = 0L
        // player attacks its first neighbor once, then everything runs on its own
        s.model.send(own, own.links.first().other(own))
        repeat(3600) { s.advance(1.0 / 60.0, now); now += 16 }
        assertTrue(rec.sounds.isNotEmpty())
        assertTrue(rec.sounds.all { it.second in 0f..1f && it.third in 0.5f..2f })
        assertTrue(Sfx.SEND in rec.sfx())
        // throttling keeps the stream far below one cue per frame
        assertTrue("too many cues: ${rec.sounds.size}", rec.sounds.size < 1500)
        assertFalse(rec.sounds.isEmpty() && rec.haptics.isEmpty())
    }
}
