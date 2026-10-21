package com.example.overdex.ui.screens.observatory

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.battle.archive.MatchArchive
import com.example.overdex.battle.replay.*
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.components.TerminalHeader
import com.example.overdex.ui.components.TerminalScreen
import com.example.overdex.ui.theme.TerminalGreen
import com.example.overdex.ui.theme.TerminalPurple
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class EditorPage { CLIPS, COMPILATIONS, EDIT }
private data class ClipPlayback(val clip: ReplayExcerpt, val archive: MatchArchive, val index: Int)

/** Library and editorial ordering share the physical shell; all touch actions live on the LCD. */
@Composable
fun ReplayEditorScreen(
    viewModel: PokedexViewModel,
    onBack: () -> Unit,
    onOpenArchives: () -> Unit,
    onUp: (() -> Unit) -> Unit,
    onDown: (() -> Unit) -> Unit,
    onLeft: (() -> Unit) -> Unit,
    onRight: (() -> Unit) -> Unit,
    onA: (() -> Unit) -> Unit,
    onB: (() -> Unit) -> Unit,
    onLcdContentUpdate: ((@Composable () -> Unit)?) -> Unit,
) {
    val context = LocalContext.current
    val store = remember { ReplayExcerptStore(context.filesDir) }
    val scope = rememberCoroutineScope()
    var clips by remember { mutableStateOf(emptyList<ReplayExcerpt>()) }
    var compilations by remember { mutableStateOf(emptyList<ReplayCompilation>()) }
    var page by remember { mutableStateOf(EditorPage.CLIPS) }
    var draft by remember { mutableStateOf<ReplayCompilation?>(null) }
    var dirty by remember { mutableStateOf(false) }
    var selected by remember { mutableIntStateOf(0) }
    var action by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var nameDialog by remember { mutableStateOf<String?>(null) }
    var discardDialog by remember { mutableStateOf(false) }
    var queue by remember { mutableStateOf(emptyList<ReplayExcerpt>()) }
    var playback by remember { mutableStateOf<ClipPlayback?>(null) }
    var completed by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    val listState = rememberLazyListState()

    suspend fun refresh() {
        val snapshot = withContext(Dispatchers.IO) { store.list() to store.compilations() }
        clips = snapshot.first
        compilations = snapshot.second
    }
    fun work(label: String, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        message = label
        scope.launch {
            try { block(); message = null }
            catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                message = e.message ?: "Could not complete this action."
            } finally { busy = false }
        }
    }
    LaunchedEffect(store) {
        busy = true
        try {
            refresh()
            val savedDraft = withContext(Dispatchers.IO) { store.loadDraft() }
            if (savedDraft != null) { draft = savedDraft; dirty = true }
        }
        catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            message = e.message
        } finally { busy = false }
    }
    fun changePage(value: EditorPage) { page = value; selected = 0; action = 0 }
    fun leave() {
        if (busy) return
        if (dirty) discardDialog = true else onBack()
    }
    fun play(index: Int, items: List<ReplayExcerpt> = queue) {
        val clip = items.getOrNull(index) ?: return
        queue = items
        playback = null
        completed = false
        work("OPENING CLIP…") {
            val archive = withContext(Dispatchers.IO) { store.loadSource(clip) }
            val model = withContext(Dispatchers.Default) { MatchReplayModel(archive) }
            check(clip.startMonotonicTimeNanos >= model.startNanos &&
                clip.endMonotonicTimeNanos <= model.endNanos &&
                clip.endMonotonicTimeNanos > clip.startMonotonicTimeNanos) { "Clip timing is outside its source replay." }
            playback = ClipPlayback(clip, archive, index)
        }
    }
    fun previewCompilation(value: ReplayCompilation) {
        val byId = clips.associateBy { it.excerptId }
        val items = value.entries.map { byId[it.excerptId] }
        if (items.isEmpty()) message = "ADD A CLIP FIRST"
        else if (items.any { it == null }) message = "A CLIP IS MISSING FROM THIS COMPILATION"
        else play(0, items.filterNotNull())
    }
    fun move(entryId: String, delta: Int) {
        val current = draft ?: return
        val from = current.entries.indexOfFirst { it.id == entryId }
        val to = (from + delta).coerceIn(0, (current.entries.size - 1).coerceAtLeast(0))
        val updated = current.moveEntry(from, to)
        if (updated != current) { draft = updated; selected = to; dirty = true }
    }

    playback?.let { open ->
        key(open.clip.excerptId, open.index) {
            MatchReplayScreen(
                archive = open.archive, onBack = { playback = null },
                onUp = onUp, onDown = onDown, onLeft = onLeft, onRight = onRight,
                onA = onA, onB = onB, onLcdContentUpdate = onLcdContentUpdate,
                playbackWindow = ReplayWindow(open.clip.startMonotonicTimeNanos, open.clip.endMonotonicTimeNanos),
                autoplay = true, allowClipSave = false,
                initialPlaybackSpeed = playbackSpeed, onPlaybackSpeedChange = { playbackSpeed = it },
                playbackLabel = "${if (completed) "FINISHED · " else ""}${open.index + 1}/${queue.size} · ${open.clip.title ?: "Clip"}",
                onPlaybackComplete = {
                    if (open.index + 1 < queue.size) play(open.index + 1) else completed = true
                },
                resolveSpeciesId = { viewModel.getPokemonByName(it)?.id },
                resolveSpeciesTypes = { viewModel.getPokemonByName(it)?.types.orEmpty() },
                resolveFastMoves = { viewModel.getPokemonByName(it)?.fastMoves.orEmpty() },
                resolveChargedMoves = { viewModel.getPokemonByName(it)?.chargedMoves.orEmpty() },
                resolveArchivedSpeciesCrops = { _, _ -> open.clip.recoveredIdentities },
                resolveFastMoveType = { species, name ->
                    viewModel.getPokemonByName(species)?.fastMoves?.firstOrNull { it.name.equals(name, true) }?.type
                },
                resolveChargedMoveType = { species, name ->
                    viewModel.getPokemonByName(species)?.chargedMoves?.firstOrNull { it.name.equals(name, true) }?.type
                },
            )
        }
        BackHandler { playback = null }
        return
    }

    val clip = clips.getOrNull(selected)
    val compilation = compilations.getOrNull(selected)
    val entry = draft?.entries?.getOrNull(selected)
    val count = when (page) {
        EditorPage.CLIPS -> clips.size
        EditorPage.COMPILATIONS -> compilations.size
        EditorPage.EDIT -> draft?.entries?.size ?: 0
    }
    val actions = when (page) {
        EditorPage.CLIPS -> listOf("PREVIEW CLIP", "ADD TO COMPILATION", "NAME / NOTES", "COMPILATIONS", "OPEN MATCHES", "BACK")
        EditorPage.COMPILATIONS -> listOf("PLAY", "EDIT", "NEW COMPILATION", "CLIPS", "BACK")
        EditorPage.EDIT -> listOf("PREVIEW", "MOVE EARLIER", "MOVE LATER", "REMOVE FROM ORDER", "ADD CLIPS", "SAVE COMPILATION", "BACK")
    }
    LaunchedEffect(page, selected, count) {
        if (count > 0) listState.animateScrollToItem(selected.coerceIn(0, count - 1))
    }
    fun act(index: Int) {
        if (busy) return
        action = index
        when (page) {
            EditorPage.CLIPS -> when (index) {
                0 -> clip?.let { play(0, listOf(it)) }
                1 -> clip?.let {
                    val current = draft ?: ReplayCompilation(title = "Compilation ${compilations.size + 1}")
                    draft = current.copy(entries = current.entries + ReplayCompilationEntry(excerptId = it.excerptId))
                    dirty = true
                    changePage(EditorPage.EDIT)
                    selected = draft!!.entries.lastIndex
                }
                2 -> if (clip != null) nameDialog = "clip"
                3 -> changePage(if (draft != null) EditorPage.EDIT else EditorPage.COMPILATIONS)
                4 -> work("KEEPING DRAFT…") {
                    draft?.let { withContext(Dispatchers.IO) { store.saveDraft(it) } }
                    onOpenArchives()
                }
                else -> leave()
            }
            EditorPage.COMPILATIONS -> when (index) {
                0 -> compilation?.let(::previewCompilation)
                1 -> compilation?.let { draft = it; dirty = false; changePage(EditorPage.EDIT) }
                2 -> { draft = ReplayCompilation(title = "Compilation ${compilations.size + 1}"); dirty = true; changePage(EditorPage.EDIT) }
                3 -> changePage(EditorPage.CLIPS)
                else -> leave()
            }
            EditorPage.EDIT -> when (index) {
                0 -> draft?.let(::previewCompilation)
                1 -> entry?.let { move(it.id, -1) }
                2 -> entry?.let { move(it.id, 1) }
                3 -> entry?.let {
                    draft = draft!!.copy(entries = draft!!.entries.filterNot { other -> other.id == it.id })
                    dirty = true
                    selected = (selected - 1).coerceAtLeast(0)
                }
                4 -> changePage(EditorPage.CLIPS)
                5 -> if (draft?.entries?.isNotEmpty() == true) nameDialog = "compilation" else message = "ADD A CLIP FIRST"
                else -> if (dirty) discardDialog = true else { draft = null; changePage(EditorPage.COMPILATIONS) }
            }
        }
    }
    SideEffect {
        onUp { if (!busy) selected = (selected - 1).coerceAtLeast(0) }
        onDown { if (!busy) selected = (selected + 1).coerceAtMost((count - 1).coerceAtLeast(0)) }
        onLeft { action = (action + actions.size - 1) % actions.size }
        onRight { action = (action + 1) % actions.size }
        onA { act(action.coerceAtMost(actions.lastIndex)) }
        onB { leave() }
    }
    BackHandler { leave() }
    if (discardDialog) AlertDialog(
        onDismissRequest = { discardDialog = false }, title = { Text("Unsaved compilation") },
        text = { Text("Keep editing, or discard the changes to this compilation? Your saved clips remain in the library.") },
        confirmButton = { TextButton(onClick = { discardDialog = false }) { Text("KEEP EDITING") } },
        dismissButton = { TextButton(onClick = {
            discardDialog = false
            work("CLOSING DRAFT…") {
                withContext(Dispatchers.IO) { store.clearDraft() }
                dirty = false; draft = null; onBack()
            }
        }) { Text("DISCARD CHANGES") } },
    )
    if (nameDialog != null) {
        val editingClip = nameDialog == "clip"
        ReplayNameDialog(if (editingClip) "Clip name and notes" else "Save compilation",
            if (editingClip) clip?.title.orEmpty() else draft?.title.orEmpty(),
            if (editingClip) clip?.notes.orEmpty() else null,
            onDismiss = { nameDialog = null },
            onSave = { title, notes ->
                val chosenClip = clip
                val chosenDraft = draft
                nameDialog = null
                work("SAVING…") {
                    withContext(Dispatchers.IO) {
                        if (editingClip) store.updateDetails(chosenClip!!, title, notes)
                        else {
                            store.saveCompilation(chosenDraft!!.copy(title = title))
                            store.clearDraft()
                        }
                    }
                    refresh()
                    if (!editingClip) { dirty = false; draft = null; changePage(EditorPage.COMPILATIONS) }
                }
            })
    }

    PublishMatchLcd(onLcdContentUpdate) {
        MatchLcdColumn {
            MatchLcdText(when (page) {
                EditorPage.CLIPS -> clip?.title ?: "CLIP LIBRARY"
                EditorPage.COMPILATIONS -> compilation?.title ?: "COMPILATIONS"
                EditorPage.EDIT -> "${draft?.title.orEmpty()}${if (dirty) " *" else ""}"
            })
            if (busy) { MatchLcdText(message ?: "WORKING…"); LinearProgressIndicator(Modifier.fillMaxWidth()) }
            else message?.let { MatchLcdText(it) }
            // Keep progress visible even when the user was scrolled to a lower action.
            if (!busy) {
                MatchLcdText("↑/↓ ITEM   ←/→ ACTION   A SELECT")
                actions.forEachIndexed { index, title ->
                    MatchLcdButton(title, { act(index) }, selected = action == index)
                }
            }
            if (page == EditorPage.EDIT && !busy) {
                MatchLcdText("HOLD AND DRAG A CLIP TO REORDER")
                draft?.entries.orEmpty().forEach { item ->
                    key(item.id) {
                        val index = draft!!.entries.indexOfFirst { it.id == item.id }
                        val name = clips.firstOrNull { it.excerptId == item.excerptId }?.title ?: "Missing clip"
                        val step = with(LocalDensity.current) { 40.dp.toPx() }
                        val latestMove by rememberUpdatedState<(Int) -> Unit>({ delta -> move(item.id, delta) })
                        Text("${index + 1}. $name", color = TerminalGreen, fontSize = 11.sp,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)
                                .border(1.dp, TerminalGreen.copy(alpha = .35f)).padding(6.dp)
                                .pointerInput(item.id) {
                                    var distance = 0f
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { distance = 0f },
                                        onDrag = { change, delta ->
                                            change.consume()
                                            distance += delta.y
                                            if (distance >= step) { latestMove(1); distance -= step }
                                            else if (distance <= -step) { latestMove(-1); distance += step }
                                        },
                                    )
                                })
                    }
                }
            }
        }
    }
    TerminalScreen {
        TerminalHeader(if (page == EditorPage.EDIT) "COMPILATION EDITOR" else if (page == EditorPage.CLIPS) "CLIP LIBRARY" else "COMPILATIONS")
        if (count == 0) Text(
            if (page == EditorPage.CLIPS) "Open a match, choose Open Replay, then mark a start and end on its LCD to save your first clip."
            else if (page == EditorPage.EDIT) "Choose ADD CLIPS to build this compilation."
            else "Create a compilation to arrange clips from different matches.",
            color = TerminalGreen, modifier = Modifier.padding(8.dp))
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (page) {
                EditorPage.CLIPS -> itemsIndexed(clips, key = { _, it -> it.excerptId }) { index, value ->
                    LibraryRow(value.title ?: "Untitled clip",
                        "${clipDuration(value)} · ${dateLabel(value.createdAtMillis)}\n${value.notes}".trim(),
                        selected == index)
                }
                EditorPage.COMPILATIONS -> itemsIndexed(compilations, key = { _, it -> it.id }) { index, value ->
                    LibraryRow(value.title, "${value.entries.size} clips · ${dateLabel(value.updatedAtMillis)}", selected == index)
                }
                EditorPage.EDIT -> itemsIndexed(draft?.entries.orEmpty(), key = { _, it -> it.id }) { index, value ->
                    val source = clips.firstOrNull { it.excerptId == value.excerptId }
                    LibraryRow("${index + 1}. ${source?.title ?: "Missing clip"}",
                        source?.let { "${clipDuration(it)}\n${it.notes}".trim() } ?: "Source unavailable", selected == index)
                }
            }
        }
    }
}

@Composable
private fun LibraryRow(title: String, detail: String, selected: Boolean) {
    Column(Modifier.fillMaxWidth().background(if (selected) TerminalGreen.copy(alpha = .12f) else Color.Transparent)
        .border(1.dp, if (selected) TerminalGreen else Color.Transparent).padding(8.dp)) {
        Text(title, color = if (selected) TerminalGreen else TerminalPurple, fontSize = 18.sp)
        if (detail.isNotBlank()) Text(detail, color = TerminalGreen, fontSize = 13.sp)
    }
}

private fun clipDuration(clip: ReplayExcerpt): String =
    String.format(Locale.ROOT, "%.1f s", (clip.endMonotonicTimeNanos - clip.startMonotonicTimeNanos) / 1e9)
private fun dateLabel(at: Long): String =
    if (at <= 0) "Earlier clip" else SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()).format(Date(at))
