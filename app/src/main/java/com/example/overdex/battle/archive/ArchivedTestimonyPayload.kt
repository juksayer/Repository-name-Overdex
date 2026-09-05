package com.example.overdex.battle.archive

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A sealed, serializable archive payload hierarchy.
 */
@Serializable
sealed interface ArchivedTestimonyPayload

@Serializable
@SerialName("raw_text")
data class ArchivedRawText(
    val value: String
) : ArchivedTestimonyPayload

@Serializable
@SerialName("raw_int")
data class ArchivedRawInt(
    val value: Int
) : ArchivedTestimonyPayload

@Serializable
@SerialName("attack_incoming")
data object ArchivedAttackIncoming : ArchivedTestimonyPayload

@Serializable
@SerialName("pokemon_identified")
data class ArchivedPokemonIdentified(
    val species: String
) : ArchivedTestimonyPayload
