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

/** Two-frame resource snapshots requested by start/entry/switch cues and cry checks. */
class TeamResourceSnapshotGate(
    private val cropNames: Set<String>,
    private val pokeBallCropNames: Set<String>
) : Observer {
    override val observerId = ObserverId("TEAM_RESOURCE_SNAPSHOT_GATE", ObservationSource.SCREEN_CAPTURE)
    override val name = "Team Resource Snapshot Gate"
    override val managesAvailability = true
    private var scope: CoroutineScope? = null
    private val remainingByCrop = cropNames.associateWith { 0 }.toMutableMap()
    private val criticalHpSides = mutableSetOf<ActivePokemonSide>()

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

    @Synchronized internal fun requestPokeBallSnapshot() = requestSnapshot(pokeBallCropNames)

    /** Red HP is an early warning. A later ball-count decrease remains the faint evidence. */
    @Synchronized internal fun observeActiveHp(side: ActivePokemonSide, filledFraction: Float) {
        when {
            side !in criticalHpSides && filledFraction <= CRITICAL_HP_FRACTION -> {
                criticalHpSides += side
                requestPokeBallSnapshot()
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
            criticalHpSides.clear()
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
    @Volatile private var playerBallCount: Int? = null
    @Volatile private var opponentBallCount: Int? = null
    @Volatile private var finalCriticalHpSeen = false

    fun isEnabled(): Boolean {
        if (ended) return false
        val current = now()
        if (current <= probeUntil) return true
        battleStartedAt ?: return false
        if (!isFinalDuel()) return false
        if (!finalCriticalHpSeen) return false
        val lastHp = lastHpSeenAt ?: return false
        return current - lastHp >= BATTLEFIELD_ABSENCE_NANOS
    }

    internal fun observeMatchStarted(at: Long) {
        battleStartedAt = at
    }

    internal fun observeActiveHp(at: Long, filledFraction: Float) {
        lastHpSeenAt = at
        if (battleStartedAt == null) battleStartedAt = at
        // Red HP is common earlier in a battle. It becomes result evidence only
        // after both badges say that this is the final one-on-one matchup.
        if (isFinalDuel() && filledFraction <= CRITICAL_HP_FRACTION) {
            finalCriticalHpSeen = true
            probeUntil = maxOf(probeUntil, at + FAINT_PROBE_NANOS)
        }
    }

    internal fun observePlayerBallCount(at: Long, visibleCount: Int) {
        playerBallCount = visibleCount
        updateTeamState(at)
    }

    internal fun observeOpponentBallCount(at: Long, visibleCount: Int) {
        opponentBallCount = visibleCount
        updateTeamState(at)
    }

    /** Two total means the final duel; one total means one winner remains. */
    private fun updateTeamState(at: Long) {
        val player = playerBallCount ?: return
        val opponent = opponentBallCount ?: return
        if (player + opponent > 2) finalCriticalHpSeen = false
        if (player + opponent == 1 && (player == 1 || opponent == 1)) {
            probeUntil = maxOf(probeUntil, at + FINAL_TEAM_PROBE_NANOS)
        }
    }

    private fun isFinalDuel(): Boolean = playerBallCount == 1 && opponentBallCount == 1

    internal fun observeMatchEnded() {
        ended = true
    }

    override fun start(match: Match) {
        if (scope != null) return
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { gateScope ->
            gateScope.launch {
                match.articles.collect { article ->
                    val at = article.monotonicTimeNanos ?: now()
                    when (val payload = article.payload) {
                        is MatchStarted -> observeMatchStarted(at)
                        is ActiveHpBarMeasured -> observeActiveHp(at, payload.filledFraction)
                        is PlayerPokeBallCountMeasured -> observePlayerBallCount(at, payload.visibleCount)
                        is OpponentBattleResourceCountMeasured -> if (payload.resource == OpponentBattleResource.POKE_BALLS) {
                            observeOpponentBallCount(at, payload.visibleCount)
                        }
                        is MatchEnded -> observeMatchEnded()
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
        playerBallCount = null
        opponentBallCount = null
        finalCriticalHpSeen = false
    }

    private companion object {
        const val BATTLEFIELD_ABSENCE_NANOS = 250_000_000L
        const val FAINT_PROBE_NANOS = 8_000_000_000L
        const val FINAL_TEAM_PROBE_NANOS = 10_000_000_000L
        const val CRITICAL_HP_FRACTION = 0.20f
    }
}
