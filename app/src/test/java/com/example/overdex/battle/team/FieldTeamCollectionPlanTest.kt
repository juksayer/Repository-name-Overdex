package com.example.overdex.battle.team

import com.example.overdex.model.*
import org.junit.Assert.*
import org.junit.Test

class FieldTeamCollectionPlanTest {
    private val reference = Pokemon(26, "Raichu (Alolan)", listOf(PokemonType.ELECTRIC, PokemonType.PSYCHIC), "Alola",
        fastMoves = listOf(Move("Spark", PokemonType.ELECTRIC, 5, 8, true)),
        chargedMoves = listOf(Move("Psychic", PokemonType.PSYCHIC, 75, 55, false)))

    @Test fun `three imports keep distinct stable cards form flags and selected moves`() {
        val draft = imported()
        val first = FieldTeamCollectionPlan.from(draft, emptyList(), 10L)
        val retry = FieldTeamCollectionPlan.from(draft, first.newCards, 20L)
        assertEquals(3, first.newCards.size)
        assertTrue(first.updatedCards.isEmpty())
        assertEquals(listOf("new-0", "new-1", "new-2"), first.team.members.map { it.ownedPokemonId })
        assertEquals(first.team, retry.team)
        assertEquals(first.newCards.map { it.id }, retry.newCards.map { it.id })
        assertTrue(first.newCards.all { it.speciesName == reference.name && it.fastMove == "Spark" && it.chargedMove1 == "Psychic" })
        assertTrue(first.newCards.first().isShadow)
        assertTrue(first.newCards.first().isShiny)
        assertNull(first.newCards.first().cp)
        assertEquals(listOf("Practice"), first.newCards.first().teams)
    }

    @Test fun `existing confirmed individual retains metadata and gets shared team membership`() {
        val card = OwnedPokemon("known", 26, "My Raichu", 1497, isShadow = true, isFavorite = true,
            fastMove = "Old move", createdAt = 1L, teams = listOf("Great League"), size = PokemonSize.XL)
        val draft = imported().copy(slots = imported().slots.toMutableList().apply {
            this[0] = this[0].copy(newCard = null, ownedPokemonId = card.id)
        })
        val plan = FieldTeamCollectionPlan.from(draft, listOf(card), 42L)
        val updated = plan.updatedCards.single()
        assertEquals(card.id, updated.id)
        assertEquals(card.createdAt, updated.createdAt)
        assertEquals(card.cp, updated.cp)
        assertEquals(card.displayName, updated.displayName)
        assertEquals(card.size, updated.size)
        assertTrue(updated.isShadow && updated.isFavorite)
        assertEquals(listOf("Great League", "Practice"), updated.teams)
        assertEquals(reference.name, updated.speciesName)
        assertEquals("Spark", updated.fastMove)
        assertEquals(42L, updated.updatedAt)
        assertNull(card.speciesName) // Review never mutates the loaded object or its history.
    }

    @Test fun `known ordinary form cannot be linked to regional reference with same dex number`() {
        val card = OwnedPokemon("known", 26, speciesName = "Raichu")
        val draft = imported().copy(slots = imported().slots.toMutableList().apply {
            this[0] = this[0].copy(newCard = null, ownedPokemonId = card.id)
        })
        assertThrows(IllegalArgumentException::class.java) { FieldTeamCollectionPlan.from(draft, listOf(card), 0L) }
    }

    @Test fun `unknown missing or duplicate individual selections cannot save`() {
        val source = imported()
        val missing = source.copy(slots = source.slots.toMutableList().apply {
            this[0] = this[0].copy(newCard = null, ownedPokemonId = "gone")
        })
        assertThrows(IllegalArgumentException::class.java) { FieldTeamCollectionPlan.from(missing, emptyList(), 0L) }
        val duplicate = source.copy(slots = source.slots.map { it.copy(newCard = source.slots.first().newCard) })
        assertThrows(IllegalArgumentException::class.java) { FieldTeamCollectionPlan.from(duplicate, emptyList(), 0L) }
        val unnamed = source.copy(teamName = "")
        assertThrows(IllegalArgumentException::class.java) { FieldTeamCollectionPlan.from(unnamed, emptyList(), 0L) }
    }

    private fun imported() = FieldTeamDraft(collectionEnabled = true, teamName = "Practice", slots = List(3) { index ->
        FieldTeamSlot(reference, reference.name, "Spark", listOf("Psychic"),
            newCard = OwnedPokemonDraft(id = "new-$index", speciesId = 26, speciesName = reference.name,
                shadow = index == 0, shiny = index == 0, createdAt = 5L))
    })
}
