package com.example.overdex.ui

import androidx.test.core.app.ApplicationProvider
import com.example.overdex.comms.FirebaseCommsBackend
import com.example.overdex.tournament.*
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Disposable accounts on the local emulators only; never run this against production accounts. */
class TournamentBackendDeviceTest {
    private data class Client(val app: FirebaseApp, val auth: FirebaseAuth,
                              val db: FirebaseFirestore, val backend: FirebaseTournamentBackend,
                              val player: TournamentPlayer)

    @Test fun twoAccountsInvitePrepareConfirmThreeBattlesAndRecoverTheSummary() = runBlocking {
        val clients = mutableListOf<Client>()
        try {
            val token = UUID.randomUUID().toString().replace("-", "")
            suspend fun client(label: String, index: Int): Client {
                val name = "tournament-$label-$token"
                val app = FirebaseApp.initializeApp(ApplicationProvider.getApplicationContext(),
                    FirebaseOptions.Builder().setProjectId("demo-overdex-comms")
                        .setApplicationId("1:123456789:android:$label$token")
                        .setApiKey("emulator-$label-$token").build(), name)
                val auth = FirebaseAuth.getInstance(app).apply { useEmulator("10.0.2.2", 9099) }
                val db = FirebaseFirestore.getInstance(app).apply { useEmulator("10.0.2.2", 8085) }
                auth.createUserWithEmailAndPassword("$label-$token@example.test", "local-test-password").await()
                val uid = requireNotNull(auth.currentUser).uid
                val code = ((token.take(8).toLong(16) * 10 + index) % 1_000_000_000_000L).toString().padStart(12, '0')
                val profile = FirebaseCommsBackend(uid, auth, db).saveProfile("Test $label", code)
                return Client(app, auth, db, FirebaseTournamentBackend(uid, auth, db),
                    TournamentPlayer(uid, profile.displayName, profile.trainerCode)).also(clients::add)
            }
            val host = client("host", 1)
            val guest = client("guest", 2)
            val outsider = client("outsider", 3)
            val now = System.currentTimeMillis()
            val event = TournamentEvent("native-$token", host.player.accountId,
                TournamentSettings(title = "Two trainer acceptance"), listOf(host.player), createdAt = now, updatedAt = now)
            host.backend.create(event)
            host.backend.host(event.id, TournamentHostCommand.Invite(guest.player))
            val invited = withTimeout(15_000) { guest.backend.events().first { events -> events.any { it.id == event.id } } }
            assertEquals(2, invited.single { it.id == event.id }.players.size)
            assertTrue(withTimeout(15_000) { outsider.backend.events().first() }.isEmpty())
            guest.backend.attendance(event.id, TournamentAttendance(accepted = true, checkedIn = true))
            host.backend.host(event.id, TournamentHostCommand.Settings(event.settings.copy(rules = "Practice event")))
            var staleCheckInRefused = false
            try { host.backend.host(event.id, TournamentHostCommand.Start) }
            catch (_: Exception) { staleCheckInRefused = true }
            assertTrue("A changed rule requires fresh check-in from both trainers", staleCheckInRefused)
            var staleActionRefused = false
            try { guest.backend.attendance(event.id, TournamentAttendance(accepted = true, checkedIn = true)) }
            catch (_: Exception) { staleActionRefused = true }
            assertTrue("A delayed check-in cannot approve rules the player had not seen", staleActionRefused)
            host.backend.attendance(event.id, TournamentAttendance(accepted = true, checkedIn = true, settingsRevision = 1))
            guest.backend.attendance(event.id, TournamentAttendance(accepted = true, checkedIn = true, settingsRevision = 1))
            host.backend.host(event.id, TournamentHostCommand.Start)
            suspend fun room(client: Client) = withTimeout(15_000) { client.backend.lobby(event.id).first { !it.fromCache } }
            var hostRoom = room(host)
            assertEquals(TournamentPhase.ACTIVE, hostRoom.event.phase)
            assertEquals(3, hostRoom.event.activeSeries.single().winsRequired)

            repeat(3) { index ->
                val series = room(host).event.activeSeries.single()
                host.backend.prepare(event.id, BattlePreparation(host.player.accountId, series.battleKey, true, BattleResultReport.WIN))
                guest.backend.prepare(event.id, BattlePreparation(guest.player.accountId, series.battleKey, true, BattleResultReport.LOSS))
                // The transaction reads acknowledged reports itself; it does not trust a stale UI frame.
                host.backend.host(event.id, TournamentHostCommand.Finalize(series.id, series.battleKey))
                hostRoom = room(host)
                assertEquals(index + 1, hostRoom.event.completedBattleCount)
                if (index < 2) {
                    assertEquals(TournamentPhase.ACTIVE, hostRoom.event.phase)
                    assertNull(hostRoom.preparation(hostRoom.event.activeSeries.single(), host.player.accountId))
                }
                var refused = false
                try { host.backend.host(event.id, TournamentHostCommand.Finalize(series.id, series.battleKey)) }
                catch (_: Exception) { refused = true }
                assertTrue("Retrying the old result cannot count a second battle", refused)
            }
            val summary = room(guest).event
            assertEquals(TournamentPhase.COMPLETED, summary.phase)
            assertEquals(host.player.accountId, summary.championId)
            assertEquals(3, summary.completedBattleCount)
            assertTrue(summary.activeBattles.isEmpty())
            // A fresh backend/listener recovers the same persisted tournament after leaving the room.
            val returned = FirebaseTournamentBackend(guest.player.accountId, guest.auth, guest.db)
            val restored = withTimeout(15_000) { returned.lobby(event.id).first { !it.fromCache } }
            assertEquals(summary, restored.event)
            guest.auth.signOut()
            var denied = false
            try { returned.events().first() } catch (_: Exception) { denied = true }
            assertTrue("Signing out invalidates the old account's backend", denied)
        } finally {
            clients.forEach { it.db.terminate().await(); it.app.delete() }
        }
    }
}
