package com.example.overdex.ui.screens

import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.overdex.model.*
import com.example.overdex.model.navigation.*
import com.example.overdex.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val ADD_PATH = "/BATTLE/Add Pokémon"
private val DraftSaver = Saver<OwnedPokemonDraft, String>(
    save = { Json.encodeToString(it) }, restore = { Json.decodeFromString<OwnedPokemonDraft>(it) },
)

/** Inputs consumed here stay inside the directory; unconsumed inputs keep normal tree behavior. */
class DirectoryOwnedPokemonEditor(
    val nodes: List<FlattenedNode>,
    val selectedPath: String,
    val keyboard: TerminalKeyboardController,
    val keyboardPrompt: String?,
    val lcdLines: List<String>,
    val activate: (OwnedPokemonField) -> Unit,
    val up: () -> Boolean,
    val down: () -> Boolean,
    val left: () -> Boolean,
    val right: () -> Boolean,
    val confirm: () -> Boolean,
    val back: () -> Boolean,
    val start: () -> Boolean,
    val select: () -> Boolean,
    val key: (String) -> Unit,
    val hold: (Int) -> Unit,
    val chooseNode: (FlattenedNode) -> Boolean,
)

private data class CardChoice(val label: String, val species: Pokemon? = null, val move: Move? = null)

@Composable
fun rememberDirectoryOwnedPokemonEditor(
    tree: TreeState,
    searchSpecies: (Flow<String>, Flow<IntRange>) -> Flow<PagingData<Pokemon>>,
    loadSpecies: suspend (Int) -> Pokemon?,
    savePokemon: suspend (OwnedPokemon) -> Unit,
    onFinished: () -> Unit,
): DirectoryOwnedPokemonEditor {
    var draft by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf(OwnedPokemonDraft()) }
    var editing by remember { mutableStateOf<OwnedPokemonField?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var choiceIndex by remember { mutableIntStateOf(0) }
    var heldDirection by remember { mutableIntStateOf(0) }
    val keyboard = rememberTerminalKeyboardController(keepVisibleAfterInput = true)
    val query = remember { MutableStateFlow("") }
    val queryText by query.collectAsState()
    val region = remember { MutableStateFlow(RegistrationRegion.ALL) }
    val regionValue by region.collectAsState()
    val searchFlow = remember { searchSpecies(query, region.map { it.dexRange }) }
    val emptySearch = remember { flowOf(PagingData.empty<Pokemon>()) }
    val search = (if (editing == OwnedPokemonField.SPECIES) searchFlow else emptySearch).collectAsLazyPagingItems()
    val species by produceState<Pokemon?>(null, draft.speciesId) {
        value = null
        value = draft.speciesId?.let { loadSpecies(it) }
    }
    val scope = rememberCoroutineScope()

    val choices = when (editing) {
        OwnedPokemonField.SPECIES -> search.itemSnapshotList.items.map { CardChoice("${it.formattedId} ${it.name}", species = it) }
        OwnedPokemonField.FAST_MOVE -> listOf(CardChoice("Not recorded")) + species?.fastMoves.orEmpty()
            .distinctBy { it.name }.map { CardChoice(it.name, move = it) }
        OwnedPokemonField.CHARGED_MOVE_1, OwnedPokemonField.CHARGED_MOVE_2 -> {
            val other = if (editing == OwnedPokemonField.CHARGED_MOVE_1) draft.chargedMove2 else draft.chargedMove1
            listOf(CardChoice("Not recorded")) + species?.chargedMoves.orEmpty().distinctBy { it.name }
                .filter { it.name != other }.map { CardChoice(it.name, move = it) }
        }
        else -> emptyList()
    }
    val isPicker = editing in setOf(OwnedPokemonField.SPECIES, OwnedPokemonField.FAST_MOVE,
        OwnedPokemonField.CHARGED_MOVE_1, OwnedPokemonField.CHARGED_MOVE_2)
    val isText = editing in setOf(OwnedPokemonField.SPECIES, OwnedPokemonField.NICKNAME,
        OwnedPokemonField.CP, OwnedPokemonField.TEAMS)
    val inBranch = tree.selectedPath == ADD_PATH || tree.selectedPath.startsWith("$ADD_PATH/")

    fun finishField() {
        editing = null
        keyboard.close()
        heldDirection = 0
    }

    fun save() {
        if (saving) return
        draft.validationError?.let { notice = it; return }
        val card = draft.toOwnedPokemon()
        val name = draft.nickname.ifBlank { draft.speciesName }
        saving = true
        notice = null
        scope.launch {
            try {
                savePokemon(card)
                finishField()
                draft = OwnedPokemonDraft()
                notice = "$name saved to owned Pokémon."
                onFinished()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                notice = "Could not save. Your entries are still here; select Save Card to retry."
            } finally {
                saving = false
            }
        }
    }

    fun activate(field: OwnedPokemonField) {
        if (saving) return
        notice = null
        finishField()
        when (field) {
            OwnedPokemonField.SAVE -> save()
            OwnedPokemonField.CANCEL -> {
                draft = OwnedPokemonDraft()
                notice = "Card cancelled."
                onFinished()
            }
            OwnedPokemonField.SHADOW -> draft = draft.copy(shadow = !draft.shadow, purified = false)
            OwnedPokemonField.PURIFIED -> draft = draft.copy(purified = !draft.purified, shadow = false)
            OwnedPokemonField.SHINY -> draft = draft.copy(shiny = !draft.shiny)
            OwnedPokemonField.SIZE -> draft = draft.copy(size = PokemonSize.entries[(draft.size.ordinal + 1) % PokemonSize.entries.size])
            else -> {
                if (field in setOf(OwnedPokemonField.FAST_MOVE, OwnedPokemonField.CHARGED_MOVE_1,
                        OwnedPokemonField.CHARGED_MOVE_2) && draft.speciesId == null) {
                    notice = "Choose a species before selecting moves."
                    return
                }
                editing = field
                choiceIndex = 0
                if (field in setOf(OwnedPokemonField.NICKNAME, OwnedPokemonField.CP, OwnedPokemonField.TEAMS)) {
                    keyboard.updateLayout(if (field == OwnedPokemonField.CP) listOf(
                        listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("DELETE", "0", "END"),
                    ) else LettersLayout)
                    keyboard.open()
                }
            }
        }
    }

    fun choose(index: Int) {
        if (saving || (editing == OwnedPokemonField.SPECIES && search.loadState.refresh is LoadState.Loading)) return
        if (editing != OwnedPokemonField.SPECIES && species == null) return
        val choice = choices.getOrNull(index) ?: return
        draft = when (editing) {
            OwnedPokemonField.SPECIES -> choice.species?.let(draft::selectSpecies) ?: draft
            OwnedPokemonField.FAST_MOVE -> draft.copy(fastMove = choice.move?.name)
            OwnedPokemonField.CHARGED_MOVE_1 -> draft.copy(chargedMove1 = choice.move?.name)
            OwnedPokemonField.CHARGED_MOVE_2 -> draft.copy(chargedMove2 = choice.move?.name)
            else -> draft
        }
        finishField()
    }

    fun moveChoice(direction: Int, steps: Int = 1) {
        choiceIndex = (choiceIndex + direction * steps).coerceIn(0, (choices.size - 1).coerceAtLeast(0))
        // Access requests the next page before accelerated browsing reaches its boundary.
        if (editing == OwnedPokemonField.SPECIES && choiceIndex < search.itemCount) search[choiceIndex]
    }

    LaunchedEffect(queryText, regionValue) { choiceIndex = 0; heldDirection = 0 }
    LaunchedEffect(choices.size) { choiceIndex = choiceIndex.coerceAtMost((choices.size - 1).coerceAtLeast(0)) }
    LaunchedEffect(editing, species) {
        val selectedMove = when (editing) {
            OwnedPokemonField.FAST_MOVE -> draft.fastMove
            OwnedPokemonField.CHARGED_MOVE_1 -> draft.chargedMove1
            OwnedPokemonField.CHARGED_MOVE_2 -> draft.chargedMove2
            else -> return@LaunchedEffect
        }
        choiceIndex = choices.indexOfFirst { it.move?.name == selectedMove }.coerceAtLeast(0)
    }
    val moveChoiceNow by rememberUpdatedState<(Int, Int) -> Unit>(::moveChoice)
    LaunchedEffect(heldDirection, editing, keyboard.isFocused) {
        if (heldDirection == 0 || !isPicker || keyboard.isFocused) return@LaunchedEffect
        val began = SystemClock.uptimeMillis()
        while (true) {
            val elapsed = SystemClock.uptimeMillis() - began
            moveChoiceNow(heldDirection, SpeciesBrowserNavigation.repeatStep(elapsed))
            delay(SpeciesBrowserNavigation.repeatDelayMillis(elapsed))
        }
    }

    fun key(key: String) {
        if (saving) return
        when (editing) {
            OwnedPokemonField.SPECIES -> if (!key.startsWith("TYPE:")) query.value = NicknameTokens.applyKey(query.value, key)
            OwnedPokemonField.NICKNAME -> draft = draft.copy(nickname = NicknameTokens.applyKey(draft.nickname, key))
            OwnedPokemonField.CP -> draft = draft.copy(cp = when {
                key == "DELETE" -> draft.cp.dropLast(1)
                key.length == 1 && key[0].isDigit() && draft.cp.length < 5 -> draft.cp + key
                else -> draft.cp
            })
            OwnedPokemonField.TEAMS -> if (!key.startsWith("TYPE:")) draft = draft.copy(teams = NicknameTokens.applyKey(draft.teams, key))
            else -> Unit
        }
    }

    val optionPrefix = "$ADD_PATH/${editing?.label}/choice/"
    val nodes = tree.visibleNodes.flatMap { row ->
        val field = ((row.node as? ActionNode)?.command as? InstrumentCommand.EditNewOwnedPokemon)?.field
        if (field == null) listOf(row) else buildList {
            add(row.copy(node = (row.node as ActionNode).copy(detail = field.value(draft))))
            if (editing == field && isPicker) {
                // Keep the directory compact while showing choices ahead of the cursor.
                val first = (choiceIndex - 3).coerceAtLeast(0)
                choices.drop(first).take(7).forEachIndexed { offset, choice ->
                    add(FlattenedNode(ActionNode(choice.label, InstrumentCommand.EditNewOwnedPokemon(field)),
                        row.depth + 1, "$optionPrefix${first + offset}", false))
                }
            }
        }
    }
    val lcdLines = when {
        saving -> listOf("SAVING CARD…")
        notice != null && inBranch -> listOf(requireNotNull(notice))
        editing == OwnedPokemonField.SPECIES -> when (search.loadState.refresh) {
            is LoadState.Error -> listOf("Could not read species.", "START: SEARCH AGAIN", "B: BACK")
            is LoadState.Loading -> listOf("READING SPECIES…")
            else -> listOf("CHOOSE SPECIES · ${regionValue.label}", "${choices.getOrNull(choiceIndex)?.label ?: "No matches"}",
                "↑ ↓ BROWSE · HOLD TO SPEED UP", "← → REGION", "START: TYPE · A: CHOOSE · B: BACK")
        }
        isPicker -> choices.getOrNull(choiceIndex)?.move?.let { moveSelectionLcdLines(it) }
            ?: listOf(if (species == null) "READING MOVES…" else "LEAVE MOVE UNRECORDED", "A: CHOOSE · B: BACK")
        inBranch -> listOf("ADD OWNED POKÉMON", "A: EDIT FIELD", "START: SAVE CARD", "Optional fields can be filled later.")
        else -> emptyList()
    }

    fun vertical(direction: Int): Boolean {
        if (saving) return true
        if (editing == null) return false
        if (keyboard.isFocused) {
            if (direction < 0) keyboard.handleUp() else keyboard.handleDown()
        } else if (isPicker) moveChoice(direction)
        return true
    }
    fun horizontal(direction: Int): Boolean {
        if (saving) return true
        if (editing == null) return false
        if (keyboard.isFocused) {
            if (direction < 0) keyboard.handleLeft() else keyboard.handleRight()
        } else if (editing == OwnedPokemonField.SPECIES) {
            region.value = RegistrationRegion.entries[(region.value.ordinal + direction).mod(RegistrationRegion.entries.size)]
        }
        return true
    }

    return DirectoryOwnedPokemonEditor(
        nodes = nodes,
        selectedPath = if (isPicker && choices.isNotEmpty()) "$optionPrefix$choiceIndex" else tree.selectedPath,
        keyboard = keyboard,
        keyboardPrompt = editing?.let { field -> if (field == OwnedPokemonField.SPECIES) "SEARCH: $queryText · END: BROWSE"
            else "${field.label}: ${field.value(draft)} · B: DONE" },
        lcdLines = lcdLines,
        activate = ::activate,
        up = { vertical(-1) }, down = { vertical(1) }, left = { horizontal(-1) }, right = { horizontal(1) },
        confirm = {
            when {
                saving -> true
                editing == null -> false
                keyboard.isFocused -> { keyboard.handleA("") { key(it) }; true }
                isPicker -> { choose(choiceIndex); true }
                else -> { keyboard.open(); true }
            }
        },
        back = { if (saving) true else if (editing != null) { finishField(); true } else false },
        start = {
            when {
                saving -> true
                editing == null && inBranch -> { save(); true }
                editing == null -> false
                keyboard.isFocused -> { keyboard.handleStart(); heldDirection = 0; true }
                isText -> { if (editing == OwnedPokemonField.SPECIES) { keyboard.updateLayout(LettersLayout); search.retry() }; keyboard.open(); true }
                else -> true
            }
        },
        select = {
            if (saving) true else if (isText) {
                if (editing != OwnedPokemonField.CP) keyboard.handleModeSwitch()
                keyboard.open(); true
            } else editing != null
        },
        key = ::key,
        hold = { direction -> heldDirection = if (!saving && isPicker && !keyboard.isFocused) direction else 0 },
        chooseNode = { row ->
            val index = row.path.takeIf { it.startsWith(optionPrefix) }?.removePrefix(optionPrefix)?.toIntOrNull()
            if (index != null) { choose(index); true } else false
        },
    )
}
