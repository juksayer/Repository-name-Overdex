package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.CompleteHpBarObserved
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.custody.TerminalParticleObservations
import com.example.overdex.battle.archive.ArchivedCompleteHpBarObserved
import com.example.overdex.battle.archive.MatchArchiveSerializer
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.battle.inference.TerminalCollapseSequences
import com.example.overdex.battle.inference.TerminalEffectFrame
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import java.util.zip.ZipFile

/** Video-derived pictures exercise measurement only; their test clocks are simulated. */
class RecordedFastDepartureCaptureTest {
    @Test fun `recorded vaporeon bar opens a window spanning the reviewed departure`() {
        val path = System.getenv("OVERDEX_SLIVER_VIDEO_ARCHIVE")
        assumeTrue(!path.isNullOrBlank())
        val archive = ZipFile(requireNotNull(path)).use { zip -> MatchArchiveSerializer.deserialize(
            zip.getInputStream(zip.getEntry("timeline.json")).bufferedReader().readText()) }
        val row = archive.articles.single { it.articleId == "d5390588-c98b-45d6-8714-acc9ab0611af" }
        val p = row.payload as ArchivedCompleteHpBarObserved
        val at = requireNotNull(row.monotonicTimeNanos)
        var now = at + (row.recordedAt - row.perceivedAt) * 1_000_000L
        val gate = TerminalVisualCaptureGate { now }
        gate.beginSession(at - 1_000_000_000L)
        gate.observe(RealityArticle(ArticleId(row.articleId), row.perceivedAt, row.recordedAt,
            SourceId(row.sourceId), CompleteHpBarObserved(ActivePokemonSide.OPPONENT,
                p.barLeft, p.barTop, p.barRight, p.barBottom, p.sourceWidth, p.sourceHeight,
                p.interiorColumns, p.liveColumns, p.damageColumns, p.status, p.candidateStartedAtNanos,
                p.appearanceStartedAtNanos, p.railBrightness, p.backgroundBrightness,
                p.backgroundMaskedColumns, p.unmaskedUncertainColumns), monotonicTimeNanos = at))
        val window = requireNotNull(gate.captureWindow(ActivePokemonSide.OPPONENT))
        assertEquals(TerminalVisualTrigger.COMPLETE_BAR_DAMAGE_HINT, window.trigger)
        assertEquals(listOf(row.articleId), window.evidenceReferences)
        // The review placed the departure ~2.0 s after this bar. This checks
        // scheduling coverage, not that live frames were actually delivered.
        assertTrue(at + 2_000_000_000L in now until window.until)
    }

    @Test fun `vaporeon departure and a living switch remain raw bounded samples`() {
        val root = System.getenv("OVERDEX_DEPARTURE_STUDY")
        assumeTrue(!root.isNullOrBlank())
        for (case in listOf("vaporeon", "switch")) {
            val files = requireNotNull(File(requireNotNull(root), case).listFiles())
                .filter { it.extension == "png" }.sortedBy { it.name }
            assertTrue("$case has retained video frames", files.size >= 20)
            var now = 100_000_000_000L
            val start = now
            val reader = TerminalParticleFrameMeasurer()
            val batches = mutableListOf<TerminalParticleObservations>()
            val buffer = TerminalParticleObservationBuffer(reader::reset, { payload, _, _, _ -> batches += payload }, { now })
            buffer.synchronize(TerminalParticleAssignment(ActivePokemonSide.OPPONENT, start,
                start + 3_000_000_000L, emptyList()), 1000)
            for (file in files) {
                val image = requireNotNull(ImageIO.read(file))
                assertEquals(336, image.width); assertEquals(747, image.height)
                val bounds = BattleCropBounds(84, 171, 336, 605)
                buffer.accept(image.width, image.height, bounds, now, 1000, null, true) {
                    reader.measure(image.width, image.height, bounds, now, image::getRGB)
                }
                now += 100_000_000L
            }
            buffer.synchronize(null, 1000)
            val samples = batches.flatMap { it.samples }
            assertTrue("$case captures source frames within budget", samples.size in 20..30)
            assertEquals("BASELINE", samples.first().status)
            assertTrue(samples.drop(1).any { it.status == "SAMPLED" || it.status == "CLUTTER" })
            assertTrue(samples.all { it.sourceAtNanos in start until start + 3_000_000_000L })
            assertTrue(batches.last().ended)
            val collapse = TerminalCollapseSequences.find(samples.mapIndexed { index, sample ->
                TerminalEffectFrame("video-frame-$index", sample)
            })
            if (case == "switch") assertTrue("a living switch is not a confirmed faint", collapse.isEmpty())
            if (case == "vaporeon") assertEquals("reviewed departure produces one visual candidate", 1, collapse.size)
            // Pixel samples alone are never a faint or a move attribution.
            println("$case: ${samples.size} simulated-cadence source frames, " +
                "${samples.count { it.points.isNotEmpty() }} with particle features, " +
                "${collapse.size} visual collapse candidates")
        }
    }
}
