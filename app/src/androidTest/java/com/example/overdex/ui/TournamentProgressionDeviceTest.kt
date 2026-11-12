package com.example.overdex.ui

import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.overdex.comms.CommsSnapshot
import com.example.overdex.comms.CommsState
import com.example.overdex.comms.CommsTrainer
import com.example.overdex.tournament.*
import com.example.overdex.ui.screens.TournamentScreen
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Actual room + physical shell controls; disposable repositories, no personal accounts/network. */
class TournamentProgressionDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val host = TournamentPlayer("host", "Host", "000000000001")
    private val guest = TournamentPlayer("guest", "Guest", "000000000002")

    private class Room(initial: TournamentLobby) {
        val state = MutableStateFlow(initial)
        val lock = Mutex()
        var prepareGate: CompletableDeferred<Unit>? = null
        var finalizations = 0
        fun backend(actor: String) = object : TournamentBackend {
            override fun events() = state.map { listOf(it.event) }
            override fun lobby(eventId: String) = state
            override suspend fun create(event: TournamentEvent) = error("Not needed in this room")
            override suspend fun attendance(eventId: String, value: TournamentAttendance) = error("Already checked in")
            override suspend fun prepare(eventId: String, value: BattlePreparation) {
                prepareGate?.await()
                lock.withLock {
                    require(value.playerId == actor)
                    require(actor in state.value.event.activeBattles[value.battleKey].orEmpty())
                    state.value = state.value.copy(preparations = state.value.preparations + (value.documentId to value))
                }
            }
            override suspend fun host(eventId: String, command: TournamentHostCommand) {
                lock.withLock {
                    val before = state.value
                    val next = TournamentEngine.apply(before, actor, command, before.event.updatedAt + 1)
                    if (command is TournamentHostCommand.Finalize) finalizations++
                    state.value = before.copy(event = next)
                }
            }
        }
    }

    private fun activeRoom(): TournamentLobby {
        val event = TournamentEvent("native-room", host.accountId, TournamentSettings(), listOf(host, guest),
            createdAt = 1, updatedAt = 1)
        val attendance = event.memberIds.associateWith { TournamentAttendance(accepted = true, checkedIn = true) }
        val lobby = TournamentLobby(event, attendance = attendance)
        return lobby.copy(event = TournamentEngine.apply(lobby, host.accountId, TournamentHostCommand.Start, 2))
    }

    private fun comms(player: TournamentPlayer) = CommsState(accountId = player.accountId,
        snapshot = CommsSnapshot(profile = CommsTrainer(player.accountId, player.trainerCode, player.displayName)), connected = true)

    private fun choose(label: String) {
        compose.onNodeWithTag("tournament-actions").performScrollToNode(hasText(label, substring = true))
        compose.onNode(hasText(label, substring = true) and hasClickAction()).performClick()
        compose.waitForIdle()
    }

    @Test fun twoPlayersCanConfirmThreeBattlesThroughTheVisibleNextAction() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val room = Room(activeRoom())
        val hostRepository = TournamentRepository(scope)
        val guestRepository = TournamentRepository(scope)
        val showingHost = mutableStateOf(true)
        try {
            runBlocking {
                hostRepository.activate(host.accountId, room.backend(host.accountId)); hostRepository.select(room.state.value.event.id)
                guestRepository.activate(guest.accountId, room.backend(guest.accountId)); guestRepository.select(room.state.value.event.id)
            }
            compose.setContent {
                key(showingHost.value) {
                    TournamentScreen(repository = if (showingHost.value) hostRepository else guestRepository,
                        commsState = comms(if (showingHost.value) host else guest), reconnect = {},
                        onBack = {}, onOpenComms = {}, onOpenPokemonGo = {})
                }
            }
            repeat(3) { index ->
                choose("READY")
                choose("I WON")
                compose.runOnIdle { showingHost.value = false }
                choose("READY")
                choose("OPPONENT WON")
                compose.onNodeWithTag("tournament-pairing-status")
                    .assertTextContains("Waiting for Host", substring = true)
                compose.onNodeWithTag("tournament-action:0").assertIsDisplayed()
                    .assertIsNotEnabled().assertTextContains("WAITING FOR HOST", substring = true)
                compose.runOnIdle { showingHost.value = true }
                // The forward action must be visible without scrolling, even with a
                // result notice. A/Start invokes this same first action.
                compose.onNodeWithTag("tournament-action:0").assertIsDisplayed().assertIsEnabled()
                    .assertTextContains(if (index == 2) "FINISH TOURNAMENT" else "NEXT BATTLE", substring = true)
                compose.onNodeWithText("READY").assertDoesNotExist()
                compose.onNodeWithTag("odxfi-button-A").performClick()
                compose.waitUntil(5_000) { room.state.value.event.completedBattleCount == index + 1 }
                compose.runOnIdle {
                    assertEquals(index + 1, room.finalizations)
                    if (index < 2) {
                        val series = room.state.value.event.activeSeries.single()
                        assertNull(room.state.value.preparation(series, host.accountId))
                        assertNull(room.state.value.preparation(series, guest.accountId))
                    }
                }
            }
            compose.runOnIdle {
                assertEquals(TournamentPhase.COMPLETED, room.state.value.event.phase)
                assertEquals(host.accountId, room.state.value.event.championId)
                assertEquals(3, room.state.value.event.completedBattleCount)
            }
            compose.onNodeWithTag("odxfi-button-B").performClick()
            compose.onNodeWithText("TOURNAMENT ROOMS").assertIsDisplayed()
            choose("OPEN")
            compose.onNodeWithText("CHAMPION · Host").assertIsDisplayed()
        } finally { scope.cancel() }
    }

    @Test fun backLeavesTheRoomAndDesktopWhileAReadinessWriteIsWaiting() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val room = Room(activeRoom()).also { it.prepareGate = CompletableDeferred() }
        val repository = TournamentRepository(scope)
        val desktopReturns = AtomicInteger()
        try {
            runBlocking { repository.activate(host.accountId, room.backend(host.accountId)); repository.select(room.state.value.event.id) }
            compose.setContent {
                TournamentScreen(repository = repository, commsState = comms(host), reconnect = {},
                    onBack = { desktopReturns.incrementAndGet() }, onOpenComms = {}, onOpenPokemonGo = {})
            }
            choose("READY")
            compose.onNodeWithText("WORKING… · B back").assertIsDisplayed()
            compose.onNodeWithTag("odxfi-button-B").performClick()
            compose.onNodeWithText("TOURNAMENT ROOMS").assertIsDisplayed()
            compose.onNodeWithTag("odxfi-button-B").performClick()
            compose.runOnIdle {
                assertEquals(1, desktopReturns.get())
                assertNull(repository.state.value.selectedId)
                assertTrue(room.state.value.preparations.isEmpty())
                room.prepareGate!!.complete(Unit)
            }
            compose.onNodeWithText("TOURNAMENT ROOMS").assertIsDisplayed()
        } finally { scope.cancel() }
    }

    @Test fun advancingARoundAutomaticallySelectsThePlayersNewPairing() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val carol = TournamentPlayer("carol", "Carol", "000000000003")
        val dan = TournamentPlayer("dan", "Dan", "000000000004")
        val first = TournamentSeries("r1s1", 1, 0, host.accountId, guest.accountId, winsRequired = 1, phase = SeriesPhase.PLAYING)
        val other = TournamentSeries("r1s2", 1, 1, carol.accountId, dan.accountId, winsRequired = 1,
            phase = SeriesPhase.COMPLETE, winnerId = carol.accountId,
            battles = listOf(TournamentBattle("r1s2b1", carol.accountId, basis = TournamentResultBasis.AGREED, completedAt = 2)))
        val final = TournamentSeries("r2s1", 2, 0, sourceA = first.id, sourceB = other.id, winsRequired = 1)
        val base = activeRoom().event.copy(players = listOf(host, guest, carol, dan),
            participantIds = listOf(host.accountId, guest.accountId, carol.accountId, dan.accountId), series = listOf(first, other, final))
        val reports = listOf(BattlePreparation(host.accountId, first.battleKey, true, BattleResultReport.WIN),
            BattlePreparation(guest.accountId, first.battleKey, true, BattleResultReport.LOSS)).associateBy { it.documentId }
        val room = Room(TournamentLobby(base, preparations = reports))
        val repository = TournamentRepository(scope)
        try {
            runBlocking { repository.activate(host.accountId, room.backend(host.accountId)); repository.select(base.id) }
            compose.setContent {
                TournamentScreen(repository = repository, commsState = comms(host), reconnect = {},
                    onBack = {}, onOpenComms = {}, onOpenPokemonGo = {})
            }
            compose.onNodeWithTag("tournament-action:0").assertIsDisplayed()
                .assertTextContains("CONFIRM RESULT · ADVANCE", substring = true)
            compose.onNodeWithTag("odxfi-button-A").performClick()
            compose.waitUntil(5_000) { room.state.value.event.activeSeries.any { it.id == final.id } }
            compose.onNodeWithTag("tournament-action:0").assertIsDisplayed().assertTextContains("READY", substring = true)
            compose.onNodeWithTag("tournament-pairing-status").assertTextContains("Mark READY", substring = true)
        } finally { scope.cancel() }
    }
}
