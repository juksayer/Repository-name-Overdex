package com.example.overdex.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/** Durable review membership only; archived evidence is stored separately by Match ID. */
@Serializable
internal data class MatchSummaryInboxState(
    val pendingIds: List<String> = emptyList(),
    val deliveredIds: List<String> = emptyList(),
    val seenIds: List<String> = emptyList(),
    val revision: Long = 0L,
)

/** A return batch, not a battle event or a request to launch over Pokémon GO. No disk I/O. */
internal class MatchSummaryHandoff(initialState: MatchSummaryInboxState = MatchSummaryInboxState()) {
    private val _pending = MutableStateFlow<List<String>>(emptyList())
    val pending = _pending.asStateFlow()
    private val _delivered = MutableStateFlow<List<String>>(emptyList())
    val delivered = _delivered.asStateFlow()
    private val seen = linkedSetOf<String>()
    private var revision = 0L

    init { restore(initialState) }

    @Synchronized
    fun offer(matchId: String): Boolean {
        require(matchId.isNotBlank())
        if (!seen.add(matchId)) return false
        _pending.value = _pending.value + matchId
        revision++
        return true
    }

    /** Remove only the frozen, displayed IDs; a completion during navigation stays pending. */
    @Synchronized
    fun acknowledge(displayedIds: Collection<String>): Boolean {
        val displayed = displayedIds.distinct().filter { it in seen }
        val removed = displayed.toSet().intersect(_pending.value.toSet())
        if (removed.isEmpty()) return false
        _pending.value = _pending.value.filterNot { it in removed }
        _delivered.value = displayed
        revision++
        return true
    }

    /** A next-match request keeps every unreviewed completion in this batch. */
    fun clearForNewMatch() = Unit

    @Synchronized
    fun snapshotState(): MatchSummaryInboxState = MatchSummaryInboxState(
        _pending.value.toList(), _delivered.value.toList(), seen.toList(), revision,
    )

    /** Merge a delayed disk read without losing newer local completions or acknowledgments. */
    @Synchronized
    fun restore(state: MatchSummaryInboxState): Boolean {
        fun clean(ids: List<String>) = ids.filter(String::isNotBlank).distinct()
        val diskPending = clean(state.pendingIds)
        val diskDelivered = clean(state.deliveredIds)
        val diskSeen = (clean(state.seenIds) + diskPending + diskDelivered).toSet()
        val localAcknowledged = seen - _pending.value.toSet()
        val diskAcknowledged = diskSeen - diskPending.toSet()
        val pending = (diskPending.filterNot { it in localAcknowledged } +
            _pending.value.filterNot { it in diskAcknowledged }).distinct()
        val delivered = _delivered.value.ifEmpty { diskDelivered }
        val allSeen = linkedSetOf<String>().apply { addAll(diskSeen); addAll(seen) }
        val changed = pending != _pending.value || delivered != _delivered.value || allSeen != seen
        seen.clear()
        seen.addAll(allSeen)
        _pending.value = pending
        _delivered.value = delivered
        revision = maxOf(revision, state.revision.coerceAtLeast(0L)) + if (changed) 1L else 0L
        return changed
    }
}
