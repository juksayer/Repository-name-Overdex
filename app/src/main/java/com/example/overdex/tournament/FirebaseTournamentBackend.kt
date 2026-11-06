package com.example.overdex.tournament

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json

/** One account, one remote room. Cached or pending writes never decide a battle result. */
class FirebaseTournamentBackend(
    private val accountId: String,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val now: () -> Long = System::currentTimeMillis,
) : TournamentBackend {
    private val rooms get() = firestore.collection("tournaments")
    private val profiles get() = firestore.collection("trainerCommsProfiles")
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    private fun requireAccount() {
        check(auth.currentUser?.uid == accountId && auth.currentUser?.isAnonymous == false) {
            "Sign in again to continue with this tournament."
        }
    }

    override fun events(): Flow<List<TournamentEvent>> = callbackFlow {
        requireAccount()
        val listener = rooms.whereArrayContains("memberIds", accountId)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                try {
                    requireAccount()
                    // The list has no cache marker in its contract; publish only server-confirmed lists.
                    if (snapshot != null && !snapshot.metadata.isFromCache && !snapshot.metadata.hasPendingWrites()) {
                        trySend(snapshot.documents.map { it.event() }.sortedByDescending { it.updatedAt })
                    }
                } catch (e: Exception) { close(e) }
            }
        awaitClose { listener.remove() }
    }

    override fun lobby(eventId: String): Flow<TournamentLobby> = combine(
        eventFrames(eventId), attendanceFrames(eventId), preparationFrames(eventId),
    ) { event, attendance, preparations ->
        TournamentLobby(event.value, attendance.value, preparations.value,
            event.fromCache || attendance.fromCache || preparations.fromCache)
    }

    override suspend fun create(event: TournamentEvent) {
        requireAccount()
        TournamentEngine.validateEvent(event)
        require(event.ownerId == accountId && event.memberIds == listOf(accountId) &&
            event.phase == TournamentPhase.LOBBY && event.revision == 0L && event.settingsRevision == 0L && event.series.isEmpty() &&
            event.participantIds.isEmpty()) { "A new tournament starts with its host in the lobby." }
        val profile = profiles.document(accountId).get(Source.SERVER).await().player()
        require(event.players.single() == profile) { "Refresh your trainer profile before creating the tournament." }
        requireAccount()
        val reference = rooms.document(event.id)
        firestore.runBatch { batch ->
            batch.set(reference, envelope(event) + mapOf("createdAt" to FieldValue.serverTimestamp()))
            batch.set(reference.collection("entrants").document(accountId),
                attendanceFields(TournamentAttendance(accepted = true, checkedIn = true)))
        }.await()
        requireAccount()
    }

    override suspend fun host(eventId: String, command: TournamentHostCommand) {
        requireAccount()
        val reference = rooms.document(eventId)
        firestore.runTransaction { transaction ->
            requireAccount()
            val event = transaction.get(reference).event()
            require(event.ownerId == accountId) { "Only the tournament host can change its progress." }
            // All reads precede writes, and retries recompute from the same current server snapshot.
            val attendance = event.memberIds.associateWith { uid ->
                transaction.get(reference.collection("entrants").document(uid)).attendance()
            }
            val preparations = event.activeBattles.flatMap { (key, players) ->
                players.map { uid ->
                    val id = "${key}_$uid"
                    id to transaction.get(reference.collection("preparations").document(id)).preparationOrNull()
                }
            }.mapNotNull { (key, value) -> value?.let { key to it } }.toMap()
            val actualCommand = if (command is TournamentHostCommand.Invite) {
                command.copy(player = transaction.get(profiles.document(command.player.accountId)).player())
            } else command
            val next = TournamentEngine.apply(TournamentLobby(event, attendance, preparations), accountId,
                actualCommand, maxOf(now(), event.updatedAt))
            transaction.update(reference, envelope(next))
        }.await()
        requireAccount()
    }

    override suspend fun attendance(eventId: String, value: TournamentAttendance) {
        requireAccount()
        require(!(value.accepted && value.declined) && (!value.checkedIn || value.accepted)) {
            "Accept the invitation before checking in, or decline it without checking in."
        }
        val reference = rooms.document(eventId)
        firestore.runTransaction { transaction ->
            requireAccount()
            val event = transaction.get(reference).event()
            require(accountId in event.memberIds && event.phase == TournamentPhase.LOBBY) {
                "Attendance can only change while this tournament is in its lobby."
            }
            require(value.settingsRevision == event.settingsRevision) {
                "The tournament rules changed. Review the current rules, then accept or check in again."
            }
            transaction.set(reference.collection("entrants").document(accountId),
                attendanceFields(value))
        }.await()
        requireAccount()
    }

    override suspend fun prepare(eventId: String, value: BattlePreparation) {
        requireAccount()
        require(value.playerId == accountId && value.note.length <= 240 &&
            value.battleKey.matches(Regex("[A-Za-z0-9_-]{1,80}"))) { "This battle preparation is not valid." }
        require(value.ready || value.result in listOf(BattleResultReport.NONE, BattleResultReport.DISPUTE)) {
            "Mark yourself ready before reporting a result."
        }
        val reference = rooms.document(eventId)
        firestore.runTransaction { transaction ->
            requireAccount()
            val event = transaction.get(reference).event()
            require(event.phase == TournamentPhase.ACTIVE && accountId in event.activeBattles[value.battleKey].orEmpty()) {
                "This battle has already advanced. Review your current pairing."
            }
            transaction.set(reference.collection("preparations").document(value.documentId), mapOf(
                "playerId" to accountId, "battleKey" to value.battleKey, "ready" to value.ready,
                "result" to value.result.name, "note" to value.note.trim(), "updatedAt" to FieldValue.serverTimestamp(),
            ))
        }.await()
        requireAccount()
    }

    private data class Frame<T>(val value: T, val fromCache: Boolean)

    private fun eventFrames(eventId: String): Flow<Frame<TournamentEvent>> = callbackFlow {
        requireAccount()
        val listener = rooms.document(eventId).addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) { close(error); return@addSnapshotListener }
            try {
                requireAccount()
                if (snapshot != null && !snapshot.metadata.hasPendingWrites()) {
                    // A first cache miss is not evidence that the remote invitation disappeared.
                    if (snapshot.exists()) trySend(Frame(snapshot.event(), snapshot.metadata.isFromCache))
                    else if (!snapshot.metadata.isFromCache) throw IllegalStateException("This tournament is no longer available to this account.")
                }
            } catch (e: Exception) { close(e) }
        }
        awaitClose { listener.remove() }
    }

    private fun attendanceFrames(eventId: String): Flow<Frame<Map<String, TournamentAttendance>>> = callbackFlow {
        requireAccount()
        val listener = rooms.document(eventId).collection("entrants")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                try {
                    requireAccount()
                    if (snapshot != null && !snapshot.metadata.hasPendingWrites()) trySend(Frame(
                        snapshot.documents.associate { it.id to it.attendance() }, snapshot.metadata.isFromCache))
                } catch (e: Exception) { close(e) }
            }
        awaitClose { listener.remove() }
    }

    private fun preparationFrames(eventId: String): Flow<Frame<Map<String, BattlePreparation>>> = callbackFlow {
        requireAccount()
        val listener = rooms.document(eventId).collection("preparations")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                try {
                    requireAccount()
                    if (snapshot != null && !snapshot.metadata.hasPendingWrites()) trySend(Frame(
                        snapshot.documents.mapNotNull { doc -> doc.preparationOrNull()?.let { doc.id to it } }.toMap(),
                        snapshot.metadata.isFromCache))
                } catch (e: Exception) { close(e) }
            }
        awaitClose { listener.remove() }
    }

    private fun envelope(event: TournamentEvent): Map<String, Any> {
        val payload = json.encodeToString(TournamentEvent.serializer(), event)
        require(payload.length <= 120_000) { "This tournament has reached this version's record limit. Its saved battles have been kept." }
        return mapOf("schemaVersion" to 1, "ownerId" to event.ownerId, "memberIds" to event.memberIds,
            "phase" to event.phase.name, "activeBattles" to event.activeBattles, "revision" to event.revision,
            "settingsRevision" to event.settingsRevision,
            "payload" to payload, "updatedAt" to FieldValue.serverTimestamp())
    }

    private fun DocumentSnapshot.event(): TournamentEvent {
        check(exists() && getLong("schemaVersion") == 1L) { "This tournament is not supported by this version of Overdex." }
        val event = json.decodeFromString(TournamentEvent.serializer(), requireNotNull(getString("payload")))
        TournamentEngine.validateEvent(event)
        val memberIds = (get("memberIds") as? List<*>)?.map { it as String }
        val active = (get("activeBattles") as? Map<*, *>)?.map { (key, players) ->
            key as String to (players as List<*>).map { it as String }
        }?.toMap()
        check(event.id == id && event.ownerId == getString("ownerId") && event.memberIds == memberIds &&
            event.phase.name == getString("phase") && event.revision == getLong("revision") &&
            event.settingsRevision == getLong("settingsRevision") &&
            event.activeBattles == active && accountId in event.memberIds) { "This tournament's access details do not match its record." }
        return event
    }

    private fun DocumentSnapshot.player(): TournamentPlayer {
        check(exists()) { "This trainer needs to set up their Trainer Comms profile first." }
        return TournamentPlayer(id, requireNotNull(getString("displayName")), requireNotNull(getString("trainerCode")))
            .also(TournamentEngine::validatePlayer)
    }

    private fun DocumentSnapshot.attendance() = if (!exists()) TournamentAttendance() else
        TournamentAttendance(getBoolean("accepted") == true, getBoolean("checkedIn") == true,
            getBoolean("declined") == true, requireNotNull(getLong("settingsRevision")))

    private fun attendanceFields(value: TournamentAttendance) = mapOf("accepted" to value.accepted,
        "checkedIn" to value.checkedIn, "declined" to value.declined,
        "settingsRevision" to value.settingsRevision, "updatedAt" to FieldValue.serverTimestamp())

    private fun DocumentSnapshot.preparationOrNull(): BattlePreparation? = if (!exists()) null else BattlePreparation(
        requireNotNull(getString("playerId")), requireNotNull(getString("battleKey")), getBoolean("ready") == true,
        BattleResultReport.valueOf(requireNotNull(getString("result"))), getString("note").orEmpty(),
    ).also { check(id == it.documentId) { "This preparation does not match its battle." } }
}
