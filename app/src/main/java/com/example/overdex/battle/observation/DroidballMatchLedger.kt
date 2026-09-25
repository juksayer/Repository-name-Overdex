package com.example.overdex.battle.observation

import com.example.overdex.battle.archive.MatchArchiveSource

/**
 * Owns the immutable match archives accumulated during one deployed Droidball field session.
 * Completing a match appends its source; it never replaces or mutates prior entries.
 */
class DroidballMatchLedger {
    private val completed = mutableListOf<MatchArchiveSource>()
    fun complete(source: MatchArchiveSource) {
        require(completed.none { it.matchId == source.matchId }) { "A match may be completed once." }
        completed += source
    }
    fun completedMatches(): List<MatchArchiveSource> = completed.toList()
}
