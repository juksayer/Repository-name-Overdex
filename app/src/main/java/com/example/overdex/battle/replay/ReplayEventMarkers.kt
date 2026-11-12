package com.example.overdex.battle.replay

import com.example.overdex.battle.BattleSpeciesDisplay
import com.example.overdex.battle.archive.ArchivedOpponentBattleResourceCountMeasured
import com.example.overdex.battle.archive.ArchivedPlayerShieldCountMeasured
import com.example.overdex.battle.archive.ArchivedRealityArticle

/** A seek destination in the completed replay; it never adds an archived battle event. */
data class ReplayEventMarker(
    val id: String,
    val kind: ReplayEventMarkerKind,
    val atNanos: Long,
    val side: String?,
    val label: String,
    /** The unchanged event clock when presentation has fitted an animation before a replacement. */
    val sourceAtNanos: Long = atNanos,
    val sourceArticleIds: List<String> = emptyList(),
) {
    val sideLabel: String get() = when (side) {
        "PLAYER" -> "YOU"
        "OPPONENT" -> "OPP"
        else -> if (kind == ReplayEventMarkerKind.MATCH_START) "" else "SIDE ?"
    }
    val description: String get() = listOf(sideLabel, label).filter(String::isNotBlank).joinToString(" · ")
}

enum class ReplayEventMarkerKind(val shortLabel: String) {
    MATCH_START("GO"), CHARGED_MOVE("CM"), FAINT("KO"),
    BUFF("↑"), DEBUFF("↓"), ACTIVE_CHANGE("↔"), SHIELD("SH"),
}

/** Pure cursor navigation also keeps different events at the same instant reachable. */
object ReplayEventMarkerNavigation {
    fun currentIndex(markers: List<ReplayEventMarker>, cursor: Long, selectedId: String?): Int {
        val selected = markers.indexOfFirst { it.id == selectedId && it.atNanos == cursor }
        return if (selected >= 0) selected else markers.indexOfLast { it.atNanos <= cursor }
    }

    fun previousIndex(markers: List<ReplayEventMarker>, cursor: Long, selectedId: String?): Int? {
        val current = currentIndex(markers, cursor, selectedId)
        return (if (markers.getOrNull(current)?.atNanos == cursor) current - 1
            else markers.indexOfLast { it.atNanos < cursor })
            .takeIf { it in markers.indices }
    }

    fun nextIndex(markers: List<ReplayEventMarker>, cursor: Long, selectedId: String?): Int? {
        val selected = markers.indexOfFirst { it.id == selectedId && it.atNanos == cursor }
        return (if (selected >= 0) selected + 1 else markers.indexOfFirst { it.atNanos > cursor })
            .takeIf { it in markers.indices }
    }
}

internal object ReplayEventMarkers {
    fun activeChanges(
        appearancesBySide: Map<String, List<ReplayIdentityObservation>>,
        knockouts: List<ReplayKnockout>,
        articlesById: Map<String, ArchivedRealityArticle> = emptyMap(),
    ): List<ReplayEventMarker> = appearancesBySide.flatMap { (side, appearances) ->
        // The lead's first recognition is an identity, not a switch into battle.
        appearances.drop(1).map { appearance ->
            val afterKo = knockouts.any { it.side == side && it.replacementAtNanos == appearance.atNanos }
            ReplayEventMarker(
                id = "entry:$side:${appearance.atNanos}",
                kind = ReplayEventMarkerKind.ACTIVE_CHANGE,
                atNanos = appearance.atNanos,
                side = side,
                // An entry without a known faint cannot establish why the previous actor left.
                label = "${if (afterKo) "Send in" else "Active change"} → ${BattleSpeciesDisplay.name(appearance.speciesName)}",
                sourceAtNanos = appearance.articleId?.let(articlesById::get)?.monotonicTimeNanos ?: appearance.atNanos,
                sourceArticleIds = (listOfNotNull(appearance.articleId) + appearance.evidenceArticleIds).distinct(),
            )
        }
    }

    fun shields(articles: List<ArchivedRealityArticle>): List<ReplayEventMarker> = buildList {
        val previousBySide = mutableMapOf<String, Pair<Int, String>>()
        articles.sortedBy { it.monotonicTimeNanos }.forEach { article ->
            val at = article.monotonicTimeNanos ?: return@forEach
            val (side, count) = when (val payload = article.payload) {
                is ArchivedPlayerShieldCountMeasured -> "PLAYER" to payload.visibleCount
                is ArchivedOpponentBattleResourceCountMeasured -> {
                    if (payload.resource != "SHIELDS") return@forEach
                    "OPPONENT" to payload.visibleCount
                }
                else -> return@forEach
            }
            if (count !in 0..2) return@forEach
            val previous = previousBySide[side]
            if (previous == null || count <= previous.first) {
                previousBySide[side] = count to article.articleId
            }
            if (previous == null || count >= previous.first) return@forEach
            val used = previous.first - count
            add(ReplayEventMarker("shield:${article.articleId}", ReplayEventMarkerKind.SHIELD, at, side,
                if (used == 1) "Shield used" else "$used shields used · exact instants unknown",
                sourceArticleIds = listOf(previous.second, article.articleId)))
        }
    }
}
