package com.example.overdex.comms

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** All remote operations are bound to the signed-in account that created this backend. */
class FirebaseCommsBackend(
    private val accountId: String,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : CommsBackend {
    private val profiles get() = firestore.collection("trainerCommsProfiles")
    private val codes get() = firestore.collection("trainerCommsCodes")
    private val rooms get() = firestore.collection("trainerConversations")
    private fun requireAccount() {
        check(auth.currentUser?.uid == accountId && auth.currentUser?.isAnonymous == false) { "Sign in again to continue." }
    }

    override suspend fun loadProfile(): CommsTrainer? {
        requireAccount()
        return profiles.document(accountId).get(Source.SERVER).await().trainerOrNull()
    }

    override suspend fun saveProfile(name: String, code: String): CommsTrainer {
        requireAccount()
        val profile = CommsTrainer(accountId, TrainerCode.normalize(code), name)
        val reference = profiles.document(accountId)
        val codeReference = codes.document(profile.trainerCode)
        firestore.runTransaction { transaction ->
            requireAccount()
            val previous = transaction.get(reference).getString("trainerCode")
            val claimed = transaction.get(codeReference).getString("accountId")
            require(claimed == null || claimed == accountId) { "That trainer code is already linked to another account." }
            transaction.set(reference, mapOf("accountId" to accountId, "trainerCode" to profile.trainerCode,
                "displayName" to name, "updatedAt" to FieldValue.serverTimestamp()))
            transaction.set(codeReference, mapOf("accountId" to accountId))
            if (previous != null && previous != profile.trainerCode) transaction.delete(codes.document(previous))
        }.await()
        return profile
    }

    override suspend fun loadTrainer(accountId: String): CommsTrainer? {
        requireAccount()
        return profiles.document(accountId).get(Source.SERVER).await().trainerOrNull()
    }

    override suspend fun findTrainer(code: String): CommsTrainer {
        requireAccount()
        val owner = codes.document(TrainerCode.normalize(code)).get(Source.SERVER).await().getString("accountId")
            ?: throw IllegalArgumentException("No Overdex account has registered that trainer code yet.")
        val trainer = profiles.document(owner).get(Source.SERVER).await().trainerOrNull()
            ?: throw IllegalStateException("This trainer's profile is not available.")
        require(trainer.trainerCode == code) { "This trainer changed their code. Try their new code." }
        return trainer
    }

    override fun conversations(): Flow<List<CommsConversation>> = callbackFlow {
        requireAccount()
        val listener = rooms.whereArrayContains("memberIds", accountId).addSnapshotListener { snapshot, error ->
            if (error != null) { close(error); return@addSnapshotListener }
            try {
                requireAccount()
                val conversations = snapshot?.documents.orEmpty().map { doc ->
                    CommsConversation(doc.id, requireNotNull(doc.getString("title")),
                        ConversationKind.valueOf(requireNotNull(doc.getString("kind"))),
                        (doc.get("memberIds") as List<*>).map { it as String },
                        requireNotNull(doc.getString("ownerId")),
                        doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L, confirmed = true)
                }
                trySend(conversations)
            } catch (e: Exception) { close(e) }
        }
        awaitClose { listener.remove() }
    }

    override fun messages(conversationId: String): Flow<List<CommsMessage>> = callbackFlow {
        requireAccount()
        val listener = rooms.document(conversationId).collection("messages").orderBy("sentAt")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                try {
                    requireAccount()
                    // A local Firestore echo is not an acknowledgement from the server.
                    val messages = snapshot?.documents.orEmpty().filterNot { it.metadata.hasPendingWrites() }
                        .mapNotNull { doc ->
                            val ack = doc.getTimestamp("serverSentAt") ?: return@mapNotNull null
                            CommsMessage(doc.id, conversationId, requireNotNull(doc.getString("senderId")),
                                requireNotNull(doc.getString("senderName")), requireNotNull(doc.getString("text")),
                                requireNotNull(doc.getLong("sentAt")), DeliveryStatus.SENT, ack.toDate().time)
                        }
                    trySend(messages)
                } catch (e: Exception) { close(e) }
            }
        awaitClose { listener.remove() }
    }

    override suspend fun createConversation(conversation: CommsConversation) {
        requireAccount()
        require(conversation.kind != ConversationKind.LEGACY && accountId in conversation.memberIds)
        val reference = rooms.document(conversation.id)
        firestore.runTransaction { transaction ->
            requireAccount()
            val existing = transaction.get(reference)
            if (existing.exists()) {
                check((existing.get("memberIds") as? List<*>)?.toSet() == conversation.memberIds.toSet() &&
                    existing.getString("kind") == conversation.kind.name) { "Conversation membership does not match." }
            } else {
                transaction.set(reference, mapOf("title" to conversation.title, "kind" to conversation.kind.name,
                    "memberIds" to conversation.memberIds, "ownerId" to accountId, "createdAt" to FieldValue.serverTimestamp()))
            }
        }.await()
    }

    override suspend fun send(message: CommsMessage) {
        requireAccount()
        require(message.senderId == accountId)
        val reference = rooms.document(message.conversationId).collection("messages").document(message.id)
        firestore.runTransaction { transaction ->
            requireAccount()
            val existing = transaction.get(reference)
            if (existing.exists()) {
                check(existing.getString("senderId") == accountId && existing.getString("text") == message.text &&
                    existing.getLong("sentAt") == message.sentAt) {
                    "The message ID is already in use."
                }
            } else {
                val senderName = requireNotNull(transaction.get(profiles.document(accountId)).getString("displayName"))
                transaction.set(reference, mapOf("senderId" to accountId, "senderName" to senderName,
                    "text" to message.text, "sentAt" to message.sentAt, "serverSentAt" to FieldValue.serverTimestamp()))
            }
        }.await()
    }

    private fun DocumentSnapshot.trainerOrNull(): CommsTrainer? = if (!exists()) null else
        CommsTrainer(id, requireNotNull(getString("trainerCode")), requireNotNull(getString("displayName")))
}
