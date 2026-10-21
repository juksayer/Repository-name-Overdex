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

/** Moondrop retained-pixel acceptance check; reads evidence without editing profiles. */
class RecordedOctober14HpDeviceTest {
    @Test fun whiteBodyAndBlueBorderStayTrackedAtFrameRate() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val hashes = listOf(
            "a1de5232881e7dde061a7ca3c7afc465b138215489d1d1ac2f8ecb7f7d1345e3",
            "db00a72ff08adc12ab1c69e3a0f1421486c9d60ed8a0b5c3819f2970bbc021d9",
        )
        val images = hashes.map { hash ->
            requireNotNull(BitmapFactory.decodeFile(File(context.filesDir, "artifacts/frames/sha256/$hash.png").path)) {
                "This device check requires the retained October 14 fa89d58b frames"
            }
        }
        val tracker = LiveBattlefieldHpTracker(BattleCalibration(
            trainerHpRegion = AnchorRegion(.05314814f, .24583323f, .30592608f, .4775002f),
            opponentHpRegion = AnchorRegion(.46500003f, .25583324f, .39185193f, .32250035f),
        ))
        val timings = mutableListOf<Double>()
        try {
            repeat(70) { index ->
                val image = images[index % 2]
                // Test playback clock; original PNG modification times are not cadence evidence.
                val start = System.nanoTime()
                val bars = tracker.measureFrame(CapturedVisualFrame(image, 1000L + index * 34L, 1_000_000_000L + index * 34_000_000L))
                if (index >= 10) timings += (System.nanoTime() - start) / 1_000_000.0
                assertEquals(ActivePokemonSide.entries.toSet(), bars.keys)
                val player = bars.getValue(ActivePokemonSide.PLAYER)
                val opponent = bars.getValue(ActivePokemonSide.OPPONENT)
                assertTrue(player.centerX < opponent.centerX)
                assertEquals(if (index % 2 == 0) .99f else .72f, player.filledFraction, .03f)
                assertEquals(if (index % 2 == 0) .96f else .81f, opponent.filledFraction, .03f)
                val appearance = requireNotNull(ActiveHpBarBorderAppearanceMeasurer.measure(image, opponent))
                assertTrue(if (index % 2 == 0) appearance.whiteFraction > .8f else appearance.blueFraction > .8f)
            }
            val sorted = timings.sorted()
            instrumentation.sendStatus(0, Bundle().apply {
                putString("hp_timing", "frames=${timings.size} median_ms=${sorted[sorted.size / 2]} p90_ms=${sorted[sorted.size * 9 / 10]} max_ms=${sorted.last()}")
            })
        } finally { images.forEach { it.recycle() } }
    }
}
