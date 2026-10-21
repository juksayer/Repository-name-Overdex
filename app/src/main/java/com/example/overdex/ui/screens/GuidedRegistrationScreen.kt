package com.example.overdex.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import com.example.overdex.model.Pokemon
import com.example.overdex.ui.ODXFi.ODXFiShell
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.components.*
import com.example.overdex.ui.theme.TerminalDimGreen
import com.example.overdex.ui.theme.TerminalGreen
import kotlinx.coroutines.flow.MutableStateFlow

/** A guided scan and its review share the same durable owned-card draft. */
@Composable
fun GuidedRegistrationScreen(
    viewModel: PokedexViewModel,
    filterSettings: FilterSettings,
    onFilterSettingsChange: (FilterSettings) -> Unit,
    onManual: () -> Unit,
    onCalibration: () -> Unit,
    onCard: (String) -> Unit,
    onBack: () -> Unit,
) {
    val controller = viewModel.guidedRegistration
    val state by controller.state.collectAsState()
    var choosingSpecies by remember { mutableStateOf(false) }
    var selected by remember { mutableIntStateOf(0) }
    val keyboard = rememberTerminalKeyboardController()
    val query = remember { MutableStateFlow("") }
    val queryText by query.collectAsState()
    val search = remember { viewModel.createSearchFlow(query) }.collectAsLazyPagingItems()
    val family = state.resolution?.candidates.orEmpty()
    val choices: List<Pokemon> = if (queryText.isBlank() && family.isNotEmpty()) family else search.itemSnapshotList.items
    val actions = buildList<Pair<String, () -> Unit>> {
        if (state.draft?.saved == true) {
            add("OPEN CARD" to { onCard(requireNotNull(state.draft).cardId) })
        } else {
            add((if (state.scanning) "STOP SCAN" else "SCAN IN POKÉMON GO") to {
                if (state.scanning) viewModel.stopGuidedRegistration() else viewModel.startGuidedRegistration()
            })
            add("CHOOSE SPECIES" to { choosingSpecies = true; selected = 0 })
            if (state.resolution?.ready == true) add("SAVE CARD" to { controller.saveCard() })
        }
        if (state.draft != null) add("NEW SCAN" to { controller.newCard(); selected = 0 })
        add("ENTER BY HAND" to { viewModel.stopGuidedRegistration(); onManual() })
        add("SCREENSHOTS / DRAGGY BOXES" to { viewModel.stopGuidedRegistration(); onCalibration() })
        add("BACK" to onBack)
    }

    fun key(value: String) {
        query.value = when (value) {
            "DELETE" -> queryText.dropLast(1)
            "SPACE" -> "$queryText "
            "#" -> queryText
            else -> queryText + value
        }
        selected = 0
    }

    fun activate() {
        when {
            keyboard.isVisible -> keyboard.handleA(queryText, ::key)
            choosingSpecies && selected == 0 -> keyboard.open()
            choosingSpecies -> choices.getOrNull(selected - 1)?.let {
                controller.confirmSpecies(it.id); choosingSpecies = false; selected = 0
            }
            !state.loading -> actions.getOrNull(selected.coerceAtMost(actions.lastIndex))?.second?.invoke()
        }
    }

    ODXFiShell(
        viewModel = viewModel, showBattleOverlay = false,
        filterSettings = filterSettings, onFilterSettingsChange = onFilterSettingsChange,
        onUp = { if (keyboard.isVisible) keyboard.handleUp() else selected = (selected - 1).coerceAtLeast(0) },
        onDown = { if (keyboard.isVisible) keyboard.handleDown() else selected = (selected + 1).coerceAtMost(if (choosingSpecies) choices.size else actions.lastIndex) },
        onLeft = { if (keyboard.isVisible) keyboard.handleLeft() },
        onRight = { if (keyboard.isVisible) keyboard.handleRight() },
        onA = ::activate,
        startLabel = if (keyboard.isVisible) "DONE" else if (choosingSpecies) "SELECT" else if (state.draft?.saved == true) "OPEN" else if (state.scanning) "STOP" else "SCAN",
        onStart = {
            when {
                keyboard.isVisible -> keyboard.close()
                choosingSpecies -> activate()
                state.draft?.saved == true -> onCard(requireNotNull(state.draft).cardId)
                state.scanning -> viewModel.stopGuidedRegistration()
                !state.loading -> viewModel.startGuidedRegistration()
            }
        },
        onB = {
            when {
                keyboard.isVisible -> keyboard.close()
                choosingSpecies -> { choosingSpecies = false; selected = 0 }
                else -> onBack()
            }
        },
        keyboardController = keyboard,
        keyboardPrompt = if (choosingSpecies) "SPECIES: $queryText" else null,
        onKeyActivated = ::key,
        lcdContent = if (keyboard.isVisible) null else {
            {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(4.dp)) {
                    if (choosingSpecies) {
                        Text("${if (selected == 0) "SEARCH" else choices.getOrNull(selected - 1)?.name.orEmpty()}\n[A] SELECT  [B] BACK", color = TerminalGreen, fontSize = 12.sp)
                    } else actions.forEachIndexed { index, (label, action) ->
                        TerminalMenuOption(label = label, selected = selected.coerceAtMost(actions.lastIndex) == index,
                            onClick = { selected = index; if (!state.loading) action() })
                    }
                }
            }
        },
    ) {
        if (choosingSpecies) {
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            LaunchedEffect(selected) { listState.animateScrollToItem(selected) }
            LazyColumn(Modifier.fillMaxSize().padding(12.dp), state = listState) {
                item { TerminalMenuOption("SEARCH: $queryText", selected = selected == 0, onClick = { keyboard.open() }) }
                items(choices.size) { index ->
                    TerminalMenuOption(choices[index].name, selected = selected == index + 1, onClick = {})
                }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TerminalHeader("scan an owned Pokémon")
                state.resolution?.species?.let { species ->
                    AsyncImage(model = species.spriteUrl, contentDescription = species.name, modifier = Modifier.size(112.dp))
                    Text(species.name, color = TerminalGreen, fontSize = 20.sp)
                }
                Text(state.message, color = TerminalGreen, fontSize = 14.sp)
                state.resolution?.let { r ->
                    Text(listOfNotNull(r.title?.let { "Name: $it" }, r.cp?.let { "CP: $it" },
                        r.candyFamilies.takeIf { it.isNotEmpty() }?.let { "Candy family: ${it.joinToString()}" },
                        r.fastMove?.let { "Fast: $it" }, r.chargedMoves.takeIf { it.isNotEmpty() }?.let { "Charged: ${it.joinToString(" / ")}" }
                    ).joinToString("\n"), color = TerminalDimGreen, fontSize = 14.sp)
                }
                state.draft?.let { draft ->
                    listOf("STATUS", "SHINY", "FAVORITE", "SIZE", "HP", "GENDER", "TYPE").forEach { field ->
                        draft.values(field).takeIf { it.isNotEmpty() }?.let { values ->
                            Text("$field: ${values.joinToString()}", color = TerminalDimGreen, fontSize = 12.sp)
                        }
                    }
                }
                Text("Use one set of boxes for the whole Pokémon detail page. Scroll each field into its box, tap the box, then confirm the reading. SAVE CARD finishes the registration when you are ready.", color = TerminalDimGreen, fontSize = 13.sp)
                Text("A nickname stays on the card. Candy identifies the family; types, evolutions and moves help identify the species. You can choose when it is unclear.", color = TerminalDimGreen, fontSize = 13.sp)
                Text("Choose Pokémon GO in Android's screen-sharing prompt. Shadow text can be read in its box. Shiny, favorite and gender icons ask for your confirmation; no icon classification is assumed.", color = TerminalDimGreen, fontSize = 12.sp)
                Text("In CARD SCAN → PROGRESS → ADJUST BOXES, drag any box into position. SAVE BOXES keeps this same layout for later scans. The guides briefly disappear for each read so they cannot cover the captured field.", color = TerminalDimGreen, fontSize = 12.sp)
                Text("LISTEN FOR CRY offers species candidates for your confirmation. Turn music off, leave sound effects on, then tap the Pokémon when listening starts. A cry helps identify species, not the individual card.", color = TerminalDimGreen, fontSize = 12.sp)
            }
        }
    }
}
