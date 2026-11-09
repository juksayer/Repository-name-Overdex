package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide
import kotlin.math.abs

/** Bitmap-free preparation/impact window. No cue or missing frame asserts damage. */
internal class ChargedImpactHpWatchPolicy {
    data class Request(
        val side: ActivePokemonSide, val reference: ActiveHpBarMeasurement,
        val anchor: ActiveHpBarMeasurement?, val token: Long, val cueArticleIds: List<String>,
    )
    private data class Reference(val bar: ActiveHpBarMeasurement, val at: Long)
    private data class Window(
        val side: ActivePokemonSide, val preparedAt: Long, var until: Long,
        val reference: ActiveHpBarMeasurement, val token: Long,
        val cueArticleIds: MutableList<String>,
        var anchor: ActiveHpBarMeasurement? = null, var anchorAt: Long? = null,
        var acquiredAt: Long? = null, var lastScanAt: Long? = null, var consumed: Boolean = false,
    )
    private val references = mutableMapOf<ActivePokemonSide, ArrayDeque<Reference>>()
    private val impacts = java.util.TreeMap<Long, String?>()
    private val boundaries = mutableMapOf<ActivePokemonSide, Long>()
    private val resetHubOnReturn = mutableSetOf<ActivePokemonSide>()
    private var window: Window? = null
    private var token = 0L
    private var latestCueAt = Long.MIN_VALUE
    private var latestFrameAt = Long.MIN_VALUE
    private var dimensions: Pair<Int, Int>? = null

    @Synchronized fun seed(side: ActivePokemonSide, bar: ActiveHpBarMeasurement, at: Long) {
        if (bar.filledFraction <= 0f || boundaries[side]?.let { at <= it } == true ||
            references[side]?.lastOrNull()?.let { at <= it.at } == true) return
        // A centered charge camera cannot replace a normal side-owned reference.
        val size = dimensions ?: return
        if (abs(bar.centerX - size.first / 2f) <= size.first * .10f) return
        val history = references.getOrPut(side) { ArrayDeque() }
        history += Reference(bar, at)
        while (history.size > 96 || history.isNotEmpty() && at - history.first().at > MAX_REFERENCE_AGE_NANOS * 2)
            history.removeFirst()
    }

    /** Get Ready belongs to the player; Attack Incoming belongs to the opponent. */
    @Synchronized fun prepare(recipient: ActivePokemonSide, at: Long, articleId: String? = null) {
        if (at <= latestCueAt || boundaries[recipient]?.let { at <= it } == true) return
        latestCueAt = at
        window = null
        val reference = references[recipient]?.lastOrNull { it.at <= at } ?: return
        if (at - reference.at !in 0..MAX_REFERENCE_AGE_NANOS) return
        window = Window(recipient, at, at + PREPARATION_WINDOW_NANOS, reference.bar, ++token,
            mutableListOf<String>().apply { if (articleId != null) add(articleId) })
        impacts.ceilingEntry(at)?.let { impact(it.key, it.value) }
    }

    @Synchronized fun impact(at: Long, articleId: String? = null) {
        impacts[at] = articleId
        while (impacts.size > 8) impacts.pollFirstEntry()
        val active = window ?: return
        if (at < active.preparedAt || at > active.until || active.consumed) return
        active.until = minOf(active.preparedAt + MAX_PHASE_NANOS, at + IMPACT_WINDOW_NANOS)
        if (articleId != null && articleId !in active.cueArticleIds) active.cueArticleIds += articleId
    }

    /** Acquisition is 10 Hz; after a real outline is found its tiny rectangle is checked every frame. */
    @Synchronized fun request(width: Int, height: Int, at: Long): Request? {
        if (at <= latestFrameAt) return null
        latestFrameAt = at
        val size = width to height
        if (dimensions != null && dimensions != size) resetAll(at)
        dimensions = size
        val active = window ?: return null
        if (at < active.preparedAt + PREPARATION_DELAY_NANOS || at > active.until || active.consumed) return null
        if (active.acquiredAt?.let { at - it >= IMPACT_WINDOW_NANOS } == true) return null
        val anchor = active.anchor?.takeIf { at - requireNotNull(active.anchorAt) in 0..MAX_TRACK_GAP_NANOS }
        if (anchor == null && active.lastScanAt?.let { at - it < ACQUISITION_INTERVAL_NANOS } == true) return null
        if (anchor == null) active.lastScanAt = at
        return Request(active.side, active.reference, anchor, active.token, active.cueArticleIds.toList())
    }

    @Synchronized fun accept(request: Request, sample: TrackedTerminalHpBarSample, at: Long): Boolean {
        val active = window ?: return false
        if (active.token != request.token || active.consumed || at > active.until ||
            boundaries[active.side]?.let { at <= it } == true) return false
        if (!ActiveHpBarGeometry.matchesSize(sample.measurement.width,
                sample.measurement.bottom - sample.measurement.top,
                active.reference.width, active.reference.bottom - active.reference.top)) return false
        active.anchor = sample.measurement
        active.anchorAt = at
        if (active.acquiredAt == null) active.acquiredAt = at
        resetHubOnReturn += active.side
        if (sample.fillColor == HpBarFillColor.EMPTY) active.consumed = true
        return true
    }

    @Synchronized fun current(request: Request) = window?.token == request.token

    @Synchronized fun cameraCueIds(side: ActivePokemonSide, bar: ActiveHpBarMeasurement, at: Long): List<String>? {
        val active = window ?: return null
        val width = dimensions?.first ?: return null
        return active.cueArticleIds.toList().takeIf { active.side == side && at >= active.preparedAt &&
            at <= active.until && abs(bar.centerX - width / 2f) <= width * .10f }
    }

    /** Reset downstream detector baselines during camera samples and once on normal reentry. */
    @Synchronized fun fastMoveEvidenceAllowed(side: ActivePokemonSide, bar: ActiveHpBarMeasurement, at: Long): Boolean {
        if (cameraCueIds(side, bar, at) != null) { resetHubOnReturn += side; return false }
        return !resetHubOnReturn.remove(side)
    }

    @Synchronized fun reset(side: ActivePokemonSide, at: Long? = null) {
        references.remove(side)
        if (window?.side == side) { window = null; token++ }
        resetHubOnReturn += side
        val boundary = at ?: latestFrameAt
        boundaries[side] = maxOf(boundaries[side] ?: Long.MIN_VALUE, boundary)
    }

    @Synchronized fun resetAll(at: Long? = null) { ActivePokemonSide.entries.forEach { reset(it, at) } }

    companion object {
        const val MAX_REFERENCE_AGE_NANOS = 5_000_000_000L
        const val PREPARATION_DELAY_NANOS = 2_000_000_000L
        const val PREPARATION_WINDOW_NANOS = 12_000_000_000L
        const val MAX_PHASE_NANOS = 15_000_000_000L
        const val IMPACT_WINDOW_NANOS = 3_000_000_000L
        const val MAX_TRACK_GAP_NANOS = 500_000_000L
        const val ACQUISITION_INTERVAL_NANOS = 100_000_000L
    }
}

/** Explicit producer provenance survives ordinary Timeline/archive conversion. */
internal object ChargedImpactHpSource {
    fun id(side: ActivePokemonSide) = "CHARGED_IMPACT_${side.name}_HP_BAR_FRAME_WITNESS"
    fun matches(sourceId: String) = ActivePokemonSide.entries.any { id(it) == sourceId }
}
