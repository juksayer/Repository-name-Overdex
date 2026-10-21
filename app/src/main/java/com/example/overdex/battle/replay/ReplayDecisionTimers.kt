package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*

data class ReplayDecisionTimerState(
    val secondsRemaining: Int?,
    val reason: String?,
    val interpolated: Boolean,
    val sourceArticleIds: List<String>,
    val basis: String = "WITNESSED_TIMER",
    val remainingFraction: Float = 1f,
)

/** Display-only interpolation of witnessed seconds; no fabricated timer Articles. */
internal class ReplayDecisionTimers(articles: List<ArchivedRealityArticle>, knockouts: List<ReplayKnockout>) {
    private data class Period(
        val start: Long, var end: Long, val reason: String?,
        val readings: MutableList<ArchivedRealityArticle> = mutableListOf(),
        val sources: List<String> = emptyList(),
    )
    private val periods: List<Period> = buildList<Period> {
        var current: Period? = null
        var incomingAt: Long? = null
        var playerFaintAt: Long? = null
        var playerSpecies: String? = null
        for (article in articles.sortedBy { it.monotonicTimeNanos }) {
            val at = article.monotonicTimeNanos ?: continue
            if (current?.let { at >= it.end } == true) current = null
            if (isReplayAttackIncomingCue(article)) incomingAt = at
            when (val payload = article.payload) {
                is ArchivedActivePokemonFaintedWitnessed -> if (payload.side == "PLAYER") playerFaintAt = at
                is ArchivedActivePokemonSpeciesWitnessed -> if (payload.side == "PLAYER") {
                    if (payload.speciesName != playerSpecies && current?.reason == "CHOOSE POKÉMON") {
                        current?.end = at
                        current = null
                    }
                    if (payload.speciesName != playerSpecies) playerFaintAt = null
                    playerSpecies = payload.speciesName
                }
                is ArchivedBattleDecisionTimerObserved -> {
                    val seconds = payload.secondsRemaining
                    if (seconds == null) {
                        current?.end = minOf(current!!.end, at)
                        current = null
                    } else if (seconds in 0..15) {
                        if (current == null) {
                            val reason = when {
                                incomingAt?.let { at - it in 0..5_000_000_000L } == true -> "SHIELD?"
                                playerFaintAt?.let { at - it in 0..10_000_000_000L } == true -> "CHOOSE POKÉMON"
                                else -> null
                            }
                            current = Period(at, at + (seconds + 1L) * SECOND, reason).also { add(it) }
                        }
                        current!!.readings += article
                        current!!.end = at + (seconds + 1L) * SECOND
                    }
                }
                is ArchivedChargeMoveUsedAnnounced, is ArchivedMatchEnded -> {
                    current?.end = minOf(current!!.end, at)
                    current = null
                    incomingAt = null
                }
                else -> Unit
            }
        }
        // Legacy archives have no central dial readings. A known replacement
        // pause is still a pause; its duration does not invent numbered seconds.
        knockouts.forEach { faint ->
            val start = faint.presentationAtNanos
            val entry = faint.replacementAtNanos ?: return@forEach
            val ended = articles.firstOrNull { it.payload is ArchivedMatchEnded && it.monotonicTimeNanos!! >= start }
                ?.monotonicTimeNanos ?: entry
            val end = minOf(entry, ended, start + 15 * SECOND)
            if (end > start) add(Period(start, end,
                if (faint.side == "PLAYER") "CHOOSE POKÉMON" else "OPPONENT CHOOSING",
                sources = faint.sourceArticleIds))
        }
    }.sortedBy { it.start }

    val windows: List<LongRange> = periods.map { it.start until it.end }

    fun at(cursor: Long): ReplayDecisionTimerState? {
        val active = periods.filter { cursor in it.start until it.end }
        // Actual numeral readings take precedence where a reconstructed pause overlaps.
        val period = active.lastOrNull { it.readings.isNotEmpty() } ?: active.lastOrNull() ?: return null
        val reading = period.readings.lastOrNull { it.monotonicTimeNanos!! <= cursor }
        val seconds = (reading?.payload as? ArchivedBattleDecisionTimerObserved)?.secondsRemaining
        val elapsed = reading?.let { cursor - it.monotonicTimeNanos!! } ?: 0L
        return ReplayDecisionTimerState(seconds?.minus((elapsed / SECOND).toInt())?.coerceAtLeast(0),
            period.reason, elapsed >= SECOND, (period.readings.map { it.articleId } + period.sources).distinct(),
            basis = if (period.readings.isEmpty()) "RECONSTRUCTED_REPLACEMENT_PAUSE" else "WITNESSED_TIMER",
            remainingFraction = ((period.end - cursor).toDouble() / (period.end - period.start)).toFloat().coerceIn(0f, 1f))
    }

    companion object { private const val SECOND = 1_000_000_000L }
}
