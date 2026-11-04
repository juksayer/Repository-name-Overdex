package com.example.overdex.battle.observation

import com.example.overdex.model.PokemonType

/** A reading is complete only when every detected badge was recognized. */
internal data class ActiveTypeIconReading(
    val types: List<PokemonType>,
    val similarity: Float,
) {
    companion object {
        fun from(matches: List<PokemonGoTypeIconMatcher.Result>): ActiveTypeIconReading? {
            if (matches.size !in 1..2 || matches.any {
                    it.type == null || !it.similarity.isFinite() || it.similarity !in 0f..1f
                }) return null
            val types = matches.map { requireNotNull(it.type) }
            if (types.distinct().size != types.size) return null
            return ActiveTypeIconReading(types, matches.minOf { it.similarity })
        }
    }
}
