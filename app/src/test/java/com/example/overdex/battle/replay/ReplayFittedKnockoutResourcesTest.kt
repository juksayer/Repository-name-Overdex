package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import org.junit.Assert.*
import org.junit.Test

class ReplayFittedKnockoutResourcesTest {
    @Test fun `Blanche incoming actors inherit the count that supports the fitted outgoing faint`() {
        val archive = MatchArchiveSerializer.deserialize(checkNotNull(
            javaClass.getResource("/replay/nov7-blanche-resource-lifetimes.json")).readText())
        val original = MatchArchiveSerializer.serialize(archive)
        val model = MatchReplayModel(archive)
        for ((incoming, count) in listOf("Sealeo" to 2, "Vaporeon" to 1)) {
            val entryAt = archive.articles.single {
                (it.payload as? ArchivedActivePokemonSpeciesWitnessed)?.speciesName == incoming
            }.monotonicTimeNanos!!
            val fallAt = entryAt - ReplayKnockoutTrack.MIN_VISIBLE_KO_NANOS
            assertEquals(count + 1, model.sceneAt(fallAt - 1).opponentResources.pokeBalls)
            assertEquals(count, model.sceneAt(fallAt).opponentResources.pokeBalls)
            assertEquals(count, model.sceneAt(entryAt).opponentResources.pokeBalls)
            assertEquals(count, model.sceneAt(entryAt + SECOND).opponentResources.pokeBalls)
            // Reverse scrubbing recomputes the earlier state; there is no sticky decrement.
            assertEquals(count + 1, model.sceneAt(fallAt - 1).opponentResources.pokeBalls)
        }
        val resultAt = archive.articles.single { it.payload is ArchivedMatchEnded }.monotonicTimeNanos!!
        assertEquals(1, model.sceneAt(resultAt).opponentResources.pokeBalls)
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `supported projection applies on either side without double decrementing or spending shields`() {
        for (side in listOf("PLAYER", "OPPONENT")) {
            val archive = archive(side)
            val model = MatchReplayModel(archive)
            val fallAt = 10 * SECOND - ReplayKnockoutTrack.MIN_VISIBLE_KO_NANOS
            for (at in listOf(fallAt, 10 * SECOND, 14 * SECOND, 20 * SECOND)) {
                val scene = model.sceneAt(at)
                val resources = if (side == "PLAYER") scene.playerResources else scene.opponentResources
                val other = if (side == "PLAYER") scene.opponentResources else scene.playerResources
                assertEquals(2, resources.pokeBalls)
                assertEquals(2, resources.shields)
                assertNull(other.pokeBalls)
                assertNull(other.shields)
            }
        }
    }

    @Test fun `an unproven departure cannot move its later count earlier`() {
        for (variant in listOf("missing-survival", "unchanged-after-entry", "other-side", "multi-loss")) {
            val base = archive("OPPONENT")
            val articles = when (variant) {
                "missing-survival" -> base.articles.filterNot { it.articleId == "alive" }
                "unchanged-after-entry" -> base.articles + event("switch-proof", balls("OPPONENT", 3), 11 * SECOND)
                "other-side" -> base.articles.map {
                    if (it.articleId == "after") it.copy(payload = balls("PLAYER", 2)) else it
                }
                else -> base.articles.map {
                    if (it.articleId == "after") it.copy(payload = balls("OPPONENT", 1)) else it
                }
            }
            assertEquals(variant, 3, MatchReplayModel(base.copy(articles = articles))
                .sceneAt(10 * SECOND).opponentResources.pokeBalls)
        }
    }

    @Test fun `nearby named faint without resource custody does not invent a count`() {
        val archive = archive("PLAYER").copy(articles = listOf(
            event("lead", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Obstagoon", 862), 0),
            event("faint", ArchivedActivePokemonFaintedWitnessed("PLAYER", "Obstagoon", 862, "TEST"), 10 * SECOND),
            event("entry", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Lucario", 448), 11 * SECOND),
        ))
        val scene = MatchReplayModel(archive).sceneAt(10 * SECOND)
        assertTrue(scene.player!!.isFainted)
        assertNull(scene.playerResources.pokeBalls)
    }

    @Test fun `one to zero evidence cannot backdate an impossible zero count onto an incoming actor`() {
        for (side in listOf("PLAYER", "OPPONENT")) {
            val base = archive(side)
            val contradictory = base.copy(articles = base.articles.map {
                when (it.articleId) {
                    "before" -> it.copy(payload = balls(side, 1))
                    "after" -> it.copy(payload = balls(side, 0))
                    else -> it
                }
            })
            val model = MatchReplayModel(contradictory)
            val justBefore = model.sceneAt(10 * SECOND - 50_000_000L)
            val outgoing = if (side == "PLAYER") justBefore.player else justBefore.opponent
            assertFalse(outgoing!!.isFainted)
            val incoming = model.sceneAt(10 * SECOND)
            assertEquals("Lucario", (if (side == "PLAYER") incoming.player else incoming.opponent)!!.speciesName)
            assertEquals(1, (if (side == "PLAYER") incoming.playerResources else incoming.opponentResources).pokeBalls)
        }
    }

    private fun archive(side: String) = MatchArchive(matchId = "resources", articles = listOf(
        event("lead", ArchivedActivePokemonSpeciesWitnessed(side, "Obstagoon", 862), 0),
        event("go", ArchivedMatchStarted, 0),
        event("before", balls(side, 3), 5 * SECOND),
        event("shields", if (side == "PLAYER") ArchivedPlayerShieldCountMeasured(2, 2)
            else ArchivedOpponentBattleResourceCountMeasured("SHIELDS", 2, 2), 5 * SECOND),
        event("entry", ArchivedActivePokemonSpeciesWitnessed(side, "Lucario", 448), 10 * SECOND),
        event("after", balls(side, 2), 14 * SECOND),
        event("alive", ArchivedActiveHpBarMeasured(side, 0, 0, 200, 15, .5f), 15 * SECOND),
        event("faint", ArchivedActivePokemonFaintedWitnessed(side, "Obstagoon", 862,
            "${side}_POKE_BALL_COUNT_DECREASE"), 14 * SECOND).copy(evidenceReferences = listOf("lead", "before", "after")),
    ))

    private fun balls(side: String, count: Int): ArchivedTestimonyPayload = if (side == "PLAYER")
        ArchivedPlayerPokeBallCountMeasured(count, 3) else ArchivedOpponentBattleResourceCountMeasured("POKE_BALLS", count, 3)

    private fun event(id: String, payload: ArchivedTestimonyPayload, at: Long) = ArchivedRealityArticle(
        id, "resources", 0, 0, "TEST", payload, emptyList(), null, null, emptyList(), at)

    private companion object { const val SECOND = 1_000_000_000L }
}
