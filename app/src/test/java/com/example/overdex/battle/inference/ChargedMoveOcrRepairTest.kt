package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.*
import com.example.overdex.data.PokemonKnowledge
import com.example.overdex.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ChargedMoveOcrRepairTest {
    private val reference by lazy {
        Json { ignoreUnknownKeys = true }.decodeFromString<GameMaster>(File("src/main/assets/gamemaster.json").readText())
    }
    private val knowledge = object : PokemonKnowledge {
        override suspend fun getPokemonByName(name: String): Pokemon? {
            val species = reference.pokemon.firstOrNull { it.speciesName.equals(name, true) } ?: return null
            return Pokemon(species.dex, species.speciesName, emptyList(), "", fastMoves = emptyList(),
                chargedMoves = species.chargedMoves.mapNotNull { id -> reference.moves.firstOrNull { it.moveId == id }?.let {
                    Move(it.name, PokemonType.valueOf(it.type.uppercase()), it.power, kotlin.math.abs(it.energy), false)
                } })
        }
        override suspend fun getPokemonById(id: Int) = reference.pokemon.firstOrNull { it.dex == id }?.let { getPokemonByName(it.speciesName) }
        override suspend fun getAllSpeciesNames() = reference.pokemon.flatMap { listOf(it.speciesName, it.speciesName.substringBefore(" (")) }.toSet()
    }

    @Test fun `recorded lowercase lce Punch resolves to Sneasels legal Ice Punch with original testimony`() = runBlocking {
        val inference = activePair("Galvantula", "Sneasel")
        val raw = announcement("Sneasel used lce Punch!")
        val result = requireNotNull(inference.accept(raw) { null })
        assertEquals("Ice Punch", result.payload.moveName)
        assertEquals(ActivePokemonSide.OPPONENT, result.payload.side)
        assertEquals("Sneasel", result.payload.speciesName)
        assertEquals(raw, result.observedArticle)
        assertEquals(listOf(raw.id, ArticleId("o")), result.predecessorIds)
        assertTrue(result.payload.basis.endsWith("OCR_LEGAL_GLYPH_REPAIR"))
        assertNull(inference.accept(announcement("Sneasel used Ice Punch!").copy(monotonicTimeNanos = 11_000_000_000)) { null })
    }

    @Test fun `recorded missing spaces and one letter error resolve only to unique active legal user`() = runBlocking {
        val inference = activePair("Galvantula", "Combusken")
        val result = requireNotNull(inference.accept(announcement("salusedDischargo")) { null })
        assertEquals("Galvantula", result.payload.speciesName)
        assertEquals("Discharge", result.payload.moveName)
        assertEquals(ActivePokemonSide.PLAYER, result.payload.side)
        assertEquals(3, result.predecessorIds.size)
    }

    @Test fun `OCR wrapping and a joined used keyword preserve complete legal move names`() = runBlocking {
        for ((player, opponent, text, move, side) in listOf(
            listOf("Abomasnow", "Sealeo", "Abomasnow usedEnergy Ball!", "Energy Ball", "PLAYER"),
            listOf("Hydreigon", "Sealeo", "Hydreigon used Brutal\nSwing!", "Brutal Swing", "PLAYER"),
            listOf("Abomasnow", "Combusken", "Combusken used Rock\nSlide!", "Rock Slide", "OPPONENT"),
            listOf("Abomasnow", "Flareon", "Flareon\nusedOverheat!", "Overheat", "OPPONENT"),
        )) {
            val raw = announcement(text)
            val result = requireNotNull(activePair(player, opponent).accept(raw) { null })
            assertEquals(move, result.payload.moveName)
            assertEquals(side, result.payload.side.name)
            assertEquals(raw, result.observedArticle)
            assertTrue(result.predecessorIds.contains(raw.id))
        }
    }

    @Test fun `format repair still rejects clipped names illegal moves and ambiguous mirrors`() = runBlocking {
        assertNull(activePair("Hydreigon", "Sealeo").accept(announcement("Hydreigon used Brutal\nSwi!")) { null })
        assertNull(activePair("Abomasnow", "Sealeo").accept(announcement("Sealeo usedEnergy Ball!")) { null })
        assertNull(activePair("Abomasnow", "Abomasnow").accept(announcement("Abomasnow usedEnergy Ball!")) { null })
        assertNull(activePair("Abomasnow", "Sealeo").accept(announcement("Energy Ball")) { null })
    }

    @Test fun `symbol and digit lookalikes resolve against the legal move vocabulary without deleting glyphs`() = runBlocking {
        for (reading in listOf("D1scharg3", "Di\$charge", "D!scharge")) {
            val result = requireNotNull(activePair("Galvantula", "Combusken").accept(announcement("gbl used $reading!")) { null })
            assertEquals("Discharge", result.payload.moveName)
            assertTrue(result.payload.basis.endsWith("OCR_LEGAL_GLYPH_REPAIR"))
        }
        assertNull(activePair("Galvantula", "Combusken").accept(announcement("gbl used D###charge!")) { null })
    }

    @Test fun `clipped or heavily damaged recorded move names remain unresolved`() = runBlocking {
        for (reading in listOf("Ear", "Ea.", "Earn Power", "Eamrowe", "Earth", "Earth Powe")) {
            assertNull(reading, activePair("Camerupt", "Vaporeon").accept(announcement("Camerupt used $reading")) { null })
        }
    }

    @Test fun `visible Weather Ball resolves through Abomasnows legal Ice variant and debits only once`() = runBlocking {
        val inference = activePair("Abomasnow", "Sneasel")
        val raw = announcement("Abomasnow used Weather Ball!")
        val result = requireNotNull(inference.accept(raw) { null })
        assertEquals("Weather Ball (Ice)", result.payload.moveName)
        assertEquals(35, result.payload.energyCost)
        assertEquals(ActivePokemonSide.PLAYER, result.payload.side)
        assertEquals(raw, result.observedArticle)
        assertTrue(result.payload.basis.endsWith("VISIBLE_MOVE_NAME_ALIAS"))
        assertNull(inference.accept(announcement("Abomasnow used VWeather Ball!")
            .copy(monotonicTimeNanos = 11_000_000_000)) { null })
        assertNull(activePair("Abomasnow", "Sneasel").accept(announcement("Abomasnow used Weather!")) { null })
        assertNull(activePair("Abomasnow", "Abomasnow").accept(raw) { null })
    }

    @Test fun `bare Weather Ball never chooses between multiple legal types`() = runBlocking {
        val pokemon = Pokemon(1, "Example", emptyList(), "", fastMoves = emptyList(), chargedMoves = listOf(
            Move("Weather Ball (Ice)", PokemonType.ICE, 60, 35, false),
            Move("Weather Ball (Fire)", PokemonType.FIRE, 60, 35, false)))
        val inference = ChargedMoveAnnouncementInference(object : PokemonKnowledge {
            override suspend fun getPokemonByName(name: String) = pokemon
            override suspend fun getPokemonById(id: Int) = pokemon
            override suspend fun getAllSpeciesNames() = setOf(pokemon.name)
        })
        inference.accept(species("p", "Example", ActivePokemonSide.PLAYER)) { null }
        assertNull(inference.accept(announcement("Example used Weather Ball!")) { null })
    }

    @Test fun `repair never overrides conflicting named species or mirror ambiguity`() = runBlocking {
        assertNull(activePair("Galvantula", "Combusken").accept(announcement("Combusken used Dischargo!")) { null })
        assertNull(activePair("Galvantula", "Galvantula").accept(announcement("gbl used Dischargo!")) { null })
        val absentOpponent = ChargedMoveAnnouncementInference(knowledge)
        absentOpponent.accept(species("p", "Galvantula", ActivePokemonSide.PLAYER)) { null }
        assertNull(absentOpponent.accept(announcement("salusedDischargo")) { null })
    }

    @Test fun `equally close legal move candidates remain ambiguous`() = runBlocking {
        val pokemon = Pokemon(1, "Example", emptyList(), "", fastMoves = emptyList(), chargedMoves =
            listOf("Ice Punch", "Icy Punch").map { Move(it, PokemonType.ICE, 1, 1, false) })
        val inference = ChargedMoveAnnouncementInference(object : PokemonKnowledge {
            override suspend fun getPokemonByName(name: String) = pokemon
            override suspend fun getPokemonById(id: Int) = pokemon
            override suspend fun getAllSpeciesNames() = setOf(pokemon.name)
        })
        inference.accept(species("p", "Example", ActivePokemonSide.PLAYER)) { null }
        assertNull(inference.accept(announcement("Example used Ica Punch!")) { null })
    }

    private suspend fun activePair(player: String, opponent: String) = ChargedMoveAnnouncementInference(knowledge).also {
        it.accept(species("p", player, ActivePokemonSide.PLAYER)) { null }
        it.accept(species("o", opponent, ActivePokemonSide.OPPONENT)) { null }
    }
    private fun announcement(text: String) = RealityArticle(ArticleId("raw-$text"), 10_000, 15_000,
        SourceId("ANNOUNCEMENT_WITNESS"), RawTestimony(text), monotonicTimeNanos = 10_000_000_000)
    private fun species(id: String, name: String, side: ActivePokemonSide) = RealityArticle(
        ArticleId(id), 0, 0, SourceId("SPECIES"), ActivePokemonSpeciesWitnessed(side, name), monotonicTimeNanos = 1)
}
