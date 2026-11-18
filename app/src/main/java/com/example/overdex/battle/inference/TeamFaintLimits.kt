package com.example.overdex.battle.inference

import com.example.overdex.model.BattleResult

/** Limits on named faint decisions. Counts constrain the past only, up to capture time. */
internal class TeamFaintLimits(private val player: Boolean) {
    private var bounds = emptyMap<Int, LongRange>()
    private val results = mutableSetOf<BattleResult>()

    fun result(result: BattleResult) = result != BattleResult.UNKNOWN && results.add(result)

    fun updateCounts(counts: List<TeamLossObservation.Count>, invalidTimes: Set<Long> = emptySet()) {
        val invalid = invalidTimes.toMutableSet()
        val clean = counts.groupBy { it.articleId }.values.mapNotNull { copies ->
            copies.distinct().singleOrNull() ?: run { invalid += copies.map { it.atNanos }; null }
        }
        val latest = mutableMapOf<Int, LongRange>()
        var remaining = 3
        for ((at, rows) in clean.groupBy { it.atNanos }.toSortedMap()) {
            val count = rows.map { it.count }.distinct().singleOrNull() ?: continue
            if (at in invalid || count !in 0..remaining) continue
            latest[count] = (latest[count]?.first ?: at)..at
            remaining = count
        }
        bounds = latest
    }

    fun permits(previous: Collection<Long>, at: Long, publishing: Boolean = false): Boolean {
        val losingResult = if (player) BattleResult.LOSS else BattleResult.WIN
        val result = results.singleOrNull()
        // While a winner is unknown, reserve the last signal. Outcome alone has no victim.
        val limit = if (result == null || result == losingResult || result == BattleResult.DRAW) 3 else 2
        if (publishing && previous.size == 2 && (result == null || results.size != 1)) return false
        return previous.size < limit && bounds.all { (remaining, seen) ->
            val pastFits = at > seen.last || previous.count { it <= seen.last } < 3 - remaining
            val futureFits = at <= seen.first || 3 - remaining < limit
            pastFits && futureFits && !(at in seen && remaining == 0 && limit == 2)
        }
    }

    fun <T> select(items: List<T>, occupied: Collection<Long> = emptyList(), time: (T) -> Long): List<T> {
        val accepted = mutableListOf<T>()
        val times = occupied.toMutableList()
        for ((at, simultaneous) in items.groupBy(time).toSortedMap()) {
            // An aggregate supplies no ordering within the interval.
            if (!permits(times + List(simultaneous.size - 1) { at }, at)) continue
            accepted += simultaneous
            repeat(simultaneous.size) { times += at }
        }
        return accepted
    }
}
