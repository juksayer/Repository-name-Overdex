package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import org.junit.Assert.*
import org.junit.Test

class ReplayOpeningPlayerTest {
    @Test fun `Blanche prompt Turtonator badge removes the unsupported Golbat opening without changing source clocks`() {
        val archive = archive()
        val original = MatchArchiveSerializer.serialize(archive)
        val model = MatchReplayModel(archive)

        for (cursor in listOf(GO - 2 * SECOND, GO, BADGE_AT - 1, BADGE_AT)) {
            val player = model.sceneAt(cursor).player!!
            assertEquals("Turtonator", player.speciesName)
            assertEquals("The later species-establishment instant remains available", BADGE_AT,
                player.identityEstablishedAtNanos)
            assertTrue("The stale Golbat loadout cannot equip Turtonator",
                model.sceneAt(cursor).playerChargedMoves.none { it.moveName in listOf("Poison Fang", "Shadow Ball") })
        }
        assertEquals(GO, model.matchStartNanos)
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `a roster consistent immediate replacement does not erase the configured lead`() {
        val archive = archive().copy(articles = archive().articles +
            event("actual-member", ArchivedPlayerTeamSlotConfigured(2, "Turtonator", 776,
                "Incinerate", listOf("Brutal Swing")), BASE + SECOND))
        val model = MatchReplayModel(archive)

        assertEquals("Golbat", model.sceneAt(GO).player?.speciesName)
        assertEquals("Turtonator", model.sceneAt(BADGE_AT).player?.speciesName)
    }

    @Test fun `a positively witnessed opening lead preserves a genuine quick switch`() {
        for (support in listOf(
            badge("lead-badge", "Golbat", 42, GO - SECOND),
            event("lead-inference", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Golbat", 42), GO,
                "TEAM_SELECT_LEAD_INFERENCE"),
            event("lead-slot", ArchivedPlayerTeamRosterSlotWitnessed(1, "Golbat", 42), GO - SECOND),
        )) {
            val model = MatchReplayModel(archive(support))
            assertEquals(support.articleId, "Golbat", model.sceneAt(GO).player?.speciesName)
            assertEquals(support.articleId, "Turtonator", model.sceneAt(BADGE_AT).player?.speciesName)
        }
    }

    @Test fun `a post GO player entry preserves its own switch boundary instead of backfilling the opener`() {
        val entryAt = GO + 500_000_000L
        val source = archive(event("entry", ArchivedRawText("Go, Turtonator!"), entryAt, "ANNOUNCEMENT_WITNESS"))
        val model = MatchReplayModel(source)

        assertEquals("Golbat", model.sceneAt(GO).player?.speciesName)
        assertEquals("Golbat", model.sceneAt(entryAt - 1).player?.speciesName)
        assertEquals("Turtonator", model.sceneAt(BADGE_AT).player?.speciesName)
    }

    @Test fun `an opening faint or depleted player resource preserves possible replacement evidence`() {
        for (support in listOf(
            event("faint", ArchivedActivePokemonFaintedWitnessed("PLAYER", "Golbat", 42,
                "PLAYER_POKE_BALL_COUNT_DECREASE"), GO + 500_000_000L),
            event("count", ArchivedPlayerPokeBallCountMeasured(2, 3), GO + 500_000_000L),
        )) {
            val model = MatchReplayModel(archive(support))
            assertEquals(support.articleId, "Golbat", model.sceneAt(GO).player?.speciesName)
            assertEquals(support.articleId, "Turtonator", model.sceneAt(BADGE_AT).player?.speciesName)
        }
    }

    @Test fun `a late weak unlocated or untimed badge cannot reject the saved opener`() {
        for (mutate in listOf<(ArchivedRealityArticle) -> ArchivedRealityArticle>(
            { it.copy(monotonicTimeNanos = GO + 2 * SECOND + 1) },
            { it.copy(confidence = .60f) },
            { it.copy(confidence = null) },
            { it.copy(sourceId = "DECISIVE_BATTLE_CRY_SPECIES_WITNESS") },
            { it.copy(monotonicTimeNanos = null) },
        )) {
            val changed = archive().let { source -> source.copy(articles = source.articles.map {
                if (it.articleId == "actual-badge") mutate(it) else it
            }) }
            assertFalse(configuredLeadContradicted(changed.articles))
            assertEquals("Golbat", MatchReplayModel(changed).sceneAt(GO).player?.speciesName)
        }
    }

    @Test fun `missing GO or missing contradictory badge retains the configured fallback`() {
        for (omitted in listOf("go", "actual-badge")) {
            val source = archive().let { it.copy(articles = it.articles.filterNot { a -> a.articleId == omitted }) }
            assertFalse(configuredLeadContradicted(source.articles))
            assertEquals("Golbat", MatchReplayModel(source).sceneAt(GO).player?.speciesName)
        }
    }

    private fun archive(vararg support: ArchivedRealityArticle) = MatchArchive(matchId = MATCH, articles = (
        listOf(
            event("record", ArchivedMatchRecordStarted, BASE),
            event("configured", ArchivedPlayerTeamSlotConfigured(1, "Golbat", 42,
                "Wing Attack", listOf("Poison Fang", "Shadow Ball")), BASE),
            event("configured-2", ArchivedPlayerTeamSlotConfigured(2, "Greninja", 658,
                "Water Shuriken", listOf("Night Slash", "Aerial Ace")), BASE),
            event("configured-3", ArchivedPlayerTeamSlotConfigured(3, "Victreebel", 71,
                "Razor Leaf", listOf("Acid Spray", "Leaf Tornado")), BASE),
            event("countdown", ArchivedCountdownGlyphWitnessed("3", .98f, 1, basis = "TEST"), GO - 3 * SECOND),
            event("go", ArchivedMatchStarted, GO),
            badge("opponent", "Sneasel", 215, GO + 100_000_000L).copy(
                sourceId = "OPPONENT_SPECIES_OVERLAY_PIPELINE",
                payload = ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Sneasel", 215)),
            badge("actual-badge", "Turtonator", 776, BADGE_AT),
        ) + support).sortedBy { it.monotonicTimeNanos })

    private fun badge(id: String, name: String, speciesId: Int, at: Long) =
        event(id, ArchivedActivePokemonSpeciesWitnessed("PLAYER", name, speciesId), at,
            "PLAYER_SPECIES_OVERLAY_PIPELINE").copy(confidence = .98f)

    private fun event(id: String, payload: ArchivedTestimonyPayload, at: Long, source: String = "TEST") =
        ArchivedRealityArticle(id, MATCH, 0, 0, source, payload, emptyList(), null, null, emptyList(), at)

    private companion object {
        const val MATCH = "blanche-opening"
        const val SECOND = 1_000_000_000L
        const val BASE = 133_910_498_744_673L
        const val GO = BASE + 42_202_672_310L
        const val BADGE_AT = BASE + 43_633_752_925L
    }
}
