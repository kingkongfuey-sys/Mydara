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
        val sounds = ArrayList<Pair<Sfx, Float>>()
        val haptics = ArrayList<Haptic>()
        override fun play(sfx: Sfx, volume: Float, rate: Float) { sounds += sfx to volume }
        override fun haptic(haptic: Haptic) { haptics += haptic }
        fun sfx() = sounds.map { it.first }
    }

    private class Rig {
        val rec = Recorder()
        val director = FeedbackDirector(Fraction.GREEN).also { it.sink = rec }
        val model = GameModel(1L)
        val mine: Planet = model.addPlanet(0.3, 0.3, Fraction.GREEN)
        val enemy: Planet = model.addPlanet(0.5, 0.3, Fraction.RED)
        val enemy2: Planet = model.addPlanet(0.7, 0.3, Fraction.YELLOW)
        val neutral: Planet = model.addPlanet(0.9, 0.3)
        val link = model.addLink(mine, enemy)

        init {
            FeedbackSettings.sound = true
            FeedbackSettings.haptics = true
        }
    }

    // ------------------------------------------------------------------ synth

    @Test
    fun everyEffectIsShortAndSoft() {
        for (sfx in Sfx.values()) {
            val pcm = SfxSynth.samples(sfx)
            assertTrue("$sfx empty", pcm.isNotEmpty())
            val seconds = pcm.size.toDouble() / SfxSynth.SAMPLE_RATE
            assertTrue("$sfx too long: $seconds", seconds in 0.02..1.6)
            val peak = pcm.maxOf { Math.abs(it.toInt()) }
            assertTrue("$sfx peak $peak (soft = about 60% of full scale)", peak in 15_000..22_000)
        }
    }

    @Test
    fun effectsAreDeterministicDistinctAndEndWithoutAClick() {
        val all = Sfx.values().associateWith { SfxSynth.samples(it) }
        for ((sfx, pcm) in all) {
            assertTrue("$sfx is not reproducible", pcm.contentEquals(SfxSynth.samples(sfx)))
            val tail = pcm.takeLast(4).maxOf { Math.abs(it.toInt()) }
            assertTrue("$sfx ends abruptly ($tail)", tail < 800)
        }
        val list = all.values.toList()
        for (i in list.indices) for (j in i + 1 until list.size) {
            assertFalse("two effects are identical", list[i].contentEquals(list[j]))
        }
    }

    @Test
    fun repeatedSoundsVaryALittleInPitchButResultsDoNot() {
        FeedbackSettings.sound = true
        val rates = ArrayList<Pair<Sfx, Float>>()
        val director = FeedbackDirector(Fraction.GREEN)
        director.sink = object : FeedbackSink {
            override fun play(sfx: Sfx, volume: Float, rate: Float) { rates += sfx to rate }
            override fun haptic(haptic: Haptic) = Unit
        }
        var now = 0L
        repeat(40) { director.nowMs = now; director.onSelect(); now += 1000 }
        director.onOutcome(Outcome.VICTORY)
        val ticks = rates.filter { it.first == Sfx.TICK }.map { it.second }
        assertTrue(ticks.all { it in 0.96f..1.04f })
        assertTrue("pitch never varies", ticks.toSet().size > 10)
        assertEquals(1f, rates.single { it.first == Sfx.VICTORY }.second, 0f)
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
        }
    }

    // ------------------------------------------------------------------ routing: only what happens to YOU

    @Test
    fun onlyYourManualSendsSound() {
        val r = Rig()
        r.director.onEvent(GameEvent.UnitsSent(r.mine, r.enemy, Fraction.GREEN, 5, auto = false))
        assertEquals(listOf(Sfx.SEND), r.rec.sfx())
        assertTrue(r.rec.haptics.isEmpty()) // sends never vibrate
        r.director.nowMs = 1000
        r.director.onEvent(GameEvent.UnitsSent(r.mine, r.enemy, Fraction.GREEN, 5, auto = true))
        r.director.onEvent(GameEvent.UnitsSent(r.enemy, r.mine, Fraction.RED, 5, auto = false))
        assertEquals(1, r.rec.sounds.size)
    }

    @Test
    fun aiTakingNeutralPlanetsOrFightingEachOtherIsSilent() {
        val r = Rig()
        r.director.onEvent(GameEvent.PlanetCaptured(r.neutral, Fraction.RED, Fraction.NEUTRAL))
        r.director.nowMs = 1000
        r.director.onEvent(GameEvent.PlanetCaptured(r.enemy2, Fraction.RED, Fraction.YELLOW))
        r.director.nowMs = 2000
        r.director.onEvent(GameEvent.PlanetHit(r.enemy, Fraction.YELLOW))
        r.director.onEvent(GameEvent.UnitDied(0.5, 0.5, Fraction.RED, r.link))
        r.director.onEvent(GameEvent.UnitDied(0.5, 0.5, Fraction.GREEN, r.link)) // even your own units dying
        assertTrue(r.rec.sounds.isEmpty())
        assertTrue(r.rec.haptics.isEmpty())
    }

    @Test
    fun capturesAndLossesOfYourPlanetsHaveDistinctQuietCues() {
        val r = Rig()
        r.director.onEvent(GameEvent.PlanetCaptured(r.neutral, Fraction.GREEN, Fraction.NEUTRAL))
        r.director.nowMs = 1000
        r.director.onEvent(GameEvent.PlanetCaptured(r.mine, Fraction.RED, Fraction.GREEN))
        assertEquals(listOf(Sfx.CAPTURE, Sfx.LOST), r.rec.sfx())
        assertEquals(listOf(Haptic.CAPTURE, Haptic.LOST), r.rec.haptics)
        assertTrue(r.rec.sounds.all { it.second <= 0.4f })
    }

    @Test
    fun onlyHitsOnYourPlanetsSound() {
        val r = Rig()
        r.director.onEvent(GameEvent.PlanetHit(r.enemy, Fraction.GREEN)) // you attack: silent
        assertTrue(r.rec.sounds.isEmpty())
        r.director.onEvent(GameEvent.PlanetHit(r.mine, Fraction.RED))
        assertEquals(listOf(Sfx.HIT), r.rec.sfx())
        assertTrue(r.rec.haptics.isEmpty())
    }

    @Test
    fun soundsAreRateLimitedGlobally() {
        val r = Rig()
        r.director.onSelect()                        // t=0
        r.director.nowMs = 100
        r.director.onEvent(GameEvent.Upgraded(r.mine, PlanetType.DEFENSIVE)) // 100ms later: dropped
        assertEquals(1, r.rec.sounds.size)
        r.director.nowMs = 200
        r.director.onEvent(GameEvent.Upgraded(r.mine, PlanetType.DEFENSIVE)) // 200ms after the tick: allowed
        assertEquals(2, r.rec.sounds.size)
        // the match result always plays
        r.director.nowMs = 210
        r.director.onOutcome(Outcome.VICTORY)
        assertEquals(Sfx.VICTORY, r.rec.sfx().last())
    }

    @Test
    fun everyVolumeIsBackgroundLevel() {
        val all = listOf(
            FeedbackDirector.VOL_TICK, FeedbackDirector.VOL_ARM, FeedbackDirector.VOL_SEND, FeedbackDirector.VOL_HIT,
            FeedbackDirector.VOL_CAPTURE, FeedbackDirector.VOL_LOST, FeedbackDirector.VOL_UPGRADE,
            FeedbackDirector.VOL_RESULT
        )
        assertTrue(all.all { it in 0.05f..0.4f })
    }

    @Test
    fun switchesSilenceEverything() {
        val r = Rig()
        FeedbackSettings.sound = false
        r.director.onEvent(GameEvent.PlanetCaptured(r.neutral, Fraction.GREEN, Fraction.NEUTRAL))
        assertTrue(r.rec.sounds.isEmpty())
        assertEquals(listOf(Haptic.CAPTURE), r.rec.haptics) // haptics have their own switch
        FeedbackSettings.sound = true
        FeedbackSettings.haptics = false
        r.director.nowMs = 1000
        r.director.onEvent(GameEvent.PlanetCaptured(r.neutral, Fraction.GREEN, Fraction.NEUTRAL))
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
        r.director.nowMs = 2000
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
    fun aMatchStaysQuietForAPassivePlayer() {
        FeedbackSettings.sound = true; FeedbackSettings.haptics = true
        val s = GameSession(GameConfig(size = MapSize.MEDIUM, opponents = 2, seed = 11L))
        val rec = Recorder()
        s.feedback.sink = rec
        s.camera.resize(1000.0, 1600.0)
        s.model.velocity = 2.0
        var now = 0L
        repeat(3600) { s.advance(1.0 / 60.0, now); now += 16 } // one minute, player does nothing
        // AIs expanding and fighting each other makes no noise; only things that hit the player do
        assertTrue("too many cues: ${rec.sounds.size}", rec.sounds.size <= 12)
        assertTrue(rec.sounds.all { it.second <= 0.4f })
        assertFalse(rec.sfx().any { it == Sfx.SEND || it == Sfx.UPGRADE || it == Sfx.CAPTURE })
    }
}
