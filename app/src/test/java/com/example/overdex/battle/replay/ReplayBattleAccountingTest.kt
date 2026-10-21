package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import org.junit.Assert.*
import org.junit.Test

class ReplayBattleAccountingTest {
    @Test fun `resources follow their own side and restore on a backward seek`() {
        val model = MatchReplayModel(MatchArchive(matchId = "match", articles = listOf(
            event("player", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Camerupt", 323), 1),
            event("opponent", ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Sealeo", 364), 1),
            event("pb", ArchivedPlayerPokeBallCountMeasured(3, 3), 2),
            event("ps", ArchivedPlayerShieldCountMeasured(2), 2),
            event("ob", ArchivedOpponentBattleResourceCountMeasured("POKE_BALLS", 3, 3), 2),
            event("os", ArchivedOpponentBattleResourceCountMeasured("SHIELDS", 2, 2), 2),
            event("ps1", ArchivedPlayerShieldCountMeasured(1), 4),
            event("ob2", ArchivedOpponentBattleResourceCountMeasured("POKE_BALLS", 2, 3), 5),
            event("os0", ArchivedOpponentBattleResourceCountMeasured("SHIELDS", 0, 2), 6),
            event("noise", ArchivedOpponentBattleResourceCountMeasured("POKE_BALLS", 3, 3), 7),
        )))
        assertEquals(ReplayTeamResources(), model.sceneAt(1).playerResources)
        assertEquals(ReplayTeamResources(3, 1), model.sceneAt(7).playerResources)
        assertEquals(ReplayTeamResources(2, 0), model.sceneAt(7).opponentResources)
        assertEquals(ReplayTeamResources(3, 2), model.sceneAt(3).opponentResources)
        assertEquals(ReplayTeamResources(3, 2), model.sceneAt(3).playerResources)
    }

    @Test fun `old archives leave unrecorded shields unknown instead of empty`() {
        val track = ReplayResourceTrack(listOf(event("balls", ArchivedPlayerPokeBallCountMeasured(2, 3), 1)), "PLAYER")
        assertEquals(ReplayTeamResources(2, null), track.stateAt(2))
    }

    @Test fun `energy stays with each combatant through switches and mirrors`() {
        val model = MatchReplayModel(MatchArchive(matchId = "match", articles = listOf(
            event("p", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Camerupt", 323), 1),
            event("o", ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Camerupt", 323), 1),
            event("p-energy", energy("PLAYER", 40), 2),
            event("o-energy", energy("OPPONENT", 20), 2),
            event("switch", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Turtonator", 776), 4),
            event("t-energy", energy("PLAYER", 20), 5),
            event("return", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Camerupt", 323), 8),
        )))
        assertEquals(40, model.sceneAt(3).playerEnergy?.recordedEnergy)
        assertEquals(0, model.sceneAt(4).playerEnergy?.recordedEnergy)
        assertEquals(20, model.sceneAt(6).playerEnergy?.recordedEnergy)
        assertEquals(40, model.sceneAt(9).playerEnergy?.recordedEnergy)
        assertEquals(20, model.sceneAt(9).opponentEnergy?.recordedEnergy)
    }

    @Test fun `energy caps before spending and faint clears only the fainted pokemon`() {
        val model = MatchReplayModel(MatchArchive(matchId = "match", articles = listOf(
            event("p", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Camerupt", 323), 1),
            event("e1", energy("PLAYER", 80), 2),
            event("e2", energy("PLAYER", 40), 3),
            event("charge", ArchivedChargedMoveEnergySpent("PLAYER", "Camerupt", "Earth Power", 55, "TEST"), 4),
            event("switch", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Turtonator", 776), 5),
            event("t-energy", energy("PLAYER", 20), 6),
            event("late-faint", ArchivedActivePokemonFaintedWitnessed("PLAYER", "Camerupt", 323, "BALLS"), 7),
        )))
        assertEquals(100, model.sceneAt(3).playerEnergy?.recordedEnergy)
        assertEquals(45, model.sceneAt(4).playerEnergy?.recordedEnergy)
        assertEquals(20, model.sceneAt(7).playerEnergy?.recordedEnergy)
        assertFalse(model.sceneAt(7).player!!.isFainted)
        assertEquals(100, model.sceneAt(3).playerEnergy?.recordedEnergy)
    }

    @Test fun `energy with the same use predecessor is not counted twice`() {
        val use = event("use", ArchivedFastMoveUseObserved("use", "PLAYER", "OPPONENT", "p", "Camerupt", listOf("HP_BORDER_PULSE"), "TEST"), 2)
        val model = MatchReplayModel(MatchArchive(matchId = "match", articles = listOf(
            event("p", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Camerupt", 323), 1), use,
            event("energy", energy("PLAYER", 20), 2).copy(predecessorIds = listOf("use")),
            event("duplicate", energy("PLAYER", 20), 2).copy(predecessorIds = listOf("use")),
        )))
        assertEquals(20, model.sceneAt(3).playerEnergy?.recordedEnergy)
    }

    private fun energy(side: String, gain: Int) = ArchivedFastMoveEnergyDerived(side, "Incinerate", gain / 20, 20, gain, "OBSERVED_FAST_MOVE_USE_TIMES_IDENTIFIED_MOVE_REFERENCE_ENERGY")
    private fun event(id: String, payload: ArchivedTestimonyPayload, at: Long) = ArchivedRealityArticle(
        articleId = id, matchId = "match", perceivedAt = at, recordedAt = at,
        sourceId = "TEST", payload = payload, monotonicTimeNanos = at,
        predecessorIds = emptyList(), confidence = 1f, sequenceNumber = null, evidenceReferences = emptyList(),
    )
}
