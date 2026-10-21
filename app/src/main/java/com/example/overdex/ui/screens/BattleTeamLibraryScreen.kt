package com.example.overdex.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.battle.team.*
import com.example.overdex.model.Pokemon
import com.example.overdex.ui.ODXFi.ODXFiShell
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.components.*
import com.example.overdex.ui.theme.TerminalGreen
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

private data class TeamChoice(val label: String, val team: CurrentBattleTeam?, val saved: SavedBattleTeam? = null, val owned: OwnedBattleTeamChoice? = null)

@Composable
fun BattleTeamLibraryScreen(
    viewModel: PokedexViewModel,
    filterSettings: FilterSettings,
    onFilterSettingsChange: (FilterSettings) -> Unit,
    onChosen: () -> Unit,
    onEditTeam: () -> Unit,
    onEditCard: (String) -> Unit,
    onBack: () -> Unit,
) {
    val saved by viewModel.savedBattleTeams.collectAsState()
    val owned by viewModel.ownedTeamChoices.collectAsState()
    val current by viewModel.currentBattleTeam.collectAsState()
    var species by remember { mutableStateOf<Map<Int, Pokemon>>(emptyMap()) }
    LaunchedEffect(owned) {
        species = owned.flatMap { it.members }.map { it.speciesId }.distinct().mapNotNull { id ->
            viewModel.getPokemonById(id)?.let { id to it }
        }.toMap()
    }
    val choices = saved.map { TeamChoice(it.name, it.team, saved = it) } +
        owned.map { TeamChoice("CARDS: ${it.name}", it.resolve(species), owned = it) }
    var selected by remember { mutableIntStateOf(0) }
    var lead by remember(selected) { mutableIntStateOf(0) }
    val choice = choices.getOrNull(selected)
    var editingName by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var replaceId by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val keyboard = rememberTerminalKeyboardController()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val actionBase = choices.size
    val labels = choices.map { it.label } + listOf("SAVE CURRENT AS NEW TEAM", "EDIT CURRENT TEAM", "BACK")
    fun key(key: String) {
        name = when (key) { "DELETE" -> name.dropLast(1); "SPACE" -> "$name "; else -> if (key.startsWith("TYPE:")) name else (name + key).take(48) }
    }
    fun beginSave(existing: SavedBattleTeam? = null) {
        if (current.members.size != 3) { message = "Complete all three slots and their moves before saving."; return }
        name = existing?.name.orEmpty(); replaceId = existing?.id; editingName = true; keyboard.open(); message = null
    }
    fun finishSave() {
        if (saving) return
        saving = true
        scope.launch {
            try { viewModel.saveNamedBattleTeam(name, replaceId); editingName = false; keyboard.close(); message = "Team saved." }
            catch (failure: Exception) { if (failure is CancellationException) throw failure; message = failure.message ?: "Unable to save team." }
            finally { saving = false }
        }
    }
    fun useChoice(edit: Boolean = false) {
        val team = choice?.team
        if (team == null) { message = "This team needs exactly three cards with known species and moves. Edit its cards below."; return }
        val members = team.members.sortedBy { it.slot }
        val ordered = (members.drop(lead) + members.take(lead)).mapIndexed { index, member -> member.copy(slot = index + 1) }
        try {
            viewModel.saveCurrentBattleTeam(CurrentBattleTeam(ordered))
            if (edit) onEditTeam() else onChosen()
        } catch (failure: Exception) { message = failure.message ?: "Unable to select team." }
    }
    fun activate() {
        if (keyboard.isVisible) keyboard.handleA(name, ::key)
        else if (editingName) finishSave()
        else when (selected) {
            actionBase -> beginSave()
            actionBase + 1 -> onEditTeam()
            actionBase + 2 -> onBack()
            else -> useChoice()
        }
    }
    fun back() { if (editingName) { editingName = false; keyboard.close() } else onBack() }
    LaunchedEffect(selected, labels.size) {
        selected = selected.coerceIn(0, labels.lastIndex)
        listState.animateScrollToItem(selected)
    }
    ODXFiShell(
        viewModel = viewModel, showBattleOverlay = false,
        filterSettings = filterSettings, onFilterSettingsChange = onFilterSettingsChange,
        onUp = { if (keyboard.isVisible) keyboard.handleUp() else if (!editingName) selected = (selected - 1).coerceAtLeast(0) },
        onDown = { if (keyboard.isVisible) keyboard.handleDown() else if (!editingName) selected = (selected + 1).coerceAtMost(labels.lastIndex) },
        onLeft = { if (keyboard.isVisible) keyboard.handleLeft() else if (!editingName) lead = (lead + 2) % 3 },
        onRight = { if (keyboard.isVisible) keyboard.handleRight() else if (!editingName) lead = (lead + 1) % 3 },
        onA = ::activate, onB = ::back,
        onSelect = { if (keyboard.isVisible) keyboard.close() else if (!editingName && choice != null) useChoice(edit = true) },
        onStart = { if (editingName) finishSave() else beginSave() }, startLabel = "SAVE",
        keyboardController = keyboard, keyboardPrompt = "TEAM NAME: $name", onKeyActivated = ::key,
        lcdContent = if (keyboard.isVisible) null else {{
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(4.dp)) {
                message?.let { Text(it, color = TerminalGreen, fontSize = 11.sp) }
                if (editingName) {
                    Text("TEAM: $name", color = TerminalGreen)
                    TerminalMenuOption("EDIT NAME", selected = false, onClick = { keyboard.open() })
                    TerminalMenuOption("SAVE", selected = false, onClick = ::finishSave)
                } else {
                    choice?.team?.members?.getOrNull(lead)?.let {
                        Text("LEAD: ${it.speciesName}\n[←/→] Change lead", color = TerminalGreen, fontSize = 12.sp)
                    }
                    TerminalMenuOption(if (choice != null) "USE TEAM" else labels[selected], selected = false, onClick = ::activate)
                    if (choice?.team != null) TerminalMenuOption("EDIT THIS LINEUP", selected = false, onClick = { useChoice(edit = true) })
                    choice?.saved?.let { team ->
                        TerminalMenuOption("REPLACE WITH CURRENT LINEUP", selected = false, onClick = { beginSave(team) })
                    }
                    choice?.owned?.members?.forEach { card ->
                        TerminalMenuOption("EDIT ${card.displayName ?: species[card.speciesId]?.name ?: "CARD"}", selected = false, onClick = { onEditCard(card.id) })
                    }
                }
                TerminalMenuOption("BACK", selected = false, onClick = ::back)
            }
        }},
    ) {
        Column(Modifier.fillMaxSize()) {
            TerminalHeader("saved teams")
            if (editingName) Text("${if (replaceId == null) "NEW TEAM" else "UPDATE TEAM"}\n$name\n${message.orEmpty()}", color = TerminalGreen, modifier = Modifier.padding(12.dp))
            else LazyColumn(Modifier.fillMaxSize().padding(10.dp), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(labels.size) { index ->
                    TerminalMenuOption(labels[index], selected = selected == index)
                    choices.getOrNull(index)?.let { item ->
                        Text(item.team?.members?.joinToString(" / ") { it.speciesName }
                            ?: "${item.owned?.members?.size ?: 0}/3 cards — needs complete moves",
                            color = TerminalGreen, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
