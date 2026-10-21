package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.ArchivedRealityArticle
import com.example.overdex.battle.archive.ArchivedStatChangeObserved
import com.example.overdex.battle.archive.ArchivedStatChangeSoundMeasured
import com.example.overdex.battle.archive.ArchivedChargedMoveEnergySpent
import com.example.overdex.battle.audio.StatChangeCue

data class ReplayStatChangeAction(val side: String?, val stat: String, val direction: Int, val progress: Float) {
    val label: String get() = "${if (stat == "ATTACK") "ATK" else "DEF"} ${if (direction > 0) "↑" else "↓"}"
}

/** Buffer the complete record, so late OCR/corrections place the cue at its original sound time. */
internal class ReplayStatChanges(
    articles: List<ArchivedRealityArticle>,
    private val combatantAt: (String, Long) -> ReplayCombatant?,
) {
    private data class Change(val at: Long, val targetAt: Long, val side: String?, val stat: String, val direction: Int)
    private val changes: List<Change>
    init {
        val byId = articles.associateBy { it.articleId }
        val resolutions = articles.filter { it.payload is ArchivedStatChangeObserved }
            .groupBy { (it.payload as ArchivedStatChangeObserved).soundArticleId }
            .mapValues { (_, revisions) -> revisions.maxWith(compareBy<ArchivedRealityArticle> { it.recordedAt }
                .thenBy { it.sequenceNumber ?: 0L }) }
        changes = articles.mapNotNull { article ->
            val sound = article.payload as? ArchivedStatChangeSoundMeasured ?: return@mapNotNull null
            val at = article.monotonicTimeNanos ?: return@mapNotNull null
            val cue = StatChangeCue.entries.firstOrNull { it.name == sound.cue } ?: return@mapNotNull null
            val resolution = resolutions[article.articleId]
            val resolved = resolution?.payload as? ArchivedStatChangeObserved
            // The sound can lag the announcement by seconds. Identify the recipient
            // at the cited move, so a later entrant never inherits the departing one's effect.
            val targetAt = resolution?.evidenceReferences.orEmpty().mapNotNull(byId::get)
                .singleOrNull { it.payload is ArchivedChargedMoveEnergySpent }?.monotonicTimeNanos ?: at
            Change(at, targetAt, resolved?.affectedSide, cue.stat, cue.direction)
        }
    }
    fun at(cursor: Long): List<ReplayStatChangeAction> = changes.mapNotNull { change ->
        val age = cursor - change.at
        if (age !in 0 until DURATION_NANOS) return@mapNotNull null
        change.side?.let { side ->
            // A label must not follow the outgoing Pokemon onto its replacement.
            val then = combatantAt(side, change.targetAt)
            val now = combatantAt(side, cursor)
            if (then?.speciesName != now?.speciesName ||
                then?.identityEstablishedAtNanos != now?.identityEstablishedAtNanos) return@mapNotNull null
        }
        ReplayStatChangeAction(change.side, change.stat, change.direction, age.toFloat() / DURATION_NANOS)
    }
    companion object { const val DURATION_NANOS = 2_000_000_000L }
}
