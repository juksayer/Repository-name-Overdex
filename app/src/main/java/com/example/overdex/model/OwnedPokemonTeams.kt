package com.example.overdex.model

import java.util.Locale

object OwnedPokemonTeams {
    fun parse(text: String): List<String> = text.split(',', '\n')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinctBy { it.lowercase(Locale.ROOT) }
}
