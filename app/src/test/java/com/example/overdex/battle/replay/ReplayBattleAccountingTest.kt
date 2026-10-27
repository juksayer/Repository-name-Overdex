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
            timed("p", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Camerupt", 323), 0.0),
            timed("o", ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Camerupt", 323), 0.0),
            timed("go", ArchivedMatchStarted, 0.0),
            identified("PLAYER", "Camerupt"), identified("OPPONENT", "Camerupt"), identified("PLAYER", "Turtonator"),
            timed("switch", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Turtonator", 776), 6.0),
            timed("return", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Camerupt", 323), 12.0),
        ) + completed("p1", "PLAYER", "Camerupt", 2.5) + completed("p2", "PLAYER", "Camerupt", 5.0) +
            completed("o1", "OPPONENT", "Camerupt", 2.5) + completed("t1", "PLAYER", "Turtonator", 8.5)))
        assertEquals(40, model.sceneAt(ns(5.5)).playerEnergy?.recordedEnergy)
        assertEquals(0, model.sceneAt(ns(6.0)).playerEnergy?.recordedEnergy)
        assertEquals(20, model.sceneAt(ns(9.0)).playerEnergy?.recordedEnergy)
        assertEquals(40, model.sceneAt(ns(13.0)).playerEnergy?.recordedEnergy)
        assertEquals(20, model.sceneAt(ns(13.0)).opponentEnergy?.recordedEnergy)
    }

    @Test fun `energy caps before spending and faint clears only the fainted pokemon`() {
        val model = MatchReplayModel(MatchArchive(matchId = "match", articles = listOf(
            timed("p", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Camerupt", 323), 0.0),
            timed("go", ArchivedMatchStarted, 0.0), identified("PLAYER", "Camerupt"), identified("PLAYER", "Turtonator"),
            timed("charge", ArchivedChargedMoveEnergySpent("PLAYER", "Camerupt", "Earth Power", 55, "TEST"), 18.0),
            timed("switch", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Turtonator", 776), 20.0),
            timed("late-faint", ArchivedActivePokemonFaintedWitnessed("PLAYER", "Camerupt", 323, "BALLS"), 25.0),
        ) + (1..6).flatMap { completed("c$it", "PLAYER", "Camerupt", it * 2.5) } +
            completed("t", "PLAYER", "Turtonator", 22.5)))
        assertEquals(100, model.sceneAt(ns(15.0)).playerEnergy?.recordedEnergy)
        assertEquals(45, model.sceneAt(ns(18.0)).playerEnergy?.recordedEnergy)
        assertEquals(20, model.sceneAt(ns(25.0)).playerEnergy?.recordedEnergy)
        assertFalse(model.sceneAt(ns(25.0)).player!!.isFainted)
        assertEquals(100, model.sceneAt(ns(15.0)).playerEnergy?.recordedEnergy)
    }

    @Test fun `energy with the same use predecessor is not counted twice`() {
        val model = MatchReplayModel(MatchArchive(matchId = "match", articles = listOf(
            timed("p", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Camerupt", 323), 0.0),
            timed("go", ArchivedMatchStarted, 0.0), identified("PLAYER", "Camerupt"),
            timed("duplicate", energy("PLAYER", 20), 2.5).copy(predecessorIds = listOf("use")),
        ) + completed("use", "PLAYER", "Camerupt", 2.5)))
        assertEquals(20, model.sceneAt(ns(3.0)).playerEnergy?.recordedEnergy)
    }

    private fun identified(side: String, species: String) = timed("$side-$species-move",
        ArchivedFastMoveIdentified(side, species, "Incinerate", ns(2.5), cadenceSampleCount = 3, basis = "TEST"), 0.0)
    private fun completed(id: String, side: String, species: String, seconds: Double) = listOf(
        timed(id, ArchivedFastMoveUseObserved(id, side, if (side == "PLAYER") "OPPONENT" else "PLAYER",
            "appearance", species, listOf("HP_BORDER_PULSE"), "TEST"), seconds),
        timed("$id-energy", energy(side, 20), seconds).copy(predecessorIds = listOf(id)))
    private fun timed(id: String, payload: ArchivedTestimonyPayload, seconds: Double) = event(id, payload, ns(seconds))
    private fun ns(seconds: Double) = (seconds * 1_000_000_000L).toLong()

    private fun energy(side: String, gain: Int) = ArchivedFastMoveEnergyDerived(side, "Incinerate", gain / 20, 20, gain, "OBSERVED_FAST_MOVE_USE_TIMES_IDENTIFIED_MOVE_REFERENCE_ENERGY")
    private fun event(id: String, payload: ArchivedTestimonyPayload, at: Long) = ArchivedRealityArticle(
        articleId = id, matchId = "match", perceivedAt = at, recordedAt = at,
        sourceId = "TEST", payload = payload, monotonicTimeNanos = at,
        predecessorIds = emptyList(), confidence = 1f, sequenceNumber = null, evidenceReferences = emptyList(),
    )
}
