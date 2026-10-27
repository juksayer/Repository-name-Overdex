package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.model.Move
import com.example.overdex.model.PokemonType
import org.junit.Assert.*
import org.junit.Test

class ReplayRosterConfirmationTest {
    @Test fun `stale configured fast move and energy do not name or credit current roster attacks`() {
        val archive = archive()
        val original = MatchArchiveSerializer.serialize(archive)
        val model = model(archive)
        val actions = (0..60).flatMap { model.sceneAt(it * 100_000_000L).fastMoveActions }.filter { it.side == "PLAYER" }
        assertTrue(actions.isNotEmpty())
        assertTrue(actions.all { it.moveName == null })
        assertEquals(0, model.sceneAt(6_000_000_000L).playerEnergy?.generated)
        assertTrue(model.sceneAt(6_000_000_000L).playerChargedMoves.isEmpty())
        assertEquals(original, MatchArchiveSerializer.serialize(archive))
    }

    @Test fun `observed move evidence after roster confirmation remains usable`() {
        val base = archive()
        val observation = event("observed", 3.0, ArchivedFastMoveIdentified("PLAYER", "Camerupt", "Incinerate",
            2_500_000_000L, cadenceSampleCount = 3, basis = "HP_BORDER_CADENCE"))
        val model = model(base.copy(articles = base.articles + observation))
        val actions = (0..60).flatMap { model.sceneAt(it * 100_000_000L).fastMoveActions }.filter { it.side == "PLAYER" }
        assertTrue(actions.isNotEmpty())
        assertEquals(setOf("Incinerate"), actions.map { it.moveName }.toSet())
        assertEquals(0, model.sceneAt(6_000_000_000L).playerEnergy?.generated)
    }

    private fun archive(): MatchArchive {
        val configured = event("old-team", 0.0, ArchivedPlayerTeamSlotConfigured(1, "Camerupt", 323,
            "Ember", listOf("Earthquake")))
        val confirmation = event("confirmed", 1.0, ArchivedPlayerTeamRosterConfirmation(true, listOf("Camerupt", null, null)))
        val identified = event("stale-move", 3.0, ArchivedFastMoveIdentified("PLAYER", "Camerupt", "Ember",
            1_000_000_000L, cadenceSampleCount = 0, basis = "CONFIGURED_PLAYER_TEAM_AND_ACTIVE_SPECIES"))
            .copy(predecessorIds = listOf("old-team"))
        val hit = event("hit", 4.0, ArchivedFastMoveUseObserved("hit", "PLAYER", "OPPONENT",
            attackerSpeciesName = "Camerupt", evidenceKinds = listOf("HP_BORDER_PULSE"), basis = "FUSED"))
        val energy = event("energy", 4.0, ArchivedFastMoveEnergyDerived("PLAYER", "Ember", 1, 6, 6, "OBSERVED_USE"))
            .copy(predecessorIds = listOf("hit", "stale-move"))
        return MatchArchive(matchId = "match", articles = listOf(configured, confirmation,
            event("player", 1.5, ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Camerupt", 323)),
            event("opponent", 1.5, ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Sealeo", 364)),
            event("start", 2.0, ArchivedMatchStarted), identified, hit, energy,
            event("tail", 6.0, ArchivedRawText("observation continues"))))
    }

    private fun model(archive: MatchArchive) = MatchReplayModel(archive, fastMovesBySpeciesName = mapOf("Camerupt" to listOf(
        Move("Ember", PokemonType.FIRE, 7, 6, true, 2), Move("Incinerate", PokemonType.FIRE, 20, 20, true, 5))))

    private fun event(id: String, seconds: Double, payload: ArchivedTestimonyPayload) = ArchivedRealityArticle(
        id, "match", (seconds * 1000).toLong(), (seconds * 1000).toLong(), "TEST", payload,
        emptyList(), 1f, null, emptyList(), (seconds * 1_000_000_000L).toLong())
}
