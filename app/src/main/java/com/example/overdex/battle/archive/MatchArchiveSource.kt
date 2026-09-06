package com.example.overdex.battle.archive

import com.example.overdex.battle.observation.MatchId
import com.example.overdex.battle.reality.RealityTimeline

/**
 * References an existing Match ledger for snapshot export.
 * Does not imply that the Match is complete or persist its contents.
 */
data class MatchArchiveSource(
    val matchId: MatchId,
    val realityTimeline: RealityTimeline
)