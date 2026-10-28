package com.example.overdex.model

import com.example.overdex.data.local.toDomain
import com.example.overdex.data.local.toEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OwnedPokemonFormPersistenceTest {
    @Test fun canonicalFormRoundTripPreservesAllSpecimenFields() {
        val original = OwnedPokemon(id = "alolan-raichu", speciesId = 26, displayName = "Surfer", cp = 1476,
            isShadow = true, isShiny = true, isFavorite = true, fastMove = "Spark", chargedMove1 = "Psychic",
            chargedMove2 = "Wild Charge", createdAt = 100, updatedAt = 200, teams = listOf("Great", "Training"),
            size = PokemonSize.XL, speciesName = "Raichu (Alolan)")
        assertEquals(original, original.toEntity().toDomain())
    }

    @Test fun oldCardsRemainUnconfirmedInsteadOfInventingAForm() {
        val old = OwnedPokemon(id = "old-raichu", speciesId = 26)
        assertNull(old.toEntity().toDomain().speciesName)
        assertEquals(old, old.toEntity().toDomain())
    }
}
