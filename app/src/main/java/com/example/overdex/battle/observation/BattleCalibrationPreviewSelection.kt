package com.example.overdex.battle.observation

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.artifact.CropArtifactReference
import com.example.overdex.battle.custody.*
import com.example.overdex.battle.inference.FastMoveUseEvidence
import com.example.overdex.battle.reality.RealityArticle

enum class BattleCalibrationPreviewSlot {
    PLAYER_SPECIES, OPPONENT_SPECIES, PLAYER_HP, OPPONENT_HP,
    PLAYER_BALLS, OPPONENT_BALLS, ANNOUNCEMENT
}


/** Pixel provenance is retained independently of any UI bitmap or current calibration. */
internal data class BattleCalibrationPreviewReference(
    val articleId: String,
    val capturedAtMillis: Long,
    val capturedAtNanos: Long?,
    val artifact: CropArtifactReference,
    val provenance: BattleCropProvenance,
) {
    fun samePixels(other: BattleCalibrationPreviewReference): Boolean =
        artifact == other.artifact && provenance == other.provenance
}

/**
 * Raw calibration pixels from source-time battle windows. Context can arrive
 * after crops: keep references long enough to reselect when a late boundary
 * rules out a newer menu/result image. No field must recognize its own target.
 */
internal class BattleCalibrationPreviewSelection(val matchId: String) {
    private data class Time(val wall: Long, val mono: Long?) {
        fun value(monotonic: Boolean): Long? = if (monotonic) mono else wall
    }
    private enum class Kind { BATTLE, PLAYER_HP, OPPONENT_HP, OPENING, END, STOP, CLOSED, ANNOUNCEMENT }
    private data class HpGeometry(
        val left: Int, val top: Int, val right: Int, val bottom: Int,
        val sourceWidth: Int?, val sourceHeight: Int?,
    )
    private data class Context(val id: String, val time: Time, val kind: Kind, val hp: HpGeometry? = null)
    private data class Window(val from: Long, val until: Long)
    private val history = linkedMapOf<BattleCalibrationPreviewSlot, ArrayDeque<BattleCalibrationPreviewReference>>()
    private val context = ArrayDeque<Context>()
    private val barriers = ArrayDeque<Context>()
    // First result/stop in each clock domain is permanent, including after a
    // long high-rate tail has left the bounded rolling context history.
    private val terminals = mutableMapOf<Pair<Kind, Boolean>, Context>()
    private var latest = emptyMap<BattleCalibrationPreviewSlot, BattleCalibrationPreviewReference>()
    private var dirty = false
    private var cachedWindows: List<Window>? = null
    // Use one clock for the complete relevant set. A legacy boundary cannot be
    // ignored just because newer crop rows happen to contain monotonic times.
    private var monotonicOnly = true
    val crops: Map<BattleCalibrationPreviewSlot, BattleCalibrationPreviewReference> get() {
        if (dirty) {
            val selected = linkedMapOf<BattleCalibrationPreviewSlot, BattleCalibrationPreviewReference>()
            for ((slot, references) in history) {
                // A retained last battle image survives a long navigation-only
                // tail even after that image leaves the bounded pending history.
                val candidates = (references.toList() + listOfNotNull(latest[slot])).filter(::eligible)
                val best = best(slot, candidates)
                if (best != null) selected[slot] = best
            }
            latest = selected
            dirty = false
        }
        return latest.toMap()
    }

    val selectedArticleIds: Set<String> get() = crops.values.map { it.articleId }.toSet()

    fun accept(article: RealityArticle) {
        if (article.matchId?.value != matchId) return
        val crop = article.payload as? CropCaptured
        if (crop != null) {
            accept(crop.cropProvenance.cropName, BattleCalibrationPreviewReference(article.id.value,
                article.perceivedAt, article.monotonicTimeNanos, crop.artifact, crop.cropProvenance))
            return
        }
        val kind = when (val p = article.payload) {
            MatchStarted, is SupportingMatchStart -> Kind.OPENING
            is ActiveHpBarMeasured -> hpKind(p.side.name).takeIf { validHp(p.barLeft, p.barTop, p.barRight, p.barBottom,
                p.filledFraction, p.sourceWidth, p.sourceHeight) }
            is ActiveHpBarDamageTickMeasured -> Kind.BATTLE.takeIf { validDamage(p.beforeFraction, p.afterFraction, p.lostFraction) }
            is FastMoveUseObserved -> Kind.BATTLE.takeIf {
                p.attackingSide != p.damagedSide && FastMoveUseEvidence.establishesUse(p.evidenceKinds)
            }
            is MatchEnded -> Kind.END
            OutOfBattleMenuWitnessed, ObservationRestartedByUser, ScreenIgnoredByUser -> Kind.CLOSED
            ObservationSessionStoppedByUser -> Kind.STOP
            GetReadyWitnessed, AttackIncoming, ChargeMoveUsedAnnounced -> Kind.ANNOUNCEMENT
            is ActivePokemonSpeciesWitnessed -> Kind.ANNOUNCEMENT.takeIf {
                article.sourceId.id == "ENTRY_ANNOUNCEMENT_SPECIES_WITNESS"
            }
            else -> null
        }
        val hp = (article.payload as? ActiveHpBarMeasured)?.let {
            HpGeometry(it.barLeft, it.barTop, it.barRight, it.barBottom, it.sourceWidth, it.sourceHeight)
        }
        if (kind != null) accept(Context(article.id.value, Time(article.perceivedAt, article.monotonicTimeNanos), kind, hp))
    }

    fun accept(article: ArchivedRealityArticle) {
        if (article.matchId != matchId) return
        val crop = article.payload as? ArchivedCropCaptured
        if (crop != null) {
            accept(crop.cropName, BattleCalibrationPreviewReference(article.articleId, article.perceivedAt,
                article.monotonicTimeNanos,
                CropArtifactReference(crop.artifactPath, crop.sha256, crop.byteCount, crop.mediaType),
                BattleCropProvenance(crop.cropName, crop.sourceWidth, crop.sourceHeight,
                    BattleCropBounds(crop.cropLeft, crop.cropTop, crop.cropRight, crop.cropBottom))))
            return
        }
        val kind = when (val p = article.payload) {
            ArchivedMatchStarted, is ArchivedSupportingMatchStart -> Kind.OPENING
            is ArchivedActiveHpBarMeasured -> hpKind(p.side).takeIf { validHp(p.barLeft, p.barTop, p.barRight, p.barBottom,
                p.filledFraction, p.sourceWidth, p.sourceHeight) }
            is ArchivedActiveHpBarDamageTickMeasured -> Kind.BATTLE.takeIf { validDamage(p.beforeFraction, p.afterFraction, p.lostFraction) }
            is ArchivedFastMoveUseObserved -> Kind.BATTLE.takeIf {
                p.attackingSide in setOf("PLAYER", "OPPONENT") && p.damagedSide in setOf("PLAYER", "OPPONENT") &&
                    p.attackingSide != p.damagedSide && FastMoveUseEvidence.establishesUse(p.evidenceKinds)
            }
            is ArchivedMatchEnded -> Kind.END
            ArchivedOutOfBattleMenuWitnessed, ArchivedObservationRestartedByUser, ArchivedScreenIgnoredByUser -> Kind.CLOSED
            ArchivedObservationSessionStoppedByUser -> Kind.STOP
            ArchivedGetReadyWitnessed, ArchivedAttackIncoming, ArchivedChargeMoveUsedAnnounced -> Kind.ANNOUNCEMENT
            is ArchivedActivePokemonSpeciesWitnessed -> Kind.ANNOUNCEMENT.takeIf {
                article.sourceId == "ENTRY_ANNOUNCEMENT_SPECIES_WITNESS"
            }
            else -> null
        }
        val hp = (article.payload as? ArchivedActiveHpBarMeasured)?.let {
            HpGeometry(it.barLeft, it.barTop, it.barRight, it.barBottom, it.sourceWidth, it.sourceHeight)
        }
        if (kind != null) accept(Context(article.articleId, Time(article.perceivedAt, article.monotonicTimeNanos), kind, hp))
    }

    private fun accept(name: String, reference: BattleCalibrationPreviewReference) {
        val slot = slots[name] ?: return
        val references = history.getOrPut(slot) { ArrayDeque() }
        if (references.any { it.articleId == reference.articleId }) return
        if (reference.capturedAtNanos == null && monotonicOnly) { monotonicOnly = false; cachedWindows = null; dirty = true }
        val at = if (monotonicOnly) requireNotNull(reference.capturedAtNanos) else reference.capturedAtMillis
        if (terminalAt(monotonicOnly)?.let { at >= it } == true) return
        references.addLast(reference)
        while (references.size > MAX_REFERENCES_PER_SLOT) {
            val oldest = references.minWithOrNull(compareBy<BattleCalibrationPreviewReference> {
                if (monotonicOnly) requireNotNull(it.capturedAtNanos) else it.capturedAtMillis
            }.thenBy { it.articleId }) ?: break
            references.remove(oldest)
            // A cold archive/live batch can contain a long navigation tail
            // before the first read of crops. Protect qualifying pixels during
            // eviction too, rather than relying on a previous UI poll.
            if (eligible(oldest)) {
                best(slot, (listOfNotNull(latest[slot]) + oldest).filter(::eligible))?.let {
                    latest = latest + (slot to it)
                }
            }
        }
        dirty = true
    }

    private fun accept(event: Context) {
        if (event.time.mono == null && monotonicOnly) { monotonicOnly = false; cachedWindows = null; dirty = true }
        if (event.kind == Kind.END || event.kind == Kind.STOP) {
            for (monotonic in listOf(false, true)) {
                val at = event.time.value(monotonic) ?: continue
                val key = event.kind to monotonic
                val previous = terminals[key]
                if (previous == null || at < requireNotNull(previous.time.value(monotonic))) terminals[key] = event
            }
            cachedWindows = null
            dirty = true
            return
        }
        if (terminalAt(monotonicOnly)?.let { requireNotNull(event.time.value(monotonicOnly)) >= it } == true) return
        if (event.kind == Kind.CLOSED) {
            if (barriers.none { it.id == event.id }) barriers.addLast(event)
            while (barriers.size > MAX_BARRIERS) barriers.removeFirst()
            cachedWindows = null
            dirty = true
            return
        }
        if (context.any { it.id == event.id }) return
        context.addLast(event)
        while (context.size > MAX_CONTEXT_EVENTS) context.removeFirst()
        cachedWindows = null
        dirty = true
    }

    private fun activeWindows(): List<Window> = cachedWindows ?: windows(monotonicOnly).also { cachedWindows = it }

    private fun eligible(reference: BattleCalibrationPreviewReference): Boolean {
        val at = if (monotonicOnly) requireNotNull(reference.capturedAtNanos) else reference.capturedAtMillis
        return activeWindows().any { at in it.from..it.until }
    }

    private fun best(slot: BattleCalibrationPreviewSlot, candidates: List<BattleCalibrationPreviewReference>): BattleCalibrationPreviewReference? {
        if (slot == BattleCalibrationPreviewSlot.PLAYER_HP || slot == BattleCalibrationPreviewSlot.OPPONENT_HP) {
            val windows = activeWindows()
            val cues = context.filter { cue ->
                (cue.kind == Kind.PLAYER_HP || cue.kind == Kind.OPPONENT_HP) &&
                    windows.any { requireNotNull(cue.time.value(monotonicOnly)) in it.from..it.until }
            }
            // HP is sampled much more frequently than its saved raw image. A
            // broad battle window can include the camera transition after bars
            // disappear. Prefer a nearby source frame, from either HP side:
            // the opposite bar still exposes a miscalibrated own-side box.
            if (cues.isNotEmpty()) return preferredHp(slot, candidates, cues)
            // Older/start-only recordings have no measured HP opportunity.
            // Keep their honest raw pixels rather than hiding bad calibration.
        }
        return (if (slot == BattleCalibrationPreviewSlot.ANNOUNCEMENT) preferredAnnouncement(candidates) else null)
            ?: candidates.reduceOrNull { old, next -> if (newer(next, old)) next else old }
    }

    private fun preferredHp(
        slot: BattleCalibrationPreviewSlot,
        candidates: List<BattleCalibrationPreviewReference>,
        cues: List<Context>,
    ): BattleCalibrationPreviewReference? {
        val tolerance = HP_FRAME_TOLERANCE_MILLIS * if (monotonicOnly) 1_000_000L else 1L
        val opportunities = cues.mapNotNull { cue ->
            val at = requireNotNull(cue.time.value(monotonicOnly))
            val nearby = candidates.mapNotNull { reference ->
                val captured = if (monotonicOnly) requireNotNull(reference.capturedAtNanos) else reference.capturedAtMillis
                val distance = kotlin.math.abs(captured.toDouble() - at)
                (reference to distance).takeIf { distance <= tolerance && sameSourceSize(reference, cue) }
            }
            val closest = nearby.minWithOrNull(compareBy<Pair<BattleCalibrationPreviewReference, Double>> { it.second }
                .thenByDescending { containsMeasuredHp(slot, it.first, cue) }
                .thenByDescending { if (monotonicOnly) it.first.capturedAtNanos else it.first.capturedAtMillis }
                .thenByDescending { it.first.articleId })?.first
            closest?.let { cue to it }
        }
        return opportunities.maxWithOrNull(compareBy<Pair<Context, BattleCalibrationPreviewReference>> {
            requireNotNull(it.first.time.value(monotonicOnly))
        }.thenBy { containsMeasuredHp(slot, it.second, it.first) }
            .thenBy { it.first.id })?.second
    }

    private fun containsMeasuredHp(
        slot: BattleCalibrationPreviewSlot,
        reference: BattleCalibrationPreviewReference,
        cue: Context,
    ): Boolean {
        if ((slot == BattleCalibrationPreviewSlot.PLAYER_HP) != (cue.kind == Kind.PLAYER_HP)) return false
        val hp = cue.hp ?: return false
        val crop = reference.provenance
        if (!sameSourceSize(reference, cue)) return false
        return hp.left >= crop.bounds.left && hp.top >= crop.bounds.top &&
            hp.right <= crop.bounds.right && hp.bottom <= crop.bounds.bottom
    }

    private fun sameSourceSize(reference: BattleCalibrationPreviewReference, cue: Context): Boolean {
        val hp = cue.hp ?: return true
        return (hp.sourceWidth == null || hp.sourceWidth == reference.provenance.sourceWidth) &&
            (hp.sourceHeight == null || hp.sourceHeight == reference.provenance.sourceHeight)
    }

    private fun terminalAt(monotonic: Boolean): Long? = terminals.entries.asSequence()
        .filter { it.key.second == monotonic }.mapNotNull { it.value.time.value(monotonic) }.minOrNull()

    private fun windows(monotonic: Boolean): List<Window> {
        val scale = if (monotonic) 1_000_000L else 1L
        val events = (context.toList() + barriers + terminals.filterKeys { it.second == monotonic }.values)
            .mapNotNull { event -> event.time.value(monotonic)?.let { it to event.kind } }
        val resultAt = events.filter { it.second == Kind.END }.minOfOrNull { it.first }
        val ended = events.filter { it.second == Kind.END || it.second == Kind.STOP }.minOfOrNull { it.first }
        val closed = events.filter { it.second == Kind.CLOSED }.map { it.first }.distinct().sorted()
        // One false bar-shaped patch in navigation cannot establish a battle.
        // Two sides in one frame or repeated bars in distinct frames can.
        val hp = events.filter { (at, kind) -> (kind == Kind.PLAYER_HP || kind == Kind.OPPONENT_HP) && (ended == null || at < ended) }
            .distinct().sortedBy { it.first }
        val credibleHp = hp.filterIndexed { index, event ->
            listOfNotNull(hp.getOrNull(index - 1), hp.getOrNull(index + 1)).any { other ->
                kotlin.math.abs(other.first.toDouble() - event.first) <= 1_000L * scale &&
                    closed.none { boundary -> boundary in minOf(event.first, other.first)..maxOf(event.first, other.first) }
            }
        }.toSet()
        val battleAnchors = events.filter { event ->
            val (at, kind) = event
            (kind == Kind.BATTLE || kind == Kind.OPENING || event in credibleHp) && (ended == null || at < ended)
        }
        val announcements = events.filter { (at, kind) ->
            kind == Kind.ANNOUNCEMENT && (ended == null || at < ended) && battleAnchors.any { anchor ->
                anchor.first <= saturatedAdd(at, 750L * scale) &&
                    closed.none { boundary -> boundary in minOf(anchor.first, at)..maxOf(anchor.first, at) }
            }
        }
        val anchors = battleAnchors + announcements
        val windows = anchors.mapNotNull { (at, kind) ->
            if (closed.binarySearch(at) >= 0) return@mapNotNull null
            val insertion = -closed.binarySearch(at) - 1
            val before = if (kind == Kind.OPENING) 0L else 1_000L * scale
            val after = if (kind == Kind.OPENING) 3_000L * scale else 1_000L * scale
            val from = maxOf(saturatedAdd(at, -before), closed.getOrNull(insertion - 1)?.let { saturatedAdd(it, 1) } ?: Long.MIN_VALUE)
            val until = minOf(saturatedAdd(at, after),
                closed.getOrNull(insertion)?.let { saturatedAdd(it, -1) } ?: Long.MAX_VALUE,
                ended?.let { saturatedAdd(it, -1) } ?: Long.MAX_VALUE)
            Window(from, until).takeIf { from <= until }
        }.toMutableList()
        // Old archives may preserve only a decisive result. Keep a bounded
        // pre-result fallback, rather than blessing that entire field session.
        if (anchors.isEmpty() && resultAt != null && ended == resultAt) {
            val priorClose = closed.lastOrNull { it < resultAt }
            val from = maxOf(saturatedAdd(resultAt, -30_000L * scale), priorClose?.let { saturatedAdd(it, 1) } ?: Long.MIN_VALUE)
            val until = saturatedAdd(resultAt, -1)
            if (from <= until) windows += Window(from, until)
        }
        val merged = mutableListOf<Window>()
        windows.sortedBy { it.from }.forEach { next ->
            val previous = merged.lastOrNull()
            if (previous != null && next.from <= saturatedAdd(previous.until, 1)) {
                merged[merged.lastIndex] = Window(previous.from, maxOf(previous.until, next.until))
            } else merged += next
        }
        return merged
    }

    private fun preferredAnnouncement(candidates: List<BattleCalibrationPreviewReference>): BattleCalibrationPreviewReference? {
        val windows = activeWindows()
        val cues = context.filter { cue ->
            if (cue.kind != Kind.ANNOUNCEMENT) false else {
                val at = requireNotNull(cue.time.value(monotonicOnly))
                windows.any { at in it.from..it.until }
            }
        }
        // Latest phase first; within it, prefer the original source frame over
        // a later empty image. With no nearby cue, preserve raw battle fallback.
        val eligible = cues.mapNotNull { cue ->
            val nearby = candidates.mapNotNull { reference ->
                val distance = if (monotonicOnly)
                    kotlin.math.abs(requireNotNull(reference.capturedAtNanos).toDouble() - requireNotNull(cue.time.mono)) / 1_000_000
                else kotlin.math.abs(reference.capturedAtMillis.toDouble() - cue.time.wall)
                if (distance <= 1_000) reference to distance else null
            }
            nearby.minWithOrNull(compareBy<Pair<BattleCalibrationPreviewReference, Double>> { it.second }
                .thenByDescending { it.first.articleId })?.first?.let { cue to it }
        }
        return eligible.reduceOrNull { old, next ->
            val previousTime = requireNotNull(old.first.time.value(monotonicOnly))
            val nextTime = requireNotNull(next.first.time.value(monotonicOnly))
            val later = nextTime > previousTime || (nextTime == previousTime && next.first.id > old.first.id)
            if (later) next else old
        }?.second
    }

    private fun newer(reference: BattleCalibrationPreviewReference, previous: BattleCalibrationPreviewReference): Boolean {
        val at = if (monotonicOnly) requireNotNull(reference.capturedAtNanos) else reference.capturedAtMillis
        val priorAt = if (monotonicOnly) requireNotNull(previous.capturedAtNanos) else previous.capturedAtMillis
        return at > priorAt || (at == priorAt && reference.articleId > previous.articleId)
    }

    companion object {
        private const val MAX_REFERENCES_PER_SLOT = 2_048
        private const val MAX_CONTEXT_EVENTS = 8_192
        private const val MAX_BARRIERS = 512
        // Recent HP observations normally arrive about one 125 ms frame apart;
        // a half-second gap can already be a completely bar-free camera phase.
        private const val HP_FRAME_TOLERANCE_MILLIS = 150L
        private val slots = mapOf(
            "PlayerActiveSpeciesTextCrop" to BattleCalibrationPreviewSlot.PLAYER_SPECIES,
            "OpponentActiveSpeciesTextCrop" to BattleCalibrationPreviewSlot.OPPONENT_SPECIES,
            "PlayerHpEvidenceCrop" to BattleCalibrationPreviewSlot.PLAYER_HP,
            "OpponentHpEvidenceCrop" to BattleCalibrationPreviewSlot.OPPONENT_HP,
            "PlayerPokeBallsCrop" to BattleCalibrationPreviewSlot.PLAYER_BALLS,
            "OpponentPokeBallsCrop" to BattleCalibrationPreviewSlot.OPPONENT_BALLS,
            "AnnouncementCrop" to BattleCalibrationPreviewSlot.ANNOUNCEMENT,
        )
        private fun saturatedAdd(value: Long, delta: Long): Long = when {
            delta > 0 && value > Long.MAX_VALUE - delta -> Long.MAX_VALUE
            delta < 0 && value < Long.MIN_VALUE - delta -> Long.MIN_VALUE
            else -> value + delta
        }
        private fun hpKind(side: String): Kind? = when (side) {
            "PLAYER" -> Kind.PLAYER_HP
            "OPPONENT" -> Kind.OPPONENT_HP
            else -> null
        }
        private fun validHp(left: Int, top: Int, right: Int, bottom: Int, fraction: Float, width: Int?, height: Int?): Boolean =
            left >= 0 && top >= 0 && right > left && bottom > top && fraction.isFinite() && fraction in 0f..1f &&
                ((width == null && height == null) || (width != null && height != null && width >= right && height >= bottom))
        private fun validDamage(before: Float, after: Float, lost: Float): Boolean =
            before.isFinite() && after.isFinite() && lost.isFinite() && before in 0f..1f && after in 0f..1f &&
                after < before && lost > 0f && kotlin.math.abs(before - after - lost) < .001f

        fun fromBattleArchive(archive: MatchArchive): BattleCalibrationPreviewSelection? =
            fromArchivedArticles(archive.matchId, archive.articles)

        fun fromArchivedArticles(matchId: String, rows: List<ArchivedRealityArticle>): BattleCalibrationPreviewSelection? {
            val selected = BattleCalibrationPreviewSelection(matchId)
            val articles = rows.filter { it.matchId == matchId }
            // Establish the clock domain and terminal boundaries before bounding
            // pending crop history, independent of asynchronous article order.
            if (articles.any { it.monotonicTimeNanos == null && when (val p = it.payload) {
                    is ArchivedCropCaptured -> p.cropName in slots
                    ArchivedMatchStarted, is ArchivedSupportingMatchStart, is ArchivedActiveHpBarMeasured,
                    is ArchivedActiveHpBarDamageTickMeasured, is ArchivedFastMoveUseObserved, is ArchivedMatchEnded,
                    ArchivedOutOfBattleMenuWitnessed, ArchivedObservationRestartedByUser, ArchivedScreenIgnoredByUser,
                    ArchivedObservationSessionStoppedByUser, ArchivedGetReadyWitnessed, ArchivedAttackIncoming,
                    ArchivedChargeMoveUsedAnnounced -> true
                    is ArchivedActivePokemonSpeciesWitnessed -> it.sourceId == "ENTRY_ANNOUNCEMENT_SPECIES_WITNESS"
                    else -> false
                } }) selected.monotonicOnly = false
            articles.filter { it.payload !is ArchivedCropCaptured }.forEach(selected::accept)
            articles.filter { it.payload is ArchivedCropCaptured }.forEach(selected::accept)
            return selected.takeIf { it.crops.isNotEmpty() }
        }
    }
}
