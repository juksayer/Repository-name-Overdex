package com.example.overdex.model

/**
 * Maps a [RecognizedPokemon] (best understanding from vision) to an [OwnedPokemon] (persistence model).
 *
 * @param speciesId The Pokédex ID of the recognized species.
 */
fun RecognizedPokemon.toOwnedPokemon(speciesId: Int): OwnedPokemon {
    return OwnedPokemon(
        speciesId = speciesId,
        cp = cp,
        isShadow = shadowBonus != null,
        displayName = null,
        isPurified = false,
        isShiny = false,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )
}
