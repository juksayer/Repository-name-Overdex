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

    fun rosterSlotsAt(at: Long, includeConfigured: Boolean = true): Map<Int, String> {
        val bySlot = mutableMapOf<Int, String>()
        val configuredIds = if (includeConfigured) recordsAt(at).map { it.articleId }.toSet() else emptySet()
        ordered.takeWhile { it.monotonicTimeNanos!! <= at }.forEach {
            when (val payload = it.payload) {
                ArchivedObservationRestartedByUser, ArchivedScreenIgnoredByUser -> bySlot.clear()
                is ArchivedPlayerTeamRosterConfirmation -> if (payload.confirmed) {
                    bySlot.clear()
                    payload.speciesBySlot.take(3).forEachIndexed { index, species ->
                        if (!species.isNullOrBlank()) bySlot[index + 1] = species
                    }
                }
                is ArchivedPlayerTeamSlotConfigured -> if (it.articleId in configuredIds) bySlot[payload.slot] = payload.speciesName
                is ArchivedPlayerTeamRosterSlotWitnessed -> bySlot[payload.slot] =
                    com.example.overdex.data.PokemonFormReference.mergeRosterName(
                        bySlot[payload.slot], payload.speciesName, payload.spriteVariant != null)
                else -> Unit
            }
        }
        return bySlot
    }

    fun conflictsWithFullRoster(name: String, at: Long): Boolean {
        val roster = rosterSlotsAt(at).values
        val speciesKey = com.example.overdex.battle.observation.TeamRosterSpeciesAttributor::speciesKey
        return roster.size == 3 && roster.none { speciesKey(it) == speciesKey(name) }
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
