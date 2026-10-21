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

class NicknamedChargedMoveTest {
    private val lunge = Move("Lunge", PokemonType.BUG, 60, 45, false)
    private val galvantula = Pokemon(596, "Galvantula", listOf(PokemonType.BUG), "Unova",
        fastMoves = emptyList(), chargedMoves = listOf(lunge))
    private val sealeo = galvantula.copy(id = 364, name = "Sealeo", chargedMoves = listOf(Move("Surf", PokemonType.WATER, 75, 45, false)))
    private fun resolver(vararg species: Pokemon) = ChargedMoveAnnouncementInference(object : PokemonKnowledge {
        override suspend fun getPokemonByName(name: String) = species.firstOrNull { it.name == name }
        override suspend fun getPokemonById(id: Int) = species.firstOrNull { it.id == id }
        override suspend fun getAllSpeciesNames() = species.map { it.name }.toSet()
    })
    private suspend fun ChargedMoveAnnouncementInference.accept(article: RealityArticle) = accept(article) { null }

    @Test fun `recorded gbl used Lunge resolves through both active movepools with citations`() = runBlocking {
        val inference = resolver(galvantula, sealeo)
        inference.accept(species("p", "Galvantula", ActivePokemonSide.PLAYER))
        inference.accept(species("o", "Sealeo", ActivePokemonSide.OPPONENT))
        val raw = announcement("gbl used Lunge!", 52_665_000_000)
        val resolved = requireNotNull(inference.accept(raw))
        assertEquals("Galvantula", resolved.payload.speciesName)
        assertEquals(ActivePokemonSide.PLAYER, resolved.payload.side)
        assertEquals("Lunge", resolved.payload.moveName)
        assertEquals(setOf("p", "o", raw.id.value), resolved.predecessorIds.map { it.value }.toSet())
        assertEquals(raw.monotonicTimeNanos, resolved.observedArticle.monotonicTimeNanos)
        assertNull(inference.accept(announcement("gbl used Lunge!", 53_704_000_000)))
    }
    @Test fun `missing name waits for both active species then resolves once without another announcement`() = runBlocking {
        val inference = resolver(galvantula, sealeo)
        inference.accept(species("p", "Galvantula", ActivePokemonSide.PLAYER))
        val raw = announcement("gbl used Lunge!")
        assertNull(inference.accept(raw))
        val resolved = inference.accept(species("o", "Sealeo", ActivePokemonSide.OPPONENT))
        assertEquals("Galvantula", resolved?.payload?.speciesName)
        assertEquals(raw, resolved?.observedArticle)
        assertEquals(setOf("p", "o", raw.id.value), resolved?.predecessorIds?.map { it.value }?.toSet())
        assertNull(inference.accept(announcement("used Lunge!")))
    }
    @Test fun `nickname does not choose between mirror combatants or two legal users`() = runBlocking {
        for (opponent in listOf(galvantula, sealeo.copy(chargedMoves = listOf(lunge)))) {
            val inference = resolver(galvantula, opponent)
            inference.accept(species("p", "Galvantula", ActivePokemonSide.PLAYER))
            inference.accept(species("o", opponent.name, ActivePokemonSide.OPPONENT))
            assertNull(inference.accept(announcement("gbl used Lunge!")))
        }
    }
    @Test fun `a recognized conflicting species name is not overruled by the legal move fallback`() = runBlocking {
        val inference = resolver(galvantula, sealeo)
        inference.accept(species("p", "Galvantula", ActivePokemonSide.PLAYER))
        inference.accept(species("o", "Sealeo", ActivePokemonSide.OPPONENT))
        assertNull(inference.accept(announcement("Sealeo used Lunge!")))
    }
    @Test fun `late announcement uses active identities at its captured time`() = runBlocking {
        val other = sealeo.copy(name = "Other", chargedMoves = emptyList())
        val inference = resolver(galvantula, sealeo, other)
        inference.accept(species("p", "Galvantula", ActivePokemonSide.PLAYER))
        inference.accept(species("o", "Sealeo", ActivePokemonSide.OPPONENT))
        inference.accept(species("later", "Other", ActivePokemonSide.PLAYER).copy(monotonicTimeNanos = 70_000_000_000))
        assertEquals("Galvantula", inference.accept(announcement("gbl used Lunge!", 52_665_000_000))?.payload?.speciesName)
    }
    private fun announcement(text: String, at: Long = 10_000_000_000) = RealityArticle(
        ArticleId("raw-$at"), 1000, 2000, SourceId("ANNOUNCEMENT_WITNESS"), RawTestimony(text), monotonicTimeNanos = at)
    private fun species(id: String, name: String, side: ActivePokemonSide) = RealityArticle(
        ArticleId(id), 1, 1, SourceId("SPECIES"), ActivePokemonSpeciesWitnessed(side, name, null), monotonicTimeNanos = 1)
}
