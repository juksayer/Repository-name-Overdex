package com.example.overdex.battle.observation

import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.InMemoryRealityTimeline
import com.example.overdex.data.*
import com.example.overdex.data.local.PokedexDatabase
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Exercises the real Custody -> Match -> identity -> move pipelines with bundled reference data. */
class PlayerActiveIdentityDeviceTest {
    @Test fun scannedLeadAndLabelResolvedSwitchKeepTheirOwnMoves() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val knowledge = PokemonRepository(PokedexDatabase.getDatabase(context).pokemonDao(),
            LocalSpriteProvider(context.assets), GameMasterLoader(context))
        val timeline = InMemoryRealityTimeline()
        val custody = InMemoryTestimonyCustody()
        val match = Match("PLAYER_FORM_HANDOFF", custody = custody, realityTimeline = timeline, pokemonKnowledge = knowledge)
        val base = System.nanoTime()
        fun send(p: TestimonyPayload, seconds: Long, source: String = "FORM_HANDOFF_TEST") =
            custody.submitTestimony(SourceId(source), p, System.currentTimeMillis(), .99f,
                emptyList(), base + seconds * 1_000_000_000L)
        suspend fun await(check: () -> Boolean) = withTimeout(15_000) { while (!check()) delay(20) }
        fun bindings() = timeline.getArticles().mapNotNull { it.payload as? PlayerActiveIdentityResolved }
        fun moves() = timeline.getArticles().mapNotNull { it.payload as? FastMoveIdentified }
        try {
            send(PlayerTeamSlotConfigured(1, "Raichu (Alolan)", 26, "Spark", listOf("Trailblaze", "Wild Charge")), 0)
            send(PlayerTeamSlotConfigured(2, "Raichu", 26, "Volt Switch", listOf("Brick Break", "Thunder")), 0)
            send(PlayerTeamSlotConfigured(3, "Obstagoon", 862, "Counter", listOf("Night Slash")), 0)
            send(PlayerTeamRosterSlotWitnessed(1, "Raichu (Alolan)", 26, "fALOLA", .2f), 1)
            send(PlayerTeamRosterSlotWitnessed(2, "Raichu", 26, "base", .2f), 1)
            send(ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Raichu", 26), 3, "PLAYER_SPECIES_OVERLAY_PIPELINE")
            await { moves().any { it.side == ActivePokemonSide.PLAYER && it.speciesName == "Raichu (Alolan)" && it.moveName == "Spark" } }
            assertEquals(1, bindings().last().teamSlot)
            send(ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Raichu", 26), 4, "PLAYER_SPECIES_OVERLAY_PIPELINE")
            send(RawTestimony("Raichu used Trailblaze!"), 7, "ANNOUNCEMENT_WITNESS")
            await { timeline.getArticles().any { (it.payload as? ChargedMoveEnergySpent)?.let { p ->
                p.speciesName == "Raichu (Alolan)" && p.moveName == "Trailblaze" } == true } }
            assertEquals(1, bindings().size)
            send(ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Obstagoon", 862), 10, "PLAYER_SPECIES_OVERLAY_PIPELINE")
            send(ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Raichu", 26), 20, "ENTRY_ANNOUNCEMENT_SPECIES_WITNESS")
            await { bindings().lastOrNull()?.appearanceStartedAtNanos == base + 20_000_000_000L }
            assertNull(bindings().last().teamSlot)
            send(PlayerChargeMoveLabelRead(1, "BRICK BREAK"), 21)
            send(PlayerChargeMoveLabelRead(1, "BRICK BREAK"), 22)
            await { moves().lastOrNull { it.side == ActivePokemonSide.PLAYER }?.let { it.speciesName == "Raichu" && it.moveName == "Volt Switch" } == true }
            assertEquals(2, bindings().last().teamSlot)
            // A delayed old badge must remain testimony without restoring Spark.
            send(ActivePokemonSpeciesWitnessed(ActivePokemonSide.PLAYER, "Obstagoon", 862), 12, "PLAYER_SPECIES_OVERLAY_PIPELINE")
            send(RawTestimony("Raichu used Brick Break!"), 25, "ANNOUNCEMENT_WITNESS")
            await { timeline.getArticles().any { (it.payload as? ChargedMoveEnergySpent)?.moveName == "Brick Break" } }
            assertEquals("Volt Switch", moves().last { it.side == ActivePokemonSide.PLAYER }.moveName)
            assertTrue(timeline.getArticles().filter { it.sourceId.id == "PLAYER_SPECIES_OVERLAY_PIPELINE" }
                .mapNotNull { it.payload as? ActivePokemonSpeciesWitnessed }.any { it.speciesName == "Raichu" })
        } finally {
            match.release()
            DroidballOverlayPresentation.reset()
        }
    }
}
