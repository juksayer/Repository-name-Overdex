package com.example.overdex.battle.observation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.data.*
import com.example.overdex.data.local.PokedexDatabase
import com.example.overdex.data.observation.OcrPriority
import com.example.overdex.data.observation.SharedLatinTextRecognizer
import com.example.overdex.model.AnchorRegion
import com.example.overdex.model.observation.CapturedVisualFrame
import com.example.overdex.model.observation.ObservationInput
import com.example.overdex.model.observation.SessionSource
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Exercise production capture/admission with recorded pixels, without changing saved profiles. */
class PreBattleSpeciesAttentionDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun load(path: String) = instrumentation.context.assets.open(path).use(BitmapFactory::decodeStream)!!
    private suspend fun await(condition: () -> Boolean) = withTimeout(45_000) {
        while (!condition()) delay(20)
    }

    private inner class Fixture(val calibration: BattleCalibration, initial: Bitmap) : AutoCloseable {
        private val context = instrumentation.targetContext
        val current = AtomicReference(CapturedVisualFrame(initial, 0, 0))
        val allowed = AtomicBoolean(true)
        val timeline = InMemoryRealityTimeline()
        val match = Match("prebattle-attention-${System.nanoTime()}", custody = InMemoryTestimonyCustody(), realityTimeline = timeline,
            pokemonKnowledge = PokemonRepository(PokedexDatabase.getDatabase(context).pokemonDao(),
                LocalSpriteProvider(context.assets), GameMasterLoader(context)))
        val store = FileCropArtifactStore(File(context.cacheDir, match.matchId).apply { mkdirs() })
        val input = object : ObservationInput {
            override val source = SessionSource.LIVE_CAPTURE
            override suspend fun supply(onVisualData: suspend (Bitmap) -> Unit) {
                while (true) { onVisualData(current.get().bitmap); delay(40) }
            }
            override suspend fun supplyFrames(onFrame: suspend (CapturedVisualFrame) -> Unit) {
                while (true) {
                    onFrame(current.get().copy(capturedAtWallTimeMillis = System.currentTimeMillis(),
                        capturedAtMonotonicTimeNanos = System.nanoTime()))
                    delay(40)
                }
            }
        }
        val pipelines = listOf(
            LiveOverlaySpeciesPipeline.player(input, calibration, store, { false }) { allowed.get() },
            LiveOverlaySpeciesPipeline.opponent(input, calibration, store, { false }) { allowed.get() },
        )
        fun start() = pipelines.forEach { it.start(match) }
        fun crops() = timeline.getArticles().filter { it.payload is CropCaptured }
        override fun close() {
            pipelines.forEach { it.stop() }
            match.release()
            DroidballOverlayPresentation.clearOpponentSpecies()
        }
    }

    // These saved name/resource rows were displaced in the actual October 19
    // frame. The existing locator must recover them before probing the badge.
    private val calibration = BattleCalibration(
        playerSpeciesNameRegion = AnchorRegion(.032738097f, .09930009f, .23214285f, .021433357f),
        opponentSpeciesNameRegion = AnchorRegion(.73363096f, .09930009f, .23363096f, .021433355f),
        playerTeamInfoRegion = AnchorRegion(.017857144f, .097602166f, .38934523f, .050234426f),
        opponentTeamInfoRegion = AnchorRegion(.59375f, .09260216f, .38339287f, .050234426f),
    )
    private val hud = AnchorRegion(401.95557f / 672, 144.32333f / 1493, 258.22223f / 672, 240f / 1493)

    @Test fun teamSelectLeavesSpeciesLaneFreeAndNativeBadgeWakesBeforeGo() = runBlocking {
        val party = load("team_select/oct21/team-select.png")
        val battle = load("species/oct19/sneasel.png")
        try {
            SharedLatinTextRecognizer.warmUp()
            Fixture(calibration, party).use { f ->
                val held = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val blocker = launch(Dispatchers.Default) {
                    SharedLatinTextRecognizer.withReadingTurn(OcrPriority.NORMAL) { held.complete(Unit); release.await() }
                }
                try {
                    held.await()
                    f.start()
                    delay(1_200)
                    assertTrue("Menu pixels must not become active-species crops", f.crops().isEmpty())
                    assertEquals(0, SharedLatinTextRecognizer.waitingCount(OcrPriority.URGENT))
                    assertFalse(f.match.speciesChecks.isChecking(ActivePokemonSide.PLAYER))
                    assertFalse(f.match.speciesChecks.isChecking(ActivePokemonSide.OPPONENT))
                    val appearedAt = System.nanoTime()
                    f.current.set(CapturedVisualFrame(battle, 0, 0, overlayRegion = hud))
                    await { f.crops().map { (it.payload as CropCaptured).cropProvenance.cropName }.distinct().size == 2 }
                    await { SharedLatinTextRecognizer.waitingCount(OcrPriority.URGENT) == 2 }
                    val capturedMillis = (System.nanoTime() - appearedAt) / 1_000_000
                    assertTrue(f.timeline.getArticles().none { it.payload is CountdownGlyphWitnessed || it.payload is MatchStarted })
                    release.complete(Unit)
                    await { f.timeline.getArticles().any { (it.payload as? ActivePokemonSpeciesWitnessed)?.let { p ->
                        p.side == ActivePokemonSide.OPPONENT && p.speciesName == "Sneasel" } == true } }
                    instrumentation.sendStatus(0, Bundle().apply {
                        putString("native_badge_wake", "cropAdmissionMs=$capturedMillis identityMs=${(System.nanoTime() - appearedAt) / 1_000_000}")
                    })
                    assertTrue("The badge probe must not invent a countdown/start", f.timeline.getArticles().none {
                        it.payload is CountdownGlyphWitnessed || it.payload is MatchStarted })
                } finally { release.complete(Unit); blocker.cancelAndJoin() }
            }
        } finally { delay(200); party.recycle(); battle.recycle() }
    }

    @Test fun entryCueWakesAReadableNameEvenWhenTheWholeBadgeIsUnavailable() = runBlocking {
        val crop = load("species/oct16/sneasel.png")
        val nameOnly = Bitmap.createBitmap(672, 1493, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
            Canvas(this).drawBitmap(crop, 493f, 96f, null)
        }
        crop.recycle()
        try {
            SharedLatinTextRecognizer.warmUp()
            Fixture(BattleCalibration(opponentSpeciesNameRegion = AnchorRegion(493/672f, 96/1493f, 157/672f, 32/1493f)), nameOnly).use { f ->
                f.start()
                delay(500)
                assertTrue(f.crops().isEmpty())
                val cueAt = System.nanoTime()
                f.match.custody.submitTestimony(SourceId("ANNOUNCEMENT_WITNESS"), RawTestimony("Go, Sneasel!"),
                    System.currentTimeMillis(), null, emptyList(), cueAt)
                await { f.timeline.getArticles().any {
                    it.sourceId.id == "OPPONENT_SPECIES_OVERLAY_PIPELINE" &&
                        (it.payload as? ActivePokemonSpeciesWitnessed)?.speciesName == "Sneasel" } }
                val check = f.timeline.getArticles().first { (it.payload as? SpeciesCheckMeasured)?.let { p ->
                    p.side == ActivePokemonSide.OPPONENT && p.reason == "ENTRY" } == true }
                assertEquals(cueAt, (check.payload as SpeciesCheckMeasured).triggerMonotonicNanos)
                assertTrue(f.timeline.getArticles().none { it.payload is CountdownGlyphWitnessed })
            }
        } finally { delay(200); nameOnly.recycle() }
    }

    @Test fun ignoredAttemptDoesNotSpendQueuedOcrButKeepsItsOriginalCrops() = runBlocking {
        val battle = load("species/oct19/sneasel.png")
        try {
            SharedLatinTextRecognizer.warmUp()
            Fixture(calibration, battle).use { f ->
                f.current.set(CapturedVisualFrame(battle, 0, 0, overlayRegion = hud))
                val held = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val blocker = launch(Dispatchers.Default) {
                    SharedLatinTextRecognizer.withReadingTurn(OcrPriority.NORMAL) { held.complete(Unit); release.await() }
                }
                try {
                    held.await(); f.start()
                    await { SharedLatinTextRecognizer.waitingCount(OcrPriority.URGENT) == 2 }
                    val cropIds = f.crops().map { it.id }.toSet()
                    assertTrue(cropIds.isNotEmpty())
                    f.allowed.set(false)
                    f.match.restartPreBattleObservationAttempt()
                    release.complete(Unit)
                    blocker.join()
                    SharedLatinTextRecognizer.withReadingTurn(OcrPriority.NORMAL) { }
                    delay(200)
                    assertTrue(f.timeline.getArticles().none { it.sourceId.id.endsWith("_PIPELINE_LATENCY") })
                    assertTrue(f.timeline.getArticles().none { it.payload is ActivePokemonSpeciesWitnessed })
                    assertTrue(f.crops().map { it.id }.containsAll(cropIds))
                } finally { release.complete(Unit); blocker.cancelAndJoin() }
            }
        } finally { delay(200); battle.recycle() }
    }
}
