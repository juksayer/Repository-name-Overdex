package com.example.overdex.model

/**
 * A saved view over the trainer's owned Pokemon cards.
 *
 * Cards keep one stable [OwnedPokemon.id] and may appear in more than one binder;
 * the binder never creates a second specimen record.
 */
enum class OwnedPokemonBinder(
    val routeKey: String,
    val displayName: String,
) {
    ALL("all", "ALL OWNED"),
    FAVORITES("favorites", "FAVORITES"),
    SHADOW("shadow", "SHADOW"),
    SHINY("shiny", "SHINY");

    fun contains(pokemon: OwnedPokemon): Boolean = when (this) {
        ALL -> true
        FAVORITES -> pokemon.isFavorite
        SHADOW -> pokemon.isShadow
        SHINY -> pokemon.isShiny
    }

    companion object {
        fun fromRouteKey(value: String?): OwnedPokemonBinder =
            entries.firstOrNull { it.routeKey.equals(value, ignoreCase = true) } ?: ALL
    }
}
