package com.example.overdex.battle.observation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.ActivePokemonSpeciesWitnessed
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.InMemoryTestimonyCustody
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.data.BattleCalibration
import com.example.overdex.data.GameMasterLoader
import com.example.overdex.data.PokemonRepository
import com.example.overdex.data.LocalSpriteProvider
import com.example.overdex.data.local.PokedexDatabase
import com.example.overdex.data.observation.SpeciesNameRecognizer
import com.example.overdex.model.AnchorRegion
import com.example.overdex.model.observation.ObservationInput
import com.example.overdex.model.observation.SessionSource
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The actual October 10 evening frame and saved misaligned name boxes. No user profile writes. */
class LiveSpeciesRecoveryDeviceTest {
    @Test fun correctedResourceRowsCountTheRecordedBallsAndShields() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val frame = instrumentation.context.assets.open("species/oct10/evening_frame.png").use(BitmapFactory::decodeStream)!!
        try {
            for ((left, width) in listOf(19 to 106, 546 to 105)) {
                val balls = Bitmap.createBitmap(frame, left, 128, width, 32)
                try { assertEquals(3, OpponentBattleResourceCounter.pokeBallCount(balls)) }
                finally { balls.recycle() }
            }
            val shields = Bitmap.createBitmap(frame, 469, 128, 60, 32)
            try { assertEquals(2, OpponentBattleResourceCounter.count(shields, 2, OpponentBattleResourceCounter::isShieldColor)) }
            finally { shields.recycle() }
        } finally { frame.recycle() }
    }

    @Test fun rowLocationKeepsTheAssignedColumnsAndDoesNotMoveAnAlreadyAlignedStrip() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        for (name in listOf("lead_frame", "evening_frame")) {
            val frame = instrumentation.context.assets.open("species/oct10/$name.png").use(BitmapFactory::decodeStream)!!
            try {
                for (left in listOf(22, 493)) {
                    val aligned = AnchorRegion(left / 672f, 96 / 1493f, 156 / 672f, 32 / 1493f)
                    assertEquals(aligned, SpeciesBadgeTextLocator.locate(frame, aligned))
                    val misplaced = aligned.copy(y = 148 / 1493f)
                    val located = SpeciesBadgeTextLocator.locate(frame, misplaced)
                    assertEquals(misplaced.x, located.x)
                    assertEquals(misplaced.width, located.width)
                    assertEquals(misplaced.height, located.height)
                    val rect = requireNotNull(BattleCropResolver.resolveRect(located, 672, 1493))
                    assertTrue("$name: $rect", rect.top <= 104 && rect.bottom >= 120)
                }
            } finally { frame.recycle() }
        }
        val empty = Bitmap.createBitmap(672, 1493, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) }
        try {
            val assigned = AnchorRegion(.7f, .1f, .25f, .02f)
            assertEquals(assigned, SpeciesBadgeTextLocator.locate(empty, assigned))
        } finally { empty.recycle() }
    }

    @Test fun savedEveningBoxesRecoverBothNamesThroughCustodyAndPopulateHud() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val frame = instrumentation.context.assets.open("species/oct10/evening_frame.png").use(BitmapFactory::decodeStream)!!
        val knowledge = PokemonRepository(PokedexDatabase.getDatabase(context).pokemonDao(),
            LocalSpriteProvider(context.assets), GameMasterLoader(context))
        val input = object : ObservationInput {
            override val source = SessionSource.LIVE_CAPTURE
            override suspend fun supply(onVisualData: suspend (Bitmap) -> Unit) {
                while (true) { onVisualData(frame); delay(40) }
            }
        }
        val calibration = BattleCalibration(
            playerSpeciesNameRegion = AnchorRegion(.032738097f, .09930009f, .23214285f, .021433357f),
            opponentSpeciesNameRegion = AnchorRegion(.73363096f, .09930009f, .23363096f, .021433355f),
        )
        val root = File(context.cacheDir, "species-recovery-${System.nanoTime()}").apply { mkdirs() }
        val timeline = InMemoryRealityTimeline()
        val match = Match(matchId = "RECORDED_SPECIES_RECOVERY", custody = InMemoryTestimonyCustody(),
            realityTimeline = timeline, pokemonKnowledge = knowledge)
        val store = FileCropArtifactStore(root)
        val player = LiveOverlaySpeciesPipeline.player(input, calibration, store) { true }
        val opponent = LiveOverlaySpeciesPipeline.opponent(input, calibration, store) { true }
        try {
            DroidballOverlayPresentation.clearOpponentSpecies()
            SpeciesNameRecognizer.warmUp()
            val start = System.nanoTime()
            player.start(match)
            opponent.start(match)
            withTimeout(10_000) {
                while (timeline.getArticles().mapNotNull { it.payload as? ActivePokemonSpeciesWitnessed }
                        .map { it.speciesName }.toSet() != setOf("Raichu", "Sneasel")) delay(25)
                while (DroidballOverlayPresentation.activeOpponentMovePossibilities.value?.speciesName != "Sneasel") delay(25)
            }
            val articles = timeline.getArticles()
            val identities = articles.filter { it.payload is ActivePokemonSpeciesWitnessed }
            assertEquals(2, identities.size)
            identities.forEach { identity ->
                assertTrue(identity.evidenceReferences.orEmpty().any { id -> articles.any { it.id.value == id && it.payload is CropCaptured } })
            }
            assertEquals("Sneasel", DroidballOverlayPresentation.opponentSpecies.value.single().speciesName)
            val moves = requireNotNull(DroidballOverlayPresentation.activeOpponentMovePossibilities.value)
            assertTrue(moves.fastMoves.isNotEmpty())
            assertTrue(moves.chargedMoves.isNotEmpty())
            Log.i("LiveSpeciesRecovery", "Both identities and HUD delivered in ${(System.nanoTime() - start) / 1_000_000} ms")
        } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
            val articles = timeline.getArticles()
            val readings = articles.filter { it.sourceId.id.contains("_OCR") }.map { it.payload }
            throw AssertionError("Species recovery timed out; counts=${articles.groupingBy { it.payload::class.simpleName }.eachCount()}; OCR=$readings", timeout)
        } finally {
            player.stop(); opponent.stop(); match.release()
            frame.recycle(); root.deleteRecursively()
            DroidballOverlayPresentation.clearOpponentSpecies()
        }
    }
}
