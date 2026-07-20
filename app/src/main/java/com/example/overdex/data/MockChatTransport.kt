package com.example.overdex.data

import com.example.overdex.model.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * In-memory implementation of [ChatTransport] used for local testing and
 * development without a real network connection.
 */
class MockChatTransport : ChatTransport {
    private val _incoming = MutableSharedFlow<ChatMessage>()
    
    /** Records the sent message (currently a no-op). */
    override suspend fun send(message: ChatMessage) {
        // Just echo it back in a mock environment if needed, 
        // or just log it.
    }

    override fun observe(): Flow<ChatMessage> = _incoming.asSharedFlow()

    suspend fun simulateIncoming(message: ChatMessage) {
        _incoming.emit(message)
    }
}
