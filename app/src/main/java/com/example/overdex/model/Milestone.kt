package com.example.overdex.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

@Serializable
enum class MilestoneType {
    FIRST_LINK,
    FIRST_POKEMON_ADDED,
    FIRST_SHINY,
    FIRST_SHADOW,
    POKEDEX_COMPLETE,
    HUNDRED_POKEMON_OWNED,
    THOUSAND_BATTLES,
    GO_FEST_ATTENDED
}

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
