package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.inference.FastMoveUseEvidence
import com.example.overdex.battle.observation.TeamRosterSpeciesAttributor
import com.example.overdex.battle.inference.TerminalChargedFaintResolver

/** Reads original evidence. Projected uses and resolved faints supply identities, never new pixels. */
internal class ReplayFinishingEvidence(
    source: List<ArchivedRealityArticle>,
    private val hp: ReplayHpEvidence,
    private val charges: List<ReplayFinishingChargeCandidate>,
    private val fast: List<ReplayFastMoveCycle>,
    private val actorAt: (String, Long) -> ReplayCombatant?,
    private val appearanceAt: (String, Long) -> Long,
    private val nextAppearance: (String, Long) -> Long?,
    private val recoveredCries: List<ReplayRecoveredCry> = emptyList(),
) {
    private val articles = source.filter { it.monotonicTimeNanos != null }
        .groupBy { it.articleId }.values.mapNotNull { it.distinct().singleOrNull() }
        .sortedWith(compareBy<ArchivedRealityArticle> { it.monotonicTimeNanos }.thenBy { it.articleId })
    private val byId = articles.associateBy { it.articleId }
    private val claims = ReplayPossibleDamageClaims.collect(articles)
    private val chargeEvidence by lazy { charges.associateWith(::findCompletionEvidence) }
    private val profiles = articles.filter { it.payload is ArchivedCompleteHpBarObserved }
        .groupBy { (it.payload as ArchivedCompleteHpBarObserved).side to it.monotonicTimeNanos }
    private val hpByFrame = articles.filter { it.payload is ArchivedActiveHpBarMeasured }
        .groupBy { (it.payload as ArchivedActiveHpBarMeasured).side to it.monotonicTimeNanos }

    /** Project uncertainty only after the appearance's faint has been resolved. */
    fun faintTiming(
        knockout: ReplayKnockout?, link: ReplayFinishingAttribution?,
    ): ReplayFaintTiming {
        val side = knockout?.side ?: link?.recipientSide ?: return ReplayFaintTiming.Unknown
        val appearance = knockout?.appearanceAtNanos ?: link?.recipientAppearanceAtNanos ?: return ReplayFaintTiming.Unknown
        val countWindow = knockout?.timing as? ReplayFaintTiming.Window
        val reception = link?.reception
        val zero = reception?.articleIds?.firstOrNull { byId[it]?.monotonicTimeNanos == reception.atNanos }
            ?.let { ReplayTimeBound(reception.atNanos, it) } ?: (knockout?.timing as? ReplayFaintTiming.ZeroHp)?.frame
        val by = listOfNotNull(zero, countWindow?.by, link?.departedBy).minByOrNull { it.atNanos }
            ?: return ReplayFaintTiming.Unknown
        val live = lastLiveBar(side, appearance)
        val after = listOfNotNull(countWindow?.after, live).maxByOrNull { it.atNanos }

        // A partial reference match gives a by-time. Promote it to an onset only
        // when an independent localization identifies the same departure.
        val departure = localizedCries.filter { row ->
            val at = row.monotonicTimeNanos!!
            val cry = row.payload as ArchivedBattleCryLocalized
            val other = if (side == "PLAYER") "OPPONENT" else "PLAYER"
            val otherId = actorAt(other, at)?.speciesId
            if (actorAt(side, at)?.speciesId != cry.speciesId || otherId == null || otherId == cry.speciesId ||
                appearanceAt(side, at) != appearance || (after != null && at <= after.atNanos)) return@filter false
            val cited = row.articleId == link?.departedBy?.articleId || row.articleId in knockout?.sourceArticleIds.orEmpty()
            val upper = if (zero != null) by.atNanos + 2_000_000_000L else by.atNanos
            val bounded = live != null && at in (by.atNanos - 2_000_000_000L)..upper
            (cited || bounded) && articles.none { a ->
                a.monotonicTimeNanos!! in minOf(at, by.atNanos)..maxOf(at, by.atNanos) &&
                    (a.payload is ArchivedObservationSessionStoppedByUser || a.payload is ArchivedScreenIgnoredByUser ||
                        a.payload is ArchivedObservationRestartedByUser)
            }
        }.singleOrNull()
        if (departure != null) return ReplayFaintTiming.Cry(ReplayTimeBound(departure.monotonicTimeNanos!!, departure.articleId))
        if (link?.basis == ReplayTerminalAudioAssociation.BASIS) {
            // When the archive retained the start of the reference waveform, the
            // first sample of that match gives an audible onset on the clip clock.
            val cry = recoveredCries.singleOrNull { it.estimatedOnsetNanos == link.landingAtNanos &&
                it.speciesId == actorAt(side, it.estimatedOnsetNanos)?.speciesId }
            val first = cry?.fragments?.filter { it.referenceOffsetNanos == 0L && it.articleId in link.faintArticleIds }
                ?.minByOrNull { it.captureAtNanos + it.queryOffsetNanos }
            if (first != null) return ReplayFaintTiming.Cry(ReplayTimeBound(first.captureAtNanos + first.queryOffsetNanos, first.articleId))
        }
        return ReplayFaintTiming.window(after, by)
    }

    private val localizedCries = articles.filter {
        val cry = it.payload as? ArchivedBattleCryLocalized
        cry != null && cry.correlation in .95f..1f && cry.durationNanos > 0 && cry.referenceSha256.isNotBlank()
    }

    private fun lastLiveBar(side: String, appearance: Long): ReplayTimeBound? {
        val matchEnd = articles.firstOrNull { it.monotonicTimeNanos!! >= appearance &&
            it.payload is ArchivedMatchEnded }?.monotonicTimeNanos ?: Long.MAX_VALUE
        for (article in articles.asReversed()) {
            val at = article.monotonicTimeNanos!!
            if (at < appearance) break
            if (at > matchEnd) continue
            val live = when (val p = article.payload) {
                is ArchivedActiveHpBarMeasured -> p.side == side
                is ArchivedCompleteHpBarObserved -> p.side == side && p.status == "LIVE_COLOR" &&
                    p.liveColumns in 1..p.interiorColumns && reliable(article) && complete(p)
                else -> false
            }
            if (!live || appearanceAt(side, at) != appearance) continue
            val key = side to at
            if (profiles[key].orEmpty().any { row ->
                    val p = row.payload as ArchivedCompleteHpBarObserved
                    p.status != "LIVE_COLOR" || p.liveColumns !in 1..p.interiorColumns ||
                        !complete(p) || !belongsToAppearance(p, at, appearance)
                } || hpByFrame[key].orEmpty().any { !hp.confirmsLiveHp(it) }) continue
            return ReplayTimeBound(at, article.articleId)
        }
        return null
    }

    // A phase can suppress decorative scheduling while a canonical incoming hit still
    // has an original recipient tick. Promote only the selected direct finish into Replay.
    val pendingFastCycles: List<ReplayFastMoveCycle> by lazy {
        val scheduledIds = fast.flatMap { it.witness.articleIds }.toSet()
        articles.filter { it.payload is ArchivedFastMoveUseObserved && it.articleId !in scheduledIds }
            .mapNotNull { a ->
                val use = a.payload as ArchivedFastMoveUseObserved
                val at = a.monotonicTimeNanos!!
                if (!opposes(use.attackingSide, use.damagedSide) || "HP_DAMAGE_TICK" !in use.evidenceKinds) return@mapNotNull null
                val sources = (a.predecessorIds + a.evidenceReferences.orEmpty()).toSet()
                val damage = sources.mapNotNull(byId::get).singleOrNull {
                    val tick = it.payload as? ArchivedActiveHpBarDamageTickMeasured
                    tick?.damagedSide == use.damagedSide && tick.afterFraction == 0f
                } ?: return@mapNotNull null
                val hitAt = damage.monotonicTimeNanos!!
                val start = maxOf(hitAt - 300_000_000L,
                    appearanceAt(use.attackingSide, hitAt), appearanceAt(use.damagedSide, hitAt))
                val end = minOf(nextAppearance(use.attackingSide, hitAt) ?: Long.MAX_VALUE,
                    nextAppearance(use.damagedSide, hitAt) ?: Long.MAX_VALUE, hitAt + 120_000_000L)
                ReplayFastMoveCycle(ReplayFastMoveWitness(use.attackingSide, at, null, null, null,
                    start, end, use.evidenceKinds.toSet(), sources + a.articleId, setOf(at, hitAt)), start, hitAt)
            }
    }

    fun observed(knockouts: List<ReplayKnockout>): List<ReplayFinishingAttribution> =
        knockouts.filter { it.sourceArticleIds.isNotEmpty() }.flatMap { faint ->
            val direct = (fast + pendingFastCycles).mapNotNull { directFast(faint, it) } +
                charges.mapNotNull { directCharge(faint, it) }
            if (direct.isNotEmpty()) direct else lastRecipient(faint)
        }

    /** A confirmed loss plus a one-ball badge can identify the only recipient even if
     * there is no terminal damage frame. The landing remains a presentation estimate. */
    private fun lastRecipient(faint: ReplayKnockout): List<ReplayFinishingAttribution> {
        if (faint.side !in SIDES || faint.replacementAtNanos != null) return emptyList()
        val side = if (faint.side == "PLAYER") "OPPONENT" else "PLAYER"
        val end = faint.recordedAtNanos
        val knownResult = articles.mapNotNull { (it.payload as? ArchivedMatchEnded)?.result }.distinct()
        if ((if (faint.side == "PLAYER") "WIN" else "LOSS") in knownResult) return emptyList()
        val counts = articles.mapNotNull { a ->
            val count = when (val p = a.payload) {
                is ArchivedPlayerPokeBallCountMeasured -> p.visibleCount.takeIf { faint.side == "PLAYER" && p.maximumCount == 3 }
                is ArchivedOpponentBattleResourceCountMeasured -> p.visibleCount.takeIf {
                    faint.side == "OPPONENT" && p.resource == "POKE_BALLS" && p.maximumCount == 3 }
                else -> null
            }
            count?.takeIf { it in 0..3 && reliable(a) }?.let { a to it }
        }
        fun badgeFor(start: Long, landing: Long, ids: Set<String>): String? {
            if (end - landing !in 0..LAST_RECIPIENT_CONFIRMATION || start > landing ||
                appearanceAt(faint.side, start) != faint.appearanceAtNanos) return null
            val badge = counts.lastOrNull { it.first.monotonicTimeNanos!! <= start } ?: return null
            val at = badge.first.monotonicTimeNanos!!
            if (badge.second != 1 || counts.any { (a, n) ->
                    val t = a.monotonicTimeNanos!!
                    (t in at..landing && n != 1 && !(t == landing && n == 0)) ||
                        (t in landing..end && n > 1) || (t < at && n == 0)
                }) return null
            // An uncited later hit (including one from the supposed victim) or an
            // observation break makes the sequence incomplete. Publication order is irrelevant.
            if (claims.any { it.atNanos > landing && it.atNanos <= end && it.articleId !in ids } ||
                articles.any { it.monotonicTimeNanos!! in start until end &&
                    (it.payload is ArchivedMatchEnded || it.payload is ArchivedScreenIgnoredByUser ||
                        it.payload is ArchivedObservationRestartedByUser || it.payload is ArchivedObservationSessionStoppedByUser) })
                return null
            return badge.first.articleId
        }

        val fastLinks = fast.mapNotNull { cycle ->
            val use = cycle.witness
            if (use.side != side || !FastMoveUseEvidence.establishesUse(use.evidenceKinds)) return@mapNotNull null
            val sources = use.articleIds.mapNotNull(byId::get).filter { it.fastOccurrenceId() != null }
            if (sources.map { it.fastOccurrenceId() }.distinct().size != 1) return@mapNotNull null
            val source = sources.first()
            val at = source.monotonicTimeNanos!!
            val actor = actorAt(side, at) ?: return@mapNotNull null
            val owner = appearanceAt(side, at)
            if (sources.any { a ->
                    val p = a.payload as ArchivedFastMoveUseObserved
                    return@any !reliable(a) || p.attackingSide != side || (p.damagedSide in SIDES && p.damagedSide != faint.side) ||
                        p.attackerSpeciesName?.let { speciesKey(it) != speciesKey(actor.speciesName) } == true ||
                        p.appearanceId?.let { byId[it]?.monotonicTimeNanos?.let { t -> appearanceAt(side, t) != owner } } == true
                } || !sameExchange(side, owner, faint, cycle.startedAtNanos, cycle.impactAtNanos)) return@mapNotNull null
            val badge = badgeFor(at, cycle.impactAtNanos, use.articleIds) ?: return@mapNotNull null
            ReplayFinishingFastAssociation(side, actor.speciesName, faint.side, faint.speciesName, faint.appearanceAtNanos,
                use.moveName, cycle.impactAtNanos, at, end, use.articleIds.sorted(), emptyList(),
                (faint.sourceArticleIds + badge).distinct(), owner, basis = LAST_RECIPIENT_BASIS)
        }
        val chargeLinks = charges.mapNotNull { charge ->
            if (charge.side != side || !charge.completed || !charge.uniquelyOwned) return@mapNotNull null
            val proof = chargeEvidence.getValue(charge)
            if (proof.isEmpty()) return@mapNotNull null
            val owner = appearanceAt(side, charge.sourceAtNanos)
            val actor = actorAt(side, charge.sourceAtNanos) ?: return@mapNotNull null
            if (!sameExchange(side, owner, faint, charge.preparationAtNanos, charge.impactAtNanos)) return@mapNotNull null
            val ids = proof + charge.articleIds
            val badge = badgeFor(charge.sourceAtNanos, charge.impactAtNanos, ids) ?: return@mapNotNull null
            val shields = unshielded(charge, faint) ?: return@mapNotNull null
            ReplayFinishingChargeAssociation(side, actor.speciesName, owner, faint.side, faint.speciesName,
                faint.appearanceAtNanos, charge.moveName, charge.sourceAtNanos, end, charge.impactAtNanos, null,
                ids.sorted(), (faint.sourceArticleIds + badge).distinct(), shields, LAST_RECIPIENT_BASIS)
        }
        return fastLinks + chargeLinks
    }

    /** Preparation custody and an empty-looking outline corroborate a count-resolved loss.
     * An uncertain interior cannot supply a measured reception through this path.
     */
    fun countImpactSupport(charge: ReplayFinishingChargeCandidate, faint: ReplayKnockout): List<String> {
        if (charge.side != "PLAYER") return emptyList()
        val gradeCrops = charge.articleIds.mapNotNull(byId::get).filter { cue ->
            replayQteProgressGrade(cue) != null && cue.monotonicTimeNanos!! in
                charge.preparationAtNanos..charge.sourceAtNanos
        }.flatMap { cue ->
            (cue.predecessorIds + cue.evidenceReferences.orEmpty()).mapNotNull(byId::get).filter { crop ->
                (crop.payload as? ArchivedCropCaptured)?.cropName == "QteProgressTextCrop" &&
                    crop.monotonicTimeNanos == cue.monotonicTimeNanos
            }.map { it.articleId }
        }.toSet()
        if (gradeCrops.isEmpty()) return emptyList()
        val owner = appearanceAt(charge.side, charge.sourceAtNanos)
        for (sample in articles) {
            val at = sample.monotonicTimeNanos!!
            if (sample.sourceId != "CHARGED_IMPACT_${faint.side}_HP_BAR_FRAME_WITNESS" ||
                at !in charge.sourceAtNanos..minOf(charge.impactAtNanos, charge.sourceAtNanos + MAX_CHARGE_IMPACT) ||
                !sameExchange(charge.side, owner, faint, at, at)) continue
            val cropIds = (sample.predecessorIds + sample.evidenceReferences.orEmpty()).filter { it in gradeCrops }
            if (cropIds.isEmpty()) continue
            val outline = matchingEmptyFrame(faint.side, at, faint.appearanceAtNanos, requireResolvedInterior = false)
            if (sample.articleId !in outline) continue
            val replacement = faint.replacementAtNanos ?: return emptyList()
            if (articles.any { later ->
                    val time = later.monotonicTimeNanos!!
                    time in at until replacement && when (val p = later.payload) {
                        is ArchivedActiveHpBarMeasured -> p.side == faint.side && hp.confirmsLiveHp(later)
                        is ArchivedCompleteHpBarObserved -> p.side == faint.side && p.liveColumns > 0 &&
                            p.status == "LIVE_COLOR" && reliable(later) && complete(p) &&
                            belongsToAppearance(p, time, faint.appearanceAtNanos)
                        else -> false
                    }
                }) return emptyList()
            return (outline + cropIds).distinct().sorted()
        }
        return emptyList()
    }

    private fun directFast(faint: ReplayKnockout, cycle: ReplayFastMoveCycle): ReplayFinishingFastAssociation? {
        val use = cycle.witness
        if (!opposes(use.side, faint.side) || "HP_DAMAGE_TICK" !in use.evidenceKinds ||
            !FastMoveUseEvidence.establishesUse(use.evidenceKinds)) return null
        val occurrences = use.articleIds.mapNotNull(byId::get).filter { it.fastOccurrenceId() != null }
            .sortedWith(compareBy<ArchivedRealityArticle> { it.monotonicTimeNanos }.thenBy { it.articleId })
        if (occurrences.mapNotNull { it.fastOccurrenceId() }.distinct().size != 1) return null
        val occurrence = occurrences.first()
        val at = occurrence.monotonicTimeNanos!!
        val attacker = actorAt(use.side, at) ?: return null
        val owner = appearanceAt(use.side, at)
        if (occurrences.any {
                val p = it.payload as ArchivedFastMoveUseObserved
                p.attackingSide != use.side || p.damagedSide != faint.side ||
                    p.attackerSpeciesName?.let { name -> speciesKey(name) != speciesKey(attacker.speciesName) } == true ||
                    p.appearanceId?.let { id -> byId[id]?.let { identity ->
                        val witness = identity.payload as? ArchivedActivePokemonSpeciesWitnessed
                        identity.monotonicTimeNanos!! > at || appearanceAt(use.side, identity.monotonicTimeNanos) != owner ||
                            (witness != null && witness.side != use.side)
                    } } == true
            }) return null
        val receipt = occurrences.flatMap { it.predecessorIds + it.evidenceReferences.orEmpty() }
            .distinct().mapNotNull(byId::get).filter { a ->
                val p = a.payload as? ArchivedActiveHpBarDamageTickMeasured ?: return@filter false
                val time = a.monotonicTimeNanos!!
                p.damagedSide == faint.side && p.beforeFraction in Float.MIN_VALUE..1f &&
                    p.afterFraction == 0f && p.lostFraction in Float.MIN_VALUE..1f &&
                    time in (at - HP_CONFIRMATION_TAIL)..(at + HP_CONFIRMATION_TAIL) &&
                    qualifiedReception(faint, use.side, owner, time) && reliable(a) &&
                    hp.acceptsSingleHit(a) && qualifiedEmpty(faint.side, time, faint.appearanceAtNanos).isNotEmpty()
            }.singleOrNull() ?: return null
        val time = receipt.monotonicTimeNanos!!
        if (cycle.impactAtNanos > faint.recordedAtNanos ||
            !sameExchange(use.side, owner, faint, cycle.startedAtNanos, cycle.impactAtNanos)) return null
        return ReplayFinishingFastAssociation(use.side, attacker.speciesName, faint.side, faint.speciesName,
            faint.appearanceAtNanos, use.moveName, time, at, faint.recordedAtNanos,
            use.articleIds.sorted(), emptyList(), faint.sourceArticleIds.sorted(), owner,
            ReplayFinishingReception(time, listOf(receipt.articleId) + qualifiedEmpty(faint.side, time, faint.appearanceAtNanos)),
            "SOURCE_LINKED_FAST_MOVE_DAMAGE_TO_ZERO")
    }

    private fun directCharge(faint: ReplayKnockout, charge: ReplayFinishingChargeCandidate): ReplayFinishingChargeAssociation? {
        if (!charge.completed || !charge.uniquelyOwned || !opposes(charge.side, faint.side) ||
            faint.recordedAtNanos - charge.sourceAtNanos !in 0..MAX_FAINT_CONFIRMATION) return null
        val attacker = actorAt(charge.side, charge.sourceAtNanos) ?: return null
        val owner = appearanceAt(charge.side, charge.sourceAtNanos)
        if (!sameExchange(charge.side, owner, faint, charge.preparationAtNanos, charge.sourceAtNanos)) return null
        val ids = chargeEvidence.getValue(charge)
        val completionIds = ids.mapNotNull(byId::get)
        if (completionIds.isEmpty() || completionIds.any { a ->
                when (val p = a.payload) {
                    is ArchivedChargedMoveEnergySpent -> p.side != charge.side ||
                        speciesKey(p.speciesName) != speciesKey(attacker.speciesName) ||
                        (charge.moveName != null && p.moveName != charge.moveName)
                    is ArchivedRawText -> USED_MOVE.matchEntire(p.value.trim())?.groupValues?.get(1)?.let {
                        speciesKey(it) != speciesKey(attacker.speciesName)
                    } == true
                    else -> false
                }
            }) return null
        val terminal = faint.basis == TerminalChargedFaintResolver.BASIS &&
            faint.sourceArticleIds.any { it in ids }
        val shieldProof = unshielded(charge, faint)
        if (!terminal && shieldProof == null) return null
        val measurements = articles.filter { a ->
            val time = a.monotonicTimeNanos!!
            a.sourceId == "CHARGED_IMPACT_${faint.side}_HP_BAR_FRAME_WITNESS" &&
                time in charge.sourceAtNanos..(charge.sourceAtNanos + MAX_CHARGE_IMPACT) &&
                faint.recordedAtNanos - time in 0..MAX_FAINT_CONFIRMATION &&
                sameExchange(charge.side, owner, faint, time, time) &&
                // A preparation cue can span multiple phases; match the completed use itself.
                (a.evidenceReferences.orEmpty() + a.predecessorIds).any { it in ids } &&
                a.articleId in qualifiedEmpty(faint.side, time, faint.appearanceAtNanos)
        }
        val measured = measurements.firstOrNull()
        if (measured == null && !terminal) return null
        val receipt = measured?.let {
            val time = it.monotonicTimeNanos!!
            ReplayFinishingReception(time, qualifiedEmpty(faint.side, time, faint.appearanceAtNanos))
        }
        val landing = receipt?.atNanos ?: faint.presentationAtNanos
        if (landing < charge.preparationAtNanos ||
            !sameExchange(charge.side, owner, faint, charge.preparationAtNanos, landing)) return null
        return ReplayFinishingChargeAssociation(charge.side, attacker.speciesName, owner,
            faint.side, faint.speciesName, faint.appearanceAtNanos, charge.moveName, charge.sourceAtNanos,
            faint.recordedAtNanos, landing, faint.replacementAtNanos, (charge.articleIds + ids).sorted(),
            faint.sourceArticleIds.sorted(), shieldProof.orEmpty(),
            if (receipt == null) TerminalChargedFaintResolver.BASIS else "PHASE_LINKED_CHARGED_IMPACT_TO_ZERO",
            receipt)
    }

    fun hasLivingHpAfter(side: String, ownerAppearance: Long, receipt: Long, end: Long): Boolean {
        val recipient = actorAt(side, receipt) ?: return true
        return articles.any { a ->
            val time = a.monotonicTimeNanos!!
            if (time !in receipt..end) return@any false
            if (a.payload !is ArchivedActiveHpBarMeasured && a.payload !is ArchivedCompleteHpBarObserved) return@any false
            val appearance = appearanceAt(side, time)
            // A later known living return also contradicts this owner. HP from
            // the incoming replacement or a distinct configured slot does not.
            if (appearance != ownerAppearance) {
                val later = actorAt(side, time) ?: return@any false
                if (later.speciesName != recipient.speciesName ||
                    (later.playerTeamSlot != null && recipient.playerTeamSlot != null &&
                        later.playerTeamSlot != recipient.playerTeamSlot)) return@any false
            }
            when (val p = a.payload) {
                is ArchivedActiveHpBarMeasured -> p.side == side && hp.confirmsLiveHp(a)
                is ArchivedCompleteHpBarObserved -> p.side == side && p.status == "LIVE_COLOR" &&
                    p.liveColumns > 0 && complete(p) && reliable(a) &&
                    belongsToAppearance(p, time, appearance)
                else -> false
            }
        }
    }

    fun allows(link: ReplayFinishingAttribution, knockouts: List<ReplayKnockout>): Boolean {
        val receipt = link.reception?.atNanos ?: link.landingAtNanos
        val owner = link.attackerAppearanceAtNanos
        if (knockouts.any { it.side == link.attackingSide && it.appearanceAtNanos == owner &&
                it.presentationAtNanos <= receipt } ||
            appearanceAt(link.attackingSide, link.moveSourceAtNanos) != owner ||
            appearanceAt(link.attackingSide, receipt) != owner ||
            appearanceAt(link.recipientSide, link.moveSourceAtNanos) != link.recipientAppearanceAtNanos ||
            appearanceAt(link.recipientSide, receipt) != link.recipientAppearanceAtNanos) return false
        val next = nextAppearance(link.recipientSide, receipt)
        // Later count publication can follow a replacement. Never inspect the replacement's HP.
        val battleEnd = articles.firstOrNull { it.payload is ArchivedMatchEnded && it.monotonicTimeNanos!! >= receipt }
            ?.monotonicTimeNanos
        val through = minOf(next?.minus(1) ?: Long.MAX_VALUE, battleEnd ?: Long.MAX_VALUE)
        if (articles.any {
                it.monotonicTimeNanos!! in link.moveSourceAtNanos until receipt &&
                    (it.payload is ArchivedObservationRestartedByUser ||
                        it.payload is ArchivedObservationSessionStoppedByUser ||
                        it.payload is ArchivedScreenIgnoredByUser || it.payload is ArchivedMatchEnded)
            }) return false
        if (hasLivingHpAfter(link.recipientSide, link.recipientAppearanceAtNanos, receipt, battleEnd ?: Long.MAX_VALUE)) return false
        val charge = charges.singleOrNull { it.side == link.attackingSide && it.sourceAtNanos == link.moveSourceAtNanos &&
            it.articleIds.any(link.moveArticleIds::contains) }
        val selectedCharge = charge.takeIf { link is ReplayFinishingChargeAssociation }
        if (selectedCharge != null && blocked(selectedCharge, link.recipientSide, through)) return false

        // A raw use remains a conflicting claim even when no decorative cycle was scheduled.
        val cycle = fast.singleOrNull { it.witness.side == link.attackingSide &&
            it.witness.articleIds.any(link.moveArticleIds::contains) }.takeIf { link is ReplayFinishingFastAssociation }
        val from = if (selectedCharge != null) selectedCharge.preparationAtNanos - MAX_PENDING_FAST
            else minOf(cycle?.startedAtNanos ?: receipt, link.moveSourceAtNanos)
        val ownIds = selectedCharge?.let { chargeEvidence.getValue(it) }.orEmpty() + link.moveArticleIds + link.reception?.articleIds.orEmpty()
        val fastOccurrences = ownIds.mapNotNull { byId[it]?.fastOccurrenceId() }.toSet()
        val competingFrom = minOf(link.moveSourceAtNanos, receipt)
        val competingUntil = minOf(through, maxOf(receipt, link.faintSourceAtNanos))
        val otherCharges = charges.filter { it !== selectedCharge && it.completed }
        val knownChargeClaims = otherCharges.flatMap { other ->
            chargeEvidence.getValue(other).map { it to other }
        }.groupBy({ it.first }, { it.second })
        return claims.none { claim ->
            claim.articleId !in ownIds && claim.atNanos in from..competingUntil &&
                (claim.attackingSide !in SIDES || claim.attackingSide == link.attackingSide) &&
                (claim.damagedSide !in SIDES || claim.damagedSide == link.recipientSide) &&
                appearanceAt(link.attackingSide, claim.atNanos) == owner &&
                appearanceAt(link.recipientSide, claim.atNanos) == link.recipientAppearanceAtNanos &&
                when (claim.kind) {
                    "FAST_USE" -> byId[claim.articleId]?.fastOccurrenceId() !in fastOccurrences &&
                        (claim.atNanos >= competingFrom || (selectedCharge != null && fast.none {
                            claim.articleId in it.witness.articleIds && it.impactAtNanos < selectedCharge.preparationAtNanos
                        }))
                    "CHARGE_USE", "CHARGE_ANNOUNCEMENT" -> claim.atNanos >= competingFrom &&
                        knownChargeClaims[claim.articleId].isNullOrEmpty()
                    else -> false
                }
        } && fast.none {
            it !== cycle && it.witness.side == link.attackingSide && it.impactAtNanos in competingFrom..competingUntil &&
                appearanceAt(link.attackingSide, it.impactAtNanos) == owner &&
                appearanceAt(link.recipientSide, it.impactAtNanos) == link.recipientAppearanceAtNanos &&
                it.witness.articleIds.none(ownIds::contains)
        } && otherCharges.none { other ->
            val overlaps = (other.side == link.attackingSide || other.side !in SIDES || !other.uniquelyOwned) &&
                other.sourceAtNanos <= competingUntil && other.impactAtNanos >= competingFrom &&
                appearanceAt(link.attackingSide, other.sourceAtNanos) == owner &&
                appearanceAt(link.recipientSide, other.sourceAtNanos) == link.recipientAppearanceAtNanos
            if (!overlaps) return@none false
            // A living bar between this charge's impact and the independently
            // supported Fast Move impact rules out the charge as the finisher.
            val survived = link.basis == ReplaySliverExchange.BASIS && other.uniquelyOwned &&
                other.side == link.attackingSide && other.sourceAtNanos < link.moveSourceAtNanos &&
                other.impactAtNanos < receipt && articles.any { row ->
                    val bar = row.payload as? ArchivedActiveHpBarMeasured
                    row.sourceId == "CHARGED_IMPACT_${link.recipientSide}_HP_BAR_FRAME_WITNESS" &&
                        bar?.side == link.recipientSide && row.monotonicTimeNanos!! in other.sourceAtNanos until link.moveSourceAtNanos &&
                        appearanceAt(link.recipientSide, row.monotonicTimeNanos) == link.recipientAppearanceAtNanos &&
                        hp.confirmsLiveHp(row)
                } && hasLivingHpAfter(link.recipientSide, link.recipientAppearanceAtNanos,
                    other.impactAtNanos + 1, receipt - 1)
            !survived && (other.side !in SIDES || !other.uniquelyOwned || !blocked(other, link.recipientSide, through))
        }
    }

    /** A typed banner with custody back to the named completion is the same occurrence. */
    private fun findCompletionEvidence(charge: ReplayFinishingChargeCandidate): Set<String> {
        val direct = charge.articleIds.mapNotNull(byId::get).filter(::isChargeCompletion)
        val named = direct.flatMap { a ->
            (a.predecessorIds + a.evidenceReferences.orEmpty()).mapNotNull(byId::get)
                .filter { isChargeCompletion(it) && it.monotonicTimeNanos == a.monotonicTimeNanos }
        }
        val supported = (direct + named).map { it.articleId }.toSet()
        return supported + articles.filter { a ->
            a.payload is ArchivedChargeMoveUsedAnnounced &&
                (a.predecessorIds + a.evidenceReferences.orEmpty()).any(supported::contains)
        }.map { it.articleId }
    }

    private fun isChargeCompletion(a: ArchivedRealityArticle): Boolean =
        a.payload is ArchivedChargedMoveEnergySpent || a.payload is ArchivedChargeMoveUsedAnnounced ||
            (a.sourceId == "ANNOUNCEMENT_WITNESS" && a.payload is ArchivedRawText &&
                USED_MOVE.matches(a.payload.value.trim()))

    private fun unshielded(charge: ReplayFinishingChargeCandidate, faint: ReplayKnockout): List<String>? {
        val shields = shieldReadings(faint.side, charge.preparationAtNanos, faint.recordedAtNanos)
        val prior = shields.lastOrNull { it.first <= charge.sourceAtNanos } ?: return null
        val later = shields.filter { it.first in charge.sourceAtNanos..faint.recordedAtNanos }
        if (later.any { it.second != prior.second } || (prior.second != 0 && later.none { it.first > charge.sourceAtNanos })) return null
        return (listOf(prior) + later).map { it.third }.distinct()
    }

    private fun blocked(charge: ReplayFinishingChargeCandidate, side: String, through: Long): Boolean {
        val shields = shieldReadings(side, charge.preparationAtNanos, minOf(through, charge.sourceAtNanos + MAX_CHARGE_IMPACT))
        val before = shields.filter { it.first <= charge.sourceAtNanos }
        val after = shields.filter { it.first >= charge.sourceAtNanos }
        return (before.isNotEmpty() && after.any { it.second != before.last().second }) ||
            after.zipWithNext().any { (a, b) -> b.second < a.second } ||
            articles.any {
                it.monotonicTimeNanos!! in charge.sourceAtNanos..minOf(through, charge.sourceAtNanos + MAX_CHARGE_IMPACT) &&
                    it.sourceId == "ANNOUNCEMENT_WITNESS" &&
                    (it.payload as? ArchivedRawText)?.value?.trim()?.trimEnd('!', '.')?.equals("Blocked", ignoreCase = true) == true
            }
    }

    private fun shieldReadings(side: String, from: Long, to: Long) = articles.mapNotNull { a ->
        val time = a.monotonicTimeNanos!!
        if (time !in from..to) return@mapNotNull null
        val count = when (val p = a.payload) {
            is ArchivedPlayerShieldCountMeasured -> p.visibleCount.takeIf { side == "PLAYER" && p.maximumCount == 2 }
            is ArchivedOpponentBattleResourceCountMeasured -> p.visibleCount.takeIf {
                side == "OPPONENT" && p.resource == "SHIELDS" && p.maximumCount == 2
            }
            else -> null
        }
        count?.takeIf { it in 0..2 }?.let { Triple(time, it, a.articleId) }
    }

    private fun qualifiedReception(faint: ReplayKnockout, side: String, owner: Long, at: Long): Boolean =
        faint.recordedAtNanos - at in 0..MAX_FAINT_CONFIRMATION &&
            sameExchange(side, owner, faint, at, at)

    private fun sameExchange(side: String, owner: Long, faint: ReplayKnockout, from: Long, at: Long): Boolean =
        owner != Long.MIN_VALUE && appearanceAt(side, from) == owner && appearanceAt(side, at) == owner &&
            appearanceAt(faint.side, from) == faint.appearanceAtNanos &&
            appearanceAt(faint.side, at) == faint.appearanceAtNanos &&
            actorAt(faint.side, at)?.let { speciesKey(it.speciesName) == speciesKey(faint.speciesName) } == true

    private fun qualifiedEmpty(side: String, time: Long, appearance: Long): List<String> =
        matchingEmptyFrame(side, time, appearance, requireResolvedInterior = true)

    private fun matchingEmptyFrame(
        side: String, time: Long, appearance: Long, requireResolvedInterior: Boolean,
    ): List<String> {
        val profile = profiles[side to time].orEmpty().singleOrNull() ?: return emptyList()
        val p = profile.payload as ArchivedCompleteHpBarObserved
        if (p.status != "VISIBLE_EMPTY" || p.liveColumns != 0 || p.damageColumns != 0 ||
            (requireResolvedInterior && (p.unmaskedUncertainColumns ?: 0) != 0) || !complete(p) || !reliable(profile) ||
            !belongsToAppearance(p, time, appearance)) return emptyList()
        val bars = hpByFrame[side to time].orEmpty()
        if (bars.isEmpty() || bars.any { a ->
                val bar = a.payload as ArchivedActiveHpBarMeasured
                bar.fillColor != "EMPTY" || bar.filledFraction != 0f || !reliable(a) || !hp.accepts(a) ||
                    bar.sourceWidth != p.sourceWidth || bar.sourceHeight != p.sourceHeight ||
                    bar.barLeft != p.barLeft || bar.barRight != p.barRight ||
                    bar.barTop != p.barTop || bar.barBottom != p.barBottom
            }) return emptyList()
        return bars.map { it.articleId } + profile.articleId
    }

    private fun belongsToAppearance(p: ArchivedCompleteHpBarObserved, frameAt: Long, appearance: Long): Boolean =
        p.appearanceStartedAtNanos?.let { it <= frameAt && appearanceAt(p.side, it) == appearance } ?: true

    private fun complete(p: ArchivedCompleteHpBarObserved) =
        p.barLeft >= 0 && p.barTop >= 0 && p.barRight <= p.sourceWidth && p.barBottom < p.sourceHeight &&
            p.barRight - p.barLeft >= 40 && p.barBottom > p.barTop &&
            p.barRight - p.barLeft >= 6 * (p.barBottom - p.barTop) &&
            p.interiorColumns in 1..(p.barRight - p.barLeft)

    private fun reliable(a: ArchivedRealityArticle) = a.confidence?.let { it in .80f..1f } == true
    private fun speciesKey(name: String) = TeamRosterSpeciesAttributor.speciesKey(name)
    private fun opposes(side: String, recipient: String) = side in SIDES && recipient in SIDES && side != recipient

    companion object {
        internal const val LAST_RECIPIENT_BASIS = "UNCONTESTED_ATTACK_ON_LAST_RECIPIENT"
        private const val LAST_RECIPIENT_CONFIRMATION = 2_500_000_000L
        private val SIDES = setOf("PLAYER", "OPPONENT")
        private val USED_MOVE = Regex("(.+?) used .+[!.]?", RegexOption.IGNORE_CASE)
        private const val HP_CONFIRMATION_TAIL = 350_000_000L
        private const val MAX_CHARGE_IMPACT = 3_500_000_000L
        private const val MAX_FAINT_CONFIRMATION = 8_000_000_000L
        private const val MAX_PENDING_FAST = 2_500_000_000L
    }
}
