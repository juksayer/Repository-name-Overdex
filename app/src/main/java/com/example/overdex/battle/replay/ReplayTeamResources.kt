package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*

/** Null means no count was recorded; a hidden badge must not look like zero. */
data class ReplayTeamResources(val pokeBalls: Int? = null, val shields: Int? = null)

internal class ReplayResourceTrack(
    articles: List<ArchivedRealityArticle>,
    private val side: String,
    knockouts: List<ReplayKnockout> = emptyList(),
) {
    // A completed archive can fit a directly resource-backed faint before its
    // replacement. Present that same proven count with the fitted fall, rather
    // than briefly resurrecting a ball on the incoming Pokemon's badge. This
    // never guesses a starting count or subtracts an extra ball from a reading.
    private val fittedCounts = knockouts.filter { it.side == side && it.verifiedPokeBallsAfter != null }
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
        for (knockout in fittedCounts) {
            if (cursor >= knockout.verifiedPokeBallsAtNanos) {
                balls = minOf(balls ?: 3, knockout.verifiedPokeBallsAfter!!)
            }
        }
        return ReplayTeamResources(balls, shields)
    }
}
