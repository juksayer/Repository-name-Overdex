package com.example.overdex.battle.archive

import com.example.overdex.battle.artifact.CropArtifactReference
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.observation.BattleCropBounds
import com.example.overdex.battle.observation.BattleCropContracts
import com.example.overdex.battle.observation.BattleCropProvenance
import com.example.overdex.battle.observation.MatchId
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.model.BattleResult
import org.junit.Assert.*
import org.junit.Test

class CompletedMatchArchiveRefreshTest {
    private val completed = MatchId("completed")

    @Test fun `accepted finishing text and late semantic evidence refresh the same completed match`() {
        val evidence = listOf(
            article("announcement", RawTestimony("Camerupt used Rock Tomb!"), "ANNOUNCEMENT_WITNESS"),
            article("progress", RawTestimony("NICE!"), "QTE_PROGRESS_TEXT_WITNESS"),
            article("ready", GetReadyWitnessed),
            article("incoming", AttackIncoming),
            article("used", ChargeMoveUsedAnnounced),
            article("qte", ChargeMoveQteVibrationPatternInferred(pulseCount = 3, windowNanos = 1)),
            article("spend", ChargedMoveEnergySpent(ActivePokemonSide.PLAYER, "Camerupt", "Rock Tomb", 60, "ANNOUNCEMENT")),
            article("species", ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Whimsicott")),
            article("identity", PlayerActiveIdentityResolved("Camerupt", "Camerupt", 323, 3, 1, "ROSTER")),
            article("faint", ActivePokemonFaintedWitnessed(ActivePokemonSide.OPPONENT, "Whimsicott")),
            article("hit", FastMoveUseObserved("hit", ActivePokemonSide.PLAYER, ActivePokemonSide.OPPONENT,
                evidenceKinds = listOf("HP_DAMAGE_TICK"))),
            article("move", FastMoveIdentified(ActivePokemonSide.PLAYER, "Camerupt", "Incinerate",
                2_500_000_000L, null, 0, "CONFIGURED")),
            article("energy", FastMoveEnergyDerived(ActivePokemonSide.PLAYER, "Incinerate", 1, 20, 20, "OBSERVED_USE")),
            article("stat", StatChangeObserved(ActivePokemonSide.OPPONENT, "ATTACK", -1, 1, "Rock Tomb", "sound")),
            article("music", MatchResultMusicMeasured("WIN", "a".repeat(64), .9f, .2f, 0, 1)),
            article("comparison", MatchResultMusicCompared("WIN", "WIN", true, "music", "result")),
        )
        evidence.forEach { row ->
            assertTrue(row.id.value, shouldRefreshCompletedMatchArchive(row, completed))
            // An old collector must update only its own Match, even after a
            // newer Match starts while this recognition job is finishing.
            assertFalse(row.id.value, shouldRefreshCompletedMatchArchive(row, MatchId("newer")))
        }
    }

    @Test fun `blank crops raw OCR and latency diagnostics do not postpone a completed save`() {
        val excluded = listOf(
            article("blank", RawTestimony("  \n"), "ANNOUNCEMENT_WITNESS"),
            article("blank-qte", RawTestimony(""), "QTE_PROGRESS_TEXT_WITNESS"),
            article("raw-ocr", RawTestimony("Camerupt used Rock Tomb!"), "ANNOUNCEMENT_WITNESS_OCR"),
            article("raw-qte", RawTestimony("NICE!"), "QTE_PROGRESS_TEXT_WITNESS_OCR"),
            article("latency", RawTestimony("queueMs=2000 nonblank=true"), "ANNOUNCEMENT_WITNESS_LATENCY"),
            article("other-text", RawTestimony("Camerupt used Rock Tomb!"), "UNRELATED_TEXT"),
            article("crop", crop()),
            article("pulse", DeviceMotionPulseMeasured(1L, 1f, 1f, 1)),
            article("result", MatchEnded(BattleResult.WIN)),
            article("availability", WitnessOperating(false)),
        )
        excluded.forEach { assertFalse(it.id.value, shouldRefreshCompletedMatchArchive(it, completed)) }
        val session = article("session", GetReadyWitnessed).copy(matchId = null)
        assertFalse(shouldRefreshCompletedMatchArchive(session, completed))
    }

    @Test fun `later snapshot includes the finishing banner and cited pixels without changing the initial snapshot or source clocks`() {
        val timeline = InMemoryRealityTimeline()
        val source = MatchArchiveSource(completed, timeline)
        val result = article("result", MatchEnded(BattleResult.WIN)).copy(monotonicTimeNanos = 20_000L)
        timeline.append(result)
        val initial = source.snapshot()

        val retained = article("retained-finishing-crop", crop()).copy(monotonicTimeNanos = 10_000L)
        val text = article("finishing-banner", RawTestimony("Camerupt used Rock Tomb!"), "ANNOUNCEMENT_WITNESS")
            .copy(perceivedAt = 10L, recordedAt = 30L, monotonicTimeNanos = retained.monotonicTimeNanos,
                evidenceReferences = listOf(retained.id.value))
        timeline.append(retained)
        timeline.append(text)
        timeline.append(article("new-match", RawTestimony("NICE!"), "QTE_PROGRESS_TEXT_WITNESS")
            .copy(matchId = MatchId("newer")))
        assertTrue(shouldRefreshCompletedMatchArchive(text, completed))

        val refreshed = source.snapshot()
        val compact = MatchArchiveArticleSelector.select(refreshed.articles, MatchArchiveExportMode.COMPACT_CITED_EVIDENCE)
        assertEquals(listOf("result"), initial.articles.map { it.articleId })
        assertEquals(completed.value, refreshed.matchId)
        assertEquals(listOf("result", "retained-finishing-crop", "finishing-banner"), compact.map { it.articleId })
        val banner = compact.single { it.articleId == text.id.value }
        assertEquals(text.perceivedAt, banner.perceivedAt)
        assertEquals(text.recordedAt, banner.recordedAt)
        assertEquals(text.monotonicTimeNanos, banner.monotonicTimeNanos)
        assertEquals(text.evidenceReferences, banner.evidenceReferences)
        assertEquals(text, timeline.getArticles().single { it.id == text.id })
    }

    private fun article(id: String, payload: TestimonyPayload, source: String = "FIXTURE") = RealityArticle(
        ArticleId(id), 1L, 2L, SourceId(source), payload, matchId = completed, monotonicTimeNanos = 3L)

    private fun crop() = CropCaptured(CropArtifactReference("artifacts/crops/test.png", "a".repeat(64), 1),
        BattleCropProvenance(BattleCropContracts.announcement.cropName, 1080, 2400,
            BattleCropBounds(50, 100, 1000, 200)))
}
