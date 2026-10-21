package com.example.overdex.battle.observation

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.data.BattleCalibration
import com.example.overdex.model.AnchorRegion
import com.example.overdex.model.observation.CapturedVisualFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Original pixels from the October 18 paired private match, not reconstructed replay. */
class RecordedOctober18PairedDeviceTest {
    @Test fun nightBattleDecisionTimersAreLocatedAndRead() = kotlinx.coroutines.runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        for ((name, expected) in listOf("moondrop-night-9.png" to 9, "pixel-night-11.png" to 11)) {
            val bitmap = instrumentation.context.assets.open("battle/oct18/$name").use(BitmapFactory::decodeStream)!!
            try {
                org.junit.Assert.assertTrue(name, DecisionTimerAppearance.hasDial(bitmap))
                val text = DecisionTimerTextReader.read(bitmap)
                assertEquals("$name: $text", expected, DecisionTimerReadingFilter.parse(text))
            } finally { bitmap.recycle() }
        }
    }

    @Test fun qteArtworkIsNotCountdownButRealGoStillMatches() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        CountdownGlyphMatcher.initialize(instrumentation.targetContext)
        for (name in listOf("abomasnow-qte-not-go.png", "real-go.png")) {
            val image = instrumentation.context.assets.open("battle/oct18/$name").use(BitmapFactory::decodeStream)!!
            try {
                val result = CountdownGlyphMatcher.match(image)
                if (name == "real-go.png") assertEquals("GO", result.candidate)
                else assertNull("QTE artwork matched ${result.candidate}: ${result.similarity}", result.candidate)
            } finally { image.recycle() }
        }
    }

    @Test fun openingHpAcquisitionAndTracking() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val cases = listOf(
            "moondrop-opening.png" to BattleCalibration(
                trainerHpRegion = AnchorRegion(.05314814f, .24583323f, .30592608f, .4775002f),
                opponentHpRegion = AnchorRegion(.46500003f, .25583324f, .39185193f, .32250035f),
            ),
            "pixel-opening.png" to BattleCalibration(
                trainerHpRegion = AnchorRegion(.023148147f, .23083325f, .47592592f, .5875001f),
                opponentHpRegion = AnchorRegion(.5f, .24083324f, .47685185f, .5075002f),
            ),
        )
        for ((name, calibration) in cases) {
            val image = instrumentation.context.assets.open("battle/oct18/$name").use(BitmapFactory::decodeStream)!!
            try {
                val tracker = LiveBattlefieldHpTracker(calibration)
                val elapsed = (0 until 16).map { i ->
                    val frame = CapturedVisualFrame(image, 1000L + i * 50L, 1_000_000_000L + i * 50_000_000L)
                    val start = System.nanoTime()
                    val bars = tracker.measureFrame(frame)
                    val ms = (System.nanoTime() - start) / 1_000_000.0
                    assertEquals(name, ActivePokemonSide.entries.toSet(), bars.keys)
                    ms
                }
                instrumentation.sendStatus(0, Bundle().apply {
                    putString("opening_hp_timing", "$name first_ms=${elapsed.first()} warm_median_ms=${elapsed.drop(6).sorted()[5]} max_ms=${elapsed.max()}")
                })
            } finally { image.recycle() }
        }
    }
}
