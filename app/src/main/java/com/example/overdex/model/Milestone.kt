package com.example.overdex.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/**
 * Defines the types of significant achievements or events in a trainer's career.
 */
@Serializable
enum class MilestoneType {
    /** The first link established with another trainer. */
    FIRST_LINK,
    /** The first Pokémon added to the trainer's collection. */
    FIRST_POKEMON_ADDED,
    FIRST_SHINY,
    FIRST_SHADOW,
    /** Completion of a regional or global Pokédex. */
    POKEDEX_COMPLETE,
    HUNDRED_POKEMON_OWNED,
    THOUSAND_BATTLES,
    /** Attendance at a physical or virtual Pokémon GO Fest. */
    GO_FEST_ATTENDED
}

/**
 * Represents a historical achievement or significant event for a trainer.
 * 
 * @property id Unique identifier for the milestone.
 * @property type The category of achievement.
 * @property achievedBy The ID of the trainer who achieved the milestone.
 * @property achievedAt The timestamp when the milestone was recorded.
 * @property payload Additional contextual data associated with the milestone.
 */
@Serializable
data class Milestone(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID = UUID.randomUUID(),
    val type: MilestoneType,
    val achievedBy: String, // TrainerId
    @Serializable(with = InstantSerializer::class)
    val achievedAt: Instant = Instant.now(),
    val payload: String
)
