package com.example.overdex.battle.archive

import org.junit.Assert.*
import org.junit.Test

class MatchComparisonTest {
    private fun article(match: String, id: String, at: Long, payload: ArchivedTestimonyPayload) =
        ArchivedRealityArticle(id, match, 1_700_000_000_000L + at, 1_700_000_000_000L + at,
            "TEST", payload, emptyList(), null, at, emptyList(), at * 1_000_000L)

    private fun use(id: String, side: String = "PLAYER") = ArchivedFastMoveUseObserved(id, side,
        if (side == "PLAYER") "OPPONENT" else "PLAYER", evidenceKinds = listOf("HP_DAMAGE_TICK"), basis = "TEST")

    private fun archive(id: String, count: Int, result: String? = "WIN"): MatchArchive = MatchArchive(
        matchId = id, articles = buildList {
            add(article(id, "start", 0, ArchivedMatchStarted))
            add(article(id, "player", 1_000, ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Camerupt", 323)))
            add(article(id, "opponent", 2_000, ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Sneasel", 215)))
            repeat(count) { n -> add(article(id, "hit-$n", 3_000L + n * 1_000, use("use-$n"))) }
            add(article(id, "charge", 20_000, ArchivedChargedMoveEnergySpent("PLAYER", "Camerupt", "Earth Power", 55, "TEST")))
            add(article(id, "same-charge", 21_000, ArchivedChargedMoveEnergySpent("PLAYER", "Camerupt", "Earth Power", 55, "TEST")))
            add(article(id, "opponent-hit", 22_000, use("opponent-use", "OPPONENT")))
            if (result != null) add(article(id, "end", 30_000, ArchivedMatchEnded(result)))
        })

    @Test fun `all matches use one scale and recorded attacks remain attributed by player species`() {
        val first = archive("one", 3)
        val projection = MatchComparison.project(listOf(MatchComparisonEntry("one", first),
            MatchComparisonEntry("two", archive("two", 8, "LOSS"))))
        assertEquals(8, projection.maximumUses)
        assertEquals(listOf("one", "two"), projection.matches.map { it.matchId })
        val player = projection.matches.first().playerUses.single()
        assertEquals(MatchComparisonPlayerUses("Camerupt", 3, 1), player)
        assertEquals(listOf("Sneasel"), projection.matches.first().summary!!.opponent.species)
        assertEquals(1, projection.matches.first().summary!!.opponent.fastMoveUses)
        assertEquals("LOSS", projection.matches.last().summary!!.result)
        assertEquals(0, projection.matches.first().unattributedPlayerFastMoveUses)
    }

    @Test fun `species changes separate rows and duplicate testimony does not add attacks`() {
        val source = archive("one", 3)
        val hit = source.articles.first { it.articleId == "hit-0" }
        val changed = source.copy(articles = source.articles.filterNot { it.payload is ArchivedMatchEnded } + listOf(
            hit, article("one", "repeated-use", 6_500, use("use-0")),
            article("one", "new-player", 23_000, ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Gourgeist", 711)),
            article("one", "new-hit", 24_000, use("gourgeist-use")),
            article("one", "end", 30_000, ArchivedMatchEnded("WIN"))))
        val match = MatchComparison.project(listOf(MatchComparisonEntry("one", changed))).matches.single()
        assertEquals(listOf(MatchComparisonPlayerUses("Camerupt", 3, 1),
            MatchComparisonPlayerUses("Gourgeist", 1, 0)), match.playerUses)
    }

    @Test fun `a missing result and zero recorded attacks stay explicit`() {
        val empty = MatchArchive(matchId = "empty", articles = listOf(
            article("empty", "start", 0, ArchivedMatchStarted),
            article("empty", "player", 1_000, ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Camerupt", 323))))
        val projection = MatchComparison.project(listOf(MatchComparisonEntry("empty", empty)))
        assertNull(projection.matches.single().summary!!.result)
        assertEquals(MatchComparisonPlayerUses("Camerupt", 0, 0), projection.matches.single().playerUses.single())
        assertEquals(1, projection.maximumUses)
        assertEquals(1, MatchComparison.project(emptyList()).maximumUses)
    }

    @Test fun `missing GO keeps recorded combat as unattributed rather than inventing species credit`() {
        val missingStart = archive("one", 4).let { archive -> archive.copy(articles =
            archive.articles.filterNot { it.payload is ArchivedMatchStarted }) }
        val projection = MatchComparison.project(listOf(MatchComparisonEntry("one", missingStart)))
        val match = projection.matches.single()
        assertNull(match.summary!!.battleDurationMillis)
        assertEquals(4, match.unattributedPlayerFastMoveUses)
        assertEquals(1, match.unattributedPlayerChargedMoveUses)
        assertEquals(MatchComparisonPlayerUses("Camerupt", 0, 0), match.playerUses.single())
        assertEquals(4, projection.maximumUses)
    }

    @Test fun `configured unseen team and foreign match testimony do not pollute selected match`() {
        val source = archive("one", 2)
        val mixed = source.copy(articles = source.articles + listOf(
            article("one", "configured", 500, ArchivedPlayerTeamSlotConfigured(2, "Turtonator", 776, "Incinerate", emptyList())),
            article("foreign", "foreign-species", 25_000, ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Gourgeist", 711)),
            article("foreign", "foreign-hit", 26_000, use("foreign-use"))))
        val match = MatchComparison.project(listOf(MatchComparisonEntry("one", mixed))).matches.single()
        assertEquals(listOf("Camerupt"), match.playerUses.map { it.speciesName })
        assertEquals(2, match.summary!!.player.fastMoveUses)
        assertEquals(2, match.playerUses.single().fastMoveUses)
    }

    @Test fun `unavailable selections stay in place and mismatched archives are never substituted`() {
        val projected = MatchComparison.project(listOf(MatchComparisonEntry("missing", null, "Not found"),
            MatchComparisonEntry("selected", archive("different", 6)), MatchComparisonEntry("missing", archive("missing", 1))))
        assertEquals(listOf("missing", "selected"), projected.matches.map { it.matchId })
        assertNull(projected.matches[0].summary)
        assertEquals("Not found", projected.matches[0].error)
        assertNull(projected.matches[1].summary)
        assertNotNull(projected.matches[1].error)
    }

    @Test fun `transient refresh errors retain a matching saved summary and chart data`() {
        val retained = MatchComparison.project(listOf(MatchComparisonEntry("one", archive("one", 5), "Try again later"))).matches.single()
        assertNotNull(retained.summary)
        assertEquals(5, retained.playerUses.single().fastMoveUses)
        assertEquals("Try again later", retained.error)
    }
}
