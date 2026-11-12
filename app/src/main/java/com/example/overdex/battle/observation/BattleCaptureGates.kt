package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.battle.timeline.observer.ObservationSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

/** Source-ordered cues request fresh, bounded acquisition; only usable readings complete it. */
private class BoundedCaptureAssignments(private val cropNames: Set<String>, private val now: () -> Long) {
    private data class Assignment(val from: Long, val until: Long, val confirmationRequested: Boolean = false)
    private val pending = mutableMapOf<String, Assignment>()
    private val requestedThrough = mutableMapOf<String, Long>()
    private val measuredThrough = mutableMapOf<String, Long>()

    fun request(crops: Set<String>, at: Long) {
        val current = now()
        if (at > current) return
        crops.filter { it in cropNames }.forEach { crop ->
            if (at <= (requestedThrough[crop] ?: Long.MIN_VALUE) ||
                at <= (measuredThrough[crop] ?: Long.MIN_VALUE)) return@forEach
            requestedThrough[crop] = at
            // Recognition can arrive several seconds after its source frame.
            // Keep that source time for ordering, but acquire current pixels for
            // a full window. Earlier queued frames/readings cannot finish it.
            pending[crop] = Assignment(current, current + WINDOW_NANOS)
        }
    }
    fun enabled(crop: String): Boolean = pending[crop]?.let { now() < it.until } == true
    fun accepts(crop: String, at: Long): Boolean = pending[crop]?.let {
        enabled(crop) && at >= it.from && at < it.until && at <= now()
    } == true
    fun hasMeasurement(crop: String): Boolean = crop in measuredThrough

    /** A first usable frame earns one bounded chance to collect an independent confirmation. */
    fun candidate(crop: String, at: Long) {
        val assignment = pending[crop] ?: return
        val current = now()
        val finalDeadline = assignment.from + 2 * WINDOW_NANOS
        // A queued frame from an earlier assignment cannot extend this one.
        // Neither repeated candidates nor slow decoding can keep capture alive.
        if (assignment.confirmationRequested || at < assignment.from || at >= assignment.until ||
            at > current || current >= finalDeadline) return
        pending[crop] = assignment.copy(until = finalDeadline, confirmationRequested = true)
    }
    fun measured(crop: String, at: Long) {
        if (crop !in cropNames || at > now() || at <= (measuredThrough[crop] ?: Long.MIN_VALUE)) return
        measuredThrough[crop] = at
        pending[crop]?.let { if (at >= it.from) pending.remove(crop) }
    }
    fun clear() { pending.clear(); requestedThrough.clear(); measuredThrough.clear() }
    companion object { const val WINDOW_NANOS = 3_000_000_000L }
}

/** Resource readers sleep after a successful assignment and wake only when that resource can change. */
class TeamResourceSnapshotGate(
    private val cropNames: Set<String>,
    private val pokeBallCropNames: Set<String>,
    private val now: () -> Long = System::nanoTime,
) : Observer {
    override val observerId = ObserverId("TEAM_RESOURCE_SNAPSHOT_GATE", ObservationSource.SCREEN_CAPTURE)
    override val name = "Team Resource Snapshot Gate"
    override val managesAvailability = true
    private var scope: CoroutineScope? = null
    private val assignments = BoundedCaptureAssignments(cropNames, now)
    private val shieldCropNames = cropNames - pokeBallCropNames
    private val speciesBySide = mutableMapOf<ActivePokemonSide, String>()
    private val speciesAtBySide = mutableMapOf<ActivePokemonSide, Long>()
    private val hpAtBySide = mutableMapOf<ActivePokemonSide, Long>()
    private val emptyHpSides = mutableSetOf<ActivePokemonSide>()
    private var initialized = false
    private var startObserved = false
    private var ended = false
    private var attemptFrom = Long.MIN_VALUE
    private var lifecycleAt = Long.MIN_VALUE

    @Synchronized fun isEnabled(cropName: String): Boolean = !ended && assignments.enabled(cropName)
    @Synchronized fun acceptsFrame(cropName: String, at: Long): Boolean =
        !ended && at >= attemptFrom && assignments.accepts(cropName, at)

    /** Preservation alone cannot establish that a badge was visible or successfully interpreted. */
    fun captured(@Suppress("UNUSED_PARAMETER") cropName: String) = Unit

    @Synchronized internal fun requestSnapshot(requestedCrops: Set<String> = cropNames, observedAt: Long = now()) {
        if (!ended && observedAt >= attemptFrom) assignments.request(requestedCrops, observedAt)
    }
    @Synchronized internal fun requestPokeBallSnapshot(observedAt: Long = now()) = requestSnapshot(pokeBallCropNames, observedAt)
    @Synchronized internal fun requestResourceWatch(observedAt: Long = now()) = requestSnapshot(shieldCropNames, observedAt)
    @Synchronized internal fun requestFaintWatch(observedAt: Long = now()) = requestPokeBallSnapshot(observedAt)
    /** A scheduler hint requests independent counting; it carries no departure or faint testimony. */
    @Synchronized fun requestDepartureCheck(side: ActivePokemonSide, observedAt: Long) =
        requestSnapshot(setOf(ballCrop(side)), observedAt)
    @Synchronized internal fun measured(cropName: String, observedAt: Long) {
        if (!ended && observedAt >= attemptFrom) assignments.measured(cropName, observedAt)
    }
    /** Scheduling feedback only; a candidate is not a count or faint testimony. */
    @Synchronized fun candidateRead(cropName: String, capturedAt: Long) {
        if (!ended && capturedAt >= attemptFrom) assignments.candidate(cropName, capturedAt)
    }

    private fun requestMissingBaseline(crops: Set<String>, at: Long) {
        requestSnapshot(crops.filterTo(linkedSetOf()) {
            !assignments.hasMeasurement(it) && !assignments.enabled(it)
        }, at)
    }

    private fun initialize(at: Long) {
        if (initialized || ended || at < attemptFrom || at > now()) return
        initialized = true
        requestSnapshot(observedAt = at)
    }
    private fun sideCrops(side: ActivePokemonSide): Set<String> = cropNames.filterTo(linkedSetOf()) {
        it == ballCrop(side) || it == shieldCrop(side)
    }
    private fun ballCrop(side: ActivePokemonSide): String = if (side == ActivePokemonSide.PLAYER)
        BattleCropContracts.playerPokeBalls.cropName else BattleCropContracts.opponentPokeBalls.cropName
    private fun shieldCrop(side: ActivePokemonSide): String = if (side == ActivePokemonSide.PLAYER)
        BattleCropContracts.playerShields.cropName else BattleCropContracts.opponentShields.cropName

    @Synchronized internal fun observeSpecies(side: ActivePokemonSide, species: String, observedAt: Long = now()) {
        if (ended || observedAt < attemptFrom || observedAt > now() ||
            observedAt <= (speciesAtBySide[side] ?: Long.MIN_VALUE)) return
        speciesAtBySide[side] = observedAt
        initialize(observedAt)
        val previous = speciesBySide.put(side, species)
        if (previous == null) {
            // The first pre-GO window may contain only one readable frame.
            // This independent entry cue retries only the still-missing side.
            requestMissingBaseline(sideCrops(side), observedAt)
        } else if (!species.equals(previous, ignoreCase = true)) {
            emptyHpSides -= side
            hpAtBySide[side] = maxOf(hpAtBySide[side] ?: Long.MIN_VALUE, observedAt)
            requestSnapshot(sideCrops(side), observedAt)
        }
    }
    /** Empty HP can wake the count reader; red HP belongs to the separate terminal HP watch. */
    @Synchronized internal fun observeActiveHp(side: ActivePokemonSide, filledFraction: Float, observedAt: Long = now()) {
        if (ended || observedAt < attemptFrom || observedAt > now() || !filledFraction.isFinite() ||
            filledFraction !in 0f..1f || observedAt <= (hpAtBySide[side] ?: Long.MIN_VALUE)) return
        hpAtBySide[side] = observedAt
        initialize(observedAt)
        if (filledFraction == 0f) {
            if (emptyHpSides.add(side)) requestSnapshot(setOf(ballCrop(side)), observedAt)
        } else emptyHpSides -= side
    }
    @Synchronized internal fun observeDamageTick(damagedSide: ActivePokemonSide, afterFraction: Float, observedAt: Long = now()) {
        // A confirmed zero is useful even when its direct HP publication was missed.
        if (afterFraction == 0f) observeActiveHp(damagedSide, afterFraction, observedAt)
    }

    @Synchronized internal fun observe(article: RealityArticle) {
        val at = article.monotonicTimeNanos ?: return
        if (at > now()) return
        when (val payload = article.payload) {
            ObservationRestartedByUser -> if (at > lifecycleAt) reset(at)
            ScreenIgnoredByUser, ObservationSessionStoppedByUser, is MatchEnded -> observeMatchEnded(at)
            else -> {
                if (ended || at < attemptFrom) return
                when (payload) {
                    MatchStarted -> {
                        initialize(at)
                        if (!startObserved) {
                            startObserved = true
                            requestMissingBaseline(cropNames, at)
                        }
                    }
                    GetReadyWitnessed, AttackIncoming, ChargeMoveUsedAnnounced -> requestResourceWatch(at)
                    is ActivePokemonSpeciesWitnessed -> observeSpecies(payload.side, payload.speciesName, at)
                    is ActiveHpBarMeasured -> observeActiveHp(payload.side, payload.filledFraction, at)
                    is ActiveHpBarDamageTickMeasured -> observeDamageTick(payload.damagedSide, payload.afterFraction, at)
                    is ActivePokemonFaintedWitnessed -> requestSnapshot(sideCrops(payload.side), at)
                    is ForcedReplacementPromptWitnessed -> requestSnapshot(sideCrops(ActivePokemonSide.PLAYER), at)
                    is SpeciesCheckMeasured -> if (payload.status == "OPENED" && payload.reason in setOf("SWITCH", "FAINT"))
                        requestSnapshot(sideCrops(payload.side), payload.triggerMonotonicNanos)
                    is PlayerPokeBallCountMeasured -> measured(BattleCropContracts.playerPokeBalls.cropName, at)
                    is PlayerShieldCountMeasured -> measured(BattleCropContracts.playerShields.cropName, at)
                    is OpponentBattleResourceCountMeasured -> measured(if (payload.resource == OpponentBattleResource.POKE_BALLS)
                        BattleCropContracts.opponentPokeBalls.cropName else BattleCropContracts.opponentShields.cropName, at)
                    else -> Unit
                }
            }
        }
    }
    override fun start(match: Match) {
        if (scope != null) return
        synchronized(this) { reset(Long.MIN_VALUE) }
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { gateScope ->
            gateScope.launch(start = CoroutineStart.UNDISPATCHED) { match.articles.collect(::observe) }
        }
    }
    override fun stop() {
        scope?.cancel("Resource snapshot gate stopped"); scope = null
        observeMatchEnded()
    }
    @Synchronized internal fun observeMatchEnded(at: Long = now()) {
        if (at < attemptFrom || at < lifecycleAt || at > now()) return
        lifecycleAt = at
        ended = true
        assignments.clear()
    }
    private fun reset(from: Long) {
        ended = false; initialized = false; startObserved = false; attemptFrom = from; lifecycleAt = from; assignments.clear()
        speciesBySide.clear(); speciesAtBySide.clear(); hpAtBySide.clear(); emptyHpSides.clear()
    }
}

/** Initial player bench acquisition, then bounded assignments for player changes or replacement text. */
class InactiveBenchSnapshotGate(
    private val cropNames: Set<String>,
    private val now: () -> Long = System::nanoTime,
) : Observer {
    override val observerId = ObserverId("INACTIVE_BENCH_SNAPSHOT_GATE", ObservationSource.SCREEN_CAPTURE)
    override val name = "Inactive Bench Snapshot Gate"
    override val managesAvailability = true
    private var scope: CoroutineScope? = null
    private val assignments = BoundedCaptureAssignments(cropNames, now)
    private var initialized = false
    private var species: String? = null
    private var speciesAt = Long.MIN_VALUE
    private var ended = false
    private var attemptFrom = Long.MIN_VALUE
    private var lifecycleAt = Long.MIN_VALUE

    @Synchronized fun isEnabled(cropName: String): Boolean = !ended && assignments.enabled(cropName)
    @Synchronized fun acceptsFrame(cropName: String, at: Long): Boolean =
        !ended && at >= attemptFrom && assignments.accepts(cropName, at)
    fun captured(@Suppress("UNUSED_PARAMETER") cropName: String) = Unit
    private fun initialize(at: Long) {
        if (initialized || ended || at < attemptFrom || at > now()) return
        initialized = true; requestSnapshot(at)
    }
    private fun requestSnapshot(at: Long) {
        if (!ended && at >= attemptFrom) assignments.request(cropNames, at)
    }
    @Synchronized internal fun measured(cropName: String, at: Long) {
        if (!ended && at >= attemptFrom) assignments.measured(cropName, at)
    }
    @Synchronized internal fun observe(article: RealityArticle) {
        val at = article.monotonicTimeNanos ?: return
        if (at > now()) return
        when (val payload = article.payload) {
            ObservationRestartedByUser -> if (at > lifecycleAt) reset(at)
            ScreenIgnoredByUser, ObservationSessionStoppedByUser, is MatchEnded -> end(at)
            else -> {
                if (ended || at < attemptFrom || at > now()) return
                when (payload) {
                    MatchStarted -> initialize(at)
                    is ActiveHpBarMeasured -> if (payload.side == ActivePokemonSide.PLAYER) initialize(at)
                    is ActivePokemonSpeciesWitnessed -> if (payload.side == ActivePokemonSide.PLAYER && at > speciesAt) {
                        speciesAt = at
                        initialize(at)
                        if (species != null && !payload.speciesName.equals(species, true)) requestSnapshot(at)
                        species = payload.speciesName
                    }
                    is ActivePokemonFaintedWitnessed -> if (payload.side == ActivePokemonSide.PLAYER) requestSnapshot(at)
                    is ForcedReplacementPromptWitnessed -> requestSnapshot(at)
                    is SpeciesCheckMeasured -> if (payload.side == ActivePokemonSide.PLAYER && payload.status == "OPENED" &&
                        payload.reason in setOf("SWITCH", "FAINT")) requestSnapshot(payload.triggerMonotonicNanos)
                    is PlayerInactiveHpBarMeasured -> measured(if (payload.slot == 0)
                        BattleCropContracts.playerInactiveUpperHpBar.cropName else BattleCropContracts.playerInactiveLowerHpBar.cropName, at)
                    is PlayerInactiveSpeciesSpriteFingerprintMeasured -> measured(if (payload.slot == 0)
                        BattleCropContracts.playerInactiveUpperSpeciesSprite.cropName else BattleCropContracts.playerInactiveLowerSpeciesSprite.cropName, at)
                    else -> Unit
                }
            }
        }
    }
    override fun start(match: Match) {
        if (scope != null) return
        synchronized(this) { reset(Long.MIN_VALUE) }
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { gateScope ->
            gateScope.launch(start = CoroutineStart.UNDISPATCHED) { match.articles.collect(::observe) }
        }
    }
    override fun stop() {
        scope?.cancel("Bench snapshot gate stopped"); scope = null
        synchronized(this) { end(now()) }
    }
    private fun end(at: Long) {
        if (at < attemptFrom || at < lifecycleAt || at > now()) return
        lifecycleAt = at
        ended = true
        assignments.clear()
    }
    private fun reset(from: Long) {
        initialized = false; species = null; speciesAt = Long.MIN_VALUE
        ended = false; attemptFrom = from; lifecycleAt = from; assignments.clear()
    }
}

/** Urgent result attention plus a sparse check for concessions with healthy reserves. */
class MatchOutcomeCaptureGate(
    private val now: () -> Long = System::nanoTime
) : Observer {
    override val observerId = ObserverId("MATCH_OUTCOME_CAPTURE_GATE", ObservationSource.SCREEN_CAPTURE)
    override val name = "Match Outcome Capture Gate"
    override val managesAvailability = true
    private var scope: CoroutineScope? = null
    @Volatile private var battleStartedAt: Long? = null
    private val lastHpSeenAt = mutableMapOf<ActivePokemonSide, Long>()
    private val lastBattlefieldHpSeenAt = mutableMapOf<ActivePokemonSide, Long>()
    @Volatile private var probeUntil: Long = 0L
    @Volatile private var ended = false
    @Volatile private var playerBallCount: Int? = null
    @Volatile private var opponentBallCount: Int? = null
    private val finalCriticalHpSides = mutableSetOf<ActivePokemonSide>()
    private var lastChargedMoveAt: Long? = null
    private var lastProbeFrameAt: Long? = null

    @Synchronized fun isEnabled(): Boolean {
        if (ended) return false
        val current = now()
        if (current <= probeUntil) return true
        battleStartedAt ?: return false
        return ActivePokemonSide.entries.any { side ->
            val lastHp = lastHpSeenAt[side] ?: return@any false
            if (!isLastTeamMember(side)) return@any false
            val age = current - lastHp
            (side in finalCriticalHpSides && age in BATTLEFIELD_ABSENCE_NANOS..FINAL_TEAM_PROBE_NANOS) ||
                age in FINAL_MEMBER_HP_LOST_NANOS..FINAL_MEMBER_RECOVERY_NANOS
        }
    }

    /**
     * A concession can end an established battle with healthy HP and reserves.
     * Keep a sparse independent check until result text is accepted; an urgent
     * window already supplies the same crop and suppresses this duplicate lane.
     */
    @Synchronized fun isProbeEnabled(): Boolean = !ended && battleStartedAt != null && !isEnabled()

    /**
     * Normal sparse checks stay two seconds apart. A final-member charged move
     * may finish before a long Rocket dialog, after the original fast window
     * has expired. Retain a short, denser tail without extending four-Hz OCR.
     * This changes acquisition only; pixels still have to establish the result.
     */
    @Synchronized fun acceptsProbeFrame(capturedAtNanos: Long): Boolean {
        val startedAt = battleStartedAt ?: return false
        if (!isProbeEnabled() || capturedAtNanos !in startedAt..now()) return false
        val interval = probeIntervalNanos(capturedAtNanos)
        val previous = lastProbeFrameAt
        if (previous != null && (capturedAtNanos <= previous || capturedAtNanos - previous < interval)) return false
        lastProbeFrameAt = capturedAtNanos
        return true
    }

    @Synchronized internal fun probeIntervalNanos(at: Long = now()): Long {
        val chargedAt = lastChargedMoveAt ?: return PROBE_CAPTURE_INTERVAL_NANOS
        if (ended || at < chargedAt || at - chargedAt > LATE_CHARGED_RESULT_NANOS) return PROBE_CAPTURE_INTERVAL_NANOS
        val finalBarMissing = ActivePokemonSide.entries.any { side ->
            // A measured charge-camera bar, including a finishing zero, does
            // not establish that the normal battlefield has returned.
            val hpAt = lastBattlefieldHpSeenAt[side] ?: return@any false
            val sideCount = if (side == ActivePokemonSide.PLAYER) playerBallCount else opponentBallCount
            val otherCount = if (side == ActivePokemonSide.PLAYER) opponentBallCount else playerBallCount
            sideCount != null && sideCount in 0..1 && (otherCount ?: 0) > 0 && chargedAt >= hpAt &&
                at - hpAt in FINAL_MEMBER_HP_LOST_NANOS..LATE_FINAL_BAR_ABSENCE_NANOS
        }
        return if (finalBarMissing) LATE_PROBE_CAPTURE_INTERVAL_NANOS else PROBE_CAPTURE_INTERVAL_NANOS
    }

    @Synchronized internal fun observeChargedMove(at: Long) {
        val startedAt = battleStartedAt ?: return
        if (!ended && at in startedAt..now() && (lastChargedMoveAt == null || at > lastChargedMoveAt!!)) {
            lastChargedMoveAt = at
        }
    }

    internal fun observeMatchStarted(at: Long) {
        battleStartedAt = at
    }

    @Synchronized internal fun observeActiveHp(
        side: ActivePokemonSide, at: Long, filledFraction: Float, chargedCamera: Boolean = false,
    ) {
        if (lastHpSeenAt[side]?.let { at < it } == true) return
        lastHpSeenAt[side] = at
        if (!chargedCamera) lastBattlefieldHpSeenAt[side] = at
        if (battleStartedAt == null) battleStartedAt = at
        // Either team can lose its final member while the other still has a
        // bench. Red HP on a side with reserves must not open this window.
        if (isLastTeamMember(side) && filledFraction <= CRITICAL_HP_FRACTION) {
            finalCriticalHpSides += side
            probeUntil = maxOf(probeUntil, at + FAINT_PROBE_NANOS)
        } else if (filledFraction > CRITICAL_HP_FRACTION) {
            finalCriticalHpSides -= side
        }
    }

    @Synchronized internal fun observePlayerBallCount(at: Long, visibleCount: Int) {
        playerBallCount = visibleCount
        updateTeamState(at)
    }

    @Synchronized internal fun observeOpponentBallCount(at: Long, visibleCount: Int) {
        opponentBallCount = visibleCount
        updateTeamState(at)
    }

    /** A team running out opens a result check; the result text still decides the outcome. */
    private fun updateTeamState(at: Long) {
        val player = playerBallCount ?: return
        val opponent = opponentBallCount ?: return
        finalCriticalHpSides.removeAll { !isLastTeamMember(it) }
        if ((player == 0 && opponent > 0) || (opponent == 0 && player > 0)) {
            probeUntil = maxOf(probeUntil, at + FINAL_TEAM_PROBE_NANOS)
        }
    }

    private fun isLastTeamMember(side: ActivePokemonSide): Boolean = when (side) {
        ActivePokemonSide.PLAYER -> playerBallCount == 1 && (opponentBallCount ?: 0) > 0
        ActivePokemonSide.OPPONENT -> opponentBallCount == 1 && (playerBallCount ?: 0) > 0
    }

    internal fun observeMatchEnded() {
        ended = true
    }

    /** A matched result track opens OCR even if the final team badges were hidden.
     * Use the end of the measured clip, not publication time, to bound stale evidence.
     */
    @Synchronized internal fun observeResultMusic(at: Long) {
        val start = battleStartedAt ?: return
        if (ended || at < start || at > now()) return
        probeUntil = maxOf(probeUntil, at + FINAL_TEAM_PROBE_NANOS)
    }

    override fun start(match: Match) {
        if (scope != null) return
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { gateScope ->
            gateScope.launch {
                match.articles.collect { article ->
                    val at = article.monotonicTimeNanos ?: now()
                    when (val payload = article.payload) {
                        is MatchStarted -> observeMatchStarted(at)
                        is ChargeMoveUsedAnnounced -> observeChargedMove(at)
                        is ActiveHpBarMeasured -> observeActiveHp(payload.side, at, payload.filledFraction,
                            chargedCamera = ChargedImpactHpSource.matches(article.sourceId.id))
                        is PlayerPokeBallCountMeasured -> observePlayerBallCount(at, payload.visibleCount)
                        is OpponentBattleResourceCountMeasured -> if (payload.resource == OpponentBattleResource.POKE_BALLS) {
                            observeOpponentBallCount(at, payload.visibleCount)
                        }
                        is MatchEnded -> observeMatchEnded()
                        is MatchResultMusicMeasured -> if (payload.theme == "WIN" || payload.theme == "LOSS") {
                            observeResultMusic(at + payload.durationNanos)
                        }
                    }
                }
            }
        }
    }

    @Synchronized override fun stop() {
        scope?.cancel("Gate stopped")
        scope = null
        battleStartedAt = null
        lastHpSeenAt.clear()
        lastBattlefieldHpSeenAt.clear()
        probeUntil = 0L
        ended = false
        playerBallCount = null
        opponentBallCount = null
        finalCriticalHpSides.clear()
        lastChargedMoveAt = null
        lastProbeFrameAt = null
    }

    companion object {
        const val PROBE_CAPTURE_INTERVAL_NANOS = 2_000_000_000L
        const val LATE_PROBE_CAPTURE_INTERVAL_NANOS = 500_000_000L
        private const val LATE_CHARGED_RESULT_NANOS = 45_000_000_000L
        private const val LATE_FINAL_BAR_ABSENCE_NANOS = 60_000_000_000L
        private const val BATTLEFIELD_ABSENCE_NANOS = 250_000_000L
        private const val FAINT_PROBE_NANOS = 8_000_000_000L
        private const val FINAL_TEAM_PROBE_NANOS = 10_000_000_000L
        // The final bar can vanish above red, before the last ball dims. This
        // only opens a bounded OCR window; it never declares a winner itself.
        private const val FINAL_MEMBER_HP_LOST_NANOS = 2_000_000_000L
        private const val FINAL_MEMBER_RECOVERY_NANOS = 35_000_000_000L
        private const val CRITICAL_HP_FRACTION = 0.20f
    }
}
