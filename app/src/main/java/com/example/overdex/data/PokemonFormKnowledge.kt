package com.example.overdex.data

import com.example.overdex.model.Move
import com.example.overdex.model.Pokemon
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale

/** Reference alternatives for an observed badge name; none claims that a form was witnessed. */
data class PokemonFormCandidates(
    val names: List<String>,
    val pokemon: List<Pokemon?>,
) {
    val complete: Boolean = pokemon.isNotEmpty() && pokemon.none { it == null }

    // Missing reference knowledge cannot make the available entries exhaustive.
    val fastMoves: List<Move> = if (!complete) emptyList() else pokemon.filterNotNull()
        .flatMap { it.fastMoves }.filter { it.isFast && it.turns != null }.distinctBy { moveKey(it.name) }
    val chargedMoves: List<Move> = if (!complete) emptyList() else pokemon.filterNotNull()
        .flatMap { it.chargedMoves }.distinctBy { moveKey(it.name) }

    private fun moveKey(value: String) = value.uppercase(Locale.ROOT).filter(Char::isLetterOrDigit)
}

/**
 * One per-Match cache shared by the HUD, forecast and Fast Move interpretation.
 * Use exact configured/resolved player references directly; a bare opponent badge
 * may stand for any catalogue form with that visible species name.
 */
class PokemonFormKnowledge(private val knowledge: PokemonKnowledge) {
    private val mutex = Mutex()
    private var catalogueNames: Set<String>? = null
    private val cache = mutableMapOf<Pair<String, Int?>, PokemonFormCandidates>()

    suspend fun forObservedName(name: String, fallbackDex: Int? = null): PokemonFormCandidates = mutex.withLock {
        val key = name.uppercase(Locale.ROOT) to fallbackDex
        cache[key]?.let { return@withLock it }
        val names = PokemonFormReference.observedReferenceNames(name,
            catalogueNames ?: knowledge.getAllSpeciesNames().also { catalogueNames = it })
        PokemonFormCandidates(names, names.map { candidate ->
            knowledge.getPokemonByName(candidate) ?: fallbackDex?.takeIf { '(' !in candidate }
                ?.let { knowledge.getPokemonById(it) }
        }).also { cache[key] = it }
    }
}
