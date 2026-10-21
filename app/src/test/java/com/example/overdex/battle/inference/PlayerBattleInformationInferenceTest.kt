package com.example.overdex.battle.inference

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.observation.MatchId
import com.example.overdex.battle.reality.*
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PlayerBattleInformationInferenceTest {
    private val mon = Pokemon(323, "Camerupt", listOf(PokemonType.FIRE, PokemonType.GROUND), "Hoenn",
        fastMoves = listOf(Move("Incinerate", PokemonType.FIRE, 20, 20, true, turns = 5), Move("Ember", PokemonType.FIRE, 6, 6, true, turns = 2)),
        chargedMoves = listOf(Move("Earth Power", PokemonType.GROUND, 90, 55, false), Move("Earthquake", PokemonType.GROUND, 110, 65, false)))
    private val knowledge = object : PokemonKnowledge {
        override suspend fun getPokemonByName(name: String) = mon.takeIf { it.name == name }
        override suspend fun getPokemonById(id: Int) = mon.takeIf { it.id == id }
        override suspend fun getAllSpeciesNames() = setOf(mon.name)
    }
    private val saved = PlayerTeamSlotConfigured(1, "Camerupt", 323, "Incinerate", listOf("Earthquake"))
    private fun event(id: String, seconds: Long, payload: TestimonyPayload) = RealityArticle(
        ArticleId(id), seconds * 1000, seconds * 1000, SourceId("TEST"), payload,
        matchId = MatchId("test"), monotonicTimeNanos = seconds * 1_000_000_000)
    private fun identity(name: String = "Camerupt") = ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, name)

    @Test fun `configuration is not evidence of itself and cached OCR does not confirm a correction`() = runBlocking {
        val check = PlayerBattleInformationInference(knowledge)
        assertTrue(check.accept(event("saved", 0, saved)).isEmpty())
        assertEquals("CONFIRMED", check.accept(event("species", 1, identity())).single().payload.status)
        assertTrue(check.accept(event("label1", 2, PlayerChargeMoveLabelRead(1, "EARTH POWER"))).isEmpty())
        assertTrue(check.accept(event("cache", 3, PlayerChargeMoveLabelRead(1, "EARTH POWER", "UNCHANGED_LETTER_PIXELS"))).isEmpty())
        val result = check.accept(event("label2", 4, PlayerChargeMoveLabelRead(1, "EARTH POWER"))).single()
        assertEquals("DIFFERS", result.payload.status)
        assertEquals("Earth Power", result.payload.observedValue)
        assertTrue(result.predecessorIds.containsAll(listOf(ArticleId("saved"), ArticleId("label1"), ArticleId("label2"))))
        assertEquals(listOf("Earthquake"), saved.chargedMoveNames)
    }

    @Test fun `bare species name supports one saved form without confirming its size`() = runBlocking {
        val gourgeist = mon.copy(id = 711, name = "Gourgeist")
        val references = object : PokemonKnowledge {
            override suspend fun getPokemonByName(name: String) = gourgeist.takeIf { name == it.name }
            override suspend fun getPokemonById(id: Int) = gourgeist.takeIf { id == it.id }
            override suspend fun getAllSpeciesNames() = setOf(gourgeist.name)
        }
        val check = PlayerBattleInformationInference(references)
        check.accept(event("saved-form", 0, saved.copy(speciesName = "Gourgeist (Average)", speciesId = 711)))
        val species = check.accept(event("badge", 1,
            ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Gourgeist", 711))).single().payload
        assertEquals("COMPATIBLE", species.status)
        assertEquals("Gourgeist", species.observedValue)
        val measured = FastMoveIdentified(ActivePokemonSide.PLAYER, "Gourgeist", "Incinerate",
            2_500_000_000, 2_500_000_000, 3, "BORDER_PULSE_CADENCE_AND_REFERENCE_KNOWLEDGE")
        val fast = check.accept(event("measured", 5, measured)).single().payload
        assertEquals("Incinerate", fast.configuredValue)
        assertEquals("CONFIRMED", fast.status)

        // Two owned forms cannot be picked apart from the bare name or national ID.
        check.accept(event("second-form", 6, saved.copy(slot = 2, speciesName = "Gourgeist (Super)", speciesId = 711)))
        val ambiguous = check.accept(event("next-measurement", 7, measured.copy(basis = "AUDIO_REFERENCE_CONFIRMED"))).single().payload
        assertNull(ambiguous.configuredValue)
        assertEquals("OBSERVED", ambiguous.status)
        val differentForm = check.accept(event("team-select", 8,
            PlayerTeamRosterSlotWitnessed(1, "Gourgeist (Super)", 711))).single().payload
        assertEquals("DIFFERS", differentForm.status)
    }

    @Test fun `unvalidated recipient artifacts cannot establish a fast move for an unknown opponent`() = runBlocking {
        val engine = FastMoveCadenceInference(knowledge)
        engine.accept(event("species", 1, ActivePokemonSpeciesWitnessed(ActivePokemonSide.OPPONENT, "Camerupt", 323)))
        engine.accept(event("visual1", 3, FastMoveRecipientVisualCadenceMeasured(ActivePokemonSide.PLAYER, 1_000_000_000)))
        val output = engine.accept(event("visual2", 4, FastMoveRecipientVisualCadenceMeasured(ActivePokemonSide.PLAYER, 1_000_000_000)))
        assertTrue(output.isEmpty())
        assertNull(engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.identifiedMoveName)
        // The hypothesis remains available without becoming an accepted identity.
        assertEquals(listOf("Ember"), engine.candidateSnapshot(ActivePokemonSide.OPPONENT)?.remainingMoveNames)
    }

    @Test fun `late labels stay with the species visible at capture and no team is required`() = runBlocking {
        val check = PlayerBattleInformationInference(knowledge)
        check.accept(event("old", 1, identity()))
        check.accept(event("new", 10, identity("Gourgeist")))
        check.accept(event("late1", 2, PlayerChargeMoveLabelRead(1, "Earth Power")))
        val result = check.accept(event("late2", 4, PlayerChargeMoveLabelRead(1, "Earth Power"))).single().payload
        assertEquals("Camerupt", result.speciesName)
        assertEquals("OBSERVED", result.status)
        assertNull(result.configuredValue)
        assertTrue(check.accept(event("wrong-species", 11, PlayerChargeMoveLabelRead(1, "Earth Power"))).isEmpty())
    }

    @Test fun `team select flags changed species and configuration alone never validates fast move`() = runBlocking {
        val check = PlayerBattleInformationInference(knowledge)
        check.accept(event("saved", 0, saved))
        val result = check.accept(event("team", 1, PlayerTeamRosterSlotWitnessed(1, "Abomasnow", 460))).single().payload
        assertEquals("DIFFERS", result.status)
        val identity = FastMoveIdentified(ActivePokemonSide.PLAYER, "Camerupt", "Incinerate", 2_500_000_000, null, 0, "CONFIGURED_PLAYER_TEAM_AND_ACTIVE_SPECIES")
        assertTrue(check.accept(event("configured-identity", 2, identity)).isEmpty())
        val verified = check.accept(event("measured", 10, identity.copy(moveName = "Ember", moveDurationNanos = 1_000_000_000,
            cadenceSampleCount = 3, basis = "BORDER_PULSE_CADENCE_AND_REFERENCE_KNOWLEDGE"))).single().payload
        assertEquals("DIFFERS", verified.status)
    }

    @Test fun `charged button effectiveness round trips without creating fast move testimony`() = runBlocking {
        val evidence = event("hint", 3, PlayerChargedMoveEffectivenessObserved(1, FastMoveEffectiveness.NOT_VERY_EFFECTIVE, "NOT VERY EFFECTIVE"))
        val archived = RealityArticleArchiveMapper.map(evidence)
        val json = Json { classDiscriminator = "kind" }
        assertEquals(archived, json.decodeFromString<ArchivedRealityArticle>(json.encodeToString(ArchivedRealityArticle.serializer(), archived)))
        val uses = FastMoveUseInference()
        uses.accept(event("go", 0, MatchStarted))
        assertTrue(uses.accept(evidence).isEmpty())
        assertTrue(uses.flush().isEmpty())
        assertTrue(PlayerBattleInformationInference(knowledge).accept(evidence).isEmpty())
    }

    @Test fun `same duration cannot replace the configured player move by an energy preference`() = runBlocking {
        val first = mon.copy(fastMoves = listOf(
            Move("Ember", PokemonType.FIRE, 6, 6, true, turns = 2),
            Move("Test higher energy", PokemonType.GROUND, 1, 12, true, turns = 2)))
        val references = object : PokemonKnowledge {
            override suspend fun getPokemonByName(name: String) = first
            override suspend fun getPokemonById(id: Int) = first
            override suspend fun getAllSpeciesNames() = setOf(first.name)
        }
        val engine = FastMoveCadenceInference(references)
        engine.accept(event("saved", 0, saved.copy(fastMoveName = "Ember")))
        engine.accept(event("species", 1, identity()))
        engine.accept(event("a", 3, ActiveHpBarBorderCadenceMeasured(ActivePokemonSide.OPPONENT, 1_000_000_000, 20f, 2)))
        val output = engine.accept(event("b", 4, ActiveHpBarBorderCadenceMeasured(ActivePokemonSide.OPPONENT, 1_000_000_000, 20f, 3)))
        assertTrue(output.mapNotNull { it.payload as? FastMoveIdentified }.all { it.moveName == "Ember" })
        assertEquals("Ember", engine.candidateSnapshot(ActivePokemonSide.PLAYER)?.identifiedMoveName)
    }

    @Test fun `measured player cadence can refute configured move while retaining saved reference`() = runBlocking {
        val engine = FastMoveCadenceInference(knowledge)
        engine.accept(event("saved", 0, saved))
        engine.accept(event("species", 1, identity()))
        engine.accept(event("a", 3, ActiveHpBarBorderCadenceMeasured(ActivePokemonSide.OPPONENT, 1_000_000_000, 20f, 2)))
        val output = engine.accept(event("b", 4, ActiveHpBarBorderCadenceMeasured(ActivePokemonSide.OPPONENT, 1_000_000_000, 20f, 3)))
        val move = output.mapNotNull { it.payload as? FastMoveIdentified }.single()
        assertEquals("Ember", move.moveName)
        assertFalse(move.basis.contains("CONFIGURED"))
        assertEquals("Incinerate", saved.fastMoveName)
    }
}
