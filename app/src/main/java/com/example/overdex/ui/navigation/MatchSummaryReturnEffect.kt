package com.example.overdex.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.overdex.battle.archive.MatchArchiveSource
import com.example.overdex.battle.observation.MatchId
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull

/** Return navigation waits for this activity; it never launches Overdex over Pokémon GO. */
@Composable
internal fun MatchSummaryReturnEffect(
    pending: StateFlow<MatchArchiveSource?>,
    onOpenSummary: (MatchArchiveSource) -> Unit,
    onHandled: (MatchId) -> Unit,
) {
    val owner = LocalLifecycleOwner.current
    val open by rememberUpdatedState(onOpenSummary)
    val handled by rememberUpdatedState(onHandled)
    LaunchedEffect(owner, pending) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            pending.filterNotNull().collect { source ->
                open(source)
                handled(source.matchId)
            }
        }
    }
}
