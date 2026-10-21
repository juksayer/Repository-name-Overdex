package com.example.overdex.data.registration

import com.example.overdex.model.*

/** Current GO families fill the gaps in the older Pokédex import without rewriting it. */
fun buildRegistrationCatalogue(
    imported: List<Pokemon>,
    reference: List<GameMasterPokemon>,
    getMove: (String) -> GameMasterMove?,
): RegistrationCatalogue {
    val byId = reference.associateBy { it.speciesId }
    val byDex = reference.groupBy { it.dex }
    fun relatives(ids: List<String>): List<Evolution> = ids.mapNotNull { id ->
        byId[id]?.let { Evolution(it.dex.toString(), it.speciesName.substringBefore(" (")) }
    }.distinctBy { it.num }
    return RegistrationCatalogue(imported.map { pokemon ->
        val forms = byDex[pokemon.id].orEmpty()
        val base = forms.firstOrNull { textKey(it.speciesName) == textKey(pokemon.name) } ?: forms.firstOrNull()
        fun moves(fast: Boolean): List<Move> = forms.flatMap { if (fast) it.fastMoves else it.chargedMoves }
            .distinct().mapNotNull { id -> getMove(id)?.let { m ->
                val type = PokemonType.entries.find { it.name.equals(m.type, true) } ?: return@let null
                Move(m.name, type, m.power, if (fast) m.energyGain else kotlin.math.abs(m.energy), fast, if (fast) m.turns else null)
            } }
        pokemon.copy(
            types = base?.types?.mapNotNull { raw -> PokemonType.entries.find { it.name.equals(raw, true) } }
                ?.takeIf { it.isNotEmpty() } ?: pokemon.types,
            prevEvolutions = (pokemon.prevEvolutions + relatives(forms.mapNotNull { it.family?.parent })).distinctBy { it.num },
            nextEvolutions = (pokemon.nextEvolutions + relatives(forms.flatMap { it.family?.evolutions.orEmpty() })).distinctBy { it.num },
            fastMoves = (pokemon.fastMoves + moves(true)).distinctBy { textKey(it.name) },
            chargedMoves = (pokemon.chargedMoves + moves(false)).distinctBy { textKey(it.name) },
        )
    })
}
