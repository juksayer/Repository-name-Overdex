package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*

/** Evidence-based balance on the replay's reconstructed hit clock, never a wall-clock forecast. */
data class ReplayEnergyState(val recordedEnergy: Int, val generated: Int, val spent: Int)

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
                             val spend: Int = 0, val faint: Boolean = false)
    private val hasCanonicalUses = articles.any { it.payload is ArchivedFastMoveUseObserved }
    private val byId = articles.associateBy { it.articleId }
    private val cycleByEvidence = cycles.flatMap { cycle -> cycle.witness.articleIds.map { it to cycle } }.toMap()
    private val entries = buildList {
        val creditedCycles = mutableSetOf<ReplayFastMoveCycle>()
        for (article in articles) {
            val at = article.monotonicTimeNanos ?: continue
            when (val p = article.payload) {
                is ArchivedFastMoveEnergyDerived -> {
                    if (p.basis.contains("CHARGE_MOVE_ENERGY_FILL")) continue
                    if (!hasCanonicalUses) {
                        // Legacy aggregate balances have no individual-use links.
                        // Preserve them at their recorded time; do not invent cycles.
                        val species = combatantAt(p.side, at)?.speciesName ?: continue
                        add(Entry(at, p.side, species, gain = p.totalEnergyGenerated.coerceAtLeast(0)))
                        continue
                    }
                    if (p.observedCompletedUses != 1) continue
                    val useArticle = article.predecessorIds.mapNotNull(byId::get)
                        .firstOrNull { it.payload is ArchivedFastMoveUseObserved }
                    val use = useArticle?.payload as? ArchivedFastMoveUseObserved
                    // The scheduler may merge jitter or reject a hit during a QTE,
                    // a faint, or an invalid HP reading. Energy follows that same
                    // accepted cycle exactly once. Raw energy Articles stay intact.
                    val cycle = cycleByEvidence[useArticle?.articleId ?: article.articleId] ?: continue
                    if (cycle.witness.side != p.side || !creditedCycles.add(cycle)) continue
                    val species = appearanceName(p.side, use?.attackerSpeciesName, at, combatantAt) ?: continue
                    add(Entry(if (cycle.hasReferenceDuration) cycle.impactAtNanos else cycle.witness.observedAtNanos,
                        p.side, species, gain = p.energyPerUse.coerceAtLeast(0)))
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
    }.sortedBy { it.at }

    fun stateAt(side: String, species: String, cursor: Long): ReplayEnergyState {
        var energy = 0
        var generated = 0
        var spent = 0
        var fainted = false
        for (entry in entries) {
            if (entry.at > cursor) break
            if (entry.side != side || key(entry.species) != key(species)) continue
            if (entry.faint) { energy = 0; fainted = true }
            if (fainted) continue
            generated += entry.gain
            spent += entry.spend
            energy = (energy + entry.gain).coerceAtMost(100)
            energy = (energy - entry.spend).coerceAtLeast(0)
        }
        return ReplayEnergyState(energy, generated, spent)
    }
    private fun key(name: String) = name.uppercase().filter(Char::isLetterOrDigit)
}
