package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.inference.FastMoveUseEvidence
import com.example.overdex.battle.inference.LiveFinishingHitResolver
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
        val confirmed = knockout?.timing as? ReplayFaintTiming.Cry
        if (confirmed != null) return confirmed
        if (link != null && (link.basis == ReplayShieldCounter.BASIS || link.basis == LiveFinishingHitResolver.CRY_EFFECT ||
                link.basis == LiveFinishingHitResolver.AUDIBLE_FAST)) {
            link.departedBy?.let { return ReplayFaintTiming.Cry(it) }
        }
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
        if (link != null && link.basis in setOf(ReplayTerminalAudioAssociation.BASIS, ReplayTerminalAudioAssociation.ANNOUNCED)) {
            // When the archive retained the start of the reference waveform, the
            // first sample of that match gives an audible onset on the clip clock.
            val first = recoveredCries.filter { it.estimatedOnsetNanos == link.landingAtNanos &&
                it.speciesId == actorAt(side, it.estimatedOnsetNanos)?.speciesId }.flatMap { it.fragments }
                .filter { it.referenceOffsetNanos == 0L && it.articleId in link.faintArticleIds }
                .minByOrNull { it.captureAtNanos + it.queryOffsetNanos }
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

    data class TerminalCharge(val faint: ReplayKnockout, val candidate: ReplayFinishingChargeAssociation)

    /**
     * An archived charged impact can show all of a formerly living fill as orange,
     * while the subsequent departure is sampled too sparsely for the live rule.
     * This bounds the faint without promoting an orange profile to measured zero HP.
     */
    fun terminalCharge(): TerminalCharge? {
        val outcomes = articles.filter { it.payload is ArchivedMatchEnded }
        val result = outcomes.firstOrNull() ?: return null
        val outcome = (result.payload as ArchivedMatchEnded).result
        if (outcomes.any { (it.payload as ArchivedMatchEnded).result != outcome }) return null
        val side = when (outcome) {
            "WIN" -> "OPPONENT"
            "LOSS" -> "PLAYER"
            else -> return null
        }
        val end = result.monotonicTimeNanos!!
        val candidates = charges.filter {
            opposes(it.side, side) && it.completed && it.uniquelyOwned && it.moveName != null &&
                end - it.sourceAtNanos in 0..20_000_000_000L
        }
        val bars = articles.filter {
            it.sourceId == "COMPLETE_${side}_HP_BAR_FRAME_WITNESS" &&
                (it.payload as? ArchivedCompleteHpBarObserved)?.side == side
        }
        return candidates.mapNotNull { charge ->
            val from = charge.sourceAtNanos
            val owner = appearanceAt(charge.side, from)
            val appearance = appearanceAt(side, from)
            val user = actorAt(charge.side, from) ?: return@mapNotNull null
            val target = actorAt(side, from) ?: return@mapNotNull null
            if (listOf(side, charge.side).any { nextAppearance(it, from)?.let { next -> next <= end } == true })
                return@mapNotNull null
            val proof = chargeEvidence.getValue(charge)
            val banner = "${com.example.overdex.battle.BattleSpeciesDisplay.name(user.speciesName)} used ${charge.moveName}"
            if (proof.mapNotNull(byId::get).none { row ->
                    row.sourceId == "ANNOUNCEMENT_WITNESS" &&
                        (row.payload as? ArchivedRawText)?.value?.trim()?.trimEnd('!', '.')?.equals(banner, true) == true
                } || blocked(charge, side, end)) return@mapNotNull null
            val badge = lastTerminalBall(side, appearance, from, end) ?: return@mapNotNull null
            val impact = bars.zipWithNext().singleOrNull { (a, b) ->
                val at = b.monotonicTimeNanos!!
                val before = a.payload as ArchivedCompleteHpBarObserved
                val after = b.payload as ArchivedCompleteHpBarObserved
                at in from..(from + 2_000_000_000L) &&
                    at - a.monotonicTimeNanos!! in 1..600_000_000L &&
                    appearanceAt(charge.side, a.monotonicTimeNanos) == owner &&
                    appearanceAt(side, at) == appearance && witnessedDamagedBar(a, side, appearance) &&
                    reliable(b) && complete(after) && belongsToAppearance(after, at, appearance) &&
                    before.sourceWidth == after.sourceWidth && before.sourceHeight == after.sourceHeight &&
                    kotlin.math.abs(before.barLeft - after.barLeft) <= 2 &&
                    kotlin.math.abs(before.barRight - after.barRight) <= 2 &&
                    kotlin.math.abs(before.barTop - after.barTop) <= after.barBottom - after.barTop &&
                    kotlin.math.abs(after.barLeft + after.barRight - after.sourceWidth) <= after.sourceWidth / 12 &&
                    after.status in CHARGE_DAMAGE_PROFILES && after.liveColumns == 0 &&
                    after.damageColumns in maxOf(3, before.liveColumns)..after.interiorColumns &&
                    after.unmaskedUncertainColumns?.let { it in 0..after.interiorColumns / 50 } == true
            } ?: return@mapNotNull null
            val (before, orange) = impact
            val at = orange.monotonicTimeNanos!!
            val bar = orange.payload as ArchivedCompleteHpBarObserved
            if (bars.groupBy { it.monotonicTimeNanos }.let { grouped ->
                    listOf(before, orange).any { frame -> grouped.getValue(frame.monotonicTimeNanos).any { it.payload != frame.payload } }
                }) return@mapNotNull null
            val crop = articles.singleOrNull { row ->
                val p = row.payload as? ArchivedCropCaptured
                row.monotonicTimeNanos == at && row.sourceId == "CHARGED_IMPACT_HP_CAPTURE" &&
                    p?.cropName == "ChargedImpactHpCrop" && p.sourceWidth == bar.sourceWidth &&
                    p.sourceHeight == bar.sourceHeight && p.cropLeft <= bar.barLeft && p.cropRight >= bar.barRight &&
                    p.cropTop <= bar.barTop && p.cropBottom > bar.barBottom
            } ?: return@mapNotNull null
            val departure = ReplayChargeDeparture.find(articles, side, at, end, bar) ?: return@mapNotNull null
            val clear = departure.by.atNanos
            if (hasLivingHpAfter(side, appearance, at, end) ||
                articles.any { row ->
                    val time = row.monotonicTimeNanos!!
                    time in from..end && when (val p = row.payload) {
                        is ArchivedObservationRestartedByUser, is ArchivedScreenIgnoredByUser,
                        is ArchivedObservationSessionStoppedByUser -> true
                        is ArchivedSpeciesCheckMeasured -> p.reason == "SWITCH" && p.status == "OPENED" &&
                            p.triggerMonotonicNanos in from..end
                        is ArchivedFastMoveUseObserved -> p.attackingSide == side && time >= clear &&
                            FastMoveUseEvidence.establishesUse(p.evidenceKinds)
                        is ArchivedActivePokemonFaintedWitnessed -> p.side != side
                        else -> isReplayConnectionInterruption(row) ||
                            (time > from && (isReplayGetReadyCue(row) || isReplayAttackIncomingCue(row)))
                    }
                }) return@mapNotNull null
            val refs = (listOf(before.articleId, orange.articleId, crop.articleId, badge.articleId, result.articleId) +
                departure.sourceArticleIds).distinct()
            val faint = ReplayKnockout(side, target.speciesName, appearance, clear, clear, null, refs,
                basis = ReplayChargeDeparture.BASIS, timing = ReplayFaintTiming.window(
                    ReplayTimeBound(before.monotonicTimeNanos!!, before.articleId), departure.by))
            TerminalCharge(faint, ReplayFinishingChargeAssociation(charge.side, user.speciesName, owner, side,
                target.speciesName, appearance, charge.moveName, from, clear, at, null,
                (charge.articleIds + proof).sorted(), refs, emptyList(), ReplayChargeDeparture.BASIS,
                departedBy = departure.by))
        }.singleOrNull()
    }

    /** A cry on the last active appearance can establish a faint while the result reader is still waiting. */
    fun repeatCryCharge(banners: List<ReplayEndingAnnouncement>): TerminalCharge? {
        if (banners.isEmpty()) return null
        val names = banners.groupBy { it.cropArticleId }
        return articles.mapNotNull { row ->
            val repeat = row.payload as? ArchivedRepeatCryDepartureCandidate ?: return@mapNotNull null
            val side = repeat.side ?: return@mapNotNull null
            if (side !in SIDES || repeat.cryOccurrenceIds.distinct().size != 2) return@mapNotNull null
            val cryAt = row.monotonicTimeNanos!!
            val cries = repeat.cryOccurrenceIds.distinct().mapNotNull(byId::get).sortedBy { it.monotonicTimeNanos }
            if (cries.size != 2 || cries.last().monotonicTimeNanos != cryAt) return@mapNotNull null
            val sounds = cries.map { it.payload as? ArchivedBattleCryLocalized ?: return@mapNotNull null }
            if (sounds.any { it.speciesId != repeat.speciesId || it.referenceSha256.isBlank() || it.durationNanos <= 0 } ||
                sounds.first().referenceSha256 != sounds.last().referenceSha256 ||
                cries.first().monotonicTimeNanos!! + sounds.first().durationNanos >= cryAt) return@mapNotNull null
            val target = actorAt(side, cryAt)?.takeIf { it.speciesId == repeat.speciesId } ?: return@mapNotNull null
            val appearance = appearanceAt(side, cryAt)
            val cast = charges.singleOrNull { it.completed && it.uniquelyOwned && opposes(it.side, side) &&
                it.moveName != null && cryAt - it.sourceAtNanos in 0..1_500_000_000L } ?: return@mapNotNull null
            val owner = actorAt(cast.side, cast.sourceAtNanos) ?: return@mapNotNull null
            if (owner.speciesId == null || owner.speciesId == target.speciesId || owner.isFainted) return@mapNotNull null
            val text = "${owner.speciesName} used ${cast.moveName}!"
            val banner = cast.articleIds.mapNotNull(byId::get).singleOrNull {
                it.sourceId == "CHARGED_IMPACT_ANNOUNCEMENT_CAPTURE" && it.monotonicTimeNanos == cast.sourceAtNanos &&
                    names[it.articleId]?.distinct()?.singleOrNull()?.text.equals(text, true)
            } ?: return@mapNotNull null
            val impact = articles.firstOrNull {
                val bar = it.payload as? ArchivedCompleteHpBarObserved ?: return@firstOrNull false
                it.sourceId == "COMPLETE_${side}_HP_BAR_FRAME_WITNESS" && bar.side == side &&
                    it.monotonicTimeNanos!! in (cast.sourceAtNanos - 1_000_000_000L)..cryAt &&
                    reliable(it) && complete(bar) && belongsToAppearance(bar, it.monotonicTimeNanos, appearance) &&
                    bar.status in CHARGE_DAMAGE_PROFILES && bar.liveColumns == 0 && bar.damageColumns >= 3 &&
                    bar.unmaskedUncertainColumns == 0 &&
                    kotlin.math.abs(bar.barLeft + bar.barRight - bar.sourceWidth) <= bar.sourceWidth / 12
            } ?: return@mapNotNull null
            val hit = impact.monotonicTimeNanos!!
            val badge = lastTerminalBall(side, appearance, hit, cryAt) ?: return@mapNotNull null
            val live = articles.lastOrNull { it.monotonicTimeNanos!! in badge.monotonicTimeNanos!! until hit &&
                witnessedDamagedBar(it, side, appearance) } ?: return@mapNotNull null
            val ownerSince = appearanceAt(cast.side, cast.sourceAtNanos)
            if (appearanceAt(side, hit) != appearance || appearanceAt(cast.side, hit) != ownerSince ||
                appearanceAt(cast.side, cryAt) != ownerSince || nextAppearance(side, hit) != null ||
                blocked(cast, side, cryAt) || hasLivingHpAfter(side, appearance, hit, Long.MAX_VALUE)) return@mapNotNull null
            val from = minOf(hit, cast.sourceAtNanos)
            if (profiles[side to hit].orEmpty().any { it.payload != impact.payload }) return@mapNotNull null
            if (articles.any { a ->
                    val time = a.monotonicTimeNanos!!
                    when (val p = a.payload) {
                        is ArchivedFastMoveUseObserved -> (time in from..cryAt && p.attackingSide == cast.side) ||
                            (time >= cryAt && p.attackingSide == side && FastMoveUseEvidence.establishesUse(p.evidenceKinds))
                        is ArchivedRawText -> time in from..cryAt && (isReplayConnectionInterruption(a) ||
                            (a.sourceId == "ANNOUNCEMENT_WITNESS" && p.value.trim().trimEnd('!').equals("Blocked", true)))
                        is ArchivedObservationSessionStoppedByUser, is ArchivedObservationRestartedByUser,
                        is ArchivedScreenIgnoredByUser -> time in from..cryAt
                        is ArchivedMatchEnded -> time < cryAt || p.result != (if (side == "OPPONENT") "WIN" else "LOSS")
                        else -> false
                    }
                }) return@mapNotNull null
            val references = listOf(row.articleId, cries.first().articleId, cries.last().articleId, badge.articleId)
            val onset = ReplayTimeBound(cryAt, cries.last().articleId)
            val faint = ReplayKnockout(side, target.speciesName, appearance, cryAt, cryAt, null, references,
                basis = REPEAT_CHARGE, timing = ReplayFaintTiming.Cry(onset))
            TerminalCharge(faint, ReplayFinishingChargeAssociation(cast.side, owner.speciesName, ownerSince, side,
                target.speciesName, appearance, cast.moveName, cast.sourceAtNanos, cryAt, hit, null,
                (cast.articleIds + banner.articleId).sorted(), references, emptyList(), REPEAT_CHARGE,
                impactSupportArticleIds = listOf(live.articleId, impact.articleId), departedBy = onset))
        }.singleOrNull()
    }

    private fun lastTerminalBall(side: String, appearance: Long, hit: Long, end: Long): ArchivedRealityArticle? {
        val counts = articles.mapNotNull { row ->
            if (row.sourceId != "${side}_POKE_BALLS_WITNESS") return@mapNotNull null
            val count = when (val p = row.payload) {
                is ArchivedPlayerPokeBallCountMeasured -> p.visibleCount.takeIf { side == "PLAYER" && p.maximumCount == 3 }
                is ArchivedOpponentBattleResourceCountMeasured -> p.visibleCount.takeIf {
                    side == "OPPONENT" && p.resource == "POKE_BALLS" && p.maximumCount == 3
                }
                else -> null
            }
            count?.takeIf { it in 0..3 && (row.confidence == null || reliable(row)) }?.let { row to it }
        }
        val (badge, count) = counts.lastOrNull { it.first.monotonicTimeNanos!! in appearance..hit } ?: return null
        if (count != 1 || counts.any { (row, n) ->
                row.monotonicTimeNanos!! in badge.monotonicTimeNanos!!..end &&
                    (n > 1 || (row.monotonicTimeNanos <= hit && n == 0))
            }) return null
        return badge
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

    fun witnessedDamagedBar(row: ArchivedRealityArticle, side: String, appearance: Long): Boolean {
        val at = row.monotonicTimeNanos ?: return false
        if (appearanceAt(side, at) != appearance) return false
        val p = row.payload as? ArchivedCompleteHpBarObserved ?: return false
        return p.side == side && p.status == "LIVE_COLOR" && p.liveColumns in 1 until p.interiorColumns &&
            reliable(row) && complete(p) && belongsToAppearance(p, at, appearance)
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
        // A cited living bar after preparation can settle an earlier Fast Move
        // only if that specific use already cites its own nonlethal damage.
        val preChargeLiveAt = selectedCharge?.let { cast -> link.faintArticleIds.mapNotNull(byId::get)
            .filter { it.monotonicTimeNanos!! in cast.preparationAtNanos until cast.sourceAtNanos &&
                witnessedDamagedBar(it, link.recipientSide, link.recipientAppearanceAtNanos) }
            .maxOfOrNull { it.monotonicTimeNanos!! } }
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
                        !(preChargeLiveAt != null && claim.atNanos < selectedCharge.preparationAtNanos &&
                            receivedNonlethalFast(claim.articleId, link.recipientSide, claim.atNanos)) &&
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
            val earlierCharge = link.basis == ReplaySliverExchange.BASIS && other.uniquelyOwned &&
                other.side == link.attackingSide && other.sourceAtNanos < link.moveSourceAtNanos &&
                other.impactAtNanos < receipt
            val survived = earlierCharge && (articles.any { row ->
                    val bar = row.payload as? ArchivedActiveHpBarMeasured
                    row.sourceId == "CHARGED_IMPACT_${link.recipientSide}_HP_BAR_FRAME_WITNESS" &&
                        bar?.side == link.recipientSide && row.monotonicTimeNanos!! in other.sourceAtNanos until link.moveSourceAtNanos &&
                        appearanceAt(link.recipientSide, row.monotonicTimeNanos) == link.recipientAppearanceAtNanos &&
                        hp.confirmsLiveHp(row)
                } && hasLivingHpAfter(link.recipientSide, link.recipientAppearanceAtNanos,
                    other.impactAtNanos + 1, receipt - 1) || neutralSurvivalBeforeRetainedImpact(link, receipt, other))
            !survived && (other.side !in SIDES || !other.uniquelyOwned || !blocked(other, link.recipientSide, through))
        }
    }

    /** A supported Fast Move in neutral play can separate the previous charge from this later impact. */
    private fun neutralSurvivalBeforeRetainedImpact(
        link: ReplayFinishingAttribution,
        receipt: Long,
        priorCharge: ReplayFinishingChargeCandidate,
    ): Boolean {
        val evidence = link.moveArticleIds.mapNotNull(byId::get)
        if (evidence.none { it.monotonicTimeNanos == receipt && ReplayRetainedSliverReader.crop(it) != null }) return false
        return evidence.any { row ->
            val bar = row.payload as? ArchivedCompleteHpBarObserved ?: return@any false
            row.monotonicTimeNanos!! in maxOf(priorCharge.impactAtNanos + 1, link.moveSourceAtNanos) until receipt &&
                ReplaySliverExchange.inBattlePosition(bar) &&
                witnessedDamagedBar(row, link.recipientSide, link.recipientAppearanceAtNanos)
        }
    }

    private fun receivedNonlethalFast(id: String, side: String, sourceAt: Long): Boolean {
        val row = byId[id] ?: return false
        val use = row.payload as? ArchivedFastMoveUseObserved ?: return false
        if (use.damagedSide != side || "HP_DAMAGE_TICK" !in use.evidenceKinds) return false
        val tickRow = (row.predecessorIds + row.evidenceReferences.orEmpty()).distinct().mapNotNull(byId::get)
            .singleOrNull { it.payload is ArchivedActiveHpBarDamageTickMeasured } ?: return false
        val tick = tickRow.payload as ArchivedActiveHpBarDamageTickMeasured
        if (tickRow.monotonicTimeNanos != sourceAt || tick.damagedSide != side ||
            !(tick.beforeFraction <= 1f && tick.afterFraction > 0f && tick.afterFraction < tick.beforeFraction)) return false
        // The exact damage amount may be uncertain. Verify what survived that
        // specific tick using the original whole bar and its co-timed measurement.
        val key = side to sourceAt
        val profile = profiles[key].orEmpty().singleOrNull() ?: return false
        if (!witnessedDamagedBar(profile, side, appearanceAt(side, sourceAt))) return false
        val p = profile.payload as ArchivedCompleteHpBarObserved
        return hpByFrame[key].orEmpty().any { sample ->
            val bar = sample.payload as ArchivedActiveHpBarMeasured
            reliable(sample) && bar.filledFraction == tick.afterFraction && bar.barLeft == p.barLeft &&
                bar.barRight == p.barRight && bar.barTop == p.barTop && bar.barBottom == p.barBottom &&
                bar.sourceWidth == p.sourceWidth && bar.sourceHeight == p.sourceHeight
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
        internal const val REPEAT_CHARGE = "NAMED_CHARGED_IMPACT_AND_SIDED_REPEAT_CRY"
        internal const val LAST_RECIPIENT_BASIS = "UNCONTESTED_ATTACK_ON_LAST_RECIPIENT"
        private val CHARGE_DAMAGE_PROFILES = setOf("DAMAGE_COLOR", "SUSTAINED_DAMAGE_COLOR", "UNRESOLVED_COLOR")
        private const val LAST_RECIPIENT_CONFIRMATION = 2_500_000_000L
        private val SIDES = setOf("PLAYER", "OPPONENT")
        private val USED_MOVE = Regex("(.+?) used .+[!.]?", RegexOption.IGNORE_CASE)
        private const val HP_CONFIRMATION_TAIL = 350_000_000L
        private const val MAX_CHARGE_IMPACT = 3_500_000_000L
        private const val MAX_FAINT_CONFIRMATION = 8_000_000_000L
        private const val MAX_PENDING_FAST = 2_500_000_000L
    }
}
