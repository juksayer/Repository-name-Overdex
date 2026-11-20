package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*

/** Balance at the supported action clock. [recordedEnergy] is the lower endpoint when the estimate is a range. */
data class ReplayEnergyState(
    val recordedEnergy: Int,
    val generated: Int,
    val spent: Int,
    val maximumEnergy: Int = recordedEnergy,
) {
    val isRange: Boolean get() = maximumEnergy != recordedEnergy
    val balanceLabel: String get() = if (isRange) "$recordedEnergy–$maximumEnergy" else "$recordedEnergy"
}

internal class ReplayEnergyTrack(
    articles: List<ArchivedRealityArticle>,
    combatantAt: (String, Long) -> ReplayCombatant?,
    cycles: List<ReplayFastMoveCycle>,
) {
    private fun appearanceName(side: String, name: String?, at: Long, combatantAt: (String, Long) -> ReplayCombatant?): String? {
        val active = combatantAt(side, at)?.speciesName ?: return name
        if (name == null) return active
        return if (com.example.overdex.battle.observation.TeamRosterSpeciesAttributor.speciesKey(name) ==
            com.example.overdex.battle.observation.TeamRosterSpeciesAttributor.speciesKey(active) && '(' !in name) active else name
    }
    private data class Entry(val at: Long, val side: String, val species: String, val gain: Int = 0,
                             val spend: Int = 0, val faint: Boolean = false, val unavailable: Boolean = false)
    private val hasCanonicalUses = articles.any { it.payload is ArchivedFastMoveUseObserved }
    private val byId = articles.associateBy { it.articleId }
    private val supportedCycles = cycles.filter {
        it.witness.articleIds.isNotEmpty() && ReplayFastMoveRhythm.INFERRED !in it.witness.evidenceKinds
    }
    private val cycleByEvidence = supportedCycles.flatMap { cycle -> cycle.witness.articleIds.map { it to cycle } }.toMap()
    private val entries = buildList {
        val energyByCycle = linkedMapOf<ReplayFastMoveCycle, MutableList<ArchivedRealityArticle>>()
        for (article in articles) {
            val at = article.monotonicTimeNanos ?: continue
            when (val p = article.payload) {
                is ArchivedFastMoveEnergyDerived -> {
                    if (p.basis.contains("CHARGE_MOVE_ENERGY_FILL")) continue
                    if (!hasCanonicalUses || p.observedCompletedUses != 1) {
                        // Preserve aggregate testimony, but it cannot establish a
                        // balance bounded by individual full-duration move cycles.
                        if (p.totalEnergyGenerated <= 0 && p.observedCompletedUses <= 0) continue
                        val species = combatantAt(p.side, at)?.speciesName ?: continue
                        add(Entry(at, p.side, species, unavailable = true))
                        continue
                    }
                    val useArticle = article.predecessorIds.mapNotNull(byId::get)
                        .firstOrNull { it.payload is ArchivedFastMoveUseObserved }
                    // A rejected or merged observation cannot secretly generate
                    // energy outside the accepted animation/HP schedule.
                    val cycle = cycleByEvidence[useArticle?.articleId ?: article.articleId] ?: continue
                    if (cycle.witness.side != p.side) continue
                    energyByCycle.getOrPut(cycle) { mutableListOf() }.add(article)
                }
                is ArchivedChargedMoveEnergySpent -> add(Entry(at, p.side,
                    appearanceName(p.side, p.speciesName, at, combatantAt) ?: p.speciesName, spend = p.energyCost.coerceAtLeast(0)))
                is ArchivedActivePokemonFaintedWitnessed -> {
                    val species = appearanceName(p.side, p.speciesName, at, combatantAt) ?: continue
                    add(Entry(at, p.side, species, faint = true))
                }
                else -> Unit
            }
        }
        for (cycle in supportedCycles) {
            val candidates = energyByCycle[cycle].orEmpty()
            // Replay can resolve identity using later evidence. Select a matching
            // derivation before deduplication so an earlier wrong guess cannot
            // supply its energy gain or block the corrected derivation.
            val matching = candidates.firstOrNull { article ->
                val p = article.payload as ArchivedFastMoveEnergyDerived
                cycle.hasReferenceDuration && cycle.witness.moveName?.let { key(it) == key(p.moveName) } == true &&
                    p.energyPerUse in 0..100
            }
            val source = matching ?: candidates.firstOrNull()
            val use = (source?.predecessorIds.orEmpty() + cycle.witness.articleIds).asSequence()
                .mapNotNull { byId[it]?.payload as? ArchivedFastMoveUseObserved }.firstOrNull()
            val at = if (cycle.hasReferenceDuration) cycle.impactAtNanos else cycle.witness.observedAtNanos
            val species = appearanceName(cycle.witness.side, use?.attackerSpeciesName,
                cycle.witness.observedAtNanos, combatantAt) ?: continue
            val gain = (matching?.payload as? ArchivedFastMoveEnergyDerived)?.energyPerUse
                ?: cycle.witness.supportedReferenceGain?.takeIf { cycle.hasReferenceDuration }
            add(Entry(at, cycle.witness.side, species, gain = gain ?: 0, unavailable = gain == null))
        }
    }.sortedBy { it.at }

    /** Null means the evidence cannot narrow the balance beyond 0–100. */
    fun stateAt(side: String, species: String, cursor: Long): ReplayEnergyState? {
        var minimum = 0
        var maximum = 0
        var generated = 0
        var spent = 0
        val speciesKey = key(species)
        for (entry in entries) {
            if (entry.at > cursor) break
            if (entry.side != side || key(entry.species) != speciesKey) continue
            if (entry.faint) return ReplayEnergyState(0, generated, spent)
            generated += entry.gain
            spent += entry.spend
            minimum = (minimum + entry.gain).coerceAtMost(100)
            maximum = if (entry.unavailable) 100 else (maximum + entry.gain).coerceAtMost(100)
            if (entry.spend > maximum) {
                // A witnessed charge proves the recorded gains were incomplete.
                // Before spending, the true balance could have been anywhere from cost to cap.
                minimum = 0
                maximum = 100
            }
            minimum = (minimum - entry.spend).coerceAtLeast(0)
            maximum = (maximum - entry.spend).coerceAtLeast(0)
        }
        return if (minimum == 0 && maximum == 100) null else ReplayEnergyState(minimum, generated, spent, maximum)
    }
    private fun key(name: String) = name.uppercase().filter(Char::isLetterOrDigit)
}
