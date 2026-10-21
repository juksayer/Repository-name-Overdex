package com.example.overdex.battle.observation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.data.observation.OcrField
import com.example.overdex.data.observation.OcrHeightProfile
import com.example.overdex.data.observation.OcrSourceContext
import com.example.overdex.data.observation.SpeciesNameRecognizer
import com.example.overdex.model.observation.OcrBox
import com.example.overdex.model.observation.OcrTextEvidence
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import com.example.overdex.battle.artifact.FileCropArtifactStore
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.custody.InMemoryTestimonyCustody
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.data.PokemonKnowledge

/** Real GO crops and an Android-captured Compose HUD; no invented OCR strings. */
class OcrSourceFilteringDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun asset(path: String) = instrumentation.context.assets.open(path).use(BitmapFactory::decodeStream)!!
    private fun save(name: String, readings: List<OcrTextEvidence>) {
        File(instrumentation.targetContext.filesDir, "ocr-source-$name.json").writeText(
            Json { prettyPrint = true }.encodeToString(ListSerializer(OcrTextEvidence.serializer()), readings))
    }
    private fun Bitmap.whole() = OcrBox(0f, 0f, width.toFloat(), height.toFloat())

    @Test fun liveWitnessPublishesFilteredTextWithOriginalReadingAndSourceTimestamp() = runBlocking {
        val root = File(instrumentation.targetContext.cacheDir, "ocr-witness-${System.nanoTime()}")
        val store = FileCropArtifactStore(root)
        val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        val match = Match("ocr-filter-test", custody = custody, realityTimeline = timeline,
            pokemonKnowledge = object : PokemonKnowledge {
                override suspend fun getAllSpeciesNames() = setOf("Sneasel", "Plusle")
                override suspend fun getPokemonByName(name: String): com.example.overdex.model.Pokemon? = null
                override suspend fun getPokemonById(id: Int): com.example.overdex.model.Pokemon? = null
            })
        val witness = PersistedAnnouncementWitness(store)
        val window = asset("battle/hud_oct13/violet-window.png")
        val panel = Bitmap.createBitmap(window, 400, 280, 415, 470)
        val hud = Bitmap.createScaledBitmap(panel, 258, 292, true)
        val game = asset("battle/hud_oct13/go-plusle.png")
        try {
            witness.start(match)
            for ((index, source) in listOf(hud, game).withIndex()) {
                val time = 1_000_000_000L + index * 1_000_000_000L
                val payload = CropCaptured(store.preservePng(source)!!,
                    BattleCropProvenance(BattleCropContracts.announcement.cropName, 672, 1493,
                        BattleCropBounds(0, 0, source.width, source.height)), source.whole())
                val receipt = custody.submitTestimony(SourceId("ANNOUNCEMENT_CROP_CAPTURE"),
                    payload, 100L + index, null, emptyList(), time)
                val accepted = withTimeout(15_000) { match.articles.first {
                    it.sourceId.id == "ANNOUNCEMENT_WITNESS" && it.monotonicTimeNanos == time
                } }
                assertEquals(if (index == 0) "le!" else "Go, Plusle!", (accepted.payload as RawTestimony).data)
                assertEquals(100L + index, accepted.perceivedAt)
                val articles = timeline.getArticles()
                val original = articles.first { it.sourceId.id == "ANNOUNCEMENT_WITNESS_OCR" && it.monotonicTimeNanos == time }
                val crop = articles.first { it.sequenceNumber == receipt.sequenceNumber }
                assertTrue(accepted.evidenceReferences!!.containsAll(listOf(original.id.value, crop.id.value)))
                assertEquals(time, original.monotonicTimeNanos)
                val reading = (original.payload as RawTestimony).ocr!!
                assertEquals(reading.rawText, (original.payload as RawTestimony).data)
                assertTrue(if (index == 0) reading.rawText.contains("Foul") else reading.rawText.contains("Plusle"))
            }
        } finally {
            witness.stop(); match.release()
            hud.recycle(); panel.recycle(); window.recycle(); game.recycle()
            root.deleteRecursively()
        }
    }

    @Test fun recordedGoTextSurvivesEvenInsideTheHudFootprint() = runBlocking {
        val announcement = asset("battle/hud_oct13/go-plusle.png")
        val species = asset("battle/oct13/species.png")
        try {
            // Deliberately declare overlap: game-sized words must not be removed
            // just because they lie beneath a transparent HUD or use teal ink.
            val entries = AnnouncementRecognizer.recognizeEvidence(announcement,
                OcrSourceContext(672, announcement.whole()), OcrHeightProfile(OcrField.ANNOUNCEMENT))
            save("game-announcement", entries)
            assertTrue(entries.any { it.acceptedText.contains("Go, Plusle!", true) })
            assertTrue(entries.flatMap { it.words }.none { it.decision != "RETAINED" })
            val badges = SpeciesNameRecognizer.recognizeCandidates(species,
                OcrSourceContext(672, species.whole()), OcrHeightProfile(OcrField.SPECIES))
            save("game-species", badges.mapNotNull { it.evidence })
            assertTrue(badges.any { it.text == "Sneasel" })
            assertTrue(badges.flatMap { it.evidence!!.words }.none { it.decision != "RETAINED" })
            val heights = badges.flatMap { it.evidence!!.words }.filter { it.text == "Sneasel" }
                .map { it.bounds!!.height }
            assertTrue("Both raw and padded treatments should read the badge", heights.size >= 2)
            // Thresholding changes the measured edge pixels: the recorded badge
            // is about 16px high in that treatment and 21px in native color.
            assertTrue("Geometry must undo the 160px padded OCR enlargement: $heights",
                heights.all { it in 15f..25f })
            assertEquals(21f, badges.first { it.treatment == "RAW" }.evidence!!.words
                .first { it.text == "Sneasel" }.bounds!!.height, 1f)
        } finally { announcement.recycle(); species.recycle() }
    }

    @Test fun capturedHudLabelsAreExcludedButTheSurvivingGameFragmentIsNotInventedIntoAName() = runBlocking {
        val window = asset("battle/hud_oct13/violet-window.png")
        val panel = Bitmap.createBitmap(window, 400, 280, 415, 470)
        val hud = Bitmap.createScaledBitmap(panel, 258, 292, true)
        try {
            val readings = AnnouncementRecognizer.recognizeEvidence(hud, OcrSourceContext(672, hud.whole()),
                OcrHeightProfile(OcrField.ANNOUNCEMENT))
            save("hud-announcement", readings)
            val raw = readings.joinToString(" ") { it.rawText }
            val accepted = readings.joinToString(" ") { it.acceptedText }
            for (label in listOf("Sneasel", "Feint", "Attack", "Shard", "Foul", "Play", "Punch", "Avalanche")) {
                assertTrue("Fixture no longer reads $label: $raw", raw.contains(label, true))
                assertFalse("Own HUD word escaped filtering: $label in $accepted", accepted.contains(label, true))
            }
            assertTrue("The visible game fragment should survive: $accepted", accepted.contains("le!"))
            assertFalse(accepted.contains("Plusle", true))
            assertNull(AnnouncementSpeciesTextResolver.resolveUsedMovePerformer(accepted, setOf("Sneasel", "Plusle")))

            // Replay's badge OCR path must apply this same check. Color comes
            // from the original crop regardless of its recognition treatment.
            val label = Bitmap.createBitmap(hud, 25, 99, 55, 24)
            try {
                val species = SpeciesNameRecognizer.recognizeCandidates(label,
                    OcrSourceContext(672, label.whole()), OcrHeightProfile(OcrField.SPECIES))
                save("hud-species", species.mapNotNull { it.evidence })
                assertTrue(species.any { it.evidence!!.rawText.contains("Sneasel", true) })
                assertTrue(species.none { it.text.contains("Sneasel", true) })
                assertTrue(species.flatMap { it.evidence!!.words }.any { it.decision == "HUD_SIZE_COLOR_POSITION" })
            } finally { label.recycle() }
        } finally { hud.recycle(); panel.recycle(); window.recycle() }
    }

    @Test fun hudColorAloneDoesNotEraseWordsOutsideTheCapturedHudBounds() = runBlocking {
        val window = asset("battle/hud_oct13/violet-window.png")
        val panel = Bitmap.createBitmap(window, 400, 280, 415, 470)
        val hud = Bitmap.createScaledBitmap(panel, 258, 292, true)
        try {
            val readings = AnnouncementRecognizer.recognizeEvidence(hud, OcrSourceContext(672),
                OcrHeightProfile(OcrField.ANNOUNCEMENT))
            save("no-footprint", readings)
            assertTrue(readings.any { it.acceptedText.contains("Foul", true) })
            assertTrue(readings.all { it.acceptedText == it.rawText })
        } finally { hud.recycle(); panel.recycle(); window.recycle() }
    }
}
