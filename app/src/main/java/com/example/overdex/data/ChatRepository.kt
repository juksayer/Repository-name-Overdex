package com.example.overdex.data

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.example.overdex.model.ChatMessage
import com.example.overdex.model.ChatMessageType
import com.example.overdex.model.OwnedPokemon
import com.example.overdex.model.Pokemon
import com.example.overdex.model.SharedPokemon
import com.example.overdex.model.TrainerIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

interface ChatRepository {
    val messages: StateFlow<List<ChatMessage>>
    suspend fun send(text: String, myIdentity: TrainerIdentity)
    suspend fun sendPokemon(ownedPokemon: OwnedPokemon, species: Pokemon, myIdentity: TrainerIdentity)
    suspend fun receive(message: ChatMessage)
    fun setTransport(transport: ChatTransport)
}

class SharedPreferencesChatRepository(
    private val context: Context
) : ChatRepository {

    private val prefs =
        context.getSharedPreferences("overdex_chat_prefs", Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    override val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private var transport: ChatTransport? = null


    override fun setTransport(transport: ChatTransport) {
        this.transport = transport

        transport.observe()
            .onEach { receive(it) }
            .launchIn(scope)
    }

    override suspend fun send(
        text: String,
        myIdentity: TrainerIdentity
    ) {

        Log.i(
            "CHAT_TRANSPORT",
            "Repository.send() transport=${transport?.javaClass?.simpleName}"
        )

        val newMessage = ChatMessage(
            senderTrainerId = myIdentity.trainerId.toString(),
            type = ChatMessageType.TEXT,
            text = text
        )

        saveMessage(newMessage)

        Log.i(
            "CHAT_TRANSPORT",
            "Repository calling transport.send()"
        )

        transport?.send(newMessage)

        Log.i(
            "CHAT_TRANSPORT",
            "Repository returned from transport.send()"
        )
    }

    override suspend fun sendPokemon(
        ownedPokemon: OwnedPokemon,
        species: Pokemon,
        myIdentity: TrainerIdentity
    ) {

        Log.i(
            "CHAT_TRANSPORT",
            "Repository.sendPokemon() transport=${transport?.javaClass?.simpleName}"
        )

        val sharedPokemon = SharedPokemon(
            speciesId = ownedPokemon.speciesId,
            speciesName = species.name,
            cp = ownedPokemon.cp,
            isShadow = ownedPokemon.isShadow,
            isPurified = ownedPokemon.isPurified,
            isShiny = ownedPokemon.isShiny,
            primaryType = species.types.firstOrNull()?.name ?: "NORMAL",
            secondaryType = species.types.getOrNull(1)?.name
        )

        val newMessage = ChatMessage(
            senderTrainerId = myIdentity.trainerId.toString(),
            type = ChatMessageType.POKEMON,
            sharedPokemon = sharedPokemon
        )

        saveMessage(newMessage)

        transport?.send(newMessage)
    }

    override suspend fun receive(message: ChatMessage) {

        Log.i(
            "CHAT_TRANSPORT",
            "Repository.receive() incoming=${message.id}"
        )

        Log.i(
            "CHAT_TRANSPORT",
            "Repository currently has ${_messages.value.size} messages"
        )

        _messages.value.forEachIndexed { index, existing ->
            Log.i(
                "CHAT_TRANSPORT",
                "[$index] existing=${existing.id}"
            )
        }

        val duplicate = _messages.value.any { it.id == message.id }

        Log.i(
            "CHAT_TRANSPORT",
            "Duplicate = $duplicate"
        )

        if (duplicate) {
            Log.i(
                "CHAT_TRANSPORT",
                "Ignoring duplicate ${message.id}"
            )
            return
        }

        Log.i(
            "CHAT_TRANSPORT",
            "Saving ${message.id}"
        )

        saveMessage(message)

        Log.i(
            "CHAT_TRANSPORT",
            "Repository now contains ${_messages.value.size} messages"
        )
    }

    private fun saveMessage(message: ChatMessage) {

        val updated =
            (_messages.value + message)
                .sortedBy { it.sentAt }

        prefs.edit {
            putString(
                "chat_messages",
                json.encodeToString(
                    ListSerializer(ChatMessage.serializer()),
                    updated
                )
            )
        }

        _messages.value = updated

        Log.i(
            "CHAT_TRANSPORT",
            "saveMessage() -> ${updated.size} messages"
        )
    }
}