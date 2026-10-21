package com.example.overdex.battle.archive

import org.junit.Assert.*
import org.junit.Test

class MatchSummaryTest {
    private fun article(id: String, at: Long, payload: ArchivedTestimonyPayload, delay: Long = 0) =
        ArchivedRealityArticle(id, "match", 1_700_000_000_000 + at, 1_700_000_000_000 + at + delay,
            "TEST", payload, emptyList(), null, at, emptyList(), at * 1_000_000L)
    private fun use(id: String, side: String = "PLAYER") = ArchivedFastMoveUseObserved(id, side,
        if (side == "PLAYER") "OPPONENT" else "PLAYER", evidenceKinds = listOf("HP_DAMAGE_TICK"), basis = "TEST")
    private fun summary(vararg articles: ArchivedRealityArticle) = MatchArchive(matchId = "match", articles = articles.toList()).summarize()

    @Test fun `uses canonical identity and excludes configured but unseen team members`() {
        val s = summary(
            article("configured", 0, ArchivedPlayerTeamSlotConfigured(1, "Camerupt", 323, "Incinerate", emptyList())),
            article("start", 1_000, ArchivedMatchStarted),
            article("identity", 1_200, ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Sneasel", 215)),
            article("repeated", 1_600, ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Sneasel", 215)),
            article("foreign", 1_800, ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Tauros", 128)).copy(matchId = "another"))
        assertEquals(listOf("Sneasel"), s.opponent.species)
        assertTrue(s.player.species.isEmpty())
        assertEquals(4, s.articleCount)
    }

    @Test fun `counts distinct uses within battle and keeps sides separate`() {
        val first = article("one", 2_000, use("use1"))
        val s = summary(article("pre", 100, use("pre")), article("start", 1_000, ArchivedMatchStarted),
            first, first, article("also-one", 2_100, use("use1")),
            article("opponent", 2_300, use("use2", "OPPONENT")),
            article("end", 5_000, ArchivedMatchEnded("WIN")), article("post", 6_000, use("post")))
        assertEquals(1, s.player.fastMoveUses)
        assertEquals(1, s.opponent.fastMoveUses)
        assertEquals("WIN", s.result)
        assertEquals(4_000L, s.battleDurationMillis)
    }

    @Test fun `publication delay does not extend battle duration`() {
        val s = summary(article("record", 0, ArchivedMatchRecordStarted),
            article("start", 10_000, ArchivedMatchStarted),
            article("end", 70_000, ArchivedMatchEnded("WIN"), delay = 22_000),
            article("late", 60_000, ArchivedRawText("earlier text"), delay = 60_000))
        assertEquals(60_000L, s.battleDurationMillis)
        assertEquals(70_000L, s.recordingDurationMillis)
    }

    @Test fun `result music does not invent a result or a final faint`() {
        val s = summary(article("start", 1_000, ArchivedMatchStarted),
            article("music", 10_000, ArchivedMatchResultMusicMeasured("WIN", "hash", .99f, .4f, 0, 4_000_000_000)))
        assertNull(s.result)
        assertEquals(0, s.opponent.faintCount)
        assertEquals("Win music heard; result unconfirmed", s.resultMusic)
    }

    @Test fun `legacy clocks and missing match start remain readable`() {
        val s = summary(article("record", 0, ArchivedMatchRecordStarted).copy(monotonicTimeNanos = null),
            article("identity", 5_000, ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Gourgeist", 711)),
            article("tail", 7_000, ArchivedRawText("")))
        assertNull(s.battleDurationMillis)
        assertEquals(7_000L, s.recordingDurationMillis)
        assertEquals(listOf("Gourgeist"), s.player.species)
        assertEquals(0, s.player.fastMoveUses)
    }

    @Test fun `missing GO does not hide recorded combat or create a start time`() {
        val s = summary(article("record", 0, ArchivedMatchRecordStarted),
            article("hit", 2_000, use("one")), article("result", 5_000, ArchivedMatchEnded("LOSS")),
            article("after", 6_000, use("after")))
        assertEquals(1, s.player.fastMoveUses)
        assertNull(s.battleDurationMillis)
    }

    @Test fun `named charges are distinct from repeated announcement and energy evidence`() {
        val charge = ArchivedChargedMoveEnergySpent("OPPONENT", "Sneasel", "Foul Play", 40, "TEST")
        val faint = ArchivedActivePokemonFaintedWitnessed("OPPONENT", "Sneasel", 215, "POKE_BALL")
        val s = summary(article("start", 0, ArchivedMatchStarted), article("charge", 10_000, charge),
            article("same-charge", 11_000, charge), article("another-charge", 25_000, charge),
            article("faint", 30_000, faint), article("faint-again", 31_000, faint),
            article("end", 33_000, ArchivedMatchEnded("WIN")))
        assertEquals(2, s.opponent.chargedMoveUses)
        assertEquals(1, s.opponent.faintCount)
        assertEquals(listOf("Sneasel: Foul Play"), s.opponent.chargedMoves)
    }
}
