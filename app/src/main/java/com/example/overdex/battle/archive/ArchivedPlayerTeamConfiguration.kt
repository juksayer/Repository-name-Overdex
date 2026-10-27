package com.example.overdex.battle.archive

/**
 * Roster confirmation identifies the three species, not their equipment or owned
 * cards. A previous saved team remains testimony, but cannot equip that roster.
 * Compare source time, so a delayed older configuration cannot restore it.
 */
internal class ArchivedPlayerTeamConfiguration(articles: List<ArchivedRealityArticle>) {
    private val ordered = articles.filter { it.monotonicTimeNanos != null }
        .sortedWith(compareBy({ it.monotonicTimeNanos }, { it.sequenceNumber ?: 0L }))
    private val configurations = ordered.filter { it.payload is ArchivedPlayerTeamSlotConfigured }
    private val confirmations = ordered.filter {
        (it.payload as? ArchivedPlayerTeamRosterConfirmation)?.confirmed == true
    }
    private val byId = ordered.associateBy { it.articleId }
    private val acceptedDerivedEvidence = mutableMapOf<String, Boolean>()
    val battleSetupAt: Long = ordered.firstOrNull { it.payload is ArchivedMatchStarted }
        ?.monotonicTimeNanos ?: Long.MAX_VALUE

    fun latestConfirmationAt(at: Long): Long? = confirmations.lastOrNull {
        it.monotonicTimeNanos!! <= at
    }?.monotonicTimeNanos

    fun recordsAt(at: Long = battleSetupAt): List<ArchivedRealityArticle> {
        val boundary = latestConfirmationAt(at)
        return configurations.filter {
            it.monotonicTimeNanos!! <= at && (boundary == null || it.monotonicTimeNanos > boundary)
        }.associateBy { (it.payload as ArchivedPlayerTeamSlotConfigured).slot }.values
            .sortedBy { (it.payload as ArchivedPlayerTeamSlotConfigured).slot }
    }

    /** Old archives can already contain conclusions made from the stale setup. */
    fun acceptsConfiguredMove(article: ArchivedRealityArticle, at: Long = battleSetupAt): Boolean {
        val move = article.payload as? ArchivedFastMoveIdentified ?: return true
        if (move.side != "PLAYER" || !move.basis.startsWith("CONFIGURED_PLAYER_TEAM")) return true
        val boundary = latestConfirmationAt(at) ?: return true // Preserve legacy recordings without this boundary.
        if ((article.monotonicTimeNanos ?: Long.MIN_VALUE) <= boundary) return false
        val current = recordsAt(at)
        val namedSources = article.predecessorIds.mapNotNull(byId::get)
            .filter { it.payload is ArchivedPlayerTeamSlotConfigured }
        if (namedSources.any { old -> current.none { it.articleId == old.articleId } }) return false
        return current.any {
            val configured = it.payload as ArchivedPlayerTeamSlotConfigured
            it.monotonicTimeNanos!! <= article.monotonicTimeNanos!! &&
                key(configured.speciesName) == key(move.speciesName) && key(configured.fastMoveName) == key(move.moveName)
        }
    }

    /** Reject stale configuration lineage only in the projection; retain every source article. */
    fun acceptsDerivedEvidence(article: ArchivedRealityArticle): Boolean =
        acceptedDerivedEvidence.getOrPut(article.articleId) {
            val boundary = latestConfirmationAt(battleSetupAt) ?: return@getOrPut true
            val currentIds = recordsAt().map { it.articleId }.toSet()
            val pending = ArrayDeque<ArchivedRealityArticle>().apply { add(article) }
            val visited = mutableSetOf<String>()
            while (pending.isNotEmpty()) {
                val candidate = pending.removeFirst()
                if (!visited.add(candidate.articleId)) continue
                if (candidate.payload is ArchivedPlayerTeamSlotConfigured &&
                    candidate.monotonicTimeNanos!! <= boundary && candidate.articleId !in currentIds) return@getOrPut false
                if (!acceptsConfiguredMove(candidate)) return@getOrPut false
                candidate.predecessorIds.mapNotNull(byId::get).forEach(pending::addLast)
            }
            true
        }

    private fun key(value: String) = value.uppercase().filter(Char::isLetterOrDigit)
}
