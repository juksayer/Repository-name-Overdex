package com.example.overdex.data

import android.util.Log
import com.example.overdex.model.ChatMessage
import com.example.overdex.model.ChatMessageType
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.util.UUID

/**
 * Firestore-based implementation of ChatTransport.
 */
class FirebaseChatTransport(
    private val myTrainerId: String,
    private val partnerTrainerId: String
) : ChatTransport {

    private val db = FirebaseFirestore.getInstance()

    private val conversationId =
        listOf(myTrainerId, partnerTrainerId)
            .sorted()
            .joinToString("_")

    private val messagesCollection = db
        .collection("conversations")
        .document(conversationId)
        .collection("messages")

    init {
        Log.i(
            "CHAT_TRANSPORT",
            """
        FirebaseChatTransport created
        myTrainerId=$myTrainerId
        partnerTrainerId=$partnerTrainerId
        conversationId=$conversationId
        """.trimIndent()
        )
    }

    override suspend fun send(message: ChatMessage) {

        Log.i(
            "CHAT_TRANSPORT",
            "SEND START"
        )

        val data = mutableMapOf<String, Any?>(
            "id" to message.id.toString(),
            "senderTrainerId" to message.senderTrainerId,
            "sentAt" to message.sentAt.toEpochMilli(),
            "type" to message.type.name,
            "text" to message.text
        )

        message.sharedPokemon?.let { sp ->
            data["sharedPokemon"] = mapOf(
                "speciesId" to sp.speciesId,
                "speciesName" to sp.speciesName,
                "cp" to sp.cp,
                "isShadow" to sp.isShadow,
                "isPurified" to sp.isPurified,
                "isShiny" to sp.isShiny,
                "primaryType" to sp.primaryType,
                "secondaryType" to sp.secondaryType
            )
        }

        Log.i(
            "CHAT_TRANSPORT",
            "About to write Firestore document..."
        )

        try {

            messagesCollection
                .document(message.id.toString())
                .set(data)

            Log.i(
                "CHAT_TRANSPORT",
                "Firestore write completed."
            )

            Log.i(
                "CHAT_TRANSPORT",
                """
                CHAT SEND
                Conversation: $conversationId
                Sender: ${message.senderTrainerId}
                Message: "${message.text}"
                """.trimIndent()
            )

        } catch (e: Exception) {

            Log.e(
                "CHAT_TRANSPORT",
                "SEND FAILED",
                e
            )
        }

        Log.i(
            "CHAT_TRANSPORT",
            "SEND END"
        )
    }

    override fun observe(): Flow<ChatMessage> = callbackFlow {

        Log.i(
            "CHAT_TRANSPORT",
            "Listener attached."
        )

        val registration = messagesCollection
            .orderBy("sentAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    Log.e(
                        "CHAT_TRANSPORT",
                        "LISTENER ERROR",
                        error
                    )
                    return@addSnapshotListener
                }

                if (snapshot == null) {
                    Log.i(
                        "CHAT_TRANSPORT",
                        "Snapshot was null."
                    )
                    return@addSnapshotListener
                }

                Log.i(
                    "CHAT_TRANSPORT",
                    "Snapshot received. ${snapshot.documentChanges.size} changes. fromCache=${snapshot.metadata.isFromCache} pendingWrites=${snapshot.metadata.hasPendingWrites()}"
                )

                for (change in snapshot.documentChanges) {

                    Log.i(
                        "CHAT_TRANSPORT",
                        "Firestore change type = ${change.type} " +
                                "fromCache=${snapshot.metadata.isFromCache} " +
                                "pendingWrites=${snapshot.metadata.hasPendingWrites()}"
                    )

                    if (change.type != DocumentChange.Type.ADDED) {
                        continue
                    }

                    try {

                        val doc = change.document

                        val spMap = doc.get("sharedPokemon") as? Map<String, Any?>
                        val sharedPokemon = spMap?.let {
                            com.example.overdex.model.SharedPokemon(
                                speciesId = (it["speciesId"] as? Long)?.toInt() ?: 0,
                                speciesName = it["speciesName"] as? String ?: "",
                                cp = (it["cp"] as? Long)?.toInt(),
                                isShadow = it["isShadow"] as? Boolean ?: false,
                                isPurified = it["isPurified"] as? Boolean ?: false,
                                isShiny = it["isShiny"] as? Boolean ?: false,
                                primaryType = it["primaryType"] as? String ?: "NORMAL",
                                secondaryType = it["secondaryType"] as? String
                            )
                        }

                        val message = ChatMessage(
                            id = UUID.fromString(doc.getString("id")),
                            senderTrainerId = doc.getString("senderTrainerId") ?: "",
                            sentAt = Instant.ofEpochMilli(
                                doc.getLong("sentAt") ?: 0L
                            ),
                            type = ChatMessageType.valueOf(
                                doc.getString("type") ?: "TEXT"
                            ),
                            text = doc.getString("text"),
                            sharedPokemon = sharedPokemon
                        )

                        Log.i(
                            "CHAT_TRANSPORT",
                            """
                            CHAT RECEIVE
                            Conversation: $conversationId
                            Sender: ${message.senderTrainerId}
                            Message: "${message.text}"
                            """.trimIndent()
                        )

                        trySend(message)

                    } catch (e: Exception) {

                        Log.e(
                            "CHAT_TRANSPORT",
                            "PARSE FAILED",
                            e
                        )
                    }
                }
            }

        awaitClose {
            Log.i(
                "CHAT_TRANSPORT",
                "Listener removed."
            )
            registration.remove()
        }
    }
}