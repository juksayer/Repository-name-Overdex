package com.example.overdex.model

/**
 * Maps a [RecognizedPokemon] belief to an [OwnedPokemon] persistence record.
 * 
 * This transformation occurs once the trainer confirms the system's best 
 * understanding of a specimen.
 *
 * @param speciesId The canonical Pokédex ID of the recognized species.
 * @param speciesName The canonical name supplied by species resolution, not unverified OCR text.
 */
fun RecognizedPokemon.toOwnedPokemon(speciesId: Int, speciesName: String? = null): OwnedPokemon {
    return OwnedPokemon(
        speciesId = speciesId,
        speciesName = speciesName?.trim()?.takeIf { it.isNotEmpty() },
        cp = cp,
        isShadow = shadowBonus != null,
        displayName = null,
        isPurified = false,
        isShiny = false,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )
}
