package com.example.overdex.battle.archive

import com.example.overdex.battle.artifact.CropArtifactReference
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.custody.MatchOutcomeLabelWitnessed
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.custody.SourceId
import com.example.overdex.battle.custody.TestimonyPayload
import com.example.overdex.battle.observation.BattleCropBounds
import com.example.overdex.battle.observation.BattleCropProvenance
import com.example.overdex.battle.observation.MatchId
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.model.BattleResult
import com.example.overdex.model.observation.OcrBox
import com.example.overdex.model.observation.OcrTextEvidence
import com.example.overdex.model.observation.OcrWordEvidence
import org.junit.Assert.*
import org.junit.Test

class MatchOutcomeLabelArchiveTest {
    @Test fun `partial outcome acceptance round trips without replacing raw OCR or losing crop custody`() {
        listOf(BattleResult.WIN to "oU WINy", BattleResult.LOSS to "OOD EFFOR").forEach { (result, raw) ->
            val ocr = OcrTextEvidence("WHITE_TEXT_210_PADDED", raw, raw,
                listOf(OcrWordEvidence(raw, 0, OcrBox(72f, 38f, 530f, 104f))), 1080, null)
            val crop = CropCaptured(CropArtifactReference("artifacts/crops/source.png", "a".repeat(64), 2048),
                BattleCropProvenance("MatchOutcomeCrop", 1080, 2400, BattleCropBounds(100, 900, 980, 1300)))
            val source = if (result == BattleResult.WIN) "YOU_WIN_WITNESS" else "GOOD_EFFORT_WITNESS"
            val original = listOf(
                article("crop", "MATCH_OUTCOME_CAPTURE", crop),
                article("raw", "MATCH_OUTCOME_TEXT_WITNESS", RawTestimony(raw, ocr), listOf("crop")),
                article("label", source,
                    MatchOutcomeLabelWitnessed(result, raw, "PARTIAL_TITLE_GEOMETRY", ocr, 2), listOf("raw")),
            )
            val archive = RealityArticleArchiveMapper.createArchive(MatchId("outcome"), original)
            val compact = archive.copy(articles = MatchArchiveArticleSelector.select(archive.articles,
                MatchArchiveExportMode.COMPACT_CITED_EVIDENCE))
            val encoded = MatchArchiveSerializer.serialize(compact)
            val restored = MatchArchiveSerializer.deserialize(encoded)

            assertEquals(compact, restored)
            assertTrue(encoded.contains("match_outcome_label_witnessed"))
            val label = restored.articles.single { it.articleId == "label" }
            val payload = label.payload as ArchivedMatchOutcomeLabelWitnessed
            assertEquals(result.name, payload.result)
            assertEquals(raw, payload.rawText)
            assertEquals(ocr, payload.ocr)
            assertEquals("PARTIAL_TITLE_GEOMETRY", payload.basis)
            assertEquals(2, payload.supportingFrameCount)
            assertEquals(1234L, label.perceivedAt)
            assertEquals(9_000_000_000L, label.monotonicTimeNanos)
            assertEquals(listOf("raw"), label.evidenceReferences)
            assertEquals(listOf("crop"), restored.articles.single { it.articleId == "raw" }.evidenceReferences)
            assertEquals(raw, (restored.articles.single { it.articleId == "raw" }.payload as ArchivedRawText).value)
            assertEquals(100, (restored.articles.single { it.articleId == "crop" }.payload as ArchivedCropCaptured).cropLeft)
        }
    }

    @Test fun `accepted result labels cannot describe a draw unknown or zero supporting frames`() {
        val ocr = OcrTextEvidence("RAW", "W", "W", emptyList(), null, null)
        listOf(BattleResult.DRAW, BattleResult.UNKNOWN).forEach { result ->
            assertThrows(IllegalArgumentException::class.java) {
                MatchOutcomeLabelWitnessed(result, "W", "TEST", ocr, 2)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            MatchOutcomeLabelWitnessed(BattleResult.WIN, "W", "TEST", ocr, 0)
        }
    }

    private fun article(id: String, source: String, payload: TestimonyPayload, refs: List<String> = emptyList()) =
        RealityArticle(ArticleId(id), 1234L, 99_999L, SourceId(source), payload,
            evidenceReferences = refs, matchId = MatchId("outcome"), monotonicTimeNanos = 9_000_000_000L)
}
