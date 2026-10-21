package com.example.overdex.battle.observation

import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.archive.*
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.inference.ChargedMoveAnnouncementInference
import com.example.overdex.battle.reality.*
import com.example.overdex.data.*
import com.example.overdex.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import com.example.overdex.model.observation.*
import com.example.overdex.battle.artifact.FileCropArtifactStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/** Real retained 672×1493 frames from the Moondrop's 1080×2400 display. */
class PlayerControlTextDeviceTest {
    private suspend fun read(name: String, pointer: Boolean): String {
        val context = InstrumentationRegistry.getInstrumentation().context
        val bitmap = context.assets.open("player_controls/$name.png").use { BitmapFactory.decodeStream(it) }
        return try { PlayerControlTextRecognizer.read(bitmap) }
        finally { bitmap.recycle() }
    }

    @Test fun recordedPointerAndButtonLabelsResolveClippedAnnouncementAndRoundTripArchive() = runBlocking {
        val raw = read("pointer-down", true)
        val point = PointerCoordinateParser.parse(raw, 1080, 2400)
        assertNotNull("Actual pointer OCR: $raw", point)
        assertEquals(328f, point!!.x, .1f)
        assertEquals(2187f, point.y, .1f)
        assertEquals(1, PointerCoordinateParser.chargedButtonSlot(point, 1080, 2400,
            BattleCalibration().trainerChargeMoveControlsRegion))
        val outsideText = read("pointer-outside-controls", true)
        val outside = PointerCoordinateParser.parse(outsideText, 1080, 2400)
        assertNotNull(outsideText, outside)
        assertNull(PointerCoordinateParser.chargedButtonSlot(outside!!, 1080, 2400,
            BattleCalibration().trainerChargeMoveControlsRegion))
        val left = read("left-label", false)
        val right = read("right-label", false)
        assertEquals("ROCKTOMB", SpeciesOcrTypography.normalizeLabel(left).uppercase())
        assertEquals("EARTHPOWER", SpeciesOcrTypography.normalizeLabel(right).uppercase())

        // The OCR strings are real; this event sequence deliberately pairs a
        // synthetic right-button contact with the historical clipped announcement.
        val mon = Pokemon(323, "Camerupt", emptyList(), "", fastMoves = emptyList(), chargedMoves = listOf(
            Move("Rock Tomb", PokemonType.ROCK, 70, 60, false),
            Move("Earth Power", PokemonType.GROUND, 90, 50, false),
            Move("Earthquake", PokemonType.GROUND, 110, 65, false)))
        val knowledge = object : PokemonKnowledge {
            override suspend fun getPokemonByName(name: String) = mon.takeIf { it.name == name }
            override suspend fun getPokemonById(id: Int) = mon.takeIf { it.id == id }
            override suspend fun getAllSpeciesNames() = setOf(mon.name)
        }
        fun article(id: String, t: Long, payload: TestimonyPayload, source: String = "TEST") = RealityArticle(
            ArticleId(id), t * 1000, 25_000, SourceId(source), payload,
            matchId = MatchId("pointer-recorded-test"), monotonicTimeNanos = t * 1_000_000_000)
        val evidence = listOf(
            article("species", 1, ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Camerupt", 323)),
            article("left", 9, PlayerChargeMoveLabelRead(1, left)),
            article("right", 9, PlayerChargeMoveLabelRead(2, right)),
            article("touch", 10, PlayerChargeButtonTouchWitnessed(2, 748f, 2187f, 1080, 2400, "P: 1/1 X: 748 Y: 2187")),
        )
        val resolver = ChargedMoveAnnouncementInference(knowledge)
        evidence.forEach { assertNull(resolver.accept(it) { null }) }
        val announcement = article("used", 19, RawTestimony("Camerupt used Ear"), "ANNOUNCEMENT_WITNESS")
        val result = resolver.accept(announcement) { null }!!
        assertEquals("Earth Power", result.payload.moveName)
        assertEquals(50, result.payload.energyCost)
        val archive = MatchArchive(matchId = "pointer-recorded-test", articles = (evidence + announcement +
            article("resolved", 19, result.payload).copy(predecessorIds = result.predecessorIds)).map(RealityArticleArchiveMapper::map))
        val json = Json { encodeDefaults = true }
        val restored = json.decodeFromString<MatchArchive>(json.encodeToString(archive))
        assertEquals(archive, restored)
        assertEquals(1, restored.articles.count { it.payload is ArchivedChargedMoveEnergySpent })
    }

    @Test fun concurrentLiveWitnessesPublishContactsAndLabelsWithoutInventingAttacks() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val test = InstrumentationRegistry.getInstrumentation().context
        val calibration = BattleCalibration()
        val frameBitmap = Bitmap.createBitmap(672, 1493, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(frameBitmap)
        listOf("pointer-down" to BattleCropContracts.pointerCoordinates,
            "left-label" to BattleCropContracts.playerChargeMoveNameLeft,
            "right-label" to BattleCropContracts.playerChargeMoveNameRight).forEach { (file, contract) ->
            val bitmap = test.assets.open("player_controls/$file.png").use { BitmapFactory.decodeStream(it) }
            canvas.drawBitmap(bitmap, null, contract.resolveRect(calibration, 672, 1493)!!, null)
            bitmap.recycle()
        }
        val frames = MutableSharedFlow<CapturedVisualFrame>()
        val input = object : ObservationInput {
            override val source = SessionSource.LIVE_CAPTURE
            override suspend fun supply(onVisualData: suspend (Bitmap) -> Unit) { frames.collect { onVisualData(it.bitmap) } }
            override suspend fun supplyFrames(onFrame: suspend (CapturedVisualFrame) -> Unit) { frames.collect { onFrame(it) } }
        }
        val mon = Pokemon(323, "Camerupt", emptyList(), "", fastMoves = emptyList(), chargedMoves = listOf(
            Move("Rock Tomb", PokemonType.ROCK, 70, 60, false), Move("Earth Power", PokemonType.GROUND, 90, 50, false)))
        val knowledge = object : PokemonKnowledge {
            override suspend fun getPokemonByName(name: String) = mon.takeIf { name == it.name }
            override suspend fun getPokemonById(id: Int) = mon.takeIf { id == it.id }
            override suspend fun getAllSpeciesNames() = setOf(mon.name)
        }
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        val match = Match("live-control-test", custody = custody, realityTimeline = timeline, pokemonKnowledge = knowledge)
        val store = FileCropArtifactStore(java.io.File(app.cacheDir, "control-witness-test").apply { mkdirs() })
        val witnesses = (0..2).map { LivePlayerControlTextWitness(input, calibration, store, { true }, it) }
        val at = System.nanoTime()
        custody.submitTestimony(SourceId("SPECIES"), ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Camerupt", 323),
            1000, null, emptyList(), at)
        witnesses.forEach { it.start(match) }
        val sender = launch {
            repeat(80) { index ->
                delay(50)
                frames.emit(CapturedVisualFrame(frameBitmap, 2000 + index * 50L, System.nanoTime(), 1080, 2400))
            }
        }
        try {
            withTimeout(15_000) {
                while (timeline.getArticles().none { it.payload is PlayerChargeButtonTouchWitnessed } ||
                    (1..2).any { slot -> timeline.getArticles().none { article ->
                        (article.payload as? PlayerChargeMoveLabelRead)?.let { it.slot == slot && it.text.isNotBlank() } == true
                    } }) delay(50)
            }
            sender.join()
            assertTrue(timeline.getArticles().none { it.payload is ChargedMoveEnergySpent || it.payload is FastMoveUseObserved })
            custody.submitTestimony(SourceId("ANNOUNCEMENT_WITNESS"), RawTestimony("Camerupt used Roc"), 8000, null, emptyList(), at + 7_000_000_000L)
            withTimeout(5_000) {
                while (timeline.getArticles().none { it.payload is ChargedMoveEnergySpent }) delay(20)
            }
            val spent = timeline.getArticles().single { it.payload is ChargedMoveEnergySpent }
            assertEquals("Rock Tomb", (spent.payload as ChargedMoveEnergySpent).moveName)
            assertTrue(spent.predecessorIds.all { id -> timeline.getArticles().any { it.id == id } })
            val payloads = timeline.getArticles().map(RealityArticleArchiveMapper::map).map { it.payload }
            assertTrue(payloads.any { it is ArchivedPlayerChargeButtonTouchWitnessed })
            assertTrue(payloads.any { it is ArchivedPlayerChargeMoveLabelRead })
            val compact = MatchArchiveArticleSelector.select(timeline.getArticles().map(RealityArticleArchiveMapper::map),
                MatchArchiveExportMode.COMPACT_CITED_EVIDENCE)
            val labels = compact.filter { it.payload is ArchivedPlayerChargeMoveLabelRead }
            assertTrue(labels.isNotEmpty())
            labels.forEach { label ->
                assertTrue(label.evidenceReferences.orEmpty().isNotEmpty())
                assertTrue(label.evidenceReferences.orEmpty().all { ref -> compact.any { it.articleId == ref && it.payload is ArchivedCropCaptured } })
            }
        } finally {
            sender.cancelAndJoin()
            witnesses.forEach { it.stop() }
            match.release()
            // The workers own the copied crops, not this reusable fixture frame.
            frameBitmap.recycle()
        }
    }
}
