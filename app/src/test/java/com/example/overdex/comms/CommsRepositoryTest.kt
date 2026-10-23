package com.example.overdex.comms

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class CommsRepositoryTest {
    private val alice = CommsTrainer("alice", "000000000001", "Alice")
    private val bob = CommsTrainer("bob", "000000000002", "Bob")
    private val charlie = CommsTrainer("charlie", "000000000003", "Charlie")

    private class MemoryStore : CommsStore {
        val accounts = mutableMapOf<String, CommsSnapshot>()
        var failWrites = false
        override suspend fun load(accountId: String) = accounts[accountId] ?: CommsSnapshot()
        override suspend fun save(accountId: String, snapshot: CommsSnapshot) {
            check(!failWrites) { "Disk full" }
            accounts[accountId] = snapshot
        }
    }
    private class Backend(var profile: CommsTrainer, val trainers: List<CommsTrainer>) : CommsBackend {
        val rooms = MutableStateFlow<List<CommsConversation>>(emptyList())
        val streams = mutableMapOf<String, MutableStateFlow<List<CommsMessage>>>()
        val sentIds = mutableListOf<String>()
        val received = mutableMapOf<String, CommsMessage>()
        var gate: CompletableDeferred<Unit>? = null
        var fail = false
        var failCreate = false
        override suspend fun loadProfile() = profile
        override suspend fun saveProfile(name: String, code: String) = profile.copy(displayName = name, trainerCode = code).also { profile = it }
        override suspend fun findTrainer(code: String) = trainers.first { it.trainerCode == code }
        override suspend fun loadTrainer(accountId: String) = trainers.find { it.accountId == accountId }
        override fun conversations() = rooms
        override fun messages(conversationId: String) = streams.getOrPut(conversationId) { MutableStateFlow(emptyList()) }
        override suspend fun createConversation(conversation: CommsConversation) {
            check(!failCreate)
            rooms.value = rooms.value.filterNot { it.id == conversation.id } + conversation.copy(confirmed = true)
        }
        override suspend fun send(message: CommsMessage) {
            sentIds += message.id
            gate?.await()
            check(!fail) { "Offline" }
            received.putIfAbsent(message.id, message.copy(status = DeliveryStatus.SENT, serverSentAt = message.sentAt + 10))
        }
        fun acknowledge(conversationId: String, message: CommsMessage) {
            messages(conversationId).value = messages(conversationId).value + message.copy(status = DeliveryStatus.SENT)
        }
    }
    private suspend fun CoroutineScope.withRepository(test: suspend (CommsRepository, MemoryStore, Backend) -> Unit) {
        val child = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val store = MemoryStore()
        val backend = Backend(alice, listOf(alice, bob, charlie))
        val repository = CommsRepository(store, child)
        try {
            repository.activate(alice.accountId, backend)
            test(repository, store, backend)
        } finally { child.cancel() }
    }
    private suspend fun until(predicate: () -> Boolean) {
        withTimeout(3_000) { while (!predicate()) delay(1) }
    }
    private suspend fun direct(repo: CommsRepository): String {
        repo.addContact(bob.trainerCode)
        return repo.createConversation(listOf(bob.accountId), "")
    }

    @Test fun `trainer codes preserve leading zeroes and reject impossible input`() {
        assertEquals("000012346666", TrainerCode.normalize("0000 1234 6666"))
        assertEquals("0000 1234 6666", TrainerCode.display("000012346666"))
        for (bad in listOf("123", "12345678901A", "１２３４５６７８９０１２", "1234567890123")) {
            assertThrows(IllegalArgumentException::class.java) { TrainerCode.normalize(bad) }
        }
    }
    @Test fun `direct room ID is stable in both directions and independent of rerolled code`() {
        assertEquals(CommsConversation.directId("alice", "bob"), CommsConversation.directId("bob", "alice"))
        assertNotEquals(CommsConversation.directId("alice", "bob"), CommsConversation.directId("alice", "charlie"))
    }
    @Test fun `requires account before sending or creating conversations`() = runBlocking {
        withRepository { repo, _, _ ->
            repo.activate(null, null)
            assertTrue(runCatching { repo.sendText("room", "hello") }.isFailure)
            assertTrue(runCatching { repo.createConversation(listOf("bob"), "") }.isFailure)
            assertNull(repo.state.value.accountId)
        }
    }
    @Test fun `pending is persisted before acknowledgement and sent only after acknowledgement`() = runBlocking {
        withRepository { repo, store, backend ->
            val id = direct(repo)
            backend.gate = CompletableDeferred()
            repo.sendText(id, "hello")
            assertEquals(DeliveryStatus.PENDING, repo.state.value.visibleMessages.single().status)
            assertEquals(DeliveryStatus.PENDING, store.accounts["alice"]!!.messages.single().status)
            backend.gate!!.complete(Unit)
            until { repo.state.value.visibleMessages.single().status == DeliveryStatus.SENT }
            assertEquals(DeliveryStatus.SENT, store.accounts["alice"]!!.messages.single().status)
        }
    }
    @Test fun `failed send survives restart and retry reuses the same message ID`() = runBlocking {
        withRepository { repo, store, backend ->
            val id = direct(repo)
            backend.fail = true
            repo.sendText(id, "keep me")
            until { repo.state.value.visibleMessages.single().status == DeliveryStatus.FAILED }
            val original = repo.state.value.visibleMessages.single().id
            repo.activate(null, null)
            repo.activate("alice", backend)
            repo.selectConversation(id)
            assertEquals(original, repo.state.value.visibleMessages.single().id)
            backend.fail = false
            repo.retry(original)
            until { repo.state.value.visibleMessages.single().status == DeliveryStatus.SENT }
            assertEquals(listOf(original, original), backend.sentIds)
            assertEquals(1, store.accounts["alice"]!!.messages.size)
            assertEquals(1, backend.received.size)
        }
    }
    @Test fun `process restart never treats interrupted pending as delivered or resends automatically`() = runBlocking {
        withRepository { repo, store, backend ->
            val id = direct(repo)
            val record = CommsMessage("pending-id", id, "alice", "Alice", "pending", 1)
            store.accounts["alice"] = store.accounts["alice"]!!.copy(messages = listOf(record))
            repo.activate("alice", backend)
            assertEquals(DeliveryStatus.FAILED, repo.state.value.snapshot.messages.single().status)
            assertTrue(backend.sentIds.isEmpty())
            backend.acknowledge(id, record)
            repo.selectConversation(id)
            until { repo.state.value.visibleMessages.single().status == DeliveryStatus.SENT }
        }
    }
    @Test fun `same remote message does not duplicate local outbox record`() = runBlocking {
        withRepository { repo, _, backend ->
            val id = direct(repo)
            repo.sendText(id, "once")
            until { repo.state.value.visibleMessages.single().status == DeliveryStatus.SENT }
            val record = repo.state.value.visibleMessages.single()
            backend.acknowledge(id, record)
            backend.acknowledge(id, record)
            assertEquals(1, repo.state.value.visibleMessages.size)
        }
    }
    @Test fun `two conversations keep messages and drafts separate`() = runBlocking {
        withRepository { repo, _, _ ->
            val first = direct(repo)
            repo.sendText(first, "for Bob")
            repo.saveDraft(first, "Bob draft")
            repo.addContact(charlie.trainerCode)
            val second = repo.createConversation(listOf("charlie"), "")
            assertTrue(repo.state.value.visibleMessages.isEmpty())
            repo.sendText(second, "for Charlie")
            assertEquals("for Charlie", repo.state.value.visibleMessages.single().text)
            repo.selectConversation(first)
            assertEquals("for Bob", repo.state.value.visibleMessages.single().text)
            assertEquals("Bob draft", repo.state.value.snapshot.drafts[first])
        }
    }
    @Test fun `signing out hides history and another account cannot load it`() = runBlocking {
        withRepository { repo, store, backend ->
            repo.sendText(direct(repo), "private to Alice")
            repo.activate(null, null)
            assertTrue(repo.state.value.snapshot.messages.isEmpty())
            repo.activate("charlie", Backend(charlie, listOf(alice, bob, charlie)))
            assertTrue(repo.state.value.snapshot.messages.isEmpty())
            assertEquals(1, store.accounts["alice"]!!.messages.size)
            repo.activate("alice", backend)
            assertEquals("private to Alice", repo.state.value.snapshot.messages.single().text)
        }
    }
    @Test fun `late acknowledgement from previous account never enters current account history`() = runBlocking {
        withRepository { repo, store, backend ->
            val id = direct(repo)
            backend.gate = CompletableDeferred()
            repo.sendText(id, "old account")
            repo.activate("charlie", Backend(charlie, listOf(alice, bob, charlie)))
            backend.gate!!.complete(Unit)
            delay(10)
            assertEquals("charlie", repo.state.value.accountId)
            assertTrue(repo.state.value.snapshot.messages.isEmpty())
            assertTrue(store.accounts["charlie"]!!.messages.isEmpty())
        }
    }
    @Test fun `group creation stores explicit unique members and stable ID`() = runBlocking {
        withRepository { repo, store, _ ->
            repo.addContact(bob.trainerCode); repo.addContact(charlie.trainerCode)
            val id = repo.createConversation(listOf("bob", "charlie", "bob"), "Practice coaching")
            val group = store.accounts["alice"]!!.conversations.single { it.id == id }
            assertEquals(ConversationKind.GROUP, group.kind)
            assertEquals(listOf("alice","bob","charlie"), group.memberIds)
            assertEquals("Practice coaching", group.title)
        }
    }
    @Test fun `failed room creation remains available for retry on first send`() = runBlocking {
        withRepository { repo, _, backend ->
            backend.failCreate = true
            val id = direct(repo)
            assertFalse(repo.state.value.selectedConversation!!.confirmed)
            backend.failCreate = false
            repo.sendText(id, "now online")
            until { repo.state.value.visibleMessages.single().status == DeliveryStatus.SENT }
            assertEquals(1, backend.rooms.value.size)
        }
    }
    @Test fun `reroll updates public code without changing account conversations or messages`() = runBlocking {
        withRepository { repo, _, _ ->
            val id = direct(repo)
            repo.sendText(id, "history")
            repo.saveProfile("Alice", "9999 0000 6666")
            assertEquals("999900006666", repo.state.value.snapshot.profile!!.trainerCode)
            assertEquals("alice", repo.state.value.snapshot.profile!!.accountId)
            assertEquals(id, repo.state.value.selectedConversationId)
            assertEquals("history", repo.state.value.visibleMessages.single().text)
        }
    }
    @Test fun `disk failure prevents network send and preserves composer draft`() = runBlocking {
        withRepository { repo, store, backend ->
            val id = direct(repo)
            repo.saveDraft(id, "unsaved")
            store.failWrites = true
            assertTrue(runCatching { repo.sendText(id, "unsaved") }.isFailure)
            assertTrue(backend.sentIds.isEmpty())
            assertEquals("unsaved", repo.state.value.snapshot.drafts[id])
        }
    }
    @Test fun `late delivery after timeout is reconciled as sent without duplication`() = runBlocking {
        val child = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val backend = Backend(alice, listOf(alice, bob))
        val repo = CommsRepository(MemoryStore(), child, deliveryTimeoutMillis = 20)
        try {
            repo.activate("alice", backend)
            val id = direct(repo)
            backend.gate = CompletableDeferred()
            repo.sendText(id, "slow server")
            until { repo.state.value.visibleMessages.single().status == DeliveryStatus.FAILED }
            val original = repo.state.value.visibleMessages.single()
            backend.acknowledge(id, original)
            until { repo.state.value.visibleMessages.single().status == DeliveryStatus.SENT }
            assertEquals(1, repo.state.value.visibleMessages.size)
            assertEquals(original.id, repo.state.value.visibleMessages.single().id)
        } finally { child.cancel() }
    }
    @Test fun `draft callbacks from a signed-out account cannot leak into another account`() = runBlocking {
        withRepository { repo, store, _ ->
            val id = direct(repo)
            repo.activate("charlie", Backend(charlie, listOf(alice, bob, charlie)))
            repo.saveDraft(id, "Alice private draft", expectedAccountId = "alice")
            assertTrue(store.accounts["charlie"]!!.drafts.isEmpty())
        }
    }
    @Test fun `read-only legacy history cannot be resent into a new conversation`() = runBlocking {
        withRepository { repo, store, backend ->
            val legacy = CommsConversation(CommsConversation.LEGACY_ID, "Old", ConversationKind.LEGACY, emptyList(), "alice", 1)
            store.accounts["alice"] = CommsSnapshot(profile = alice, conversations = listOf(legacy))
            repo.activate("alice", backend)
            repo.selectConversation(legacy.id)
            assertTrue(runCatching { repo.sendText(legacy.id, "should not send") }.isFailure)
            assertTrue(backend.sentIds.isEmpty())
        }
    }

}
