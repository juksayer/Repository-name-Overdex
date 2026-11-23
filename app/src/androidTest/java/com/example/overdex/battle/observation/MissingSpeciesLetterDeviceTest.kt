package com.example.overdex.battle.observation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.archive.*
import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.data.*
import com.example.overdex.data.local.PokedexDatabase
import com.example.overdex.data.observation.SpeciesNameRecognizer
import com.example.overdex.model.AnchorRegion
import com.example.overdex.model.observation.ObservationInput
import com.example.overdex.model.observation.SessionSource
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Synthetic missing-ink labels exercise native OCR, custody and HUD; not a live battle accuracy test. */
class MissingSpeciesLetterDeviceTest {
    @Test fun partialBadgeNamesReachBothSidesAndTheOpponentMoveListWithRepairProvenance() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val knowledge = PokemonRepository(PokedexDatabase.getDatabase(context).pokemonDao(),
            LocalSpriteProvider(context.assets), GameMasterLoader(context))
        val frame = Bitmap.createBitmap(672, 1493, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(55, 85, 90)
            textSize = 28f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        Canvas(frame).apply {
            drawText("karmory", 25f, 122f, paint)
            drawText("s armory", 496f, 122f, paint)
        }
        val withBadge = frame.copy(Bitmap.Config.ARGB_8888, true)
        Canvas(withBadge).apply {
            val ball = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.RED }
            listOf(32f, 68f, 104f, 570f, 606f, 642f).forEach { x ->
                drawCircle(x, 145f, 7f, ball)
            }
        }
        val current = java.util.concurrent.atomic.AtomicReference(frame)
        val input = object : ObservationInput {
            override val source = SessionSource.LIVE_CAPTURE
            override suspend fun supply(onVisualData: suspend (Bitmap) -> Unit) {
                while (true) { onVisualData(current.get()); delay(40) }
            }
        }
        val calibration = BattleCalibration(
            playerSpeciesNameRegion = AnchorRegion(22/672f, 96/1493f, 157/672f, 32/1493f),
            opponentSpeciesNameRegion = AnchorRegion(493/672f, 96/1493f, 157/672f, 32/1493f),
        )
        val root = File(context.cacheDir, "missing-species-${System.nanoTime()}").apply { mkdirs() }
        val timeline = InMemoryRealityTimeline()
        val match = Match("MISSING_SPECIES_LABEL_TEST", custody = InMemoryTestimonyCustody(),
            realityTimeline = timeline, pokemonKnowledge = knowledge)
        val store = FileCropArtifactStore(root)
        val player = LiveOverlaySpeciesPipeline.player(input, calibration, store) { true }
        val opponent = LiveOverlaySpeciesPipeline.opponent(input, calibration, store) { true }
        try {
            DroidballOverlayPresentation.clearOpponentSpecies()
            knowledge.getAllSpeciesNames()
            SpeciesNameRecognizer.warmUp()
            match.witnessGoForSpeciesTest()
            player.start(match)
            opponent.start(match)
            timeline.awaitSpeciesStage("both unbadged words to reach raw OCR") {
                timeline.getArticles().filter { it.sourceId.id.contains("_SPECIES_OVERLAY_PIPELINE_OCR_") }
                    .map { it.sourceId.id.substringBefore('_') }.distinct().size == 2
            }
            assertTrue("Repaired words on a blank screen cannot occupy either team", timeline.getArticles()
                .none { it.payload is ActivePokemonSpeciesWitnessed })
            val changedAt = System.nanoTime()
            current.set(withBadge)
            timeline.awaitSpeciesStage("fresh native badge crops on both sides after the rejected words") {
                timeline.getArticles().filter { (it.monotonicTimeNanos ?: 0L) >= changedAt }
                    .mapNotNull { it.payload as? CropCaptured }.filter { it.nativeTeamBadgePresent == true }
                    .map { it.cropProvenance.cropName }.distinct().size == 2
            }
            timeline.awaitSpeciesStage("both repaired names and the opponent move list") {
                timeline.getArticles().count { it.payload is ActivePokemonSpeciesWitnessed } >= 2 &&
                    DroidballOverlayPresentation.activeOpponentMovePossibilities.value?.speciesName == "Skarmory"
            }
            val articles = timeline.getArticles()
            val identities = articles.filter { it.payload is ActivePokemonSpeciesWitnessed }
            assertEquals(setOf(ActivePokemonSide.PLAYER, ActivePokemonSide.OPPONENT),
                identities.map { (it.payload as ActivePokemonSpeciesWitnessed).side }.toSet())
            for (article in identities) {
                val result = article.payload as ActivePokemonSpeciesWitnessed
                assertEquals("Skarmory", result.speciesName)
                assertEquals("UNAMBIGUOUS_CATALOGUE_MISSING_LETTERS_1", result.ocrMatchBasis)
                assertFalse(result.ocrText.orEmpty().equals("Skarmory", true))
                assertEquals(.82f, requireNotNull(article.confidence), .001f)
                assertTrue(article.evidenceReferences.orEmpty().any { id -> articles.any {
                    it.id.value == id && it.payload is CropCaptured && it.monotonicTimeNanos == article.monotonicTimeNanos
                } })
                assertTrue(articles.any { raw -> raw.sourceId.id.contains("_SPECIES_OVERLAY_PIPELINE_OCR_") &&
                    (raw.payload as? RawTestimony)?.data == result.ocrText && raw.monotonicTimeNanos == article.monotonicTimeNanos })
                assertTrue(article.evidenceReferences.orEmpty().any { id -> articles.any {
                    it.id.value == id && (it.payload as? CropCaptured)?.nativeTeamBadgePresent == true } })
                val archived = RealityArticleArchiveMapper.map(article)
                val json = kotlinx.serialization.json.Json { classDiscriminator = "kind" }
                assertEquals(archived, json.decodeFromString(ArchivedRealityArticle.serializer(),
                    json.encodeToString(ArchivedRealityArticle.serializer(), archived)))
            }
            val moves = requireNotNull(DroidballOverlayPresentation.activeOpponentMovePossibilities.value)
            assertTrue(moves.fastMoves.isNotEmpty())
            assertTrue(moves.chargedMoves.isNotEmpty())
        } finally {
            player.stop(); opponent.stop(); match.release()
            frame.recycle(); withBadge.recycle(); root.deleteRecursively()
            DroidballOverlayPresentation.clearOpponentSpecies()
        }
    }
}
