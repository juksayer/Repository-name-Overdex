package com.example.overdex.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

@Serializable
enum class SharedEventType {
    LINKED,
    DISPLAY_NAME_CHANGED,
    MILESTONE,
    NOTE
}

@Serializable
data class SharedEvent(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID = UUID.randomUUID(),
    @Serializable(with = InstantSerializer::class)
    val timestamp: Instant = Instant.now(),
    val authorTrainerId: String,
    val type: SharedEventType,
    val payload: String,
    val milestone: Milestone? = null // Optional milestone data
)
