package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*

/** Null means no count was recorded; a hidden badge must not look like zero. */
data class ReplayTeamResources(val pokeBalls: Int? = null, val shields: Int? = null)

internal class ReplayResourceTrack(articles: List<ArchivedRealityArticle>, private val side: String) {
    private val measurements = articles.filter {
        it.payload is ArchivedPlayerPokeBallCountMeasured || it.payload is ArchivedPlayerShieldCountMeasured ||
            it.payload is ArchivedOpponentBattleResourceCountMeasured
    }.sortedBy { it.monotonicTimeNanos }

    fun stateAt(cursor: Long): ReplayTeamResources {
        var balls: Int? = null
        var shields: Int? = null
        for (article in measurements) {
            val at = article.monotonicTimeNanos ?: continue
            if (at > cursor) break
            when (val p = article.payload) {
                is ArchivedPlayerPokeBallCountMeasured -> if (side == "PLAYER" && p.visibleCount in 0..3) {
                    balls = minOf(balls ?: 3, p.visibleCount)
                }
                is ArchivedPlayerShieldCountMeasured -> if (side == "PLAYER" && p.visibleCount in 0..2) {
                    shields = minOf(shields ?: 2, p.visibleCount)
                }
                is ArchivedOpponentBattleResourceCountMeasured -> if (side == "OPPONENT") {
                    when (p.resource) {
                        "POKE_BALLS" -> if (p.visibleCount in 0..3) balls = minOf(balls ?: 3, p.visibleCount)
                        "SHIELDS" -> if (p.visibleCount in 0..2) shields = minOf(shields ?: 2, p.visibleCount)
                    }
                }
                else -> Unit
            }
        }
        return ReplayTeamResources(balls, shields)
    }
}
