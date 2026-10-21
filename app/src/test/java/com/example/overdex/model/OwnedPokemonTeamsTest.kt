package com.example.overdex.model

import com.example.overdex.data.local.toDomain
import com.example.overdex.data.local.toEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class OwnedPokemonTeamsTest {
    @Test fun `one specimen may join several named teams without duplicate memberships`() {
        assertEquals(listOf("Great League", "Shadow Crew"), OwnedPokemonTeams.parse(" Great League, Shadow Crew, great league\n\n"))
        assertEquals(emptyList<String>(), OwnedPokemonTeams.parse(" , \n"))
    }

    @Test fun `teams round trip with specimen identity nickname and moves intact`() {
        val specimen = OwnedPokemon(id = "charizard-1", speciesId = 6, displayName = "Ember:FIRE:", fastMove = "Wing Attack", chargedMove1 = "Blast Burn", teams = listOf("Great League", "Coach's team"))
        assertEquals(specimen, specimen.toEntity().toDomain())
        assertEquals(emptyList<String>(), specimen.toEntity().copy(teamsJson = "[]").toDomain().teams)
    }
}
