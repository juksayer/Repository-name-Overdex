package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActiveHpBarMeasured
import com.example.overdex.battle.custody.ActiveHpBarDamageTickMeasured
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ActivePokemonSpeciesWitnessed
import com.example.overdex.battle.custody.BattleCryCandidatesMeasured
import com.example.overdex.battle.custody.ChargeMoveUsedAnnounced
import com.example.overdex.battle.custody.MatchEnded
import com.example.overdex.battle.custody.MatchResultMusicMeasured
import com.example.overdex.battle.custody.MatchStarted
import com.example.overdex.battle.custody.OpponentBattleResource
import com.example.overdex.battle.custody.OpponentBattleResourceCountMeasured
import com.example.overdex.battle.custody.PlayerPokeBallCountMeasured
import com.example.overdex.battle.custody.SpeciesCheckMeasured
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.battle.timeline.observer.ObservationSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Two-frame resource snapshots, with a brief ball watch spanning entry and faint animations. */
class TeamResourceSnapshotGate(
    private val cropNames: Set<String>,
    private val pokeBallCropNames: Set<String>,
    private val now: () -> Long = System::nanoTime,
) : Observer {
    override val observerId = ObserverId("TEAM_RESOURCE_SNAPSHOT_GATE", ObservationSource.SCREEN_CAPTURE)
    override val name = "Team Resource Snapshot Gate"
    override val managesAvailability = true
    private var scope: CoroutineScope? = null
    private val remainingByCrop = cropNames.associateWith { 0 }.toMutableMap()
    private val criticalHpSides = mutableSetOf<ActivePokemonSide>()
    private val lastHpSampleAtBySide = mutableMapOf<ActivePokemonSide, Long>()
    private val speciesAtBySide = mutableMapOf<ActivePokemonSide, Long>()
    private var faintWatchUntil = Long.MIN_VALUE
    private var resourceWatchUntil = Long.MIN_VALUE
    private val speciesBySide = mutableMapOf<ActivePokemonSide, String>()
    private var ended = false

    @Synchronized fun isEnabled(cropName: String): Boolean =
        !ended && (remainingByCrop.getOrDefault(cropName, 0) > 0 ||
            (cropName in cropNames && now() < resourceWatchUntil) ||
            (cropName in pokeBallCropNames && now() < faintWatchUntil))

    @Synchronized fun captured(cropName: String) {
        val remaining = remainingByCrop.getOrDefault(cropName, 0)
        if (remaining > 0) remainingByCrop[cropName] = remaining - 1
    }

    @Synchronized internal fun requestSnapshot(requestedCrops: Set<String> = cropNames) {
        requestedCrops.forEach { cropName ->
            if (cropName in cropNames) remainingByCrop[cropName] = REQUIRED_SAMPLES
        }
    }

    @Synchronized internal fun requestPokeBallSnapshot() = requestSnapshot(pokeBallCropNames)

    /** Give hidden/transitioning badges time to become readable before settling. */
    @Synchronized internal fun requestResourceWatch() {
        requestSnapshot()
        resourceWatchUntil = now() + 3_000_000_000L
    }

    /** Entry/faint animations can hide a badge for the first two frames. */
    @Synchronized internal fun requestFaintWatch(observedAt: Long = now()) {
        requestPokeBallSnapshot()
        faintWatchUntil = maxOf(faintWatchUntil, observedAt + FAINT_WATCH_NANOS)
    }

    @Synchronized internal fun observeSpecies(side: ActivePokemonSide, species: String, observedAt: Long = now()) {
        if (ended || speciesAtBySide[side]?.let { observedAt < it } == true) return
        speciesAtBySide[side] = observedAt
        val previous = speciesBySide.put(side, species)
        if (!species.equals(previous, ignoreCase = true)) {
            criticalHpSides -= side
            lastHpSampleAtBySide[side] = maxOf(lastHpSampleAtBySide[side] ?: Long.MIN_VALUE, observedAt)
            requestResourceWatch()
            requestFaintWatch()
        }
    }

    /** Fresh red HP keeps observation available; only a later ball decrease establishes a faint. */
    @Synchronized internal fun observeActiveHp(side: ActivePokemonSide, filledFraction: Float, observedAt: Long = now()) {
        val current = now()
        if (ended || !filledFraction.isFinite() || filledFraction !in 0f..1f ||
            observedAt > current || current - observedAt >= FAINT_WATCH_NANOS ||
            lastHpSampleAtBySide[side]?.let { observedAt <= it } == true) return
        lastHpSampleAtBySide[side] = observedAt
        when {
            filledFraction <= CRITICAL_HP_FRACTION -> {
                if (criticalHpSides.add(side)) requestFaintWatch(observedAt)
                else faintWatchUntil = maxOf(faintWatchUntil, observedAt + FAINT_WATCH_NANOS)
            }
            side in criticalHpSides && filledFraction >= CRITICAL_HP_REARM_FRACTION -> {
                criticalHpSides -= side
            }
        }
    }

    /** Each further hit in red HP refreshes the brief Poké Ball watch window. */
    @Synchronized internal fun observeDamageTick(damagedSide: ActivePokemonSide, afterFraction: Float) {
        if (afterFraction <= CRITICAL_HP_FRACTION) {
            criticalHpSides += damagedSide
            requestFaintWatch()
        }
    }

    override fun start(match: Match) {
        if (scope != null) return
        synchronized(this) { clearPending(); ended = false }
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { gateScope ->
            gateScope.launch {
                match.articles.collect { article ->
                    when (val payload = article.payload) {
                        is RawTestimony -> if (
                            article.sourceId.id == "ANNOUNCEMENT_WITNESS" &&
                            (payload.data as? String)?.trim()?.startsWith("Go,", ignoreCase = true) == true
                        ) { requestResourceWatch(); requestFaintWatch() }
                        is MatchStarted -> { requestResourceWatch(); requestFaintWatch() }
                        com.example.overdex.battle.custody.ChargeMoveUsedAnnounced -> requestResourceWatch()
                        is ActivePokemonSpeciesWitnessed -> observeSpecies(payload.side, payload.speciesName,
                            article.monotonicTimeNanos ?: now())
                        is ActiveHpBarMeasured -> observeActiveHp(payload.side, payload.filledFraction,
                            article.monotonicTimeNanos ?: now())
                        is ActiveHpBarDamageTickMeasured -> observeDamageTick(payload.damagedSide, payload.afterFraction)
                        is BattleCryCandidatesMeasured -> if (payload.candidates.isNotEmpty() && payload.cueKind != "FAST_MOVE_IMPACT") {
                            requestFaintWatch()
                        }
                        is SpeciesCheckMeasured -> if (
                            payload.status == "OPENED" &&
                            payload.reason in setOf("ENTRY", "SWITCH", "FAINT")
                        ) { requestResourceWatch(); requestFaintWatch() }
                        is MatchEnded -> observeMatchEnded()
                    }
                }
            }
        }
    }

    override fun stop() {
        scope?.cancel("Resource snapshot gate stopped")
        scope = null
        observeMatchEnded()
    }

    @Synchronized internal fun observeMatchEnded() {
        ended = true
        clearPending()
    }

    @Synchronized private fun clearPending() {
        remainingByCrop.keys.forEach { remainingByCrop[it] = 0 }
        criticalHpSides.clear()
        lastHpSampleAtBySide.clear()
        speciesAtBySide.clear()
        speciesBySide.clear()
        faintWatchUntil = Long.MIN_VALUE
        resourceWatchUntil = Long.MIN_VALUE
    }

    private companion object {
        const val REQUIRED_SAMPLES = 2
        const val CRITICAL_HP_FRACTION = 0.20f
        const val CRITICAL_HP_REARM_FRACTION = 0.24f
        const val FAINT_WATCH_NANOS = 3_000_000_000L
    }
}

/** One bench snapshot per entry, switch, or faint, shared by the four inactive-card crops. */
class InactiveBenchSnapshotGate(
    private val cropNames: Set<String>
) : Observer {
    override val observerId = ObserverId("INACTIVE_BENCH_SNAPSHOT_GATE", ObservationSource.SCREEN_CAPTURE)
    override val name = "Inactive Bench Snapshot Gate"
    override val managesAvailability = true
    private var scope: CoroutineScope? = null
    private val pending = linkedSetOf<String>()
    private var sawLiveHp = false

    @Synchronized fun isEnabled(cropName: String): Boolean = cropName in pending
    @Synchronized fun captured(cropName: String) { pending.remove(cropName) }
    @Synchronized private fun requestSnapshot() { pending += cropNames }

    override fun start(match: Match) {
        if (scope != null) return
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { gateScope ->
            gateScope.launch {
                match.articles.collect { article ->
                    when (val payload = article.payload) {
                        is MatchStarted -> requestSnapshot()
                        is ActivePokemonSpeciesWitnessed -> if (payload.side == ActivePokemonSide.PLAYER) requestSnapshot()
                        is ActiveHpBarMeasured -> if (!sawLiveHp) {
                            sawLiveHp = true
                            requestSnapshot()
                        }
                        is SpeciesCheckMeasured -> if (
                            payload.side == ActivePokemonSide.PLAYER && payload.status == "OPENED" &&
                            payload.reason in setOf("ENTRY", "EMPTY_HP")
                        ) requestSnapshot()
                    }
                }
            }
        }
    }

    override fun stop() {
        scope?.cancel("Gate stopped")
        scope = null
        synchronized(this) {
            pending.clear()
            sawLiveHp = false
        }
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
