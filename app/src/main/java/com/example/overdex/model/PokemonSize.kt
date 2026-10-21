package com.example.overdex.model

import kotlinx.serialization.Serializable

/** Pokémon GO's specimen size label, independent of Candy XL or battle level. */
@Serializable
enum class PokemonSize(val label: String) {
    UNKNOWN("Not recorded"), XXS("XXS"), XS("XS"), AVERAGE("Average"), XL("XL"), XXL("XXL")
}
