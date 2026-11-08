package com.example.overdex.battle.archive

import com.example.overdex.battle.custody.*
import com.example.overdex.battle.observation.MatchId
import com.example.overdex.battle.reality.RealityArticle

/** Late interpreted evidence can finish after the result's first archive snapshot. */
internal fun shouldRefreshCompletedMatchArchive(article: RealityArticle, completedMatchId: MatchId): Boolean {
    if (article.matchId != completedMatchId) return false
    return when (val payload = article.payload) {
        is RawTestimony -> article.sourceId.id in COMPLETED_MATCH_TEXT_SOURCES &&
            (payload.data as? String)?.isNotBlank() == true
        GetReadyWitnessed, AttackIncoming, ChargeMoveUsedAnnounced,
        is ChargeMoveQteVibrationPatternInferred, is ChargedMoveEnergySpent,
        is ActivePokemonSpeciesWitnessed, is PlayerActiveIdentityResolved, is ActivePokemonFaintedWitnessed,
        is FastMoveUseObserved, is FastMoveIdentified, is FastMoveEnergyDerived,
        is StatChangeObserved, is MatchResultMusicMeasured, is MatchResultMusicCompared -> true
        else -> false
    }
}

private val COMPLETED_MATCH_TEXT_SOURCES = setOf("ANNOUNCEMENT_WITNESS", "QTE_PROGRESS_TEXT_WITNESS")
