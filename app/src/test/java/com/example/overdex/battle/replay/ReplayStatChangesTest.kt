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
        val unattributed = model(listOf(sound())).sceneAt(10_000_000_000).statChanges.single()
        assertNull(unattributed.side)
        assertNull(unattributed.moveName)
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

    @Test fun `Sierra cue sequence keeps missing Poison Fang attribution unknown and Crunch on its recipient`() {
        val first = sound().copy(articleId = "first-cue", monotonicTimeNanos = 39_853_217_156L,
            payload = ArchivedStatChangeSoundMeasured("DEFENSE_DOWN", "sha", .9975f, .8133f, 917_125_000L))
        val crunchSound = first.copy(articleId = "crunch-cue", monotonicTimeNanos = 50_732_561_849L)
        val third = first.copy(articleId = "third-cue", monotonicTimeNanos = 62_621_040_850L)
        val model = MatchReplayModel(MatchArchive(matchId = "test", articles = listOf(
            event("p", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Golbat", 42), 1),
            event("o", ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Pancham", 674), 1),
            first,
            crunchSound,
            event("crunch", ArchivedChargedMoveEnergySpent("OPPONENT", "Pancham", "Crunch", 45, "ANNOUNCEMENT"), 51)
                .copy(monotonicTimeNanos = 51_050_073_234L),
            resolution("PLAYER").copy(payload = ArchivedStatChangeObserved("PLAYER", "DEFENSE", -1, 1,
                "Crunch", "crunch-cue", "AUDIO_CUE_AND_NAMED_CHARGED_MOVE"),
                monotonicTimeNanos = crunchSound.monotonicTimeNanos, evidenceReferences = listOf("crunch-cue", "crunch")),
            third,
        )))
        assertNull(model.sceneAt(first.monotonicTimeNanos!!).statChanges.single().side)
        val crunch = model.sceneAt(crunchSound.monotonicTimeNanos!!).statChanges.single()
        assertEquals("PLAYER", crunch.side)
        assertEquals("Crunch", crunch.moveName)
        assertEquals("DEF ↓", crunch.label)
        assertEquals("Golbat", model.sceneAt(crunchSound.monotonicTimeNanos!!).player?.speciesName)
        val missingAnnouncement = model.sceneAt(third.monotonicTimeNanos!!).statChanges.single()
        assertNull(missingAnnouncement.side)
        assertNull(missingAnnouncement.moveName)
    }

    @Test fun `player Poison Fang shows its recorded cause on the opponent and in event details`() {
        val move = event("poison-fang", ArchivedChargedMoveEnergySpent("PLAYER", "Golbat", "Poison Fang", 40,
            "ANNOUNCEMENT"), 9)
        val heard = sound().copy(payload = ArchivedStatChangeSoundMeasured("DEFENSE_DOWN", "sha", .99f, .8f,
            917_125_000L))
        val attributed = resolution("OPPONENT").copy(payload = ArchivedStatChangeObserved("OPPONENT", "DEFENSE",
            -1, 1, "Poison Fang", "sound", "AUDIO_CUE_AND_NAMED_CHARGED_MOVE"),
            evidenceReferences = listOf("sound", "poison-fang"))
        val replay = MatchReplayModel(MatchArchive(matchId = "test", articles = listOf(
            event("p", ArchivedActivePokemonSpeciesWitnessed("PLAYER", "Golbat", 42), 1),
            event("o", ArchivedActivePokemonSpeciesWitnessed("OPPONENT", "Pancham", 674), 1),
            move, heard, attributed,
        )))
        val scene = replay.sceneAt(10_000_000_000)
        val effect = scene.statChanges.single()
        assertEquals("OPPONENT", effect.side)
        assertEquals("Pancham", scene.opponent?.speciesName)
        assertEquals("Poison Fang", effect.moveName)
        assertEquals("DEF ↓", effect.label)
        assertTrue(scene.latestEvidenceLabel.contains("Poison Fang"))
        assertTrue(scene.latestEvidenceLabel.contains("STAT CHANGE"))
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
