package com.example.overdex.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.comms.CommsTrainer
import com.example.overdex.comms.CommsState
import com.example.overdex.comms.TrainerCode
import com.example.overdex.comms.TrainerCommsServices
import com.example.overdex.tournament.*
import com.example.overdex.ui.ODXFi.ODXFiShell
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.components.TerminalPathIndicator
import com.example.overdex.ui.components.rememberTerminalKeyboardController
import com.example.overdex.ui.theme.TerminalDimGreen
import com.example.overdex.ui.theme.TerminalGreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class TournamentPage { LIST, ROOM, SETTINGS, INVITE, HISTORY, RULES, NOTE }
private enum class TournamentNote { PROBLEM, RESOLVE_A, RESOLVE_B, RESOLVE_DRAW, END_EARLY }
internal data class TournamentAction(val label: String, val enabled: Boolean = true, val run: () -> Unit)
private data class TournamentDisplayRow(val title: String, val detail: String = "", val warning: Boolean = false)
private data class TournamentTextEdit(val field: Int, val title: String)
private val TournamentWarning = Color(0xFFFFC36B)

/** Private tournament rooms. The CRT displays; the LCD and instrument controls own all actions. */
@Composable
fun TournamentScreen(
    services: TournamentServices,
    comms: TrainerCommsServices,
    viewModel: PokedexViewModel,
    onBack: () -> Unit,
    onOpenComms: () -> Unit,
    onOpenPokemonGo: () -> Unit,
) {
    val commsState by comms.repository.state.collectAsState()
    TournamentScreen(
        repository = services.repository,
        commsState = commsState,
        reconnect = services::reconnect,
        viewModel = viewModel,
        onBack = onBack,
        onOpenComms = onOpenComms,
        onOpenPokemonGo = onOpenPokemonGo,
    )
}

/** The room uses the same repository for app services and isolated device checks. */
@Composable
internal fun TournamentScreen(
    repository: TournamentRepository,
    commsState: CommsState,
    reconnect: suspend () -> Unit,
    viewModel: PokedexViewModel? = null,
    onBack: () -> Unit,
    onOpenComms: () -> Unit,
    onOpenPokemonGo: () -> Unit,
) {
    val state by repository.state.collectAsState()
    val scope = rememberCoroutineScope()
    val keyboard = rememberTerminalKeyboardController()
    val listState = rememberLazyListState()
    var page by rememberSaveable { mutableStateOf(TournamentPage.LIST) }
    var selectedRow by rememberSaveable { mutableIntStateOf(0) }
    var selectedAction by rememberSaveable { mutableIntStateOf(0) }
    var draft by remember { mutableStateOf(TournamentSettings()) }
    var editingExisting by rememberSaveable { mutableStateOf(false) }
    var selectedContacts by remember { mutableStateOf(setOf<String>()) }
    var notePurpose by remember { mutableStateOf(TournamentNote.PROBLEM) }
    var noteSeriesId by remember { mutableStateOf<String?>(null) }
    var noteBattleKey by remember { mutableStateOf<String?>(null) }
    var noteDraft by remember { mutableStateOf("") }
    var textEdit by remember { mutableStateOf<TournamentTextEdit?>(null) }
    var textDraft by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var operation by remember { mutableStateOf<Job?>(null) }
    // Account listeners can arrive in either order; never show one account through another.
    val accountId = state.accountId.takeIf { it == commsState.accountId }
    val profile = commsState.snapshot.profile?.takeIf { it.accountId == accountId }
    val contacts = commsState.snapshot.contacts.sortedBy { it.displayName.lowercase() }
    val events = state.events.sortedByDescending { it.updatedAt }
    val lobby = state.lobby
    val event = lobby?.event?.takeIf { accountId != null && accountId in it.memberIds }
    val owner = accountId != null && accountId == event?.ownerId
    val mine = event?.players?.firstOrNull { it.accountId == accountId }
    val attendance = accountId?.let { lobby?.attendance?.get(it) } ?: TournamentAttendance()
    val mySeries = event?.activeSeries?.firstOrNull { accountId in it.playerIds }
    val selectedSeries = event?.let { it.series.getOrNull(selectedRow - it.players.size) }
    val pairing = selectedSeries ?: mySeries
    val inPairing = accountId != null && pairing != null && accountId in pairing.playerIds
    val preparation = if (pairing != null && accountId != null) lobby?.preparation(pairing, accountId) else null
    val opponents = pairing?.playerIds?.filter { it != accountId }.orEmpty()
    val opponentId = opponents.firstOrNull()
    val currentCheckIn = attendance.accepted && attendance.checkedIn && attendance.settingsRevision == event?.settingsRevision
    val checkedIn = event?.let { currentEvent -> currentEvent.players.count { player ->
        lobby?.attendance?.get(player.accountId)?.let { entry ->
            entry.accepted && entry.checkedIn && entry.settingsRevision == currentEvent.settingsRevision
        } == true
    } } ?: 0

    fun work(success: String? = null, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        notice = null
        val nextOperation = scope.launch(start = CoroutineStart.LAZY) {
            try { block(); if (success != null) notice = success }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                notice = tournamentError(error)
            } finally {
                if (operation == coroutineContext[Job]) {
                    busy = false
                    operation = null
                }
            }
        }
        operation = nextOperation
        nextOperation.start()
    }
    fun changePage(value: TournamentPage) {
        page = value
        selectedRow = 0
        selectedAction = 0
        notice = null
    }
    fun openRoom(id: String) {
        work {
            repository.select(id)
            changePage(TournamentPage.ROOM)
        }
    }
    fun beginSettings(existing: Boolean) {
        editingExisting = existing
        draft = if (existing) event?.settings ?: TournamentSettings() else TournamentSettings()
        changePage(TournamentPage.SETTINGS)
    }
    fun beginText(field: Int, title: String, value: String) {
        textDraft = value
        textEdit = TournamentTextEdit(field, title)
        keyboard.open()
    }
    fun finishText() {
        val editing = textEdit ?: return
        when (editing.field) {
            0 -> draft = draft.copy(title = textDraft.trim())
            2 -> draft = draft.copy(rules = textDraft.trim())
            else -> noteDraft = textDraft.trim()
        }
        textEdit = null
        keyboard.close()
    }
    fun cancelText() { textEdit = null; keyboard.close() }
    fun inputKey(key: String) {
        textDraft = when (key) {
            "DELETE", "DEL", "⌫", "BACKSPACE", "←" -> textDraft.dropLast(1)
            "SPACE", "SPC", "␣" -> "$textDraft "
            else -> textDraft + key
        }.take(when (textEdit?.field) { 0 -> 80; 2 -> 2000; else -> 240 })
    }
    fun goBack() {
        // Leaving a view must not wait for a network acknowledgement. Cancelling this
        // wait does not rewrite a result; the server's room is observed again on return.
        operation?.cancel()
        operation = null
        busy = false
        notice = null
        when {
            keyboard.isVisible || textEdit != null -> cancelText()
            page == TournamentPage.LIST -> onBack()
            page == TournamentPage.ROOM -> {
                changePage(TournamentPage.LIST)
                // Enqueue the deselection before another room can be opened.
                // Repository selection is serialized, including listener retirement.
                scope.launch(start = CoroutineStart.UNDISPATCHED) { repository.select(null) }
            }
            page == TournamentPage.SETTINGS && !editingExisting -> changePage(TournamentPage.LIST)
            else -> changePage(TournamentPage.ROOM)
        }
    }
    fun beginNote(purpose: TournamentNote) {
        notePurpose = purpose
        noteSeriesId = pairing?.id
        noteBattleKey = pairing?.battleKey
        noteDraft = ""
        changePage(TournamentPage.NOTE)
        beginText(10, when (purpose) {
            TournamentNote.PROBLEM -> "What do you need?"
            TournamentNote.END_EARLY -> "Why is the event ending early?"
            else -> "Reason for this result correction"
        }, "")
    }
    fun chooseRow() {
        when (page) {
            TournamentPage.LIST -> events.getOrNull(selectedRow)?.let { openRoom(it.id) }
            TournamentPage.INVITE -> contacts.getOrNull(selectedRow)?.takeIf { it.accountId !in event?.memberIds.orEmpty() }?.let {
                selectedContacts = if (it.accountId in selectedContacts) selectedContacts - it.accountId else selectedContacts + it.accountId
            }
            TournamentPage.SETTINGS -> when (selectedRow) {
                0 -> beginText(0, "Tournament name", draft.title)
                1 -> draft = draft.copy(league = TournamentLeague.entries[(draft.league.ordinal + 1) % TournamentLeague.entries.size])
                2 -> beginText(2, "Additional rules", draft.rules)
                3 -> draft = draft.copy(winsPerSeries = draft.winsPerSeries % 3 + 1)
                4 -> draft = draft.copy(winsInFinal = draft.winsInFinal % 3 + 1)
            }
            else -> Unit
        }
    }
    fun report(result: BattleResultReport) {
        val series = pairing ?: return
        val player = accountId ?: return
        val value = BattlePreparation(playerId = player, battleKey = series.battleKey, ready = true, result = result)
        work("Result reported. Waiting for confirmation.") { repository.prepare(value) }
    }
    fun setReady(ready: Boolean) {
        val series = pairing ?: return
        val player = accountId ?: return
        val value = BattlePreparation(playerId = player, battleKey = series.battleKey,
            ready = ready, note = if (ready) "" else "Need a moment")
        work { repository.prepare(value) }
    }
    fun submitNote() {
        val currentEvent = event ?: return
        val series = currentEvent.series.find { it.id == noteSeriesId }
        val battleKey = noteBattleKey
        val reason = noteDraft.trim()
        if (reason.isBlank()) return
        when (notePurpose) {
            TournamentNote.PROBLEM -> {
                val player = accountId ?: return
                val key = battleKey ?: return
                work("Your pairing has been marked for attention.") {
                    repository.prepare(BattlePreparation(player, key, ready = false,
                        result = BattleResultReport.DISPUTE, note = reason))
                    changePage(TournamentPage.ROOM)
                }
            }
            TournamentNote.END_EARLY -> work {
                repository.host(TournamentHostCommand.EndEarly(reason))
                changePage(TournamentPage.ROOM)
            }
            else -> {
                val resolved = series ?: return
                val key = battleKey ?: return
                val draw = notePurpose == TournamentNote.RESOLVE_DRAW
                val winner = if (draw) null else if (notePurpose == TournamentNote.RESOLVE_A) resolved.playerA else resolved.playerB
                work {
                    repository.host(TournamentHostCommand.Resolve(resolved.id, key, winner, draw, reason))
                    changePage(TournamentPage.ROOM)
                }
            }
        }
    }

    // END on the touch keyboard is handled inside the shared keyboard before it reaches this screen.
    LaunchedEffect(keyboard.isVisible) { if (!keyboard.isVisible && textEdit != null) finishText() }
    LaunchedEffect(accountId) {
        page = if (state.selectedId != null) TournamentPage.ROOM else TournamentPage.LIST
        selectedRow = 0
        selectedContacts = emptySet()
        cancelText()
    }
    LaunchedEffect(event?.id, event?.phase, mySeries?.id) {
        if (page == TournamentPage.ROOM && mySeries != null)
            selectedRow = event.players.size + event.series.indexOf(mySeries)
    }
    BackHandler { goBack() }

    val noteTarget = event?.series?.find { it.id == noteSeriesId }
    val rows = if (accountId == null || profile == null) emptyList() else when (page) {
        TournamentPage.LIST -> events.map { item -> TournamentDisplayRow(item.settings.title,
            "${tournamentPhaseLabel(item.phase)} · ${item.players.size} trainers · ${item.completedBattleCount} battles" +
                if (item.ownerId == accountId) " · host" else "") }
        TournamentPage.SETTINGS -> listOf(
            TournamentDisplayRow("Name", draft.title.ifEmpty { "Enter a name" }),
            TournamentDisplayRow("League", tournamentLeagueLabel(draft.league)),
            TournamentDisplayRow("Additional rules", draft.rules.take(240).ifBlank { "No additional restrictions" } + if (draft.rules.length > 240) "…" else ""),
            TournamentDisplayRow("Wins to advance", "${draft.winsPerSeries}"),
            TournamentDisplayRow("Wins in final", "${draft.winsInFinal} requested; at least 3 with two trainers, 2 with three"),
        )
        TournamentPage.INVITE -> contacts.map { contact ->
            val joined = contact.accountId in event?.memberIds.orEmpty()
            TournamentDisplayRow((if (joined) "[invited] " else if (contact.accountId in selectedContacts) "[✓] " else "[ ] ") + contact.displayName,
                TrainerCode.display(contact.trainerCode))
        }
        TournamentPage.ROOM -> if (event == null) emptyList() else {
            event.players.map { player ->
                val status = lobby?.attendance?.get(player.accountId) ?: TournamentAttendance()
                TournamentDisplayRow(player.displayName + if (player.accountId == event.ownerId) " · host" else "",
                    when {
                        event.phase != TournamentPhase.LOBBY && player.accountId == event.championId -> "Tournament champion"
                        event.phase != TournamentPhase.LOBBY && player.accountId !in event.participantIds -> "Did not enter the bracket"
                        event.series.any { it.phase == SeriesPhase.COMPLETE && player.accountId in it.playerIds && it.winnerId != player.accountId } -> "Eliminated"
                        event.phase == TournamentPhase.ACTIVE -> "In the bracket"
                        status.declined -> "Declined invitation"
                        status.accepted && status.checkedIn && status.settingsRevision != event.settingsRevision -> "Rules changed · check in again"
                        status.checkedIn && status.accepted -> "Checked in"
                        status.accepted -> "Accepted · not checked in"
                        else -> "Invited · awaiting acceptance"
                    })
            } + event.series.map { series ->
                val detail = when (series.phase) {
                    SeriesPhase.BYE -> "Bye · no battle played"
                    SeriesPhase.WAITING -> "Waiting for an earlier pairing"
                    SeriesPhase.COMPLETE -> "${event.name(series.winnerId)} advances"
                    SeriesPhase.PLAYING -> "${series.wins(series.playerA)}–${series.wins(series.playerB)} · first to ${series.winsRequired}" +
                        series.playerIds.joinToString(separator = "", prefix = "") { id ->
                            val prep = lobby?.preparation(series, id)
                            "\n${event.name(id)}: " + tournamentPreparationLabel(prep)
                        }
                }
                TournamentDisplayRow("Round ${series.round} · ${event.name(series.playerA)} vs ${event.name(series.playerB)}", detail,
                    series.playerIds.any { lobby?.preparation(series, it)?.result == BattleResultReport.DISPUTE })
            }
        }
        TournamentPage.RULES -> listOf(TournamentDisplayRow("League", event?.settings?.league?.let(::tournamentLeagueLabel).orEmpty())) +
            event?.settings?.rules.orEmpty().ifBlank { "No additional restrictions." }.chunked(220).mapIndexed { index, part ->
                TournamentDisplayRow(if (index == 0) "Additional rules" else "Rules continued", part)
            }
        TournamentPage.HISTORY -> event?.series.orEmpty().flatMap { series ->
            series.battles.mapIndexed { index, battle -> TournamentDisplayRow(
                "Round ${series.round} · battle ${index + 1} · " + if (battle.draw) "Draw" else "${event?.name(battle.winnerId)} won",
                "${event?.name(series.playerA)} vs ${event?.name(series.playerB)}\n" +
                    (if (battle.basis == TournamentResultBasis.AGREED) "Both players agreed" else "Host resolved: ${battle.reason}") +
                    "\n${tournamentTime(battle.completedAt)}")
            }
        }
        TournamentPage.NOTE -> listOf(TournamentDisplayRow(when (notePurpose) {
            TournamentNote.PROBLEM -> "Report a problem"
            TournamentNote.END_EARLY -> "End tournament early"
            TournamentNote.RESOLVE_A -> "Record ${event?.name(noteTarget?.playerA)} as winner"
            TournamentNote.RESOLVE_B -> "Record ${event?.name(noteTarget?.playerB)} as winner"
            TournamentNote.RESOLVE_DRAW -> "Record a draw"
        }, noteDraft.ifBlank { "A reason is required. Select EDIT to enter it." }, warning = true))
    }
    val canUseNetwork = accountId != null && state.connected && !busy && !state.loading &&
        (page == TournamentPage.LIST || lobby?.fromCache != true)
    val activePairing = pairing?.phase == SeriesPhase.PLAYING && event?.phase == TournamentPhase.ACTIVE
    val agreement = if (activePairing && lobby != null && pairing != null)
        TournamentEngine.agreement(lobby, pairing) else null
    val resultReported = preparation?.result in listOf(BattleResultReport.WIN, BattleResultReport.LOSS, BattleResultReport.DRAW)
    val progressLabel = when {
        agreement?.draw == true -> "RECORD DRAW · REPLAY"
        agreement != null && pairing != null && pairing.wins(agreement.winnerId) + 1 >= pairing.winsRequired ->
            if (pairing.round == event?.series?.maxOfOrNull { it.round }) "FINISH TOURNAMENT" else "CONFIRM RESULT · ADVANCE"
        else -> "NEXT BATTLE"
    }
    val pairingStatus = when {
        agreement != null && owner -> "Both players agreed. Choose $progressLabel to record this battle."
        agreement != null -> "Both players agreed. Waiting for ${event?.let { it.name(it.ownerId) }} to confirm the result."
        resultReported && pairing != null && opponents.any { lobby?.preparation(pairing, it)?.result !in listOf(null, BattleResultReport.NONE) } ->
            "Results need review. Check each player's report or report a problem."
        resultReported -> "Your result is reported. Waiting for your opponent's report."
        preparation?.ready == true -> "Ready · report the result after playing"
        else -> "Mark READY before starting this battle"
    }
    val actions = when {
        accountId == null || profile == null -> listOf(
            TournamentAction("TRAINER COMMS") { onOpenComms() },
            TournamentAction("BACK") { onBack() },
        )
        page == TournamentPage.LIST -> listOf(
            TournamentAction("OPEN", events.isNotEmpty()) { chooseRow() },
            TournamentAction("NEW TOURNAMENT", canUseNetwork) { beginSettings(false) },
            TournamentAction("TRAINER COMMS") { onOpenComms() },
            TournamentAction("RECONNECT", !busy) { work { reconnect() } },
            TournamentAction("BACK") { onBack() },
        )
        page == TournamentPage.SETTINGS -> listOf(
            TournamentAction(if (selectedRow in listOf(1, 3, 4)) "CHANGE" else "EDIT") { chooseRow() },
            TournamentAction(if (editingExisting) "SAVE SETTINGS" else "CREATE ROOM", canUseNetwork && draft.title.isNotBlank()) {
                val settings = draft
                val host = profile.toTournamentPlayer()
                work {
                    if (editingExisting) repository.host(TournamentHostCommand.Settings(settings))
                    else repository.create(settings, host)
                    changePage(TournamentPage.ROOM)
                }
            },
            TournamentAction("BACK") { goBack() },
        )
        page == TournamentPage.INVITE -> listOf(
            TournamentAction("SELECT TRAINER", contacts.isNotEmpty()) { chooseRow() },
            TournamentAction("SEND INVITATIONS", canUseNetwork && selectedContacts.isNotEmpty()) {
                val invitations = contacts.filter { it.accountId in selectedContacts && it.accountId !in event?.memberIds.orEmpty() }
                work {
                    invitations.forEach { repository.host(TournamentHostCommand.Invite(it.toTournamentPlayer())) }
                    selectedContacts = emptySet()
                    changePage(TournamentPage.ROOM)
                }
            },
            TournamentAction("ADD TRAINER") { onOpenComms() },
            TournamentAction("BACK") { goBack() },
        )
        page == TournamentPage.NOTE -> listOf(
            TournamentAction("EDIT REASON") { beginText(10, "Reason", noteDraft) },
            TournamentAction("CONFIRM", canUseNetwork && (if (notePurpose == TournamentNote.PROBLEM) noteDraft.isNotBlank() else noteDraft.trim().length >= 5)) { submitNote() },
            TournamentAction("CANCEL") { goBack() },
        )
        page in listOf(TournamentPage.HISTORY, TournamentPage.RULES) -> listOf(TournamentAction("BACK TO ROOM") { changePage(TournamentPage.ROOM) })
        else -> buildList {
            if (owner && agreement != null && pairing != null) {
                add(TournamentAction(progressLabel, canUseNetwork) {
                    val id = pairing.id; val key = pairing.battleKey
                    work { repository.host(TournamentHostCommand.Finalize(id, key)) }
                })
            } else if (agreement != null && inPairing) {
                add(TournamentAction("WAITING FOR HOST", enabled = false) {})
            }
            if (event?.phase == TournamentPhase.LOBBY && mine != null) {
                if (!attendance.accepted) {
                    add(TournamentAction("ACCEPT INVITATION", canUseNetwork) {
                        val value = TournamentAttendance(accepted = true, settingsRevision = event.settingsRevision)
                        work { repository.attendance(value) }
                    })
                    add(TournamentAction("DECLINE", canUseNetwork && !owner) {
                        val value = TournamentAttendance(declined = true, settingsRevision = event.settingsRevision)
                        work("Invitation declined.") { repository.attendance(value); repository.select(null); changePage(TournamentPage.LIST) }
                    })
                } else add(TournamentAction(if (currentCheckIn) "LEAVE CHECK-IN" else "CHECK IN", canUseNetwork) {
                    val value = attendance.copy(checkedIn = !currentCheckIn, settingsRevision = event.settingsRevision)
                    work { repository.attendance(value) }
                })
            }
            if (activePairing && inPairing) {
                // READY creates a fresh preparation. Do not offer it over a result
                // that has already been reported for this same battle.
                if (!resultReported) {
                    add(TournamentAction("READY", canUseNetwork) { setReady(true) })
                    add(TournamentAction("NEED A MOMENT", canUseNetwork) { setReady(false) })
                }
                add(TournamentAction("OPEN POKÉMON GO") { onOpenPokemonGo() })
                add(TournamentAction("I WON", canUseNetwork && preparation?.ready == true) { report(BattleResultReport.WIN) })
                add(TournamentAction("OPPONENT WON", canUseNetwork && preparation?.ready == true) { report(BattleResultReport.LOSS) })
                add(TournamentAction("DRAW", canUseNetwork && preparation?.ready == true) { report(BattleResultReport.DRAW) })
                add(TournamentAction("REPORT A PROBLEM", canUseNetwork) { beginNote(TournamentNote.PROBLEM) })
            }
            if (mySeries != null && selectedSeries != mySeries) add(TournamentAction("MY PAIRING") {
                selectedRow = event.players.size + event.series.indexOf(mySeries)
            })
            if (owner && event?.phase == TournamentPhase.LOBBY) {
                add(TournamentAction("INVITE TRAINERS", canUseNetwork && event.players.size < TournamentEvent.MAX_PLAYERS) {
                    selectedContacts = emptySet(); changePage(TournamentPage.INVITE)
                })
                add(TournamentAction("SETTINGS", canUseNetwork) { beginSettings(true) })
                add(TournamentAction("START TOURNAMENT", canUseNetwork && checkedIn >= 2) { work { repository.host(TournamentHostCommand.Start) } })
            }
            if (owner && activePairing) {
                add(TournamentAction("RESOLVE: ${event?.name(pairing.playerA)}", canUseNetwork) { beginNote(TournamentNote.RESOLVE_A) })
                add(TournamentAction("RESOLVE: ${event?.name(pairing.playerB)}", canUseNetwork) { beginNote(TournamentNote.RESOLVE_B) })
                add(TournamentAction("RESOLVE: DRAW", canUseNetwork) { beginNote(TournamentNote.RESOLVE_DRAW) })
            }
            if (owner && event?.phase in listOf(TournamentPhase.LOBBY, TournamentPhase.ACTIVE))
                add(TournamentAction("END EARLY", canUseNetwork) { beginNote(TournamentNote.END_EARLY) })
            add(TournamentAction("VIEW RULES") { changePage(TournamentPage.RULES) })
            add(TournamentAction("BATTLE HISTORY") { changePage(TournamentPage.HISTORY) })
            add(TournamentAction("RECONNECT", !busy) { work { reconnect() } })
            add(TournamentAction("TOURNAMENT LIST") { goBack() })
        }
    }
    LaunchedEffect(rows.size, page) { selectedRow = selectedRow.coerceIn(0, (rows.size - 1).coerceAtLeast(0)) }
    LaunchedEffect(selectedRow, page, rows.size) { if (rows.isNotEmpty()) listState.animateScrollToItem(selectedRow) }
    LaunchedEffect(page, selectedRow, actions.map { it.label }) { selectedAction = 0 }
    fun activate() { actions.getOrNull(selectedAction)?.takeIf { it.enabled && !busy }?.run?.invoke() }

    ODXFiShell(
        viewModel = viewModel,
        showBattleOverlay = false,
        lcdEdgeToEdgeContent = true,
        keyboardController = keyboard,
        keyboardPreviewOnCrt = textEdit?.let { "${it.title}: ${textDraft}_" },
        onKeyActivated = ::inputKey,
        onUp = { if (keyboard.isFocused) keyboard.handleUp() else selectedRow = (selectedRow - 1).coerceAtLeast(0) },
        onDown = { if (keyboard.isFocused) keyboard.handleDown() else selectedRow = (selectedRow + 1).coerceAtMost((rows.size - 1).coerceAtLeast(0)) },
        onLeft = { if (keyboard.isFocused) keyboard.handleLeft() else selectedAction = (selectedAction - 1).coerceAtLeast(0) },
        onRight = { if (keyboard.isFocused) keyboard.handleRight() else selectedAction = (selectedAction + 1).coerceAtMost(actions.lastIndex) },
        onA = { if (keyboard.isFocused) keyboard.handleA(textDraft, ::inputKey) else activate() },
        onB = ::goBack,
        onSelect = { if (keyboard.isVisible) finishText() else if (!busy) chooseRow() },
        onStart = { if (keyboard.isVisible) finishText() else activate() },
        lcdContent = {
            Column(Modifier.fillMaxSize().padding(horizontal = 4.dp).testTag("tournament-lcd")) {
                if (busy || state.loading) Text("WORKING… · B back", color = TerminalGreen, fontSize = 12.sp, maxLines = 1)
                TournamentActionButtons(
                    actions = actions,
                    selectedAction = selectedAction,
                    busy = busy,
                    onSelectedAction = { selectedAction = it },
                    modifier = Modifier.weight(1f),
                )
                Text("← → actions · A choose · B back", color = TerminalDimGreen, fontSize = 11.sp, maxLines = 1)
            }
        },
    ) { _ ->
        Column(Modifier.fillMaxSize().padding(10.dp).testTag("tournament-crt")) {
            TerminalPathIndicator(path = "/battle/tournaments" + if (page == TournamentPage.LIST) "" else "/${page.name.lowercase()}")
            Text(if (page == TournamentPage.LIST) "TOURNAMENT ROOMS" else when (page) {
                TournamentPage.SETTINGS -> if (editingExisting) "TOURNAMENT SETTINGS" else "NEW TOURNAMENT"
                TournamentPage.INVITE -> "INVITE TRAINERS (${selectedContacts.size})"
                TournamentPage.HISTORY -> "BATTLE HISTORY"
                TournamentPage.RULES -> "TOURNAMENT RULES"
                TournamentPage.NOTE -> "REVIEW BEFORE CONFIRMING"
                else -> event?.settings?.title ?: "OPENING ROOM…"
            }, color = TerminalGreen, fontSize = 20.sp)
            Spacer(Modifier.height(8.dp))
            notice?.let { Text(it, color = TournamentWarning, fontSize = 13.sp) }
            state.error?.let { Text(it, color = TournamentWarning, fontSize = 13.sp) }
            if (!state.connected && accountId != null) Text("Offline · reconnect before changing this room", color = TournamentWarning, fontSize = 12.sp)
            if (lobby?.fromCache == true && page != TournamentPage.LIST) Text("Saved room · waiting for online confirmation", color = TournamentWarning, fontSize = 12.sp)
            if (page == TournamentPage.ROOM && activePairing && pairing != null) {
                Text("BATTLE ${pairing.battles.size + 1} · ${event?.name(pairing.playerA)} ${pairing.wins(pairing.playerA)}–${pairing.wins(pairing.playerB)} ${event?.name(pairing.playerB)}",
                    color = TerminalGreen, fontSize = 14.sp)
                Text(pairingStatus, color = if (agreement != null) TournamentWarning else TerminalDimGreen,
                    fontSize = 13.sp, modifier = Modifier.testTag("tournament-pairing-status"))
            }
            when {
                accountId == null -> Text("Sign in through Trainer Comms to create a private tournament or receive an invitation.", color = TerminalGreen)
                profile == null -> Text("Set your trainer name and Pokémon GO code in Trainer Comms before joining a tournament.", color = TerminalGreen)
                page == TournamentPage.LIST -> Text("One host configures the room and invites trainers. Single elimination · at least 2 trainers and 3 completed battles.", color = TerminalDimGreen, fontSize = 13.sp)
                page == TournamentPage.SETTINGS -> Text("Two-trainer finals require 3 wins; three-trainer finals require at least 2. Draws are recorded and replayed. Byes do not count as battles.", color = TerminalDimGreen, fontSize = 12.sp)
                page == TournamentPage.INVITE -> Text("Choose registered Trainer Comms contacts, then send invitations. Invitees accept and check in from their own phones.", color = TerminalDimGreen, fontSize = 12.sp)
                page == TournamentPage.NOTE -> Text(if (notePurpose == TournamentNote.END_EARLY)
                    "This closes the event without inventing unplayed battles. Existing results remain. Give a reason of 5–240 characters."
                    else if (notePurpose == TournamentNote.PROBLEM) "Your opponent and host can read this note. It clears your ready status."
                    else "Only record a battle that was actually played. Give a reason of 5–240 characters; it stays in the event history.", color = TournamentWarning, fontSize = 12.sp)
                event != null -> {
                    Text("${tournamentPhaseLabel(event.phase)} · host ${event.name(event.ownerId)} · ${tournamentLeagueLabel(event.settings.league)}",
                        color = TerminalDimGreen, fontSize = 12.sp)
                    Text("${event.completedBattleCount} completed battles" + if (event.phase == TournamentPhase.LOBBY) " · $checkedIn checked in" else "",
                        color = TerminalGreen, fontSize = 12.sp)
                    if (event.phase == TournamentPhase.LOBBY) Text("The host starts after at least two trainers check in. Pokémon GO challenges are started in Pokémon GO.", color = TerminalDimGreen, fontSize = 12.sp)
                    event.settings.rules.takeIf { it.isNotBlank() }?.let { Text(it, color = TerminalDimGreen, fontSize = 12.sp, maxLines = 4) }
                    event.championId?.let { Text("CHAMPION · ${event.name(it)}", color = TerminalGreen, fontSize = 18.sp) }
                    if (event.phase == TournamentPhase.ENDED_EARLY) Text("Ended early: ${event.endReason}", color = TournamentWarning, fontSize = 13.sp)
                    if (event.phase == TournamentPhase.ACTIVE && !activePairing) Text("Both players report each result. The host confirms the agreement before the next battle.", color = TerminalDimGreen, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            if (rows.isEmpty() && accountId != null && profile != null && !state.loading) Text(when (page) {
                TournamentPage.LIST -> "No tournament rooms yet. Create one, or ask a host to invite you."
                TournamentPage.INVITE -> "No saved trainers yet. ADD TRAINER opens Trainer Comms."
                TournamentPage.HISTORY -> "No battles have been finalized."
                else -> "Waiting for the room."
            }, color = TerminalDimGreen, fontSize = 14.sp)
            LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(rows) { index, row ->
                    Column(Modifier.fillMaxWidth().background(if (index == selectedRow) Color(0xFF143D23) else Color.Transparent)
                        .padding(8.dp).testTag("tournament-row:$index")) {
                        Text((if (index == selectedRow) "> " else "") + row.title,
                            color = if (row.warning) TournamentWarning else TerminalGreen, fontSize = 16.sp)
                        if (row.detail.isNotBlank()) Text(row.detail, color = if (row.warning) TournamentWarning else TerminalDimGreen, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/** Keep the selected LCD action in view on small screens and with large text. */
@Composable
internal fun TournamentActionButtons(
    actions: List<TournamentAction>,
    selectedAction: Int,
    busy: Boolean,
    onSelectedAction: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberLazyListState()
    LaunchedEffect(selectedAction, actions.map { it.label }) {
        if (actions.isNotEmpty()) scroll.animateScrollToItem(selectedAction.coerceIn(actions.indices))
    }
    LazyColumn(state = scroll, modifier = modifier.fillMaxWidth().testTag("tournament-actions")) {
        itemsIndexed(actions) { index, action ->
            TextButton(onClick = { onSelectedAction(index); if (action.enabled && !busy) action.run() },
                enabled = action.enabled && !busy,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                modifier = Modifier.fillMaxWidth().testTag("tournament-action:$index")) {
                Text((if (index == selectedAction) "> " else "") + action.label,
                    color = if (action.enabled && !busy) TerminalGreen else TerminalDimGreen,
                    fontSize = 14.sp, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private fun CommsTrainer.toTournamentPlayer() = TournamentPlayer(accountId, displayName, trainerCode)

internal fun tournamentLeagueLabel(league: TournamentLeague): String = when (league) {
    TournamentLeague.GREAT -> "Great League · 1,500 CP"
    TournamentLeague.ULTRA -> "Ultra League · 2,500 CP"
    TournamentLeague.MASTER -> "Master League · no CP limit"
}
internal fun tournamentPhaseLabel(phase: TournamentPhase): String = when (phase) {
    TournamentPhase.LOBBY -> "Pre-match lobby"
    TournamentPhase.ACTIVE -> "In progress"
    TournamentPhase.COMPLETED -> "Completed"
    TournamentPhase.ENDED_EARLY -> "Ended early"
}
internal fun tournamentPreparationLabel(value: BattlePreparation?): String = when {
    value == null -> "Not ready"
    value.result == BattleResultReport.DISPUTE -> "Needs attention" + value.note.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
    value.result == BattleResultReport.WIN -> "Reported a win"
    value.result == BattleResultReport.LOSS -> "Reported a loss"
    value.result == BattleResultReport.DRAW -> "Reported a draw"
    value.ready -> "Ready"
    value.note.isNotBlank() -> value.note
    else -> "Not ready"
}
private fun tournamentTime(time: Long): String = DateTimeFormatter.ofPattern("MMM d, HH:mm")
    .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(time))
private fun tournamentError(error: Exception): String {
    val cause = generateSequence<Throwable>(error) { it.cause }.last()
    return when (cause) {
        is IllegalArgumentException, is IllegalStateException -> cause.message ?: "Check the room and try again."
        is com.google.firebase.FirebaseNetworkException -> "No connection. Reconnect before changing the tournament."
        is com.google.firebase.firestore.FirebaseFirestoreException -> when (cause.code) {
            com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED -> "This action is not available for your account. Refresh the room or ask its host."
            com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE -> "The tournament service is unavailable. Reconnect and try again."
            else -> "The tournament change was not confirmed. Refresh the room before trying again."
        }
        else -> "The request was not confirmed. Reconnect and try again."
    }
}
