package com.example.overdex.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.data.FieldNoteImportCandidate
import com.example.overdex.data.FieldNoteImportDecision
import com.example.overdex.data.FieldNoteTransferPackage
import com.example.overdex.model.PersonalFieldNote
import com.example.overdex.model.SyllableCorrection
import com.example.overdex.ui.ODXFi.ODXFiShell
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.components.FilterSettings
import kotlinx.coroutines.launch

private val ExchangeInk = Color(0xFFD8E8C5)
private val ExchangeAccent = Color(0xFF9FBF85)
private val ExchangeRule = Color(0xFF3D5839)

private data class NoteExchangeAction(
    val id: String,
    val label: String,
    val enabled: Boolean = true,
    val perform: () -> Unit,
)

/** The CRT is a protected preview; all choices live in the LCD and instrument controls. */
@Composable
fun FieldNotesExchangeScreen(
    viewModel: PokedexViewModel,
    filterSettings: FilterSettings,
    onFilterSettingsChange: (FilterSettings) -> Unit,
    notes: List<PersonalFieldNote>,
    corrections: List<SyllableCorrection>,
    incoming: FieldNoteTransferPackage?,
    candidates: List<FieldNoteImportCandidate>,
    busy: Boolean,
    message: String?,
    onSetShareable: (String, Boolean) -> Unit,
    onExportFile: (Boolean) -> Unit,
    onImportFile: () -> Unit,
    onSaveIncoming: (Set<String>, Boolean) -> Unit,
    onClearIncoming: () -> Unit,
    onNearby: (() -> Unit)? = null,
    onBack: () -> Unit,
) {
    var selectedActionId by rememberSaveable { mutableStateOf("import") }
    var localNoteId by rememberSaveable { mutableStateOf<String?>(null) }
    var incomingNoteId by rememberSaveable(incoming?.packageId) { mutableStateOf<String?>(null) }
    var includeSyllableNotes by rememberSaveable(incoming?.packageId) { mutableStateOf(false) }
    val actionList = rememberLazyListState()
    val previewList = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val selectedLocal = notes.firstOrNull { it.id == localNoteId } ?: notes.firstOrNull()
    val currentCandidate = candidates.firstOrNull { it.note.id == incomingNoteId } ?: candidates.firstOrNull()
    val currentNote = if (incoming == null) selectedLocal else currentCandidate?.note
    val eligibleIds = candidates.filter { it.decision.canSave() }.map { it.note.id }.toSet()
    val shareableCount = notes.count { it.shareable }
    val shownCorrections = incoming?.corrections ?: corrections
    val previewKey = "${incoming?.packageId ?: "local"}:${currentNote?.id.orEmpty()}"
    var lastPreviewKey by rememberSaveable { mutableStateOf<String?>(null) }

    fun page(delta: Int) {
        if (incoming != null) {
            if (candidates.isEmpty()) return
            val current = candidates.indexOfFirst { it.note.id == currentCandidate?.note?.id }.coerceAtLeast(0)
            incomingNoteId = candidates[(current + delta).coerceIn(0, candidates.lastIndex)].note.id
        } else {
            if (notes.isEmpty()) return
            val current = notes.indexOfFirst { it.id == selectedLocal?.id }.coerceAtLeast(0)
            val nextId = notes[(current + delta).coerceIn(0, notes.lastIndex)].id
            localNoteId = nextId
            selectedActionId = "share:$nextId"
        }
    }
    fun scrollPreview(direction: Int) {
        val viewport = previewList.layoutInfo.viewportEndOffset - previewList.layoutInfo.viewportStartOffset
        scope.launch { previewList.animateScrollBy(direction * viewport * 0.65f) }
    }

    val actions = buildList {
        if (incoming == null) {
            notes.forEach { note ->
                add(NoteExchangeAction("share:${note.id}",
                    "${if (note.shareable) "✓" else "○"} share ${note.title}${if (note.received) " · received" else ""}", !busy) {
                    localNoteId = note.id
                    onSetShareable(note.id, !note.shareable)
                })
            }
            if (corrections.isNotEmpty()) add(NoteExchangeAction("annotations",
                "${if (includeSyllableNotes) "✓" else "○"} include ${corrections.size} syllable notes", !busy) {
                includeSyllableNotes = !includeSyllableNotes
            })
            val hasExport = shareableCount > 0 || (includeSyllableNotes && corrections.isNotEmpty())
            add(NoteExchangeAction("export", "export selected notes to file", !busy && hasExport) {
                onExportFile(includeSyllableNotes)
            })
            add(NoteExchangeAction("import", "open a note package", !busy, onImportFile))
        } else {
            if (currentCandidate != null) add(NoteExchangeAction("save-current",
                if (currentCandidate.decision == FieldNoteImportDecision.NEWER) "save this received update" else "save this note",
                !busy && currentCandidate.decision.canSave()) {
                onSaveIncoming(setOf(currentCandidate.note.id), includeSyllableNotes)
            })
            add(NoteExchangeAction("save-eligible", "save ${eligibleIds.size} new / newer notes",
                !busy && eligibleIds.isNotEmpty()) { onSaveIncoming(eligibleIds, includeSyllableNotes) })
            if (incoming.corrections.isNotEmpty()) {
                add(NoteExchangeAction("annotations",
                    "${if (includeSyllableNotes) "✓" else "○"} include ${incoming.corrections.size} syllable notes", !busy) {
                    includeSyllableNotes = !includeSyllableNotes
                })
                add(NoteExchangeAction("save-corrections", "save syllable notes only", !busy && includeSyllableNotes) {
                    onSaveIncoming(emptySet(), true)
                })
            }
            add(NoteExchangeAction("discard", "discard this incoming package", !busy, onClearIncoming))
        }
        onNearby?.let { add(NoteExchangeAction("nearby", "nearby exchange", !busy, it)) }
        if (previewList.canScrollBackward) add(NoteExchangeAction("read-up", "read higher in preview") { scrollPreview(-1) })
        if (previewList.canScrollForward) add(NoteExchangeAction("read-down", "read lower in preview") { scrollPreview(1) })
        add(NoteExchangeAction("back", "return", perform = onBack))
    }
    val actionIndex = actions.indexOfFirst { it.id == selectedActionId }.coerceAtLeast(0)
    fun selectAction(index: Int) {
        val action = actions[index.coerceIn(0, actions.lastIndex)]
        selectedActionId = action.id
        if (action.id.startsWith("share:")) localNoteId = action.id.removePrefix("share:")
    }
    fun activate(action: NoteExchangeAction) {
        if (action.enabled) action.perform()
    }

    LaunchedEffect(actionIndex, actions.map { it.id }) { actionList.animateScrollToItem(actionIndex) }
    LaunchedEffect(previewKey) {
        if (lastPreviewKey != previewKey) {
            previewList.scrollToItem(0)
            lastPreviewKey = previewKey
        }
    }
    BackHandler(onBack = onBack)

    ODXFiShell(
        viewModel = viewModel,
        showBattleOverlay = false,
        filterSettings = filterSettings,
        onFilterSettingsChange = onFilterSettingsChange,
        onUp = { selectAction(actionIndex - 1) },
        onDown = { selectAction(actionIndex + 1) },
        onLeft = { page(-1) },
        onRight = { page(1) },
        onA = { activate(actions[actionIndex]) },
        onB = onBack,
        onSelect = {
            if (!busy && shownCorrections.isNotEmpty()) includeSyllableNotes = !includeSyllableNotes
        },
        lcdEdgeToEdgeContent = true,
        lcdContent = {
            Column(Modifier.fillMaxSize().testTag("field-notes-exchange-lcd")) {
                Text(if (incoming == null) "${shareableCount} notes selected for exchange" else
                    currentCandidate?.decision?.label() ?: "incoming syllable notes",
                    color = ExchangeInk, fontSize = 12.sp, lineHeight = 17.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("field-notes-exchange-decision"))
                if (busy || !message.isNullOrBlank()) Text(if (busy) "Working…" else message.orEmpty(),
                    color = ExchangeAccent, fontSize = 11.sp, lineHeight = 15.sp, maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp).testTag("field-notes-exchange-message"))
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = actionList) {
                    items(actions, key = { it.id }) { action ->
                        Row(Modifier.fillMaxWidth().clickable(enabled = action.enabled) {
                            selectedActionId = action.id
                            activate(action)
                        }.background(if (action.id == actions[actionIndex].id) ExchangeRule.copy(alpha = .4f) else Color.Transparent)
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                            .testTag("field-notes-exchange-action:${action.id}")) {
                            Text(if (action.id == actions[actionIndex].id) ">" else "·",
                                color = ExchangeAccent, modifier = Modifier.width(15.dp), fontSize = 12.sp)
                            Text(action.label, color = if (action.enabled) ExchangeInk else ExchangeAccent.copy(alpha = .45f),
                                fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        },
    ) {
        LazyColumn(Modifier.fillMaxSize().padding(16.dp).testTag("field-notes-exchange-crt"), state = previewList,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item("heading") {
                Text(if (incoming == null) "field notes / exchange" else "field notes / incoming preview",
                    color = ExchangeAccent, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
            }
            item("context") {
                Text(if (incoming == null)
                    "Only notes marked share are exported. Marking a note does not send it."
                else "${incoming.notes.size} notes · ${incoming.corrections.size} syllable notes\nFrom ${incoming.senderTrainerId}",
                    color = ExchangeAccent, fontSize = 13.sp, lineHeight = 20.sp)
            }
            if (!message.isNullOrBlank()) item("message") {
                Text(message, color = ExchangeInk, fontSize = 13.sp, lineHeight = 20.sp)
            }
            if (currentNote != null) {
                item("note-heading") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(currentNote.speciesName, color = ExchangeAccent, fontSize = 16.sp)
                        Text(currentNote.title, color = ExchangeInk, fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold, fontSize = 23.sp, lineHeight = 29.sp)
                        Text("entry ${currentNote.id}", color = ExchangeAccent, fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp, modifier = Modifier.testTag("field-notes-exchange-note-id"))
                        Text("${currentNote.author} · revision ${currentNote.revision}", color = ExchangeAccent,
                            fontSize = 12.sp, lineHeight = 18.sp)
                        Text("Original trainer: ${currentNote.originTrainerId}", color = ExchangeAccent,
                            fontSize = 11.sp, lineHeight = 17.sp, modifier = Modifier.testTag("field-notes-exchange-origin"))
                        if (incoming != null) Text(currentCandidate!!.decision.label(), color = ExchangeInk,
                            fontSize = 13.sp, modifier = Modifier.testTag("field-notes-exchange-preview-decision"))
                        else Text(if (currentNote.received) "Received copy · original attribution retained" else "Your local note",
                            color = ExchangeAccent, fontSize = 12.sp)
                    }
                }
                items(currentNote.lines.size, key = { "poem:$it" }) { index ->
                    Text(currentNote.lines[index], color = ExchangeInk, fontFamily = FontFamily.Serif,
                        fontSize = 19.sp, lineHeight = 29.sp)
                }
                if (currentNote.lesson.isNotBlank()) item("lesson") {
                    Text("Lesson\n${currentNote.lesson}", color = ExchangeInk, fontSize = 14.sp, lineHeight = 22.sp)
                }
            } else item("empty") {
                Text(if (incoming != null) "This package has no note pages. Review any syllable notes below."
                else "There are no personal or received notes yet. You can open a package from another trainer.",
                    color = ExchangeInk, fontSize = 15.sp, lineHeight = 23.sp)
            }
            if (shownCorrections.isNotEmpty()) {
                item("corrections-heading") {
                    Text("SYLLABLE NOTES · ${if (includeSyllableNotes) "included" else "not selected"}",
                        color = ExchangeAccent, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
                items(shownCorrections, key = { "correction:${it.id}" }) { correction ->
                    CorrectionExchangePreview(correction)
                }
            }
        }
    }
}

private fun FieldNoteImportDecision.canSave() = this == FieldNoteImportDecision.NEW || this == FieldNoteImportDecision.NEWER

private fun FieldNoteImportDecision.label(): String = when (this) {
    FieldNoteImportDecision.NEW -> "new note · ready to save"
    FieldNoteImportDecision.NEWER -> "newer received revision · review before saving"
    FieldNoteImportDecision.DUPLICATE -> "already saved · no replacement needed"
    FieldNoteImportDecision.OLDER -> "older revision · saved copy kept"
    FieldNoteImportDecision.CONFLICT -> "conflicting copy · saved copy protected"
    FieldNoteImportDecision.OWNED_CONFLICT -> "your original writing · protected"
}

@Composable
private fun CorrectionExchangePreview(correction: SyllableCorrection) {
    Column(Modifier.fillMaxWidth().testTag("field-notes-exchange-correction:${correction.id}"),
        verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("${correction.entryId} · revision ${correction.entryRevision} · line ${correction.lineIndex + 1}",
            color = ExchangeAccent, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 17.sp)
        Text("Original: ${correction.originalLine.ifBlank { "Not supplied" }}", color = ExchangeInk,
            fontSize = 13.sp, lineHeight = 20.sp)
        correction.proposedCount?.let { Text("Suggested count: $it", color = ExchangeInk, fontSize = 13.sp) }
        if (correction.pronunciation.isNotBlank()) Text("Pronunciation: ${correction.pronunciation}",
            color = ExchangeInk, fontSize = 13.sp, lineHeight = 20.sp)
        if (correction.replacementLine.isNotBlank()) Text("Proposed: ${correction.replacementLine}",
            color = ExchangeInk, fontSize = 13.sp, lineHeight = 20.sp)
        Text("${correction.author} · ${correction.originTrainerId}", color = ExchangeAccent,
            fontSize = 11.sp, lineHeight = 17.sp)
    }
}
