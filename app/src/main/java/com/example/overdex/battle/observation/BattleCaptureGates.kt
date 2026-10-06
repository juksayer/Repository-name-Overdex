package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActiveHpBarMeasured
import com.example.overdex.battle.custody.ActiveHpBarDamageTickMeasured
import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.ActivePokemonSpeciesWitnessed
import com.example.overdex.battle.custody.BattleCryCandidatesMeasured
import com.example.overdex.battle.custody.MatchEnded
import com.example.overdex.battle.custody.MatchStarted
import com.example.overdex.battle.custody.OpponentBattleResource
import com.example.overdex.battle.custody.OpponentBattleResourceCountMeasured
import com.example.overdex.battle.custody.SpeciesCheckMeasured
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.timeline.observer.ObserverId
import com.example.overdex.battle.timeline.observer.ObservationSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Two-frame resource snapshots requested by start/entry/switch cues and cry checks. */
class OpponentResourceSnapshotGate(
    private val cropNames: Set<String>,
    private val pokeBallCropName: String
) : Observer {
    override val observerId = ObserverId("OPPONENT_RESOURCE_SNAPSHOT_GATE", ObservationSource.SCREEN_CAPTURE)
    override val name = "Opponent Resource Snapshot Gate"
    override val managesAvailability = true
    private var scope: CoroutineScope? = null
    private val remainingByCrop = cropNames.associateWith { 0 }.toMutableMap()
    private var opponentHpIsCritical = false

    @Synchronized fun isEnabled(cropName: String): Boolean = remainingByCrop.getOrDefault(cropName, 0) > 0

    @Synchronized fun captured(cropName: String) {
        val remaining = remainingByCrop.getOrDefault(cropName, 0)
        if (remaining > 0) remainingByCrop[cropName] = remaining - 1
    }

    @Synchronized internal fun requestSnapshot(requestedCrops: Set<String> = cropNames) {
        requestedCrops.forEach { cropName ->
            if (cropName in cropNames) remainingByCrop[cropName] = REQUIRED_SAMPLES
        }
    }

    @Synchronized internal fun requestPokeBallSnapshot() = requestSnapshot(setOf(pokeBallCropName))

    /** Red HP is an early warning. A later ball-count decrease remains the faint evidence. */
    @Synchronized internal fun observeActiveHp(side: ActivePokemonSide, filledFraction: Float) {
        if (side != ActivePokemonSide.OPPONENT) return
        when {
            !opponentHpIsCritical && filledFraction <= CRITICAL_HP_FRACTION -> {
                opponentHpIsCritical = true
                requestPokeBallSnapshot()
            }
            opponentHpIsCritical && filledFraction >= CRITICAL_HP_REARM_FRACTION -> {
                opponentHpIsCritical = false
            }
        }
    }

    /** Each further hit in red HP refreshes the brief Poké Ball watch window. */
    @Synchronized internal fun observeDamageTick(damagedSide: ActivePokemonSide, afterFraction: Float) {
        if (damagedSide == ActivePokemonSide.OPPONENT && afterFraction <= CRITICAL_HP_FRACTION) {
            opponentHpIsCritical = true
            requestPokeBallSnapshot()
        }
    }

    override fun start(match: Match) {
        if (scope != null) return
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { gateScope ->
            gateScope.launch {
                match.articles.collect { article ->
                    when (val payload = article.payload) {
                        is RawTestimony -> if (
                            article.sourceId.id == "ANNOUNCEMENT_WITNESS" &&
                            (payload.data as? String)?.trim()?.startsWith("Go,", ignoreCase = true) == true
                        ) requestSnapshot()
                        is MatchStarted -> requestSnapshot()
                        is ActiveHpBarMeasured -> observeActiveHp(payload.side, payload.filledFraction)
                        is ActiveHpBarDamageTickMeasured -> observeDamageTick(payload.damagedSide, payload.afterFraction)
                        is BattleCryCandidatesMeasured -> if (payload.candidates.isNotEmpty()) {
                            requestPokeBallSnapshot()
                        }
                        is SpeciesCheckMeasured -> if (
                            payload.status == "OPENED" &&
                            payload.reason in setOf("ENTRY", "SWITCH")
                        ) requestSnapshot()
                    }
                }
            }
        }
    }

    override fun stop() {
        scope?.cancel("Resource snapshot gate stopped")
        scope = null
        synchronized(this) {
            remainingByCrop.keys.forEach { remainingByCrop[it] = 0 }
            opponentHpIsCritical = false
        }
    }

    private companion object {
        const val REQUIRED_SAMPLES = 2
        const val CRITICAL_HP_FRACTION = 0.20f
        const val CRITICAL_HP_REARM_FRACTION = 0.24f
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

/** Opens the result-text crop only when the live battlefield plausibly disappeared. */
class MatchOutcomeCaptureGate(
    private val now: () -> Long = System::nanoTime
) : Observer {
    override val observerId = ObserverId("MATCH_OUTCOME_CAPTURE_GATE", ObservationSource.SCREEN_CAPTURE)
    override val name = "Match Outcome Capture Gate"
    override val managesAvailability = true
    private var scope: CoroutineScope? = null
    @Volatile private var battleStartedAt: Long? = null
    @Volatile private var lastHpSeenAt: Long? = null
    @Volatile private var probeUntil: Long = 0L
    @Volatile private var ended = false
    private var sawPositiveOpponentBallCount = false

    fun isEnabled(): Boolean {
        if (ended) return false
        val current = now()
        if (current <= probeUntil) return true
        val started = battleStartedAt ?: return false
        if (current - started >= LATE_MATCH_PROBE_START_NANOS) return true
        val lastHp = lastHpSeenAt ?: return false
        return current - lastHp >= BATTLEFIELD_ABSENCE_NANOS
    }

    override fun start(match: Match) {
        if (scope != null) return
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { gateScope ->
            gateScope.launch {
                match.articles.collect { article ->
                    val at = article.monotonicTimeNanos ?: now()
                    when (val payload = article.payload) {
                        is MatchStarted -> battleStartedAt = at
                        is ActiveHpBarMeasured -> {
                            lastHpSeenAt = at
                            if (battleStartedAt == null) battleStartedAt = at
                            if (payload.filledFraction <= 0.02f) probeUntil = at + FAINT_PROBE_NANOS
                        }
                        is OpponentBattleResourceCountMeasured -> if (payload.resource == OpponentBattleResource.POKE_BALLS) {
                            if (payload.visibleCount > 0) sawPositiveOpponentBallCount = true
                            if (sawPositiveOpponentBallCount && payload.visibleCount == 0) {
                                probeUntil = at + FINAL_TEAM_PROBE_NANOS
                            }
                        }
                        is MatchEnded -> ended = true
                    }
                }
            }
        }
    }

    override fun stop() {
        scope?.cancel("Gate stopped")
        scope = null
        battleStartedAt = null
        lastHpSeenAt = null
        probeUntil = 0L
        ended = false
        sawPositiveOpponentBallCount = false
    }

    private companion object {
        const val BATTLEFIELD_ABSENCE_NANOS = 1_250_000_000L
        const val FAINT_PROBE_NANOS = 4_000_000_000L
        const val FINAL_TEAM_PROBE_NANOS = 10_000_000_000L
        const val LATE_MATCH_PROBE_START_NANOS = 130_000_000_000L
    }
}
