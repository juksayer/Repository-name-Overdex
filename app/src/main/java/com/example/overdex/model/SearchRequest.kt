package com.example.overdex.model

data class SearchRequest(
    val text: String = "",
    val type: PokemonType? = null
) {
    data class ActiveFilter(
        val label: String
    )

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