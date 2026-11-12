package com.example.overdex.battle.forecast

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.inference.FastMoveUseEvidence
import com.example.overdex.battle.inference.LiveVerifiedHpFaintHistory
import com.example.overdex.battle.inference.TeamLossResolver
import com.example.overdex.battle.inference.VerifiedHpFaintResolver
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
class OpponentEnergyForecast(private val onRebuild: ((String, Int) -> Unit)? = null,
    private val faintHistoryCache: com.example.overdex.battle.inference.FaintHistoryCache? = null) {
    private data class Event(val at: Long, val payload: TestimonyPayload)
    private val eventIds = mutableSetOf<String>()
    // Stable source-time order is maintained only when semantic evidence arrives.
    private val events = mutableListOf<Event>()
    private val moves = mutableMapOf<String, List<Move>>()
    private val candidates = mutableMapOf<String, Set<String>>()
    private val identities = mutableMapOf<String, Pair<Long, FastMoveIdentified>>()
    private val hpHistories = ActivePokemonSide.entries.associateWith { LiveVerifiedHpFaintHistory(it, onRebuild, faintHistoryCache) }.toMutableMap()
    private val terminalHistories = ActivePokemonSide.entries.associateWith {
        com.example.overdex.battle.inference.LiveTerminalChargedFaintHistory(it, onRebuild, faintHistoryCache) }.toMutableMap()
    private val battlefield = ForecastBattlefieldVisibility()
    private val selectedMoves = mutableMapOf<String, Move>()
    private var projection: Projection? = null
    private var projectionBuilds = 0L
    private var eventsApplied = 0L

    /** Deterministic work accounting for tests; no per-tick history logging. */
    internal data class ProjectionWork(val builds: Long, val eventsApplied: Long)
    @Synchronized internal fun projectionWork() = ProjectionWork(projectionBuilds, eventsApplied)

    /** Called on the live frame path, before queued OCR/custody delivery. */
    @Synchronized fun observeBattlefield(at: Long, bothBarsVisible: Boolean) {
        val changedFrom = battlefield.observe(at, bothBarsVisible) ?: return
        // Normal live frames extend visibility after the last processed event.
        // Do not replay history for those frames. A recovered/confirmed interval
        // can reach a processed hit or pause and needs a fresh projection.
        if (projection?.let { changedFrom <= it.lastEventAt } == true) projection = null
    }

    @Synchronized fun registerMoves(species: String, available: List<Move>) {
        val id = key(species)
        val eligible = available.filter { it.isFast && (it.turns ?: 0) > 0 && it.energy > 0 }
        if (moves[id] == eligible) return
        moves[id] = eligible
        updateSelectedMove(id)
    }

    @Synchronized fun restrictCandidates(species: String, names: List<String>) {
        if (names.isEmpty()) return
        val id = key(species)
        val allowed = names.map(::key).toSet()
        if (candidates[id] == allowed) return
        candidates[id] = allowed
        updateSelectedMove(id)
    }

    @Synchronized fun observe(article: RealityArticle) {
        val at = article.monotonicTimeNanos ?: return
        val p = article.payload
        hpHistories.values.forEach { history ->
            val before = history.reconciliation.faints
            history.accept(article)
            if (before != history.reconciliation.faints) projection = null
        }
        terminalHistories.values.forEach { history -> if (history.accept(article)) projection = null }
        if (p is FastMoveIdentified && p.side == ActivePokemonSide.OPPONENT) {
            if (at >= (identities[key(p.speciesName)]?.first ?: Long.MIN_VALUE)) {
                val id = key(p.speciesName)
                val previous = identities.put(id, at to p)?.second
                updateSelectedMove(id)
                // The identity flag is presentation state even when the move's
                // energy/duration did not change (for example a resolved tie).
                if (previous != p) projection = null
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
        if (!eventIds.add(article.id.value)) return
        val index = eventLimit(at)
        events.add(index, Event(at, p))
        // Appended evidence extends the checkpoint. A late event must replay in
        // source order, including the original insertion order for equal times.
        if (projection?.let { index < it.processedEvents } == true) projection = null
    }

    @Synchronized fun reset() {
        events.clear(); eventIds.clear(); moves.clear(); candidates.clear(); identities.clear()
        selectedMoves.clear(); projection = null
        projectionBuilds = 0L; eventsApplied = 0L
        battlefield.clear()
        ActivePokemonSide.entries.forEach {
            hpHistories[it] = LiveVerifiedHpFaintHistory(it, onRebuild, faintHistoryCache)
            terminalHistories[it] = com.example.overdex.battle.inference.LiveTerminalChargedFaintHistory(it, onRebuild, faintHistoryCache)
        }
    }

    private fun chosenMove(species: String): Move? = selectedMoves[key(species)]

    private fun updateSelectedMove(id: String) {
        val chosen = selectMove(id)
        val previous = if (chosen == null) selectedMoves.remove(id) else selectedMoves.put(id, chosen)
        if (previous != chosen) projection = null
    }

    private fun selectMove(id: String): Move? {
        val pool = moves[id].orEmpty()
        val identity = identities[id]?.second
        val eligible = candidates[id]?.let { allowed -> pool.filter { key(it.name) in allowed } }
            ?.takeIf { it.isNotEmpty() } ?: pool
        // A tie-break remains a hypothesis. Other compatible identity evidence
        // can select the actual move, including one with less energy per turn.
        if (identity != null && !identity.basis.contains("TIE_BREAK")) {
            eligible.firstOrNull { key(it.name) == key(identity.moveName) }?.let { return it }
        }
        return eligible.minWithOrNull(compareByDescending<Move> { it.energy.toDouble() / it.turns!! }
            .thenBy { it.name })
    }

    /** Number of events at or before this source time (an upper bound). */
    private fun eventLimit(at: Long): Int {
        var low = 0
        var high = events.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (events[middle].at <= at) low = middle + 1 else high = middle
        }
        return low
    }

    @Synchronized fun estimate(now: Long): OpponentEnergyEstimate? {
        val limit = eventLimit(now)
        val state = projection?.takeIf { it.processedEvents <= limit }
            ?: Projection().also { projection = it; projectionBuilds++ }
        while (state.processedEvents < limit) {
            val event = events[state.processedEvents]
            state.accept(event)
            state.processedEvents++
            state.lastEventAt = event.at
            eventsApplied++
        }
        return state.estimate(now)
    }

    private data class Balance(var energy: Int = 0, var expected: Int = 0, var observed: Int = 0,
                               var activeNanos: Long = 0, var unexplained: Int = 0, var fainted: Boolean = false)

    /**
     * Evidence-derived state stops at the last semantic event, never at a HUD
     * tick. Tick estimates only advance the current balance in local scalars;
     * they cannot award energy into the checkpoint or affect a later spend.
     */
    private inner class Projection {
        var processedEvents = 0
        var lastEventAt = Long.MIN_VALUE
        val balances = mutableMapOf<String, Balance>()
        val seenUses = mutableSetOf<String>()
        val seenFaints = mutableSetOf<Pair<ActivePokemonSide, String>>()
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
        private fun balance(name: String) = balances.getOrPut(key(name)) { Balance() }
        private fun begin(at: Long, initialUses: Int = 0) {
            segmentAt = at; segmentAwarded = 0; segmentInitialUses = initialUses; segmentMeasuredNanos = 0
        }
        private fun advance(at: Long) {
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
        private fun pause(at: Long) {
            advance(at); running = false; earliestResume = at
            partialCompletionAvailable = false
        }
        fun accept(event: Event) {
            val t = event.at
            val p = event.payload
            if (p is ActivePokemonFaintedWitnessed && com.example.overdex.battle.inference.isRevisableVisualFaint(p.basis) &&
                (hpHistories.getValue(p.side).reconciliation.faints.map { it.atNanos to it.owner.speciesName } +
                    terminalHistories.getValue(p.side).resolutions.map { it.atNanos to it.owner.speciesName }).none {
                    it.first == t && p.speciesName?.let(TeamLossResolver::speciesKey) == TeamLossResolver.speciesKey(it.second)
                }) return
            advance(t)
            when (p) {
                ObservationRestartedByUser -> {
                    balances.clear(); activeBySide.clear(); seenUses.clear(); seenFaints.clear()
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
                    val name = p.speciesName ?: activeBySide[p.side]
                    if (name != null && !seenFaints.add(p.side to key(name))) return
                    // A later count may corroborate an HP KO after a replacement
                    // is active. It cannot pause that replacement a second time.
                    if (name == null || activeBySide[p.side]?.let { key(it) != key(name) } != true) pause(t)
                    if (p.side == ActivePokemonSide.OPPONENT) {
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
                    if (ended || !seenUses.add(p.useId)) return
                    // A stray pulse from a charge animation must not restart
                    // perfect-play extrapolation while the battle view is gone.
                    if (!battlefield.contains(t)) return
                    if (t < chargeLockedUntil || t < decisionLockedUntil) return
                    val opponent = p.attackingSide == ActivePokemonSide.OPPONENT
                    val named = p.attackerSpeciesName
                    if (opponent && named != null && key(named) != key(active.orEmpty())) {
                        // Correctly timed named uses can recover a missing badge.
                        active = named; activeBySide[ActivePokemonSide.OPPONENT] = named; begin(t)
                    }
                    val name = active ?: return
                    val b = balance(name)
                    if (b.fainted) return
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
        fun estimate(now: Long): OpponentEnergyEstimate? {
            val name = active ?: return null
            val move = chosenMove(name) ?: return null
            val b = balance(name)
            var energy = b.energy
            var expected = b.expected
            var activeNanos = b.activeNanos
            if (running && !ended && !b.fainted) {
                val elapsed = battlefield.elapsed(segmentAt, now)
                activeNanos += elapsed - segmentMeasuredNanos
                val uses = segmentInitialUses + battlefield.completedCycles(segmentAt, now, move.turns!! * TURN_NANOS)
                val gain = (uses - segmentAwarded).coerceAtLeast(0)
                expected += gain
                energy = (energy + gain * move.energy).coerceAtMost(100)
            }
            val identity = identities[key(name)]?.second
            return OpponentEnergyEstimate(name, move.name, energy, expected, b.observed,
                (activeNanos / TURN_NANOS).toInt(), !running || ended || b.fainted || !battlefield.contains(now), partial,
                b.unexplained, identity != null && !identity.basis.contains("TIE_BREAK") &&
                    key(identity.moveName) == key(move.name))
        }
    }

    private fun key(value: String) = value.uppercase().filter(Char::isLetterOrDigit)
    private companion object { const val TURN_NANOS = 500_000_000L }
}
