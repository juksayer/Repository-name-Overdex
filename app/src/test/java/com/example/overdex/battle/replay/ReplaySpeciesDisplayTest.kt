package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.team.OwnedPokemonBattleSnapshot
import com.example.overdex.model.PokemonSize
import org.junit.Assert.*
import org.junit.Test

class ReplaySpeciesDisplayTest {
    @Test fun `replay names markers and generated announcements omit unconfirmed catalogue size`() {
        val archive = MatchArchive(matchId = "display", articles = listOf(
            event("team", ArchivedPlayerTeamSlotConfigured(3, "Gourgeist (Average)", 711,
                "Hex", listOf("Seed Bomb")), 0),
            event("lead", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Turtonator", 776), 1),
            event("entry", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Gourgeist (Average)", 711), 5)
                .copy(sourceId = "ENTRY_ANNOUNCEMENT_SPECIES_WITNESS"),
            event("charge", ArchivedChargedMoveEnergySpent("PLAYER", "Gourgeist (Average)",
                "Seed Bomb", 45, "TEST"), 10),
            event("faint", ArchivedActivePokemonFaintedWitnessed("PLAYER", "Gourgeist (Average)", 711, "TEST"), 20),
        ))
        val original = MatchArchiveSerializer.serialize(archive)
        val replay = MatchReplayModel(archive)
        assertEquals("Gourgeist (Average)", replay.sceneAt(6 * SECOND).player!!.speciesName)
        assertEquals("Gourgeist", replay.sceneAt(6 * SECOND).player!!.displayName)
        assertEquals("Go, Gourgeist!", replay.sceneAt(6 * SECOND).announcement)
        assertEquals("Gourgeist used Seed Bomb!", replay.sceneAt(10 * SECOND).announcement)
        assertTrue(replay.eventMarkers.any { it.label == "Active change → Gourgeist" })
        assertEquals("Faint · Gourgeist", replay.eventMarkers.single { it.kind == ReplayEventMarkerKind.FAINT }.label)
        assertEquals("FAINTED: Gourgeist", replay.sceneAt(20 * SECOND).latestEvidenceLabel)
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `explicit specimen size remains separate and is not converted to a catalogue form`() {
        val snapshot = OwnedPokemonBattleSnapshot("owned", 711, size = PokemonSize.XXL,
            speciesName = "Gourgeist (Average)")
        val archive = MatchArchive(matchId = "display", articles = listOf(
            event("team", ArchivedPlayerTeamSlotConfigured(1, "Gourgeist (Average)", 711,
                "Hex", listOf("Seed Bomb"), snapshot), 0),
            event("badge", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Gourgeist", 711), 1),
        ))
        val original = MatchArchiveSerializer.serialize(archive)
        val combatant = MatchReplayModel(archive).sceneAt(2 * SECOND).player!!
        assertEquals("Gourgeist", combatant.displayName)
        assertEquals("Gourgeist (Average)", combatant.speciesName)
        assertEquals(PokemonSize.XXL, (archive.articles.first().payload as ArchivedPlayerTeamSlotConfigured).ownedPokemon!!.size)
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `explicit regional form stays visible and keeps its distinct internal name`() {
        val combatant = ReplayCombatant("Raichu (Alolan)", 26, 0)
        assertEquals("Raichu (Alolan)", combatant.displayName)
        assertEquals("Raichu (Alolan)", combatant.speciesName)
    }

    private fun event(id: String, payload: ArchivedTestimonyPayload, seconds: Long) = ArchivedRealityArticle(
        id, "display", 0, 0, "TEST", payload, emptyList(), null, null, emptyList(), seconds * SECOND)
    private companion object { const val SECOND = 1_000_000_000L }
}
