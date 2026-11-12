package com.example.overdex.battle.archive

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.battle.observation.MatchId
import com.example.overdex.model.observation.OcrTextEvidence
import org.junit.Assert.*
import org.junit.Test

class FinishingObservationArchiveTest {
    @Test fun `longer QTE collection retains all34 actual frame references without a faint event`() {
        val refs = (1..34).map { "image-$it" }
        val sequence = live("qte-sequence", TerminalVisualSequenceCaptured(ActivePokemonSide.OPPONENT,
            "PLAYER_QTE_PROGRESS", 1_000_000_000L, 9_250_000_000L, 34), 9_250_000_000L, refs)
        val mapped = RealityArticleArchiveMapper.map(sequence)
        val archive = MatchArchive(matchId = "finishing", articles = listOf(mapped))
        val decoded = MatchArchiveSerializer.deserialize(MatchArchiveSerializer.serialize(archive))
        assertEquals(archive, decoded)
        assertEquals(34, (decoded.articles.single().payload as ArchivedTerminalVisualSequenceCaptured).frameCount)
        assertEquals(refs, decoded.articles.single().evidenceReferences)
        assertThrows(IllegalArgumentException::class.java) {
            TerminalVisualSequenceCaptured(ActivePokemonSide.OPPONENT, "PLAYER_QTE_PROGRESS", 1, 10, 35)
        }
    }

    @Test fun `prompt and neutral visual sequence retain source clocks and cited raw pixels in compact`() {
        val crop = ArchivedCropCaptured("artifacts/crops/sha256/${"a".repeat(64)}.png", "a".repeat(64),
            1, "image/png", "PlayerTerminalVisualCrop", 1080, 2400, 0, 600, 800, 2200)
        val pixel = live("pixel", RawTestimony("placeholder"), 11).let(RealityArticleArchiveMapper::map).copy(payload = crop)
        val ocr = OcrTextEvidence("NORMAL", "Switch in a new Pokémon?", "Switch in a new Pokémon?", emptyList(), 1080, null)
        val prompt = live("prompt", ForcedReplacementPromptWitnessed(ocr.rawText, "EXACT_PHRASE", ocr), 12, listOf("pixel"))
        val sequence = live("sequence", TerminalVisualSequenceCaptured(ActivePokemonSide.PLAYER, "DEPARTURE", 11, 11, 1), 11,
            listOf("pixel", "prompt"))
        val rows = listOf(pixel, RealityArticleArchiveMapper.map(prompt), RealityArticleArchiveMapper.map(sequence))
        val compact = MatchArchiveArticleSelector.select(rows, MatchArchiveExportMode.COMPACT_CITED_EVIDENCE)
        assertEquals(rows, compact)
        val archive = MatchArchive(matchId = "finishing", articles = compact)
        assertEquals(archive, MatchArchiveSerializer.deserialize(MatchArchiveSerializer.serialize(archive)))
        assertEquals(11L, compact.last().monotonicTimeNanos)
        assertEquals(listOf("pixel", "prompt"), compact.last().evidenceReferences)
        assertFalse(compact.any { it.payload is ArchivedActivePokemonFaintedWitnessed || it.payload is ArchivedFastMoveUseObserved })
    }

    @Test fun `expired unread replacement crop survives for calibration without a prompt conclusion`() {
        val crop = ArchivedCropCaptured("artifacts/crops/sha256/${"a".repeat(64)}.png", "a".repeat(64),
            1, "image/png", "ForcedReplacementMessageCrop", 1080, 2400, 130, 1740, 950, 1896)
        val raw = RealityArticleArchiveMapper.map(live("unread", RawTestimony("placeholder"), 11)).copy(
            payload = crop, sourceId = "FORCED_REPLACEMENT_MESSAGE_CAPTURE")
        assertEquals(listOf(raw), MatchArchiveArticleSelector.select(listOf(raw), MatchArchiveExportMode.COMPACT_CITED_EVIDENCE))
        assertTrue(MatchArchiveArticleSelector.select(listOf(raw.copy(sourceId = "OTHER")), MatchArchiveExportMode.COMPACT_CITED_EVIDENCE).isEmpty())
    }

    @Test fun `terminal raw crops survive interrupted summary publication without inventing a sequence`() {
        for (side in listOf("Player", "Opponent")) {
            val crop = ArchivedCropCaptured("artifacts/crops/sha256/${"a".repeat(64)}.png", "a".repeat(64),
                1, "image/png", "${side}TerminalVisualCrop", 1080, 2400, 130, 1740, 950, 1896)
            val raw = RealityArticleArchiveMapper.map(live("actual-crop", RawTestimony("placeholder"), 11)).copy(
                payload = crop, sourceId = "${side.uppercase()}_TERMINAL_VISUAL_CAPTURE")
            assertEquals(listOf(raw), MatchArchiveArticleSelector.select(listOf(raw), MatchArchiveExportMode.COMPACT_CITED_EVIDENCE))
            assertTrue(MatchArchiveArticleSelector.select(listOf(raw.copy(sourceId = "OTHER")), MatchArchiveExportMode.COMPACT_CITED_EVIDENCE).isEmpty())
        }
    }

    private fun live(id: String, payload: TestimonyPayload, at: Long, refs: List<String> = emptyList()) = RealityArticle(
        ArticleId(id), 1000 + at, 9000, SourceId("TEST"), payload, evidenceReferences = refs,
        matchId = MatchId("finishing"), monotonicTimeNanos = at)
}
