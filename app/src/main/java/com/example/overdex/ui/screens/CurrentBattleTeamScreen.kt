package com.example.overdex.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.overdex.battle.team.CurrentBattleTeam
import com.example.overdex.battle.team.CurrentBattleTeamMember
import com.example.overdex.model.Pokemon
import com.example.overdex.ui.ODXFi.ODXFiShell
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.components.FilterSettings
import com.example.overdex.ui.components.TerminalHeader
import com.example.overdex.ui.components.TerminalMenuOption
import com.example.overdex.ui.components.rememberHandheldNavigationController
import com.example.overdex.ui.components.rememberTerminalKeyboardController
import com.example.overdex.ui.theme.TerminalGreen
import kotlinx.coroutines.flow.MutableStateFlow

private enum class CurrentTeamStep { TEAM, SPECIES, FAST_MOVE, CHARGED_MOVES }

/**
 * The short Overmon-style team builder. It records known player facts before
 * capture begins; it does not present those facts as visual observations.
 */
@Composable
fun CurrentBattleTeamScreen(
    viewModel: PokedexViewModel,
    filterSettings: FilterSettings,
    onFilterSettingsChange: (FilterSettings) -> Unit,
    onBack: () -> Unit,
    onReadyToLaunch: (() -> Unit)? = null,
) {
    val savedTeam by viewModel.currentBattleTeam.collectAsState()
    var step by remember { mutableStateOf(CurrentTeamStep.TEAM) }
    var editingSlot by remember { mutableStateOf<Int?>(null) }
    var selectedSpecies by remember { mutableStateOf<Pokemon?>(null) }
    var selectedFastMove by remember { mutableStateOf<String?>(null) }
    var selectedChargedMoves by remember { mutableStateOf(linkedSetOf<String>()) }

    val keyboard = rememberTerminalKeyboardController()
    val searchQuery = remember { MutableStateFlow("") }
    val query by searchQuery.collectAsState()
    val pokemonItems = remember(searchQuery) { viewModel.createSearchFlow(searchQuery) }
        .collectAsLazyPagingItems()

    val nav = rememberHandheldNavigationController(itemCount = {
        when (step) {
            CurrentTeamStep.TEAM -> savedTeam.members.size +
                (if (savedTeam.members.size < 3) 1 else 0) +
                (if (savedTeam.members.size == 3 && onReadyToLaunch != null) 1 else 0) +
                (if (savedTeam.members.isNotEmpty()) 1 else 0)
            CurrentTeamStep.SPECIES -> pokemonItems.itemCount + 1
            CurrentTeamStep.FAST_MOVE -> selectedSpecies?.fastMoves?.size ?: 0
            CurrentTeamStep.CHARGED_MOVES -> (selectedSpecies?.chargedMoves?.size ?: 0) + 1
        }
    })

    fun beginMember(slot: Int?) {
        editingSlot = slot
        selectedSpecies = null
        selectedFastMove = null
        selectedChargedMoves = linkedSetOf()
        searchQuery.value = ""
        step = CurrentTeamStep.SPECIES
        nav.setIndex(0)
    }

    fun saveMember() {
        val species = selectedSpecies ?: return
        val fastMove = selectedFastMove ?: return
        if (selectedChargedMoves.isEmpty()) return
        val slot = editingSlot ?: (savedTeam.members.size + 1)
        val member = CurrentBattleTeamMember(
            slot = slot,
            speciesId = species.id,
            speciesName = species.name,
            fastMoveName = fastMove,
            chargedMoveNames = selectedChargedMoves.toList()
        )
        viewModel.saveCurrentBattleTeam(
            CurrentBattleTeam((savedTeam.members.filterNot { it.slot == slot } + member).sortedBy { it.slot })
        )
        step = CurrentTeamStep.TEAM
        nav.setIndex(0)
    }

    fun activate(index: Int) {
        when (step) {
            CurrentTeamStep.TEAM -> {
                if (index < savedTeam.members.size) {
                    beginMember(savedTeam.members[index].slot)
                    return
                }
                var actionIndex = savedTeam.members.size
                if (savedTeam.members.size < 3) {
                    if (index == actionIndex) {
                        beginMember(null)
                        return
                    }
                    actionIndex++
                }
                if (savedTeam.members.size == 3 && onReadyToLaunch != null) {
                    if (index == actionIndex) {
                        onReadyToLaunch()
                        return
                    }
                    actionIndex++
                }
                if (savedTeam.members.isNotEmpty() && index == actionIndex) {
                    viewModel.clearCurrentBattleTeam()
                    nav.setIndex(0)
                }
            }
            CurrentTeamStep.SPECIES -> {
                if (index == 0) keyboard.open()
                else if (index - 1 in 0 until pokemonItems.itemCount) pokemonItems[index - 1]?.let { pokemon ->
                    selectedSpecies = pokemon
                    step = CurrentTeamStep.FAST_MOVE
                    nav.setIndex(0)
                }
            }
            CurrentTeamStep.FAST_MOVE -> selectedSpecies?.fastMoves?.getOrNull(index)?.let { move ->
                selectedFastMove = move.name
                step = CurrentTeamStep.CHARGED_MOVES
                nav.setIndex(0)
            }
            CurrentTeamStep.CHARGED_MOVES -> {
                val charged = selectedSpecies?.chargedMoves.orEmpty()
                if (index < charged.size) {
                    val name = charged[index].name
                    selectedChargedMoves = LinkedHashSet(selectedChargedMoves).apply {
                        if (!remove(name) && size < 2) add(name)
                    }
                } else if (selectedChargedMoves.isNotEmpty()) {
                    saveMember()
                }
            }
        }
    }

    fun typeKey(key: String) {
        searchQuery.value = when (key) {
            "SPACE" -> searchQuery.value + " "
            "DELETE" -> searchQuery.value.dropLast(1)
            else -> searchQuery.value + key
        }
    }

    fun backOneStep() {
        when (step) {
            CurrentTeamStep.TEAM -> onBack()
            CurrentTeamStep.SPECIES -> { step = CurrentTeamStep.TEAM; nav.setIndex(0) }
            CurrentTeamStep.FAST_MOVE -> { step = CurrentTeamStep.SPECIES; nav.setIndex(0) }
            CurrentTeamStep.CHARGED_MOVES -> { step = CurrentTeamStep.FAST_MOVE; nav.setIndex(0) }
        }
    }

    val lcdLines = when (step) {
        CurrentTeamStep.TEAM -> listOf(
            "CURRENT TEAM",
            "${savedTeam.members.size}/3 READY",
        )
        CurrentTeamStep.SPECIES -> listOf(
            "SELECT SPECIES",
            query.ifBlank { "TYPE A NAME OR BROWSE" },
        )
        CurrentTeamStep.FAST_MOVE -> moveSelectionLcdLines(
            move = selectedSpecies?.fastMoves?.getOrNull(nav.selectedIndex),
        )
        CurrentTeamStep.CHARGED_MOVES -> {
            val moves = selectedSpecies?.chargedMoves.orEmpty()
            val highlighted = moves.getOrNull(nav.selectedIndex)
            if (highlighted != null) {
                moveSelectionLcdLines(
                    move = highlighted,
                    selected = highlighted.name in selectedChargedMoves,
                )
            } else {
                listOf(
                    "ADD TO TEAM",
                    selectedSpecies?.name?.uppercase() ?: "UNKNOWN SPECIES",
                    "FAST ${selectedFastMove ?: "---"}",
                    "CHARGED ${selectedChargedMoves.size}/2",
                ) + selectedChargedMoves.map(String::uppercase)
            }
        }
    }

    ODXFiShell(
        viewModel = viewModel,
        showBattleOverlay = false,
        lcdLines = lcdLines,
        onUp = { if (keyboard.isVisible) keyboard.handleUp() else nav.moveUp() },
        onDown = { if (keyboard.isVisible) keyboard.handleDown() else nav.moveDown() },
        onLeft = { if (keyboard.isVisible) keyboard.handleLeft() },
        onRight = { if (keyboard.isVisible) keyboard.handleRight() },
        onA = {
            if (keyboard.isVisible) keyboard.handleA(query, ::typeKey)
            else activate(nav.selectedIndex)
        },
        onB = { if (keyboard.isVisible) keyboard.handleB() else backOneStep() },
        keyboardController = keyboard,
        onKeyActivated = { if (keyboard.isVisible) typeKey(it) },
        filterSettings = filterSettings,
        onFilterSettingsChange = onFilterSettingsChange
    ) {
        Column(Modifier.fillMaxSize()) {
            TerminalHeader("current team")
            when (step) {
                CurrentTeamStep.TEAM -> CurrentTeamOverview(
                    savedTeam,
                    nav.selectedIndex,
                    showLaunch = onReadyToLaunch != null,
                )
                CurrentTeamStep.SPECIES -> SpeciesSearchStep(query, pokemonItems, nav.selectedIndex) { }
                CurrentTeamStep.FAST_MOVE -> MoveSelectionStep(
                    "SELECT FAST MOVE",
                    selectedSpecies?.fastMoves.orEmpty(),
                    setOfNotNull(selectedFastMove),
                    nav.selectedIndex
                ) { }
                CurrentTeamStep.CHARGED_MOVES -> MoveSelectionStep(
                    "SELECT CHARGED MOVES (1-2)",
                    selectedSpecies?.chargedMoves.orEmpty(),
                    selectedChargedMoves,
                    nav.selectedIndex,
                    showNext = true,
                    nextLabel = "ADD TO TEAM"
                ) { }
            }
        }
    }
}

@Composable
private fun CurrentTeamOverview(
    team: CurrentBattleTeam,
    selectedIndex: Int,
    showLaunch: Boolean = false,
) {
    val addActionIndex = team.members.size.takeIf { team.members.size < 3 }
    val launchActionIndex = if (team.members.size == 3 && showLaunch) team.members.size else null
    val clearActionIndex = if (team.members.isNotEmpty()) {
        team.members.size +
            (if (addActionIndex != null) 1 else 0) +
            (if (launchActionIndex != null) 1 else 0)
    } else null

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (team.members.isEmpty()) {
            item {
                Text("NO TEAM CONFIGURED", color = TerminalGreen.copy(alpha = 0.65f), fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
            }
        }
        team.members.forEachIndexed { index, member ->
            item {
                TerminalMenuOption(
                    label = "${member.slot}. ${member.speciesName}",
                    selected = selectedIndex == index,
                    status = member.fastMoveName
                )
                Text(
                    member.chargedMoveNames.joinToString(" / "),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(start = 14.dp)
                )
            }
        }
        if (addActionIndex != null) {
            item { TerminalMenuOption("ADD POKEMON", selected = selectedIndex == addActionIndex) }
        }
        if (launchActionIndex != null) {
            item { TerminalMenuOption("LAUNCH DROIDBALL", selected = selectedIndex == launchActionIndex) }
        }
        if (clearActionIndex != null) {
            item { TerminalMenuOption("CLEAR TEAM", selected = selectedIndex == clearActionIndex) }
        }
    }
}
