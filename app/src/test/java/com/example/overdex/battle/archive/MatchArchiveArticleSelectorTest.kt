package com.example.overdex.battle.archive

import com.example.overdex.battle.observation.BattleCalibrationPreviewSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchArchiveArticleSelectorTest {
    @Test fun `compact keeps charged impact samples even when the bar is obscured or ownership is unresolved`() {
        val payload = ArchivedCropCaptured("artifacts/crops/sha256/${"a".repeat(64)}.png", "a".repeat(64),
            1, "image/png", "ChargedImpactHpCrop", 672, 1493, 168, 358, 504, 926)
        val rows = (1..3).map { index ->
            timed("charge-$index", payload, 10_000L + index * 500).copy(sourceId = "CHARGED_IMPACT_HP_CAPTURE")
        }
        val wrongSource = rows.first().copy(articleId = "wrong-source", sourceId = "OTHER_CAPTURE")
        val wrongPurpose = rows.first().copy(articleId = "wrong-purpose", payload = payload.copy(cropName = "PlayerHpEvidenceCrop"))
        assertEquals(rows, select(rows + listOf(wrongSource, wrongPurpose)))
    }
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
    fun `compact retains manual hand samples only for their exact capture source and purpose`() {
        val payload = ArchivedCropCaptured("artifacts/crops/sha256/${"a".repeat(64)}.png", "a".repeat(64),
            1, "image/png", "OpponentTrainerHandCrop", 1080, 2400, 1045, 600, 1070, 680)
        val hand = timed("hand", payload, 10_100).copy(sourceId = "OPPONENT_TRAINER_HAND_CAPTURE")
        val otherWitness = hand.copy(articleId = "same-purpose-other-witness", sourceId = "QTE_TYPE_ICONS_CAPTURE")
        val otherPurpose = hand.copy(articleId = "same-witness-other-purpose",
            payload = payload.copy(cropName = "QteTypeIconsCrop"))
        val unrelated = hand.copy(articleId = "unrelated", sourceId = "OTHER_CAPTURE")
        // A genuine battle preview opportunity must not admit unrelated hand crops.
        val start = timed("start", ArchivedMatchStarted, 10_000)
        val rows = listOf(start, hand, otherWitness, otherPurpose, unrelated)

        assertEquals(listOf(start, hand), select(rows))
        assertTrue(previewIds(rows).isEmpty())
        assertEquals(listOf(hand), select(listOf(hand))) // Manual inspection needs no successful reader.
    }

    @Test
    fun `full retains every article`() {
        val articles = listOf(article("raw", ArchivedRawText("x")))
        assertTrue(MatchArchiveArticleSelector.select(articles, MatchArchiveExportMode.FULL_FORENSIC) === articles)
    }

    @Test fun `compact keeps at most one extra raw in battle preview for each of seven slots`() {
        val names = listOf("PlayerActiveSpeciesTextCrop", "OpponentActiveSpeciesTextCrop", "PlayerHpEvidenceCrop",
            "OpponentHpEvidenceCrop", "PlayerPokeBallsCrop", "OpponentPokeBallsCrop", "AnnouncementCrop")
        val articles = (1..40).flatMap { second ->
            val at = second * 2_000L
            listOf(timed("context-$second", hp(), at), timed("paired-$second", hp().copy(side = "PLAYER"), at)) +
                names.map { name -> preview("$name-$second", name, at + 10) }
        }
        val compact = select(articles)
        val retained = compact.filter { it.payload is ArchivedCropCaptured }
        assertEquals(7, retained.size)
        assertEquals(names.map { "$it-40" }.toSet(), retained.map { it.articleId }.toSet())
        assertEquals(80, compact.count { it.payload is ArchivedActiveHpBarMeasured })
        assertEquals(previewIds(articles), previewIds(compact))
    }

    @Test fun `unreadable HP and name crops survive with only a separate real battle start cue`() {
        val start = timed("start", ArchivedMatchStarted, 10_000)
        val badHp = preview("bad-hp", "PlayerHpEvidenceCrop", 10_500)
        val badName = preview("bad-name", "PlayerActiveSpeciesTextCrop", 10_600)
        val noContext = preview("overworld", "OpponentHpEvidenceCrop", 5_000)
        assertEquals(listOf("start", "bad-hp", "bad-name"), select(listOf(start, badHp, badName, noContext)).map { it.articleId })
        // There is no HP measurement or OCR success in this input: inspection must expose bad calibration.
    }

    @Test fun `GO missed battle uses valid independent damage but rejects invalid measurements and old overworld`() {
        val damage = timed("hit", ArchivedActiveHpBarDamageTickMeasured("OPPONENT", 1f, .9f, .1f), 20_000)
        val crop = preview("actual-player-hp", "PlayerHpEvidenceCrop", 20_100)
        val old = preview("old-menu", "PlayerHpEvidenceCrop", 1_000)
        val invalid = timed("invalid-hp", hp().copy(filledFraction = Float.NaN), 30_000)
        val unsupported = preview("unsupported", "OpponentHpEvidenceCrop", 30_100)
        assertEquals(setOf("actual-player-hp"), select(listOf(old, crop, invalid, unsupported, damage))
            .filter { it.payload is ArchivedCropCaptured }.map { it.articleId }.toSet())
    }

    @Test fun `post result crops are excluded even if later geometry resembles a bar`() {
        val good = preview("battle", "PlayerHpEvidenceCrop", 10_100)
        val later = preview("result-blank", "PlayerHpEvidenceCrop", 12_100)
        val articles = listOf(timed("start", ArchivedMatchStarted, 9_000), timed("hp", hp(), 10_000), good,
            timed("end", ArchivedMatchEnded("WIN"), 11_000),
            timed("post-hp", hp(), 12_000), later)
        assertEquals(listOf("battle"), select(articles).filter { it.payload is ArchivedCropCaptured }.map { it.articleId })
    }

    @Test fun `stopped sessions cannot add later previews and another match cannot supply context`() {
        val crop = preview("good", "OpponentHpEvidenceCrop", 10_100)
        val stopped = timed("stop", ArchivedObservationSessionStoppedByUser, 11_000)
        val after = preview("after", "OpponentHpEvidenceCrop", 12_100)
        val foreign = timed("other-hit", hp(), 12_000).copy(matchId = "another-match")
        val compact = select(listOf(timed("start", ArchivedMatchStarted, 9_000), crop, timed("hp", hp(), 10_000), stopped, after, foreign))
        assertEquals(listOf("good"), compact.filter { it.payload is ArchivedCropCaptured }.map { it.articleId })
        assertFalse(select(listOf(after, foreign)).any { it.payload is ArchivedCropCaptured })
    }

    @Test fun `source capture time determines retained preview regardless of delivery order or wall clock drift`() {
        val old = preview("old", "PlayerHpEvidenceCrop", 10_000).copy(perceivedAt = 50_000_000, recordedAt = 99_000_000)
        val fresh = preview("fresh", "PlayerHpEvidenceCrop", 10_500).copy(perceivedAt = 1, recordedAt = 2)
        val support = timed("start", ArchivedMatchStarted, 10_000)
        assertEquals(listOf("fresh"), select(listOf(fresh, support, old))
            .filter { it.payload is ArchivedCropCaptured }.map { it.articleId })
    }

    @Test fun `legacy previews use capture wall time consistently when monotonic evidence is absent`() {
        val old = preview("old", "PlayerHpEvidenceCrop", 10_000).copy(monotonicTimeNanos = null, recordedAt = 99_000_000)
        val fresh = preview("fresh", "PlayerHpEvidenceCrop", 10_500).copy(monotonicTimeNanos = null)
        val support = timed("start", ArchivedMatchStarted, 10_000).copy(monotonicTimeNanos = null)
        assertEquals(listOf("fresh"), select(listOf(fresh, support, old))
            .filter { it.payload is ArchivedCropCaptured }.map { it.articleId })
    }

    @Test fun `retained preview joins original evidence closure without altering archived articles`() {
        val audio = timed("audio-root", ArchivedAudioCaptured("artifacts/audio/sha256/${"b".repeat(64)}.wav",
            "b".repeat(64), 1, "audio/wav", 1, 1, 1, "BATTLE_HUD"), 10_000)
        val source = preview("source", "OtherRawCrop", 10_000).copy(predecessorIds = listOf(audio.articleId))
        val crop = preview("preview", "PlayerHpEvidenceCrop", 10_100).copy(evidenceReferences = listOf(source.articleId))
        val unused = preview("unused", "OtherRawCrop", 10_100)
        val support = timed("support", ArchivedGetReadyWitnessed, 10_000)
        val start = timed("start", ArchivedMatchStarted, 9_000)
        val compact = select(listOf(audio, source, crop, unused, support, start))
        assertEquals(listOf(audio, source, crop, support, start), compact)
        assertEquals(listOf("source"), crop.evidenceReferences)
    }

    @Test fun `one later navigation bar cannot discard the earlier real battle preview`() {
        val start = timed("start", ArchivedMatchStarted, 10_000)
        val battle = preview("battle", "PlayerHpEvidenceCrop", 10_100)
        val menu = timed("menu", ArchivedOutOfBattleMenuWitnessed, 11_000)
        val falseBar = timed("navigation-bar", hp(), 12_000)
        val navigation = preview("navigation", "PlayerHpEvidenceCrop", 12_100)
        val rows = listOf(start, battle, menu, falseBar, navigation)
        val compact = select(rows)
        assertEquals(listOf("battle"), compact.filter { it.payload is ArchivedCropCaptured }.map { it.articleId })
        assertEquals(previewIds(rows), previewIds(compact))
        assertEquals(compact, select(compact))
    }

    @Test fun `compact preview and cold selection preserve phase announcement preference over newer scenery`() {
        val rows = listOf(timed("start", ArchivedMatchStarted, 10_000),
            preview("charge-text-even-if-unreadable", "AnnouncementCrop", 11_000),
            timed("qte", ArchivedGetReadyWitnessed, 11_000),
            preview("later-scenery", "AnnouncementCrop", 12_000))
        val compact = select(rows)
        assertEquals(listOf("charge-text-even-if-unreadable"), compact.filter { it.payload is ArchivedCropCaptured }.map { it.articleId })
        assertEquals(setOf("charge-text-even-if-unreadable"), previewIds(compact))
    }

    @Test fun `unclassified movement cannot authorize raw preview retention as a Fast Move use`() {
        val crop = preview("crop", "PlayerHpEvidenceCrop", 10_100)
        val unsupported = timed("visual-only", ArchivedFastMoveUseObserved("use", "PLAYER", "OPPONENT",
            evidenceKinds = listOf("RECIPIENT_VISUAL_ARTIFACT"), basis = "UNCLASSIFIED_MOTION"), 10_000)
        assertFalse(select(listOf(crop, unsupported)).any { it.payload is ArchivedCropCaptured })
        val supported = unsupported.copy(payload = (unsupported.payload as ArchivedFastMoveUseObserved)
            .copy(evidenceKinds = listOf("HP_DAMAGE_TICK")))
        assertEquals(listOf("crop"), select(listOf(crop, supported))
            .filter { it.payload is ArchivedCropCaptured }.map { it.articleId })
    }

    @Test fun `late result is applied before a long post result raw tail is bounded`() {
        val start = timed("start", ArchivedMatchStarted, 10_000)
        val battle = preview("battle", "PlayerHpEvidenceCrop", 10_100)
        val tail = (1..2_100).map { preview("result-$it", "PlayerHpEvidenceCrop", 20_000L + it) }
        // This publication order previously evicted the good crop before END was admitted.
        val rows = listOf(start, battle) + tail + timed("late-end", ArchivedMatchEnded("WIN"), 11_000)
        val compact = select(rows)
        assertEquals(listOf("battle"), compact.filter { it.payload is ArchivedCropCaptured }.map { it.articleId })
        assertEquals(setOf("battle"), previewIds(compact))
    }

    private fun select(articles: List<ArchivedRealityArticle>) =
        MatchArchiveArticleSelector.select(articles, MatchArchiveExportMode.COMPACT_CITED_EVIDENCE)

    private fun previewIds(articles: List<ArchivedRealityArticle>) =
        BattleCalibrationPreviewSelection.fromArchivedArticles("match", articles)?.selectedArticleIds.orEmpty()

    private fun hp() = ArchivedActiveHpBarMeasured("OPPONENT", 10, 20, 90, 30, .8f, 100, 200)

    private fun preview(id: String, cropName: String, atMillis: Long) = timed(id,
        ArchivedCropCaptured("artifacts/crops/sha256/${"a".repeat(64)}.png", "a".repeat(64),
            1, "image/png", cropName, 100, 200, 10, 20, 90, 30), atMillis)

    private fun timed(id: String, payload: ArchivedTestimonyPayload, atMillis: Long) =
        article(id, payload).copy(perceivedAt = 1_000_000L + atMillis, recordedAt = 2_000_000L + atMillis,
            monotonicTimeNanos = atMillis * 1_000_000L)

    private fun article(
        id: String,
        payload: ArchivedTestimonyPayload,
        evidence: List<String>? = null,
        predecessors: List<String> = emptyList()
    ) = ArchivedRealityArticle(id, "match", 0, 0, "test", payload, predecessors, null, null, evidence)
}
