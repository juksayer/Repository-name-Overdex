package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import org.junit.Assert.*
import org.junit.Test

class ReplayTeamLossProjectionTest {
    @Test fun `a count loss without unique actor does not retain an old guessed faint`() {
        val archive = archive(alive = false)
        val original = MatchArchiveSerializer.serialize(archive)
        val model = MatchReplayModel(archive)
        assertTrue(model.eventMarkers.none { it.kind == ReplayEventMarkerKind.FAINT })
        assertFalse(model.sceneAt(19L * SECOND).opponent!!.isFainted)
        assertFalse(model.sceneAt(22L * SECOND).opponent!!.isFainted)
        assertTrue(model.timedArticles.any { it.articleId == "wrong-faint" })
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `later positive HP corrects the actor and preserves original source clocks and raw articles`() {
        val archive = archive(alive = true)
        val original = MatchArchiveSerializer.serialize(archive)
        for (input in listOf(archive, archive.copy(articles = archive.articles.reversed()))) {
            val model = MatchReplayModel(input)
            val marker = model.eventMarkers.single { it.kind == ReplayEventMarkerKind.FAINT }
            assertEquals("Faint · Gourgeist", marker.label)
            assertEquals(21L * SECOND, marker.sourceAtNanos)
            assertTrue(marker.sourceArticleIds.containsAll(listOf("first", "before", "after", "alive")))
            assertFalse("The incorrect derived label is not evidence", "wrong-faint" in marker.sourceArticleIds)
            val outgoing = model.sceneAt(20L * SECOND - 50_000_000L).opponent!!
            assertTrue(outgoing.isFainted)
            assertEquals(ReplayTeamLossProjection.BASIS, outgoing.knockoutBasis)
            assertEquals(21L * SECOND, outgoing.faintedAtNanos)
            assertFalse(model.sceneAt(22L * SECOND).opponent!!.isFainted)
        }
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `independent count repair does not depend on the old faint or its citations`() {
        val original = archive(alive = true)
        val without = original.copy(articles = original.articles.filterNot { it.articleId == "wrong-faint" })
        assertEquals(MatchReplayModel(original).eventMarkers, MatchReplayModel(without).eventMarkers)
    }

    @Test fun `conflicting raw identity IDs remain barriers instead of silently restoring an old actor`() {
        val base = archive(alive = false)
        val input = base.copy(articles = base.articles + listOf(
            event("conflict", ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Quagsire", 195), 19),
            event("conflict", ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Camerupt", 323), 19),
            event("alive", ArchivedActiveHpBarMeasured("OPPONENT", 0, 0, 200, 15, .5f), 22),
        ))
        assertTrue(ReplayTeamLossProjection(input).resolutionsByArticleId.isEmpty())
        assertTrue(ReplayTeamLossProjection(input.copy(articles = input.articles.reversed()))
            .resolutionsByArticleId.isEmpty())
    }

    @Test fun `legacy non-count faint remains available without contradicting observations`() {
        val input = archive(alive = false).copy(articles = listOf(
            event("first", ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Carbink", 703), 0),
            event("faint", ArchivedActivePokemonFaintedWitnessed("OPPONENT", "Carbink", 703, "TEST"), 10),
        ))
        assertTrue(MatchReplayModel(input).sceneAt(11L * SECOND).opponent!!.isFainted)
    }

    @Test fun `explicit faint contradicted by independent later survival cannot override the count projection`() {
        val input = archive(alive = true).let { archive -> archive.copy(articles = archive.articles.map {
            if (it.articleId == "wrong-faint") it.copy(payload =
                (it.payload as ArchivedActivePokemonFaintedWitnessed).copy(basis = "EXPLICIT_TEST")) else it
        }) }
        val model = MatchReplayModel(input)
        assertEquals(listOf("Faint · Gourgeist"), model.eventMarkers.filter {
            it.kind == ReplayEventMarkerKind.FAINT }.map { it.label })
        assertFalse(model.sceneAt(22L * SECOND).opponent!!.isFainted)
    }

    @Test fun `globally contradictory identity cannot remove a legacy faint through later HP`() {
        // Cover both one name with conflicting IDs and one ID with conflicting names.
        for ((earlierName, laterId) in listOf("Carbink" to 711, "Gourgeist" to 703)) {
            val input = MatchArchive(matchId = "projection", articles = listOf(
                event("earlier", ArchivedActivePokemonSpeciesWitnessed("OPPONENT", earlierName, 703), 0),
                event("before", ArchivedOpponentBattleResourceCountMeasured("POKE_BALLS", 3, 3), 1),
                event("legacy", ArchivedActivePokemonFaintedWitnessed("OPPONENT", "Carbink", 703,
                    "EXPLICIT_TEST"), 10),
                event("after", ArchivedOpponentBattleResourceCountMeasured("POKE_BALLS", 2, 3), 11),
                event("later", ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Carbink", laterId), 12),
                event("alive", ArchivedActiveHpBarMeasured("OPPONENT", 0, 0, 200, 15, .5f), 13),
            ))
            for (ordered in listOf(input, input.copy(articles = input.articles.reversed()))) {
                val projection = ReplayTeamLossProjection(ordered)
                assertTrue(projection.resolutionsByArticleId.isEmpty())
                assertTrue("Ambiguous HP ownership must not erase an independent legacy faint",
                    projection.articles.any { it.articleId == "legacy" })
            }
        }
    }

    private fun archive(alive: Boolean): MatchArchive = MatchArchive(matchId = "projection", articles = buildList {
        add(event("first", ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Gourgeist", 711), 0))
        add(event("before", ArchivedOpponentBattleResourceCountMeasured("POKE_BALLS", 3, 3), 10))
        add(event("second", ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Carbink", 703), 20))
        add(event("after", ArchivedOpponentBattleResourceCountMeasured("POKE_BALLS", 2, 3), 21))
        add(event("wrong-faint", ArchivedActivePokemonFaintedWitnessed("OPPONENT", "Carbink", 703,
            "OPPONENT_POKE_BALL_COUNT_DECREASE"), 21))
        if (alive) add(event("alive", ArchivedActiveHpBarMeasured("OPPONENT", 0, 0, 200, 15, .2f), 22))
    })

    private fun event(id: String, payload: ArchivedTestimonyPayload, seconds: Long) = ArchivedRealityArticle(
        id, "projection", 0, 0, "TEST", payload, emptyList(), null, null, emptyList(), seconds * SECOND)

    private companion object { const val SECOND = 1_000_000_000L }
}
