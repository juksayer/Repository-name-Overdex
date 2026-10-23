package com.example.overdex.comms

import kotlinx.coroutines.flow.Flow

interface CommsBackend {
    suspend fun loadProfile(): CommsTrainer?
    suspend fun saveProfile(name: String, code: String): CommsTrainer
    suspend fun findTrainer(code: String): CommsTrainer
    suspend fun loadTrainer(accountId: String): CommsTrainer?
    fun conversations(): Flow<List<CommsConversation>>
    fun messages(conversationId: String): Flow<List<CommsMessage>>
    suspend fun createConversation(conversation: CommsConversation)
    /** Returns only after the server acknowledges the immutable message. */
    suspend fun send(message: CommsMessage)
}

interface CommsStore {
    suspend fun load(accountId: String): CommsSnapshot
    suspend fun save(accountId: String, snapshot: CommsSnapshot)
}
