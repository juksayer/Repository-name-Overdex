package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.observation.TeamRosterSpeciesAttributor
import com.example.overdex.battle.observation.isSideLocatedSpeciesSource
import com.example.overdex.model.Move
import kotlin.math.abs
import kotlin.math.roundToInt

/** The change is evidence of damage. Even a complete outline may have an unreadable interior. */
internal data class ReplaySliverChange(
    val live: ArchivedRealityArticle,
    val liveMeasurement: ArchivedRealityArticle,
    val damage: ArchivedRealityArticle,
    val departure: ReplayRetainedSliverDeparture?,
    val fieldFlash: Boolean = false,
) {
    val at get() = damage.monotonicTimeNanos!!
    val recipientSide get() = (damage.payload as ArchivedCompleteHpBarObserved).side
    val attackerSide get() = if (recipientSide == "PLAYER") "OPPONENT" else "PLAYER"
}

internal object ReplaySliverExchange {
    const val BASIS = "FAST_EFFECT_AND_SLIVER_CHANGE_WITH_DEPARTURE_CRY"
    private const val SECOND = 1_000_000_000L
    private fun reliable(article: ArchivedRealityArticle) = article.confidence?.let { it in .8f..1f } == true

    private fun whole(p: ArchivedCompleteHpBarObserved, side: String = "OPPONENT") = p.side == side &&
        p.barLeft >= 0 && p.barTop >= 0 && p.barRight < p.sourceWidth && p.barBottom < p.sourceHeight &&
        p.barBottom > p.barTop && p.barRight - p.barLeft >= 6 * (p.barBottom - p.barTop) &&
        p.interiorColumns in 40..(p.barRight - p.barLeft) &&
        p.liveColumns in 0..p.interiorColumns && p.damageColumns in 0..(p.interiorColumns - p.liveColumns)

    private fun aligned(a: ArchivedCompleteHpBarObserved, b: ArchivedCompleteHpBarObserved) =
        a.sourceWidth == b.sourceWidth && a.sourceHeight == b.sourceHeight &&
            abs(a.barLeft - b.barLeft) <= 2 && abs(a.barRight - b.barRight) <= 2 &&
            abs(a.barTop - b.barTop) <= a.barBottom - a.barTop

    internal fun inBattlePosition(p: ArchivedCompleteHpBarObserved) = when (p.side) {
        "PLAYER" -> p.barRight <= p.sourceWidth / 2
        "OPPONENT" -> p.barLeft >= p.sourceWidth / 2
        else -> false
    }

    fun profilePairs(
        rows: List<ArchivedRealityArticle>,
        side: String,
        retained: List<ReplayRetainedSliverProfile> = emptyList(),
    ): List<Pair<ArchivedRealityArticle, ArchivedRealityArticle>> {
        val native = rows.filter { (it.payload as? ArchivedCompleteHpBarObserved)?.side == side && it.monotonicTimeNanos != null }
        val nativeTimes = native.mapTo(mutableSetOf()) { it.monotonicTimeNanos }
        val originalCrops = rows.filter { it.monotonicTimeNanos != null && ReplayRetainedSliverReader.crop(it) != null }
            .groupBy { it.articleId }
        val fieldAnchors = retained.mapNotNull { candidate ->
            candidate.fieldLiveArticleId?.let { candidate.cropArticleId to it }
        }.toMap()
        // The projected measurement cites the crop it came from and never enters the original Timeline.
        val measured = retained.mapNotNull { candidate ->
            val source = originalCrops[candidate.cropArticleId]?.singleOrNull() ?: return@mapNotNull null
            val isField = ReplayEndingHpFrames.crop(source) != null
            val uncertainty = if (isField) candidate.profile.interiorColumns / 50 else 0
            if (source.monotonicTimeNanos in nativeTimes || candidate.profile.side != side ||
                candidate.profile.liveColumns != 0 || (source.articleId in fieldAnchors) != isField ||
                candidate.profile.unmaskedUncertainColumns?.let { it in 0..uncertainty } != true)
                return@mapNotNull null
            source.copy(payload = candidate.profile, confidence = candidate.confidence)
        }
        val profiles = (native + measured).groupBy { it.monotonicTimeNanos!! }.toSortedMap().values
        // Conflicting measurements at one capture time break the sequence in either publication order.
        fun unambiguous(frame: List<ArchivedRealityArticle>): ArchivedRealityArticle? =
            frame.minBy { it.articleId }.takeIf { a -> frame.all { it.payload == a.payload && reliable(it) } }
        return profiles.zipWithNext().mapNotNull { (earlier, later) ->
            val live = unambiguous(earlier) ?: return@mapNotNull null
            val damage = unambiguous(later) ?: return@mapNotNull null
            val a = live.payload as ArchivedCompleteHpBarObserved
            val b = damage.payload as ArchivedCompleteHpBarObserved
            val at = damage.monotonicTimeNanos!!
            val fieldAnchor = fieldAnchors[damage.articleId]
            val gap = at - live.monotonicTimeNanos!!
            val sameBar = if (fieldAnchor == null) gap in 1..SECOND && aligned(a, b) else
                fieldAnchor == live.articleId && gap in 1..ReplayEndingHpFrames.AFTER_SLIVER_NANOS
            if (!sameBar || !whole(a, side) || !whole(b, side) ||
                !inBattlePosition(a) || !inBattlePosition(b) || a.status != "LIVE_COLOR" ||
                a.liveColumns !in 1..(a.interiorColumns / 8) ||
                b.liveColumns != 0 || b.damageColumns < maxOf(2, a.liveColumns - 1) ||
                b.status !in setOf("DAMAGE_COLOR", "SUSTAINED_DAMAGE_COLOR", "UNRESOLVED_COLOR")) return@mapNotNull null
            live to damage
        }
    }

    fun changes(
        rows: List<ArchivedRealityArticle>,
        side: String = "OPPONENT",
        retained: ReplayRetainedSliverEvidence = ReplayRetainedSliverEvidence(),
    ): List<ReplaySliverChange> {
        val smallBars = rows.filter { a -> val p = a.payload as? ArchivedActiveHpBarMeasured
            p?.side == side && (p.fillColor == "RED" || p.fillColor == null) && p.filledFraction > 0 && p.filledFraction <= .15f &&
                a.monotonicTimeNanos != null && reliable(a) }.sortedBy { it.monotonicTimeNanos }
        val fieldIds = retained.profiles.filter { it.fieldLiveArticleId != null }.mapTo(hashSetOf()) { it.cropArticleId }
        return profilePairs(rows, side, retained.profiles).mapNotNull { (live, damage) ->
            val a = live.payload as ArchivedCompleteHpBarObserved
            val b = damage.payload as ArchivedCompleteHpBarObserved
            val at = damage.monotonicTimeNanos!!
            val departure = retained.departures.singleOrNull { it.damageArticleId == damage.articleId }
            val field = damage.articleId in fieldIds
            val confirmed = smallBars.lastOrNull { r ->
                val p = r.payload as ArchivedActiveHpBarMeasured
                // A legacy unlabelled fill still corroborates the live columns if its
                // geometry agrees on the same source frame, within two fill columns.
                // An independent departure sequence permits one pixel of rail rounding.
                val railTolerance = if (departure != null || field) 1 else 0
                val agrees = p.fillColor == "RED" || (r.monotonicTimeNanos == live.monotonicTimeNanos &&
                    p.barLeft == a.barLeft && p.barRight == a.barRight &&
                    abs(p.barTop - a.barTop) <= railTolerance && abs(p.barBottom - a.barBottom) <= railTolerance &&
                    abs((p.filledFraction * a.interiorColumns).roundToInt() - a.liveColumns) <= 2)
                if (field) agrees && r.monotonicTimeNanos == live.monotonicTimeNanos else
                    agrees && r.monotonicTimeNanos!! in (at - SECOND) until at && p.sourceWidth == b.sourceWidth &&
                    p.sourceHeight == b.sourceHeight && abs(p.barLeft - b.barLeft) <= 2 &&
                    abs(p.barRight - b.barRight) <= 2 && abs(p.barTop - b.barTop) <= 2 * (b.barBottom - b.barTop)
            } ?: return@mapNotNull null
            ReplaySliverChange(live, confirmed, damage, departure, field)
        }
    }

    fun changesOnBothSides(rows: List<ArchivedRealityArticle>, retained: ReplayRetainedSliverEvidence = ReplayRetainedSliverEvidence()) =
        listOf("PLAYER", "OPPONENT").flatMap { changes(rows, it, retained) }.sortedBy { it.at }

    internal fun ownership(
        change: ReplaySliverChange, rows: List<ArchivedRealityArticle>,
        actorAt: (String, Long) -> ReplayCombatant?, appearanceAt: (String, Long) -> Long,
    ): List<String>? {
        val side = change.recipientSide
        val since = appearanceAt(side, change.at)
        val before = minOf(change.live.monotonicTimeNanos!!, change.liveMeasurement.monotonicTimeNanos!!)
        val target = actorAt(side, change.at) ?: return null
        if (appearanceAt(side, before) != since) return null
        val hints = listOf(change.live, change.damage).mapNotNull { row ->
            val hint = (row.payload as ArchivedCompleteHpBarObserved).appearanceStartedAtNanos ?: return@mapNotNull null
            if (hint > row.monotonicTimeNanos!!) return null
            hint
        }
        if (hints.all { it >= since }) return emptyList()
        // A delayed badge can leave an older appearance on these two samples.
        // Resolve ownership from an independent badge within the current appearance.
        val badges = rows.filter { row ->
            val species = row.payload as? ArchivedActivePokemonSpeciesWitnessed ?: return@filter false
            species.side == side && row.monotonicTimeNanos!! in since..before &&
                isSideLocatedSpeciesSource(row.sourceId) && reliable(row)
        }
        val latest = badges.lastOrNull()?.monotonicTimeNanos ?: return null
        val current = badges.filter { it.monotonicTimeNanos == latest }
        val key = TeamRosterSpeciesAttributor.speciesKey(target.speciesName)
        if (target.speciesId == null || current.any {
                val species = it.payload as ArchivedActivePokemonSpeciesWitnessed
                species.speciesId != target.speciesId || TeamRosterSpeciesAttributor.speciesKey(species.speciesName) != key
            }) return null
        return current.map { it.articleId }.distinct().sorted()
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

    /** The same recording can corroborate a mixed waveform through its stored spectral read. */
    private fun spectralCorroboration(
        rows: List<ArchivedRealityArticle>, effect: ReplayMixedAudioCandidate, attacker: String,
    ): String? = effect.fragments.asSequence().filter { it.correlation >= .75f && it.durationNanos >= 350_000_000L }
        .mapNotNull { fragment ->
            val capture = rows.singleOrNull { it.articleId == fragment.articleId && it.monotonicTimeNanos == fragment.captureAtNanos }
            val clip = capture?.payload as? ArchivedAudioCaptured ?: return@mapNotNull null
            if (clip.sha256 != fragment.audioSha256 || clip.captureSource != "PLAYBACK_POKEMON_GO" ||
                capture.sourceId != "FAST_MOVE_STREAM_AUDIO_CAPTURE") return@mapNotNull null
            rows.singleOrNull { row ->
                val sound = row.payload as? ArchivedFastMoveSoundMeasured ?: return@singleOrNull false
                if (row.sourceId != "FAST_MOVE_SOUND_WITNESS" || row.monotonicTimeNanos != fragment.captureAtNanos ||
                    !sound.referenceMatchAccepted || listOfNotNull(sound.attackingSide, sound.cueAttackingSide).any { it != attacker })
                    return@singleOrNull false
                val best = sound.referenceCandidates.maxByOrNull { it.similarity } ?: return@singleOrNull false
                val rival = sound.referenceCandidates.filter { it.moveName != best.moveName }.maxOfOrNull { it.similarity }
                    ?: return@singleOrNull false
                best.moveName == effect.moveName && best.referenceSha256 == fragment.referenceSha256 &&
                    best.similarity >= .97f && best.changingSimilarity >= .8f && best.similarity - rival >= .06f &&
                    best.matchedDurationNanos >= 350_000_000L &&
                    abs(fragment.captureAtNanos + best.matchingOffsetNanos - best.referenceOffsetNanos -
                        fragment.estimatedOnsetNanos) <= 35_000_000L
            }?.articleId
        }.distinct().singleOrNull()

    fun hits(
        rows: List<ArchivedRealityArticle>, changes: List<ReplaySliverChange>, audio: ReplayAudioRecovery,
        ordinary: List<ReplayFastMoveCycle>, movesFor: (String, Long) -> List<Move>, equipped: (String, Long) -> String?,
        sameExchange: (Long, Long) -> Boolean, actorAt: (String, Long) -> ReplayCombatant?,
        appearanceAt: (String, Long) -> Long,
    ): List<ReplayFastMoveCycle> = changes.mapNotNull { change ->
        val at = change.at
        val side = change.attackerSide
        val possible = movesFor(side, at)
        val opponents = movesFor(change.recipientSide, at)
        if (possible.isEmpty() || opponents.isEmpty() ||
            (possible + opponents).any { it.name !in audio.referenceCandidateNames }) return@mapNotNull null
        val sounds = audio.mixedCandidates.mapNotNull { c ->
            val move = possible.firstOrNull { it.name == c.moveName } ?: return@mapNotNull null
            val turns = move.turns ?: return@mapNotNull null
            if (at - c.estimatedOnsetNanos !in 1..(turns * SECOND / 2)) return@mapNotNull null
            val coherent = qualifiedEffect(c)
            val spectrum = if (change.fieldFlash && !coherent) spectralCorroboration(rows, c, side) else null
            if (!coherent && spectrum == null) return@mapNotNull null
            c to spectrum
        }
        val (sound, spectrum) = sounds.singleOrNull() ?: return@mapNotNull null
        val start = sound.estimatedOnsetNanos
        val name = sound.moveName
        val identity = ownership(change, rows, actorAt, appearanceAt) ?: return@mapNotNull null
        val knownMove = equipped(side, start)
        // When no move was identified live, also require a distinct, strongly
        // matched effect from the same attacker appearance before this hit.
        val prior = if (knownMove == null) audio.fastEffects.lastOrNull {
            it.moveName == name && it.estimatedOnsetNanos in appearanceAt(side, start) until start &&
                it.fragments.none { f -> sound.fragments.any { current -> current.articleId == f.articleId } } &&
                movesFor(change.recipientSide, it.estimatedOnsetNanos).let { rivals ->
                    rivals.isNotEmpty() && rivals.none { rival -> rival.name == name }
                }
        } else null
        if ((knownMove != name && prior == null) || opponents.any { it.name == name } || !sameExchange(start, at) ||
            !sameExchange(change.live.monotonicTimeNanos!!, at) ||
            ordinary.any { c -> c.witness.side == side &&
                (c.witness.observedAtNanos in start..at || c.impactAtNanos in start..at) }) return@mapNotNull null
        val refs = sound.fragments.map { it.articleId } + listOfNotNull(spectrum) + identity + prior?.fragments.orEmpty().map { it.articleId } +
            listOf(change.live.articleId, change.liveMeasurement.articleId, change.damage.articleId)
        val move = possible.first { it.name == name }
        ReplayFastMoveCycle(ReplayFastMoveWitness(side, at, name, move.type, at - start, start,
            at + 120_000_000L, setOf("FAST_EFFECT_AND_COMPLETE_BAR_COLOR_CHANGE"),
            refs.toSet(), setOf(start, at, change.live.monotonicTimeNanos!!, change.liveMeasurement.monotonicTimeNanos!!) +
                sound.fragments.map { it.captureAtNanos }), start, at)
    }.groupBy { it.witness.side to it.startedAtNanos }.values.mapNotNull { it.singleOrNull() }

    data class Departure(
        val change: ReplaySliverChange,
        val faint: ReplayKnockout,
        val cry: ReplayPartialCryCandidate,
        val bound: ReplayTimeBound,
    )

    fun departures(
        rows: List<ArchivedRealityArticle>, changes: List<ReplaySliverChange>, audio: ReplayAudioRecovery,
        knockouts: List<ReplayKnockout>, actorAt: (String, Long) -> ReplayCombatant?,
        appearanceAt: (String, Long) -> Long, nextAppearance: (String, Long) -> Long?,
        hasLivingHp: (String, Long, Long, Long) -> Boolean,
    ): List<Departure> {
        if (changes.isEmpty() || audio.partialCryCandidates.isEmpty()) return emptyList()
        val results = rows.filter { it.payload is ArchivedMatchEnded }
        val result = results.firstOrNull()
        val outcome = (result?.payload as? ArchivedMatchEnded)?.result
        val end = result?.monotonicTimeNanos ?: rows.lastOrNull()?.monotonicTimeNanos ?: return emptyList()
        return changes.mapNotNull { change ->
            val at = change.at
            val side = change.recipientSide
            val attacker = change.attackerSide
            val target = actorAt(side, at) ?: return@mapNotNull null
            val attackerId = actorAt(attacker, at)?.speciesId ?: return@mapNotNull null
            if (target.speciesId == null || target.speciesId == attackerId) return@mapNotNull null
            val cry = audio.partialCryCandidates.singleOrNull { it.speciesId == target.speciesId &&
                it.fragments.isNotEmpty() && it.estimatedOnsetNanos in at..minOf(end, at + 2 * SECOND) } ?: return@mapNotNull null
            if (change.fieldFlash && cry.fragments.map { it.audioSha256 }.distinct().size < 2) return@mapNotNull null
            val cryAt = cry.estimatedOnsetNanos
            val since = appearanceAt(side, at)
            val lastLive = change.liveMeasurement.monotonicTimeNanos!!
            val identity = ownership(change, rows, actorAt, appearanceAt) ?: return@mapNotNull null
            val departure = change.departure
            val burst = departure?.let { verified ->
                val sources = rows.filter { it.articleId in verified.particleArticleIds }
                if (sources.isEmpty() || sources.size != verified.particleArticleIds.size ||
                    sources.any { (it.payload as? ArchivedTerminalParticleObservations)?.requestedRecipient != side })
                    return@let null
                rows.singleOrNull { frame -> frame.articleId == verified.cropArticleId &&
                    frame.sourceId == "${side}_TERMINAL_VISUAL_CAPTURE" &&
                    ReplayRetainedSliverReader.crop(frame) != null &&
                    frame.monotonicTimeNanos!! in cryAt..minOf(end, cryAt + 2 * SECOND) }
            }
            val fragment = cry.fragments.minBy { it.captureAtNanos + it.queryOffsetNanos }
            val localized = rows.singleOrNull { row ->
                val sound = row.payload as? ArchivedBattleCryLocalized
                sound?.speciesId == target.speciesId && sound.correlation in .95f..1f &&
                    abs(row.monotonicTimeNanos!! - cryAt) <= 50_000_000L &&
                    cry.fragments.any { it.referenceSha256 == sound.referenceSha256 }
            }
            // An independently retained whole waveform can place the same call on its audible onset.
            val whole = if (ReplayRetainedSliverReader.crop(rows.singleOrNull {
                    it.articleId == change.damage.articleId
                } ?: change.damage) != null) audio.terminalWholeCries
                .filter { it.speciesId == cry.speciesId && it.estimatedOnsetNanos in at..end &&
                    abs(it.estimatedOnsetNanos - cryAt) <= 100_000_000L }
                .flatMap { it.fragments }.filter { fit ->
                    fit.referenceOffsetNanos == 0L && fit.correlation >= .9f &&
                        cry.fragments.any { it.referenceSha256 == fit.referenceSha256 && it.articleId != fit.articleId }
                }.minByOrNull { it.estimatedOnsetNanos } else null
            // A verified visual departure can corroborate a partial match that
            // includes the actual start of the cry in the source recording.
            val measuredStart = if (burst != null) cry.fragments.filter {
                it.referenceOffsetNanos == 0L && it.correlation >= .85f &&
                    abs(it.estimatedOnsetNanos - cryAt) <= 5_000_000L
            }.minByOrNull { it.estimatedOnsetNanos } else null
            val fieldOnset = if (change.fieldFlash) matchingCryOnset(cry, audio, at) else null
            val exact = localized?.let { ReplayTimeBound(it.monotonicTimeNanos!!, it.articleId) }
                ?: (whole ?: measuredStart ?: fieldOnset)?.let { ReplayTimeBound(it.estimatedOnsetNanos, it.articleId) }
            val by = exact
                ?: ReplayTimeBound(fragment.captureAtNanos + fragment.queryOffsetNanos, fragment.articleId)
            if (by.atNanos !in at..end || appearanceAt(side, by.atNanos) != since ||
                actorAt(attacker, cryAt)?.speciesId == target.speciesId ||
                hasLivingHp(side, since, at, end) || rows.any { row ->
                    val p = row.payload as? ArchivedCompleteHpBarObserved ?: return@any false
                    p.side == side && row.monotonicTimeNanos!! in at..end && reliable(row) &&
                        whole(p, side) && inBattlePosition(p) && p.status == "LIVE_COLOR" && p.liveColumns > 0 &&
                        appearanceAt(side, row.monotonicTimeNanos) == since
                }) return@mapNotNull null
            val known = knockouts.singleOrNull { it.side == side && it.appearanceAtNanos == since &&
                it.speciesName.equals(target.speciesName, ignoreCase = true) }
            val replacement = nextAppearance(side, at)
            val through = minOf(end, replacement?.minus(1) ?: end)
            // A later outgoing attack is proof that the target was still participating.
            if (rows.any { r -> r.monotonicTimeNanos!! in lastLive..through && when (val p = r.payload) {
                    is ArchivedObservationSessionStoppedByUser, is ArchivedObservationRestartedByUser, is ArchivedScreenIgnoredByUser -> true
                    is ArchivedFastMoveUseObserved -> r.monotonicTimeNanos > by.atNanos && p.attackingSide == side
                    is ArchivedChargedMoveEnergySpent, is ArchivedChargeMoveUsedAnnounced -> true
                    else -> isReplayConnectionInterruption(r) || isReplayPlayerChargeCue(r) || isReplayAttackIncomingCue(r)
                } }) return@mapNotNull null
            val cryIds = cry.fragments.map { it.articleId } + listOfNotNull(exact?.articleId)
            val visualIds = if (burst == null) emptyList() else listOf(burst.articleId) + departure?.particleArticleIds.orEmpty()
            val evidence = identity + listOf(change.liveMeasurement.articleId, change.live.articleId, change.damage.articleId) +
                cryIds + visualIds
            val timing = if (exact != null && (whole != null || measuredStart != null || fieldOnset != null)) ReplayFaintTiming.Cry(exact)
                else ReplayFaintTiming.window(ReplayTimeBound(lastLive, change.liveMeasurement.articleId), by)
            val faint = if (known != null) {
                if (known.verifiedPokeBallsAfter !in 1..2 || known.sourceArticleIds.isEmpty() ||
                    known.replacementAtNanos?.let { it - cryAt in 0..(4 * SECOND) } != true ||
                    known.timing is ReplayFaintTiming.ZeroHp) return@mapNotNull null
                known.copy(presentationAtNanos = by.atNanos, timing = timing,
                    sourceArticleIds = (known.sourceArticleIds + evidence).distinct())
            } else {
                val defeatedSide = when (outcome) {
                    "WIN" -> "OPPONENT"
                    "LOSS" -> "PLAYER"
                    else -> null
                }
                if (result == null || defeatedSide != side || results.any { (it.payload as ArchivedMatchEnded).result != outcome } ||
                    end - cryAt !in 0..(20 * SECOND) || through != end) return@mapNotNull null
                val counts = rows.mapNotNull { row -> ballCount(row, side)?.let { row to it } }
                // A settled one-ball count remains valid for the same fighter
                // in the verified field view. A later contradictory count still wins.
                val countSince = if (change.fieldFlash) since else maxOf(since, lastLive - 10 * SECOND)
                val prior = counts.lastOrNull { (row, _) ->
                    row.monotonicTimeNanos!! in countSince..lastLive
                }
                val lastBall = prior?.takeIf { (row, count) -> count == 1 && counts.none { (later, n) ->
                    later.monotonicTimeNanos!! in row.monotonicTimeNanos!!..end && n > 1
                } }?.first
                if (lastBall == null) {
                    val damage = change.damage.payload as ArchivedCompleteHpBarObserved
                    if (burst == null || damage.unmaskedUncertainColumns?.let { it in 0..(damage.interiorColumns / 50) } != true ||
                        counts.any { (row, n) -> row.monotonicTimeNanos!! in burst.monotonicTimeNanos!!..end && n > 0 })
                        return@mapNotNull null
                }
                ReplayKnockout(side, target.speciesName, since, if (change.fieldFlash) by.atNanos else cryAt, by.atNanos, null,
                    (evidence + listOfNotNull(lastBall?.articleId, result.articleId)).distinct(), basis = DEPARTURE, timing = timing)
            }
            Departure(change, faint, cry, by)
        }.groupBy { it.faint.side to it.faint.appearanceAtNanos }.values.mapNotNull { it.singleOrNull() }
    }

    private fun matchingCryOnset(cry: ReplayPartialCryCandidate, audio: ReplayAudioRecovery, at: Long): ReplayAudioFragment? {
        val onset = audio.cries.singleOrNull { match ->
            match.speciesId == cry.speciesId && abs(match.estimatedOnsetNanos - cry.estimatedOnsetNanos) <= 20_000_000L
        } ?: return null
        return onset.fragments.filter { first ->
            first.referenceOffsetNanos == 0L && first.correlation >= .95f && first.estimatedOnsetNanos >= at &&
                cry.fragments.any { later -> later.audioSha256 != first.audioSha256 &&
                    later.referenceSha256 == first.referenceSha256 &&
                    abs(later.estimatedOnsetNanos - first.estimatedOnsetNanos) <= 20_000_000L }
        }.minByOrNull { it.estimatedOnsetNanos }
    }

    private fun ballCount(row: ArchivedRealityArticle, side: String): Int? {
        if (row.sourceId != "${side}_POKE_BALLS_WITNESS" || row.confidence != null && !reliable(row)) return null
        return when (val payload = row.payload) {
            is ArchivedPlayerPokeBallCountMeasured -> payload.visibleCount.takeIf { side == "PLAYER" && payload.maximumCount == 3 }
            is ArchivedOpponentBattleResourceCountMeasured -> payload.visibleCount.takeIf {
                side == "OPPONENT" && payload.resource == "POKE_BALLS" && payload.maximumCount == 3
            }
            else -> null
        }?.takeIf { it in 0..3 }
    }

    const val DEPARTURE = "SLIVER_CHANGE_AND_DEPARTURE_CRY"

    fun associate(
        departures: List<Departure>, hits: List<ReplayFastMoveCycle>, audio: ReplayAudioRecovery,
        actorAt: (String, Long) -> ReplayCombatant?, appearanceAt: (String, Long) -> Long,
        movesFor: (String, Long) -> List<Move>,
    ): List<ReplayFinishingFastAssociation> = departures.mapNotNull { departure ->
        val side = departure.change.attackerSide
        val recipient = departure.change.recipientSide
        val hit = hits.singleOrNull { it.witness.side == side && it.impactAtNanos == departure.change.at }
            ?: return@mapNotNull null
        val start = hit.startedAtNanos
        val impact = hit.impactAtNanos
        val cryAt = departure.cry.estimatedOnsetNanos
        val owner = actorAt(side, start) ?: return@mapNotNull null
        val faint = departure.faint
        if (appearanceAt(side, start) != appearanceAt(side, impact) ||
            appearanceAt(recipient, start) != faint.appearanceAtNanos) return@mapNotNull null
        val possible = movesFor(side, start).map { it.name }
        if (audio.mixedCandidates.any { it.moveName in possible && it.estimatedOnsetNanos in impact..cryAt &&
                qualifiedEffect(it) } || (departure.change.fieldFlash && audio.fastEffects.any {
                it.moveName in possible && it.estimatedOnsetNanos in impact..cryAt
            })) return@mapNotNull null
        // The bar change locates the impact for playback. Its uncertain interior cannot testify to zero HP.
        ReplayFinishingFastAssociation(side, owner.speciesName, recipient, faint.speciesName, faint.appearanceAtNanos,
            hit.witness.moveName, impact, start, faint.recordedAtNanos, hit.witness.articleIds.sorted(),
            departure.cry.fragments.map { it.articleId }.distinct(), faint.sourceArticleIds,
            attackerAppearanceAtNanos = appearanceAt(side, start), basis = BASIS,
            departedBy = departure.bound)
    }
}
