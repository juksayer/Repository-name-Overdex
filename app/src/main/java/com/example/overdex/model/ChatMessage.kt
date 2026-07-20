package com.example.overdex.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/**
 * Defines the type of content within a chat message.
 */
@Serializable
enum class ChatMessageType {
    /** A plain text message. */
    TEXT,
    /** A system-generated notification (e.g., "Trainer joined"). */
    SYSTEM,
    /** A message containing a shared Pokémon specimen record. */
    POKEMON
}

/**
 * Represents a discrete communication in a trainer-to-trainer chat.
 * 
 * @property id Unique identifier for the message.
 * @property senderTrainerId The ID of the trainer who sent the message.
 * @property sentAt The timestamp when the message was sent.
 * @property type The kind of message.
 * @property text The string content (for TEXT or SYSTEM types).
 * @property sharedPokemon The Pokémon data (for POKEMON type).
 */
@Serializable
data class ChatMessage(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID = UUID.randomUUID(),
    val senderTrainerId: String,
    @Serializable(with = InstantSerializer::class)
    val sentAt: Instant = Instant.now(),
    val type: ChatMessageType,
    val text: String? = null,
    val sharedPokemon: SharedPokemon? = null
)
