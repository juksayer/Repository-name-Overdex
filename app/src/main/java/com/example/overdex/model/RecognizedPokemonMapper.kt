package com.example.overdex.model

/**
 * Maps a [RecognizedPokemon] belief to an [OwnedPokemon] persistence record.
 * 
 * This transformation occurs once the trainer confirms the system's best 
 * understanding of a specimen.
 *
 * @param speciesId The canonical Pokédex ID of the recognized species.
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
