package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import kotlin.math.abs

/** An explicitly reviewed correspondence, not an assumed shared device clock. */
internal data class PairedReplayAnchor(val primaryArticleId: String, val secondaryArticleId: String)

/** Original article and archive identity remain available even after clock and side mapping. */
internal data class PairedReplayObservation(
    val archiveId: String,
    val article: ArchivedRealityArticle,
    val offsetNanos: Long,
    val side: String,
    val species: String?,
    val kind: Kind,
    val moveName: String? = null,
) {
    enum class Kind { FAST_MOVE, CHARGED_MOVE, FAINT }
}

internal data class PairedReplayEvent(
    val observations: List<PairedReplayObservation>,
    /** A nearby alternative makes coincidence unresolved; it must not be silently suppressed. */
    val ambiguous: Boolean,
)

internal data class PairedReplayEvidence(
    val secondaryClockOffsetNanos: Long,
    val anchorResidualNanos: Long,
    val events: List<PairedReplayEvent>,
    val alignedIdentities: List<ReplayIdentityObservation> = emptyList(),
)

/**
 * Source-preserving preparation for paired playback. No KO cause is inferred here.
 * One-to-one matching is permitted only when both sides have a unique counterpart;
 * unresolved nearby observations survive as separate, explicitly ambiguous events.
 */
internal object PairedReplayEvidenceBuilder {
    private const val MAX_ANCHOR_RESIDUAL = 750_000_000L
    private const val MATCH_WINDOW = 450_000_000L

    /** Unique mirrored entry/move announcements are proposed anchors, never match identification. */
    fun proposedAnchors(primary: MatchArchive, secondary: MatchArchive): List<PairedReplayAnchor> {
        fun keys(archive: MatchArchive, reverse: Boolean): Map<Pair<String, String>, List<ArchivedRealityArticle>> =
            archive.withQualifiedBattleIdentities().articles.filter { it.monotonicTimeNanos != null }.mapNotNull { a ->
                val p = a.payload as? ArchivedActivePokemonSpeciesWitnessed ?: return@mapNotNull null
                val text = p.ocrText?.trim()?.uppercase()?.replace(Regex("[^A-Z0-9]"), "") ?: return@mapNotNull null
                if (!(text.startsWith("GO") || text.contains("USED")) || p.side !in setOf("PLAYER", "OPPONENT")) return@mapNotNull null
                val side = if (!reverse) p.side else if (p.side == "PLAYER") "OPPONENT" else "PLAYER"
                (side to text) to a
            }.groupBy({ it.first }, { it.second })
        val a = keys(primary, false)
        val b = keys(secondary, true)
        val pStart = start(primary)
        val sStart = start(secondary)
        val proposed = a.flatMap { (key, left) ->
            val right = b[key].orEmpty()
            fun near(x: ArchivedRealityArticle, y: ArchivedRealityArticle) =
                abs((x.monotonicTimeNanos!! - pStart) - (y.monotonicTimeNanos!! - sStart)) <= 1_500_000_000L
            left.mapNotNull { x ->
                val y = right.filter { near(x, it) }.singleOrNull() ?: return@mapNotNull null
                if (left.count { near(it, y) } != 1) null else PairedReplayAnchor(x.articleId, y.articleId)
            }
        }
        // An entry animation may appear much earlier on one client. Keep only
        // the consensus cluster; do not drag an otherwise sound alignment to it.
        val byPrimary = primary.articles.associateBy { it.articleId }
        val bySecondary = secondary.articles.associateBy { it.articleId }
        val offsets = proposed.associateWith { anchor ->
            (byPrimary.getValue(anchor.primaryArticleId).monotonicTimeNanos!! - pStart) -
                (bySecondary.getValue(anchor.secondaryArticleId).monotonicTimeNanos!! - sStart)
        }
        val center = offsets.values.sorted().let { if (it.isEmpty()) return emptyList() else it[it.size / 2] }
        return proposed.filter { abs(offsets.getValue(it) - center) <= MAX_ANCHOR_RESIDUAL }
    }

    fun build(primary: MatchArchive, secondary: MatchArchive, anchors: List<PairedReplayAnchor>): PairedReplayEvidence {
        require(primary.matchId != secondary.matchId)
        require(anchors.size >= 2 && anchors.map { it.primaryArticleId }.distinct().size == anchors.size &&
            anchors.map { it.secondaryArticleId }.distinct().size == anchors.size)
        val pStart = start(primary)
        val sStart = start(secondary)
        val pById = primary.articles.associateBy { it.articleId }
        val sById = secondary.articles.associateBy { it.articleId }
        val offsets = anchors.map { anchor ->
            val p = requireNotNull(pById[anchor.primaryArticleId])
            val s = requireNotNull(sById[anchor.secondaryArticleId])
            require(p.payload::class == s.payload::class) { "Anchor types differ" }
            (requireNotNull(p.monotonicTimeNanos) - pStart) - (requireNotNull(s.monotonicTimeNanos) - sStart)
        }.sorted()
        val offset = offsets[offsets.size / 2]
        val residual = offsets.maxOf { abs(it - offset) }
        require(residual <= MAX_ANCHOR_RESIDUAL) { "Anchor alignment is inconsistent" }
        val left = observations(primary, pStart, 0, false)
        val right = observations(secondary, sStart, offset, true)
        val pCandidates = left.associateWith { p -> right.filter { compatible(p, it, residual) } }
        val sCandidates = right.associateWith { s -> left.filter { compatible(it, s, residual) } }
        val consumed = mutableSetOf<PairedReplayObservation>()
        val events = mutableListOf<PairedReplayEvent>()
        for (p in left) {
            val candidates = pCandidates.getValue(p)
            val s = candidates.singleOrNull()?.takeIf { sCandidates.getValue(it).size == 1 }
            if (s != null) {
                consumed += s
                // Timing and species alone cannot prove that two observations
                // are the same hit; playback must surface this as a candidate.
                events += PairedReplayEvent(listOf(p, s), true)
            } else events += PairedReplayEvent(listOf(p), candidates.isNotEmpty())
        }
        right.filterNot { it in consumed }.forEach { s ->
            events += PairedReplayEvent(listOf(s), sCandidates.getValue(s).isNotEmpty())
        }
        val secondaryRows = secondary.withQualifiedBattleIdentities().articles
        val identities = secondaryRows.mapNotNull { row ->
            val identity = row.payload as? ArchivedActivePokemonSpeciesWitnessed ?: return@mapNotNull null
            val side = when (identity.side) {
                "OPPONENT" -> "PLAYER"
                "PLAYER" -> "OPPONENT"
                else -> return@mapNotNull null
            }
            val refs = row.evidenceReferences.orEmpty().mapNotNull(sById::get)
            val source = when (row.sourceId) {
                "${identity.side}_SPECIES_OVERLAY_PIPELINE" -> refs.singleOrNull {
                    val crop = it.payload as? ArchivedCropCaptured
                    crop?.cropName == (if (side == "PLAYER") "OpponentActiveSpeciesTextCrop" else "PlayerActiveSpeciesTextCrop") &&
                        crop.nativeTeamBadgePresent != false && (crop.nativeTeamBadgePresent == true ||
                        identity.ocrText?.trim().equals(identity.speciesName, true))
                }
                "ENTRY_ANNOUNCEMENT_SPECIES_WITNESS" -> refs.singleOrNull {
                    it.sourceId == "ANNOUNCEMENT_WITNESS" &&
                        (it.payload as? ArchivedRawText)?.value?.trim()?.equals("Go, ${identity.speciesName}!", true) == true
                }
                else -> null
            } ?: return@mapNotNull null
            val captured = source.monotonicTimeNanos?.takeIf { it >= sStart } ?: return@mapNotNull null
            if (row.sourceId == "ENTRY_ANNOUNCEMENT_SPECIES_WITNESS" &&
                archivedEntrySide(identity.speciesName, captured, secondaryRows) != identity.side) return@mapNotNull null
            ReplayIdentityObservation(side, identity.speciesName, identity.speciesId, pStart + captured - sStart + offset,
                "PAIRED BADGE OR ENTRY", sourceId = row.sourceId, confidence = row.confidence, articleId = row.articleId,
                evidenceArticleIds = listOf(secondary.matchId, source.articleId, row.articleId).distinct())
        }
        return PairedReplayEvidence(offset, residual, events.sortedBy { it.observations.minOf { o -> o.offsetNanos } }, identities)
    }

    private fun start(archive: MatchArchive): Long = requireNotNull(archive.articles.singleOrNull {
        it.payload is ArchivedMatchStarted && it.monotonicTimeNanos != null
    }?.monotonicTimeNanos) { "Exactly one timed Match Start is required" }

    private fun observations(archive: MatchArchive, start: Long, shift: Long, reverse: Boolean): List<PairedReplayObservation> {
        val originals = archive.articles.associateBy { it.articleId }
        return archive.withQualifiedBattleIdentities().articles.mapNotNull { a ->
            val at = a.monotonicTimeNanos ?: return@mapNotNull null
            val (side, species, kind, moveName) = when (val p = a.payload) {
                is ArchivedFastMoveUseObserved -> listOf(p.attackingSide, p.attackerSpeciesName, PairedReplayObservation.Kind.FAST_MOVE, null)
                is ArchivedActivePokemonFaintedWitnessed -> listOf(p.side, p.speciesName, PairedReplayObservation.Kind.FAINT, null)
                is ArchivedActivePokemonSpeciesWitnessed -> {
                    val text = p.ocrText?.trim() ?: return@mapNotNull null
                    val prefix = "${p.speciesName} used "
                    if (!text.startsWith(prefix, ignoreCase = true) || !text.endsWith('!')) return@mapNotNull null
                    val move = text.substring(prefix.length, text.length - 1).trim()
                    if (move.isEmpty()) return@mapNotNull null
                    listOf(p.side, p.speciesName, PairedReplayObservation.Kind.CHARGED_MOVE, move)
                }
                else -> return@mapNotNull null
            }
            val rawSide = side as? String ?: return@mapNotNull null
            if (rawSide !in setOf("PLAYER", "OPPONENT")) return@mapNotNull null
            val mapped = if (!reverse) rawSide else if (rawSide == "PLAYER") "OPPONENT" else "PLAYER"
            PairedReplayObservation(archive.matchId, originals.getValue(a.articleId), at - start + shift, mapped, species as String?, kind as PairedReplayObservation.Kind, moveName as String?)
        }
    }

    private fun compatible(a: PairedReplayObservation, b: PairedReplayObservation, residual: Long): Boolean =
        a.kind == b.kind && a.side == b.side && a.species != null && b.species != null &&
            a.species.equals(b.species, ignoreCase = true) &&
            (a.kind != PairedReplayObservation.Kind.CHARGED_MOVE || a.moveName.equals(b.moveName, ignoreCase = true)) &&
            abs(a.offsetNanos - b.offsetNanos) <= MATCH_WINDOW + residual
}
