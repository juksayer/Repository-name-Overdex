package com.example.overdex.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult as PlayServicesResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.google.android.gms.tasks.Task
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FieldNotesNearbyPeer(val endpointId: String, val name: String, val trainerId: String)

data class FieldNotesNearbyState(
    val status: String = "Nearby exchange is ready.",
    val peers: List<FieldNotesNearbyPeer> = emptyList(),
    val authDigits: String? = null,
    val peerName: String? = null,
    val peerTrainerId: String? = null,
    val connected: Boolean = false,
    val sending: Boolean = false,
    /** Transport delivery only; this never means the recipient saved or imported the note. */
    val sent: Boolean = false,
    val error: String? = null,
    /** Preview-only bytes. Call consumeIncoming for an owned copy; no import happens here. */
    val pendingBytes: ByteArray? = null,
    val awaitingLocalConfirmation: Boolean = false,
    val localConfirmed: Boolean = false,
    val running: Boolean = false,
)

/**
 * One explicitly authenticated, offline journal exchange with the already-linked trainer.
 * Endpoint UUIDs/names are self-declared filters, not identity proof. Both people must compare codes.
 *
 * API basis: developers.google.com/nearby/connections/android/manage-connections and /exchange-data.
 * No FILE/STREAM payloads, automatic acceptance, import, durable save, or background service.
 */
class FieldNotesNearbyExchange internal constructor(
    context: Context,
    private val clientFactory: () -> ConnectionsClient,
    private val availability: () -> Int,
    private val missingPermissions: () -> List<String>,
) : AutoCloseable {
    /** Real-device entry point; the internal constructor only substitutes boundary dependencies. */
    constructor(context: Context) : this(
        context.applicationContext,
        realClientFactory(context.applicationContext),
        realAvailability(context.applicationContext),
        realMissingPermissions(context.applicationContext),
    )
    private val context = context.applicationContext
    private val lock = Any()
    private val _state = MutableStateFlow(FieldNotesNearbyState())
    val state: StateFlow<FieldNotesNearbyState> = _state.asStateFlow()
    private var generation = 0L
    private var session: Session? = null
    private var pendingIncoming: ByteArray? = null

    private class Session(
        val generation: Long,
        val client: ConnectionsClient,
        val localEndpointName: String,
        val expectedPartnerId: String,
        val hosting: Boolean,
    ) {
        val peers = linkedMapOf<String, FieldNotesNearbyPeer>()
        var endpointId: String? = null
        var localConfirmed = false
        var connected = false
        var outgoingPayload: Long? = null
        var incomingPayload: Long? = null
        var incomingBytes: ByteArray? = null
        val completedIncoming = linkedSetOf<Long>()
    }

    fun startHosting(localTrainerId: String, name: String, expectedPartnerId: String) = synchronized(lock) {
        val current = begin(localTrainerId, name, expectedPartnerId, hosting = true) ?: return@synchronized
        _state.value = _state.value.copy(status = "Opening nearby hosting…")
        operation(current, "Nearby hosting could not start.", {
            current.client.startAdvertising(current.localEndpointName, serviceId(), lifecycle(current),
                AdvertisingOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build())
        }) {
            if (current.endpointId == null) _state.value = _state.value.copy(status = "Waiting for the linked trainer nearby.")
        }
    }

    fun startDiscovering(localTrainerId: String, name: String, expectedPartnerId: String) = synchronized(lock) {
        val current = begin(localTrainerId, name, expectedPartnerId, hosting = false) ?: return@synchronized
        _state.value = _state.value.copy(status = "Looking for the linked trainer nearby…")
        operation(current, "Nearby discovery could not start.", {
            current.client.startDiscovery(serviceId(), discovery(current),
                DiscoveryOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build())
        }) { }
    }

    fun connect(endpointId: String) = synchronized(lock) {
        val current = session ?: return@synchronized localError("Start nearby discovery first.")
        if (current.hosting || current.endpointId != null) return@synchronized localError("This exchange already has a selected connection.")
        val peer = current.peers[endpointId] ?: return@synchronized localError("That linked trainer is no longer in the nearby list.")
        current.endpointId = endpointId
        stopDiscoveryAndAdvertising(current)
        _state.value = _state.value.copy(status = "Requesting a connection…", peers = listOf(peer), peerName = peer.name,
            peerTrainerId = peer.trainerId, error = null)
        operation(current, "The nearby connection could not be requested.", {
            current.client.requestConnection(current.localEndpointName, endpointId, lifecycle(current))
        }) { }
    }

    fun confirmAuthentication(accept: Boolean) = synchronized(lock) {
        val current = session ?: return@synchronized
        val endpoint = current.endpointId ?: return@synchronized
        if (!_state.value.awaitingLocalConfirmation || current.localConfirmed) return@synchronized
        if (!accept) {
            quiet { current.client.rejectConnection(endpoint) }
            finish(current, "Connection declined.", null)
            return@synchronized
        }
        // This is the only acceptConnection call. A connection request never accepts itself.
        current.localConfirmed = true
        _state.value = _state.value.copy(status = "Code confirmed here. Waiting for the other trainer…",
            awaitingLocalConfirmation = false, localConfirmed = true, error = null)
        operation(current, "The confirmed nearby connection could not be accepted.", {
            current.client.acceptConnection(endpoint, payloads(current))
        }) { }
    }

    fun send(bytes: ByteArray) = synchronized(lock) {
        val current = session
        val endpoint = current?.endpointId
        if (current == null || endpoint == null || !current.connected || !current.localConfirmed) {
            return@synchronized localError("Both trainers must confirm the matching code before sending.")
        }
        if (bytes.isEmpty() || bytes.size > MAX_BYTES) return@synchronized localError("A nearby journal packet must contain 1–32,768 bytes.")
        if (current.outgoingPayload != null) return@synchronized localError("Wait for the current journal packet to finish sending.")
        val payload = Payload.fromBytes(bytes.copyOf())
        current.outgoingPayload = payload.id
        _state.value = _state.value.copy(status = "Sending journal packet…", sending = true, sent = false, error = null)
        operation(current, "The journal packet could not be sent.", {
            current.client.sendPayload(endpoint, payload)
        }, terminal = false, relevant = { current.outgoingPayload == payload.id }, onFailure = {
            current.outgoingPayload = null
            _state.value = _state.value.copy(sending = false, sent = false)
        }) { /* A successful Task only queues a send; transfer SUCCESS is the delivery boundary. */ }
    }

    fun consumeIncoming(): ByteArray? = synchronized(lock) {
        val bytes = pendingIncoming?.copyOf() ?: return@synchronized null
        pendingIncoming = null
        _state.value = _state.value.copy(pendingBytes = null)
        bytes
    }

    override fun close() = synchronized(lock) {
        generation++
        val previous = session
        session = null
        pendingIncoming = null
        previous?.let(::stop)
        _state.value = FieldNotesNearbyState(status = "Nearby exchange closed.")
    }

    private fun begin(localId: String, name: String, partnerId: String, hosting: Boolean): Session? {
        close()
        val local = canonicalUuid(localId)
        val expected = canonicalUuid(partnerId)
        if (local == null || expected == null || local == expected) {
            localError("Choose a different linked trainer with a valid trainer ID.")
            return null
        }
        val availabilityCode = try { availability() }
        catch (_: Exception) { -1 }
        if (availabilityCode != PlayServicesResult.SUCCESS) {
            localError("Google Play services is unavailable or needs an update on this device (code $availabilityCode).")
            return null
        }
        val missing = missingPermissions()
        if (missing.isNotEmpty()) {
            localError("Nearby permission is needed before hosting or finding a trainer: ${missing.joinToString { it.substringAfterLast('.') }}.")
            return null
        }
        return try {
            Session(generation, clientFactory(), encodeEndpoint(local, name), expected, hosting).also {
                session = it
                _state.value = FieldNotesNearbyState(running = true)
            }
        } catch (_: SecurityException) {
            localError("Android denied nearby access. Review this app's nearby permissions."); null
        } catch (_: Exception) {
            localError("Nearby exchange could not be initialized on this device."); null
        }
    }

    private fun discovery(current: Session) = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) = synchronized(lock) {
            if (!active(current) || current.endpointId != null || info.serviceId != serviceId()) return@synchronized
            val identity = decodeEndpoint(info.endpointName.orEmpty()) ?: return@synchronized
            if (identity.first != current.expectedPartnerId) return@synchronized
            if (endpointId !in current.peers && current.peers.size >= MAX_PEERS) return@synchronized
            current.peers[endpointId] = FieldNotesNearbyPeer(endpointId, identity.second, identity.first)
            _state.value = _state.value.copy(status = "Linked trainer found. Choose the device, then compare its code.",
                peers = current.peers.values.toList(), error = null)
        }

        override fun onEndpointLost(endpointId: String) = synchronized(lock) {
            if (!active(current) || current.endpointId != null) return@synchronized
            current.peers.remove(endpointId)
            _state.value = _state.value.copy(peers = current.peers.values.toList())
        }
    }

    private fun lifecycle(current: Session) = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) = synchronized(lock) {
            if (!active(current)) {
                if (session == null) quiet { current.client.disconnectFromEndpoint(endpointId) }
                return@synchronized
            }
            val identity = decodeEndpoint(info.endpointName.orEmpty())
            val digits = info.authenticationDigits.orEmpty()
            if (identity == null || identity.first != current.expectedPartnerId || !digits.matches(Regex("[0-9]{4}")) ||
                (current.endpointId != null && current.endpointId != endpointId) ||
                (!current.hosting && current.endpointId == null)) {
                quiet { current.client.rejectConnection(endpointId) }
                quiet { current.client.disconnectFromEndpoint(endpointId) }
                return@synchronized
            }
            if (current.endpointId == endpointId && _state.value.authDigits != null) return@synchronized
            current.endpointId = endpointId
            stopDiscoveryAndAdvertising(current)
            _state.value = _state.value.copy(status = "Compare this code on both devices before confirming.",
                peers = emptyList(), authDigits = digits, peerName = identity.second, peerTrainerId = identity.first,
                connected = false, awaitingLocalConfirmation = true, localConfirmed = false, error = null)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) = synchronized(lock) {
            if (!active(current) || current.endpointId != endpointId) return@synchronized
            if (!result.status.isSuccess || !current.localConfirmed) {
                finish(current, "Nearby connection was not established.", "The connection was declined or could not be authenticated.")
                return@synchronized
            }
            current.connected = true
            _state.value = _state.value.copy(status = "Connected. Choose a journal packet to send.",
                connected = true, awaitingLocalConfirmation = false, error = null)
        }

        override fun onDisconnected(endpointId: String) = synchronized(lock) {
            if (!active(current) || current.endpointId != endpointId) return@synchronized
            finish(current, if (pendingIncoming != null) "Disconnected. The received packet is ready to review." else "Nearby trainer disconnected.", null)
        }
    }

    private fun payloads(current: Session) = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) = synchronized(lock) {
            if (!authenticated(current, endpointId)) return@synchronized
            if (payload.type != Payload.Type.BYTES) {
                quiet { current.client.cancelPayload(payload.id) }
                finish(current, "Nearby exchange stopped.", "Only small journal byte packets are accepted.")
                return@synchronized
            }
            if (payload.id in current.completedIncoming) return@synchronized
            val bytes = payload.asBytes()
            if (bytes == null || bytes.isEmpty() || bytes.size > MAX_BYTES) {
                quiet { current.client.cancelPayload(payload.id) }
                localError("The received journal packet is empty or larger than 32 KiB.")
                return@synchronized
            }
            if (pendingIncoming != null || current.incomingPayload != null) {
                quiet { current.client.cancelPayload(payload.id) }
                localError("Review or dismiss the received packet before accepting another.")
                return@synchronized
            }
            current.incomingPayload = payload.id
            current.incomingBytes = bytes.copyOf()
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = synchronized(lock) {
            if (!authenticated(current, endpointId)) return@synchronized
            if (update.payloadId == current.outgoingPayload) {
                when (update.status) {
                    PayloadTransferUpdate.Status.SUCCESS -> {
                        current.outgoingPayload = null
                        _state.value = _state.value.copy(status = "Packet delivered to the other device; saving there is a separate choice.",
                            sending = false, sent = true, error = null)
                    }
                    PayloadTransferUpdate.Status.FAILURE, PayloadTransferUpdate.Status.CANCELED -> {
                        current.outgoingPayload = null
                        _state.value = _state.value.copy(status = "Journal packet was not delivered.",
                            sending = false, sent = false, error = "The nearby transfer failed or was canceled. You can try sending again.")
                    }
                }
            }
            if (update.payloadId == current.incomingPayload) {
                when (update.status) {
                    PayloadTransferUpdate.Status.SUCCESS -> {
                        pendingIncoming = current.incomingBytes?.copyOf()
                        current.completedIncoming.add(update.payloadId)
                        while (current.completedIncoming.size > MAX_REMEMBERED_PAYLOADS) current.completedIncoming.remove(current.completedIncoming.first())
                        current.incomingPayload = null
                        current.incomingBytes = null
                        _state.value = _state.value.copy(status = "A journal packet arrived. Review it before saving.",
                            pendingBytes = pendingIncoming?.copyOf(), error = null)
                    }
                    PayloadTransferUpdate.Status.FAILURE, PayloadTransferUpdate.Status.CANCELED -> {
                        current.incomingPayload = null
                        current.incomingBytes = null
                        localError("The incoming journal packet did not finish transferring.")
                    }
                }
            }
        }
    }

    private fun operation(current: Session, failureMessage: String, task: () -> Task<Void>,
        terminal: Boolean = true, relevant: () -> Boolean = { true }, onFailure: () -> Unit = {}, onSuccess: () -> Unit) {
        try {
            task().addOnSuccessListener { synchronized(lock) {
                if (active(current) && relevant()) onSuccess()
                else if (session == null) stop(current)
            } }.addOnFailureListener { error -> synchronized(lock) {
                if (active(current) && relevant()) {
                    onFailure()
                    val message = if (error is SecurityException) "Android denied nearby access. Review this app's nearby permissions." else failureMessage
                    if (terminal) finish(current, "Nearby exchange stopped.", message) else localError(message)
                }
            } }.addOnCanceledListener { synchronized(lock) {
                if (active(current) && relevant()) {
                    onFailure()
                    if (terminal) finish(current, "Nearby exchange stopped.", "The nearby operation was canceled.")
                    else localError("The nearby operation was canceled.")
                }
            } }
        } catch (_: SecurityException) {
            if (active(current) && relevant()) {
                onFailure()
                val message = "Android denied nearby access. Review this app's nearby permissions."
                if (terminal) finish(current, "Nearby exchange stopped.", message) else localError(message)
            }
        } catch (_: Exception) {
            if (active(current) && relevant()) {
                onFailure()
                if (terminal) finish(current, "Nearby exchange stopped.", failureMessage) else localError(failureMessage)
            }
        }
    }

    private fun authenticated(current: Session, endpointId: String): Boolean =
        active(current) && current.connected && current.localConfirmed && current.endpointId == endpointId

    private fun active(current: Session): Boolean = session === current && generation == current.generation

    private fun finish(current: Session, status: String, error: String?) {
        if (!active(current)) return
        generation++
        session = null
        stop(current)
        _state.value = _state.value.copy(status = status, peers = emptyList(), authDigits = null,
            connected = false, sending = false, awaitingLocalConfirmation = false, localConfirmed = false, running = false, error = error)
    }

    private fun stopDiscoveryAndAdvertising(current: Session) {
        quiet { current.client.stopDiscovery() }
        quiet { current.client.stopAdvertising() }
    }

    private fun stop(current: Session) {
        stopDiscoveryAndAdvertising(current)
        current.outgoingPayload?.let { id -> quiet { current.client.cancelPayload(id) } }
        current.incomingPayload?.let { id -> quiet { current.client.cancelPayload(id) } }
        current.endpointId?.let { endpoint -> quiet { current.client.disconnectFromEndpoint(endpoint) } }
        quiet { current.client.stopAllEndpoints() }
        current.incomingBytes = null
    }

    private inline fun quiet(operation: () -> Unit) { try { operation() } catch (_: Exception) { /* Already stopped locally; late callbacks cannot reenter. */ } }
    private fun localError(message: String) { _state.value = _state.value.copy(error = message) }
    private fun serviceId() = "${context.packageName}.field-notes.v1"

    companion object {
        const val MAX_BYTES = 32 * 1024
        private const val MAX_PEERS = 8
        private const val MAX_REMEMBERED_PAYLOADS = 8
        private const val ENDPOINT_PREFIX = "odxjn1"

        private fun realClientFactory(context: Context): () -> ConnectionsClient =
            { Nearby.getConnectionsClient(context) }

        private fun realAvailability(context: Context): () -> Int =
            { GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) }

        private fun realMissingPermissions(context: Context): () -> List<String> = {
            requiredPermissions(Build.VERSION.SDK_INT, context.applicationInfo.targetSdkVersion)
                .filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        }

        /** Runtime permissions only. Manifest Wi-Fi/Bluetooth declarations are also required. */
        fun requiredPermissions(sdkInt: Int, targetSdkInt: Int = 36): List<String> = buildList {
            if (sdkInt in 23..32) {
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                if (sdkInt >= 29) add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (sdkInt >= 31) {
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (sdkInt >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
            if (sdkInt >= 37 && targetSdkInt >= 37) add("android.permission.ACCESS_LOCAL_NETWORK")
        }

        private fun canonicalUuid(value: String): String? = try {
            UUID.fromString(value).toString().takeIf { it.equals(value, ignoreCase = true) }
        } catch (_: IllegalArgumentException) { null }

        private fun encodeEndpoint(trainerId: String, name: String): String {
            val clean = name.filterNot(Char::isISOControl).trim().ifEmpty { "Trainer" }
            val label = StringBuilder()
            for (point in clean.codePoints().toArray()) {
                val next = String(Character.toChars(point))
                if ((label.toString() + next).toByteArray(Charsets.UTF_8).size > 48) break
                label.append(next)
            }
            val encoded = Base64.encodeToString(label.toString().toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            return "$ENDPOINT_PREFIX|$trainerId|$encoded"
        }

        private fun decodeEndpoint(value: String): Pair<String, String>? {
            if (value.length > 128) return null
            val parts = value.split('|')
            if (parts.size != 3 || parts[0] != ENDPOINT_PREFIX) return null
            val trainerId = canonicalUuid(parts[1]) ?: return null
            return try {
                val raw = Base64.decode(parts[2], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
                if (raw.isEmpty() || raw.size > 48) return null
                val name = raw.toString(Charsets.UTF_8)
                if (!raw.contentEquals(name.toByteArray(Charsets.UTF_8)) || name.any(Char::isISOControl) || name.isBlank()) null
                else trainerId to name
            } catch (_: IllegalArgumentException) { null }
        }
    }
}
