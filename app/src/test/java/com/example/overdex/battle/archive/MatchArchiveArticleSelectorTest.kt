package com.example.overdex.battle.archive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchArchiveArticleSelectorTest {
    @Test
    fun `compact retains cited crop and audio artifacts but omits uncited artifacts`() {
        val crop = article("crop", ArchivedCropCaptured("artifacts/crops/sha256/${"a".repeat(64)}.png", "a".repeat(64), 1, "image/png", "COUNTDOWN", 1, 1, 0, 0, 1, 1))
        val audio = article("audio", ArchivedAudioCaptured("artifacts/audio/sha256/${"b".repeat(64)}.wav", "b".repeat(64), 1, "audio/wav", 1, 1, 1, "BATTLE_HUD"))
        val unused = article("unused", ArchivedCropCaptured("artifacts/crops/sha256/${"c".repeat(64)}.png", "c".repeat(64), 1, "image/png", "HP", 1, 1, 0, 0, 1, 1))
        val observation = article("observation", ArchivedRawText("GO"), evidence = listOf("crop"), predecessors = listOf("audio"))

        val compact = MatchArchiveArticleSelector.select(listOf(crop, audio, unused, observation), MatchArchiveExportMode.COMPACT_CITED_EVIDENCE)

        assertEquals(listOf("crop", "audio", "observation"), compact.map { it.articleId })
    }

    @Test
    fun `compact keeps bounded QTE calibration samples without calling them identified types`() {
        val payload = ArchivedCropCaptured("artifacts/crops/sha256/${"a".repeat(64)}.png", "a".repeat(64),
            1, "image/png", "QteTypeIconsCrop", 100, 100, 30, 40, 70, 60)
        val sample = article("qte", payload).copy(sourceId = "QTE_TYPE_ICONS_CAPTURE")
        val unrelated = article("unrelated", payload)
        val compact = MatchArchiveArticleSelector.select(listOf(sample, unrelated), MatchArchiveExportMode.COMPACT_CITED_EVIDENCE)
        assertEquals(listOf(sample), compact)
    }

    @Test
    fun `full retains every article`() {
        val articles = listOf(article("raw", ArchivedRawText("x")))
        assertTrue(MatchArchiveArticleSelector.select(articles, MatchArchiveExportMode.FULL_FORENSIC) === articles)
    }

    private fun article(
        id: String,
        payload: ArchivedTestimonyPayload,
        evidence: List<String>? = null,
        predecessors: List<String> = emptyList()
    ) = ArchivedRealityArticle(id, "match", 0, 0, "test", payload, predecessors, null, null, evidence)
}
