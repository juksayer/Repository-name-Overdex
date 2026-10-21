package com.example.overdex.battle.team

import com.example.overdex.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class OwnedBattleTeamTest {
    private val reference = Pokemon(25, "Pikachu", listOf(PokemonType.ELECTRIC), "Kanto",
        fastMoves = listOf(Move("Thunder Shock", PokemonType.ELECTRIC, 3, 9, true, 2)),
        chargedMoves = listOf(Move("Discharge", PokemonType.ELECTRIC, 65, 45, false)))
    private val cards = (1..3).map { OwnedPokemon(id = "card-$it", speciesId = 25, fastMove = "Thunder Shock",
        chargedMove1 = "Discharge", createdAt = it.toLong(), teams = listOf("Testing")) }

    @Test fun `card memberships resolve one named team with specimen identities`() {
        val choice = OwnedBattleTeamChoice.fromCards(cards.reversed()).single()
        val team = requireNotNull(choice.resolve(mapOf(25 to reference)))
        assertEquals(listOf("card-1", "card-2", "card-3"), team.members.map { it.ownedPokemonId })
        assertEquals(listOf(1, 2, 3), team.members.map { it.slot })
        assertEquals("Thunder Shock", team.members.first().fastMoveName)
    }

    @Test fun `incomplete or illegal move selections cannot become battle knowledge`() {
        assertNull(OwnedBattleTeamChoice("Missing", cards.take(2)).resolve(mapOf(25 to reference)))
        assertNull(OwnedBattleTeamChoice("Unknown", cards).resolve(emptyMap()))
        assertNull(OwnedBattleTeamChoice("No move", listOf(cards[0].copy(fastMove = null)) + cards.drop(1)).resolve(mapOf(25 to reference)))
        assertNull(OwnedBattleTeamChoice("Wrong move", listOf(cards[0].copy(chargedMove2 = "Surf")) + cards.drop(1)).resolve(mapOf(25 to reference)))
    }

    @Test fun `multiple memberships share the original cards and normalize name case`() {
        val shared = cards.mapIndexed { index, card -> card.copy(teams = listOf(if (index == 0) "TESTING" else "Testing", "Other")) }
        val choices = OwnedBattleTeamChoice.fromCards(shared)
        assertEquals(2, choices.size)
        choices.forEach { assertSame(shared[0], it.members[0]) }
    }

    @Test fun `older active teams decode without requiring card ids`() {
        val encoded = """{"members":[{"slot":1,"speciesId":25,"speciesName":"Pikachu","fastMoveName":"Thunder Shock","chargedMoveNames":["Discharge"]}]}"""
        val decoded = Json.decodeFromString<CurrentBattleTeam>(encoded)
        assertNull(decoded.members.single().ownedPokemonId)
        val named = SavedBattleTeam("one", "Great", decoded)
        assertEquals(named, Json.decodeFromString<SavedBattleTeam>(Json.encodeToString(named)))
    }
}
