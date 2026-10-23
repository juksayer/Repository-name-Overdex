package com.example.overdex.comms

import com.example.overdex.model.ChatMessage
import kotlinx.serialization.json.Json

/** Old records never contained recipients or acknowledgements; don't invent either. */
object LegacyCommsHistory {
    private val json = Json { ignoreUnknownKeys = true }
    fun read(raw: String, accountId: String): CommsSnapshot {
        val original = json.decodeFromString<List<ChatMessage>>(raw)
        val converted = original.map { message ->
            val pokemon = message.sharedPokemon
            val body = message.text ?: pokemon?.let {
                "Shared Pokémon: ${it.speciesName}, CP ${it.cp ?: "unknown"}" +
                    (if (it.isShadow) " · Shadow" else "") + (if (it.isPurified) " · Purified" else "") +
                    (if (it.isShiny) " · Shiny" else "") + "\n" +
                    listOfNotNull(it.primaryType, it.secondaryType).joinToString(" / ")
            } ?: "System event"
            CommsMessage(message.id.toString(), CommsConversation.LEGACY_ID, message.senderTrainerId,
                "Legacy trainer", body, message.sentAt.toEpochMilli(), DeliveryStatus.UNKNOWN)
        }
        return CommsSnapshot(
            conversations = if (converted.isEmpty()) emptyList() else listOf(CommsConversation(
                CommsConversation.LEGACY_ID, "History from this device", ConversationKind.LEGACY,
                emptyList(), accountId, converted.minOf { it.sentAt })),
            messages = converted,
        )
    }
}
