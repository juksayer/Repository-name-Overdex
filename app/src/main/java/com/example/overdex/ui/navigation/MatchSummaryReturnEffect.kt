package com.example.overdex.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter

/** Return navigation waits for this activity; it never launches Overdex over Pokémon GO. */
@Composable
internal fun MatchSummaryReturnEffect(
    pending: StateFlow<List<String>>,
    onOpenSummary: (List<String>) -> Unit,
    onHandled: (List<String>) -> Unit,
) {
    val owner = LocalLifecycleOwner.current
    val open by rememberUpdatedState(onOpenSummary)
    val handled by rememberUpdatedState(onHandled)
    LaunchedEffect(owner, pending) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            pending.filter { it.isNotEmpty() }.collect { ids ->
                // This exact batch owns the destination even if another match
                // completes while it is being opened.
                open(ids)
                handled(ids)
            }
        }
    }
}
