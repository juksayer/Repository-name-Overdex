package com.example.overdex.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.battle.archive.MatchArchive
import com.example.overdex.battle.history.*
import com.example.overdex.model.PokemonSize
import com.example.overdex.ui.ODXFi.ODXFiShell
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.components.*
import com.example.overdex.ui.screens.observatory.MatchReplayScreen
import com.example.overdex.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BattleRecordsScreen(viewModel: PokedexViewModel, ownedId: String? = null, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { BattleRecordStore(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var library by remember { mutableStateOf<BattleRecordLibrary?>(null) }
    var progress by remember { mutableStateOf(0 to 0) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember(ownedId) { mutableStateOf(BattleRecordQuery(ownedPokemonId = ownedId)) }
    var filters by remember { mutableStateOf(false) }
    var field by remember { mutableIntStateOf(0) }
    var selected by remember { mutableIntStateOf(0) }
    var opening by remember { mutableStateOf(false) }
    var replay by remember { mutableStateOf<MatchArchive?>(null) }
    var replayAt by remember { mutableStateOf<Long?>(null) }
    val keyboard = rememberTerminalKeyboardController()
    var replayUp by remember { mutableStateOf<() -> Unit>({}) }
    var replayDown by remember { mutableStateOf<() -> Unit>({}) }
    var replayA by remember { mutableStateOf<() -> Unit>({}) }
    var replayB by remember { mutableStateOf<() -> Unit>({}) }
    var replayLcd by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
    val list = rememberLazyListState()
    LaunchedEffect(store) {
        try { library = store.refresh { done, total -> progress = done to total } }
        catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; error = e.message }
    }
    val all = remember(library) { library?.records.orEmpty().flatMap { record -> record.record.events.map { record to it } } }
    val rows = remember(all, query) { all.filter { query.accepts(it.second) } }
    val totals = remember(all, query) { all.filter { query.copy(kind = null).accepts(it.second) } }
    LaunchedEffect(query) { selected = 0 }
    LaunchedEffect(selected, rows.size) { if (rows.isNotEmpty()) list.animateScrollToItem(selected.coerceAtMost(rows.lastIndex)) }
    val picked = rows.getOrNull(selected)
    val fieldLabels = listOf("SPECIES", "MOVE", "OPPONENT", "SHADOW ONLY", "SIZE", "EVENT")
    fun fieldValue(index: Int): String = when (index) {
        0 -> query.speciesName.orEmpty(); 1 -> query.moveName.orEmpty(); 2 -> query.opponentSpecies.orEmpty()
        3 -> if (query.shadowOnly) "YES" else "ANY"
        4 -> query.size?.label ?: "ANY"
        else -> query.kind?.name?.replace('_', ' ') ?: "ALL"
    }
    fun key(key: String) {
        val value = com.example.overdex.model.NicknameTokens.applyKey(fieldValue(field), key)
        query = when (field) { 0 -> query.copy(speciesName = value); 1 -> query.copy(moveName = value)
            2 -> query.copy(opponentSpecies = value); else -> query }
    }
    fun edit() {
        when (field) {
            in 0..2 -> { keyboard.updateLayout(LettersLayout); keyboard.open() }
            3 -> query = query.copy(shadowOnly = !query.shadowOnly)
            4 -> { val sizes = listOf(null) + PokemonSize.entries; query = query.copy(size = sizes[(sizes.indexOf(query.size) + 1) % sizes.size]) }
            5 -> { val kinds = listOf(null) + BattleMetricKind.entries; query = query.copy(kind = kinds[(kinds.indexOf(query.kind) + 1) % kinds.size]) }
        }
    }
    fun openReplay(index: Int = selected) {
        val choice = rows.getOrNull(index) ?: return
        if (opening) return
        opening = true; error = null
        scope.launch {
            try {
                replay = withContext(Dispatchers.IO) { store.readArchive(Uri.parse(choice.first.archiveUri)) }
                replayAt = choice.second.atNanos - 2_000_000_000L
            } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; error = e.message }
            finally { opening = false }
        }
    }
    fun back() {
        when { replay != null -> replay = null; keyboard.isVisible -> keyboard.handleB(); filters -> filters = false; else -> onBack() }
    }
    BackHandler { back() }
    ODXFiShell(viewModel = viewModel, showBattleOverlay = false, keyboardController = keyboard,
        keyboardPrompt = "${fieldLabels[field]}: ${fieldValue(field)}",
        onKeyActivated = ::key,
        onUp = { when { replay != null -> replayUp(); keyboard.isVisible -> keyboard.handleUp(); filters -> field = (field - 1).coerceAtLeast(0); else -> selected = (selected - 1).coerceAtLeast(0) } },
        onDown = { when { replay != null -> replayDown(); keyboard.isVisible -> keyboard.handleDown(); filters -> field = (field + 1).coerceAtMost(5); else -> selected = (selected + 1).coerceAtMost((rows.size - 1).coerceAtLeast(0)) } },
        onLeft = { if (keyboard.isVisible) keyboard.handleLeft() }, onRight = { if (keyboard.isVisible) keyboard.handleRight() },
        onA = { when { replay != null -> replayA(); keyboard.isVisible -> keyboard.handleA("") { key(it) }; filters -> edit(); else -> openReplay() } },
        onB = { if (replay != null) replayB() else back() },
        onSelect = { if (keyboard.isVisible) keyboard.handleModeSwitch() else if (replay == null) { field = 5; edit() } },
        onStart = { if (keyboard.isVisible) keyboard.handleStart() else if (replay == null) filters = !filters },
        startLabel = if (replay != null) "START" else if (filters) "DONE" else "FILTER",
        lcdContent = {
            if (replay != null) replayLcd?.invoke() else Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                if (filters) {
                    TerminalText("${fieldLabels[field]}: ${fieldValue(field).ifBlank { "ANY" }}", fontSize = 14.sp)
                    TerminalButton(text = "EDIT / CHANGE", onClick = ::edit)
                    TerminalButton(text = "SHOW RESULTS", onClick = { filters = false })
                } else {
                    TerminalText(if (opening) "READING MATCH…" else picked?.second?.let { "${it.speciesName} · ${it.kind.name.replace('_', ' ')}" } ?: "BATTLE RECORDS", fontSize = 13.sp)
                    picked?.let { (record, event) ->
                        TerminalText("${record.record.result ?: "Result not recorded"} · +${((event.atNanos - (record.record.matchStartNanos ?: event.atNanos)) / 1_000_000_000L)}s", fontSize = 11.sp)
                        if (event.kind == BattleMetricKind.KNOCKOUT) TerminalText("Credit supported by a recent hit and a witnessed faint.", fontSize = 10.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TerminalButton(text = "REPLAY", modifier = Modifier.weight(1f), onClick = { openReplay() })
                        TerminalButton(text = "FILTER", modifier = Modifier.weight(1f), onClick = { filters = true })
                    }
                    TerminalText("[↑/↓] SELECT · [SELECT] EVENT · [B] BACK", fontSize = 10.sp)
                }
            }
        }) {
        val activeReplay = replay
        if (activeReplay != null) MatchReplayScreen(activeReplay, onBack = { replay = null },
            onUp = { replayUp = it }, onDown = { replayDown = it }, onA = { replayA = it }, onB = { replayB = it },
            onLcdContentUpdate = { replayLcd = it }, initialCursorNanos = replayAt,
            resolveSpeciesId = { viewModel.getPokemonByName(it)?.id },
            resolveSpeciesTypes = { viewModel.getPokemonByName(it)?.types.orEmpty() },
            resolveFastMoves = { viewModel.getPokemonByName(it)?.fastMoves.orEmpty() },
            resolveFastMoveType = { species, move -> viewModel.getPokemonByName(species)?.fastMoves?.firstOrNull { it.name.equals(move, true) }?.type },
            resolveChargedMoveType = { species, move -> viewModel.getPokemonByName(species)?.chargedMoves?.firstOrNull { it.name.equals(move, true) }?.type },
            resolveChargedMoves = { species -> viewModel.getPokemonByName(species)?.chargedMoves.orEmpty() },
        ) else TerminalScreen {
            TerminalHeader(if (ownedId == null) "BATTLE RECORDS" else "THIS CARD'S RECORD")
            error?.let { TerminalText(it, color = TerminalPurple) }
            if (library == null && error == null) {
                TerminalText("READING MATCHES ${progress.first}/${progress.second}")
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (filters) {
                fieldLabels.forEachIndexed { index, label -> TerminalMenuOption(label, selected = index == field, status = fieldValue(index).ifBlank { "ANY" }) { field = index; edit() } }
                TerminalText("Use complete species/move names. Size is the GO size label; old unknown sizes stay unknown.", fontSize = 12.sp)
            } else {
                val counts = totals.groupingBy { it.second.kind }.eachCount()
                val matches = totals.map { it.first.record }.distinctBy { it.matchId }
                val results = matches.groupingBy { it.result ?: "UNKNOWN" }.eachCount()
                TerminalText("${matches.size} MATCHES · ${counts[BattleMetricKind.KNOCKOUT] ?: 0} SUPPORTED KOs\n" +
                    "${counts[BattleMetricKind.FAST_MOVE] ?: 0} FAST · ${counts[BattleMetricKind.CHARGED_MOVE] ?: 0} CHARGED · ${counts[BattleMetricKind.FAINT] ?: 0} FAINTS", fontSize = 12.sp)
                TerminalText("${results["WIN"] ?: 0} W · ${results["LOSS"] ?: 0} L · ${results["DRAW"] ?: 0} T · ${results["UNKNOWN"] ?: 0} UNKNOWN", fontSize = 11.sp)
                TerminalText("Recorded evidence only; missed observations are not zero activity.", color = TerminalDimGreen, fontSize = 10.sp)
                if (rows.isEmpty() && library != null) TerminalText(if (ownedId != null) "No linked events yet. Choose a team made from owned cards before recording. Older species-only matches cannot identify this individual." else "No matching recorded events.")
                if (ownedId == null && library != null) TerminalText("${library!!.records.sumOf { it.record.uncreditedOpponentFaints }} opponent faints without a credited attacker.", color = TerminalDimGreen, fontSize = 10.sp)
                if (!library?.failures.isNullOrEmpty()) TerminalText("${library!!.failures.size} match file(s) could not be read.", color = TerminalPurple, fontSize = 11.sp)
                LazyColumn(state = list, modifier = Modifier.weight(1f)) {
                    itemsIndexed(rows, key = { _, pair -> "${pair.first.record.matchId}:${pair.second.id}" }) { index, (record, event) ->
                        TerminalMenuOption(label = "${event.speciesName}: ${event.kind.name.replace('_', ' ')}${event.moveName?.let { " · $it" }.orEmpty()}",
                            selected = selected == index, status = SimpleDateFormat("MMM d HH:mm", Locale.ROOT).format(Date(record.record.recordedAt))) { selected = index; openReplay(index) }
                        event.opponentSpecies?.let { TerminalText("  vs $it", fontSize = 11.sp, color = TerminalDimGreen) }
                    }
                }
            }
        }
    }
}
