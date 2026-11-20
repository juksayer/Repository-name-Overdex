package com.example.overdex.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.overdex.comms.*
import com.example.overdex.battle.archive.ArchiveDirectoryManager
import com.example.overdex.battle.history.BattleRecordStore
import com.example.overdex.battle.replay.ReplayReferenceLookup
import com.example.overdex.battle.replay.prepareReplay
import com.example.overdex.battle.replay.ReplayShare
import com.example.overdex.battle.replay.SharedReplay
import com.example.overdex.battle.replay.ReplayCorrectionTransfer
import com.example.overdex.data.TrainerRepository
import com.example.overdex.ui.ODXFi.ODXFiShell
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.components.*
import com.example.overdex.ui.theme.TerminalGreen
import com.example.overdex.ui.theme.TerminalDimGreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private data class CommsAction(val label: String, val enabled: Boolean = true, val run: () -> Unit)

/** CRT is a display; every action is also reachable through the LCD and physical controls. */
@Composable
fun TrainerCommsScreen(
    services: TrainerCommsServices,
    trainerRepository: TrainerRepository,
    viewModel: PokedexViewModel,
    onBack: () -> Unit,
    archiveDirectory: ArchiveDirectoryManager,
    onOpenSharedReplay: (accountId: String, replay: SharedReplay) -> Unit,
) {
    val repository = services.repository
    val context = LocalContext.current
    val state by repository.state.collectAsState()
    val email by services.email.collectAsState()
    val localIdentity by trainerRepository.identity.collectAsState()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val keyboard = rememberTerminalKeyboardController()
    val listState = rememberLazyListState()
    var selectedRow by rememberSaveable { mutableIntStateOf(0) }
    var selectedAction by rememberSaveable { mutableIntStateOf(0) }
    var choosingMembers by rememberSaveable { mutableStateOf(false) }
    var selectedMembers by remember { mutableStateOf(setOf<String>()) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val conversation = state.selectedConversation
    var draft by remember(state.accountId, state.selectedConversationId) {
        mutableStateOf(state.snapshot.drafts[state.selectedConversationId].orEmpty())
    }
    val profile = state.snapshot.profile
    val conversations = state.snapshot.conversations.sortedByDescending { item ->
        state.snapshot.messages.filter { it.conversationId == item.id }.maxOfOrNull { it.sentAt } ?: item.createdAt
    }
    val messages = state.visibleMessages
    val contacts = state.snapshot.contacts.sortedBy { it.displayName.lowercase() }
    var outgoingConversationId by remember { mutableStateOf<String?>(null) }
    var outgoingAccountId by remember { mutableStateOf<String?>(null) }
    var shareChoices by remember { mutableStateOf(emptyList<ArchiveDirectoryManager.ArchiveEntry>()) }
    var replayCodeToCopy by remember(state.accountId) { mutableStateOf<String?>(null) }
    var fileToSave by remember { mutableStateOf<ByteArray?>(null) }
    val saveCorrectionFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val bytes = fileToSave
        fileToSave = null
        if (uri != null && bytes != null) {
            runCatching { requireNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(bytes) } }
                .onSuccess { notice = "Correction file saved. Open the matching Replay to import it." }
                .onFailure { notice = "Could not save the correction file." }
        }
    }
    val attachCorrectionFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = outgoingConversationId
        val accountId = outgoingAccountId
        outgoingConversationId = null
        outgoingAccountId = null
        if (uri != null && id != null) {
            scope.launch {
                try {
                    val message = withContext(Dispatchers.IO) {
                        val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { ReplayCorrectionTransfer.readBounded(it) }
                        ReplayCorrectionTransfer.toMessage(bytes)
                    }
                    check(accountId != null && repository.state.value.accountId == accountId) { "Account changed while choosing a file." }
                    withContext(Dispatchers.IO) { repository.sendText(id, message, expectedAccountId = accountId) }
                    notice = "Correction file queued."
                } catch (e: Exception) { if (e is CancellationException) throw e; notice = e.message ?: "Could not send the correction file." }
            }
        }
    }

    fun work(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        notice = null
        scope.launch {
            try { withContext(Dispatchers.IO) { block() } }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                notice = commsErrorMessage(e)
            } finally { busy = false }
        }
    }
    fun copyReplayCode(uri: android.net.Uri, account: String, conversationId: String) {
        dialog = null
        work {
            withContext(Dispatchers.Main) { notice = "Preparing Replay…" }
            val ready = if (viewModel.replayPreparation.owns(uri)) {
                viewModel.replayPreparation.observe(uri).first { it.complete }
            } else {
                val archive = BattleRecordStore(context).readArchive(uri)
                prepareReplay(context, archive, uri,
                    ReplayReferenceLookup.fromRepository(viewModel::getPokemonByName, viewModel::getAllSpeciesNames), {})
            }
            val replay = requireNotNull(ready.replay) { ready.error ?: "This Replay is unavailable." }
            require(ready.error == null) { ready.error.orEmpty() }
            withContext(Dispatchers.Main) { notice = "Uploading review…" }
            val code = services.replayCodes().copyCode(account, conversationId, ReplayShare.encode(replay))
            withContext(Dispatchers.Main) {
                if (repository.state.value.accountId == account) {
                    clipboard.setText(AnnotatedString(code))
                    replayCodeToCopy = code
                    val readyToSend = repository.state.value.selectedConversationId == conversationId && draft.isBlank()
                    if (readyToSend) {
                        draft = code
                        selectedAction = 2
                    }
                    notice = if (readyToSend) "Replay code ready to send." else "Replay code copied."
                }
            }
        }
    }
    var exportedReplayTarget by rememberSaveable { mutableStateOf<List<String>?>(null) }
    val openExportedReplay = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = exportedReplayTarget
        exportedReplayTarget = null
        if (uri != null && target != null && repository.state.value.accountId == target[0]) {
            copyReplayCode(uri, target[0], target[1])
        }
    }
    suspend fun openReplayCode(code: String, account: String) {
        val replay = withContext(Dispatchers.IO) { services.replayCodes().open(account, code) }
        if (repository.state.value.accountId == account) onOpenSharedReplay(account, replay)
    }
    fun goBack() {
        when {
            keyboard.isVisible -> keyboard.close()
            conversation != null -> work { repository.selectConversation(null) }
            choosingMembers -> choosingMembers = false
            else -> onBack()
        }
    }
    fun chooseRow() {
        if (choosingMembers) {
            contacts.getOrNull(selectedRow)?.let { contact ->
                selectedMembers = if (contact.accountId in selectedMembers) selectedMembers - contact.accountId
                    else if (selectedMembers.size < 7) selectedMembers + contact.accountId else selectedMembers
            }
        } else if (conversation == null) conversations.getOrNull(selectedRow)?.let {
            work { repository.selectConversation(it.id) }
        }
    }
    fun createConversation(title: String) {
        val members = selectedMembers.toList()
        work {
            repository.createConversation(members, title)
            withContext(Dispatchers.Main) { choosingMembers = false; selectedMembers = emptySet() }
        }
    }
    fun inputKey(key: String) {
        val next = when (key) {
            "DELETE", "DEL", "⌫", "BACKSPACE", "←" -> draft.dropLast(1)
            "SPACE", "SPC", "␣" -> "$draft "
            else -> draft + key
        }.take(CommsRepository.MAX_TEXT_LENGTH)
        draft = next
    }

    LaunchedEffect(state.accountId) {
        choosingMembers = false
        selectedMembers = emptySet()
        keyboard.close()
        dialog = null
    }
    LaunchedEffect(state.selectedConversationId, choosingMembers) {
        selectedRow = if (conversation != null) (messages.size - 1).coerceAtLeast(0) else 0
        selectedAction = 0
        keyboard.close()
        listState.scrollToItem(0)
    }
    LaunchedEffect(draft, state.selectedConversationId) {
        val id = state.selectedConversationId
        if (id != null && conversation?.kind != ConversationKind.LEGACY) {
            // Keep draft writes ordered with the outbox. No network operation occurs here.
            try { withContext(Dispatchers.IO) { repository.saveDraft(id, draft, state.accountId) } }
            catch (e: Exception) { if (e is CancellationException) throw e else notice = "The draft could not be saved." }
        }
    }
    val rowCount = when { conversation != null -> messages.size; choosingMembers -> contacts.size; else -> conversations.size }
    LaunchedEffect(rowCount) { selectedRow = selectedRow.coerceIn(0, (rowCount - 1).coerceAtLeast(0)) }
    LaunchedEffect(selectedRow, rowCount) { if (rowCount > 0) listState.animateScrollToItem(selectedRow) }
    BackHandler(enabled = dialog == null) { goBack() }

    val actions = when {
        state.accountId == null -> listOf(
            CommsAction("SIGN IN", services.available) { dialog = "signin" },
            CommsAction("CREATE ACCOUNT", services.available) { dialog = "create" },
            CommsAction("BACK") { onBack() })
        profile == null -> listOf(
            CommsAction("SET NAME + CODE") { dialog = "profile" },
            CommsAction("ACCOUNT") { dialog = "account" },
            CommsAction("RECONNECT") { work { services.reconnect() } },
            CommsAction("BACK") { onBack() })
        choosingMembers -> listOf(
            CommsAction("SELECT", contacts.isNotEmpty()) { chooseRow() },
            CommsAction("ADD TRAINER") { dialog = "contact" },
            CommsAction("CREATE", selectedMembers.isNotEmpty()) {
                if (selectedMembers.size == 1) createConversation("") else dialog = "group"
            },
            CommsAction("BACK") { choosingMembers = false })
        conversation != null -> {
            val selected = messages.getOrNull(selectedRow)
            listOf(
                CommsAction("WRITE", conversation.kind != ConversationKind.LEGACY) { keyboard.open() },
                CommsAction("PASTE", conversation.kind != ConversationKind.LEGACY) {
                    clipboard.getText()?.text?.let { pasted ->
                        val code = runCatching { TrainerReplayCode.parse(pasted) }.getOrNull()
                        if (code == null || runCatching { TrainerReplayCode.parse(draft) }.getOrNull() != code) {
                            draft = (draft + pasted).take(CommsRepository.MAX_TEXT_LENGTH)
                        }
                    }
                },
                CommsAction("SEND", conversation.kind != ConversationKind.LEGACY && draft.isNotBlank()) {
                    val body = draft
                    work {
                        repository.sendText(conversation.id, body)
                        withContext(Dispatchers.Main) { draft = ""; keyboard.close() }
                    }
                },
                CommsAction("COPY REPLAY CODE", conversation.confirmed && conversation.kind != ConversationKind.LEGACY) {
                    work {
                        val choices = archiveDirectory.listArchives()
                        withContext(Dispatchers.Main) {
                            if (repository.state.value.selectedConversationId == conversation.id &&
                                repository.state.value.accountId == state.accountId) {
                                shareChoices = choices
                                dialog = "shareReplay"
                            }
                        }
                    }
                },
                CommsAction("OPEN REPLAY CODE", conversation.kind != ConversationKind.LEGACY) {
                    val code = selected?.text?.takeIf { runCatching { TrainerReplayCode.parse(it) }.isSuccess }
                    val account = state.accountId
                    if (code != null && account != null) {
                        work { withContext(Dispatchers.Main) { openReplayCode(code, account) } }
                    } else dialog = "openReplay"
                },
                CommsAction("SEND CORRECTION FILE", conversation.kind != ConversationKind.LEGACY) {
                    outgoingConversationId = conversation.id
                    outgoingAccountId = state.accountId
                    attachCorrectionFile.launch(arrayOf("application/json", "application/octet-stream"))
                },
                CommsAction("SAVE CORRECTION FILE", selected != null && runCatching { ReplayCorrectionTransfer.fromMessage(selected.text) != null }.getOrDefault(false)) {
                    selected?.let { item ->
                        runCatching { ReplayCorrectionTransfer.fromMessage(item.text) }.getOrNull()?.let { bytes ->
                            fileToSave = bytes
                            saveCorrectionFile.launch("overdex-correction.json")
                        }
                    }
                },
                CommsAction("RETRY", selected?.status == DeliveryStatus.FAILED && selected.senderId == state.accountId) {
                    selected?.let { work { repository.retry(it.id) } }
                },
                CommsAction("BACK") { goBack() })
        }
        else -> listOf(
            CommsAction("OPEN", conversations.isNotEmpty()) { chooseRow() },
            CommsAction("OPEN REPLAY CODE") { dialog = "openReplay" },
            CommsAction("NEW CHAT") { choosingMembers = true; selectedMembers = emptySet() },
            CommsAction("MY CODE") { dialog = "profile" },
            CommsAction("ACCOUNT") { dialog = "account" },
            CommsAction("RECONNECT") { work { services.reconnect() } },
            CommsAction("BACK") { onBack() })
    }
    LaunchedEffect(actions.map { it.label }) { selectedAction = 0 }
    fun activate() { actions.getOrNull(selectedAction)?.takeIf { it.enabled && !busy && !state.loading }?.run?.invoke() }
    val showComposer = conversation != null && conversation.kind != ConversationKind.LEGACY
    val composerActions = if (showComposer) 3 else 0

    ODXFiShell(
        viewModel = viewModel,
        showBattleOverlay = false,
        keyboardController = keyboard,
        keyboardPreviewOnCrt = draft.takeLast(70).ifEmpty { "MESSAGE" },
        onKeyActivated = { key -> keyboard.activateKey(key, ::inputKey) },
        onUp = { if (keyboard.isFocused) keyboard.handleUp() else selectedRow = (selectedRow - 1).coerceAtLeast(0) },
        onDown = { if (keyboard.isFocused) keyboard.handleDown() else selectedRow = (selectedRow + 1).coerceAtMost((rowCount - 1).coerceAtLeast(0)) },
        onLeft = { if (keyboard.isFocused) keyboard.handleLeft() else selectedAction = (selectedAction - 1).coerceAtLeast(0) },
        onRight = { if (keyboard.isFocused) keyboard.handleRight() else selectedAction = (selectedAction + 1).coerceAtMost(actions.lastIndex) },
        onA = { if (keyboard.isFocused) keyboard.handleA(draft, ::inputKey) else activate() },
        onB = ::goBack,
        onSelect = { if (keyboard.isVisible) keyboard.close() else chooseRow() },
        onStart = { if (keyboard.isVisible) keyboard.close() else activate() },
        lcdContent = {
            Column(Modifier.fillMaxSize().padding(horizontal = 3.dp)) {
                if (showComposer) {
                    Row(Modifier.fillMaxWidth()) {
                        actions.take(composerActions).forEachIndexed { index, action ->
                            CommsLcdButton(action, index == selectedAction, busy || state.loading, Modifier.weight(1f)) {
                                selectedAction = index
                            }
                        }
                    }
                    Text(draft.ifEmpty { "Write a message" }, color = TerminalGreen, fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    actions.drop(composerActions).chunked(2).forEachIndexed { row, buttons ->
                        Row(Modifier.fillMaxWidth()) {
                            buttons.forEachIndexed { column, action ->
                                val index = composerActions + row * 2 + column
                                val requester = remember { BringIntoViewRequester() }
                                LaunchedEffect(selectedAction) {
                                    if (index == selectedAction) requester.bringIntoView()
                                }
                                CommsLcdButton(action, index == selectedAction, busy || state.loading,
                                    Modifier.weight(1f).bringIntoViewRequester(requester)) { selectedAction = index }
                            }
                        }
                    }
                }
            }
        },
    ) { _ ->
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            TerminalPathIndicator(path = "/PROFILE/TRAINER COMMS")
            val status = notice ?: if (busy || state.loading) "Working…" else null
            status?.let { Text(it, color = Color(0xFFFFC36B), fontSize = 13.sp) }
            replayCodeToCopy?.let { code ->
                TextButton(onClick = { clipboard.setText(AnnotatedString(code)) }) {
                    Text("Copy $code", color = TerminalGreen, fontSize = 13.sp)
                }
            }
            when {
                state.accountId == null -> {
                    Spacer(Modifier.height(22.dp))
                    TerminalText("TRAINER COMMS", fontSize = 23.sp)
                    Spacer(Modifier.height(16.dp))
                    Text("Sign in to keep your conversations when you change phones. Find other trainers using their 12-digit Pokémon GO code.", color = TerminalGreen)
                    Spacer(Modifier.height(16.dp))
                    Text("Use your Overdex account email and password. Your Pokémon GO password is never requested.", color = TerminalDimGreen)
                }
                profile == null -> {
                    Text("Set your trainer name and Pokémon GO code to get started.", color = TerminalGreen)
                    Text("The code helps other trainers find you. Your account keeps your conversations together if you reroll it.", color = TerminalDimGreen)
                }
                else -> {
                    Text(when { choosingMembers -> "CHOOSE TRAINERS (${selectedMembers.size}/7)";
                        conversation != null -> conversationTitle(conversation, state); else -> "CONVERSATIONS" },
                        color = TerminalGreen, fontSize = 20.sp)
                    if (conversation != null) {
                        Text(if (conversation.kind == ConversationKind.LEGACY)
                            "Read-only history from this device. Recipients and delivery were not recorded."
                            else conversation.memberIds.joinToString(" · ") { memberName(it, state) },
                            color = TerminalDimGreen, fontSize = 12.sp)
                        if (!conversation.confirmed && conversation.kind != ConversationKind.LEGACY)
                            Text("Conversation not yet confirmed online.", color = Color(0xFFFFC36B), fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    if (rowCount == 0) Text(when {
                        choosingMembers -> "Add a trainer by their Pokémon GO code. They must have an Overdex account too."
                        conversation != null -> "No messages yet."
                        else -> "Start a direct conversation or choose several trainers for a group."
                    }, color = TerminalDimGreen)
                    LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when {
                            conversation != null -> itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
                                Column(Modifier.fillMaxWidth().background(if (index == selectedRow) Color(0xFF143D23) else Color.Transparent)
                                    .clickable { selectedRow = index }.padding(8.dp)) {
                                    val mine = message.senderId == state.accountId ||
                                        (conversation.kind == ConversationKind.LEGACY && message.senderId == localIdentity?.trainerId?.toString())
                                    Text((if (mine) "YOU" else message.senderName) + " · " + messageTime(message.sentAt), color = TerminalDimGreen, fontSize = 11.sp)
                                    Text(if (runCatching { ReplayCorrectionTransfer.fromMessage(message.text) != null }.getOrDefault(false))
                                        "CORRECTION FILE · select SAVE CORRECTION FILE" else message.text, color = TerminalGreen, fontSize = 16.sp)
                                    Text(when (message.status) {
                                        DeliveryStatus.PENDING -> "PENDING"
                                        DeliveryStatus.SENT -> "SENT · saved online"
                                        DeliveryStatus.FAILED -> "NOT CONFIRMED · select RETRY"
                                        DeliveryStatus.UNKNOWN -> "DELIVERY UNKNOWN · legacy history"
                                    }, color = if (message.status == DeliveryStatus.FAILED) Color(0xFFFFC36B) else TerminalDimGreen, fontSize = 11.sp)
                                }
                            }
                            choosingMembers -> itemsIndexed(contacts, key = { _, contact -> contact.accountId }) { index, contact ->
                                Text((if (index == selectedRow) "> " else "  ") +
                                    (if (contact.accountId in selectedMembers) "[✓] " else "[ ] ") + contact.displayName +
                                    "\n    " + TrainerCode.display(contact.trainerCode),
                                    color = if (index == selectedRow) TerminalGreen else TerminalDimGreen,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                            }
                            else -> itemsIndexed(conversations, key = { _, item -> item.id }) { index, item ->
                                Column(Modifier.fillMaxWidth().background(if (index == selectedRow) Color(0xFF143D23) else Color.Transparent).padding(8.dp)) {
                                    Text((if (index == selectedRow) "> " else "") + conversationTitle(item, state), color = TerminalGreen, fontSize = 18.sp)
                                    Text(if (item.kind == ConversationKind.LEGACY) "Saved local history" else "${item.memberIds.size} trainers", color = TerminalDimGreen, fontSize = 12.sp)
                                    state.snapshot.messages.lastOrNull { it.conversationId == item.id }?.let {
                                        Text(if (runCatching { ReplayCorrectionTransfer.fromMessage(it.text) != null }.getOrDefault(false))
                                            "Correction file" else it.text, color = TerminalDimGreen, maxLines = 2, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            state.error?.let { Text(it, color = Color(0xFFFFC36B), fontSize = 12.sp) }
        }
    }

    when (dialog) {
        "shareReplay" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Copy Replay Code") },
            text = {
                Column {
                    Text("Choose a match to share its Timeline and reviewed findings with ${conversation?.let { conversationTitle(it, state) }.orEmpty()}. Original images and audio stay on this device.")
                    if (shareChoices.isEmpty()) Text("No saved matches.")
                    LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        itemsIndexed(shareChoices, key = { _, item -> item.name }) { _, item ->
                            TextButton(onClick = {
                                val account = state.accountId
                                val room = conversation
                                if (account != null && room != null) copyReplayCode(item.uri, account, room.id)
                            }) {
                                Text(item.name.substringBefore('_') + "  " +
                                    item.name.substringAfter('_').substringBefore('_').substringBefore(".odxmatch"))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val account = state.accountId
                    val room = conversation
                    if (account != null && room != null) {
                        exportedReplayTarget = listOf(account, room.id)
                        dialog = null
                        openExportedReplay.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                    }
                }) { Text("Choose exported match") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
        "openReplay" -> CommsTextDialog(
            "Open Replay Code", listOf("Replay code"), listOf(
                clipboard.getText()?.text?.takeIf { runCatching { TrainerReplayCode.parse(it) }.isSuccess }.orEmpty()
            ), "Use the account that belongs to the conversation where the Replay was shared.",
            { dialog = null }, replayCodeField = true, onSave = { fields ->
                val account = requireNotNull(state.accountId)
                openReplayCode(fields.single(), account)
            },
        )
        "signin", "create" -> CommsSignInDialog(create = dialog == "create", services = services,
            onClose = { dialog = null }, onNotice = { notice = it })
        "profile" -> CommsTextDialog(
            title = "Your trainer identity",
            labels = listOf("Trainer name", "Pokémon GO trainer code"),
            initial = listOf(profile?.displayName ?: localIdentity?.displayName.orEmpty(),
                profile?.trainerCode ?: localIdentity?.pokemonGoTrainerCode.orEmpty()),
            note = "Share the code with other trainers. Changing it keeps your conversations. This is an entered code, not Pokémon GO account verification.",
            onClose = { dialog = null },
            onSave = { fields ->
                withContext(Dispatchers.IO) { repository.saveProfile(fields[0], fields[1]) }
                trainerRepository.updateDisplayName(fields[0].trim())
                trainerRepository.updatePokemonGoTrainerCode(TrainerCode.normalize(fields[1]))
            })
        "contact" -> CommsTextDialog("Add trainer", listOf("12-digit Pokémon GO code"), listOf(""),
            "The trainer needs to register their code in Overdex first.", { dialog = null },
            onSave = { fields -> withContext(Dispatchers.IO) { repository.addContact(fields.single()) } })
        "group" -> CommsTextDialog("Name the group", listOf("Group name"), listOf(""),
            "${selectedMembers.size + 1} trainers, including you. Membership is fixed for this first version.",
            { dialog = null }, onSave = { fields ->
                withContext(Dispatchers.IO) { repository.createConversation(selectedMembers.toList(), fields.single()) }
                choosingMembers = false; selectedMembers = emptySet()
            })
        "account" -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text("Trainer Comms account") },
            text = { Column {
                Text(email.orEmpty())
                profile?.let { Text("${it.displayName}\n${TrainerCode.display(it.trainerCode)}") }
                Text("Signing out keeps this account’s saved conversations on this device.")
                TextButton(onClick = { profile?.let { clipboard.setText(AnnotatedString(TrainerCode.display(it.trainerCode))) }; dialog = null }) { Text("COPY TRAINER CODE") }
            } },
            confirmButton = { TextButton(onClick = { dialog = null; work { services.signOut() } }) { Text("SIGN OUT") } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("BACK") } })
    }
}

@Composable
private fun CommsLcdButton(action: CommsAction, selected: Boolean, busy: Boolean, modifier: Modifier,
                           onSelect: () -> Unit) {
    TextButton(onClick = { onSelect(); action.run() }, enabled = action.enabled && !busy, modifier = modifier,
        contentPadding = PaddingValues(horizontal = 2.dp)) {
        Text((if (selected) "> " else "") + action.label,
            color = if (action.enabled) TerminalGreen else TerminalDimGreen, fontSize = 12.sp)
    }
}

private fun memberName(id: String, state: CommsState): String = when (id) {
    state.accountId -> state.snapshot.profile?.displayName ?: "You"
    else -> state.snapshot.contacts.find { it.accountId == id }?.displayName ?: "Trainer"
}
private fun conversationTitle(conversation: CommsConversation, state: CommsState): String =
    if (conversation.kind == ConversationKind.DIRECT) conversation.memberIds.firstOrNull { it != state.accountId }
        ?.let { memberName(it, state) } ?: conversation.title else conversation.title
private fun messageTime(time: Long): String = DateTimeFormatter.ofPattern("MMM d HH:mm")
    .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(time))

private fun commsErrorMessage(error: Exception): String {
    val cause = generateSequence<Throwable>(error) { it.cause }.last()
    return when (cause) {
        is IllegalArgumentException -> cause.message ?: "Check the details and try again."
        is com.google.firebase.auth.FirebaseAuthWeakPasswordException -> "Choose a stronger password of at least six characters."
        is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException -> "Check your email and password."
        is com.google.firebase.auth.FirebaseAuthUserCollisionException -> "An account already uses that email. Sign in or reset its password."
        is com.google.firebase.FirebaseNetworkException -> "No connection. Your saved history is still on this device."
        else -> "Trainer Comms could not complete that request. Check your connection and account setup."
    }
}

@Composable
private fun CommsSignInDialog(create: Boolean, services: TrainerCommsServices, onClose: () -> Unit, onNotice: (String) -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    // Passwords are not saved in instance state, preferences, logs, or the conversation store.
    var password by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun submit(reset: Boolean) {
        if (busy) return
        if (!reset && create && password != repeated) { error = "The passwords do not match."; return }
        busy = true; error = null
        scope.launch {
            try {
                if (reset) {
                    services.resetPassword(email)
                    onNotice("If an account uses that email, password reset instructions will arrive there.")
                } else services.signIn(email, password, create)
                password = ""; repeated = ""; onClose()
            } catch (e: Exception) { if (e is CancellationException) throw e else error = commsErrorMessage(e) }
            finally { busy = false }
        }
    }
    AlertDialog(onDismissRequest = { if (!busy) onClose() }, title = { Text(if (create) "Create Overdex account" else "Sign in to Overdex") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Your email stays private. Other trainers find you by your Pokémon GO trainer code.")
            OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), enabled = !busy)
            OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), enabled = !busy)
            if (create) OutlinedTextField(repeated, { repeated = it }, label = { Text("Repeat password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), enabled = !busy)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (!create) TextButton(onClick = { submit(true) }, enabled = email.isNotBlank() && !busy) { Text("RESET PASSWORD") }
        } },
        confirmButton = { TextButton(onClick = { submit(false) }, enabled = !busy && email.isNotBlank() && password.length >= 6) {
            Text(if (create) "CREATE ACCOUNT" else "SIGN IN")
        } }, dismissButton = { TextButton(onClick = onClose, enabled = !busy) { Text("BACK") } })
}

@Composable
private fun CommsTextDialog(title: String, labels: List<String>, initial: List<String>, note: String,
    onClose: () -> Unit, replayCodeField: Boolean = false, onSave: suspend (List<String>) -> Unit) {
    var values by remember { mutableStateOf(initial) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = { if (!busy) onClose() }, title = { Text(title) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(note)
            labels.forEachIndexed { index, label ->
                val trainerCode = !replayCodeField && label.contains("code", ignoreCase = true)
                OutlinedTextField(values[index], { value -> values = values.toMutableList().also { it[index] = value.take(if (trainerCode) 20 else 80) } },
                    label = { Text(label) }, singleLine = true, enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = if (trainerCode) KeyboardType.Number else KeyboardType.Text))
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }, confirmButton = { TextButton(enabled = !busy && values.all { it.isNotBlank() }, onClick = {
        busy = true; error = null
        scope.launch {
            try { onSave(values); onClose() }
            catch (e: Exception) { if (e is CancellationException) throw e else error = commsErrorMessage(e) }
            finally { busy = false }
        }
    }) { Text(if (replayCodeField) "Open" else "Save") } }, dismissButton = { TextButton(onClick = onClose, enabled = !busy) { Text("BACK") } })
}
