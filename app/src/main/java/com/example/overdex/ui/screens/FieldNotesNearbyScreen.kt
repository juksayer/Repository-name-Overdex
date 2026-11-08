package com.example.overdex.ui.screens

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.overdex.data.FieldNoteTransferCodec
import com.example.overdex.data.FieldNotesNearbyExchange
import com.example.overdex.data.SharedPreferencesPartnerRepository
import com.example.overdex.data.TrainerRepository
import com.example.overdex.ui.ODXFi.ODXFiShell
import com.example.overdex.ui.PokedexViewModel
import com.example.overdex.ui.components.FilterSettings
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val NearbyJournalInk = Color(0xFFD8E8C5)
private val NearbyJournalAccent = Color(0xFF9FBF85)
private val NearbyJournalRule = Color(0xFF3D5839)
private enum class NearbyJournalMode { RECEIVE, FIND }
private data class NearbyJournalAction(val id: String, val label: String, val enabled: Boolean = true, val perform: () -> Unit)

/** Foreground-only exchange; the parent owns packet preparation and the explicit import preview. */
@Composable
fun FieldNotesNearbyScreen(
    viewModel: PokedexViewModel,
    filterSettings: FilterSettings,
    onFilterSettingsChange: (FilterSettings) -> Unit,
    onPreparePackage: (includeCorrections: Boolean) -> ByteArray,
    onReceived: (ByteArray) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val trainers = remember(appContext) { TrainerRepository(appContext) }
    val partners = remember(appContext) { SharedPreferencesPartnerRepository(appContext) }
    val identity by trainers.identity.collectAsState()
    val partner by partners.partner.collectAsState()
    val exchange = remember(appContext) { FieldNotesNearbyExchange(appContext) }
    val state by exchange.state.collectAsState()
    var selectedId by rememberSaveable { mutableStateOf("receive") }
    var includeCorrections by rememberSaveable { mutableStateOf(false) }
    var permissionMode by remember { mutableStateOf<NearbyJournalMode?>(null) }
    var lastMode by remember { mutableStateOf<NearbyJournalMode?>(null) }
    var foreground by remember(lifecycleOwner) { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var stopped by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    val lcd = rememberLazyListState()
    val crt = rememberScrollState()
    val scope = rememberCoroutineScope()
    val local = identity ?: trainers.getIdentity()
    val linked = partner
    val linkedId = linked?.trainerId?.let(::nearbyJournalUuid)
    val canExchange = linkedId != null && linkedId != local.trainerId.toString()
    val localName = local.displayName?.takeIf { it.isNotBlank() } ?: "Trainer"
    val linkedName = linked?.displayName?.takeIf { it.isNotBlank() } ?: "Linked trainer"

    fun missingPermissions() = FieldNotesNearbyExchange.requiredPermissions(Build.VERSION.SDK_INT, appContext.applicationInfo.targetSdkVersion)
        .filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }

    fun start(mode: NearbyJournalMode) {
        if (!foreground) {
            localError = "Nearby exchange stays stopped while this screen is inactive. Choose receive or find again."
            return
        }
        val expected = linkedId
        if (!canExchange || expected == null) {
            localError = "Link a real trainer through Profile's QR exchange first."
            return
        }
        stopped = false
        localError = null
        lastMode = mode
        if (mode == NearbyJournalMode.RECEIVE) exchange.startHosting(local.trainerId.toString(), localName, expected)
        else exchange.startDiscovering(local.trainerId.toString(), localName, expected)
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        val mode = permissionMode
        permissionMode = null
        if (missingPermissions().isNotEmpty())
            localError = "Nearby permission was not granted. Nothing is being advertised or discovered. You can try again."
        else if (mode != null && foreground) start(mode)
    }
    fun request(mode: NearbyJournalMode) {
        if (!canExchange || permissionMode != null) return
        localError = null
        val missing = missingPermissions()
        if (missing.isEmpty()) start(mode)
        else {
            exchange.close()
            permissionMode = mode
            try { permissions.launch(missing.toTypedArray()) }
            catch (_: Exception) {
                permissionMode = null
                localError = "The nearby permission request could not be opened. Choose receive or find to try again."
            }
        }
    }
    fun stop() {
        permissionMode = null
        exchange.close()
        stopped = true
    }
    fun back() { stop(); onBack() }
    fun send() {
        localError = null
        try {
            val bytes = onPreparePackage(includeCorrections)
            when {
                bytes.isEmpty() -> localError = "There are no shareable field notes in this packet."
                bytes.size > FieldNotesNearbyExchange.MAX_BYTES -> localError = "Use file exchange for larger packages."
                else -> {
                    val packet = FieldNoteTransferCodec.decode(bytes)
                    if (packet.notes.isEmpty() && packet.corrections.isEmpty())
                        localError = "There are no shareable field notes or selected annotations in this packet."
                    else exchange.send(bytes)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            localError = error.message?.takeIf { it.isNotBlank() } ?: "The journal packet could not be prepared."
        }
    }

    DisposableEffect(lifecycleOwner, exchange) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> foreground = true
                Lifecycle.Event.ON_STOP -> { foreground = false; permissionMode = null; exchange.close(); stopped = true }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); exchange.close() }
    }
    LaunchedEffect(state.pendingBytes) {
        if (state.pendingBytes != null) {
            // Capture the code-confirmed peer before closing clears the transport state.
            val authenticatedTrainerId = state.peerTrainerId
            val bytes = exchange.consumeIncoming()
            if (bytes != null) {
                // Consume even a rejected packet, and stop the radio before opening any preview.
                exchange.close()
                try {
                    val packet = FieldNoteTransferCodec.decode(bytes)
                    require(authenticatedTrainerId != null &&
                        packet.senderTrainerId.equals(authenticatedTrainerId, ignoreCase = true)) {
                        "This packet's sender does not match the trainer whose connection code you confirmed. Nothing was imported."
                    }
                    onReceived(bytes)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    localError = error.message?.takeIf { it.isNotBlank() }
                        ?: "The received journal packet could not be opened for review."
                }
            }
        }
    }
    LaunchedEffect(linkedId) {
        if (lastMode != null) { stop(); localError = "The linked trainer changed. Choose receive or find again." }
    }
    BackHandler(onBack = ::back)

    val actions = buildList {
        if (canExchange) {
            when {
                state.authDigits != null && !state.connected -> {
                    if (state.awaitingLocalConfirmation) {
                        add(NearbyJournalAction("confirm", "the codes match · confirm") { exchange.confirmAuthentication(true) })
                        add(NearbyJournalAction("reject", "codes differ · reject") { exchange.confirmAuthentication(false) })
                    }
                    add(NearbyJournalAction("cancel", "cancel nearby connection", perform = ::stop))
                }
                state.connected -> {
                    add(NearbyJournalAction("annotations", "${if (includeCorrections) "[✓]" else "[ ]"} include my annotations", !state.sending) {
                        includeCorrections = !includeCorrections
                    })
                    add(NearbyJournalAction("send", if (state.sending) "sending…" else "send my shareable notes", !state.sending, ::send))
                    add(NearbyJournalAction("cancel", "disconnect", perform = ::stop))
                }
                else -> {
                    add(NearbyJournalAction("receive", "receive nearby", permissionMode == null) { request(NearbyJournalMode.RECEIVE) })
                    add(NearbyJournalAction("find", "find linked trainer", permissionMode == null) { request(NearbyJournalMode.FIND) })
                    state.peers.forEach { peer ->
                        add(NearbyJournalAction("peer:${peer.endpointId}", "connect · ${peer.name}") { exchange.connect(peer.endpointId) })
                    }
                    if ((state.error != null || localError != null || stopped) && lastMode != null) {
                        add(NearbyJournalAction("retry", "try ${if (lastMode == NearbyJournalMode.RECEIVE) "receiving" else "finding"} again", permissionMode == null) {
                            lastMode?.let(::request)
                        })
                    }
                    if (state.running) add(NearbyJournalAction("cancel", "stop nearby exchange", perform = ::stop))
                }
            }
        }
        add(NearbyJournalAction("back", "return to journal", perform = ::back))
    }
    val selectedIndex = actions.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    fun move(delta: Int) { selectedId = actions[(selectedIndex + delta).coerceIn(0, actions.lastIndex)].id }
    fun activate() { actions.getOrNull(selectedIndex)?.takeIf { it.enabled }?.perform?.invoke() }
    LaunchedEffect(selectedIndex, actions.map { it.id }) { lcd.animateScrollToItem(selectedIndex) }

    ODXFiShell(
        viewModel = viewModel, showBattleOverlay = false,
        filterSettings = filterSettings, onFilterSettingsChange = onFilterSettingsChange,
        onUp = { move(-1) }, onDown = { move(1) }, onA = ::activate, onB = ::back,
        onLeft = { scope.launch { crt.animateScrollBy(-crt.viewportSize * 0.65f) } },
        onRight = { scope.launch { crt.animateScrollBy(crt.viewportSize * 0.65f) } },
        // Start is explicit navigation here; it never starts or stops Droidball.
        onStart = ::activate, startLabel = "START",
        lcdEdgeToEdgeContent = true,
        lcdContent = {
            LazyColumn(Modifier.fillMaxSize().testTag("field-notes-nearby-actions"), state = lcd) {
                items(actions, key = { it.id }) { action ->
                    Text("${if (actions[selectedIndex].id == action.id) "> " else ""}${action.label}",
                        color = if (action.enabled) NearbyJournalInk else NearbyJournalAccent.copy(alpha = 0.45f),
                        fontSize = 12.sp, lineHeight = 18.sp,
                        modifier = Modifier.fillMaxWidth()
                            .background(if (actions[selectedIndex].id == action.id) NearbyJournalRule.copy(alpha = 0.4f) else Color.Transparent)
                            .clickable(enabled = action.enabled) { selectedId = action.id; action.perform() }
                            .padding(horizontal = 9.dp, vertical = 12.dp).testTag("field-notes-nearby-action:${action.id}"))
                }
            }
        },
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(crt).padding(17.dp).testTag("field-notes-nearby-crt"),
            verticalArrangement = Arrangement.spacedBy(15.dp)) {
            Text("nearby journal exchange", color = NearbyJournalAccent, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            Text(if (linked == null) "Link a trainer first" else linkedName, color = NearbyJournalInk,
                fontFamily = FontFamily.Serif, fontSize = 25.sp, lineHeight = 31.sp)
            when {
                linked == null -> Text("Open Profile and scan the other trainer's Overdex QR code. Then return here. Nearby journal exchange is limited to your linked trainer.",
                    color = NearbyJournalInk, fontSize = 16.sp, lineHeight = 24.sp)
                !canExchange -> Text("This link is a test identity or your own trainer ID. Link a different, real trainer through Profile's QR exchange before using nearby journal exchange.",
                    color = NearbyJournalInk, fontSize = 16.sp, lineHeight = 24.sp)
                else -> {
                    Text("You · $localName", color = NearbyJournalAccent, fontSize = 12.sp)
                    Text(if (permissionMode != null) "Waiting for nearby permission…" else state.status,
                        color = NearbyJournalInk, fontSize = 16.sp, lineHeight = 24.sp,
                        modifier = Modifier.testTag("field-notes-nearby-status"))
                    if (state.authDigits != null && !state.connected) {
                        Text(state.authDigits!!, color = NearbyJournalInk, fontFamily = FontFamily.Monospace,
                            fontSize = 44.sp, letterSpacing = 8.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth().border(1.dp, NearbyJournalRule).padding(12.dp)
                                .testTag("field-notes-nearby-auth-code"))
                        Text("Compare these four digits on both phones. Both trainers must confirm only when the codes match.",
                            color = NearbyJournalInk, fontSize = 15.sp, lineHeight = 23.sp)
                        Text("Trainer names and linked IDs are self-declared. The name alone does not prove which device is connecting; compare the code with the person beside you.",
                            color = NearbyJournalAccent, fontSize = 12.sp, lineHeight = 19.sp)
                    } else if (state.connected) {
                        Text("The connection is confirmed. Only your notes marked shareable are included. Annotations are optional and off until you select them.",
                            color = NearbyJournalInk, fontSize = 15.sp, lineHeight = 23.sp)
                        Text(if (includeCorrections) "Annotations included" else "Annotations not included", color = NearbyJournalAccent, fontSize = 12.sp,
                            modifier = Modifier.testTag("field-notes-nearby-annotation-setting"))
                        if (state.sent) Text("Delivered to their device. They still choose whether to save it.", color = NearbyJournalInk,
                            fontSize = 15.sp, lineHeight = 23.sp, modifier = Modifier.testTag("field-notes-nearby-delivered"))
                    } else {
                        Text("One trainer chooses receive nearby; the other chooses find linked trainer. You can exchange without an internet connection.",
                            color = NearbyJournalInk, fontSize = 15.sp, lineHeight = 23.sp)
                        if (state.peers.isNotEmpty()) Text("${state.peers.size} linked device${if (state.peers.size == 1) "" else "s"} found · choose in the LCD",
                            color = NearbyJournalAccent, fontSize = 12.sp)
                        if (stopped) Text("Nearby is stopped. It will not restart automatically.", color = NearbyJournalAccent, fontSize = 12.sp)
                    }
                    (localError ?: state.error)?.let { error ->
                        Text(error, color = Color(0xFFFFC897), fontSize = 14.sp, lineHeight = 22.sp,
                            modifier = Modifier.testTag("field-notes-nearby-error"))
                    }
                    Text("Incoming notes open for review. Receiving never saves them automatically. Author names are reported by the packet; they are not digital signatures.",
                        color = NearbyJournalAccent, fontSize = 12.sp, lineHeight = 19.sp)
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

private fun nearbyJournalUuid(value: String): String? = try {
    UUID.fromString(value).toString().takeIf { it.equals(value, ignoreCase = true) }
} catch (_: IllegalArgumentException) { null }
