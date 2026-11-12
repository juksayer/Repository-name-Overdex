package com.example.overdex.ui.screens.observatory

import androidx.compose.runtime.*
import com.example.overdex.battle.archive.MatchArchive
import com.example.overdex.battle.archive.MatchComparisonEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** The route owns a frozen list of IDs. Late evidence refreshes rows, never batch membership. */
@Composable
fun MatchComparisonRoute(
    matchIds: List<String>,
    loadMatch: suspend (String) -> MatchArchive?,
    onOpenMatch: (String) -> Unit,
    onBack: () -> Unit,
    onUp: (() -> Unit) -> Unit,
    onDown: (() -> Unit) -> Unit,
    onLeft: (() -> Unit) -> Unit,
    onRight: (() -> Unit) -> Unit,
    onA: (() -> Unit) -> Unit,
    onB: (() -> Unit) -> Unit,
    onLcdContentUpdate: ((@Composable () -> Unit)?) -> Unit,
) {
    val ids = remember(matchIds) { matchIds.distinct() }
    val loader by rememberUpdatedState(loadMatch)
    var entries by remember(ids) { mutableStateOf(ids.map { MatchComparisonEntry(it, null) }) }
    var loading by remember(ids) { mutableStateOf(true) }
    LaunchedEffect(ids) {
        while (isActive) {
            val previous = entries.associateBy { it.matchId }
            entries = withContext(Dispatchers.IO) {
                ids.map { id ->
                    try {
                        val archive = loader(id)
                        MatchComparisonEntry(id, archive, if (archive == null) "Waiting for this match record." else null)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        MatchComparisonEntry(id, previous[id]?.archive, "Unable to refresh this match record.")
                    }
                }
            }
            loading = false
            delay(1_000)
        }
    }
    MatchComparisonScreen(entries, loading, onOpenMatch, onBack,
        onUp, onDown, onLeft, onRight, onA, onB, onLcdContentUpdate)
}
