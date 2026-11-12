package com.example.overdex.battle.inference

import com.example.overdex.battle.observation.ActiveHpBarGeometry
import com.example.overdex.battle.observation.TerminalHpWatchPolicy
import kotlin.math.abs
import kotlin.math.max

/** Source-time inputs for one side. Conclusions, cries and inferred damage are not inputs. */
internal sealed interface VerifiedHpFaintObservation {
    val id: String
    val at: Long

    data class Identity(override val id: String, override val at: Long, val name: String,
                        val speciesId: Int?, val independent: Boolean) : VerifiedHpFaintObservation
    data class Hp(override val id: String, override val at: Long, val left: Int, val top: Int,
                  val right: Int, val bottom: Int, val fraction: Float, val color: String?,
                  val width: Int?, val height: Int?, val confidence: Float?,
                  val trackedBattlefieldSource: Boolean) : VerifiedHpFaintObservation {
        val barWidth get() = right - left
        val barHeight get() = bottom - top
    }
    data class Boundary(override val id: String, override val at: Long,
                        val discardAttempt: Boolean = false,
                        val clearIdentity: Boolean = true) : VerifiedHpFaintObservation
}

internal data class VerifiedHpFaintResolution(
    val emptyArticleId: String,
    val atNanos: Long,
    val owner: TeamLossCandidate,
    val evidenceReferences: List<String>,
)

internal data class VerifiedHpFaintReconciliation(
    val faints: List<VerifiedHpFaintResolution>,
    /** A later independent positive reading disproves these zero values, not their raw existence. */
    val contradictedEmptyEvidence: Map<String, List<String>>,
)

/**
 * A strict RED -> EMPTY outline in one owned battlefield interval can support a
 * KO without a final badge count. Missing pixels, numeric zero and charge-camera
 * bars cannot. Completed history can retract the conclusion on later survival.
 * This resolver neither estimates damage nor manufactures resource counts.
 */
internal object VerifiedHpFaintResolver {
    const val BASIS = "VERIFIED_RED_TO_EMPTY_HP"
    private data class EmptyReading(val id: String, val at: Long, val owner: String, val evidence: List<String>)
    private data class Survival(val hpAt: Long, val identityAt: Long, val evidence: List<String>)

    fun resolve(input: Collection<VerifiedHpFaintObservation>): List<VerifiedHpFaintResolution> = reconcile(input).faints

    fun reconcile(input: Collection<VerifiedHpFaintObservation>): VerifiedHpFaintReconciliation {
        // A conflicting custody ID is an unknown barrier at every supplied time.
        val events = input.groupBy { it.id }.values.flatMap { copies ->
            copies.distinct().singleOrNull()?.let(::listOf) ?: copies.map {
                VerifiedHpFaintObservation.Boundary(it.id, it.at)
            }
        }.filter { it.at >= 0 }.sortedWith(compareBy({ it.at }, { it.id }))
        val identities = events.filterIsInstance<VerifiedHpFaintObservation.Identity>()
        val named = identities.filter { it.independent && it.speciesId != null }
        val ambiguousNames = named.groupBy { key(it.name) }
            .filterValues { rows -> rows.map { it.speciesId }.distinct().size > 1 }.keys
        val ambiguousIds = named.groupBy { it.speciesId }
            .filterValues { rows -> rows.map { key(it.name) }.distinct().size > 1 }.keys
        var owner: TeamLossCandidate? = null
        var independentIdentity: VerifiedHpFaintObservation.Identity? = null
        var red: VerifiedHpFaintObservation.Hp? = null
        var armAt: Long? = null
        val faints = mutableListOf<VerifiedHpFaintResolution>()
        val emptyReadings = mutableListOf<EmptyReading>()
        val survived = mutableMapOf<String, Survival>()

        for ((at, frame) in events.groupBy { it.at }) {
            val boundaries = frame.filterIsInstance<VerifiedHpFaintObservation.Boundary>()
            if (boundaries.isNotEmpty()) {
                if (boundaries.any { it.clearIdentity || it.discardAttempt }) {
                    owner = null; independentIdentity = null
                }
                red = null; armAt = null
                if (boundaries.any { it.discardAttempt }) { faints.clear(); emptyReadings.clear(); survived.clear() }
                continue // No ordering can be inferred within a boundary's source frame.
            }
            val names = frame.filterIsInstance<VerifiedHpFaintObservation.Identity>()
            // An announcement repeating a known badge name adds no ownership
            // proof, but also does not make the known actor disappear. Explicit
            // entries arrive as boundaries, including same-name re-entries.
            val repeatsKnown = owner != null && names.all { !it.independent &&
                key(it.name) == key(owner!!.speciesName) &&
                (it.speciesId == null || owner!!.speciesId == null || it.speciesId == owner!!.speciesId) }
            if (names.isNotEmpty() && !repeatsKnown) {
                val valid = names.all { it.independent && key(it.name).isNotBlank() &&
                    key(it.name) !in ambiguousNames && it.speciesId !in ambiguousIds } &&
                    names.map { key(it.name) }.distinct().size == 1 &&
                    names.mapNotNull { it.speciesId }.distinct().size <= 1
                val name = names.first()
                if (!valid) { owner = null; independentIdentity = null; red = null; armAt = null }
                else {
                    independentIdentity = name
                    if (owner?.let { key(it.speciesName) != key(name.name) } != false) {
                        owner = TeamLossCandidate(name.id, at, name.name, name.speciesId)
                        red = null; armAt = null
                    }
                }
            }
            val readings = frame.filterIsInstance<VerifiedHpFaintObservation.Hp>()
            if (readings.isEmpty()) continue
            // Duplicate deliveries of one ID were removed above. Two disagreeing
            // readers at one time cannot choose which bar belongs to this actor.
            if (readings.size != 1) { red = null; armAt = null; continue }
            val hp = readings.single()
            val actor = owner ?: continue
            if (!valid(hp) || actor.appearanceAtNanos >= at) { red = null; armAt = null; continue }
            if (hp.fraction > 0f) independentIdentity?.let { badge ->
                survived[key(actor.speciesName)] = Survival(at, badge.at, listOf(badge.id, hp.id))
            }
            val previousRed = red
            if (previousRed != null && (!near(previousRed, hp) ||
                    hp.fraction > TerminalHpWatchPolicy.MAX_RED_FRACTION)) {
                red = null; armAt = null
            }
            if (!hp.trackedBattlefieldSource) { red = null; armAt = null; continue }
            when {
                hp.color == "RED" && hp.fraction > 0f &&
                    hp.fraction <= TerminalHpWatchPolicy.MAX_RED_FRACTION -> {
                    if (armAt == null || at - armAt!! >= TerminalHpWatchPolicy.MAX_BURST_NANOS) armAt = at
                    red = hp
                }
                hp.color == "EMPTY" && hp.fraction == 0f -> {
                    val anchor = red
                    // Repeated zeros are not additional KOs. Retain their owners
                    // so a later survival proof can also reject those HP values.
                    emptyReadings += EmptyReading(hp.id, at, key(actor.speciesName),
                        listOfNotNull(actor.appearanceArticleId, anchor?.id, hp.id))
                    if (anchor != null && armAt?.let {
                            at - it in 1 until TerminalHpWatchPolicy.MAX_BURST_NANOS } == true && near(anchor, hp)) {
                        faints += VerifiedHpFaintResolution(hp.id, at, actor,
                            listOf(actor.appearanceArticleId, anchor.id, hp.id))
                    }
                    red = null; armAt = null
                }
                hp.fraction == 0f -> { red = null; armAt = null }
            }
        }
        // HP after an apparent KO might belong to an unidentified replacement.
        // Require a fresh independent badge after the empty reading as well;
        // the old active name must not silently own the replacement's HP.
        val contradicted = emptyReadings.filter { empty -> survived[empty.owner]?.let {
            it.hpAt > empty.at && it.identityAt > empty.at
        } == true }
        val contradictedIds = contradicted.map { it.id }.toSet()
        return VerifiedHpFaintReconciliation(
            faints.filterNot { it.emptyArticleId in contradictedIds }.distinctBy { key(it.owner.speciesName) },
            contradicted.associate { it.id to (it.evidence + survived.getValue(it.owner).evidence).distinct() },
        )
    }

    private fun valid(hp: VerifiedHpFaintObservation.Hp): Boolean =
        hp.width != null && hp.height != null && hp.width > 0 && hp.height > 0 &&
            hp.left >= 0 && hp.top >= 0 && hp.right <= hp.width && hp.bottom < hp.height &&
            hp.barWidth >= 32 && hp.barHeight >= 6 && hp.fraction.isFinite() && hp.fraction in 0f..1f &&
            hp.confidence?.let { it.isFinite() && it in .80f..1f } == true &&
            (hp.color == null || (hp.color == "EMPTY" && hp.fraction == 0f) ||
                (hp.color in setOf("RED", "YELLOW", "GREEN") && hp.fraction > 0f))

    private fun near(a: VerifiedHpFaintObservation.Hp, b: VerifiedHpFaintObservation.Hp): Boolean =
        a.width == b.width && a.height == b.height &&
            ActiveHpBarGeometry.matchesSize(b.barWidth, b.barHeight, a.barWidth, a.barHeight) &&
            abs(a.left - b.left) <= max(4, a.barWidth / 25) &&
            abs(a.right - b.right) <= max(4, a.barWidth / 25) &&
            abs(a.top - b.top) <= max(4, a.barHeight)

    private fun key(name: String) = TeamLossResolver.speciesKey(name)
}
