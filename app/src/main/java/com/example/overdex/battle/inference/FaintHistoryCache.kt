package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.ActivePokemonSide

/**
 * Match-owned memoization of pure resolvers. Keys retain the complete, ordered
 * immutable observation values, not just IDs, sizes, or a hash. Each consumer
 * still owns its history, filters, conflicts, source references and recovery state.
 */
class FaintHistoryCache(
    private val maxEntries: Int = 16,
    private val maxRows: Int = 8_192,
) {
    init { require(maxEntries > 0); require(maxRows > 0) }
    private enum class Kind { TEAM_LOSS, VERIFIED_HP, TERMINAL_CHARGE }
    private data class Key(val kind: Kind, val side: ActivePokemonSide, val rows: List<*>)
    data class Statistics(val hits: Long, val misses: Long, val oversized: Long,
        val evictions: Long, val entries: Int, val retainedRows: Int)
    private val entries = LinkedHashMap<Key, Any>(16, .75f, true)
    private var retainedRows = 0
    private var hits = 0L
    private var misses = 0L
    private var oversized = 0L
    private var evictions = 0L

    internal fun team(side: ActivePokemonSide, rows: List<TeamLossObservation>,
        compute: () -> List<TeamLossResolution>): List<TeamLossResolution> =
        resolve(Kind.TEAM_LOSS, side, rows, compute)

    internal fun hp(side: ActivePokemonSide, rows: List<VerifiedHpFaintObservation>,
        compute: () -> VerifiedHpFaintReconciliation): VerifiedHpFaintReconciliation =
        resolve(Kind.VERIFIED_HP, side, rows, compute)

    internal fun terminal(side: ActivePokemonSide, rows: List<TerminalFinishRecord>,
        compute: () -> List<TerminalChargedFaintResolution>): List<TerminalChargedFaintResolution> =
        resolve(Kind.TERMINAL_CHARGE, side, rows, compute)

    @Synchronized
    private fun <R : Any> resolve(kind: Kind, side: ActivePokemonSide, rows: List<*>, compute: () -> R): R {
        if (rows.size > maxRows) { oversized++; misses++; return compute() }
        val key = Key(kind, side, rows.toList())
        @Suppress("UNCHECKED_CAST")
        entries[key]?.let { hits++; return it as R }
        misses++
        val result = compute() // An exception is never cached.
        while (entries.size >= maxEntries || retainedRows + rows.size > maxRows) {
            val oldest = entries.entries.iterator()
            val evicted = oldest.next()
            retainedRows -= evicted.key.rows.size
            oldest.remove(); evictions++
        }
        entries[key] = result
        retainedRows += rows.size
        return result
    }

    @Synchronized fun snapshot() = Statistics(hits, misses, oversized, evictions, entries.size, retainedRows)
    @Synchronized fun clear() { entries.clear(); retainedRows = 0 }
}
