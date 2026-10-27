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
                             val spend: Int = 0, val faint: Boolean = false, val unavailable: Boolean = false)
    private val hasCanonicalUses = articles.any { it.payload is ArchivedFastMoveUseObserved }
    private val byId = articles.associateBy { it.articleId }
    private val cycleByEvidence = cycles.flatMap { cycle -> cycle.witness.articleIds.map { it to cycle } }.toMap()
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
        for ((cycle, candidates) in energyByCycle) {
            // Replay can resolve identity using later evidence. Select a matching
            // derivation before deduplication so an earlier wrong guess cannot
            // supply its energy gain or block the corrected derivation.
            val matching = candidates.firstOrNull { article ->
                val p = article.payload as ArchivedFastMoveEnergyDerived
                cycle.hasReferenceDuration && cycle.witness.moveName?.let { key(it) == key(p.moveName) } == true &&
                    p.energyPerUse >= 0
            }
            val source = matching ?: candidates.first()
            val use = source.predecessorIds.mapNotNull(byId::get)
                .mapNotNull { it.payload as? ArchivedFastMoveUseObserved }.firstOrNull()
            val at = if (cycle.hasReferenceDuration) cycle.impactAtNanos else cycle.witness.observedAtNanos
            val species = appearanceName(cycle.witness.side, use?.attackerSpeciesName,
                cycle.witness.observedAtNanos, combatantAt) ?: continue
            val gain = (matching?.payload as? ArchivedFastMoveEnergyDerived)?.energyPerUse
            add(Entry(at, cycle.witness.side, species, gain = gain ?: 0, unavailable = matching == null))
        }
    }.sortedBy { it.at }

    /** Null means recorded evidence cannot support a physically bounded balance. */
    fun stateAt(side: String, species: String, cursor: Long): ReplayEnergyState? {
        var energy = 0
        var generated = 0
        var spent = 0
        var fainted = false
        var unavailable = false
        for (entry in entries) {
            if (entry.at > cursor) break
            if (entry.side != side || key(entry.species) != key(species)) continue
            if (entry.faint) { energy = 0; fainted = true; unavailable = false }
            if (fainted) continue
            unavailable = unavailable || entry.unavailable
            generated += entry.gain
            spent += entry.spend
            energy = (energy + entry.gain).coerceAtMost(100)
            energy = (energy - entry.spend).coerceAtLeast(0)
        }
        return if (unavailable) null else ReplayEnergyState(energy, generated, spent)
    }
    private fun key(name: String) = name.uppercase().filter(Char::isLetterOrDigit)
}
