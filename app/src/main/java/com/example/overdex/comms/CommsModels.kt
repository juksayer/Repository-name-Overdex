package com.example.overdex.comms

import kotlinx.serialization.Serializable
import java.security.MessageDigest

/** Public discovery uses the GO code. Access and history use the immutable account ID. */
object TrainerCode {
    fun normalize(input: String): String {
        val code = input.filterNot { it.isWhitespace() || it == '-' }
        require(code.matches(Regex("[0-9]{12}"))) { "Enter all 12 digits of the Pokémon GO trainer code." }
        return code
    }
    fun display(code: String) = code.chunked(4).joinToString(" ")
}

@Serializable
data class CommsTrainer(val accountId: String, val trainerCode: String, val displayName: String)

@Serializable
enum class ConversationKind { DIRECT, GROUP, LEGACY }

@Serializable
data class CommsConversation(
    val id: String,
    val title: String,
    val kind: ConversationKind,
    val memberIds: List<String>,
    val ownerId: String,
    val createdAt: Long,
    val confirmed: Boolean = false,
) {
    companion object {
        const val MAX_MEMBERS = 8
        const val LEGACY_ID = "legacy-local-history"
        fun directId(first: String, second: String): String {
            require(first != second)
            val bytes = listOf(first, second).sorted().joinToString("\n").toByteArray(Charsets.UTF_8)
            return "direct-" + MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
        }
    }
}

@Serializable
enum class DeliveryStatus { PENDING, SENT, FAILED, UNKNOWN }

@Serializable
data class CommsMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val sentAt: Long,
    val status: DeliveryStatus = DeliveryStatus.PENDING,
    val serverSentAt: Long? = null,
    val failure: String? = null,
)

@Serializable
data class CommsSnapshot(
    val profile: CommsTrainer? = null,
    val contacts: List<CommsTrainer> = emptyList(),
    val conversations: List<CommsConversation> = emptyList(),
    val messages: List<CommsMessage> = emptyList(),
    val drafts: Map<String, String> = emptyMap(),
)

data class CommsState(
    val accountId: String? = null,
    val snapshot: CommsSnapshot = CommsSnapshot(),
    val selectedConversationId: String? = null,
    val loading: Boolean = false,
    val connected: Boolean = false,
    val error: String? = null,
) {
    val selectedConversation get() = snapshot.conversations.find { it.id == selectedConversationId }
    val visibleMessages get() = snapshot.messages.filter { it.conversationId == selectedConversationId }
        .sortedWith(compareBy<CommsMessage> { it.sentAt }.thenBy { it.id })
}
