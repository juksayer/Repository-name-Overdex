package com.example.overdex.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import coil.compose.AsyncImage
import com.example.overdex.media.FieldNotesPageTurnSound
import com.example.overdex.model.FieldNoteJournalEntry
import com.example.overdex.model.OwnedPokemon
import com.example.overdex.ui.FieldNotesViewModel
import com.example.overdex.ui.ODXFi.ODXFiShell
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.components.FilterSettings
import com.example.overdex.ui.components.rememberTerminalKeyboardController
import com.example.overdex.ui.theme.TerminalGreen
import kotlinx.coroutines.launch
import java.util.Locale

private val JournalPaper = Color(0xFF101A12)
private val JournalInk = Color(0xFFD8E8C5)
private val JournalAccent = Color(0xFF9FBF85)
private val JournalRule = Color(0xFF3D5839)

private data class JournalAction(val id: String, val label: String, val perform: () -> Unit)

/** The journal is read through the CRT; the LCD and instrument buttons own every action. */
@Composable
fun FieldNotesScreen(
    viewModel: PokedexViewModel,
    filterSettings: FilterSettings,
    onFilterSettingsChange: (FilterSettings) -> Unit,
    onOpenSpecies: (Int) -> Unit,
    onOpenOwned: (String) -> Unit,
    onBack: () -> Unit,
    onPageTurn: (() -> Unit)? = null,
) {
    val journal: FieldNotesViewModel = composeViewModel()
    val entries by journal.entries.collectAsState()
    val favorites by journal.favorites.collectAsState()
    val owned by journal.ownedPokemon.collectAsState()
    val loading by journal.loading.collectAsState()
    val loadError by journal.loadError.collectAsState()
    val saveError by journal.saveError.collectAsState()
    var selectedNoteId by rememberSaveable { mutableStateOf<String?>(null) }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var indexOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var indexNoteId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedActionId by rememberSaveable { mutableStateOf("species") }
    var lastScrolledNoteId by rememberSaveable { mutableStateOf<String?>(null) }
    val keyboard = rememberTerminalKeyboardController()
    val pageScroll = rememberScrollState()
    val actionList = rememberLazyListState()
    val indexList = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val pageSound = remember(context.applicationContext) { FieldNotesPageTurnSound(context.applicationContext) }
    DisposableEffect(pageSound) { onDispose { pageSound.release() } }
    fun playPageTurn() { if (onPageTurn != null) onPageTurn() else pageSound.turn() }

    val pages = remember(entries, favorites, favoritesOnly) {
        if (favoritesOnly) entries.filter { it.note.id in favorites } else entries
    }
    val current = pages.firstOrNull { it.note.id == selectedNoteId } ?: pages.firstOrNull()
    val pageIndex = pages.indexOf(current)
    val results = remember(pages, query) { pages.filter { it.matchesJournalQuery(query) } }
    val resultIndex = results.indexOfFirst { it.note.id == indexNoteId }.coerceAtLeast(0)
    val indexEntry = results.getOrNull(resultIndex)
    val displayed = if (indexOpen) indexEntry else current
    // This is the raw owned-card stream, independent of any binder's search/filter state.
    val matchingOwned = remember(owned, displayed?.speciesId) {
        owned.filter { it.speciesId == displayed?.speciesId }.distinctBy { it.id }
    }

    fun turnPage(delta: Int) {
        if (pages.isEmpty()) return
        val next = pages[(pageIndex + delta).coerceIn(0, pages.lastIndex)]
        if (next.note.id != current?.note?.id) {
            selectedNoteId = next.note.id
            playPageTurn()
        }
    }
    fun showIndex() {
        indexNoteId = current?.note?.id
        indexOpen = true
        query = ""
        keyboard.open()
    }
    fun readIndexEntry() {
        val entry = indexEntry ?: return
        if (entry.note.id != current?.note?.id) playPageTurn()
        selectedNoteId = entry.note.id
        indexOpen = false
        query = ""
        keyboard.close()
        selectedActionId = "species"
    }
    fun changeCollection() {
        favoritesOnly = !favoritesOnly
        query = ""
        indexNoteId = null
    }
    fun back() {
        when {
            keyboard.handleB() -> Unit
            indexOpen -> { indexOpen = false; query = ""; selectedActionId = "search" }
            else -> { query = ""; onBack() }
        }
    }
    fun key(key: String) {
        query = when {
            key == "DELETE" -> query.dropLast(1)
            key == "SPACE" -> (query + " ").take(80)
            key.startsWith("TYPE:") -> query
            key.length > 1 -> key.take(80) // A swipe suggestion is a complete species/name query.
            else -> (query + key).take(80)
        }
        indexNoteId = null
    }
    fun toggleFavorite() { displayed?.note?.id?.let(journal::toggleFavorite) }

    val actions = buildList {
        if (loadError != null) add(JournalAction("retry", "retry loading", journal::retry))
        if (indexOpen) {
            if (indexEntry != null) add(JournalAction("read", "read this note", ::readIndexEntry))
            add(JournalAction("search", "edit search", keyboard::open))
            add(JournalAction("close_index", "return to journal") { indexOpen = false; query = "" })
        } else {
            if (displayed != null) add(JournalAction("species", "open ${displayed.speciesName} in Overdex") {
                onOpenSpecies(displayed.speciesId)
            })
            add(JournalAction("search", "find a species or note", ::showIndex))
        }
        if (displayed != null) {
            add(JournalAction("favorite", if (displayed.note.id in favorites) "remove favorite" else "favorite this note", ::toggleFavorite))
        }
        add(JournalAction("collection", if (favoritesOnly) "show all notes" else "show favorites", ::changeCollection))
        if (!indexOpen && current != null) {
            if (pageIndex > 0) add(JournalAction("previous", "previous page") { turnPage(-1) })
            if (pageIndex < pages.lastIndex) add(JournalAction("next", "next page") { turnPage(1) })
            if (pageScroll.canScrollBackward) add(JournalAction("read_up", "read higher on this page") {
                scope.launch { pageScroll.animateScrollBy(-pageScroll.viewportSize * 0.65f) }
            })
            if (pageScroll.canScrollForward) add(JournalAction("read_down", "read lower on this page") {
                scope.launch { pageScroll.animateScrollBy(pageScroll.viewportSize * 0.65f) }
            })
        }
        matchingOwned.forEach { card ->
            add(JournalAction("owned:${card.id}", card.journalLabel(displayed?.speciesName.orEmpty())) { onOpenOwned(card.id) })
        }
        if (saveError != null) add(JournalAction("dismiss_error", "dismiss save message", journal::dismissSaveError))
        add(JournalAction("back", "return", ::back))
    }
    val actionIndex = actions.indexOfFirst { it.id == selectedActionId }.coerceAtLeast(0)
    fun moveVertical(delta: Int) {
        if (indexOpen) {
            indexNoteId = results.getOrNull((resultIndex + delta).coerceIn(0, results.lastIndex.coerceAtLeast(0)))?.note?.id
        } else {
            selectedActionId = actions[(actionIndex + delta).coerceIn(0, actions.lastIndex)].id
        }
    }

    LaunchedEffect(current?.note?.id) {
        current?.note?.id?.let { noteId ->
            if (noteId != lastScrolledNoteId) {
                pageScroll.scrollTo(0)
                lastScrolledNoteId = noteId
            }
        }
    }
    LaunchedEffect(actionIndex, actions.map { it.id }) {
        if (actions.isNotEmpty()) actionList.animateScrollToItem(actionIndex)
    }
    LaunchedEffect(resultIndex, results.map { it.note.id }) {
        if (results.isNotEmpty()) indexList.animateScrollToItem(resultIndex)
    }
    BackHandler(onBack = ::back)

    ODXFiShell(
        viewModel = viewModel,
        showBattleOverlay = false,
        filterSettings = filterSettings,
        onFilterSettingsChange = onFilterSettingsChange,
        onUp = { if (keyboard.isVisible) keyboard.handleUp() else moveVertical(-1) },
        onDown = { if (keyboard.isVisible) keyboard.handleDown() else moveVertical(1) },
        onLeft = {
            if (keyboard.isVisible) keyboard.handleLeft()
            else if (indexOpen) moveVertical(-1) else turnPage(-1)
        },
        onRight = {
            if (keyboard.isVisible) keyboard.handleRight()
            else if (indexOpen) moveVertical(1) else turnPage(1)
        },
        onA = {
            when {
                keyboard.isVisible -> keyboard.handleA(query, ::key)
                indexOpen -> readIndexEntry()
                else -> actions.getOrNull(actionIndex)?.perform?.invoke()
            }
        },
        onB = ::back,
        onSelect = { if (keyboard.isVisible) keyboard.handleStart() else toggleFavorite() },
        onStart = { if (keyboard.isVisible) keyboard.handleStart() else changeCollection() },
        startLabel = if (keyboard.isVisible) "END" else "START",
        keyboardController = keyboard,
        onKeyActivated = ::key,
        lcdEdgeToEdgeContent = true,
        lcdContent = {
            Column(Modifier.fillMaxSize().testTag("field-notes-lcd")) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (displayed != null) {
                        val sprite = remember(displayed.speciesId) { viewModel.spriteProvider.getSpriteUrl(displayed.speciesId) }
                        AsyncImage(
                            model = sprite,
                            contentDescription = displayed.speciesName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(44.dp).testTag("field-notes-species-sprite"),
                        )
                        Spacer(Modifier.width(7.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(displayed?.speciesName ?: "field notes", color = JournalInk, fontSize = 14.sp,
                            lineHeight = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            when {
                                loading -> "Opening journal…"
                                indexOpen -> "${results.size} matching notes"
                                pages.isEmpty() -> if (favoritesOnly) "No favorites yet" else "No notes available"
                                else -> "${pageIndex + 1} / ${pages.size} · ${if (favoritesOnly) "favorites" else "all notes"}"
                            },
                            color = JournalAccent, fontSize = 10.sp, lineHeight = 13.sp,
                        )
                    }
                    if (displayed?.note?.id?.let { it in favorites } == true) Text("★", color = JournalAccent, fontSize = 19.sp)
                }
                if (saveError != null) Text(saveError!!, color = Color(0xFFFFC897), fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 8.dp).testTag("field-notes-save-error"))
                LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("field-notes-actions"), state = actionList) {
                    items(actions, key = { it.id }) { action ->
                        JournalLcdAction(action, selected = !indexOpen && actions[actionIndex].id == action.id) {
                            selectedActionId = action.id
                            action.perform()
                        }
                    }
                }
            }
        },
    ) {
        when {
            loading -> JournalMessage("field notes", "Opening the journal…")
            loadError != null -> JournalMessage("unable to open journal", loadError!!)
            indexOpen -> {
                Column(Modifier.fillMaxSize().padding(12.dp).testTag("field-notes-index")) {
                    Text("field notes / index", color = JournalAccent, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    Text("find: ${query.ifBlank { "_" }}", color = JournalInk, fontSize = 17.sp,
                        modifier = Modifier.padding(vertical = 10.dp).testTag("field-notes-query"))
                    Text("${results.size} notes · ${if (favoritesOnly) "favorites" else "all species"}",
                        color = JournalAccent, fontSize = 11.sp)
                    if (results.isEmpty()) {
                        Text("No notes match this search.", color = JournalInk, modifier = Modifier.padding(top = 22.dp))
                    } else LazyColumn(Modifier.weight(1f).padding(top = 12.dp), state = indexList,
                        verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(results, key = { it.note.id }) { entry ->
                            val selected = entry.note.id == indexEntry?.note?.id
                            Column(Modifier.fillMaxWidth()
                                .background(if (selected) JournalRule.copy(alpha = 0.45f) else Color.Transparent)
                                .border(1.dp, if (selected) JournalAccent else Color.Transparent)
                                .padding(9.dp).testTag("field-notes-result:${entry.note.id}")) {
                                Text("${if (selected) "> " else ""}${entry.speciesName}", color = JournalInk,
                                    fontSize = 17.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                                Text(entry.note.title, color = JournalAccent, fontSize = 12.sp, lineHeight = 17.sp)
                            }
                        }
                    }
                }
            }
            current == null -> JournalMessage("field notes", if (favoritesOnly)
                "Your favorite pages will appear here. Return to all notes and mark a page with Select."
                else "No field notes are available.")
            else -> JournalPage(current, pageIndex + 1, pages.size, current.note.id in favorites,
                Modifier.verticalScroll(pageScroll))
        }
    }
}

@Composable
private fun JournalPage(
    entry: FieldNoteJournalEntry,
    page: Int,
    pageCount: Int,
    favorite: Boolean,
    scrollModifier: Modifier,
) {
    Box(Modifier.fillMaxSize().padding(6.dp)
        .background(JournalPaper, RoundedCornerShape(3.dp))
        .border(1.dp, JournalRule, RoundedCornerShape(3.dp)).testTag("field-notes-page")) {
        Column(Modifier.fillMaxSize().then(scrollModifier).drawBehind {
            val margin = 13.dp.toPx()
            drawLine(JournalRule.copy(alpha = 0.6f), Offset(margin, 0f), Offset(margin, size.height), 1.dp.toPx())
        }.padding(start = 25.dp, end = 16.dp, top = 19.dp, bottom = 22.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("FIELD NOTES", color = JournalAccent, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                Text("${if (favorite) "★ · " else ""}$page / $pageCount", color = JournalAccent,
                    fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }
            Spacer(Modifier.height(22.dp))
            Text(entry.speciesName, color = JournalAccent, fontFamily = FontFamily.Serif,
                fontSize = 17.sp, fontStyle = FontStyle.Italic)
            Text(entry.note.title, color = JournalInk, fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp,
                modifier = Modifier.padding(top = 7.dp).testTag("field-notes-note-title"))
            Spacer(Modifier.height(13.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(JournalRule))
            Spacer(Modifier.height(25.dp))
            // Preserve the actual line count and source order, including intentional two-line notes.
            entry.note.lines.forEach { line ->
                Text(line, color = JournalInk, fontFamily = FontFamily.Serif, fontSize = 20.sp,
                    lineHeight = 30.sp, modifier = Modifier.padding(bottom = 9.dp))
            }
            if (!entry.note.lesson.isNullOrBlank()) {
                Spacer(Modifier.height(18.dp))
                Text("LESSON", color = JournalAccent, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                Text(entry.note.lesson!!, color = JournalInk.copy(alpha = 0.9f), fontSize = 14.sp,
                    lineHeight = 22.sp, modifier = Modifier.padding(top = 7.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(entry.note.author, color = JournalAccent, fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic, fontSize = 13.sp, lineHeight = 19.sp)
            if (entry.note.revision > 1) Text("revision ${entry.note.revision}", color = JournalAccent.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
        }
    }
}

@Composable
private fun JournalMessage(title: String, message: String) {
    Column(Modifier.fillMaxSize().padding(22.dp)) {
        Text(title, color = JournalAccent, fontFamily = FontFamily.Serif, fontSize = 23.sp)
        Text(message, color = JournalInk, fontSize = 16.sp, lineHeight = 24.sp,
            modifier = Modifier.padding(top = 18.dp))
    }
}

@Composable
private fun JournalLcdAction(action: JournalAction, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick)
        .background(if (selected) JournalRule.copy(alpha = 0.4f) else Color.Transparent)
        .padding(horizontal = 8.dp, vertical = 9.dp).testTag("field-notes-action:${action.id}"),
        verticalAlignment = Alignment.Top) {
        Text(if (selected) ">" else "·", color = if (selected) TerminalGreen else JournalAccent,
            fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.width(15.dp))
        Text(action.label, color = if (selected) JournalInk else JournalAccent,
            fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f))
    }
}

private fun FieldNoteJournalEntry.matchesJournalQuery(query: String): Boolean {
    val value = query.trim().removePrefix("#").lowercase(Locale.ROOT)
    if (value.isEmpty()) return true
    return speciesId.toString() == value || sourceSpeciesId.toString() == value ||
        speciesName.lowercase(Locale.ROOT).contains(value) || note.title.lowercase(Locale.ROOT).contains(value) ||
        note.lines.any { it.lowercase(Locale.ROOT).contains(value) }
}

private fun OwnedPokemon.journalLabel(fallbackName: String): String {
    val name = displayName?.takeIf { it.isNotBlank() } ?: speciesName?.takeIf { it.isNotBlank() } ?: fallbackName
    val details = buildList {
        if (isShadow) add("shadow")
        if (isPurified) add("purified")
        if (isShiny) add("shiny")
        cp?.let { add("CP $it") }
    }
    return "owned · $name${if (details.isEmpty()) "" else " · ${details.joinToString(" · ")}"}"
}
