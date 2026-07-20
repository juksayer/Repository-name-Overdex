package com.example.overdex.data

import com.example.overdex.model.ChatMessage
import kotlinx.coroutines.flow.Flow

/**
 * Low-level transport abstraction for transmitting [ChatMessage]s between trainers.
 * 
 * Implementations of this interface (e.g., Firebase, Bluetooth) handle the actual
 * network or peer-to-peer communication logic.
 */
interface ChatTransport {
    /**
     * Sends a message to the remote trainer(s).
     */
    suspend fun send(message: ChatMessage)

    /**
     * Returns a reactive flow of incoming messages from the transport.
     */
    fun observe(): Flow<ChatMessage>
}
