package com.example.overdex.data

import com.example.overdex.model.ChatMessage
import kotlinx.coroutines.flow.Flow

/**
 * Low-level transport for chat messages.
 */
interface ChatTransport {
    /**
     * Sends a message to the transport layer.
     */
    suspend fun send(message: ChatMessage)

    /**
     * Observes incoming messages from the transport layer.
     */
    fun observe(): Flow<ChatMessage>
}
