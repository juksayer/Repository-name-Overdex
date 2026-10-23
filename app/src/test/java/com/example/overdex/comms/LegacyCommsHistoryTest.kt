package com.example.overdex.comms

import com.example.overdex.model.ChatMessage
import com.example.overdex.model.ChatMessageType
import com.example.overdex.model.SharedPokemon
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.UUID

class LegacyCommsHistoryTest {
    @Test fun `legacy messages preserve identity time text and unknown delivery without guessing recipients`() {
        val message = ChatMessage(UUID.randomUUID(), "old-trainer-uuid", Instant.ofEpochMilli(1234),
            ChatMessageType.TEXT, "Original message")
        val raw = Json.encodeToString(listOf(message))
        val migrated = LegacyCommsHistory.read(raw, "account")
        val record = migrated.messages.single()
        assertEquals(message.id.toString(), record.id)
        assertEquals("old-trainer-uuid", record.senderId)
        assertEquals(1234, record.sentAt)
        assertEquals("Original message", record.text)
        assertEquals(DeliveryStatus.UNKNOWN, record.status)
        assertTrue(migrated.conversations.single().memberIds.isEmpty())
        assertEquals(ConversationKind.LEGACY, migrated.conversations.single().kind)
        assertEquals(raw, Json.encodeToString(listOf(message)))
    }
    @Test fun `legacy shared pokemon remains readable`() {
        val message = ChatMessage(senderTrainerId = "old", type = ChatMessageType.POKEMON,
            sharedPokemon = SharedPokemon(6, "Charizard", 1499, true, false, true, "FIRE", "FLYING"))
        val record = LegacyCommsHistory.read(Json.encodeToString(listOf(message)), "account").messages.single()
        for (detail in listOf("Charizard", "1499", "Shadow", "Shiny", "FIRE", "FLYING")) assertTrue(record.text.contains(detail))
    }
    @Test fun `corrupt legacy data fails visibly instead of replacing it with empty history`() {
        assertThrows(Exception::class.java) { LegacyCommsHistory.read("broken-json", "account") }
    }
}
