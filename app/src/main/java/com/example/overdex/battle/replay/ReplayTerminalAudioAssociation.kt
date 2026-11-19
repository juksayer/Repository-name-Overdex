package com.example.overdex.battle.replay

import com.example.overdex.battle.BattleSpeciesDisplay
import com.example.overdex.battle.archive.*
import com.example.overdex.battle.inference.TerminalCollapseSequences
import com.example.overdex.battle.inference.TerminalEffectFrame

/** Retrospective inference, never a replacement for a measured empty HP bar or raw faint. */
internal object ReplayTerminalAudioAssociation {
    const val BASIS = "RECOVERED_CHARGE_AND_PARTIAL_CRY_WITH_LAST_SURVIVOR_AND_RESULT"
    const val ANNOUNCED = "ANNOUNCED_CHARGE_AND_LAST_OPPONENT_DEPARTURE_CRY"

    const val ANNOUNCED_PARTIAL = "ANNOUNCED_CHARGE_AND_PARTIAL_CRY_DURING_RECIPIENT_COLLAPSE"

    fun announcedFinish(
        rows: List<ArchivedRealityArticle>,
        recovery: ReplayAudioRecovery,
        charges: List<ReplayFinishingChargeCandidate>,
        actorAt: (String, Long) -> ReplayCombatant?,
        appearanceAt: (String, Long) -> Long,
        nextAppearance: (String, Long) -> Long?,
        witnessedDamagedBar: (ArchivedRealityArticle, String, Long) -> Boolean = { _, _, _ -> false },
    ): ReplayFinishingChargeAssociation? {
        if (recovery.terminalWholeCries.isEmpty() && recovery.partialCryCandidates.isEmpty()) return null
        val ordered = rows.filter { it.monotonicTimeNanos != null }
            .sortedWith(compareBy<ArchivedRealityArticle> { it.monotonicTimeNanos }.thenBy { it.articleId })
        val results = ordered.filter { it.payload is ArchivedMatchEnded }
        if (results.any { (it.payload as ArchivedMatchEnded).result != "WIN" }) return null
        val result = results.firstOrNull() ?: return null
        val end = result.monotonicTimeNanos!!
        val wholeCries = recovery.terminalWholeCries.filter { end - it.estimatedOnsetNanos in 0..20 * S }
        if (wholeCries.size > 1) return null
        val cry = wholeCries.singleOrNull() ?: recovery.partialCryCandidates.singleOrNull { candidate ->
            end - candidate.estimatedOnsetNanos in 0..20 * S && charges.any {
                candidate.estimatedOnsetNanos - it.sourceAtNanos in 0..5 * S
            }
        } ?: return null
        val at = cry.estimatedOnsetNanos
        val charge = charges.singleOrNull {
            it.completed && it.uniquelyOwned && it.side == "PLAYER" && at - it.sourceAtNanos in 0..5 * S
        } ?: return null
        val start = charge.sourceAtNanos
        val owner = actorAt("PLAYER", start) ?: return null
        val target = actorAt("OPPONENT", start) ?: return null
        if (owner.isFainted || target.isFainted || owner.speciesId == null ||
            target.speciesId != cry.speciesId || owner.speciesId == target.speciesId) return null
        if (listOf("PLAYER", "OPPONENT").any { side -> nextAppearance(side, start)?.let { it <= end } == true }) return null
        val named = ordered.singleOrNull { row ->
            val move = row.payload as? ArchivedChargedMoveEnergySpent
            row.articleId in charge.articleIds && move?.side == "PLAYER" &&
                move.moveName == charge.moveName && BattleSpeciesDisplay.name(move.speciesName) == BattleSpeciesDisplay.name(owner.speciesName)
        } ?: return null
        val badges = ordered.filter { row ->
            val count = row.payload as? ArchivedOpponentBattleResourceCountMeasured
            row.sourceId == "OPPONENT_POKE_BALLS_WITNESS" && count?.resource == "POKE_BALLS" && count.maximumCount == 3
        }
        val badge = badges.lastOrNull { it.monotonicTimeNanos!! <= start } ?: return null
        if ((badge.payload as ArchivedOpponentBattleResourceCountMeasured).visibleCount != 1 ||
            badges.any { it.monotonicTimeNanos!! in badge.monotonicTimeNanos!!..end &&
                (it.payload as ArchivedOpponentBattleResourceCountMeasured).visibleCount > 1 }) return null

        if (recovery.fastEffects.any { it.estimatedOnsetNanos in start..end } ||
            ordered.any { it.monotonicTimeNanos!! in start..end && when (val p = it.payload) {
                is ArchivedFastMoveUseObserved, ArchivedGetReadyWitnessed, ArchivedAttackIncoming,
                ArchivedObservationRestartedByUser, ArchivedScreenIgnoredByUser, ArchivedObservationSessionStoppedByUser -> true
                is ArchivedChargedMoveEnergySpent -> it.articleId != named.articleId
                is ArchivedRawText -> it.sourceId == "ANNOUNCEMENT_WITNESS" && departureConflict.containsMatchIn(p.value)
                else -> false
            } }) return null

        // The cry gives the time. For visual corroboration, the flash must contract and clear on
        // contiguous source frames; a capture gap cannot stand in for a clear frame.
        val phases = ordered.mapNotNull { row ->
            val batch = row.payload as? ArchivedTerminalParticleObservations ?: return@mapNotNull null
            if (batch.requestedRecipient != "OPPONENT" || start !in batch.phaseAtNanos..batch.sourceDeadlineNanos) return@mapNotNull null
            batch.phaseAtNanos to batch.samples.filter { it.sourceAtNanos in at..minOf(at + 2 * S, end) }
                .map { TerminalEffectFrame(row.articleId, it) }
        }.groupBy({ it.first }, { it.second })
        val departure = phases.values.flatMap { batches ->
            TerminalCollapseSequences.find(batches.flatten(), requiredClearFrames = 1) { frame, core ->
                core.area >= .005 * frame.sourceWidth * frame.sourceHeight
            }
        }.singleOrNull()
        if (cry is ReplayPartialCryCandidate) {
            val flash = departure ?: return null
            val expected = "${BattleSpeciesDisplay.name(owner.speciesName)} used ${charge.moveName}"
            val announcement = ordered.firstOrNull { row ->
                row.sourceId == "ANNOUNCEMENT_WITNESS" && row.articleId in named.predecessorIds &&
                    (row.payload as? ArchivedRawText)?.value?.trim()?.trimEnd('!', '.')?.equals(expected, true) == true
            } ?: return null
            // Only the captured part of the cry can corroborate a flash. The
            // back-projected onset can precede the clip by over a second.
            val overlaps = cry.fragments.any { fragment ->
                val begin = fragment.captureAtNanos + fragment.queryOffsetNanos
                begin < flash.clearedAt && begin + fragment.durationNanos > flash.startedAt
            }
            if (!overlaps) return null
            val clearFrame = phases.values.flatten().flatten().firstOrNull {
                it.sample.sourceAtNanos == flash.clearedAt && it.articleId in flash.evidenceReferences
            } ?: return null
            val refs = cry.fragments.map { it.articleId } + flash.evidenceReferences + listOf(badge.articleId, result.articleId)
            return ReplayFinishingChargeAssociation(
                "PLAYER", owner.speciesName, appearanceAt("PLAYER", start),
                "OPPONENT", target.speciesName, appearanceAt("OPPONENT", start), charge.moveName,
                start, flash.clearedAt, flash.startedAt, null, (charge.articleIds + announcement.articleId).toList(), refs.distinct(),
                emptyList(), ANNOUNCED_PARTIAL, departedBy = ReplayTimeBound(flash.clearedAt, clearFrame.articleId),
            )
        }
        val departureRefs = departure?.evidenceReferences ?: run {
            // During an established appearance a second capture can corroborate
            // the same cry when the departure flash is incomplete. It is one cry.
            val whole = cry.fragments.filter { it.correlation >= .95f }
            val partial = recovery.partialCryCandidates.singleOrNull { it.speciesId == cry.speciesId &&
                kotlin.math.abs(it.estimatedOnsetNanos - at) <= 5_000_000L } ?: return null
            val corroboration = partial.fragments.filter { part ->
                kotlin.math.abs(part.estimatedOnsetNanos - at) <= 5_000_000L && whole.any { fit ->
                    part.articleId != fit.articleId && part.audioSha256 != fit.audioSha256 &&
                        part.referenceSha256 == fit.referenceSha256 &&
                        kotlin.math.abs(part.estimatedOnsetNanos - fit.estimatedOnsetNanos) <= 5_000_000L
                }
            }
            val corroborated = corroboration.groupBy { it.articleId }.filterValues { fragments ->
                fragments.maxOf { it.referenceOffsetNanos } >= fragments.minOf { it.referenceOffsetNanos + it.durationNanos }
            }
            if (corroborated.isEmpty()) return null
            val appearance = appearanceAt("OPPONENT", start)
            val live = ordered.lastOrNull { row ->
                row.monotonicTimeNanos!! in maxOf(appearance, start - 2 * S)..start &&
                    witnessedDamagedBar(row, "OPPONENT", appearance)
            } ?: return null
            corroborated.keys.toList() + live.articleId
        }
        val refs = cry.fragments.map { it.articleId } + departureRefs + listOf(badge.articleId, result.articleId)
        return ReplayFinishingChargeAssociation(
            "PLAYER", owner.speciesName, appearanceAt("PLAYER", start),
            "OPPONENT", target.speciesName, appearanceAt("OPPONENT", start), charge.moveName,
            start, at, at, null, charge.articleIds.toList(), refs.distinct(), emptyList(), ANNOUNCED,
            departedBy = ReplayTimeBound(at, cry.fragments.first().articleId),
        )
    }

    private val departureConflict = Regex("""\b(blocked|go,)""", RegexOption.IGNORE_CASE)
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
            // The first matched part of the cry supplies a by-time even when the clip
            // began before the attack. An extrapolated cry onset still drives presentation.
            val firstCry = cry.fragments.minBy { it.captureAtNanos + it.queryOffsetNanos }
            ReplayFinishingChargeAssociation("PLAYER", player.speciesName, appearanceAt("PLAYER", at),
                "OPPONENT", target.speciesName, appearanceAt("OPPONENT", at), recovered.moveName,
                at, cry.fragments.minOf { it.captureAtNanos }, cryAt, null,
                recovered.fragments.map { it.articleId }, cry.fragments.map { it.articleId } + listOf(count.articleId, result.articleId) + identities,
                listOf(shields.articleId), BASIS,
                departedBy = ReplayTimeBound(firstCry.captureAtNanos + firstCry.queryOffsetNanos, firstCry.articleId))
        }
    }
}
