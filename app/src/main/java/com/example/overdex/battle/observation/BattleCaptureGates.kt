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
    private var faintWatchUntil = Long.MIN_VALUE
    private var resourceWatchUntil = Long.MIN_VALUE
    private val speciesBySide = mutableMapOf<ActivePokemonSide, String>()

    @Synchronized fun isEnabled(cropName: String): Boolean =
        remainingByCrop.getOrDefault(cropName, 0) > 0 ||
            (cropName in cropNames && now() < resourceWatchUntil) ||
            (cropName in pokeBallCropNames && now() < faintWatchUntil)

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
    @Synchronized internal fun requestFaintWatch() {
        requestPokeBallSnapshot()
        faintWatchUntil = now() + 3_000_000_000L
    }

    @Synchronized internal fun observeSpecies(side: ActivePokemonSide, species: String) {
        val previous = speciesBySide.put(side, species)
        if (!species.equals(previous, ignoreCase = true)) {
            requestResourceWatch()
            requestFaintWatch()
        }
    }

    /** Red HP is an early warning. A later ball-count decrease remains the faint evidence. */
    @Synchronized internal fun observeActiveHp(side: ActivePokemonSide, filledFraction: Float) {
        when {
            side !in criticalHpSides && filledFraction <= CRITICAL_HP_FRACTION -> {
                criticalHpSides += side
                requestFaintWatch()
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
                        is ActivePokemonSpeciesWitnessed -> observeSpecies(payload.side, payload.speciesName)
                        is ActiveHpBarMeasured -> observeActiveHp(payload.side, payload.filledFraction)
                        is ActiveHpBarDamageTickMeasured -> observeDamageTick(payload.damagedSide, payload.afterFraction)
                        is BattleCryCandidatesMeasured -> if (payload.candidates.isNotEmpty() && payload.cueKind != "FAST_MOVE_IMPACT") {
                            requestFaintWatch()
                        }
                        is SpeciesCheckMeasured -> if (
                            payload.status == "OPENED" &&
                            payload.reason in setOf("ENTRY", "SWITCH", "FAINT")
                        ) { requestResourceWatch(); requestFaintWatch() }
                        is MatchEnded -> clearPending()
                    }
                }
            }
        }
    }

    override fun stop() {
        scope?.cancel("Resource snapshot gate stopped")
        scope = null
        clearPending()
    }

    @Synchronized private fun clearPending() {
        remainingByCrop.keys.forEach { remainingByCrop[it] = 0 }
        criticalHpSides.clear()
        speciesBySide.clear()
        faintWatchUntil = Long.MIN_VALUE
        resourceWatchUntil = Long.MIN_VALUE
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
    private val lastHpSeenAt = mutableMapOf<ActivePokemonSide, Long>()
    @Volatile private var probeUntil: Long = 0L
    @Volatile private var ended = false
    @Volatile private var playerBallCount: Int? = null
    @Volatile private var opponentBallCount: Int? = null
    private val finalCriticalHpSides = mutableSetOf<ActivePokemonSide>()

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

    internal fun observeMatchStarted(at: Long) {
        battleStartedAt = at
    }

    @Synchronized internal fun observeActiveHp(side: ActivePokemonSide, at: Long, filledFraction: Float) {
        lastHpSeenAt[side] = at
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

    override fun start(match: Match) {
        if (scope != null) return
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob()).also { gateScope ->
            gateScope.launch {
                match.articles.collect { article ->
                    val at = article.monotonicTimeNanos ?: now()
                    when (val payload = article.payload) {
                        is MatchStarted -> observeMatchStarted(at)
                        is ActiveHpBarMeasured -> observeActiveHp(payload.side, at, payload.filledFraction)
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

    @Synchronized override fun stop() {
        scope?.cancel("Gate stopped")
        scope = null
        battleStartedAt = null
        lastHpSeenAt.clear()
        probeUntil = 0L
        ended = false
        playerBallCount = null
        opponentBallCount = null
        finalCriticalHpSides.clear()
    }

    private companion object {
        const val BATTLEFIELD_ABSENCE_NANOS = 250_000_000L
        const val FAINT_PROBE_NANOS = 8_000_000_000L
        const val FINAL_TEAM_PROBE_NANOS = 10_000_000_000L
        // The final bar can vanish above red, before the last ball dims. This
        // only opens a bounded OCR window; it never declares a winner itself.
        const val FINAL_MEMBER_HP_LOST_NANOS = 2_000_000_000L
        const val FINAL_MEMBER_RECOVERY_NANOS = 35_000_000_000L
        const val CRITICAL_HP_FRACTION = 0.20f
    }
}
