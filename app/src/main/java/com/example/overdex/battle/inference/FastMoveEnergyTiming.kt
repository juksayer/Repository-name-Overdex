package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.AttackIncoming
import com.example.overdex.battle.custody.BattleDecisionTimerObserved
import com.example.overdex.battle.custody.ChargeMoveUsedAnnounced
import com.example.overdex.battle.custody.GetReadyWitnessed
import com.example.overdex.battle.custody.MatchEnded
import com.example.overdex.battle.custody.MatchStarted
import com.example.overdex.battle.custody.TestimonyPayload
import com.example.overdex.battle.reality.RealityArticle
import java.util.EnumMap
import java.util.TreeSet
import java.util.TreeMap

/**
 * A physical ceiling on derived energy, not another attack witness. Each credit
 * reserves a full reference cycle ending no later than its observed source time.
 * A small past-only completion window accommodates capture delay; rebuilding the
 * schedule prevents that allowance from accumulating into extra cycles.
 */
internal class FastMoveEnergyTiming {
    private data class Pause(val start: Long, var end: Long)
    private data class Reservation(
        val observedAt: Long,
        val duration: Long,
        val earliestStart: Long,
        // Evidence arriving later cannot rewrite already-emitted energy. Keep
        // the constraints known when a reservation was accepted, while every
        // new credit must satisfy all boundaries currently known for its time.
        val pauses: List<Pair<Long, Long>>,
    )

    private val reservations = EnumMap<ActivePokemonSide, MutableList<Reservation>>(ActivePokemonSide::class.java)
    private val appearanceBoundaries = EnumMap<ActivePokemonSide, TreeSet<Long>>(ActivePokemonSide::class.java)
    private val faintTimes = EnumMap<ActivePokemonSide, TreeSet<Long>>(ActivePokemonSide::class.java)
    private val chargedPauses = mutableListOf<Pause>()
    private val decisionPauses = mutableListOf<Pause>()
    private val phaseCues = TreeMap<Long, MutableList<TestimonyPayload>>()
    private var matchStart: Long? = null
    private var matchEnd: Long? = null

    fun observe(article: RealityArticle) {
        val at = article.monotonicTimeNanos ?: return
        when (val payload = article.payload) {
            MatchStarted -> matchStart = minOf(matchStart ?: at, at)
            is MatchEnded -> matchEnd = minOf(matchEnd ?: at, at)
            AttackIncoming, GetReadyWitnessed, ChargeMoveUsedAnnounced, is BattleDecisionTimerObserved -> {
                val cues = phaseCues.getOrPut(at) { mutableListOf() }
                if (payload !in cues) cues += payload
                rebuildPauses()
            }
            else -> Unit
        }
    }

    private fun rebuildPauses() {
        // An end cue can finish OCR before its start cue. Rebuild in source
        // order so that late delivery cannot extend an already completed QTE.
        chargedPauses.clear()
        decisionPauses.clear()
        for ((at, cues) in phaseCues) for (payload in cues) when (payload) {
            AttackIncoming, GetReadyWitnessed -> {
                if (chargedPauses.none { at >= it.start && at < it.end })
                    chargedPauses += Pause(at, add(at, CHARGED_MAX_PHASE_NANOS))
            }
            ChargeMoveUsedAnnounced -> {
                decisionPauses.lastOrNull { at >= it.start && at < it.end }?.end = at
                val phase = chargedPauses.lastOrNull { at >= it.start && at < it.end }
                    ?: Pause(at, at).also { chargedPauses += it }
                phase.end = add(at, CHARGED_IMPACT_TAIL_NANOS)
            }
            is BattleDecisionTimerObserved -> {
                val active = decisionPauses.lastOrNull { at >= it.start && at < it.end }
                val remaining = payload.secondsRemaining
                if (remaining == null) active?.end = at
                else if (remaining in 0..15) {
                    val end = add(at, (remaining + 1L) * 1_000_000_000L)
                    if (active == null) decisionPauses += Pause(at, end) else active.end = end
                }
            }
            else -> Unit
        }
    }

    /** A genuine appearance transition is a barrier; a request to scan is not. */
    fun appearanceStarted(side: ActivePokemonSide, at: Long) {
        appearanceBoundaries.getOrPut(side) { TreeSet() }.add(at)
    }

    fun fainted(side: ActivePokemonSide, at: Long) {
        faintTimes.getOrPut(side) { TreeSet() }.add(at)
    }

    fun reserve(side: ActivePokemonSide, observedAt: Long, duration: Long): Boolean {
        if (duration <= 0 || matchEnd?.let { observedAt >= it } == true) return false
        val lag = minOf(duration / 4, MAX_CAPTURE_LAG_NANOS)
        var earliestStart = subtract(subtract(observedAt, lag), duration)
        // Without GO or an appearance boundary, one in-flight completion may
        // precede the start of observation. Its full interval still excludes
        // overlapping credits; elapsed idle time never becomes a credit bank.
        matchStart?.let { earliestStart = maxOf(earliestStart, it) }
        appearanceBoundaries[side]?.floor(observedAt)?.let { earliestStart = maxOf(earliestStart, it) }
        // Neither side can generate Fast Move energy while a fainted opponent
        // has not yet been replaced. An unreadable entry keeps this unavailable.
        val faintPauses = faintTimes.flatMap { (faintedSide, times) ->
            times.map { it to (appearanceBoundaries[faintedSide]?.higher(it) ?: Long.MAX_VALUE) }
        }
        val pauses = ((chargedPauses + decisionPauses).map { it.start to it.end } + faintPauses)
            .sortedBy { it.first }
        // A delayed capture is not permission to reinterpret an observation
        // made during a known pause as an earlier completed attack.
        if ((chargedPauses + decisionPauses).any { observedAt >= it.start && observedAt < it.end } ||
            faintPauses.any { (start, end) -> observedAt > start && observedAt < end }) return false
        val candidate = Reservation(observedAt, duration, earliestStart, pauses)
        val accepted = reservations.getOrPut(side) { mutableListOf() }
        val ordered = (accepted + candidate).sortedBy { it.observedAt }
        var previousEnd: Long? = null
        for (use in ordered) {
            var start = maxOf(use.earliestStart, previousEnd ?: Long.MIN_VALUE)
            val latestStart = subtract(use.observedAt, use.duration)
            for ((pauseStart, pauseEnd) in use.pauses) {
                if (start >= pauseEnd) continue
                if (add(start, use.duration) <= pauseStart) break
                start = pauseEnd
            }
            if (start > latestStart) return false
            previousEnd = add(start, use.duration)
        }
        accepted += candidate
        return true
    }

    private fun add(value: Long, delta: Long): Long =
        if (value > Long.MAX_VALUE - delta) Long.MAX_VALUE else value + delta

    private fun subtract(value: Long, delta: Long): Long =
        if (value < Long.MIN_VALUE + delta) Long.MIN_VALUE else value - delta

    private companion object {
        const val MAX_CAPTURE_LAG_NANOS = 250_000_000L
        // Match the existing observed-use charged-phase suppression window.
        const val CHARGED_MAX_PHASE_NANOS = 12_000_000_000L
        const val CHARGED_IMPACT_TAIL_NANOS = 750_000_000L
    }
}
