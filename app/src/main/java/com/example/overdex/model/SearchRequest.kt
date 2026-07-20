package com.example.overdex.model

/**
 * Models a query for searching and filtering the Pokémon collection or Pokédex.
 * 
 * @property text The name or partial string to search for.
 * @property type An optional elemental type to filter results by.
 */
data class SearchRequest(
    val text: String = "",
    val type: PokemonType? = null
) {
    /** Represents an active filter criterion applied to the search. */
    data class ActiveFilter(
        val label: String
    )

    /** Returns the list of UI labels for all active filters. */
    val activeFilters: List<ActiveFilter>
        get() = buildList {
            type?.let {
                add(
                    ActiveFilter(
                        label = it.name
                    )
                )
            }
        }
}