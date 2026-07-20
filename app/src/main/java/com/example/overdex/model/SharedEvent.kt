package com.example.overdex.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/**
 * Defines the type of event shared between trainers.
 */
@Serializable
enum class SharedEventType {
    /** Two trainers established a link. */
    LINKED,
    /** a trainer changed their public name. */
    DISPLAY_NAME_CHANGED,
    /** A significant career milestone was achieved. */
    MILESTONE,
    /** A trainer added a social note. */
    NOTE
}

/**
 * A social event shared within a trainer link.
 * 
 * @property id Unique identifier for the event.
 * @property timestamp Time when the event was recorded.
 * @property authorTrainerId The ID of the trainer who triggered the event.
 * @property type The kind of event.
 * @property payload A string description or data associated with the event.
 * @property milestone Optional detailed milestone data if the type is MILESTONE.
 */
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
