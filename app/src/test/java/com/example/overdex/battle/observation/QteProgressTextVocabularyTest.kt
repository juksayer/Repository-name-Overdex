package com.example.overdex.battle.observation

import com.example.overdex.battle.archive.RealityArticleArchiveMapper
import com.example.overdex.battle.artifact.CropArtifactReference
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.InMemoryTestimonyCustody
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.battle.replay.replayQteProgressGrade
import com.example.overdex.battle.timeline.observer.ObservationSource
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.data.observation.OcrHeightProfile
import com.example.overdex.data.observation.OcrPriority
import com.example.overdex.model.observation.OcrTextEvidence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class QteProgressTextVocabularyTest {
    @Test fun `only the observed extra I whole token alias resolves`() {
        for ((source, accepted) in listOf("NIICE!" to "NICE!", "niice" to "NICE", "  NiIcE!  " to "NICE!")) {
            val resolved = requireNotNull(resolveQteProgressText(source))
            assertEquals(source, resolved.originalText)
            assertEquals(accepted, resolved.acceptedText)
            assertEquals("KNOWN_QTE_GRADE_DUPLICATED_I", resolved.basis)
        }
        for (unresolved in listOf("NICE!", "GREAT!", "EXCELLENT!", "NIIICE!", "NI1CE!", "NICE!!",
                "NICE?", "NIICE?", "NIICE move", "very NIICE!", "Get Ready!", "NOICE!", "", "N I I C E")) {
            assertNull("Unexpected vocabulary repair of $unresolved", resolveQteProgressText(unresolved))
        }
    }

    @Test fun `dedicated witness preserves native reading and cited repair at the capture clock`() = runBlocking {
        val fixture = Fixture(QTE_SOURCE, BattleCropContracts.qteProgressText, "NIICE!")
        try {
            val capture = fixture.capture()
            val accepted = fixture.accepted()
            val raw = fixture.timeline.getArticles().single { it.sourceId.id == "${QTE_SOURCE}_OCR" }
            val resolution = fixture.timeline.getArticles().single {
                it.sourceId.id == "${QTE_SOURCE}_VOCABULARY_RESOLUTION"
            }
            assertEquals("NIICE!", (raw.payload as RawTestimony).data)
            assertEquals("NIICE!", (raw.payload as RawTestimony).ocr!!.rawText)
            assertEquals("NIICE!", (raw.payload as RawTestimony).ocr!!.acceptedText)
            assertEquals("NICE!", (accepted.payload as RawTestimony).data)
            assertEquals("NICE", replayQteProgressGrade(RealityArticleArchiveMapper.map(accepted)))
            assertEquals("basis=KNOWN_QTE_GRADE_DUPLICATED_I original=NIICE! accepted=NICE!",
                (resolution.payload as RawTestimony).data)
            assertEquals(listOf(capture.id.value, raw.id.value), resolution.evidenceReferences)
            assertEquals(listOf(capture.id.value, raw.id.value, resolution.id.value), accepted.evidenceReferences)
            for (article in listOf(raw, resolution, accepted)) {
                assertEquals(capture.perceivedAt, article.perceivedAt)
                assertEquals(capture.monotonicTimeNanos, article.monotonicTimeNanos)
            }
        } finally { fixture.close() }
    }

    @Test fun `announcement or mismatched crop cannot repair a nice like species or announcement`() = runBlocking {
        for ((source, crop) in listOf(
            "ANNOUNCEMENT_WITNESS" to BattleCropContracts.announcement,
            "ANNOUNCEMENT_WITNESS" to BattleCropContracts.qteProgressText,
            QTE_SOURCE to BattleCropContracts.announcement,
        )) {
            val fixture = Fixture(source, crop, "NIICE!")
            try {
                fixture.capture()
                assertEquals("NIICE!", (fixture.accepted().payload as RawTestimony).data)
                assertTrue(fixture.timeline.getArticles().none { it.sourceId.id.endsWith("_VOCABULARY_RESOLUTION") })
            } finally { fixture.close() }
        }
    }

    @Test fun `unknown text in the grade crop stays raw and does not become a replay grade`() = runBlocking {
        for (text in listOf("NIICE move", "NIIICE!", "NOICE!", "GREAT effect")) {
            val fixture = Fixture(QTE_SOURCE, BattleCropContracts.qteProgressText, text)
            try {
                fixture.capture()
                val accepted = fixture.accepted()
                assertEquals(text, (accepted.payload as RawTestimony).data)
                assertNull(replayQteProgressGrade(RealityArticleArchiveMapper.map(accepted)))
                assertTrue(fixture.timeline.getArticles().none { it.sourceId.id.endsWith("_VOCABULARY_RESOLUTION") })
            } finally { fixture.close() }
        }
    }

    private class Fixture(private val source: String, private val crop: BattleCropContract, text: String) {
        private val custody = InMemoryTestimonyCustody()
        val timeline = InMemoryRealityTimeline()
        private val match = Match("qte-vocabulary", custody = custody, realityTimeline = timeline,
            pokemonKnowledge = FakePokemonKnowledge())
        private val capturedAt = System.nanoTime()
        private val witness = PersistedAnnouncementWitness(object : AnnouncementCropReader {
            override suspend fun <T> withReadingTurn(priority: OcrPriority,
                block: suspend (AnnouncementCropReadingTurn) -> T): T = block(object : AnnouncementCropReadingTurn {
                override val nativeReadNanos = 1L
                override val readCount = 1
                override suspend fun recognize(article: RealityArticle, profile: OcrHeightProfile) =
                    listOf(OcrTextEvidence("RAW", text, text, emptyList(), 1080, null))
            })
        }, { capturedAt + 1_000_000L }, ObserverId(source, ObservationSource.SCREEN_CAPTURE),
            cropContract = crop, dispatcher = Dispatchers.Unconfined)

        suspend fun capture(): RealityArticle {
            witness.start(match)
            val receipt = custody.submitTestimony(SourceId("QTE_TEST_CAPTURE"), CropCaptured(
                CropArtifactReference("artifacts/crops/qte-alias.png", "a".repeat(64), 1),
                BattleCropProvenance(crop.cropName, 1080, 2280, BattleCropBounds(129, 524, 950, 821))),
                12345L, null, emptyList(), capturedAt)
            match.awaitAcceptedEvidencePublished()
            return timeline.getArticles().single { it.sequenceNumber == receipt.sequenceNumber }
        }

        suspend fun accepted(): RealityArticle {
            val article = withTimeout(5_000) { match.articles.first { it.sourceId.id == source } }
            match.awaitAcceptedEvidencePublished()
            return article
        }

        fun close() { witness.stop(); match.release() }
    }

    private companion object { const val QTE_SOURCE = "QTE_PROGRESS_TEXT_WITNESS" }
}
