package com.example.overdex.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.model.FieldNoteDraft
import com.example.overdex.model.FieldNoteJournalEntry
import com.example.overdex.model.SyllableCorrection
import com.example.overdex.ui.ODXFi.ODXFiShell
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.components.FilterSettings
import com.example.overdex.ui.components.QwertyLayout
import com.example.overdex.ui.components.TerminalKeyboardController
import com.example.overdex.ui.components.rememberTerminalKeyboardController
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

private val NoteEditorPaper = Color(0xFF101A12)
private val NoteEditorInk = Color(0xFFD8E8C5)
private val NoteEditorAccent = Color(0xFF9FBF85)
private val NoteEditorRule = Color(0xFF3D5839)
private val SyllableCountKeys = listOf(
    listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"),
    listOf("0", "DELETE", "END"),
)
private val NotePunctuationKeys = listOf(
    listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
    listOf(".", ",", "'", "!", "?", ":", ";", "-", "(", ")"),
    listOf("\"", "/", "&", "…", "—", "SPACE", "DELETE"),
    listOf("letters", "a/A", "END"),
)

private data class NoteEditorField(
    val id: String,
    val label: String,
    val text: String,
    val limit: Int,
    val numeric: Boolean = false,
    val change: (String) -> Unit,
)

private data class NoteEditorRow(
    val id: String,
    val label: String,
    val text: String = "",
    val action: Boolean = false,
    val enabled: Boolean = true,
    val numeric: Boolean = false,
    val activate: () -> Unit,
)

/** Draft ownership stays with the caller: each edit is published before another key is entered. */
@Composable
fun FieldNoteEditorScreen(
    viewModel: PokedexViewModel,
    filterSettings: FilterSettings,
    onFilterSettingsChange: (FilterSettings) -> Unit,
    draft: FieldNoteDraft,
    onDraftChange: (FieldNoteDraft) -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onBack: () -> Unit,
    saving: Boolean,
    message: String?,
) {
    val keyboard = rememberTerminalKeyboardController()
    var selectedId by rememberSaveable(draft.id) { mutableStateOf("title") }
    var confirmingDiscard by rememberSaveable(draft.id) { mutableStateOf(false) }
    var validation by rememberSaveable(draft.id) { mutableStateOf<String?>(null) }
    val fields = buildList {
        add(NoteEditorField("title", "title", draft.title, 200) { onDraftChange(draft.copy(title = it)) })
        // Empty optional third fields do not add a line to an existing two-line source note.
        repeat(maxOf(3, draft.lines.size)) { index ->
            add(NoteEditorField("line:$index", "line ${index + 1}", draft.lines.getOrNull(index).orEmpty(), 2_000) { text ->
                val next = draft.lines.toMutableList()
                while (next.size <= index) next.add("")
                next[index] = text
                onDraftChange(draft.copy(lines = next))
            })
        }
        add(NoteEditorField("lesson", "lesson", draft.lesson, 4_000) { onDraftChange(draft.copy(lesson = it)) })
        add(NoteEditorField("author", "author", draft.author, 200) { onDraftChange(draft.copy(author = it)) })
    }
    val latestFields by rememberUpdatedState(fields)
    fun edit(field: NoteEditorField) {
        if (saving) return
        selectedId = field.id
        validation = null
        keyboard.updateLayout(QwertyLayout)
        keyboard.open()
    }
    fun save() {
        if (saving || confirmingDiscard) return
        validation = when {
            draft.title.isBlank() -> "Add a title before saving the note."
            draft.lines.none { it.isNotBlank() } -> "Add at least one poem line before saving the note."
            else -> null
        }
        if (validation == null) onSave()
    }
    fun back() {
        when {
            keyboard.handleB() -> Unit
            confirmingDiscard -> { confirmingDiscard = false; selectedId = "discard" }
            else -> onBack()
        }
    }
    val rows = if (confirmingDiscard) listOf(
        NoteEditorRow("keep", "keep draft", action = true) { confirmingDiscard = false; selectedId = "discard" },
        NoteEditorRow("confirm_discard", "discard this draft", action = true, enabled = !saving, activate = onDiscard),
    ) else buildList {
        fields.forEach { field -> add(NoteEditorRow(field.id, field.label, field.text, enabled = !saving) { edit(field) }) }
        add(NoteEditorRow("save", if (draft.editingNoteId == null) "save personal note" else "save changes",
            action = true, enabled = !saving, activate = ::save))
        add(NoteEditorRow("back", "return · keep draft", action = true, activate = onBack))
        add(NoteEditorRow("discard", "discard draft…", action = true, enabled = !saving) {
            confirmingDiscard = true
            selectedId = "keep"
        })
    }
    NoteEditingShell(
        viewModel, filterSettings, onFilterSettingsChange,
        title = if (confirmingDiscard) "discard this draft?" else if (draft.editingNoteId == null) "write a field note" else "edit personal note",
        species = draft.speciesName,
        context = if (confirmingDiscard) "This removes the unfinished draft. Keep it to continue writing later."
            else "Personal journal · species is fixed for this draft",
        rows = rows, selectedId = selectedId, select = { selectedId = it },
        keyboard = keyboard,
        key = { key ->
            if (!saving) latestFields.firstOrNull { it.id == selectedId }?.let { field ->
                validation = null
                val next = noteEditorText(field.text, key, field.limit)
                if (next != field.text) field.change(next)
            }
        },
        onSave = ::save, onBack = ::back, saving = saving, message = validation ?: message,
        testPrefix = "field-note-editor",
    )
}

/** An annotation records a proposed reading; it never changes the original poem or its revision. */
@Composable
fun SyllableCorrectionEditorScreen(
    viewModel: PokedexViewModel,
    filterSettings: FilterSettings,
    onFilterSettingsChange: (FilterSettings) -> Unit,
    entry: FieldNoteJournalEntry,
    onSave: (SyllableCorrection) -> Unit,
    onBack: () -> Unit,
    saving: Boolean,
    message: String?,
) {
    val keyboard = rememberTerminalKeyboardController()
    var selectedId by rememberSaveable(entry.note.id, entry.note.revision) { mutableStateOf("original:0") }
    var lineIndex by rememberSaveable(entry.note.id, entry.note.revision) { mutableIntStateOf(0) }
    var counts by rememberSaveable(entry.note.id, entry.note.revision) { mutableStateOf(List(entry.note.lines.size) { "" }) }
    var pronunciations by rememberSaveable(entry.note.id, entry.note.revision) { mutableStateOf(List(entry.note.lines.size) { "" }) }
    var replacements by rememberSaveable(entry.note.id, entry.note.revision) { mutableStateOf(List(entry.note.lines.size) { "" }) }
    val correctionId by rememberSaveable(entry.note.id, entry.note.revision) { mutableStateOf(UUID.randomUUID().toString()) }
    val createdAt by rememberSaveable(entry.note.id, entry.note.revision) { mutableLongStateOf(System.currentTimeMillis()) }
    var validation by rememberSaveable(entry.note.id, entry.note.revision) { mutableStateOf<String?>(null) }
    val original = entry.note.lines.getOrNull(lineIndex)
    fun select(id: String) {
        selectedId = id
        id.removePrefix("original:").takeIf { id.startsWith("original:") }?.toIntOrNull()?.let {
            if (it in entry.note.lines.indices) { lineIndex = it; validation = null }
        }
    }
    val fields = listOf(
        NoteEditorField("count", "proposed syllables · optional", counts.getOrElse(lineIndex) { "" }, 2, numeric = true) { value ->
            counts = counts.mapIndexed { index, old -> if (index == lineIndex) value else old }
        },
        NoteEditorField("pronunciation", "pronunciation / explanation", pronunciations.getOrElse(lineIndex) { "" }, 2_000) { value ->
            pronunciations = pronunciations.mapIndexed { index, old -> if (index == lineIndex) value else old }
        },
        NoteEditorField("replacement", "suggested line · optional", replacements.getOrElse(lineIndex) { "" }, 2_000) { value ->
            replacements = replacements.mapIndexed { index, old -> if (index == lineIndex) value else old }
        },
    )
    val latestFields by rememberUpdatedState(fields)
    fun edit(field: NoteEditorField) {
        if (saving || original == null) return
        selectedId = field.id
        keyboard.updateLayout(if (field.numeric) SyllableCountKeys else QwertyLayout)
        keyboard.open()
    }
    fun save() {
        if (saving || original == null) return
        val count = counts.getOrNull(lineIndex)?.toIntOrNull()
        val explanation = pronunciations.getOrElse(lineIndex) { "" }
        val replacement = replacements.getOrElse(lineIndex) { "" }
        if (count == null && explanation.isBlank() && replacement.isBlank()) {
            validation = "Add a proposed count, an explanation, or a suggested line before saving."
            return
        }
        validation = null
        onSave(SyllableCorrection(
            id = correctionId, entryId = entry.note.id, entryRevision = entry.note.revision,
            lineIndex = lineIndex, proposedCount = count, pronunciation = explanation,
            replacementLine = replacement, createdAt = createdAt, originalLine = original,
        ))
    }
    fun back() { if (!keyboard.handleB()) onBack() }
    val rows = buildList {
        entry.note.lines.forEachIndexed { index, line ->
            add(NoteEditorRow("original:$index", "original line ${index + 1}${if (lineIndex == index) " · selected" else ""}", line,
                enabled = !saving) { select("original:$index") })
        }
        if (original != null) {
            fields.forEach { field -> add(NoteEditorRow(field.id, field.label, field.text, enabled = !saving, numeric = field.numeric) { edit(field) }) }
            add(NoteEditorRow("save", "save local annotation", action = true, enabled = !saving, activate = ::save))
        }
        add(NoteEditorRow("back", "return", action = true, activate = onBack))
    }
    NoteEditingShell(
        viewModel, filterSettings, onFilterSettingsChange,
        title = "syllable annotation", species = entry.speciesName,
        context = "${entry.note.title} · entry ${entry.note.id} · revision ${entry.note.revision}\nA proposed reading, not an automatic syllable count.",
        originalLine = original?.let { "line ${lineIndex + 1} · $it" },
        rows = rows, selectedId = selectedId, select = ::select,
        keyboard = keyboard,
        key = { key ->
            if (!saving && original != null) latestFields.firstOrNull { it.id == selectedId }?.let { field ->
                val next = noteEditorText(field.text, key, field.limit)
                if (next != field.text && (!field.numeric || next.all(Char::isDigit))) {
                    validation = null
                    field.change(next)
                }
            }
        },
        onSave = ::save, onBack = ::back, saving = saving, message = validation ?: message,
        testPrefix = "syllable-editor",
    )
}

/** Shared presentation only; note creation and source annotations have separate caller-owned saves. */
@Composable
private fun NoteEditingShell(
    viewModel: PokedexViewModel,
    filterSettings: FilterSettings,
    onFilterSettingsChange: (FilterSettings) -> Unit,
    title: String,
    species: String,
    context: String,
    rows: List<NoteEditorRow>,
    selectedId: String,
    select: (String) -> Unit,
    keyboard: TerminalKeyboardController,
    key: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    saving: Boolean,
    message: String?,
    testPrefix: String,
    originalLine: String? = null,
) {
    val crtList = rememberLazyListState()
    val lcdList = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var uppercase by rememberSaveable(testPrefix) { mutableStateOf(false) }
    val latestKey by rememberUpdatedState(key)
    val selectedIndex = rows.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    val selected = rows.getOrNull(selectedIndex)
    fun move(delta: Int) {
        if (saving || rows.isEmpty()) return
        select(rows[(selectedIndex + delta).coerceIn(0, rows.lastIndex)].id)
    }
    fun activate() { if (selected?.enabled == true) selected.activate() }
    fun inputKey(value: String) {
        // These editor controls are not prose, even if an unexpected layout sends one to a count field.
        when (value) {
            "letters" -> if (selected?.numeric != true) keyboard.updateLayout(QwertyLayout)
            "a/A" -> if (selected?.numeric != true) uppercase = !uppercase
            else -> latestKey(if (value.length == 1 && value.single().isLetter()) {
                if (uppercase) value.uppercase(Locale.ROOT) else value.lowercase(Locale.ROOT)
            } else value) // Swipe names retain their canonical source casing.
        }
    }
    fun scroll(direction: Int) {
        scope.launch {
            val viewport = (crtList.layoutInfo.viewportEndOffset - crtList.layoutInfo.viewportStartOffset).coerceAtLeast(1)
            crtList.animateScrollBy(direction * viewport * 0.65f)
        }
    }
    LaunchedEffect(selectedId, rows.map { it.id }) {
        if (rows.isNotEmpty()) {
            crtList.animateScrollToItem(selectedIndex)
            lcdList.animateScrollToItem(selectedIndex)
        }
    }
    BackHandler(onBack = onBack)
    ODXFiShell(
        viewModel = viewModel, showBattleOverlay = false,
        filterSettings = filterSettings, onFilterSettingsChange = onFilterSettingsChange,
        onUp = { if (keyboard.isVisible) keyboard.handleUp() else move(-1) },
        onDown = { if (keyboard.isVisible) keyboard.handleDown() else move(1) },
        onLeft = { if (keyboard.isVisible) keyboard.handleLeft() else scroll(-1) },
        onRight = { if (keyboard.isVisible) keyboard.handleRight() else scroll(1) },
        onA = { if (keyboard.isVisible) keyboard.handleA(selected?.text.orEmpty(), ::inputKey) else activate() },
        onB = onBack,
        onSelect = {
            if (keyboard.isVisible) {
                if (selected?.numeric == true) keyboard.handleStart()
                else keyboard.updateLayout(if (keyboard.layout == NotePunctuationKeys) QwertyLayout else NotePunctuationKeys)
            }
        },
        onStart = { if (keyboard.isVisible) keyboard.handleStart() else onSave() },
        startLabel = if (keyboard.isVisible) "END" else "SAVE",
        keyboardController = keyboard, onKeyActivated = ::inputKey,
        lcdEdgeToEdgeContent = true,
        lcdContent = {
            Column(Modifier.fillMaxSize().testTag("$testPrefix-lcd")) {
                if (saving || message != null) Row(Modifier.fillMaxWidth().padding(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (saving) {
                        CircularProgressIndicator(Modifier.size(15.dp), color = NoteEditorAccent, strokeWidth = 2.dp)
                        Spacer(Modifier.size(7.dp))
                    }
                    Text(if (saving) "Saving…" else message.orEmpty(), color = NoteEditorInk,
                        fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.weight(1f).testTag("$testPrefix-message"))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    EditorLcdScroll("read ↑", saving) { scroll(-1) }
                    EditorLcdScroll("read ↓", saving) { scroll(1) }
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("$testPrefix-actions"), state = lcdList) {
                    items(rows, key = { it.id }) { row ->
                        val label = when {
                            row.action -> row.label
                            row.id.startsWith("original:") -> "choose · ${row.label}"
                            else -> "edit · ${row.label}"
                        }
                        Text("${if (row.id == selected?.id) "> " else ""}$label",
                            color = if (row.enabled) NoteEditorInk else NoteEditorAccent.copy(alpha = 0.45f),
                            fontSize = 12.sp, lineHeight = 17.sp,
                            modifier = Modifier.fillMaxWidth()
                                .background(if (row.id == selected?.id) NoteEditorRule.copy(alpha = 0.45f) else Color.Transparent)
                                .clickable(enabled = row.enabled) { select(row.id); row.activate() }
                                .padding(horizontal = 8.dp, vertical = 10.dp).testTag("$testPrefix-action:${row.id}"))
                    }
                }
            }
        },
    ) {
        Column(Modifier.fillMaxSize().background(NoteEditorPaper).padding(11.dp).testTag("$testPrefix-crt")) {
            Text(title, color = NoteEditorAccent, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            Text(species, color = NoteEditorInk, fontFamily = FontFamily.Serif, fontSize = 23.sp,
                modifier = Modifier.padding(top = 5.dp).testTag("$testPrefix-species"))
            Text(context, color = NoteEditorAccent, fontSize = 11.sp, lineHeight = 16.sp,
                modifier = Modifier.padding(top = 5.dp, bottom = 10.dp))
            if (keyboard.isVisible && (saving || message != null)) Text(
                if (saving) "Saving…" else message.orEmpty(), color = NoteEditorInk, fontSize = 11.sp,
                lineHeight = 16.sp, modifier = Modifier.padding(bottom = 7.dp),
            )
            if (keyboard.isVisible && selected?.numeric != true) Text(
                "${if (uppercase) "ABC" else "abc"} · SELECT: ${if (keyboard.layout == NotePunctuationKeys) "letters" else "punctuation"}",
                color = NoteEditorAccent,
                fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                modifier = Modifier.padding(bottom = 7.dp).testTag("$testPrefix-keyboard-status"),
            )
            if (originalLine != null) Text(originalLine, color = NoteEditorInk, fontFamily = FontFamily.Serif,
                fontSize = 16.sp, lineHeight = 23.sp, maxLines = 4, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().border(1.dp, NoteEditorRule).padding(8.dp)
                    .testTag("$testPrefix-original-line"))
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 7.dp), state = crtList,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rows, key = { it.id }) { row ->
                    val active = row.id == selected?.id
                    Column(Modifier.fillMaxWidth().border(1.dp, if (active) NoteEditorAccent else NoteEditorRule)
                        .background(if (active) NoteEditorRule.copy(alpha = 0.28f) else Color.Transparent)
                        .padding(9.dp).testTag("$testPrefix-field:${row.id}")) {
                        Text(row.label, color = NoteEditorAccent, fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp, lineHeight = 16.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                        if (!row.action) {
                            val displayed = if (active && keyboard.isVisible) {
                                "${if (row.text.length > 160) "…" else ""}${row.text.takeLast(160)}▌"
                            } else row.text
                            Text(displayed, color = NoteEditorInk, fontFamily = FontFamily.Serif,
                                fontSize = 18.sp, lineHeight = 26.sp, modifier = Modifier.padding(top = 5.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorLcdScroll(label: String, saving: Boolean, scroll: () -> Unit) {
    Text(label, color = NoteEditorAccent, fontSize = 10.sp,
        modifier = Modifier.clickable(enabled = !saving, onClick = scroll).padding(horizontal = 9.dp, vertical = 6.dp))
}

private fun noteEditorText(current: String, key: String, limit: Int): String = when {
    key == "DELETE" -> current.dropLast(1)
    key == "SPACE" -> (current + " ").take(limit)
    key.startsWith("TYPE:") -> current
    else -> (current + if (key.length > 1 && current.lastOrNull()?.isWhitespace() == false) " $key" else key).take(limit)
}
