package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.observation.isSideLocatedSpeciesSource
import com.example.overdex.model.Move
import kotlin.math.abs

/** The change is evidence of damage. Even a complete outline may have an unreadable interior. */
internal data class ReplaySliverChange(
    val live: ArchivedRealityArticle,
    val red: ArchivedRealityArticle,
    val damage: ArchivedRealityArticle,
) {
    val at get() = damage.monotonicTimeNanos!!
}

internal object ReplaySliverExchange {
    const val BASIS = "FAST_EFFECT_AND_SLIVER_CHANGE_WITH_DEPARTURE_CRY"
    private const val SECOND = 1_000_000_000L
    private fun reliable(article: ArchivedRealityArticle) = article.confidence?.let { it in .8f..1f } == true

    private fun whole(p: ArchivedCompleteHpBarObserved) = p.side == "OPPONENT" &&
        p.barLeft >= 0 && p.barTop >= 0 && p.barRight < p.sourceWidth && p.barBottom < p.sourceHeight &&
        p.barBottom > p.barTop && p.barRight - p.barLeft >= 6 * (p.barBottom - p.barTop) &&
        p.interiorColumns in 40..(p.barRight - p.barLeft) &&
        p.liveColumns in 0..p.interiorColumns && p.damageColumns in 0..(p.interiorColumns - p.liveColumns)

    private fun aligned(a: ArchivedCompleteHpBarObserved, b: ArchivedCompleteHpBarObserved) =
        a.sourceWidth == b.sourceWidth && a.sourceHeight == b.sourceHeight &&
            abs(a.barLeft - b.barLeft) <= 2 && abs(a.barRight - b.barRight) <= 2 &&
            abs(a.barTop - b.barTop) <= a.barBottom - a.barTop

    fun changes(rows: List<ArchivedRealityArticle>): List<ReplaySliverChange> {
        val profiles = rows.filter { (it.payload as? ArchivedCompleteHpBarObserved)?.side == "OPPONENT" &&
            it.monotonicTimeNanos != null }.groupBy { it.monotonicTimeNanos!! }.toSortedMap().values
        // Conflicting measurements at one capture time break the sequence in either publication order.
        fun unambiguous(frame: List<ArchivedRealityArticle>): ArchivedRealityArticle? =
            frame.minBy { it.articleId }.takeIf { a -> frame.all { it.payload == a.payload && reliable(it) } }
        val red = rows.filter { a -> val p = a.payload as? ArchivedActiveHpBarMeasured
            p?.side == "OPPONENT" && p.fillColor == "RED" && p.filledFraction > 0 && p.filledFraction <= .15f &&
                a.monotonicTimeNanos != null && reliable(a) }.sortedBy { it.monotonicTimeNanos }
        return profiles.zipWithNext().mapNotNull { (earlier, later) ->
            val live = unambiguous(earlier) ?: return@mapNotNull null
            val damage = unambiguous(later) ?: return@mapNotNull null
            val a = live.payload as ArchivedCompleteHpBarObserved
            val b = damage.payload as ArchivedCompleteHpBarObserved
            val at = damage.monotonicTimeNanos!!
            if (at - live.monotonicTimeNanos!! !in 1..SECOND || !whole(a) || !whole(b) || !aligned(a, b) ||
                a.barLeft < a.sourceWidth / 2 || a.status != "LIVE_COLOR" || a.liveColumns !in 1..(a.interiorColumns / 8) ||
                b.liveColumns != 0 || b.damageColumns < maxOf(2, a.liveColumns - 1) ||
                b.status !in setOf("DAMAGE_COLOR", "SUSTAINED_DAMAGE_COLOR", "UNRESOLVED_COLOR")) return@mapNotNull null
            val confirmed = red.lastOrNull { r ->
                val p = r.payload as ArchivedActiveHpBarMeasured
                r.monotonicTimeNanos!! in (at - SECOND) until at && p.sourceWidth == b.sourceWidth &&
                    p.sourceHeight == b.sourceHeight && abs(p.barLeft - b.barLeft) <= 2 &&
                    abs(p.barRight - b.barRight) <= 2 && abs(p.barTop - b.barTop) <= 2 * (b.barBottom - b.barTop)
            } ?: return@mapNotNull null
            ReplaySliverChange(live, confirmed, damage)
        }
    }

    data class IncomingBar(val firstSeenAt: Long, val evidenceIds: List<String>)

    /** A named entrance can be read a frame after the replacement's full, centered bar appears. */
    fun incomingBar(rows: List<ArchivedRealityArticle>, audio: ReplayAudioRecovery, incomingId: Int,
                    announcedAt: Long): IncomingBar? {
        val cries = audio.partialCryCandidates.filter { announcedAt - it.estimatedOnsetNanos in 0..(3 * SECOND) }
        if (cries.isEmpty()) return null
        fun badge(a: ArchivedRealityArticle) = (a.payload as? ArchivedActivePokemonSpeciesWitnessed)
            ?.takeIf { it.side == "OPPONENT" && isSideLocatedSpeciesSource(a.sourceId) }
        val previous = rows.lastOrNull { it.monotonicTimeNanos!! < announcedAt && badge(it) != null }
            ?: return null
        val incoming = rows.firstOrNull { it.monotonicTimeNanos!! >= announcedAt && badge(it) != null }
            ?: return null
        if (badge(previous)?.speciesId == incomingId || badge(incoming)?.speciesId != incomingId ||
            incoming.monotonicTimeNanos!! - announcedAt > 15 * SECOND) return null
        val cry = cries.singleOrNull { it.speciesId == badge(previous)?.speciesId } ?: return null
        val adjacent = rows.filter { r -> val p = r.payload as? ArchivedCompleteHpBarObserved
            p?.side == "OPPONENT" && r.monotonicTimeNanos?.let { it in (announcedAt - SECOND / 4)..(announcedAt + SECOND / 2) } == true
        }.sortedBy { it.monotonicTimeNanos }
        val before = adjacent.lastOrNull { it.monotonicTimeNanos!! < announcedAt } ?: return null
        val after = adjacent.firstOrNull { it.monotonicTimeNanos!! >= announcedAt } ?: return null
        fun entering(r: ArchivedRealityArticle): Boolean {
            val p = r.payload as ArchivedCompleteHpBarObserved
            return reliable(r) && whole(p) && p.status == "LIVE_COLOR" &&
                p.liveColumns >= .95f * p.interiorColumns &&
                abs(p.barLeft + p.barRight - p.sourceWidth) <= p.sourceWidth / 12
        }
        if (!entering(before) || !entering(after)) return null
        val a = before.payload as ArchivedCompleteHpBarObserved
        val b = after.payload as ArchivedCompleteHpBarObserved
        // As the incoming sprite grows, its full bar rises while its end caps remain aligned horizontally.
        if (a.sourceWidth != b.sourceWidth || a.sourceHeight != b.sourceHeight ||
            abs(a.barLeft - b.barLeft) > 2 || abs(a.barRight - b.barRight) > 2 ||
            a.barTop - b.barTop !in -2..(2 * (a.barBottom - a.barTop))) return null
        return IncomingBar(before.monotonicTimeNanos!!, listOf(before, after, previous, incoming)
            .map { it.articleId } + cry.fragments.map { it.articleId }.distinct())
    }

    private fun qualifiedEffect(candidate: ReplayMixedAudioCandidate): Boolean =
        candidate.fragments.any { a -> candidate.fragments.any { b ->
            a.referenceSha256 == b.referenceSha256 && a.durationNanos >= SECOND / 5 &&
                a.durationNanos == b.durationNanos && minOf(a.correlation, b.correlation) >= .35f &&
                abs(a.referenceOffsetNanos - b.referenceOffsetNanos) >= a.durationNanos &&
                abs(a.estimatedOnsetNanos - b.estimatedOnsetNanos) <= 5_000_000L
        } }

    fun hits(
        changes: List<ReplaySliverChange>, audio: ReplayAudioRecovery, ordinary: List<ReplayFastMoveCycle>,
        playerMoves: (Long) -> List<Move>, equipped: (Long) -> String?, opponentMoves: (Long) -> List<Move>,
        sameExchange: (Long, Long) -> Boolean, opponentSince: (Long) -> Long,
    ): List<ReplayFastMoveCycle> = changes.mapNotNull { change ->
        val at = change.at
        val possible = playerMoves(at)
        val opponents = opponentMoves(at)
        if (possible.isEmpty() || opponents.isEmpty() ||
            (possible + opponents).any { it.name !in audio.referenceCandidateNames }) return@mapNotNull null
        val sounds = audio.mixedCandidates.filter { c ->
            val move = possible.firstOrNull { it.name == c.moveName } ?: return@filter false
            val turns = move.turns ?: return@filter false
            at - c.estimatedOnsetNanos in 1..(turns * SECOND / 2) &&
                qualifiedEffect(c)
        }
        val sound = sounds.singleOrNull() ?: return@mapNotNull null
        val start = sound.estimatedOnsetNanos
        val name = sound.moveName
        if (listOf(change.live, change.damage).any { article ->
                (article.payload as ArchivedCompleteHpBarObserved).appearanceStartedAtNanos?.let { appearance ->
                    appearance !in opponentSince(at)..article.monotonicTimeNanos!!
                } == true
            }) return@mapNotNull null
        if (equipped(start) != name || opponents.any { it.name == name } || !sameExchange(start, at) ||
            !sameExchange(change.live.monotonicTimeNanos!!, at) ||
            ordinary.any { c -> c.witness.side == "PLAYER" &&
                (c.witness.observedAtNanos in start..at || c.impactAtNanos in start..at) }) return@mapNotNull null
        val refs = sound.fragments.map { it.articleId } + listOf(change.live.articleId, change.red.articleId, change.damage.articleId)
        val move = possible.first { it.name == name }
        ReplayFastMoveCycle(ReplayFastMoveWitness("PLAYER", at, name, move.type, at - start, start,
            at + 120_000_000L, setOf("FAST_EFFECT_AND_COMPLETE_BAR_COLOR_CHANGE"),
            refs.toSet(), setOf(start, at, change.live.monotonicTimeNanos!!, change.red.monotonicTimeNanos!!) +
                sound.fragments.map { it.captureAtNanos }), start, at)
    }.groupBy { it.startedAtNanos }.values.mapNotNull { it.singleOrNull() }

    data class Departure(val change: ReplaySliverChange, val faint: ReplayKnockout, val cry: ReplayPartialCryCandidate)

    fun departures(
        rows: List<ArchivedRealityArticle>, changes: List<ReplaySliverChange>, audio: ReplayAudioRecovery,
        knockouts: List<ReplayKnockout>, actorAt: (String, Long) -> ReplayCombatant?,
        appearanceAt: (String, Long) -> Long, nextAppearance: (String, Long) -> Long?,
        hasLivingHp: (String, Long, Long, Long) -> Boolean,
    ): List<Departure> {
        if (changes.isEmpty() || audio.partialCryCandidates.isEmpty()) return emptyList()
        val result = rows.firstOrNull { it.payload is ArchivedMatchEnded }
        val end = result?.monotonicTimeNanos ?: rows.lastOrNull()?.monotonicTimeNanos ?: return emptyList()
        return changes.mapNotNull { change ->
            val at = change.at
            val target = actorAt("OPPONENT", at) ?: return@mapNotNull null
            val playerId = actorAt("PLAYER", at)?.speciesId ?: return@mapNotNull null
            if (target.speciesId == null || target.speciesId == playerId) return@mapNotNull null
            val cry = audio.partialCryCandidates.singleOrNull { it.speciesId == target.speciesId &&
                it.fragments.isNotEmpty() && it.estimatedOnsetNanos in at..minOf(end, at + 2 * SECOND) } ?: return@mapNotNull null
            val cryAt = cry.estimatedOnsetNanos
            val since = appearanceAt("OPPONENT", at)
            val lastLive = change.red.monotonicTimeNanos!!
            if (listOf(change.live, change.damage).any { row ->
                    (row.payload as ArchivedCompleteHpBarObserved).appearanceStartedAtNanos?.let { appearance ->
                        appearance !in since..row.monotonicTimeNanos!!
                    } == true
                }) return@mapNotNull null
            val fragment = cry.fragments.minBy { it.captureAtNanos + it.queryOffsetNanos }
            val by = ReplayTimeBound(fragment.captureAtNanos + fragment.queryOffsetNanos, fragment.articleId)
            if (by.atNanos !in cryAt..end || appearanceAt("OPPONENT", minOf(change.live.monotonicTimeNanos!!, lastLive)) != since ||
                appearanceAt("OPPONENT", by.atNanos) != since || actorAt("PLAYER", cryAt)?.speciesId == target.speciesId ||
                hasLivingHp("OPPONENT", since, at, end)) return@mapNotNull null
            val known = knockouts.singleOrNull { it.side == "OPPONENT" && it.appearanceAtNanos == since &&
                it.speciesName.equals(target.speciesName, ignoreCase = true) }
            val replacement = nextAppearance("OPPONENT", at)
            val through = minOf(end, replacement?.minus(1) ?: end)
            // A later outgoing attack is proof that the target was still participating.
            if (rows.any { r -> r.monotonicTimeNanos!! in lastLive..through && when (val p = r.payload) {
                    is ArchivedObservationSessionStoppedByUser, is ArchivedObservationRestartedByUser, is ArchivedScreenIgnoredByUser -> true
                    is ArchivedFastMoveUseObserved -> r.monotonicTimeNanos > by.atNanos && p.attackingSide == "OPPONENT"
                    is ArchivedChargedMoveEnergySpent, is ArchivedChargeMoveUsedAnnounced -> true
                    else -> isReplayPlayerChargeCue(r) || isReplayAttackIncomingCue(r)
                } }) return@mapNotNull null
            val cryIds = cry.fragments.map { it.articleId }
            val colorIds = listOf(change.red.articleId, change.live.articleId, change.damage.articleId)
            val timing = ReplayFaintTiming.window(ReplayTimeBound(lastLive, change.red.articleId), by)
            val faint = if (known != null) {
                if (known.verifiedPokeBallsAfter !in 1..2 || known.sourceArticleIds.isEmpty() ||
                    known.replacementAtNanos?.let { it - cryAt in 0..(4 * SECOND) } != true ||
                    known.timing is ReplayFaintTiming.ZeroHp) return@mapNotNull null
                known.copy(presentationAtNanos = by.atNanos, timing = timing,
                    sourceArticleIds = (known.sourceArticleIds + colorIds + cryIds).distinct())
            } else {
                if ((result?.payload as? ArchivedMatchEnded)?.result != "WIN" ||
                    end - cryAt !in 0..(20 * SECOND) || through != end) return@mapNotNull null
                val counts = rows.filter { a -> val p = a.payload as? ArchivedOpponentBattleResourceCountMeasured
                    p?.resource == "POKE_BALLS" && p.maximumCount == 3 && a.sourceId == "OPPONENT_POKE_BALLS_WITNESS" &&
                        (a.confidence == null || reliable(a)) }
                val prior = counts.lastOrNull { it.monotonicTimeNanos!! in maxOf(since, lastLive - 10 * SECOND)..lastLive }
                    ?: return@mapNotNull null
                if ((prior.payload as ArchivedOpponentBattleResourceCountMeasured).visibleCount != 1 ||
                    counts.any { it.monotonicTimeNanos!! in prior.monotonicTimeNanos!!..end &&
                        (it.payload as ArchivedOpponentBattleResourceCountMeasured).visibleCount !in 0..1 }) return@mapNotNull null
                ReplayKnockout("OPPONENT", target.speciesName, since, cryAt, by.atNanos, null,
                    (colorIds + cryIds + prior.articleId + result.articleId).distinct(), basis = DEPARTURE, timing = timing)
            }
            Departure(change, faint, cry)
        }.groupBy { it.faint.appearanceAtNanos }.values.mapNotNull { it.singleOrNull() }
    }

    const val DEPARTURE = "LAST_OPPONENT_SLIVER_CHANGE_AND_DEPARTURE_CRY"

    fun associate(
        departures: List<Departure>, hits: List<ReplayFastMoveCycle>, audio: ReplayAudioRecovery,
        actorAt: (String, Long) -> ReplayCombatant?, appearanceAt: (String, Long) -> Long,
        playerMoves: (Long) -> List<Move>,
    ): List<ReplayFinishingFastAssociation> = departures.mapNotNull { departure ->
        val hit = hits.singleOrNull { it.impactAtNanos == departure.change.at } ?: return@mapNotNull null
        val start = hit.startedAtNanos
        val impact = hit.impactAtNanos
        val cryAt = departure.cry.estimatedOnsetNanos
        val owner = actorAt("PLAYER", start) ?: return@mapNotNull null
        val faint = departure.faint
        if (appearanceAt("PLAYER", start) != appearanceAt("PLAYER", impact) ||
            appearanceAt("OPPONENT", start) != faint.appearanceAtNanos) return@mapNotNull null
        val possible = playerMoves(start).map { it.name }
        if (audio.mixedCandidates.any { it.moveName in possible && it.estimatedOnsetNanos in impact..cryAt &&
                qualifiedEffect(it) }) return@mapNotNull null
        // The bar change locates the impact for playback. Its uncertain interior cannot testify to zero HP.
        ReplayFinishingFastAssociation("PLAYER", owner.speciesName, "OPPONENT", faint.speciesName, faint.appearanceAtNanos,
            hit.witness.moveName, impact, start, faint.recordedAtNanos, hit.witness.articleIds.sorted(),
            departure.cry.fragments.map { it.articleId }.distinct(), faint.sourceArticleIds,
            attackerAppearanceAtNanos = appearanceAt("PLAYER", start), basis = BASIS,
            departedBy = (faint.timing as ReplayFaintTiming.Window).by)
    }
}
