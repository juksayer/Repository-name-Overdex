package com.example.overdex.model

import kotlinx.serialization.Serializable

/**
 * Canonical Battle Haiku knowledge for a single Pokémon species.
 *
 * A Pokémon may have multiple field notes over time
 * (official, community, seasonal, translations, etc.).
 */
@Serializable
data class BattleHaiku(
    val id: Int,
    val species: String,
    val fieldNote: List<FieldNote>
)

/**
 * A single Battle Haiku.
 */
@Serializable
data class FieldNote(
    /**
     * Stable identifier.
     *
     * Examples:
     * 001-001
     * 001-002
     * 025-001
     */
    val id: String,

    /**
     * Title displayed above the haiku.
     */
    val title: String,

    /**
     * Three haiku lines.
     *
     * lines[0] = 5 syllables
     * lines[1] = 7 syllables
     * lines[2] = 5 syllables
     */
    val lines: List<String>,

    /**
     * Optional lesson shown beneath the poem.
     */
    val lesson: String? = null,

    /**
     * Original author.
     */
    val author: String,

    /**
     * Editorially approved for publication.
     */
    val verified: Boolean = true,

    /**
     * Revision number.
     */
    val revision: Int = 1
)