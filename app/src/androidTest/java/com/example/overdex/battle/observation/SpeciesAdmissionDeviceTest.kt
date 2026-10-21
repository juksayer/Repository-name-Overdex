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
import com.example.overdex.data.observation.*
import com.example.overdex.model.AnchorRegion
import com.example.overdex.model.observation.ObservationInput
import com.example.overdex.model.observation.SessionSource
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SpeciesAdmissionDeviceTest {
    @Test fun queuedBadgeIsReplacedBeforeOcrAndOnlyFreshIdentityReachesHud() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        fun frame(name: String): Bitmap {
            val crop = instrumentation.context.assets.open("species/oct16/$name.png").use(BitmapFactory::decodeStream)!!
            return Bitmap.createBitmap(672, 1493, Bitmap.Config.ARGB_8888).also {
                it.eraseColor(Color.WHITE)
                Canvas(it).drawBitmap(crop, 493f, 96f, null)
                crop.recycle()
            }
        }
        val old = frame("sneasel")
        val fresh = frame("vaporeon")
        val current = AtomicReference(old)
        val input = object : ObservationInput {
            override val source = SessionSource.LIVE_CAPTURE
            override suspend fun supply(onVisualData: suspend (Bitmap) -> Unit) {
                while (true) { onVisualData(current.get()); delay(40) }
            }
        }
        val knowledge = PokemonRepository(PokedexDatabase.getDatabase(context).pokemonDao(),
            LocalSpriteProvider(context.assets), GameMasterLoader(context))
        val timeline = InMemoryRealityTimeline()
        val match = Match("OCT16_FRESH_SPECIES", custody = InMemoryTestimonyCustody(),
            realityTimeline = timeline, pokemonKnowledge = knowledge)
        val root = File(context.cacheDir, "species-admission-${System.nanoTime()}").apply { mkdirs() }
        val calibration = BattleCalibration(opponentSpeciesNameRegion = AnchorRegion(493/672f, 96/1493f, 157/672f, 32/1493f))
        val pipeline = LiveOverlaySpeciesPipeline.opponent(input, calibration, FileCropArtifactStore(root)) { true }
        val held = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var blocker: Job? = null
        suspend fun await(check: () -> Boolean) = withTimeout(10_000) { while (!check()) delay(20) }
        try {
            DroidballOverlayPresentation.clearOpponentSpecies()
            SpeciesNameRecognizer.warmUp()
            blocker = launch(Dispatchers.Default) {
                SharedLatinTextRecognizer.withReadingTurn(OcrPriority.NORMAL) { held.complete(Unit); release.await() }
            }
            held.await()
            pipeline.start(match)
            await { timeline.getArticles().any { it.payload is CropCaptured } }
            await { SharedLatinTextRecognizer.waitingCount(OcrPriority.URGENT) == 1 }
            // OCR is waiting with Sneasel. Publish the next combatant before admission.
            val changedAt = System.nanoTime()
            current.set(fresh)
            await { timeline.getArticles().count { it.payload is CropCaptured &&
                (it.monotonicTimeNanos ?: 0) > changedAt } >= 2 }
            release.complete(Unit)
            await { DroidballOverlayPresentation.activeOpponentMovePossibilities.value?.speciesName == "Vaporeon" }
            val articles = timeline.getArticles()
            val identities = articles.filter { it.payload is ActivePokemonSpeciesWitnessed }
            assertEquals(listOf("Vaporeon"), identities.map { (it.payload as ActivePokemonSpeciesWitnessed).speciesName })
            val identity = identities.single()
            assertTrue(requireNotNull(identity.monotonicTimeNanos) > changedAt)
            assertTrue(identity.evidenceReferences.orEmpty().any { id -> articles.any {
                it.id.value == id && it.payload is CropCaptured && it.monotonicTimeNanos == identity.monotonicTimeNanos } })
            val timing = articles.single { it.sourceId.id == "OPPONENT_SPECIES_OVERLAY_PIPELINE_LATENCY" }
            val text = (timing.payload as RawTestimony).data.toString()
            instrumentation.sendStatus(0, Bundle().apply { putString("fresh_species_timing", text) })
            assertFalse(text.contains("replacedCrops=0"))
            assertTrue(text.contains("passes=1"))

            // A later independent badge switch must close the requested check,
            // not immediately reopen another check from its own accepted result.
            current.set(old)
            match.speciesChecks.request(ActivePokemonSide.OPPONENT, "ENTRY", null)
            await { DroidballOverlayPresentation.activeOpponentMovePossibilities.value?.speciesName == "Sneasel" }
            assertFalse(match.speciesChecks.isChecking(ActivePokemonSide.OPPONENT))
            assertTrue(timeline.getArticles().none { (it.payload as? SpeciesCheckMeasured)?.let { p ->
                p.status == "OPENED" && p.reason == "SWITCH" } == true })
        } finally {
            release.complete(Unit)
            blocker?.cancelAndJoin()
            pipeline.stop(); match.release()
            old.recycle(); fresh.recycle(); root.deleteRecursively()
            DroidballOverlayPresentation.clearOpponentSpecies()
        }
    }
}
