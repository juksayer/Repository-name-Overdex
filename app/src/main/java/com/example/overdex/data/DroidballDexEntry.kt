package com.example.overdex.data

import com.example.overdex.data.local.PokemonDao
import com.example.overdex.data.local.PokemonEntity

/** Overdex's own mascot, browsable in the Pokédex but not a GO recognition candidate. */
object DroidballDexEntry {
    const val ID = 10001
    const val NAME = "Droidball"
    // Preserve the original field-note source and its historical numbering.
    const val FIELD_NOTE_SOURCE_ID = 1026
    const val SPRITE_URI = "android.resource://com.example.overdex/drawable/droidball_dex"

    val entity = PokemonEntity(
        id = ID,
        name = NAME,
        typesJson = "[\"PSYCHIC\"]",
        region = "Overdex",
        genus = "Observation Pokémon",
        fastMovesJson = "[]",
        chargedMovesJson = "[]",
        spriteUrl = SPRITE_URI,
        cryUrl = "",
        description = "No one knows where Droidball came from. It just showed up one day and started winning battles through observation alone.",
    )

    /** Also runs on existing installations; no catalog reset or schema migration. */
    suspend fun install(dao: PokemonDao) {
        if (dao.getPokemonById(ID) != entity) dao.insertAll(listOf(entity))
    }
}
