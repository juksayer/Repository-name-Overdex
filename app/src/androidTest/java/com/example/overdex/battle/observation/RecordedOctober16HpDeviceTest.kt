package com.example.overdex.battle.observation

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.data.BattleCalibration
import com.example.overdex.model.AnchorRegion
import com.example.overdex.model.observation.CapturedVisualFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Runs original Moondrop frames through both live acquisition and local tracking. */
class RecordedOctober16HpDeviceTest {
    @Test fun orangeCameruptArtDoesNotSuppressEitherBar() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val image = requireNotNull(BitmapFactory.decodeFile(File(instrumentation.targetContext.filesDir,
            "artifacts/frames/sha256/2f0e2d48cd592c78e58a452a68800066a3d1f9c39413bf23a78360b2f725f157.png").path)) {
            "Requires the retained October 16 15:15 Camerupt match frame"
        }
        val tracker = LiveBattlefieldHpTracker(BattleCalibration(
            trainerHpRegion = AnchorRegion(.05314814f, .24583323f, .30592608f, .4775002f),
            opponentHpRegion = AnchorRegion(.46500003f, .25583324f, .39185193f, .32250035f),
        ))
        val timings = mutableListOf<Double>()
        try {
            repeat(60) { index ->
                // Synthetic times test acquisition/cache/local tracking, not actual attack count.
                val frame = CapturedVisualFrame(image, 1000L + index * 34L, 1_000_000_000L + index * 34_000_000L)
                val start = System.nanoTime()
                val bars = tracker.measureFrame(frame)
                if (index >= 10) timings += (System.nanoTime() - start) / 1_000_000.0
                assertEquals(ActivePokemonSide.entries.toSet(), bars.keys)
                assertEquals(.725f, bars.getValue(ActivePokemonSide.PLAYER).filledFraction, .02f)
                assertEquals(.878f, bars.getValue(ActivePokemonSide.OPPONENT).filledFraction, .02f)
                assertEquals(bars.getValue(ActivePokemonSide.PLAYER), tracker.measure(frame, ActivePokemonSide.PLAYER))
                assertEquals(bars.getValue(ActivePokemonSide.OPPONENT), tracker.measure(frame, ActivePokemonSide.OPPONENT))
            }
            val sorted = timings.sorted()
            instrumentation.sendStatus(0, Bundle().apply {
                putString("camerupt_hp_timing", "frames=${sorted.size} median_ms=${sorted[sorted.size / 2]} p90_ms=${sorted[sorted.size * 9 / 10]} max_ms=${sorted.last()}")
            })
        } finally { image.recycle() }
    }

    @Test fun whiteAbomasnowOutlineRetainsBothCombatants() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val hashes = listOf(
            "d908de8853d88a07b15203619e3df23e91cf4ad6f4ce5fea8713644497ba4438",
            "ef41adc21e2c07ddf4c876e9e98cb07ab5744968e73cd4a0bda473cf1dfe65a8",
            "e08654352cb8debfd686a94ac19831e75725802e9e51f77fe6a3e0a2fb119d90",
        )
        val images = hashes.map { hash ->
            requireNotNull(BitmapFactory.decodeFile(File(instrumentation.targetContext.filesDir,
                "artifacts/frames/sha256/$hash.png").path)) {
                "Requires retained October 16 SFX-only match frames from the Moondrop"
            }
        }
        val calibration = BattleCalibration(
            trainerHpRegion = AnchorRegion(.05314814f, .24583323f, .30592608f, .4775002f),
            opponentHpRegion = AnchorRegion(.46500003f, .25583324f, .39185193f, .32250035f),
        )
        val expectedHp = listOf(.886f, .773f, .665f)
        val tracker = LiveBattlefieldHpTracker(calibration)
        val timings = mutableListOf<Double>()
        try {
            repeat(63) { index ->
                val image = images[index % images.size]
                // Synthetic playback times exercise the scanner, not observed attack cadence.
                val frame = CapturedVisualFrame(image, 1000L + index * 34L, 1_000_000_000L + index * 34_000_000L)
                val start = System.nanoTime()
                val bars = (if (index < 3) LiveBattlefieldHpTracker(calibration) else tracker).measureFrame(frame)
                if (index >= 13) timings += (System.nanoTime() - start) / 1_000_000.0
                assertEquals(ActivePokemonSide.entries.toSet(), bars.keys)
                assertEquals(expectedHp[index % images.size], bars.getValue(ActivePokemonSide.PLAYER).filledFraction, .02f)
                assertTrue(bars.getValue(ActivePokemonSide.OPPONENT).filledFraction > .97f)
            }
            val sorted = timings.sorted()
            instrumentation.sendStatus(0, Bundle().apply {
                putString("hp_timing", "frames=${sorted.size} median_ms=${sorted[sorted.size / 2]} p90_ms=${sorted[sorted.size * 9 / 10]} max_ms=${sorted.last()}")
            })
        } finally { images.forEach { it.recycle() } }
    }
}
