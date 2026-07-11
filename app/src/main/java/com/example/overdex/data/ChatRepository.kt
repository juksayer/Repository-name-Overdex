package com.example.overdex.data

import android.content.Context
import com.example.overdex.model.ChatMessage
import com.example.overdex.model.ChatMessageType
import com.example.overdex.model.TrainerIdentity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer

/**
 * Manages the single private conversation between the trainer and their partner.
 */
interface ChatRepository {
    val messages: StateFlow<List<ChatMessage>>
    suspend fun send(text: String, myIdentity: TrainerIdentity)
    suspend fun receive(message: ChatMessage)
}

class SharedPreferencesChatRepository(private val context: Context) : ChatRepository {
    private val prefs = context.getSharedPreferences("overdex_chat_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    override val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    init {
        val storedJson = prefs.getString("chat_messages", "[]")
        if (storedJson != null) {
            try {
                _messages.value = json.decodeFromString(ListSerializer(ChatMessage.serializer()), storedJson)
            } catch (e: Exception) {
                // Handle malformed data
            }
        }
    }

    override suspend fun send(text: String, myIdentity: TrainerIdentity) {
        val newMessage = ChatMessage(
            senderTrainerId = myIdentity.trainerId.toString(),
            type = ChatMessageType.TEXT,
            text = text
        )
        saveMessage(newMessage)
    }

    override suspend fun receive(message: ChatMessage) {
        saveMessage(message)
    }

    private fun saveMessage(message: ChatMessage) {
        val currentList = _messages.value.toMutableList()
        currentList.add(message)
        val sortedList = currentList.sortedBy { it.sentAt }
        
        val chatJson = json.encodeToString(ListSerializer(ChatMessage.serializer()), sortedList)
        prefs.edit().putString("chat_messages", chatJson).apply()
        _messages.value = sortedList
    }
}
