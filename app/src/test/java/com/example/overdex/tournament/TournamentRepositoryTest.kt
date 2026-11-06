package com.example.overdex.tournament

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class TournamentRepositoryTest {
    private val alice = TournamentPlayer("alice", "Alice", "000000000001")
    private val bob = TournamentPlayer("bob", "Bob", "000000000002")
    private fun event(id: String = "room", owner: TournamentPlayer = alice) = TournamentEvent(
        id, owner.accountId, TournamentSettings(), listOf(owner), createdAt = 10, updatedAt = 10)

    private class Backend : TournamentBackend {
        val rooms = MutableStateFlow<List<TournamentEvent>>(emptyList())
        val lobbies = mutableMapOf<String, MutableStateFlow<TournamentLobby>>()
        var eventsGate: CompletableDeferred<Unit>? = null
        val lobbyGates = mutableMapOf<String, CompletableDeferred<Unit>>()
        var gate: CompletableDeferred<Unit>? = null
        var fail: Exception? = null
        val created = mutableListOf<TournamentEvent>()
        val hostCommands = mutableListOf<Pair<String, TournamentHostCommand>>()
        val attendanceWrites = mutableListOf<TournamentAttendance>()
        val preparationWrites = mutableListOf<BattlePreparation>()
        var eventListeners = 0
        val lobbyListeners = mutableMapOf<String, Int>()
        override fun events() = flow {
            eventsGate?.await()
            rooms.collect { emit(it) }
        }.onStart { eventListeners++ }.onCompletion { eventListeners-- }
        override fun lobby(eventId: String): Flow<TournamentLobby> = flow {
            lobbyGates[eventId]?.await()
            lobbies[eventId]?.collect { emit(it) } ?: CompletableDeferred<Unit>().await()
        }.onStart { lobbyListeners[eventId] = (lobbyListeners[eventId] ?: 0) + 1 }
            .onCompletion { lobbyListeners[eventId] = (lobbyListeners[eventId] ?: 0) - 1 }
        override suspend fun create(event: TournamentEvent) {
            gate?.await()
            fail?.let { throw it }
            created += event
        }
        override suspend fun host(eventId: String, command: TournamentHostCommand) {
            gate?.await()
            fail?.let { throw it }
            hostCommands += eventId to command
        }
        override suspend fun attendance(eventId: String, value: TournamentAttendance) {
            fail?.let { throw it }
            attendanceWrites += value
        }
        override suspend fun prepare(eventId: String, value: BattlePreparation) {
            fail?.let { throw it }
            preparationWrites += value
        }
    }

    private suspend fun withRepository(timeout: Long = 25_000,
                                       test: suspend CoroutineScope.(TournamentRepository, Backend) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val backend = Backend()
        val repository = TournamentRepository(scope, timeout, now = { 100L }, newId = { "new-room" })
        try { repository.activate("alice", backend); scope.test(repository, backend) }
        finally { scope.cancel() }
    }

    private suspend fun until(predicate: () -> Boolean) = withTimeout(3_000) { while (!predicate()) delay(1) }

    @Test fun `signed out account cannot create or modify a tournament`() = runBlocking {
        withRepository { repository, backend ->
            repository.activate(null, null)
            assertTrue(runCatching { repository.create(TournamentSettings(), alice) }.isFailure)
            assertTrue(runCatching { repository.host(TournamentHostCommand.Start) }.isFailure)
            assertTrue(runCatching { repository.prepare(BattlePreparation("alice", "r1s1b1", true)) }.isFailure)
            assertTrue(backend.created.isEmpty())
            assertNull(repository.state.value.accountId)
            assertFalse(repository.state.value.connected)
        }
    }

    @Test fun `creation waits for acknowledgement without publishing a local tournament`() = runBlocking {
        withRepository { repository, backend ->
            backend.gate = CompletableDeferred()
            val creation = async { repository.create(TournamentSettings(), alice) }
            assertFalse(creation.isCompleted)
            assertTrue(repository.state.value.events.isEmpty())
            assertNull(repository.state.value.selectedId)
            backend.gate!!.complete(Unit)
            assertEquals("new-room", creation.await())
            assertEquals("new-room", repository.state.value.selectedId)
            assertTrue(repository.state.value.events.isEmpty())
            assertNull(repository.state.value.lobby)
            backend.rooms.value = backend.created.toList()
            until { repository.state.value.events.size == 1 }
            assertEquals("new-room", repository.state.value.events.single().id)
        }
    }

    @Test fun `failed create keeps remote room list unchanged and explains failure`() = runBlocking {
        withRepository { repository, backend ->
            backend.fail = IllegalStateException("Server denied this tournament")
            assertTrue(runCatching { repository.create(TournamentSettings(), alice) }.isFailure)
            assertTrue(repository.state.value.events.isEmpty())
            assertNull(repository.state.value.selectedId)
            assertEquals("Server denied this tournament", repository.state.value.error)
        }
    }

    @Test fun `account switch removes old listeners and hides old tournament immediately`() = runBlocking {
        withRepository { repository, backend ->
            val room = event()
            backend.rooms.value = listOf(room)
            backend.lobbies[room.id] = MutableStateFlow(TournamentLobby(room))
            repository.select(room.id)
            assertEquals(room, repository.state.value.lobby?.event)
            assertEquals(1, backend.eventListeners)
            assertEquals(1, backend.lobbyListeners[room.id])
            val next = Backend()
            repository.activate("bob", next)
            assertEquals("bob", repository.state.value.accountId)
            assertTrue(repository.state.value.events.isEmpty())
            assertNull(repository.state.value.lobby)
            assertNull(repository.state.value.selectedId)
            assertEquals(0, backend.eventListeners)
            assertEquals(0, backend.lobbyListeners[room.id])
            backend.rooms.value = listOf(room.copy(settings = room.settings.copy(title = "Old account")))
            assertTrue(repository.state.value.events.isEmpty())
        }
    }

    @Test fun `same account reconnect restores selected room while new account does not`() = runBlocking {
        withRepository { repository, backend ->
            val room = event()
            backend.lobbies[room.id] = MutableStateFlow(TournamentLobby(room))
            repository.select(room.id)
            val replacement = Backend().also { it.lobbies[room.id] = MutableStateFlow(TournamentLobby(room)) }
            repository.activate("alice", replacement)
            assertEquals(room.id, repository.state.value.selectedId)
            assertEquals(room, repository.state.value.lobby?.event)
            assertEquals(0, backend.lobbyListeners[room.id])
            assertEquals(1, replacement.lobbyListeners[room.id])
        }
    }

    @Test fun `completion from an old account cannot select its room on the new account`() = runBlocking {
        withRepository { repository, backend ->
            backend.gate = CompletableDeferred()
            val creation = async { runCatching { repository.create(TournamentSettings(), alice) } }
            repository.activate("bob", Backend())
            backend.gate!!.complete(Unit)
            assertTrue(creation.await().isFailure)
            assertEquals("bob", repository.state.value.accountId)
            assertNull(repository.state.value.selectedId)
            assertTrue(repository.state.value.events.isEmpty())
            assertNull(repository.state.value.error)
        }
    }

    @Test fun `selecting a different room cancels its previous listener`() = runBlocking {
        withRepository { repository, backend ->
            val first = event("first")
            val second = event("second")
            backend.lobbies[first.id] = MutableStateFlow(TournamentLobby(first))
            backend.lobbies[second.id] = MutableStateFlow(TournamentLobby(second))
            repository.select(first.id)
            repository.select(second.id)
            assertEquals(0, backend.lobbyListeners[first.id])
            assertEquals(1, backend.lobbyListeners[second.id])
            assertEquals(second, repository.state.value.lobby?.event)
            repository.select(null)
            assertNull(repository.state.value.lobby)
            assertEquals(0, backend.lobbyListeners[second.id])
        }
    }

    @Test fun `cached lobby is visible but does not claim an online connection`() = runBlocking {
        withRepository { repository, backend ->
            val room = event()
            backend.lobbies[room.id] = MutableStateFlow(TournamentLobby(room, fromCache = true))
            repository.select(room.id)
            assertEquals(room, repository.state.value.lobby?.event)
            assertFalse(repository.state.value.connected)
            backend.rooms.value = listOf(room)
            assertFalse(repository.state.value.connected)
            backend.lobbies[room.id]!!.value = TournamentLobby(room, fromCache = false)
            assertTrue(repository.state.value.connected)
        }
    }

    @Test fun `acknowledging a host write never invents a local battle result`() = runBlocking {
        withRepository { repository, backend ->
            val room = event()
            backend.lobbies[room.id] = MutableStateFlow(TournamentLobby(room))
            repository.select(room.id)
            repository.host(TournamentHostCommand.Start)
            assertEquals(room, repository.state.value.lobby?.event)
            assertEquals(0, repository.state.value.lobby?.event?.completedBattleCount)
            assertEquals(room.id to TournamentHostCommand.Start, backend.hostCommands.single())
        }
    }

    @Test fun `attendance and preparation validate account and readiness before a write`() = runBlocking {
        withRepository { repository, backend ->
            repository.select("room")
            assertTrue(runCatching { repository.attendance(TournamentAttendance(accepted = false, checkedIn = true)) }.isFailure)
            assertTrue(runCatching { repository.attendance(TournamentAttendance(accepted = true, declined = true)) }.isFailure)
            assertTrue(runCatching { repository.prepare(BattlePreparation("bob", "r1s1b1", true)) }.isFailure)
            assertTrue(runCatching { repository.prepare(BattlePreparation("alice", "r1s1b1", result = BattleResultReport.WIN)) }.isFailure)
            repository.prepare(BattlePreparation("alice", "r1s1b1", result = BattleResultReport.DISPUTE, note = "Need a moment"))
            repository.attendance(TournamentAttendance(declined = true))
            assertEquals(BattleResultReport.DISPUTE, backend.preparationWrites.single().result)
            assertTrue(backend.attendanceWrites.single().declined)
        }
    }

    @Test fun `unconfirmed timeout keeps the known lobby and warns to review before retry`() = runBlocking {
        withRepository(timeout = 10) { repository, backend ->
            val room = event()
            backend.lobbies[room.id] = MutableStateFlow(TournamentLobby(room))
            repository.select(room.id)
            backend.gate = CompletableDeferred()
            assertTrue(runCatching { repository.host(TournamentHostCommand.Start) }.isFailure)
            assertEquals(room, repository.state.value.lobby?.event)
            assertTrue(repository.state.value.error.orEmpty().contains("not confirmed"))
            assertTrue(backend.hostCommands.isEmpty())
        }
    }

    @Test fun `missing initial server list stops loading but keeps listener for later recovery`() = runBlocking {
        withRepository(timeout = 20) { repository, _ ->
            val waiting = Backend().also {
                it.eventsGate = CompletableDeferred()
                it.rooms.value = listOf(event())
            }
            repository.activate("alice", waiting)
            assertTrue(repository.state.value.loading)
            until { repository.state.value.error != null }
            assertFalse(repository.state.value.loading)
            assertFalse(repository.state.value.connected)
            assertTrue(repository.state.value.events.isEmpty())
            assertNull(repository.state.value.lobby)
            assertEquals(1, waiting.eventListeners)
            waiting.eventsGate!!.complete(Unit)
            until { repository.state.value.connected }
            assertEquals("room", repository.state.value.events.single().id)
            assertNull(repository.state.value.error)
        }
    }

    @Test fun `missing initial room stops loading and later server frame restores the same selection`() = runBlocking {
        withRepository(timeout = 20) { repository, backend ->
            val room = event()
            backend.lobbyGates[room.id] = CompletableDeferred()
            backend.lobbies[room.id] = MutableStateFlow(TournamentLobby(room))
            repository.select(room.id)
            assertTrue(repository.state.value.loading)
            until { repository.state.value.error != null }
            assertFalse(repository.state.value.loading)
            assertEquals(room.id, repository.state.value.selectedId)
            assertNull(repository.state.value.lobby)
            assertEquals(1, backend.lobbyListeners[room.id])
            backend.lobbyGates[room.id]!!.complete(Unit)
            until { repository.state.value.connected }
            assertEquals(room, repository.state.value.lobby?.event)
            assertNull(repository.state.value.error)
        }
    }

    @Test fun `room deadline preserves cached information as read only until server confirmation`() = runBlocking {
        withRepository(timeout = 20) { repository, backend ->
            val room = event()
            backend.lobbies[room.id] = MutableStateFlow(TournamentLobby(room, fromCache = true))
            repository.select(room.id)
            until { repository.state.value.error != null }
            assertEquals(room, repository.state.value.lobby?.event)
            assertTrue(repository.state.value.lobby?.fromCache == true)
            assertFalse(repository.state.value.connected)
            assertFalse(repository.state.value.loading)
            backend.lobbies[room.id]!!.value = TournamentLobby(room)
            until { repository.state.value.connected }
            assertNull(repository.state.value.error)
        }
    }

    @Test fun `old account snapshot deadline cannot overwrite a connected new account`() = runBlocking {
        withRepository(timeout = 20) { repository, _ ->
            val waiting = Backend().also { it.eventsGate = CompletableDeferred() }
            repository.activate("alice", waiting)
            val next = Backend().also { it.rooms.value = listOf(event("bob-room", bob)) }
            repository.activate("bob", next)
            delay(50)
            assertEquals("bob", repository.state.value.accountId)
            assertTrue(repository.state.value.connected)
            assertEquals("bob-room", repository.state.value.events.single().id)
            assertNull(repository.state.value.error)
            assertEquals(0, waiting.eventListeners)
        }
    }
}
