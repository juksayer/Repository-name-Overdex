package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.observation.isSideLocatedSpeciesSource
import com.example.overdex.battle.reality.RealityArticle

/** Source-time HP evidence for one side; published faint conclusions are never inputs. */
internal class LiveVerifiedHpFaintHistory(private val side: ActivePokemonSide,
    private val onRebuild: ((String, Int) -> Unit)? = null,
    private val cache: FaintHistoryCache? = null) {
    private val sources = linkedMapOf<String, RealityArticle>()
    private val observations = linkedMapOf<String, VerifiedHpFaintObservation>()
    private val conflicts = mutableListOf<VerifiedHpFaintObservation>()
    private val conflictingIds = mutableSetOf<String>()
    private var start = Long.MIN_VALUE
    private var end = Long.MAX_VALUE
    private var attemptFrom = Long.MIN_VALUE
    private var matchId: String? = null
    private val emptyTimes = mutableMapOf<String, Long>()
    private val identityTimes = java.util.TreeSet<Long>()
    internal var rebuilds = 0
        private set
    var reconciliation = VerifiedHpFaintReconciliation(emptyList(), emptyMap())
        private set

    fun source(id: String): RealityArticle? = sources[id]

    /** Returns true only when the current faint/survival conclusion changes. */
    fun accept(article: RealityArticle): Boolean {
        val at = article.monotonicTimeNanos ?: return false
        val suppliedMatch = article.matchId?.value
        if (matchId != null && suppliedMatch != matchId) return false
        if (suppliedMatch != null) matchId = suppliedMatch
        if (at < attemptFrom) return false
        val id = article.id.value
        val p = article.payload
        if (p == ObservationRestartedByUser || p == ScreenIgnoredByUser) {
            attemptFrom = at
            observations.clear(); sources.clear(); conflicts.clear(); conflictingIds.clear()
            emptyTimes.clear(); identityTimes.clear()
            start = at; end = Long.MAX_VALUE
            return replace(VerifiedHpFaintReconciliation(emptyList(), emptyMap()))
        }
        if (p == MatchStarted) start = maxOf(attemptFrom, if (start == Long.MIN_VALUE) at else minOf(start, at))
        if (p is MatchEnded) end = minOf(end, at)
        val observation = when (p) {
            is ActivePokemonSpeciesWitnessed -> if (p.side != side) null
                else if (article.sourceId.id == "ENTRY_ANNOUNCEMENT_SPECIES_WITNESS")
                    VerifiedHpFaintObservation.Boundary(id, at)
                else VerifiedHpFaintObservation.Identity(id, at, p.speciesName, p.speciesId,
                    isSideLocatedSpeciesSource(article.sourceId.id) &&
                        article.confidence?.let { it.isFinite() && it in .80f..1f } == true)
            is ActiveHpBarMeasured -> if (p.side != side) null else {
                val normal = article.sourceId.id == "${side.name}_ACTIVE_HP_BAR_WITNESS"
                val charged = article.sourceId.id == "CHARGED_IMPACT_${side.name}_HP_BAR_FRAME_WITNESS"
                if (normal || charged) VerifiedHpFaintObservation.Hp(id, at, p.barLeft, p.barTop,
                    p.barRight, p.barBottom, p.filledFraction, p.fillColor, p.sourceWidth,
                    p.sourceHeight, article.confidence, normal)
                else VerifiedHpFaintObservation.Boundary(id, at, clearIdentity = false)
            }
            is SpeciesCheckMeasured -> if (p.side == side && p.status == "OPENED" && p.reason == "SWITCH")
                VerifiedHpFaintObservation.Boundary(id, p.triggerMonotonicNanos) else null
            GetReadyWitnessed, AttackIncoming, ChargeMoveUsedAnnounced ->
                VerifiedHpFaintObservation.Boundary(id, at, clearIdentity = false)
            is MatchEnded -> VerifiedHpFaintObservation.Boundary(id, at)
            else -> null
        }
        if (observation == null && p != MatchStarted) return false
        if (id in conflictingIds || sources[id] == article) return false
        if (sources.containsKey(id)) {
            conflictingIds += id
            conflicts += VerifiedHpFaintObservation.Boundary(id, requireNotNull(sources.remove(id)?.monotonicTimeNanos))
            conflicts += VerifiedHpFaintObservation.Boundary(id, at)
            observations.remove(id)
        } else {
            sources[id] = article
            if (observation != null) observations[id] = observation
        }
        if (observation is VerifiedHpFaintObservation.Identity && observation.independent) identityTimes += at
        if (observation is VerifiedHpFaintObservation.Hp && observation.fraction == 0f) emptyTimes[id] = at
        if (emptyTimes.isEmpty()) return false
        // Keep source evidence for delayed OCR, but ordinary positive frames
        // after a settled conclusion must not sort/replay the battle at 20 Hz.
        if (observation is VerifiedHpFaintObservation.Hp && observation.fraction > 0f &&
            id !in conflictingIds && emptyTimes.values.none { it >= at } &&
            emptyTimes.none { (emptyId, emptyAt) ->
                emptyId !in reconciliation.contradictedEmptyEvidence &&
                    (identityTimes.floor(at) ?: Long.MIN_VALUE) > emptyAt
            }) return false
        rebuilds++
        val input = (observations.values + conflicts).filter {
            it.at <= end && (it !is VerifiedHpFaintObservation.Hp || it.at >= start)
        }
        val compute = {
            onRebuild?.invoke("verified_hp_${side.name}", observations.size + conflicts.size)
            VerifiedHpFaintResolver.reconcile(input)
        }
        return replace(cache?.hp(side, input, compute) ?: compute())
    }

    private fun replace(next: VerifiedHpFaintReconciliation): Boolean {
        if (next == reconciliation) return false
        reconciliation = next
        return true
    }
}
