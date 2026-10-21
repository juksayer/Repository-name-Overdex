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
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.overdex.model.*
import com.example.overdex.ui.ODXFi.ODXFiShell
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.components.*
import com.example.overdex.ui.theme.TerminalGreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

private enum class BinderPage { LIBRARY, EDIT, TYPES, EXCLUDED_TYPES, SPECIES, EXCLUDED_SPECIES }
private data class BinderEditorRow(val label: String, val action: () -> Unit)

/** The CRT lists choices; the LCD and instrument controls own editing and activation. */
@Composable
fun BinderLibraryScreen(
    viewModel: PokedexViewModel,
    filterSettings: FilterSettings,
    onFilterSettingsChange: (FilterSettings) -> Unit,
    onOpen: (OwnedPokemonBinder) -> Unit,
    onBack: () -> Unit,
) {
    val custom by viewModel.customBinders.collectAsState()
    val library = OwnedPokemonBinder.entries + custom
    var page by remember { mutableStateOf(BinderPage.LIBRARY) }
    var selected by remember { mutableIntStateOf(0) }
    var draft by remember { mutableStateOf(OwnedPokemonBinder(UUID.randomUUID().toString(), "")) }
    var minCp by remember { mutableStateOf("") }
    var maxCp by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var inputField by remember { mutableStateOf("name") }
    val keyboard = rememberTerminalKeyboardController()
    val scope = rememberCoroutineScope()
    val query = remember { MutableStateFlow("") }
    val queryText by query.collectAsState()
    val species = remember { viewModel.createSearchFlow(query) }.collectAsLazyPagingItems()
    val speciesPage = page == BinderPage.SPECIES || page == BinderPage.EXCLUDED_SPECIES
    val typePage = page == BinderPage.TYPES || page == BinderPage.EXCLUDED_TYPES
    val listState = rememberLazyListState()

    fun changePage(next: BinderPage) { page = next; selected = 0; error = null }
    fun edit(binder: OwnedPokemonBinder? = null) {
        draft = binder ?: OwnedPokemonBinder(UUID.randomUUID().toString(), "")
        minCp = draft.rules.minCp?.toString().orEmpty()
        maxCp = draft.rules.maxCp?.toString().orEmpty()
        changePage(BinderPage.EDIT)
    }
    fun input(field: String) { inputField = field; keyboard.open() }
    fun key(key: String) {
        val old = when (inputField) { "min" -> minCp; "max" -> maxCp; "search" -> queryText; else -> draft.displayName }
        val next = when (key) { "DELETE" -> old.dropLast(1); "SPACE" -> "$old "; else -> if (key.startsWith("TYPE:")) old else old + key }
        when (inputField) {
            "min" -> if (next.length <= 5 && next.all(Char::isDigit)) minCp = next
            "max" -> if (next.length <= 5 && next.all(Char::isDigit)) maxCp = next
            "search" -> { query.value = next; selected = 0 }
            else -> if (next.length <= 48) draft = draft.copy(displayName = next)
        }
    }
    fun save() {
        if (saving) return
        val updated = draft.copy(rules = draft.rules.copy(minCp = minCp.toIntOrNull(), maxCp = maxCp.toIntOrNull()))
        saving = true
        scope.launch {
            try { viewModel.saveBinder(updated); changePage(BinderPage.LIBRARY) }
            catch (failure: Exception) { if (failure is CancellationException) throw failure; error = failure.message ?: "Unable to save binder." }
            finally { saving = false }
        }
    }
    fun back() {
        when {
            keyboard.isVisible -> keyboard.close()
            page == BinderPage.LIBRARY -> onBack()
            page == BinderPage.EDIT -> changePage(BinderPage.LIBRARY)
            else -> changePage(BinderPage.EDIT)
        }
    }
    val rules = draft.rules
    val regions = listOf<String?>(null) + RegistrationRegion.entries.filter { it != RegistrationRegion.ALL }.map { it.label }
    val editorRows = listOf(
        BinderEditorRow("NAME: ${draft.displayName.ifBlank { "Enter name" }}") { input("name") },
        BinderEditorRow("LEAGUE: ${rules.league.label}") { draft = draft.copy(rules = rules.copy(league = BinderLeague.entries[(rules.league.ordinal + 1) % BinderLeague.entries.size])) },
        BinderEditorRow("MIN CP: ${minCp.ifBlank { "Any" }}") { input("min") },
        BinderEditorRow("MAX CP: ${maxCp.ifBlank { rules.league.maxCp?.toString() ?: "Any" }}") { input("max") },
        BinderEditorRow("REGION: ${rules.region ?: "Any"}") { draft = draft.copy(rules = rules.copy(region = regions[(regions.indexOf(rules.region) + 1) % regions.size])) },
        BinderEditorRow("SHADOW: ${rules.shadow.label}") { draft = draft.copy(rules = rules.copy(shadow = BinderFlag.entries[(rules.shadow.ordinal + 1) % 3])) },
        BinderEditorRow("SHINY: ${rules.shiny.label}") { draft = draft.copy(rules = rules.copy(shiny = BinderFlag.entries[(rules.shiny.ordinal + 1) % 3])) },
        BinderEditorRow("FAVORITES: ${rules.favorite.label}") { draft = draft.copy(rules = rules.copy(favorite = BinderFlag.entries[(rules.favorite.ordinal + 1) % 3])) },
        BinderEditorRow("ALLOWED TYPES: ${rules.allowedTypes.size.takeIf { it > 0 } ?: "Any"}") { changePage(BinderPage.TYPES) },
        BinderEditorRow("EXCLUDED TYPES: ${rules.excludedTypes.size}") { changePage(BinderPage.EXCLUDED_TYPES) },
        BinderEditorRow("ALLOWED SPECIES: ${rules.allowedSpecies.size.takeIf { it > 0 } ?: "Any"}") { changePage(BinderPage.SPECIES) },
        BinderEditorRow("EXCLUDED SPECIES: ${rules.excludedSpecies.size}") { changePage(BinderPage.EXCLUDED_SPECIES) },
        BinderEditorRow("SAVE BINDER", ::save),
    )
    val count = when {
        page == BinderPage.LIBRARY -> library.size + 1
        page == BinderPage.EDIT -> editorRows.size
        typePage -> PokemonType.entries.size + 1
        else -> species.itemCount + 2
    }
    fun activate() {
        if (saving) return
        if (keyboard.isVisible) { keyboard.handleA("", ::key); return }
        when {
            page == BinderPage.LIBRARY -> if (selected == 0) edit() else library.getOrNull(selected - 1)?.let(onOpen)
            page == BinderPage.EDIT -> editorRows.getOrNull(selected)?.action?.invoke()
            typePage -> {
                val type = PokemonType.entries.getOrNull(selected)
                if (type == null) changePage(BinderPage.EDIT) else {
                    val old = if (page == BinderPage.TYPES) rules.allowedTypes else rules.excludedTypes
                    val next = if (type in old) old - type else old + type
                    draft = draft.copy(rules = if (page == BinderPage.TYPES) rules.copy(allowedTypes = next) else rules.copy(excludedTypes = next))
                }
            }
            selected == 0 -> input("search")
            selected == count - 1 -> changePage(BinderPage.EDIT)
            else -> species[selected - 1]?.let { pokemon ->
                val old = if (page == BinderPage.SPECIES) rules.allowedSpecies else rules.excludedSpecies
                val next = if (pokemon.id in old) old - pokemon.id else old + pokemon.id
                draft = draft.copy(rules = if (page == BinderPage.SPECIES) rules.copy(allowedSpecies = next) else rules.copy(excludedSpecies = next))
            }
        }
    }
    LaunchedEffect(selected, page, count) {
        selected = selected.coerceIn(0, (count - 1).coerceAtLeast(0))
        if (count > 0) listState.animateScrollToItem(selected)
    }
    val hint = when {
        saving -> "Saving binder…"
        error != null -> error!!
        page == BinderPage.LIBRARY -> "[A] Open   [+] New\n[SELECT] Edit selected binder"
        page == BinderPage.EDIT -> "Matching cards appear automatically.\n[A] Edit field · [SAVE] Save binder"
        typePage -> "Allowed: at least one selected type. Excluded: either type disqualifies. None selected means unrestricted."
        else -> "Choose species, then DONE. An exclusion takes priority."
    }
    ODXFiShell(
        viewModel = viewModel, showBattleOverlay = false,
        filterSettings = filterSettings, onFilterSettingsChange = onFilterSettingsChange,
        onUp = { if (keyboard.isVisible) keyboard.handleUp() else selected = (selected - 1).coerceAtLeast(0) },
        onDown = { if (keyboard.isVisible) keyboard.handleDown() else selected = (selected + 1).coerceAtMost(count - 1) },
        onLeft = { if (keyboard.isVisible) keyboard.handleLeft() }, onRight = { if (keyboard.isVisible) keyboard.handleRight() },
        onA = ::activate, onB = ::back,
        onSelect = {
            if (keyboard.isVisible) keyboard.handleStart()
            else if (page == BinderPage.LIBRARY) library.getOrNull(selected - 1)?.takeUnless { it.isBuiltIn }?.let { edit(it) }
            else if (speciesPage) input("search")
        },
        onStart = { if (keyboard.isVisible) keyboard.handleStart() else if (page == BinderPage.LIBRARY) edit() else if (page == BinderPage.EDIT) save() else changePage(BinderPage.EDIT) },
        startLabel = if (page == BinderPage.LIBRARY) "+" else "SAVE",
        keyboardController = keyboard, onKeyActivated = ::key,
        keyboardPrompt = when (inputField) { "min" -> "MIN CP: $minCp"; "max" -> "MAX CP: $maxCp"; "search" -> "SPECIES: $queryText"; else -> "NAME: ${draft.displayName}" },
        lcdContent = if (keyboard.isVisible) null else {{
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(4.dp)) {
                Text(hint, color = TerminalGreen, fontSize = 11.sp)
                TerminalMenuOption("SELECT", selected = false, onClick = ::activate)
                if (page == BinderPage.EDIT) TerminalMenuOption("SAVE BINDER", selected = false, onClick = ::save)
                if (page == BinderPage.LIBRARY) library.getOrNull(selected - 1)?.takeUnless { it.isBuiltIn }?.let { binder ->
                    TerminalMenuOption("EDIT ${binder.displayName}", selected = false, onClick = { edit(binder) })
                }
                TerminalMenuOption("BACK", selected = false, onClick = ::back)
            }
        }},
    ) {
        Column(Modifier.fillMaxSize()) {
            TerminalHeader(if (page == BinderPage.LIBRARY) "binders" else if (page == BinderPage.EDIT) "binder rules" else page.name.replace('_', ' '))
            LazyColumn(Modifier.fillMaxSize().padding(8.dp), state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(count) { index ->
                    val label = when {
                        page == BinderPage.LIBRARY -> if (index == 0) "+ NEW BINDER" else library[index - 1].displayName
                        page == BinderPage.EDIT -> editorRows[index].label
                        typePage -> PokemonType.entries.getOrNull(index)?.let { type ->
                            val checked = type in if (page == BinderPage.TYPES) rules.allowedTypes else rules.excludedTypes
                            "${if (checked) "[✓]" else "[ ]"} ${type.name}"
                        } ?: "DONE"
                        index == 0 -> "SEARCH: $queryText"
                        index == count - 1 -> "DONE"
                        else -> species[index - 1]?.let { pokemon ->
                            val checked = pokemon.id in if (page == BinderPage.SPECIES) rules.allowedSpecies else rules.excludedSpecies
                            "${if (checked) "[✓]" else "[ ]"} ${pokemon.name}"
                        } ?: "Loading…"
                    }
                    TerminalMenuOption(label, selected = selected == index)
                }
            }
        }
    }
}
