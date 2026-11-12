package com.example.overdex.battle.inference

import java.util.Locale

/** Independent, already side-attributed inputs. Derived faint events never enter this resolver. */
sealed interface TeamLossObservation {
    val articleId: String
    val atNanos: Long

    data class Identity(
        override val articleId: String,
        override val atNanos: Long,
        val speciesName: String,
        val speciesId: Int? = null,
    ) : TeamLossObservation

    data class Count(
        override val articleId: String,
        override val atNanos: Long,
        val count: Int,
    ) : TeamLossObservation

    /** A valid independent positive-HP measurement, not an inferred attack or replay HP. */
    data class Alive(override val articleId: String, override val atNanos: Long) : TeamLossObservation
}

data class TeamLossCandidate(
    val appearanceArticleId: String,
    val appearanceAtNanos: Long,
    val speciesName: String,
    val speciesId: Int? = null,
)

data class TeamLossResolution(
    val lossId: String,
    val sourceAtNanos: Long,
    val beforeCount: Int,
    val afterCount: Int,
    val candidates: List<TeamLossCandidate>,
    val resolved: TeamLossCandidate?,
    val evidenceReferences: List<String>,
    /** Certain owners of this one count interval; list order does not establish faint order. */
    val resolvedCandidates: List<TeamLossCandidate> = listOfNotNull(resolved),
)

/** Rebuildable source-time projection. A resource loss is certain before its combatant is known. */
object TeamLossResolver {
    fun speciesKey(name: String): String = name.replace(Regex("\\s*\\([^)]*\\)\\s*$"), "")
        .uppercase(Locale.ROOT).filter(Char::isLetterOrDigit)

    private data class Appearance(
        val at: Long,
        var until: Long = Long.MAX_VALUE,
        val key: String?,
        val identities: MutableList<TeamLossObservation.Identity>,
    ) {
        fun candidate(at: Long): TeamLossCandidate {
            val known = identities.filter { it.atNanos <= at }
            val first = known.first()
            // A later form refinement may describe the same actor, but not before it was read.
            val name = known.maxBy { it.speciesName.length }.speciesName
            return TeamLossCandidate(first.articleId, first.atNanos, name,
                known.lastOrNull { it.speciesId != null }?.speciesId)
        }
    }

    private data class AliveProof(val at: Long, val articleId: String, val identityIds: List<String>)
    private data class Loss(
        val before: TeamLossObservation.Count,
        val after: TeamLossObservation.Count,
        val candidates: List<TeamLossCandidate>,
        val incomplete: Boolean,
        val contradicted: Boolean,
        val evidence: MutableSet<String>,
        var resolvedCandidates: List<TeamLossCandidate> = emptyList(),
    )

    fun resolve(observations: List<TeamLossObservation>): List<TeamLossResolution> {
        // Duplicate deliveries have one vote. An ID with contradictory contents has none.
        val sourceGroups = observations.groupBy { it.articleId }
        val invalidGroups = sourceGroups.filter { (id, copies) -> id.isBlank() || copies.distinct().size > 1 }
        val invalid = invalidGroups.values.flatten()
        val clean = sourceGroups.filterKeys { it !in invalidGroups }
            .values.map { it.first() }
            .sortedWith(compareBy<TeamLossObservation> { it.atNanos }.thenBy { it.articleId })
        // Discarding an unreadable identity must not silently extend the previous actor's HP ownership.
        val identityBarriers = invalid.filterIsInstance<TeamLossObservation.Identity>()
            .map { it.copy(speciesName = "", speciesId = null) }
        val identities = clean.filterIsInstance<TeamLossObservation.Identity>()
        val namedIds = identities.filter { it.speciesId != null && speciesKey(it.speciesName).isNotBlank() }
        val contradictoryNames = namedIds.groupBy { speciesKey(it.speciesName) }
            .filterValues { group -> group.map { it.speciesId }.distinct().size > 1 }.keys
        val contradictoryDex = namedIds.groupBy { it.speciesId }
            .filterValues { group -> group.map { speciesKey(it.speciesName) }.distinct().size > 1 }.keys
        val identityConflictEvidence = mutableMapOf<String, List<String>>()
        val consistentIdentities = identities.map { identity ->
            val key = speciesKey(identity.speciesName)
            if (key in contradictoryNames || identity.speciesId in contradictoryDex) {
                identityConflictEvidence[identity.articleId] = namedIds.filter {
                    (key in contradictoryNames && speciesKey(it.speciesName) == key) ||
                        (identity.speciesId in contradictoryDex && it.speciesId == identity.speciesId)
                }.map { it.articleId }
                identity.copy(speciesName = "", speciesId = null)
            } else identity
        }
        val appearances = appearances((consistentIdentities + identityBarriers)
            .sortedWith(compareBy<TeamLossObservation.Identity> { it.atNanos }.thenBy { it.articleId }))
        val alive = mutableMapOf<String, MutableList<AliveProof>>()
        var appearanceIndex = -1
        clean.filterIsInstance<TeamLossObservation.Alive>().forEach { proof ->
            while (appearanceIndex + 1 < appearances.size && appearances[appearanceIndex + 1].at <= proof.atNanos)
                appearanceIndex++
            val appearance = appearances.getOrNull(appearanceIndex) ?: return@forEach
            val key = appearance.key ?: return@forEach
            val identityIds = appearance.identities.filter { it.atNanos <= proof.atNanos }.map { it.articleId }
            alive.getOrPut(key) { mutableListOf() } += AliveProof(proof.atNanos, proof.articleId, identityIds)
        }

        val countGroups = clean.filterIsInstance<TeamLossObservation.Count>()
            .filter { it.count in 0..3 }.groupBy { it.atNanos }
        val conflictingCountTimes = countGroups.filterValues { group -> group.map { it.count }.distinct().size > 1 }.keys +
            invalid.filterIsInstance<TeamLossObservation.Count>().map { it.atNanos }
        val counts = countGroups.filterKeys { it !in conflictingCountTimes }.values.map { it.first() }
        val losses = mutableListOf<Loss>()
        var baseline: TeamLossObservation.Count? = null
        for (count in counts) {
            val before = baseline
            if (before == null || count.count == before.count) { baseline = count; continue }
            if (count.count > before.count) continue // A noisy increase cannot refill the team or create another loss.
            baseline = count
            val interval = appearances.filter { it.at <= count.atNanos && it.until > before.atNanos }
            val evidence = linkedSetOf(before.articleId, count.articleId)
            interval.forEach { appearance ->
                appearance.identities.filter { it.atNanos <= count.atNanos && it.articleId.isNotBlank() }
                    .forEach { identity ->
                        evidence += identity.articleId
                        evidence += identityConflictEvidence[identity.articleId].orEmpty()
                    }
            }
            val candidates = interval.filter { it.key != null }.groupBy { it.key!! }
                .mapNotNull { (key, visits) ->
                    // Identity alone may linger after a KO. Positive HP after the loss is independent survival proof.
                    val proof = alive[key]?.firstOrNull { it.at > count.atNanos }
                    if (proof != null) {
                        evidence += proof.articleId
                        evidence += proof.identityIds
                        null
                    } else visits.last().candidate(count.atNanos)
                }.sortedWith(compareBy<TeamLossCandidate> { it.appearanceAtNanos }.thenBy { it.appearanceArticleId })
            val survivingAfterZero = if (count.count == 0) clean.filterIsInstance<TeamLossObservation.Alive>()
                .firstOrNull { it.atNanos > count.atNanos } else null
            // A side cannot have zero survivors and then show positive HP. Do not assign that contradiction
            // to an earlier departed actor just because the replacement is independently still alive.
            if (survivingAfterZero != null) {
                evidence += survivingAfterZero.articleId
                alive.values.flatten().firstOrNull { it.articleId == survivingAfterZero.articleId }
                    ?.let { evidence += it.identityIds }
            }
            // A first identity after the count leaves an earlier actor unknown. Seeing all three
            // distinct team members by this loss exhausts that uncertainty without backdating an identity.
            val initialGap = before.atNanos < (appearances.firstOrNull()?.at ?: Long.MAX_VALUE)
            val knownTeamSize = appearances.filter { it.at <= count.atNanos }.mapNotNull { it.key }.distinct().size
            losses += Loss(before, count, candidates,
                (initialGap && knownTeamSize != 3) || interval.any { it.key == null } ||
                    conflictingCountTimes.any { it > before.atNanos && it <= count.atNanos },
                survivingAfterZero != null,
                evidence)
        }
        resolveUniqueAssignments(losses)
        return losses.map { loss -> TeamLossResolution(
            loss.after.articleId, loss.after.atNanos, loss.before.count, loss.after.count,
            loss.candidates,
            loss.resolvedCandidates.singleOrNull().takeIf { loss.before.count - loss.after.count == 1 },
            loss.evidence.sorted(), loss.resolvedCandidates,
        ) }
    }

    private fun appearances(identities: List<TeamLossObservation.Identity>): List<Appearance> {
        val result = mutableListOf<Appearance>()
        for ((at, group) in identities.groupBy { it.atNanos }) {
            val keys = group.map { speciesKey(it.speciesName) }.distinct()
            val dex = group.mapNotNull { it.speciesId }.distinct()
            val key = keys.singleOrNull()?.takeIf { it.isNotBlank() && dex.size <= 1 }
            val prior = result.lastOrNull()
            val previousDex = prior?.identities?.mapNotNull { it.speciesId }?.distinct().orEmpty()
            val compatible = key != null && key == prior?.key &&
                (previousDex + dex).distinct().size <= 1
            if (compatible) prior!!.identities += group else {
                prior?.until = at
                // Contradictory same-species IDs create an unknown interval, not a different actor by assumption.
                val conflictingId = key != null && key == prior?.key && (previousDex + dex).distinct().size > 1
                result += Appearance(at, key = key.takeUnless { conflictingId }, identities = group.toMutableList())
            }
        }
        return result
    }

    /**
     * A three-member team has at most three losses, even when intermediate counts were missed.
     * Assign unordered owner sets of the observed cardinality, then retain only owners present in
     * every consistent assignment. Never split a skipped count into invented events or an order.
     */
    private fun resolveUniqueAssignments(losses: List<Loss>) {
        // Impossible or contradicted losses must remain constraints. Dropping them could let a
        // connected loss claim an actor despite no joint explanation of the observed counts.
        val pending = losses.toMutableList()
        while (pending.isNotEmpty()) {
            val component = mutableListOf(pending.removeAt(0))
            val keys = component.first().candidates.mapTo(mutableSetOf()) { speciesKey(it.speciesName) }
            do {
                val connected = pending.filter { loss -> loss.candidates.any { speciesKey(it.speciesName) in keys } }
                pending.removeAll(connected.toSet())
                component += connected
                connected.flatMap { it.candidates }.forEach { keys += speciesKey(it.speciesName) }
            } while (connected.isNotEmpty())
            val guaranteed = component.map { mutableSetOf<String>() }
            val chosen = arrayOfNulls<Set<String>>(component.size)
            var completions = 0
            fun visit(index: Int, used: Set<String>) {
                if (index == component.size) {
                    chosen.forEachIndexed { i, keys ->
                        if (completions == 0) guaranteed[i] += requireNotNull(keys)
                        else guaranteed[i].retainAll(requireNotNull(keys))
                    }
                    completions++
                    return
                }
                val loss = component[index]
                if (loss.contradicted) return
                val delta = loss.before.count - loss.after.count
                val keys = loss.candidates.map { speciesKey(it.speciesName) }.filter { it !in used }
                // Each omitted owner occupies a distinct unknown slot in this interval. Unknown
                // slots may fill only an explicitly incomplete history; they never become names.
                val knownSizes = if (loss.incomplete) 0..minOf(delta, keys.size) else delta..delta
                for (size in knownSizes) for (owners in ownerSets(keys, size)) {
                    chosen[index] = owners
                    visit(index + 1, used + owners)
                }
            }
            visit(0, emptySet())
            if (completions == 0) continue // Conflicting losses cannot make one actor faint twice.
            val proof = component.flatMap { it.evidence }.toSet()
            component.forEachIndexed { index, loss ->
                loss.resolvedCandidates = loss.candidates.filter { speciesKey(it.speciesName) in guaranteed[index] }
                if (loss.resolvedCandidates.isNotEmpty()) loss.evidence += proof
            }
        }
    }

    private fun ownerSets(keys: List<String>, size: Int): List<Set<String>> {
        if (size > keys.size) return emptyList()
        val result = mutableListOf<Set<String>>()
        val chosen = linkedSetOf<String>()
        fun choose(start: Int) {
            if (chosen.size == size) {
                result += chosen.toSet()
                return
            }
            for (index in start..keys.size - (size - chosen.size)) {
                chosen += keys[index]
                choose(index + 1)
                chosen -= keys[index]
            }
        }
        choose(0)
        return result
    }
}
