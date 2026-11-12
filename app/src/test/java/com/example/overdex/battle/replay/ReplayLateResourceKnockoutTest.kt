package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import org.junit.Assert.*
import org.junit.Test

class ReplayLateResourceKnockoutTest {
    @Test fun `Blanche resource chains preserve both late non-final knockouts before their replacements`() {
        // Exact source clocks, IDs, counter values and custody links; no media.
        val archive = MatchArchiveSerializer.deserialize(checkNotNull(
            javaClass.getResource("/replay/nov7-blanche-resource-lifetimes.json")).readText())
        val original = MatchArchiveSerializer.serialize(archive)
        val model = MatchReplayModel(archive)
        for ((outgoing, incoming) in listOf("Sneasel" to "Sealeo", "Sealeo" to "Vaporeon")) {
            val replacement = archive.articles.single {
                (it.payload as? ArchivedActivePokemonSpeciesWitnessed)?.speciesName == incoming
            }
            val faint = archive.articles.single {
                (it.payload as? ArchivedActivePokemonFaintedWitnessed)?.speciesName == outgoing
            }
            val entryAt = replacement.monotonicTimeNanos!!
            assertTrue(faint.monotonicTimeNanos!! - entryAt > 2 * SECOND)
            val justBefore = model.sceneAt(entryAt - 50_000_000L).opponent!!
            assertEquals(outgoing, justBefore.speciesName)
            assertTrue(justBefore.isFainted)
            assertEquals(faint.monotonicTimeNanos, justBefore.faintedAtNanos)
            assertEquals(entryAt - ReplayKnockoutTrack.MIN_VISIBLE_KO_NANOS, justBefore.knockoutPresentationAtNanos)
            assertTrue(justBefore.knockoutSourceArticleIds.containsAll(faint.evidenceReferences!!))
            val pose = ReplayFaintPose.at(justBefore.knockoutPresentationAtNanos, entryAt - 50_000_000L,
                false, justBefore.knockoutFinishByNanos)!!
            assertEquals(90f, pose.rotationDegrees, 0f)
            assertEquals(0f, pose.saturation, 0f)
            val next = model.sceneAt(entryAt).opponent!!
            assertEquals(incoming, next.speciesName)
            assertFalse(next.isFainted)
        }
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `a directly cited one-ball drop can fit a late named knockout on either side`() {
        for (side in listOf("PLAYER", "OPPONENT")) {
            val archive = archive(side)
            val original = MatchArchiveSerializer.serialize(archive)
            val model = MatchReplayModel(archive)
            val before = combatant(model, side, 9_950)
            assertEquals("Obstagoon", before.speciesName)
            assertTrue(before.isFainted)
            assertEquals(14 * SECOND, before.faintedAtNanos)
            assertTrue(before.knockoutSourceArticleIds.containsAll(listOf("replacement", "first", "before", "after", "alive")))
            assertEquals(ReplayTeamLossProjection.BASIS, before.knockoutBasis)
            assertEquals("Lucario", combatant(model, side, 10_000).speciesName)
            assertFalse(combatant(model, side, 14_000).isFainted)
            assertEquals(original, MatchArchiveSerializer.serialize(archive))
        }
    }

    @Test fun `independent source evidence resolves regardless of the legacy faint citations`() {
        val original = archive("OPPONENT")
        for (references in listOf(emptyList(), listOf("first"), listOf("before", "after"))) {
            assertTrue(combatant(MatchReplayModel(replace(original, "faint") { it.copy(evidenceReferences = references) }), "OPPONENT", 9_950).isFainted)
        }
    }

    @Test fun `opposite side and shield resources cannot corroborate the named faint`() {
        for (kind in listOf("OTHER_SIDE", "SHIELDS")) {
            var original = archive("OPPONENT")
            for (id in listOf("before", "after")) original = replace(original, id) { article ->
                val count = (article.payload as ArchivedOpponentBattleResourceCountMeasured).visibleCount
                article.copy(payload = if (kind == "OTHER_SIDE") ArchivedPlayerPokeBallCountMeasured(count, 3)
                    else ArchivedOpponentBattleResourceCountMeasured("SHIELDS", count, 3))
            }
            assertNoOutgoingKo(original)
        }
    }

    @Test fun `a nondecrease or loss of multiple balls is not a uniquely corroborated knockout`() {
        for (after in listOf(3, 1, 0, -1, 4)) {
            assertNoOutgoingKo(replace(archive("OPPONENT"), "after") {
                it.copy(payload = ArchivedOpponentBattleResourceCountMeasured("POKE_BALLS", after, 3))
            })
        }
    }

    @Test fun `wrong match and invalid maximum counts cannot corroborate a faint`() {
        for (mutate in listOf<(ArchivedRealityArticle) -> ArchivedRealityArticle>(
            { it.copy(matchId = "another") },
            { it.copy(payload = ArchivedOpponentBattleResourceCountMeasured("POKE_BALLS", 2, 2)) },
        )) assertNoOutgoingKo(replace(archive("OPPONENT"), "after", mutate))
    }

    @Test fun `an unchanged ball count after replacement preserves the voluntary switch`() {
        val original = archive("OPPONENT")
        val stable = event("stable", ArchivedOpponentBattleResourceCountMeasured("POKE_BALLS", 3, 3), 11_000)
        assertNoOutgoingKo(original.copy(articles = original.articles + stable))
    }

    @Test fun `raw source history selects the returning appearance regardless of old identity citations`() {
        val original = archive("PLAYER")
        val returned = original.copy(articles = original.articles.map {
            when (it.articleId) {
                "replacement" -> it.copy(monotonicTimeNanos = 20 * SECOND)
                "before" -> it.copy(monotonicTimeNanos = 15 * SECOND)
                "after", "faint" -> it.copy(monotonicTimeNanos = 25 * SECOND)
                "alive" -> it.copy(monotonicTimeNanos = 26 * SECOND)
                else -> it
            }
        } + listOf(
            event("away", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Lucario", 448), 5_000),
            event("return", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Obstagoon", 862), 10_000),
        ))
        val model = MatchReplayModel(returned)
        assertTrue(combatant(model, "PLAYER", 19_950).isFainted)
        assertFalse(combatant(model, "PLAYER", 4_950).isFainted)
        // Old derived citations do not change the independent resolution.
        val linked = MatchReplayModel(replace(returned, "faint") {
            it.copy(evidenceReferences = listOf("return", "before", "after"))
        })
        assertTrue(combatant(linked, "PLAYER", 19_950).isFainted)
        assertFalse(combatant(linked, "PLAYER", 4_950).isFainted)
    }

    @Test fun `positive HP on the returning species excludes it from the intervening team loss`() {
        val original = archive("PLAYER")
        val model = MatchReplayModel(original.copy(articles = original.articles +
            event("return", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Obstagoon", 862), 13_000)))
        assertFalse(combatant(model, "PLAYER", 9_950).isFainted)
        assertFalse(combatant(model, "PLAYER", 13_500).isFainted)
        assertFalse(combatant(model, "PLAYER", 14_500).isFainted)
        assertTrue(combatant(model, "PLAYER", 12_950).isFainted)
    }

    @Test fun `direct predecessor custody links also corroborate the delayed counter faint`() {
        val linked = replace(archive("PLAYER"), "faint") {
            it.copy(predecessorIds = it.evidenceReferences!!, evidenceReferences = emptyList())
        }
        assertTrue(combatant(MatchReplayModel(linked), "PLAYER", 9_950).isFainted)
    }

    private fun assertNoOutgoingKo(archive: MatchArchive) {
        val model = MatchReplayModel(archive)
        assertFalse(combatant(model, "OPPONENT", 9_950).isFainted)
        assertNull(model.sceneAt(9_950 * MS).decisionTimer)
    }

    private fun archive(side: String) = MatchArchive(matchId = "late-resource", articles = listOf(
        event("first", ArchivedActivePokemonSpeciesWitnessed(side, "Obstagoon", 862), 0),
        event("other", ArchivedActivePokemonSpeciesWitnessed(if (side == "PLAYER") "OPPONENT" else "PLAYER", "Camerupt", 323), 0),
        event("go", ArchivedMatchStarted, 0),
        event("before", balls(side, 3), 5_000),
        event("replacement", ArchivedActivePokemonSpeciesWitnessed(side, "Lucario", 448), 10_000),
        event("after", balls(side, 2), 14_000),
        event("alive", ArchivedActiveHpBarMeasured(side, 0, 0, 200, 15, .5f), 15_000),
        event("faint", ArchivedActivePokemonFaintedWitnessed(side, "Obstagoon", 862,
            "${side}_POKE_BALL_COUNT_DECREASE"), 14_000).copy(evidenceReferences = listOf("first", "before", "after")),
    ))

    private fun balls(side: String, count: Int): ArchivedTestimonyPayload = if (side == "PLAYER")
        ArchivedPlayerPokeBallCountMeasured(count, 3) else ArchivedOpponentBattleResourceCountMeasured("POKE_BALLS", count, 3)

    private fun replace(archive: MatchArchive, id: String, change: (ArchivedRealityArticle) -> ArchivedRealityArticle) =
        archive.copy(articles = archive.articles.map { if (it.articleId == id) change(it) else it })

    private fun combatant(model: MatchReplayModel, side: String, ms: Long) = model.sceneAt(ms * MS).let {
        (if (side == "PLAYER") it.player else it.opponent)!!
    }

    private fun event(id: String, payload: ArchivedTestimonyPayload, ms: Long) = ArchivedRealityArticle(
        id, "late-resource", 0, 0, "TEST", payload, emptyList(), null, null, emptyList(), ms * MS)

    private companion object { const val MS = 1_000_000L; const val SECOND = 1_000_000_000L }
}
