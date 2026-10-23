package com.example.overdex.comms

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.UUID

/** Account-scoped durable outbox. Local echoes never acknowledge delivery. */
class CommsRepository(
    private val store: CommsStore,
    private val scope: CoroutineScope,
    private val deliveryTimeoutMillis: Long = 25_000,
) {
    private val mutableState = MutableStateFlow(CommsState())
    val state = mutableState.asStateFlow()
    private val lock = Mutex()
    private val sessionLock = Mutex()
    private var generation = 0L
    private var backend: CommsBackend? = null
    private var sessionJob: Job? = null
    private var messagesJob: Job? = null
    private val sending = mutableSetOf<String>()
    private var storageReady = false

    suspend fun activate(accountId: String?, connection: CommsBackend?) = sessionLock.withLock {
        sessionJob?.cancelAndJoin()
        messagesJob?.cancelAndJoin()
        lock.withLock {
            generation++
            sending.clear()
            backend = null
            storageReady = false
            mutableState.value = CommsState(accountId = accountId, loading = accountId != null)
        }
        if (accountId == null || connection == null) return@withLock
        val token = generation
        try {
            val stored = store.load(accountId)
            check(stored.profile == null || stored.profile.accountId == accountId)
            // A process can die after the remote write. Retry uses the SAME ID, never a new send.
            val recovered = stored.copy(messages = stored.messages.map {
                if (it.status == DeliveryStatus.PENDING) it.copy(status = DeliveryStatus.FAILED,
                    failure = "Delivery was not confirmed. Open the conversation to check or retry.") else it
            })
            lock.withLock {
                store.save(accountId, recovered)
                backend = connection
                storageReady = true
                mutableState.value = CommsState(accountId, recovered)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            mutableState.value = CommsState(accountId = accountId,
                error = "Saved conversations could not be opened. The original history has been kept.")
            return@withLock
        }
        sessionJob = scope.launch {
            // Start listening even if the profile request is offline or temporarily denied.
            launch {
                try {
                    val profile = connection.loadProfile()
                    if (profile != null) change(token) { it.copy(profile = profile) }
                } catch (e: Exception) { report(token, e) }
            }
            try {
                connection.conversations().collect { remote ->
                    change(token) { old ->
                        val localOnly = old.conversations.filter { !it.confirmed || it.kind == ConversationKind.LEGACY }
                        old.copy(conversations = (localOnly.associateBy { it.id } + remote.associateBy { it.id }).values.toList())
                    }
                    lock.withLock {
                        if (token == generation) mutableState.value = state.value.copy(connected = true, error = null)
                    }
                    val known = state.value.snapshot.contacts.map { it.accountId }.toSet() + accountId
                    val missing = remote.flatMap { it.memberIds }.distinct().filterNot { it in known }
                    missing.forEach { memberId ->
                        val trainer = connection.loadTrainer(memberId)
                        if (trainer != null) change(token) { old ->
                            old.copy(contacts = old.contacts.filterNot { it.accountId == memberId } + trainer)
                        }
                    }
                }
            } catch (e: Exception) { report(token, e) }
        }
    }

    suspend fun saveProfile(name: String, rawCode: String) {
        val (token, connection) = session()
        val cleanName = name.trim()
        require(cleanName.isNotEmpty() && cleanName.length <= 40) { "Use a trainer name of 1–40 characters." }
        val profile = connection.saveProfile(cleanName, TrainerCode.normalize(rawCode))
        change(token) { it.copy(profile = profile) }
    }

    suspend fun addContact(rawCode: String) {
        val (token, connection) = session()
        val trainer = connection.findTrainer(TrainerCode.normalize(rawCode))
        require(trainer.accountId != state.value.accountId) { "That is your own trainer code." }
        change(token) { old -> old.copy(contacts = (old.contacts.filterNot { it.accountId == trainer.accountId } + trainer)) }
    }

    suspend fun createConversation(contactIds: List<String>, groupName: String): String {
        val (token, connection) = session()
        val current = state.value
        val uid = requireNotNull(current.accountId)
        requireNotNull(current.snapshot.profile) { "Set your trainer name and code first." }
        val others = contactIds.distinct().filterNot { it == uid }
        require(others.isNotEmpty() && others.size < CommsConversation.MAX_MEMBERS) { "Choose 1–7 other trainers." }
        require(others.all { id -> current.snapshot.contacts.any { it.accountId == id } }) { "Add each trainer by code first." }
        val isDirect = others.size == 1
        val title = if (isDirect) "Direct conversation" else groupName.trim()
        require(title.isNotEmpty() && title.length <= 80) { "Give the group a name of 1–80 characters." }
        val id = if (isDirect) CommsConversation.directId(uid, others.single()) else UUID.randomUUID().toString()
        val existing = current.snapshot.conversations.find { it.id == id }
        val conversation = existing ?: CommsConversation(id, title,
            if (isDirect) ConversationKind.DIRECT else ConversationKind.GROUP,
            (others + uid).sorted(), uid, System.currentTimeMillis())
        change(token) { old -> old.copy(conversations = old.conversations.filterNot { it.id == id } + conversation) }
        // Keep a failed conversation locally so a first send can retry its creation.
        try {
            connection.createConversation(conversation)
            change(token) { old -> old.copy(conversations = old.conversations.map {
                if (it.id == id) it.copy(confirmed = true) else it
            }) }
        } catch (e: Exception) { report(token, e) }
        selectConversation(id)
        return id
    }

    suspend fun selectConversation(id: String?) {
        messagesJob?.cancelAndJoin()
        val token = generation
        val connection = backend
        lock.withLock {
            require(id == null || state.value.snapshot.conversations.any { it.id == id })
            mutableState.value = state.value.copy(selectedConversationId = id)
        }
        if (id == null || id == CommsConversation.LEGACY_ID || connection == null) return
        messagesJob = scope.launch {
            try {
                connection.messages(id).collect { incoming ->
                    change(token) { old ->
                        val all = old.messages.associateBy { it.conversationId to it.id }.toMutableMap()
                        incoming.forEach { remote ->
                            val key = remote.conversationId to remote.id
                            // Only server-confirmed records enter this stream.
                            all[key] = remote.copy(status = DeliveryStatus.SENT, failure = null)
                        }
                        old.copy(messages = all.values.toList())
                    }
                }
            } catch (e: Exception) { report(token, e) }
        }
    }

    suspend fun saveDraft(conversationId: String, text: String, expectedAccountId: String? = state.value.accountId) {
        require(text.length <= MAX_TEXT_LENGTH)
        val token = lock.withLock {
            if (state.value.accountId != expectedAccountId || !storageReady) return
            generation
        }
        change(token) { old ->
            if (old.conversations.none { it.id == conversationId && it.kind != ConversationKind.LEGACY }) old
            else old.copy(drafts = old.drafts + (conversationId to text))
        }
    }

    /** Queues durably before attempting a network write; UI can clear the composer afterwards. */
    suspend fun sendText(conversationId: String, text: String) {
        val (token, _) = session()
        val current = state.value
        val profile = requireNotNull(current.snapshot.profile) { "Set your trainer name and code first." }
        val conversation = requireNotNull(current.snapshot.conversations.find { it.id == conversationId })
        require(conversation.kind != ConversationKind.LEGACY) { "Old local history is read-only." }
        val body = text.trim()
        require(body.isNotEmpty() && body.length <= MAX_TEXT_LENGTH) { "Messages can contain 1–4,000 characters." }
        val message = CommsMessage(UUID.randomUUID().toString(), conversationId, profile.accountId,
            profile.displayName, body, System.currentTimeMillis())
        change(token) { it.copy(messages = it.messages + message, drafts = it.drafts - conversationId) }
        scope.launch { safeDeliver(token, message.id) }
    }

    suspend fun retry(messageId: String) {
        val (token, _) = session()
        val message = requireNotNull(state.value.snapshot.messages.find { it.id == messageId })
        require(message.senderId == state.value.accountId && message.status == DeliveryStatus.FAILED) { "This message cannot be retried." }
        scope.launch { safeDeliver(token, messageId) }
    }

    private suspend fun safeDeliver(token: Long, id: String) {
        try { deliver(token, id) }
        catch (e: Exception) {
            if (e is CancellationException) throw e
            lock.withLock {
                if (token == generation) mutableState.value = state.value.copy(
                    error = "The delivery status could not be saved. Reopen the conversation before retrying.")
            }
        }
    }

    private suspend fun deliver(token: Long, id: String) {
        val details = lock.withLock {
            if (token != generation || !sending.add(id)) return
            val message = state.value.snapshot.messages.first { it.id == id }
            val conversation = state.value.snapshot.conversations.first { it.id == message.conversationId }
            Triple(message, conversation, requireNotNull(backend))
        }
        try {
            change(token) { old -> old.copy(messages = old.messages.map {
                if (it.id == id && it.status != DeliveryStatus.SENT) it.copy(status = DeliveryStatus.PENDING, failure = null) else it
            }) }
            withTimeout(deliveryTimeoutMillis) {
                details.third.createConversation(details.second)
                details.third.send(details.first)
            }
            change(token) { old -> old.copy(messages = old.messages.map {
                if (it.id == id) it.copy(status = DeliveryStatus.SENT, failure = null) else it
            }) }
        } catch (e: Exception) {
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            change(token) { old -> old.copy(messages = old.messages.map {
                // A listener can acknowledge a late write before the timeout handler runs.
                if (it.id == id && it.status != DeliveryStatus.SENT) it.copy(status = DeliveryStatus.FAILED,
                    failure = "Delivery not confirmed. Check your connection and retry.") else it
            }) }
        } finally { lock.withLock { if (token == generation) sending.remove(id) } }
    }

    private suspend fun session(): Pair<Long, CommsBackend> = lock.withLock {
        generation to requireNotNull(backend) { "Sign in to Trainer Comms first." }
    }
    private suspend fun change(token: Long, update: (CommsSnapshot) -> CommsSnapshot) = lock.withLock {
        if (token != generation || !storageReady) return@withLock
        val uid = state.value.accountId ?: return@withLock
        val next = update(state.value.snapshot)
        store.save(uid, next)
        mutableState.value = state.value.copy(snapshot = next)
    }
    private suspend fun report(token: Long, e: Exception) {
        if (e is CancellationException) throw e
        lock.withLock {
            if (token == generation) mutableState.value = state.value.copy(connected = false,
                error = "Trainer Comms could not sync. Check your connection or sign in again.")
        }
    }
    companion object { const val MAX_TEXT_LENGTH = 4_000 }
}
