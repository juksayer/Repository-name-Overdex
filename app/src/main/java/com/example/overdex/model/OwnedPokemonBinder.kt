package com.example.overdex.model

import kotlinx.serialization.Serializable

@Serializable
enum class BinderFlag(val label: String) {
    ANY("Any"), ONLY("Only"), EXCLUDE("Exclude");
    fun accepts(value: Boolean) = when (this) { ANY -> true; ONLY -> value; EXCLUDE -> !value }
}

/** Standard open-league presets. Seasonal cups use explicit additional rules. */
@Serializable
enum class BinderLeague(val label: String, val maxCp: Int?) {
    ANY("No league restriction", null), GREAT("Great League", 1500),
    ULTRA("Ultra League", 2500), MASTER("Master League", null)
}

@Serializable
data class BinderRules(
    val league: BinderLeague = BinderLeague.ANY,
    val minCp: Int? = null,
    val maxCp: Int? = null,
    val allowedTypes: Set<PokemonType> = emptySet(),
    val excludedTypes: Set<PokemonType> = emptySet(),
    val region: String? = null,
    val shadow: BinderFlag = BinderFlag.ANY,
    val shiny: BinderFlag = BinderFlag.ANY,
    val favorite: BinderFlag = BinderFlag.ANY,
    val allowedSpecies: Set<Int> = emptySet(),
    val excludedSpecies: Set<Int> = emptySet(),
) {
    val effectiveMaxCp: Int? get() = listOfNotNull(maxCp, league.maxCp).minOrNull()
    fun validationError(): String? = when {
        minCp != null && minCp < 10 -> "Minimum CP must be at least 10."
        maxCp != null && maxCp < 10 -> "Maximum CP must be at least 10."
        minCp != null && effectiveMaxCp != null && minCp > effectiveMaxCp!! -> "Minimum CP exceeds the league or binder maximum."
        else -> null
    }

    fun accepts(card: OwnedPokemon, species: Pokemon?): Boolean {
        if (validationError() != null) return false
        if (!shadow.accepts(card.isShadow) || !shiny.accepts(card.isShiny) || !favorite.accepts(card.isFavorite)) return false
        if (card.speciesId in excludedSpecies || allowedSpecies.isNotEmpty() && card.speciesId !in allowedSpecies) return false
        if (minCp != null || effectiveMaxCp != null) {
            val cp = card.cp ?: return false // Unknown CP cannot establish eligibility.
            if (cp < (minCp ?: 10) || cp > (effectiveMaxCp ?: Int.MAX_VALUE)) return false
        }
        if (allowedTypes.isNotEmpty() || excludedTypes.isNotEmpty() || region != null || league != BinderLeague.ANY) {
            val reference = species ?: return false
            if (allowedTypes.isNotEmpty() && reference.types.none(allowedTypes::contains)) return false
            if (reference.types.any(excludedTypes::contains)) return false
            if (region != null) {
                // Older imported rows label Hisui as Paldea. National-dex sections
                // keep binder filtering consistent with registration's region tabs.
                val range = REGION_RANGES.entries.firstOrNull { it.key.equals(region, true) }?.value
                if (range != null && reference.id !in range) return false
                if (range == null && !region.equals(reference.region, true)) return false
            }
            if (league != BinderLeague.ANY && Regex("\\b(mega|primal)\\b", RegexOption.IGNORE_CASE).containsMatchIn(reference.name)) return false
        }
        return true
    }

    private companion object {
        val REGION_RANGES = mapOf("Kanto" to 1..151, "Johto" to 152..251, "Hoenn" to 252..386,
            "Sinnoh" to 387..493, "Unova" to 494..649, "Kalos" to 650..721, "Alola" to 722..809,
            "Galar" to 810..898, "Hisui" to 899..905, "Paldea" to 906..1025)
    }
}

/** A saved view: the same specimen can appear in several binders without being copied. */
@Serializable
data class OwnedPokemonBinder(
    val routeKey: String,
    val displayName: String,
    val rules: BinderRules = BinderRules(),
) {
    val isBuiltIn: Boolean get() = routeKey in setOf("all", "favorites", "shadow", "shiny")
    fun contains(pokemon: OwnedPokemon): Boolean = rules.accepts(pokemon, null)
    fun contains(pokemon: OwnedPokemon, species: Pokemon?): Boolean = rules.accepts(pokemon, species)

    companion object {
        val ALL = OwnedPokemonBinder("all", "ALL OWNED")
        val FAVORITES = OwnedPokemonBinder("favorites", "FAVORITES", BinderRules(favorite = BinderFlag.ONLY))
        val SHADOW = OwnedPokemonBinder("shadow", "SHADOW", BinderRules(shadow = BinderFlag.ONLY))
        val SHINY = OwnedPokemonBinder("shiny", "SHINY", BinderRules(shiny = BinderFlag.ONLY))
        val entries = listOf(ALL, FAVORITES, SHADOW, SHINY)
        fun fromRouteKey(value: String?): OwnedPokemonBinder = entries.firstOrNull { it.routeKey.equals(value, true) } ?: ALL
    }
}
