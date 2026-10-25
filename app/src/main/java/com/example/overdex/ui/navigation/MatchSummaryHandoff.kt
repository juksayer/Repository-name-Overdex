package com.example.overdex.ui.navigation

import com.example.overdex.battle.archive.MatchArchiveSource
import com.example.overdex.battle.observation.MatchId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A pending in-process return destination, not a battle event or a foreground launch. */
internal class MatchSummaryHandoff {
    private val _pending = MutableStateFlow<MatchArchiveSource?>(null)
    val pending = _pending.asStateFlow()
    private var lastOfferedMatchId: MatchId? = null

    @Synchronized
    fun offer(source: MatchArchiveSource) {
        if (source.matchId == lastOfferedMatchId) return
        lastOfferedMatchId = source.matchId
        _pending.value = source
    }

    @Synchronized
    fun acknowledge(matchId: MatchId): Boolean {
        if (_pending.value?.matchId != matchId) return false
        _pending.value = null
        return true
    }

    /** Starting another match is an explicit choice to keep recording. */
    @Synchronized
    fun clearForNewMatch() {
        _pending.value = null
    }
}
