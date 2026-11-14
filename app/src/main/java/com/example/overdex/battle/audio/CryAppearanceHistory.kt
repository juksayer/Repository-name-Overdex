package com.example.overdex.battle.audio

import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.inference.CryAppearanceInterval

/** Small capture-time identity history. Audio-derived identities must not be supplied here. */
internal class CryAppearanceHistory {
    internal data class Identity(val id: String, val atNanos: Long, val side: ActivePokemonSide,
        val speciesId: Int?, val entry: Boolean = false)
    private val identities = linkedMapOf<String, Identity>()
    var unavailable = false
        private set

    @Synchronized fun reset() { identities.clear(); unavailable=false }
    @Synchronized fun accept(identity: Identity) {
        if (unavailable || identity.id.isBlank()) return
        val old=identities[identity.id]
        if (old!=null && old!=identity || old==null && identities.size>=MAX_IDENTITIES) {
            unavailable=true
            return
        }
        identities[identity.id]=identity
    }
    @Synchronized fun speciesIds(): Set<Int> = if(unavailable) emptySet() else identities.values.mapNotNull { it.speciesId }.toSet()

    @Synchronized fun intervals(): List<CryAppearanceInterval> {
        if(unavailable)return emptyList()
        return ActivePokemonSide.entries.flatMap { side ->
            val result=mutableListOf<CryAppearanceInterval>()
            var lastSighting: Pair<Long, List<String>>? = null
            for((at,group) in identities.values.filter { it.side==side }.groupBy { it.atNanos }.toSortedMap()) {
                val species=group.map { it.speciesId }.distinct().singleOrNull()
                val previous=result.lastOrNull()
                val entry=group.any { it.entry }
                val ids=group.map { it.id }.sorted()
                if(previous!=null && previous.speciesId==species && !entry) {
                    lastSighting = at to ids
                    continue
                }
                if(previous!=null)result[result.lastIndex]=previous.copy(untilNanos=at)
                result += CryAppearanceInterval(ids.first(),side,species,at,Long.MAX_VALUE,
                    (ids + lastSighting?.second.orEmpty()).distinct(),
                    lastSighting?.first?.let { if (it == Long.MAX_VALUE) it else it + 1 } ?: Long.MIN_VALUE)
                lastSighting = at to ids
            }
            result
        }
    }
    companion object { const val MAX_IDENTITIES=512 }
}
