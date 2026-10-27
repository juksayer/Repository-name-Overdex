package com.example.overdex.battle.forecast

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.inference.FastMoveUseEvidence
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.model.Move

/** A disposable presentation estimate. None of these values are Timeline testimony. */
data class OpponentEnergyEstimate(
    val speciesName: String,
    val assumedMoveName: String,
    val energy: Int,
    val expectedUses: Int,
    val observedUses: Int,
    val activeTurns: Int,
    val paused: Boolean,
    val partialStart: Boolean,
    /** A witnessed spend the model could not fund: keep the discrepancy visible. */
    val unexplainedSpend: Int,
    val moveIdentified: Boolean,
)

/**
 * Perfect-play hypothesis, reconciled against source-time events. Rebuild this
 * small projection when late observations or a better move identity arrive;
 * never edit an Article or turn a predicted attack into an observed attack.
 *
 * Each running segment earns at most its elapsed complete move cycles. Observed
 * uses remain a separate count and cannot make an impossible energy burst. One
 * first observed completion may belong to a move already in progress when a
 * partial recording began. Interruptions discard unfinished cycles. Stored energy survives
 * switches, is capped at 100, and is spent only for witnessed charged moves.
 */
class OpponentEnergyForecast {
    private data class Event(val at: Long, val payload: TestimonyPayload)
    private val events = linkedMapOf<String, Event>()
    private val moves = mutableMapOf<String, List<Move>>()
    private val candidates = mutableMapOf<String, Set<String>>()
    private val identities = mutableMapOf<String, Pair<Long, FastMoveIdentified>>()
    private val battlefield = ForecastBattlefieldVisibility()

    /** Called on the live frame path, before queued OCR/custody delivery. */
    @Synchronized fun observeBattlefield(at: Long, bothBarsVisible: Boolean) {
        battlefield.observe(at, bothBarsVisible)
    }

    @Synchronized fun registerMoves(species: String, available: List<Move>) {
        moves[key(species)] = available.filter { it.isFast && (it.turns ?: 0) > 0 && it.energy > 0 }
    }

    @Synchronized fun restrictCandidates(species: String, names: List<String>) {
        if (names.isNotEmpty()) candidates[key(species)] = names.map(::key).toSet()
    }

    @Synchronized fun observe(article: RealityArticle) {
        val at = article.monotonicTimeNanos ?: return
        val p = article.payload
        if (p is FastMoveIdentified && p.side == ActivePokemonSide.OPPONENT) {
            if (at >= (identities[key(p.speciesName)]?.first ?: Long.MIN_VALUE)) {
                identities[key(p.speciesName)] = at to p
            }
            return
        }
        if (p is FastMoveUseObserved && !FastMoveUseEvidence.establishesUse(p.evidenceKinds)) return
        if (p !is ActivePokemonSpeciesWitnessed && p !is ActivePokemonFaintedWitnessed &&
            p !is FastMoveUseObserved && p !is ChargedMoveEnergySpent &&
            p !is BattleDecisionTimerObserved && p !is MatchEnded &&
            p != MatchStarted && p != AttackIncoming && p != GetReadyWitnessed &&
            p != ChargeMoveUsedAnnounced && p != ScreenIgnoredByUser &&
            p != ObservationSessionStoppedByUser && p != ObservationRestartedByUser) return
        events.putIfAbsent(article.id.value, Event(at, p))
    }

    @Synchronized fun reset() {
        events.clear(); moves.clear(); candidates.clear(); identities.clear()
        battlefield.clear()
    }

    private fun chosenMove(species: String): Move? {
        val id = key(species)
        val pool = moves[id].orEmpty()
        val identity = identities[id]?.second
        val eligible = candidates[id]?.let { allowed -> pool.filter { key(it.name) in allowed } }
            ?.takeIf { it.isNotEmpty() } ?: pool
        // A tie-break remains a hypothesis. Other compatible identity evidence
        // can select the actual move, including one with less energy per turn.
        if (identity != null && !identity.basis.contains("TIE_BREAK")) {
            eligible.firstOrNull { key(it.name) == key(identity.moveName) }?.let { return it }
        }
        return eligible.sortedWith(compareByDescending<Move> { it.energy.toDouble() / it.turns!! }
            .thenBy { it.name }).firstOrNull()
    }

    @Synchronized fun estimate(now: Long): OpponentEnergyEstimate? {
        data class Balance(var energy: Int = 0, var expected: Int = 0, var observed: Int = 0,
                           var activeNanos: Long = 0, var unexplained: Int = 0, var fainted: Boolean = false)
        val balances = mutableMapOf<String, Balance>()
        val seenUses = mutableSetOf<String>()
        val activeBySide = mutableMapOf<ActivePokemonSide, String>()
        var active: String? = null
        var start: Long? = null
        var partial = true
        var running = false
        var ended = false
        var segmentAt = 0L
        var segmentAwarded = 0
        var segmentInitialUses = 0
        var segmentMeasuredNanos = 0L
        var partialCompletionAvailable = true
        var earliestResume = 0L
        var chargeLockedUntil = Long.MIN_VALUE
        var decisionLockedUntil = Long.MIN_VALUE
        fun balance(name: String) = balances.getOrPut(key(name)) { Balance() }
        fun begin(at: Long, initialUses: Int = 0) {
            segmentAt = at; segmentAwarded = 0; segmentInitialUses = initialUses; segmentMeasuredNanos = 0
        }
        fun advance(at: Long) {
            val name = active ?: return
            if (!running || ended) return
            val b = balance(name)
            val move = chosenMove(name) ?: return
            if (b.fainted) return
            val elapsed = battlefield.elapsed(segmentAt, at)
            b.activeNanos += elapsed - segmentMeasuredNanos
            segmentMeasuredNanos = elapsed
            val uses = segmentInitialUses + battlefield.completedCycles(segmentAt, at, move.turns!! * TURN_NANOS)
            val gain = (uses - segmentAwarded).coerceAtLeast(0)
            b.expected += gain
            b.energy = (b.energy + gain * move.energy).coerceAtMost(100)
            segmentAwarded = uses
        }
        fun pause(at: Long) {
            advance(at); running = false; earliestResume = at
            partialCompletionAvailable = false
        }
        for (event in events.values.filter { it.at <= now }.sortedBy { it.at }) {
            val t = event.at
            val p = event.payload
            advance(t)
            when (p) {
                ObservationRestartedByUser -> {
                    balances.clear(); activeBySide.clear(); seenUses.clear()
                    active = null; start = null; running = false; ended = false; partial = true
                    earliestResume = t; begin(t)
                    partialCompletionAvailable = true
                    chargeLockedUntil = Long.MIN_VALUE; decisionLockedUntil = Long.MIN_VALUE
                }
                MatchStarted -> if (start == null && !ended) {
                    start = t; partial = false; running = true; begin(t)
                    partialCompletionAvailable = false
                }
                is ActivePokemonSpeciesWitnessed -> {
                    val previous = activeBySide.put(p.side, p.speciesName)
                    if (!running && previous != null && key(previous) != key(p.speciesName)) {
                        // A resumed move cannot have started while the previous
                        // combatant was still awaiting a replacement.
                        earliestResume = maxOf(earliestResume, p.appearanceStartedAtNanos ?: t)
                    }
                    if (p.side == ActivePokemonSide.OPPONENT && key(active.orEmpty()) != key(p.speciesName)) {
                        active = p.speciesName
                        // The first identity may be published late. Source-time
                        // switch/faint boundaries still prevent backfilling a later combatant.
                        begin(if (previous == null && running && balances.isEmpty()) start ?: t else t)
                        balance(p.speciesName)
                    }
                    // A replacement identity alone does not prove the selection
                    // timer ended. Wait for a new accepted hit before resuming.
                }
                AttackIncoming, GetReadyWitnessed -> {
                    pause(t)
                    chargeLockedUntil = maxOf(chargeLockedUntil, t + 12_000_000_000L)
                }
                ChargeMoveUsedAnnounced -> {
                    pause(t)
                    chargeLockedUntil = t + 750_000_000L
                    decisionLockedUntil = Long.MIN_VALUE
                }
                is BattleDecisionTimerObserved -> {
                    decisionLockedUntil = p.secondsRemaining?.let { t + (it + 1L) * 1_000_000_000L }
                        ?: Long.MIN_VALUE
                    if (p.secondsRemaining != null) pause(t)
                }
                is ActivePokemonFaintedWitnessed -> {
                    pause(t)
                    if (p.side == ActivePokemonSide.OPPONENT) {
                        val name = p.speciesName ?: active
                        name?.let { balance(it).apply { energy = 0; fainted = true } }
                    }
                }
                is ChargedMoveEnergySpent -> {
                    pause(t)
                    chargeLockedUntil = t + 750_000_000L
                    if (p.side == ActivePokemonSide.OPPONENT) {
                        val b = balance(p.speciesName)
                        b.unexplained += (p.energyCost - b.energy).coerceAtLeast(0)
                        b.energy = (b.energy - p.energyCost).coerceAtLeast(0)
                    }
                }
                is FastMoveUseObserved -> {
                    if (ended || !seenUses.add(p.useId)) continue
                    // A stray pulse from a charge animation must not restart
                    // perfect-play extrapolation while the battle view is gone.
                    if (!battlefield.contains(t)) continue
                    if (t < chargeLockedUntil || t < decisionLockedUntil) continue
                    val opponent = p.attackingSide == ActivePokemonSide.OPPONENT
                    val named = p.attackerSpeciesName
                    if (opponent && named != null && key(named) != key(active.orEmpty())) {
                        // Correctly timed named uses can recover a missing badge.
                        active = named; activeBySide[ActivePokemonSide.OPPONENT] = named; begin(t)
                    }
                    val name = active ?: continue
                    val b = balance(name)
                    if (b.fainted) continue
                    val firstPartialCompletion = start == null && opponent && partialCompletionAvailable
                    if (start == null) start = t // partial recording, no invented GO
                    if (!running) {
                        running = true
                        val cycle = if (opponent) (chosenMove(name)?.turns ?: 0) * TURN_NANOS else 0L
                        if (firstPartialCompletion) {
                            // Capture may begin after this move started. Credit
                            // this one witnessed completion; every later gain
                            // must wait a complete visible cycle from this hit.
                            begin(t, initialUses = 1)
                        } else begin(maxOf(earliestResume, chargeLockedUntil, decisionLockedUntil, t - cycle))
                    }
                    partialCompletionAvailable = false
                    if (opponent) b.observed++
                    advance(t)
                }
                is MatchEnded, ScreenIgnoredByUser, ObservationSessionStoppedByUser -> {
                    pause(t); ended = true
                }
                else -> Unit
            }
        }
        advance(now)
        val name = active ?: return null
        val move = chosenMove(name) ?: return null
        val b = balance(name)
        val identity = identities[key(name)]?.second
        return OpponentEnergyEstimate(name, move.name, b.energy, b.expected, b.observed,
            (b.activeNanos / TURN_NANOS).toInt(), !running || ended || b.fainted || !battlefield.contains(now), partial,
            b.unexplained, identity != null && !identity.basis.contains("TIE_BREAK") &&
                key(identity.moveName) == key(move.name))
    }

    private fun key(value: String) = value.uppercase().filter(Char::isLetterOrDigit)
    private companion object { const val TURN_NANOS = 500_000_000L }
}
