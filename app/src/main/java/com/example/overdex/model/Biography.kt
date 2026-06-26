package com.example.overdex.model

import kotlinx.serialization.Serializable

/**
 * Encapsulates the entire multi-section biography for a Pokémon.
 */
@Serializable
data class PokemonBiography(
    val sections: List<BiographySection> = emptyList()
)

/**
 * A discrete block of biographical information.
 */
@Serializable
data class BiographySection(
    val type: BiographySectionType,
    val title: String,
    val subtitle: String? = null, // Future enhancement: added optional subtitle support
    val content: List<String>, // Each item is a paragraph or bullet point
    val references: List<BiographyReference> = emptyList()
)

/**
 * Defines the known categories of information.
 */
@Serializable
enum class BiographySectionType {
    BIOLOGY,
    ORIGINS,
    ANIME,
    GAMES,
    POKEMON_GO,
    TRIVIA,
    REFERENCES
}

/**
 * A rich citation model supporting academic-style references,
 * media sources, and external links.
 */
@Serializable
data class BiographyReference(
    val type: ReferenceType,
    val title: String, // e.g., "Pokémon Red Version Manual" or "Bulbapedia: Charizard"
    val author: String? = null, // e.g., "Game Freak" or "Ken Sugimori"
    val year: Int? = null,
    val version: String? = null, // e.g., "1.0", "Season 1, Ep 2", or "Volume 4"
    val identifier: String? = null // URL, ISBN, or DOI
)

/**
 * Supported source media types for references.
 */
@Serializable
enum class ReferenceType {
    WEBSITE,      // Clickable URL
    GAME_SOURCE,  // Game manual or flavor text source
    ANIME_SOURCE, // Anime credits or episode details
    MANGA_SOURCE, // Manga volume/chapter
    BOOK,         // Official guides or art books
    OTHER
}
