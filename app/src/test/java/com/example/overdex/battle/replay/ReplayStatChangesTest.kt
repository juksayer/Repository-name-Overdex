package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import org.junit.Assert.*
import org.junit.Test

class ReplayStatChangesTest {
    @Test fun `late attribution plays at the sound time and is stable when pausing or seeking`() {
        val model = model(listOf(sound(), resolution("OPPONENT").copy(recordedAt = 90_000)))
        assertTrue(model.sceneAt(9_000_000_000).statChanges.isEmpty())
        val action = model.sceneAt(10_500_000_000).statChanges.single()
        assertEquals("OPPONENT", action.side)
        assertEquals("ATK ↓", action.label)
        assertEquals(.25f, action.progress, .0001f)
        assertEquals(action, model.sceneAt(10_500_000_000).statChanges.single())
        assertTrue(model.sceneAt(12_000_000_000).statChanges.isEmpty())
        assertEquals(action, model.sceneAt(10_500_000_000).statChanges.single())
    }
    @Test fun `unattributed sound and a revoked side remain explicitly uncertain`() {
        assertNull(model(listOf(sound())).sceneAt(10_000_000_000).statChanges.single().side)
        val corrections = listOf(sound(), resolution("OPPONENT"), resolution(null).copy(articleId = "revised", recordedAt = 50_000))
        assertNull(model(corrections).sceneAt(10_000_000_000).statChanges.single().side)
    }
    @Test fun `replacement pokemon does not inherit the outgoing stat animation`() {
        val model = model(listOf(sound(), resolution("OPPONENT"),
            event("switch", ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Vaporeon", 134), 11)))
        assertEquals(1, model.sceneAt(10_500_000_000).statChanges.size)
        assertTrue(model.sceneAt(11_500_000_000).statChanges.isEmpty())
    }
    @Test fun `a mirror does not move the indicator onto both combatants`() {
        val actions = model(listOf(sound(), resolution("PLAYER"))).sceneAt(10_000_000_000).statChanges
        assertEquals(listOf("PLAYER"), actions.map { it.side })
    }

    @Test fun `sound after a switch never applies the earlier moves effect to the replacement`() {
        val model = model(listOf(
            event("move", ArchivedChargedMoveEnergySpent("PLAYER", "Camerupt", "Rock Tomb", 60, "ANNOUNCEMENT"), 6),
            event("switch", ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Vaporeon", 134), 9),
            sound(), resolution("OPPONENT").copy(evidenceReferences = listOf("sound", "move")),
        ))
        assertTrue(model.sceneAt(10_000_000_000).statChanges.isEmpty())
    }

    private fun model(extra: List<ArchivedRealityArticle>) = MatchReplayModel(MatchArchive(matchId = "test", articles = listOf(
        event("p", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Camerupt", 323), 1),
        event("o", ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Camerupt", 323), 1),
    ) + extra))
    private fun sound() = event("sound", ArchivedStatChangeSoundMeasured("ATTACK_DOWN", "sha", .97f, .7f, 900_000_000), 10)
    private fun resolution(side: String?) = event("resolve", ArchivedStatChangeObserved(side, "ATTACK", -1, 1, "Rock Tomb", "sound", "AUDIO_CUE_AND_NAMED_CHARGED_MOVE"), 10)
    private fun event(id: String, payload: ArchivedTestimonyPayload, seconds: Long) = ArchivedRealityArticle(
        id, "test", seconds * 1000, seconds * 1000, "TEST", payload, emptyList(), null, null, emptyList(), seconds * 1_000_000_000)
}
