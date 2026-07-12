package com.example.overdex.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

@Serializable
enum class ChatMessageType {
    TEXT,
    SYSTEM,
    POKEMON
}

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
