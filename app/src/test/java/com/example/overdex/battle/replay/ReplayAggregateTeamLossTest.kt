package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import org.junit.Assert.*
import org.junit.Test

class ReplayAggregateTeamLossTest {
    @Test fun `both owners can be displayed on either side while their aggregate count stays at its capture time`() {
        for (side in listOf("PLAYER", "OPPONENT")) {
            val archive = archive(side, survival = true)
            val before = MatchArchiveSerializer.serialize(archive)
            val projection = ReplayTeamLossProjection(archive)
            assertEquals(2, projection.resolutionsByArticleId.size)
            assertEquals(setOf("Turtonator", "Gourgeist"), projection.resolutionsByArticleId.values
                .map { it.resolved!!.speciesName }.toSet())
            assertTrue(projection.resolutionsByArticleId.keys.all { it.startsWith("replay-team-loss:$side:after:") })
            val reverse = ReplayTeamLossProjection(archive.copy(articles = archive.articles.reversed()))
            assertEquals(projection.resolutionsByArticleId, reverse.resolutionsByArticleId)
            val replay = MatchReplayModel(archive)
            val faints = replay.eventMarkers.filter { it.kind == ReplayEventMarkerKind.FAINT }
            assertEquals(2, faints.size)
            assertTrue(faints.all { it.sourceAtNanos == 22 * SECOND && it.side == side })
            for (at in listOf(10, 19, 20, 21, 22, 25, 10)) {
                val scene = replay.sceneAt(at * SECOND)
                val resources = if (side == "PLAYER") scene.playerResources else scene.opponentResources
                assertEquals(if (at < 22) 3 else 1, resources.pokeBalls)
            }
            val incoming = replay.sceneAt(20 * SECOND)
            assertFalse((if (side == "PLAYER") incoming.player else incoming.opponent)!!.isFainted)
            assertEquals(before, MatchArchiveSerializer.serialize(archive))
        }
    }

    @Test fun `two losses with three possible owners cannot faint any actor until independent survival resolves them`() {
        for (side in listOf("PLAYER", "OPPONENT")) {
            val archive = archive(side, survival = false)
            assertTrue(ReplayTeamLossProjection(archive).resolutionsByArticleId.isEmpty())
            val replay = MatchReplayModel(archive)
            assertTrue(replay.eventMarkers.none { it.kind == ReplayEventMarkerKind.FAINT })
            val scene = replay.sceneAt(23 * SECOND)
            val actor = if (side == "PLAYER") scene.player else scene.opponent
            val resources = if (side == "PLAYER") scene.playerResources else scene.opponentResources
            assertFalse(actor!!.isFainted)
            assertEquals("The team loss itself is still known", 1, resources.pokeBalls)
        }
    }

    @Test fun `a delayed intermediate count replaces aggregate conclusions without duplicating faint owners`() {
        for (side in listOf("PLAYER", "OPPONENT")) {
            // These are retained historical conclusions made before count two
            // finished recognition. They must stay inspectable, but cannot
            // compete with the current projection of all independent sources.
            val aggregate = archive(side, survival = true).let { base -> base.copy(articles = base.articles + listOf(
                event("old-aggregate-lead", ArchivedActivePokemonFaintedWitnessed(side, "Turtonator", 776,
                    "${side}_POKE_BALL_COUNT_DECREASE_AGGREGATE"), 22),
                event("old-aggregate-second", ArchivedActivePokemonFaintedWitnessed(side, "Gourgeist", 711,
                    "${side}_POKE_BALL_COUNT_DECREASE_AGGREGATE"), 22),
            )) }
            val originalAggregate = MatchArchiveSerializer.serialize(aggregate)
            val complete = aggregate.copy(articles = aggregate.articles + event("late-middle", balls(side, 2), 12))
            val completeBytes = MatchArchiveSerializer.serialize(complete)
            for (input in listOf(complete, complete.copy(articles = complete.articles.reversed()))) {
                val projection = ReplayTeamLossProjection(input)
                assertEquals(setOf("replay-team-loss:$side:late-middle", "replay-team-loss:$side:after"),
                    projection.resolutionsByArticleId.keys)
                assertFalse(projection.articles.any { it.articleId.startsWith("old-aggregate-") })
                val replay = MatchReplayModel(input)
                val faints = replay.eventMarkers.filter { it.kind == ReplayEventMarkerKind.FAINT }
                assertEquals(2, faints.size)
                assertEquals(mapOf("Faint · Turtonator" to 12 * SECOND, "Faint · Gourgeist" to 22 * SECOND),
                    faints.associate { it.label to it.sourceAtNanos })
                assertTrue(faints.all { "late-middle" in it.sourceArticleIds })
                assertEquals(2, replay.timedArticles.count { it.articleId.startsWith("old-aggregate-") })
                val scene = replay.sceneAt(25 * SECOND)
                assertFalse((if (side == "PLAYER") scene.player else scene.opponent)!!.isFainted)
                assertEquals(1, (if (side == "PLAYER") scene.playerResources else scene.opponentResources).pokeBalls)
            }
            assertEquals(originalAggregate, MatchArchiveSerializer.serialize(aggregate))
            assertEquals(completeBytes, MatchArchiveSerializer.serialize(complete))
            assertEquals(22 * SECOND, aggregate.articles.single { it.articleId == "old-aggregate-lead" }.monotonicTimeNanos)
        }
    }

    private fun archive(side: String, survival: Boolean) = MatchArchive(matchId = "aggregate", articles = buildList {
        add(event("go", ArchivedMatchStarted, 0))
        add(event("lead", ArchivedActivePokemonSpeciesWitnessed(side, "Turtonator", 776), 0))
        add(event("before", balls(side, 3), 5))
        add(event("second", ArchivedActivePokemonSpeciesWitnessed(side, "Gourgeist", 711), 10))
        add(event("last", ArchivedActivePokemonSpeciesWitnessed(side, "Camerupt", 323), 20))
        add(event("after", balls(side, 1), 22))
        if (survival) add(event("alive", ArchivedActiveHpBarMeasured(side, 0, 0, 200, 15, .5f), 25))
    })

    private fun balls(side: String, count: Int): ArchivedTestimonyPayload = if (side == "PLAYER")
        ArchivedPlayerPokeBallCountMeasured(count, 3) else ArchivedOpponentBattleResourceCountMeasured("POKE_BALLS", count, 3)

    private fun event(id: String, payload: ArchivedTestimonyPayload, second: Long) = ArchivedRealityArticle(
        id, "aggregate", 0, 0, "TEST", payload, emptyList(), null, null, emptyList(), second * SECOND)

    private companion object { const val SECOND = 1_000_000_000L }
}
