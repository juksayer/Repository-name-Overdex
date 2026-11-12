package com.example.overdex.battle.observation

import com.example.overdex.battle.custody.ActivePokemonSide
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.reality.RealityArticle
import kotlin.math.abs

/** Only the accepted dedicated grade stream can provide this player-QTE cue. */
internal fun isAcceptedPlayerQteProgressCue(article: RealityArticle): Boolean =
    article.sourceId.id == "QTE_PROGRESS_TEXT_WITNESS" &&
        ((article.payload as? RawTestimony)?.data as? String)?.let(::isPlayerQteGradeText) == true

internal fun isPlayerQteGradeText(text: String): Boolean = PLAYER_QTE_GRADE.matches(text.trim())

private val PLAYER_QTE_GRADE = Regex("(NICE|GREAT|EXCELLENT)!?", RegexOption.IGNORE_CASE)

/** Bitmap-free preparation/impact window. No cue or missing frame asserts damage. */
internal class ChargedImpactHpWatchPolicy {
    enum class PreparationResult {
        PREPARED, SUPPORTED_EXISTING, DUPLICATE_OR_OLDER, APPEARANCE_BOUNDARY,
        EXPIRED, NO_REFERENCE, STALE_REFERENCE,
    }
    data class Request(
        val side: ActivePokemonSide, val reference: ActiveHpBarMeasurement,
        val anchor: ActiveHpBarMeasurement?, val token: Long, val cueArticleIds: List<String>,
    )
    private data class Reference(val bar: ActiveHpBarMeasurement, val at: Long)
    private data class Window(
        val side: ActivePokemonSide, val preparedAt: Long, var until: Long,
        val reference: ActiveHpBarMeasurement, val referenceAt: Long, val token: Long,
        val cueArticleIds: MutableList<String>,
        var anchor: ActiveHpBarMeasurement? = null, var anchorAt: Long? = null,
        var acquiredAt: Long? = null, var lastScanAt: Long? = null, var consumed: Boolean = false,
        var firstImpactAt: Long? = null,
        var qteCueAt: Long? = null,
    )
    private val references = mutableMapOf<ActivePokemonSide, ArrayDeque<Reference>>()
    private val impacts = java.util.TreeMap<Long, String?>()
    private val boundaries = mutableMapOf<ActivePokemonSide, Long>()
    // Invalidates work already scanning when a hard lifecycle/geometry reset occurs.
    // A delayed source-time identity boundary does not invalidate newer pixels.
    private val referenceEpochs = mutableMapOf<ActivePokemonSide, Long>()
    private val resetHubOnReturn = mutableSetOf<ActivePokemonSide>()
    private var window: Window? = null
    private var token = 0L
    private var latestCueAt = Long.MIN_VALUE
    private var retryablePreparation: Pair<ActivePokemonSide, Long>? = null
    private var latestFrameAt = Long.MIN_VALUE
    private var dimensions: Pair<Int, Int>? = null

    @Synchronized fun snapshotReferenceEpoch(side: ActivePokemonSide): Long = referenceEpochs[side] ?: 0L

    /** The ordinary locator may finish after an identity arrives, but never across a hard reset. */
    @Synchronized fun seedIfCurrent(
        side: ActivePokemonSide, bar: ActiveHpBarMeasurement, width: Int, height: Int,
        at: Long, epoch: Long,
    ): Boolean {
        if (epoch != snapshotReferenceEpoch(side) || dimensions != (width to height)) return false
        return seed(side, bar, at)
    }

    @Synchronized fun seed(side: ActivePokemonSide, bar: ActiveHpBarMeasurement, at: Long): Boolean {
        if (bar.filledFraction <= 0f || boundaries[side]?.let { at <= it } == true ||
            references[side]?.lastOrNull()?.let { at <= it.at } == true) return false
        // A centered charge camera cannot replace a normal side-owned reference.
        val size = dimensions ?: return false
        if (abs(bar.centerX - size.first / 2f) <= size.first * .10f) return false
        val history = references.getOrPut(side) { ArrayDeque() }
        history += Reference(bar, at)
        while (history.size > 96 || history.isNotEmpty() && at - history.first().at > MAX_REFERENCE_AGE_NANOS * 2)
            history.removeFirst()
        return true
    }

    /** Get Ready belongs to the player; Attack Incoming belongs to the opponent. */
    @Synchronized fun prepare(recipient: ActivePokemonSide, at: Long, articleId: String? = null): PreparationResult {
        if (at < latestCueAt || at == latestCueAt && retryablePreparation != (recipient to at))
            return PreparationResult.DUPLICATE_OR_OLDER
        if (boundaries[recipient]?.let { at <= it } == true) return PreparationResult.APPEARANCE_BOUNDARY
        if (latestFrameAt > at + PREPARATION_WINDOW_NANOS) return PreparationResult.EXPIRED
        latestCueAt = at
        window = null
        // A trusted newer phase invalidates the old recipient immediately. If
        // its pre-cue geometry is still in flight, canonical delivery may retry
        // this same cue once that measurement arrives; successful windows never renew.
        retryablePreparation = recipient to at
        val reference = references[recipient]?.lastOrNull { it.at <= at } ?: return PreparationResult.NO_REFERENCE
        if (at - reference.at !in 0..MAX_REFERENCE_AGE_NANOS) return PreparationResult.STALE_REFERENCE
        retryablePreparation = null
        window = Window(recipient, at, at + PREPARATION_WINDOW_NANOS, reference.bar, reference.at, ++token,
            mutableListOf<String>().apply { if (articleId != null) add(articleId) })
        impacts.ceilingEntry(at)?.let { impact(it.key, it.value) }
        return PreparationResult.PREPARED
    }

    /**
     * Accepted player QTE progress can arrive before delayed Get Ready OCR.
     * It provides recipient ownership, not an impact or damage measurement.
     * Later grades support the same phase without renewing its clock/anchor.
     */
    @Synchronized fun prepareFromPlayerQte(at: Long, articleId: String): PreparationResult {
        val recipient = ActivePokemonSide.OPPONENT
        if (at <= latestCueAt) return PreparationResult.DUPLICATE_OR_OLDER
        if (boundaries[recipient]?.let { at <= it } == true) return PreparationResult.APPEARANCE_BOUNDARY
        val active = window
        if (active != null && active.side == recipient && at in active.preparedAt..active.until) {
            latestCueAt = at
            retryablePreparation = null
            if (active.qteCueAt == null) {
                active.qteCueAt = at
                active.cueArticleIds += articleId
            }
            return PreparationResult.SUPPORTED_EXISTING
        }
        // Failed/stale progress must not discard a valid phase or block an
        // older Get Ready that still has usable source-time reference geometry.
        val reference = references[recipient]?.lastOrNull { it.at <= at } ?: return PreparationResult.NO_REFERENCE
        if (at - reference.at !in 0..MAX_REFERENCE_AGE_NANOS) return PreparationResult.STALE_REFERENCE
        if (latestFrameAt > at + PREPARATION_WINDOW_NANOS) return PreparationResult.EXPIRED
        return prepare(recipient, at, articleId).also {
            if (it == PreparationResult.PREPARED) window?.qteCueAt = at
        }
    }

    @Synchronized fun impact(at: Long, articleId: String? = null) {
        impacts[at] = articleId
        while (impacts.size > 8) impacts.pollFirstEntry()
        val active = window ?: return
        if (at < active.preparedAt || at > active.until || active.consumed) return
        val firstImpactAt = active.firstImpactAt
        if (firstImpactAt == null || at < firstImpactAt) {
            // Acquiring an early green outline does not mean the charged hit
            // has landed. The earliest source-time closing cue authorizes a
            // bounded window. Delayed older cues may tighten its deadline;
            // repeated or later OCR can never slide it forward.
            active.firstImpactAt = at
            active.until = minOf(active.preparedAt + MAX_PHASE_NANOS, at + IMPACT_WINDOW_NANOS)
        }
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
        if (active.firstImpactAt == null &&
            active.acquiredAt?.let { at - it >= IMPACT_WINDOW_NANOS } == true) return null
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

    /** Sparse scheduling diagnostics only; querying this cannot advance scan cadence. */
    @Synchronized fun preparationDescription(): String = window?.let {
        "windowToken=${it.token} recipient=${it.side} preparedAt=${it.preparedAt} " +
            "referenceAt=${it.referenceAt} until=${it.until}"
    } ?: "windowToken=none"

    /** Sparse forensic crops share the phase window, without advancing the live scan cadence. */
    @Synchronized fun canCapture(at: Long): Boolean {
        val active = window ?: return false
        return at >= active.preparedAt + PREPARATION_DELAY_NANOS && at <= active.until &&
            !active.consumed && boundaries[active.side]?.let { at <= it } != true &&
            (active.firstImpactAt != null ||
                active.acquiredAt?.let { at - it >= IMPACT_WINDOW_NANOS } != true)
    }

    @Synchronized fun cameraCueIds(side: ActivePokemonSide, bar: ActiveHpBarMeasurement, at: Long): List<String>? {
        val active = window ?: return null
        val width = dimensions?.first ?: return null
        return active.cueArticleIds.toList().takeIf { active.side == side && at >= active.preparedAt &&
            at <= active.until && abs(bar.centerX - width / 2f) <= width * .10f }
    }

    enum class FastMoveDisposition { EXCLUDED, BASELINE_ONLY, EVIDENCE }

    /** Camera pixels are excluded; the first normal sample only establishes a baseline. */
    @Synchronized fun fastMoveDisposition(side: ActivePokemonSide, bar: ActiveHpBarMeasurement, at: Long): FastMoveDisposition {
        if (cameraCueIds(side, bar, at) != null) {
            resetHubOnReturn += side
            return FastMoveDisposition.EXCLUDED
        }
        return if (resetHubOnReturn.remove(side)) FastMoveDisposition.BASELINE_ONLY else FastMoveDisposition.EVIDENCE
    }

    fun fastMoveEvidenceAllowed(side: ActivePokemonSide, bar: ActiveHpBarMeasurement, at: Long): Boolean =
        fastMoveDisposition(side, bar, at) == FastMoveDisposition.EVIDENCE

    /**
     * Identity describes its captured appearance, not when recognition finished.
     * Keep only geometry and phases wholly after that boundary. This must not
     * renew a retained phase's deadline, acquisition token, or consumed EMPTY.
     * Stop/disable/resize and unresolved departure resets still use [reset].
     */
    @Synchronized fun appearanceBoundary(side: ActivePokemonSide, at: Long) {
        if (at <= (boundaries[side] ?: Long.MIN_VALUE)) return
        boundaries[side] = at
        references[side]?.let { history ->
            while (history.isNotEmpty() && history.first().at <= at) history.removeFirst()
            if (history.isEmpty()) references.remove(side)
        }
        if (retryablePreparation?.let { it.first == side && it.second <= at } == true)
            retryablePreparation = null
        window?.takeIf { it.side == side }?.let {
            if (it.preparedAt <= at || it.referenceAt <= at) { window = null; token++ }
        }
        resetHubOnReturn += side
    }

    @Synchronized fun reset(side: ActivePokemonSide, at: Long? = null) {
        referenceEpochs[side] = snapshotReferenceEpoch(side) + 1L
        references.remove(side)
        if (retryablePreparation?.first == side) retryablePreparation = null
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
