package com.example.overdex.tournament

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.UUID

/** Account-scoped room observation. It survives Activity/GO navigation, but never an account switch. */
class TournamentRepository(
    private val scope: CoroutineScope,
    private val operationTimeoutMillis: Long = 25_000,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val mutableState = MutableStateFlow(TournamentState())
    val state = mutableState.asStateFlow()
    private val lock = Mutex()
    private val sessionLock = Mutex()
    private var generation = 0L
    private var selectionGeneration = 0L
    private var backend: TournamentBackend? = null
    private var eventsJob: Job? = null
    private var lobbyJob: Job? = null

    suspend fun activate(accountId: String?, connection: TournamentBackend?) = sessionLock.withLock {
        val previous = lock.withLock {
            val selected = state.value.selectedId.takeIf { accountId != null && state.value.accountId == accountId }
            generation++
            selectionGeneration++
            backend = connection.takeIf { accountId != null }
            val jobs = eventsJob to lobbyJob
            eventsJob = null
            lobbyJob = null
            // Hide the old account before waiting for any listener to finish.
            mutableState.value = TournamentState(accountId = accountId, selectedId = selected, loading = accountId != null)
            jobs
        }
        previous.first?.cancelAndJoin()
        previous.second?.cancelAndJoin()
        if (accountId == null || connection == null) return@withLock
        val token = generation
        eventsJob = scope.launch {
            // Firestore can wait indefinitely for its first server frame while offline.
            // Report that wait without cancelling the listener, so a later connection recovers.
            val deadline = launch {
                delay(operationTimeoutMillis)
                lock.withLock {
                    if (token == generation) mutableState.value = state.value.copy(loading = false,
                        error = "The tournament list has not been confirmed online. Check your connection or reconnect.")
                }
            }
            try {
                connection.events().collect { events ->
                    lock.withLock {
                        if (token != generation) return@withLock
                        check(events.all { accountId in it.memberIds }) { "A tournament belongs to a different account." }
                        deadline.cancel()
                        val awaitingRoom = state.value.selectedId != null && state.value.lobby == null
                        val awaitingRoomConfirmation = state.value.selectedId != null && state.value.lobby?.fromCache != false
                        mutableState.value = state.value.copy(events = events,
                            loading = if (awaitingRoom) state.value.loading else false,
                            connected = !awaitingRoom && (state.value.lobby?.let { !it.fromCache } ?: true),
                            error = if (awaitingRoomConfirmation) state.value.error else null)
                    }
                }
            } catch (e: Exception) { report(token, e) }
            finally { deadline.cancel() }
        }
        state.value.selectedId?.let { startLobby(token, connection, it) }
    }

    suspend fun select(id: String?) = selectForSession(id, null)

    private suspend fun selectForSession(id: String?, expectedGeneration: Long?) = sessionLock.withLock {
        val selected = lock.withLock {
            check(expectedGeneration == null || expectedGeneration == generation) {
                "The signed-in account changed. Reopen this tournament."
            }
            require(id == null || id.matches(Regex("[A-Za-z0-9_-]{1,80}"))) { "This tournament is not valid." }
            val connection = backend
            require(id == null || connection != null) { "Sign in to Trainer Comms first." }
            selectionGeneration++
            val old = lobbyJob
            lobbyJob = null
            mutableState.value = state.value.copy(selectedId = id, lobby = null,
                loading = id != null, error = null)
            Triple(old, generation, connection)
        }
        selected.first?.cancelAndJoin()
        if (id != null) startLobby(selected.second, requireNotNull(selected.third), id)
    }

    suspend fun create(settings: TournamentSettings, host: TournamentPlayer): String {
        val session = session()
        TournamentEngine.validateSettings(settings)
        TournamentEngine.validatePlayer(host)
        require(host.accountId == session.accountId) { "Create the tournament using your signed-in trainer profile." }
        val time = now()
        val event = TournamentEvent(newId(), host.accountId,
            settings.copy(title = settings.title.trim(), rules = settings.rules.trim()), listOf(host),
            createdAt = time, updatedAt = time)
        perform(session) { it.create(event) }
        // A successful write alone does not publish an optimistic room or victory.
        selectForSession(event.id, session.token)
        return event.id
    }

    suspend fun host(command: TournamentHostCommand) {
        val session = session()
        val id = requireNotNull(state.value.selectedId) { "Open a tournament first." }
        perform(session) { it.host(id, command) }
    }

    suspend fun attendance(value: TournamentAttendance) {
        require(!(value.accepted && value.declined) && (!value.checkedIn || value.accepted)) {
            "Accept the invitation before checking in, or decline it without checking in."
        }
        val session = session()
        val id = requireNotNull(state.value.selectedId) { "Open a tournament first." }
        perform(session) { it.attendance(id, value) }
    }

    suspend fun prepare(value: BattlePreparation) {
        val session = session()
        require(value.playerId == session.accountId) { "You can only prepare or report your own battle." }
        require(value.note.length <= 240 && (value.ready || value.result in listOf(BattleResultReport.NONE, BattleResultReport.DISPUTE))) {
            "Mark yourself ready before reporting a result, and keep notes within 240 characters."
        }
        val id = requireNotNull(state.value.selectedId) { "Open a tournament first." }
        perform(session) { it.prepare(id, value) }
    }

    private fun startLobby(token: Long, connection: TournamentBackend, id: String) {
        val selection = selectionGeneration
        lobbyJob = scope.launch {
            val deadline = launch {
                delay(operationTimeoutMillis)
                lock.withLock {
                    if (token == generation && selection == selectionGeneration && state.value.selectedId == id) {
                        mutableState.value = state.value.copy(loading = false, connected = false,
                            error = "This room has not been confirmed online. Saved information is read-only; check your connection or reconnect.")
                    }
                }
            }
            try {
                connection.lobby(id).collect { incoming ->
                    lock.withLock {
                        if (token != generation || selection != selectionGeneration || state.value.selectedId != id) return@withLock
                        check(incoming.event.id == id && state.value.accountId in incoming.event.memberIds) {
                            "This tournament belongs to a different account."
                        }
                        if (!incoming.fromCache) deadline.cancel()
                        mutableState.value = state.value.copy(lobby = incoming, loading = false,
                            connected = !incoming.fromCache, error = if (incoming.fromCache) state.value.error else null)
                    }
                }
            } catch (e: Exception) {
                if (selection == selectionGeneration) report(token, e)
            } finally { deadline.cancel() }
        }
    }

    private data class Session(val token: Long, val accountId: String, val backend: TournamentBackend)
    private suspend fun session() = lock.withLock {
        Session(generation, requireNotNull(state.value.accountId) { "Sign in to Trainer Comms first." },
            requireNotNull(backend) { "Sign in to Trainer Comms first." })
    }

    private suspend fun isCurrent(token: Long) = lock.withLock { token == generation }

    private suspend fun perform(session: Session, operation: suspend (TournamentBackend) -> Unit) {
        lock.withLock {
            check(session.token == generation) { "The signed-in account changed. Reopen this tournament." }
            mutableState.value = state.value.copy(error = null)
        }
        try {
            withTimeout(operationTimeoutMillis) { operation(session.backend) }
            check(isCurrent(session.token)) { "The signed-in account changed. Reopen this tournament." }
        } catch (e: Exception) {
            if (e is CancellationException && e !is TimeoutCancellationException) throw e
            val message = if (e is TimeoutCancellationException)
                "The server has not confirmed this change. Reconnect and review the room before trying again."
            else e.message?.takeIf { it.isNotBlank() } ?: "Tournament sync failed. Check your connection and reconnect."
            lock.withLock {
                if (session.token == generation) mutableState.value = state.value.copy(error = message)
            }
            throw e
        }
    }

    private suspend fun report(token: Long, error: Exception) {
        if (error is CancellationException) throw error
        lock.withLock {
            if (token == generation) mutableState.value = state.value.copy(loading = false, connected = false,
                error = error.message?.takeIf { it.isNotBlank() } ?: "Tournament sync failed. Check your connection and reconnect.")
        }
    }
}
