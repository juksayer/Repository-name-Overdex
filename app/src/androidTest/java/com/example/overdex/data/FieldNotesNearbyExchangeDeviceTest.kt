package com.example.overdex.data

import android.os.Build
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.common.api.Status
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import java.io.ByteArrayInputStream
import java.lang.reflect.Proxy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Production transport callbacks and Google Tasks, with a proxy client that never opens radios. */
class FieldNotesNearbyExchangeDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val exchanges = mutableListOf<FieldNotesNearbyExchange>()

    @Before fun requireDisposableEmulator() {
        assertTrue("Run nearby state-machine tests only on a disposable emulator",
            Build.HARDWARE in setOf("ranchu", "goldfish") || Build.FINGERPRINT.contains("generic"))
    }

    @After fun stopIsolatedTransports() {
        main { exchanges.forEach(FieldNotesNearbyExchange::close) }
    }

    @Test fun linkedPeerStillRequiresExplicitCodeConfirmationThenConnectionSuccess() {
        val fake = ClientHarness()
        val exchange = exchange(fake)
        main { exchange.startHosting(LOCAL, "Local trainer", PARTNER) }
        assertTrue(exchange.state.value.running)
        assertEquals(0, fake.accepted.size)
        main { fake.lifecycle.onConnectionInitiated("stranger", info(STRANGER)) }
        assertEquals(listOf("stranger"), fake.rejected)
        assertNull(exchange.state.value.authDigits)
        main { fake.lifecycle.onConnectionInitiated(ENDPOINT, info(PARTNER)) }
        assertEquals(info(PARTNER).authenticationDigits, exchange.state.value.authDigits)
        assertTrue(exchange.state.value.awaitingLocalConfirmation)
        assertEquals("Receiving a request must never accept it", 0, fake.accepted.size)
        main { exchange.send(byteArrayOf(1)) }
        assertEquals("No payload before the code is confirmed", 0, fake.sends.size)
        main { exchange.confirmAuthentication(true) }
        assertEquals(listOf(ENDPOINT), fake.accepted)
        assertTrue(exchange.state.value.localConfirmed)
        assertFalse("Local confirmation does not establish the remote connection", exchange.state.value.connected)
        main { fake.lifecycle.onConnectionResult(ENDPOINT, ConnectionResolution(Status.RESULT_SUCCESS)) }
        assertTrue(exchange.state.value.connected)
        main { exchange.confirmAuthentication(true) }
        assertEquals("Repeated input cannot accept twice", listOf(ENDPOINT), fake.accepted)
    }

    @Test fun queuedSendIsNotDeliveryAndLateFailedAttemptCannotClobberItsRetry() {
        val (exchange, fake) = connected()
        main { exchange.send("first packet".toByteArray()) }
        val first = fake.sends.single()
        assertTrue(exchange.state.value.sending)
        assertFalse(exchange.state.value.sent)
        main { fake.transfer(first.payload, PayloadTransferUpdate.Status.FAILURE) }
        assertFalse(exchange.state.value.sending)
        assertNotNull(exchange.state.value.error)
        main { exchange.send("retry packet".toByteArray()) }
        val retry = fake.sends.last()
        assertTrue(exchange.state.value.sending)
        assertNull(exchange.state.value.error)
        main { first.completion.setException(IllegalStateException("Old send Task finally failed")) }
        assertTrue("Late failure belongs to the old attempt", exchange.state.value.sending)
        assertNull("Old failure must not add an error to the new attempt", exchange.state.value.error)
        main { retry.completion.setResult(null) }
        assertTrue("Successful sendPayload Task means queued, not delivered", exchange.state.value.sending)
        assertFalse(exchange.state.value.sent)
        main { fake.transfer(retry.payload, PayloadTransferUpdate.Status.SUCCESS) }
        assertFalse(exchange.state.value.sending)
        assertTrue(exchange.state.value.sent)
        assertTrue(exchange.state.value.status.contains("saving there is a separate choice"))
    }

    @Test fun discoveryFiltersLinkedIdentityAndCloseIgnoresOldCallbacksEvenAfterRestart() {
        val fake = ClientHarness()
        val exchange = exchange(fake)
        main { exchange.startDiscovering(LOCAL, "Local trainer", PARTNER) }
        val oldDiscovery = fake.discovery
        val service = fake.serviceId
        main {
            oldDiscovery.onEndpointFound("stranger", DiscoveredEndpointInfo(service, endpointName(STRANGER)))
            oldDiscovery.onEndpointFound(ENDPOINT, DiscoveredEndpointInfo("another.service", endpointName(PARTNER)))
        }
        assertTrue(exchange.state.value.peers.isEmpty())
        main { oldDiscovery.onEndpointFound(ENDPOINT, DiscoveredEndpointInfo(service, endpointName(PARTNER))) }
        assertEquals(ENDPOINT, exchange.state.value.peers.single().endpointId)
        main { exchange.connect(ENDPOINT) }
        assertTrue("Discovery stops once one device is selected", fake.stopDiscoveryCount > 0)
        val oldLifecycle = fake.lifecycle
        main {
            oldLifecycle.onConnectionInitiated(ENDPOINT, info(PARTNER))
            exchange.confirmAuthentication(true)
            oldLifecycle.onConnectionResult(ENDPOINT, ConnectionResolution(Status.RESULT_SUCCESS))
            exchange.send(byteArrayOf(1, 2, 3))
        }
        val oldPayloads = fake.payloads
        val oldSend = fake.sends.single()
        main { exchange.close() }
        val closed = exchange.state.value
        main {
            oldLifecycle.onConnectionInitiated(ENDPOINT, info(PARTNER))
            oldLifecycle.onConnectionResult(ENDPOINT, ConnectionResolution(Status.RESULT_SUCCESS))
            oldPayloads.onPayloadReceived(ENDPOINT, Payload.fromBytes(byteArrayOf(7)))
            oldPayloads.onPayloadTransferUpdate(ENDPOINT, update(oldSend.payload, PayloadTransferUpdate.Status.SUCCESS))
        }
        assertEquals("Closed sessions cannot republish data or reconnect", closed, exchange.state.value)
        assertTrue(fake.stopAllCount > 0)
        assertTrue(fake.canceled.contains(oldSend.payload.id))
        main { exchange.startHosting(LOCAL, "Local trainer", PARTNER) }
        val restarted = exchange.state.value
        main {
            oldDiscovery.onEndpointFound("late", DiscoveredEndpointInfo(service, endpointName(PARTNER)))
            oldLifecycle.onDisconnected(ENDPOINT)
            oldSend.completion.setException(IllegalStateException("Previous session failure"))
        }
        assertEquals("An old callback cannot mutate the restarted session", restarted, exchange.state.value)
    }

    @Test fun incomingBytesAreBoundedSinglePreviewCopiesAndSurviveOrdinaryDisconnect() {
        val (exchange, fake) = connected()
        val wrongEndpoint = Payload.fromBytes(byteArrayOf(1))
        main {
            fake.payloads.onPayloadReceived("not-the-confirmed-device", wrongEndpoint)
            fake.payloads.onPayloadTransferUpdate("not-the-confirmed-device", update(wrongEndpoint, PayloadTransferUpdate.Status.SUCCESS))
        }
        assertNull(exchange.state.value.pendingBytes)
        val oversized = Payload.fromBytes(ByteArray(FieldNotesNearbyExchange.MAX_BYTES + 1))
        main { fake.payloads.onPayloadReceived(ENDPOINT, oversized) }
        assertTrue(fake.canceled.contains(oversized.id))
        assertNull(exchange.state.value.pendingBytes)
        val original = byteArrayOf(11, 22, 33)
        val first = Payload.fromBytes(original)
        val extra = Payload.fromBytes(byteArrayOf(44))
        main {
            fake.payloads.onPayloadReceived(ENDPOINT, first)
            original[0] = 99
            fake.payloads.onPayloadReceived(ENDPOINT, extra)
        }
        assertTrue("Only one incoming payload is retained", fake.canceled.contains(extra.id))
        assertNull("Receiving bytes is not completed delivery", exchange.state.value.pendingBytes)
        main { fake.transfer(first, PayloadTransferUpdate.Status.SUCCESS) }
        assertArrayEquals(byteArrayOf(11, 22, 33), exchange.state.value.pendingBytes)
        val another = Payload.fromBytes(byteArrayOf(55))
        main {
            fake.payloads.onPayloadReceived(ENDPOINT, another)
            fake.transfer(another, PayloadTransferUpdate.Status.SUCCESS)
        }
        assertTrue(fake.canceled.contains(another.id))
        assertArrayEquals("A new packet cannot replace a waiting preview", byteArrayOf(11, 22, 33), exchange.state.value.pendingBytes)
        main { fake.lifecycle.onDisconnected(ENDPOINT) }
        assertFalse(exchange.state.value.connected)
        assertArrayEquals("Disconnect preserves only the completed preview", byteArrayOf(11, 22, 33), exchange.state.value.pendingBytes)
        var consumed: ByteArray? = null
        main { consumed = exchange.consumeIncoming() }
        assertArrayEquals(byteArrayOf(11, 22, 33), consumed)
        assertNull(exchange.state.value.pendingBytes)
        main { assertNull("Preview is consumed once", exchange.consumeIncoming()) }
    }

    @Test fun nonBytePayloadStopsExchangeWithoutPublishingAnyPreview() {
        val (exchange, fake) = connected()
        val stream = Payload.fromStream(ByteArrayInputStream(byteArrayOf(1, 2)))
        try {
            main { fake.payloads.onPayloadReceived(ENDPOINT, stream) }
            assertFalse(exchange.state.value.running)
            assertFalse(exchange.state.value.connected)
            assertNull(exchange.state.value.pendingBytes)
            assertTrue(fake.canceled.contains(stream.id))
            assertTrue(exchange.state.value.error.orEmpty().contains("Only small journal byte packets"))
            main { fake.transfer(stream, PayloadTransferUpdate.Status.SUCCESS) }
            assertNull(exchange.state.value.pendingBytes)
        } finally { stream.close() }
    }

    @Test fun unavailableServicesAndDeniedPermissionsNeverCreateANearbyClient() {
        var clientsCreated = 0
        val fake = ClientHarness()
        val unavailable = FieldNotesNearbyExchange(context, { clientsCreated++; fake.client }, { 2 }, { emptyList() })
        val denied = FieldNotesNearbyExchange(context, { clientsCreated++; fake.client }, { 0 }, { listOf("android.permission.BLUETOOTH_SCAN") })
        exchanges += unavailable
        exchanges += denied
        main {
            unavailable.startHosting(LOCAL, "Local trainer", PARTNER)
            denied.startDiscovering(LOCAL, "Local trainer", PARTNER)
        }
        assertEquals("Boundary checks precede client creation", 0, clientsCreated)
        assertFalse(unavailable.state.value.running)
        assertFalse(denied.state.value.running)
        assertTrue(unavailable.state.value.error.orEmpty().contains("Google Play services"))
        assertTrue(denied.state.value.error.orEmpty().contains("BLUETOOTH_SCAN"))
    }

    private fun exchange(fake: ClientHarness): FieldNotesNearbyExchange =
        FieldNotesNearbyExchange(context, { fake.client }, { 0 }, { emptyList() }).also { exchanges += it }

    private fun connected(): Pair<FieldNotesNearbyExchange, ClientHarness> {
        val fake = ClientHarness()
        val exchange = exchange(fake)
        main {
            exchange.startHosting(LOCAL, "Local trainer", PARTNER)
            fake.lifecycle.onConnectionInitiated(ENDPOINT, info(PARTNER))
            exchange.confirmAuthentication(true)
            fake.lifecycle.onConnectionResult(ENDPOINT, ConnectionResolution(Status.RESULT_SUCCESS))
        }
        assertTrue(exchange.state.value.connected)
        return exchange to fake
    }

    private fun main(action: () -> Unit) {
        instrumentation.runOnMainSync { action() }
        instrumentation.waitForIdleSync() // Google Tasks' default listeners execute on the main looper.
    }

    private class ClientHarness {
        lateinit var lifecycle: ConnectionLifecycleCallback
        lateinit var discovery: EndpointDiscoveryCallback
        lateinit var payloads: PayloadCallback
        var serviceId = ""
        val accepted = mutableListOf<String>()
        val rejected = mutableListOf<String>()
        val canceled = mutableListOf<Long>()
        val sends = mutableListOf<Send>()
        var stopDiscoveryCount = 0
        var stopAllCount = 0
        data class Send(val payload: Payload, val completion: TaskCompletionSource<Void> = TaskCompletionSource())
        val client: ConnectionsClient = Proxy.newProxyInstance(ConnectionsClient::class.java.classLoader,
            arrayOf(ConnectionsClient::class.java)) { proxy, method, arguments ->
            val args = arguments.orEmpty()
            when (method.name) {
                "startAdvertising" -> { serviceId = args[1] as String; lifecycle = args[2] as ConnectionLifecycleCallback; completed() }
                "startDiscovery" -> { serviceId = args[0] as String; discovery = args[1] as EndpointDiscoveryCallback; completed() }
                "requestConnection" -> { lifecycle = args[2] as ConnectionLifecycleCallback; completed() }
                "acceptConnection" -> { accepted += args[0] as String; payloads = args[1] as PayloadCallback; completed() }
                "rejectConnection" -> { rejected += args[0] as String; completed() }
                "sendPayload" -> Send(args[1] as Payload).also { sends += it }.completion.task
                "cancelPayload" -> { canceled += args[0] as Long; completed() }
                "stopDiscovery" -> { stopDiscoveryCount++; null }
                "stopAllEndpoints" -> { stopAllCount++; null }
                "stopAdvertising", "disconnectFromEndpoint" -> null
                "toString" -> "Radio-free nearby test client"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args.firstOrNull()
                else -> throw AssertionError("Unexpected ConnectionsClient call: ${method.name}")
            }
        } as ConnectionsClient
        fun transfer(payload: Payload, status: Int) = payloads.onPayloadTransferUpdate(ENDPOINT, update(payload, status))
        private fun completed(): Task<Void> = Tasks.forResult<Void>(null)
    }

    companion object {
        private const val LOCAL = "10000000-0000-0000-0000-000000000001"
        private const val PARTNER = "20000000-0000-0000-0000-000000000002"
        private const val STRANGER = "30000000-0000-0000-0000-000000000003"
        private const val ENDPOINT = "confirmed-device"
        private fun endpointName(id: String) = "odxjn1|$id|${Base64.encodeToString("Trainer".toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)}"
        private fun info(id: String) = ConnectionInfo(endpointName(id), "shared-channel-token", true)
        private fun update(payload: Payload, status: Int): PayloadTransferUpdate = PayloadTransferUpdate.Builder()
            .setPayloadId(payload.id).setStatus(status).setTotalBytes(payload.asBytes()?.size?.toLong() ?: 2L)
            .setBytesTransferred(if (status == PayloadTransferUpdate.Status.SUCCESS) payload.asBytes()?.size?.toLong() ?: 2L else 0L).build()
    }
}
