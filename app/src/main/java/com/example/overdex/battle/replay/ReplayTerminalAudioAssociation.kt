package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*

/** Retrospective inference, never a replacement for a measured empty HP bar or raw faint. */
internal object ReplayTerminalAudioAssociation {
    const val BASIS = "RECOVERED_CHARGE_AND_PARTIAL_CRY_WITH_LAST_SURVIVOR_AND_RESULT"
    private const val S = 1_000_000_000L
    fun resolve(articles: List<ArchivedRealityArticle>, recovery: ReplayAudioRecovery,
                charges: List<ReplayFinishingChargeCandidate>, fast: List<ReplayFastMoveCycle>,
                actorAt: (String, Long) -> ReplayCombatant?, appearanceAt: (String, Long) -> Long,
                nextAppearance: (String, Long) -> Long?, acceptsHp: (ArchivedRealityArticle) -> Boolean,
                missingReferencesAt: (Long) -> List<String> = { recovery.unavailableReferences }): List<ReplayFinishingChargeAssociation> {
        if (recovery.unavailableArtifacts.isNotEmpty()) return emptyList()
        val ordered = articles.filter { it.monotonicTimeNanos != null }.sortedWith(compareBy<ArchivedRealityArticle> { it.monotonicTimeNanos }.thenBy { it.articleId })
        val results = ordered.filter { it.payload is ArchivedMatchEnded }
        val result = results.firstOrNull() ?: return emptyList()
        // Initially scoped to a native win and the opponent's last survivor. Loss/tie have other ambiguities.
        if (results.any { (it.payload as ArchivedMatchEnded).result != "WIN" }) return emptyList()
        val end = result.monotonicTimeNanos!!
        val claims = ReplayPossibleDamageClaims.collect(ordered)
        return recovery.charges.mapNotNull { recovered ->
            val at = recovered.estimatedOnsetNanos
            if (missingReferencesAt(at).isNotEmpty()) return@mapNotNull null
            val charge = charges.singleOrNull { it.sourceAtNanos == at && it.moveName == recovered.moveName && it.side == "PLAYER" && it.completed }
                ?: return@mapNotNull null
            val player = actorAt("PLAYER", at) ?: return@mapNotNull null
            val target = actorAt("OPPONENT", at) ?: return@mapNotNull null
            if (player.speciesId == null || target.speciesId == null || player.speciesId == target.speciesId || player.isFainted || target.isFainted) return@mapNotNull null
            val cry = recovery.cries.singleOrNull { it.speciesId == target.speciesId &&
                it.estimatedOnsetNanos - at in recovered.referenceDurationNanos..(8*S) && end - it.estimatedOnsetNanos in 0..(30*S) }
                ?: return@mapNotNull null
            val cryAt = cry.estimatedOnsetNanos
            if (recovery.cries.any { it !== cry && it.estimatedOnsetNanos in at..end }) return@mapNotNull null
            if (nextAppearance("PLAYER", at)?.let { it <= end } == true || nextAppearance("OPPONENT", at)?.let { it <= end } == true) return@mapNotNull null
            if (charges.any { it !== charge && it.sourceAtNanos in (at - 5*S)..end }) return@mapNotNull null
            if (recovery.fastEffects.any { it.estimatedOnsetNanos in (at - 5*S)..end }) return@mapNotNull null
            // Include pending uses before the charge, original clocks, unnamed uses and schedule exclusions.
            if (fast.any { it.impactAtNanos in (at - 5*S)..end || it.witness.sourceTimesNanos.any { t -> t in (at - 5*S)..end } }) return@mapNotNull null
            val fragments = recovered.fragments + cry.fragments
            val explainedAudio = fragments.map { it.articleId }.toSet()
            if (claims.any { claim ->
                if (claim.atNanos !in (at - 5*S)..end) false else {
                    val a = ordered.first { it.articleId == claim.articleId }
                    val sound = a.payload as? ArchivedFastMoveSoundMeasured
                    // Unclassified sound in the very same original window is explained by the recovered
                    // effect/cry. A separately accepted Fast identity remains a competing claim.
                    val soundAt = sound?.soundOnsetMonotonicNanos ?: sound?.onsetOffsetNanos?.let { a.monotonicTimeNanos!! + it }
                    val explained = sound != null && !sound.referenceMatchAccepted && soundAt != null &&
                        !a.evidenceReferences.isNullOrEmpty() && a.evidenceReferences.all { it in explainedAudio } &&
                        fragments.any { f -> f.articleId in a.evidenceReferences && soundAt in
                            (f.captureAtNanos + f.queryOffsetNanos) until (f.captureAtNanos + f.queryOffsetNanos + f.durationNanos) }
                    !explained &&
                        claim.articleId !in charge.articleIds
                }
            }) return@mapNotNull null
            val count = ordered.lastOrNull { a -> a.monotonicTimeNanos!! <= at && (a.payload as? ArchivedOpponentBattleResourceCountMeasured)?.let {
                it.resource == "POKE_BALLS" && it.maximumCount == 3 } == true } ?: return@mapNotNull null
            if ((count.payload as ArchivedOpponentBattleResourceCountMeasured).visibleCount != 1 || at - count.monotonicTimeNanos!! > 40*S) return@mapNotNull null
            val shields = ordered.lastOrNull { a -> a.monotonicTimeNanos!! <= at && (a.payload as? ArchivedOpponentBattleResourceCountMeasured)?.let {
                it.resource == "SHIELDS" && it.maximumCount == 2 } == true } ?: return@mapNotNull null
            if ((shields.payload as ArchivedOpponentBattleResourceCountMeasured).visibleCount != 0 || at - shields.monotonicTimeNanos!! > 40*S) return@mapNotNull null
            if (ordered.any { a -> (a.monotonicTimeNanos == count.monotonicTimeNanos &&
                    (a.payload as? ArchivedOpponentBattleResourceCountMeasured)?.let { it.resource == "POKE_BALLS" && it.visibleCount != 1 } == true) ||
                    (a.monotonicTimeNanos == shields.monotonicTimeNanos &&
                    (a.payload as? ArchivedOpponentBattleResourceCountMeasured)?.let { it.resource == "SHIELDS" && it.visibleCount != 0 } == true) }) return@mapNotNull null
            // An unclassified positive reading is not confirmed survival, but it
            // leaves enough uncertainty to withhold this decisive causal link.
            if (ordered.any { a -> a.monotonicTimeNanos!! in at..end && when (val p = a.payload) {
                    is ArchivedActiveHpBarMeasured -> p.side == "OPPONENT" && p.filledFraction.isFinite() &&
                        p.filledFraction > 0f && p.filledFraction <= 1f && a.monotonicTimeNanos >= cryAt &&
                        (acceptsHp(a) || (p.fillColor == null && p.sourceWidth != null && p.sourceHeight != null &&
                            p.barLeft >= 0 && p.barTop >= 0 && p.barRight > p.barLeft && p.barBottom > p.barTop &&
                            p.barRight <= p.sourceWidth && p.barBottom < p.sourceHeight))
                    is ArchivedOpponentBattleResourceCountMeasured -> (p.resource == "SHIELDS" && p.visibleCount != 0) || (p.resource == "POKE_BALLS" && p.visibleCount !in 0..1)
                    is ArchivedActivePokemonFaintedWitnessed, is ArchivedGetReadyWitnessed, is ArchivedAttackIncoming,
                    is ArchivedObservationRestartedByUser, is ArchivedObservationSessionStoppedByUser, is ArchivedScreenIgnoredByUser -> true
                    else -> false
                } }) return@mapNotNull null
            val identities = ordered.filter { a ->
                (a.payload is ArchivedActivePokemonSpeciesWitnessed || a.payload is ArchivedPlayerActiveIdentityResolved) &&
                    a.monotonicTimeNanos!! in minOf(appearanceAt("PLAYER", at), appearanceAt("OPPONENT", at))..at
            }.map { it.articleId }
            ReplayFinishingChargeAssociation("PLAYER", player.speciesName, appearanceAt("PLAYER", at),
                "OPPONENT", target.speciesName, appearanceAt("OPPONENT", at), recovered.moveName,
                at, cry.fragments.minOf { it.captureAtNanos }, cryAt, null,
                recovered.fragments.map { it.articleId }, cry.fragments.map { it.articleId } + listOf(count.articleId, result.articleId) + identities,
                listOf(shields.articleId), BASIS)
        }
    }
}
