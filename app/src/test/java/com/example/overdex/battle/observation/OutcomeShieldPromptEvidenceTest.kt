package com.example.overdex.battle.observation

import com.example.overdex.battle.archive.ArchivedCropCaptured
import com.example.overdex.battle.archive.ArchivedRawText
import com.example.overdex.battle.archive.ArchivedRealityArticle
import com.example.overdex.battle.artifact.CropArtifactReference
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.model.observation.OcrBox
import com.example.overdex.model.observation.OcrTextEvidence
import com.example.overdex.model.observation.OcrWordEvidence
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class OutcomeShieldPromptEvidenceTest {
    @Test fun `recorded no-shields text is usable with original geometry and absent optional width`() {
        val fixture = fixture()
        val before = json.encodeToString(fixture)
        val raw = fixture.single { it.articleId == RAW_ID }
        val crop = liveCrop(fixture.single { it.articleId == CROP_ID })
        val reading = requireNotNull((raw.payload as ArchivedRawText).ocr)

        assertEquals(36, fixture.size)
        assertNull(reading.sourceFrameWidth)
        assertEquals(OutcomeShieldPromptEvidence.PROMPT, OutcomeShieldPromptEvidence.resolve(crop, reading))
        assertEquals(10966831338499L, crop.monotonicTimeNanos)
        assertEquals(raw.monotonicTimeNanos, crop.monotonicTimeNanos)
        assertEquals(raw.perceivedAt, crop.perceivedAt)
        assertEquals(listOf(CROP_ID), raw.evidenceReferences)
        assertEquals("The source Articles and clocks remain untouched", before, json.encodeToString(fixture))
        val otherReading = fixture.mapNotNull { (it.payload as? ArchivedRawText)?.ocr }
            .single { it.rawText == "No Prote1 S'lds remainir:" }
        assertNull("The failed threshold read cannot repair itself from the successful RAW reading",
            OutcomeShieldPromptEvidence.resolve(crop, otherReading))
    }

    @Test fun `only whole prompt with whitespace case and optional single exclamation variations matches`() {
        for (text in listOf(OutcomeShieldPromptEvidence.PROMPT, "no protect shields remaining",
            "  NO\nProtect\tShields remaining!  ")) assertTrue(text, OutcomeShieldPromptEvidence.matchesText(text))
        for (text in listOf("", "No Protect Shields", "Protect Shields remaining!", "No Protect Shields remaining!!",
            "NoProtectShieldsremaining", "No Protect Shield remaining!", "No Prote1 S'lds remainir:",
            "No Protect Shields remaining! YOU WIN!", "Attack incoming!", "Use a Protect Shield?",
            "Get Ready!", "EXCELLENT", "NO PROTECT SHIELDS REMAINING?")) {
            assertFalse(text, OutcomeShieldPromptEvidence.matchesText(text))
        }
    }

    @Test fun `both dedicated outcome capture sources are accepted but other sources and contracts are not`() {
        val (crop, reading) = recorded()
        for (source in listOf("MATCH_OUTCOME_CROP_CAPTURE", "MATCH_OUTCOME_PROBE_CAPTURE")) {
            assertNotNull(OutcomeShieldPromptEvidence.resolve(crop.copy(sourceId = SourceId(source)), reading))
        }
        for (source in listOf("", "ANNOUNCEMENT_CROP_CAPTURE", "QTE_PROGRESS_TEXT_CAPTURE", "MATCH_OUTCOME_TEXT_WITNESS")) {
            assertNull(OutcomeShieldPromptEvidence.resolve(crop.copy(sourceId = SourceId(source)), reading))
        }
        val captured = crop.payload as CropCaptured
        assertNull(OutcomeShieldPromptEvidence.resolve(crop.copy(payload = captured.copy(
            cropProvenance = captured.cropProvenance.copy(cropName = BattleCropContracts.announcement.cropName))), reading))
    }

    @Test fun `missing source custody and invalid source bounds cannot supply a prompt`() {
        val (crop, reading) = recorded()
        val captured = crop.payload as CropCaptured
        val invalid = listOf(crop.copy(id = ArticleId("")), crop.copy(matchId = null),
            crop.copy(matchId = MatchId("")), crop.copy(monotonicTimeNanos = null),
            crop.copy(monotonicTimeNanos = -1L), crop.copy(payload = RawTestimony(OutcomeShieldPromptEvidence.PROMPT))) +
            listOf(BattleCropBounds(-1, 0, 464, 217), BattleCropBounds(0, -1, 464, 217),
                BattleCropBounds(0, 0, 673, 217), BattleCropBounds(0, 0, 464, 1494),
                BattleCropBounds(20, 0, 20, 217), BattleCropBounds(0, 20, 464, 20)).map { bounds ->
                crop.copy(payload = captured.copy(cropProvenance = captured.cropProvenance.copy(bounds = bounds)))
            } + listOf(crop.copy(payload = captured.copy(cropProvenance = captured.cropProvenance.copy(sourceWidth = 0))),
                crop.copy(payload = captured.copy(cropProvenance = captured.cropProvenance.copy(sourceHeight = -1))))
        invalid.forEach { assertNull(OutcomeShieldPromptEvidence.resolve(it, reading)) }
        assertNotNull(OutcomeShieldPromptEvidence.resolve(crop, reading.copy(sourceFrameWidth = 672)))
        assertNull(OutcomeShieldPromptEvidence.resolve(crop, reading.copy(sourceFrameWidth = 1080)))
    }

    @Test fun `accepted wording must be independently reconstructed by retained words`() {
        val (crop, reading) = recorded()
        val invalid = listOf(reading.copy(words = emptyList()), reading.copy(words = reading.words.dropLast(1)),
            reading.copy(words = reading.words.reversed()),
            reading.copy(words = reading.words.mapIndexed { i, word -> if (i == 1) word.copy(decision = "HUD_SIZE_POSITION") else word }),
            reading.copy(words = reading.words + OcrWordEvidence("WIN", 1, OcrBox(10f, 100f, 40f, 130f))),
            reading.copy(acceptedText = "Get Ready!"))
        invalid.forEach { assertNull(OutcomeShieldPromptEvidence.resolve(crop, it)) }
        assertNotNull(OutcomeShieldPromptEvidence.resolve(crop, reading.copy(words = reading.words +
            OcrWordEvidence("HUD", 1, null, decision = "HUD_SIZE_POSITION"))))
    }

    @Test fun `every retained word needs finite positive geometry wholly inside the original crop`() {
        val (crop, reading) = recorded()
        val invalidBounds = listOf(null, OcrBox(Float.NaN, 55f, 99f, 84f),
            OcrBox(68f, Float.NEGATIVE_INFINITY, 99f, 84f), OcrBox(68f, 55f, Float.POSITIVE_INFINITY, 84f),
            OcrBox(68f, 55f, 99f, Float.NaN), OcrBox(-.01f, 55f, 99f, 84f),
            OcrBox(68f, -.01f, 99f, 84f), OcrBox(68f, 55f, 464.01f, 84f),
            OcrBox(68f, 55f, 99f, 217.01f), OcrBox(99f, 55f, 99f, 84f),
            OcrBox(68f, 84f, 99f, 55f))
        invalidBounds.forEach { bounds ->
            assertNull("Rejected word bounds $bounds", OutcomeShieldPromptEvidence.resolve(crop,
                reading.copy(words = reading.words.mapIndexed { i, word -> if (i == 0) word.copy(bounds = bounds) else word })))
        }
    }

    @Test fun `any capture-time overlay intersection rejects while adjacent or distant overlays do not`() {
        val (crop, reading) = recorded()
        val captured = crop.payload as CropCaptured
        fun resolve(overlay: OcrBox) = OutcomeShieldPromptEvidence.resolve(
            crop.copy(payload = captured.copy(overlayBounds = overlay)), reading)
        assertNull(resolve(OcrBox(68f, 55f, 99f, 84f)))
        assertNull(resolve(OcrBox(98.99f, 55f, 100f, 56f)))
        assertNull(resolve(OcrBox(0f, 0f, Float.NaN, 90f)))
        assertNull(resolve(OcrBox(20f, 20f, 10f, 10f)))
        assertNotNull(resolve(OcrBox(0f, 0f, 68f, 55f)))
        assertNotNull(resolve(OcrBox(0f, 100f, 464f, 217f)))
        assertNotNull(resolve(OcrBox(-100f, -100f, -50f, -50f)))
    }

    private fun recorded(): Pair<RealityArticle, OcrTextEvidence> {
        val articles = fixture()
        return liveCrop(articles.single { it.articleId == CROP_ID }) to
            requireNotNull((articles.single { it.articleId == RAW_ID }.payload as ArchivedRawText).ocr)
    }

    private fun fixture(): List<ArchivedRealityArticle> = json.decodeFromString(
        checkNotNull(javaClass.getResourceAsStream("/battle/recorded-evidence/2026-11-11-no-shields-phase/articles.json"))
            .bufferedReader().use { it.readText() })

    private fun liveCrop(article: ArchivedRealityArticle): RealityArticle {
        val p = article.payload as ArchivedCropCaptured
        return RealityArticle(ArticleId(article.articleId), article.perceivedAt, article.recordedAt,
            SourceId(article.sourceId), CropCaptured(CropArtifactReference(p.artifactPath, p.sha256, p.byteCount, p.mediaType),
                BattleCropProvenance(p.cropName, p.sourceWidth, p.sourceHeight,
                    BattleCropBounds(p.cropLeft, p.cropTop, p.cropRight, p.cropBottom)), p.overlayBounds),
            predecessorIds = article.predecessorIds.map(::ArticleId), confidence = article.confidence,
            sequenceNumber = article.sequenceNumber, evidenceReferences = article.evidenceReferences,
            matchId = MatchId(article.matchId), monotonicTimeNanos = article.monotonicTimeNanos)
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true; classDiscriminator = "kind" }
        const val CROP_ID = "87936339-bf17-4117-9448-d2d1eb858337"
        const val RAW_ID = "a5ec92e7-b8c7-4463-945e-cfa70e34e28a"
    }
}
