package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.ArticleId
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.Move
import com.example.overdex.model.Pokemon
import com.example.overdex.model.PokemonType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PlayerTeamConfigurationScopeTest {
    private val powder = Move("Powder Snow", PokemonType.ICE, 5, 8, true, 2)
    private val claw = Move("Shadow Claw", PokemonType.GHOST, 6, 8, true, 2)
    private val sandslash = Pokemon(28, "Sandslash (Alolan)", listOf(PokemonType.ICE, PokemonType.STEEL), "Alola",
        fastMoves = listOf(powder, claw), chargedMoves = emptyList())
    private val knowledge = object : PokemonKnowledge {
        override suspend fun getPokemonByName(name: String) = sandslash.takeIf { name == it.name }
        override suspend fun getPokemonById(id: Int) = sandslash.takeIf { id == it.id }
        override suspend fun getAllSpeciesNames() = setOf(sandslash.name)
    }
    private fun article(id: String, seconds: Long, payload: TestimonyPayload) = RealityArticle(
        ArticleId(id), seconds * 1_000, seconds * 1_000, SourceId("TEST"), payload,
        monotonicTimeNanos = seconds * 1_000_000_000L)
    private fun config(id: String, seconds: Long, move: String = "Shadow Claw", slot: Int = 1) = article(id, seconds,
        PlayerTeamSlotConfigured(slot, sandslash.name, sandslash.id, move, listOf("Ice Punch")))
    private fun confirmation(seconds: Long = 5, confirmed: Boolean = true,
        roster: List<String?> = listOf(sandslash.name, "Dragalge", "Aegislash")) =
        article("confirm-$seconds-$confirmed", seconds, PlayerTeamRosterConfirmation(confirmed, roster))
    private fun species(seconds: Long) = article("species-$seconds", seconds,
        ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, sandslash.name, sandslash.id))

    @Test fun `undated legacy configuration works until a confirmed roster requires newer source time`() = runBlocking {
        val scope = PlayerTeamConfigurationScope()
        val undated = config("legacy", 1).copy(monotonicTimeNanos = null)
        scope.accept(undated)
        assertTrue(scope.isCurrent(undated))
        val engine = FastMoveCadenceInference(knowledge)
        engine.accept(undated)
        assertEquals("Shadow Claw", (engine.accept(species(2)).single().payload as FastMoveIdentified).moveName)
        scope.accept(confirmation())
        scope.accept(undated)
        assertTrue(scope.current().isEmpty())
        engine.accept(confirmation())
        engine.accept(undated)
        assertTrue(engine.accept(species(7)).isEmpty())
        assertNull(engine.candidateSnapshot(ActivePokemonSide.PLAYER)?.identifiedMoveName)
    }

    @Test fun `confirmation invalidates the same species copy and delayed earlier saves cannot restore it`() {
        val scope = PlayerTeamConfigurationScope()
        val original = config("old", 1)
        scope.accept(original)
        assertTrue(scope.accept(confirmation()))
        assertTrue(scope.current().isEmpty())
        val delayed = config("delayed", 4, "Powder Snow")
        scope.accept(delayed)
        scope.accept(config("same-instant", 5))
        assertFalse(scope.isCurrent(delayed))
        assertTrue(scope.current().isEmpty())
        assertEquals(original, scope.at(2_000_000_000L)[1]) // Historical reference remains intact.
        assertEquals("Shadow Claw", (original.payload as PlayerTeamSlotConfigured).fastMoveName)
    }

    @Test fun `only a later explicit save restores its slot while rejection preserves saved configuration`() {
        val scope = PlayerTeamConfigurationScope()
        val original = config("old", 1)
        scope.accept(original)
        assertFalse(scope.accept(confirmation(3, confirmed = false)))
        assertTrue(scope.isCurrent(original))
        scope.accept(confirmation())
        val saved = config("saved", 6, "Powder Snow", slot = 2)
        scope.accept(saved)
        assertEquals(mapOf(2 to saved), scope.current())
        scope.accept(config("delayed-older-save", 4, slot = 2))
        assertTrue(scope.isCurrent(saved))
        // Arrival order cannot erase a genuinely newer configuration.
        scope.accept(confirmation(4))
        assertTrue(scope.isCurrent(saved))
    }

    @Test fun `same species roster confirmation drops cached configured fast move until explicitly saved`() = runBlocking {
        val engine = FastMoveCadenceInference(knowledge)
        engine.accept(config("old", 1))
        assertEquals("Shadow Claw", (engine.accept(species(2)).single().payload as FastMoveIdentified).moveName)
        engine.accept(confirmation())
        assertNull(engine.candidateSnapshot(ActivePokemonSide.PLAYER))
        engine.accept(config("late", 4))
        assertTrue(engine.accept(species(7)).isEmpty())
        assertNull(engine.candidateSnapshot(ActivePokemonSide.PLAYER)?.identifiedMoveName)
        val restored = engine.accept(config("saved", 8, "Powder Snow")).single().payload as FastMoveIdentified
        assertEquals("Powder Snow", restored.moveName)
        assertEquals("CONFIGURED_PLAYER_TEAM_AND_ACTIVE_SPECIES", restored.basis)
    }

    @Test fun `rejected roster does not discard a configured fast move`() = runBlocking {
        val engine = FastMoveCadenceInference(knowledge)
        engine.accept(config("old", 1))
        engine.accept(species(2))
        engine.accept(confirmation(confirmed = false))
        assertEquals("Shadow Claw", engine.candidateSnapshot(ActivePokemonSide.PLAYER)?.identifiedMoveName)
    }

    @Test fun `new confirmed roster replaces old configured identity candidates and stale scan cannot restore them`() {
        val engine = PlayerActiveIdentityInference()
        engine.accept(article("old", 1, PlayerTeamSlotConfigured(1, "Raichu (Alolan)", 26, "Spark", listOf("Wild Charge"))))
        engine.accept(confirmation(roster = listOf("Raichu", "Dragalge", "Aegislash")))
        engine.accept(article("late-scan", 4, PlayerTeamRosterSlotWitnessed(1, "Raichu (Alolan)", 26, "alola", .1f)))
        engine.accept(article("late-config", 4, PlayerTeamSlotConfigured(1, "Raichu (Alolan)", 26, "Spark", listOf("Wild Charge"))))
        val resolved = engine.accept(article("badge", 7, ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Raichu", 26))).single().payload
        assertEquals("Raichu", resolved.speciesName)
        assertEquals(1, resolved.teamSlot)
    }

    @Test fun `old charge loadouts cannot distinguish same named forms after confirmed rescan`() {
        val engine = PlayerActiveIdentityInference()
        engine.accept(article("alolan", 1, PlayerTeamSlotConfigured(1, "Raichu (Alolan)", 26, "Spark", listOf("Wild Charge"))))
        engine.accept(article("ordinary", 1, PlayerTeamSlotConfigured(2, "Raichu", 26, "Volt Switch", listOf("Brick Break"))))
        engine.accept(confirmation(roster = listOf("Aegislash", "Raichu", "Raichu (Alolan)")))
        engine.accept(article("lead", 6, ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Aegislash", 681)))
        engine.accept(article("entry", 10, ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Raichu", 26)))
        engine.accept(article("label-a", 11, PlayerChargeMoveLabelRead(1, "WILD CHARGE")))
        assertTrue(engine.accept(article("label-b", 12, PlayerChargeMoveLabelRead(1, "WILD CHARGE"))).isEmpty())
        assertNull(engine.at(12_000_000_000L)?.payload?.teamSlot)
        engine.accept(article("saved", 13, PlayerTeamSlotConfigured(3, "Raichu (Alolan)", 26, "Spark", listOf("Wild Charge"))))
        engine.accept(article("fresh-a", 20, PlayerChargeMoveLabelRead(1, "WILD CHARGE")))
        assertEquals(3, engine.accept(article("fresh-b", 21, PlayerChargeMoveLabelRead(1, "WILD CHARGE"))).single().payload.teamSlot)
    }

    @Test fun `independent player evidence is observed not compared against an invalidated loadout`() = runBlocking {
        val engine = PlayerBattleInformationInference(knowledge)
        engine.accept(config("old", 1))
        engine.accept(confirmation())
        engine.accept(config("late", 4))
        engine.accept(species(6))
        val measured = FastMoveIdentified(ActivePokemonSide.PLAYER, sandslash.name, "Powder Snow",
            1_000_000_000L, 1_000_000_000L, 3, "BORDER_PULSE_CADENCE_AND_REFERENCE_KNOWLEDGE")
        val observation = engine.accept(article("measured", 7, measured)).single().payload
        assertEquals("OBSERVED", observation.status)
        assertNull(observation.configuredValue)
        engine.accept(config("saved", 8, "Powder Snow"))
        val confirmed = engine.accept(article("verified", 9, measured)).single().payload
        assertEquals("CONFIRMED", confirmed.status)
        assertEquals("Powder Snow", confirmed.configuredValue)
    }

    @Test fun `energy does not retain or revive the old configured identity after roster confirmation`() = runBlocking {
        val engine = FastMoveEnergyInference(knowledge)
        fun identity(id: String, at: Long, move: String) = article(id, at, FastMoveIdentified(
            ActivePokemonSide.PLAYER, sandslash.name, move, 1_000_000_000L, null, 0,
            "CONFIGURED_PLAYER_TEAM_AND_ACTIVE_SPECIES"))
        fun hit(id: String, at: Long) = article(id, at, FastMoveUseObserved(id, ActivePokemonSide.PLAYER,
            ActivePokemonSide.OPPONENT, "appearance", sandslash.name, listOf("HP_BORDER_PULSE")))
        engine.accept(species(1))
        engine.accept(identity("old-identity", 2, "Shadow Claw"))
        assertEquals(8, engine.accept(hit("before", 3)).single().payload.energyPerUse)
        engine.accept(confirmation())
        engine.accept(species(6))
        assertTrue(engine.accept(identity("late-old-identity", 4, "Shadow Claw")).isEmpty())
        assertTrue(engine.accept(hit("after", 7)).isEmpty())
        val recovered = engine.accept(identity("fresh-identity", 8, "Powder Snow"))
        assertEquals("Powder Snow", recovered.single().payload.moveName)
        assertEquals("after", recovered.single().observedArticle.id.value)
    }
}
