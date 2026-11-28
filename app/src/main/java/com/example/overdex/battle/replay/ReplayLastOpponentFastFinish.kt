package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.inference.TerminalCollapseSequences
import com.example.overdex.battle.inference.TerminalEffectFrame
import com.example.overdex.data.GameMasterLoader
import com.example.overdex.model.Move
import com.example.overdex.model.PokemonType
import java.io.InputStream
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Retrospective audio inference. The cry is a presentation boundary, never a measured damage tick. */
@kotlinx.serialization.Serializable
data class ReplayLastOpponentFastEvidence(
    val audio: ReplayAudioRecovery,
    @kotlinx.serialization.SerialName("playerMoves") val attackingMoves: Map<String, Move>,
    @kotlinx.serialization.SerialName("opponentMoves") val defendingMoves: Set<String>,
    val cryId: String,
    val recipientSide: String = "OPPONENT",
) {
    val attackingSide get() = if (recipientSide == "PLAYER") "OPPONENT" else "PLAYER"
}

internal object ReplayLastOpponentFastFinish {
    const val BASIS = "LAST_OPPONENT_CRY_AND_RECEIVED_FAST_MOVE_PATTERN"
    const val MIXED_NEAR_DEPARTURE = "SIDE_SCOPED_SOUND_AND_FINAL_DEPARTURE_CRY"
    const val PLAYER_FAINT = "LAST_PLAYER_CRY_AND_RECEIVED_FAST_MOVE_PATTERN"
    val bases = setOf(BASIS, PLAYER_FAINT)
    private const val S = 1_000_000_000L

    suspend fun read(archive: MatchArchive, catalog: GameMasterLoader, open: () -> InputStream?,
                     asset: (String) -> ByteArray): ReplayLastOpponentFastEvidence? = read(archive, catalog, open, asset, null)

    suspend fun read(
        archive: MatchArchive, catalog: GameMasterLoader,
        open: () -> InputStream?, asset: (String) -> ByteArray, cache: ReplayReviewCache?,
    ): ReplayLastOpponentFastEvidence? = withContext(Dispatchers.Default) {
        val rows = archive.articles.filter { it.matchId == archive.matchId && it.monotonicTimeNanos != null }
            .sortedBy { it.monotonicTimeNanos }
        val cry = rows.lastOrNull { it.payload is ArchivedBattleCryLocalized } ?: return@withContext null
        val at = cry.monotonicTimeNanos!!
        val sound = cry.payload as ArchivedBattleCryLocalized
        fun active(side: String) = rows.lastOrNull { it.monotonicTimeNanos!! <= at &&
            (it.payload as? ArchivedActivePokemonSpeciesWitnessed)?.side == side }?.payload as? ArchivedActivePokemonSpeciesWitnessed
        val player = active("PLAYER")?.speciesId ?: return@withContext null
        val opponent = active("OPPONENT")?.speciesId ?: return@withContext null
        if (player == opponent || sound.correlation < .95f) return@withContext null
        val recipient = when (sound.speciesId) {
            player -> "PLAYER"
            opponent -> "OPPONENT"
            else -> return@withContext null
        }
        val badge = rows.lastOrNull { it.monotonicTimeNanos!! <= at && it.remainingBalls(recipient) != null }
        if (badge?.remainingBalls(recipient) != 1) return@withContext null
        // A bare species badge leaves form open. Include every indexed form on both sides.
        fun moves(id: Int): Map<String, Move>? {
            val ids = catalog.allPokemon().filter { it.dex == id }.flatMap { it.fastMoves }.distinct()
            return ids.map { moveId -> catalog.getMove(moveId) ?: return null }.associate { m ->
                m.name to Move(m.name, PokemonType.valueOf(m.type.uppercase()), m.power, m.energyGain, true, m.turns)
            }
        }
        val playerMoves = moves(player) ?: return@withContext null
        val opponentMoves = moves(opponent) ?: return@withContext null
        if (playerMoves.isEmpty() || opponentMoves.isEmpty()) return@withContext null
        val audio = ArchivedEndingAudioRecognizer.recognize(archive, (playerMoves + opponentMoves).values.toList(),
            setOf(player, opponent), open, (at - 6*S)..at, null, asset, cache)
        if (recipient == "OPPONENT") ReplayLastOpponentFastEvidence(audio, playerMoves, opponentMoves.keys, cry.articleId)
        else ReplayLastOpponentFastEvidence(audio, opponentMoves, playerMoves.keys, cry.articleId, recipient)
    }

    private fun ArchivedRealityArticle.remainingBalls(side: String): Int? = when (val p = payload) {
        is ArchivedOpponentBattleResourceCountMeasured -> p.visibleCount.takeIf {
            side == "OPPONENT" && p.maximumCount == 3 && p.resource == "POKE_BALLS"
        }
        is ArchivedPlayerPokeBallCountMeasured -> p.visibleCount.takeIf { side == "PLAYER" && p.maximumCount == 3 }
        else -> null
    }

    fun resolve(
        evidence: ReplayLastOpponentFastEvidence, rows: List<ArchivedRealityArticle>,
        owner: ReplayCombatant, target: ReplayCombatant, ownerSince: Long, targetSince: Long, darkCropIds: List<String> = emptyList(),
    ): ReplayFinishingFastAssociation? {
        val cry = rows.singleOrNull { it.articleId == evidence.cryId } ?: return null
        val at = cry.monotonicTimeNanos!!
        val sound = cry.payload as? ArchivedBattleCryLocalized ?: return null
        val attacker = evidence.attackingSide
        val recipient = evidence.recipientSide
        val expectedOutcome = if (recipient == "OPPONENT") "WIN" else "LOSS"
        val audio = evidence.audio
        if (target.speciesId != sound.speciesId || owner.speciesId == target.speciesId ||
            owner.isFainted || target.isFainted || audio.unavailableReferences.isNotEmpty() ||
            audio.unavailableArtifacts.isNotEmpty()) return null
        val cryMatch = audio.cries.singleOrNull { it.speciesId == target.speciesId &&
            abs(it.estimatedOnsetNanos - at) <= 50_000_000L &&
            it.fragments.any { fragment -> fragment.referenceSha256 == sound.referenceSha256 } } ?: return null
        fun strong(c: ReplayMixedAudioCandidate) = c.fragments.any { a -> c.fragments.any { b ->
            a.referenceSha256 == b.referenceSha256 && minOf(a.correlation, b.correlation) >= .5f &&
                abs(a.estimatedOnsetNanos - b.estimatedOnsetNanos) <= 5_000_000L &&
                abs(a.referenceOffsetNanos - b.referenceOffsetNanos) >= a.durationNanos
        } }
        val candidates = audio.mixedCandidates.filter { it.moveName in evidence.attackingMoves }
        val possible = candidates.filter { candidate ->
            val turns = evidence.attackingMoves[candidate.moveName]?.turns ?: return@filter false
            at - candidate.estimatedOnsetNanos in 0..(turns * S / 2)
        }
        val attack = possible.singleOrNull() ?: return null
        val start = attack.estimatedOnsetNanos
        val move = evidence.attackingMoves.getValue(attack.moveName)
        if (!strong(attack) || attack.moveName in evidence.defendingMoves || start < maxOf(ownerSince,targetSince) ||
            at - start > (move.turns ?: return null)*S/2 ||
            audio.fastEffects.any { it.estimatedOnsetNanos in start..at && it.moveName in evidence.attackingMoves &&
                (it.moveName != attack.moveName || abs(it.estimatedOnsetNanos-start) > 80_000_000L) }) return null
        fun outcome(row: ArchivedRealityArticle) = when (val p = row.payload) {
            is ArchivedMatchEnded -> p.result
            is ArchivedMatchOutcomeLabelWitnessed -> p.result
            else -> null
        }
        val outcomes = rows.filter { outcome(it) in setOf("WIN", "LOSS", "DRAW") }
        if (outcomes.any { outcome(it) != expectedOutcome || it.monotonicTimeNanos!! < at }) return null
        val result = outcomes.firstOrNull()
        val end = rows.maxOf { it.monotonicTimeNanos!! }
        val badge = rows.lastOrNull { it.monotonicTimeNanos!! <= start && it.remainingBalls(recipient) != null } ?: return null
        if (badge.remainingBalls(recipient) != 1 || start - badge.monotonicTimeNanos!! > 30*S) return null
        if (rows.any { r -> r.monotonicTimeNanos!! in start-S..end && when (val p = r.payload) {
            is ArchivedAttackIncoming, is ArchivedGetReadyWitnessed, is ArchivedChargeMoveUsedAnnounced,
            is ArchivedChargedMoveEnergySpent, is ArchivedChargeMoveQteVibrationPatternInferred -> true
            is ArchivedObservationRestartedByUser, is ArchivedObservationSessionStoppedByUser,
            is ArchivedScreenIgnoredByUser -> r.monotonicTimeNanos <= at
            is ArchivedFastMoveUseObserved -> r.monotonicTimeNanos >= start && (p.attackingSide != recipient || r.monotonicTimeNanos >= at)
            is ArchivedActiveHpBarDamageTickMeasured -> r.monotonicTimeNanos >= start && (p.damagedSide != attacker || r.monotonicTimeNanos >= at)
            is ArchivedBattleCryLocalized -> r.articleId != cry.articleId
            is ArchivedOpponentBattleResourceCountMeasured, is ArchivedPlayerPokeBallCountMeasured -> (r.remainingBalls(recipient) ?: 0) > 1
            is ArchivedActivePokemonSpeciesWitnessed -> p.speciesId != (if (p.side == attacker) owner.speciesId else target.speciesId)
            else -> false
        } }) return null
        val sliver = rows.lastOrNull { r -> val p = r.payload as? ArchivedCompleteHpBarObserved
            p?.side == recipient && r.monotonicTimeNanos!! in start..at } ?: return null
        val bar = sliver.payload as ArchivedCompleteHpBarObserved
        if (sliver.confidence?.let { it in .8f..1f } != true ||
            bar.appearanceStartedAtNanos?.let { it !in targetSince..sliver.monotonicTimeNanos!! } == true ||
            bar.barLeft < 0 || bar.barTop < 0 ||
            bar.barRight >= bar.sourceWidth || bar.barBottom >= bar.sourceHeight || bar.barBottom <= bar.barTop ||
            bar.barRight-bar.barLeft < 6*(bar.barBottom-bar.barTop) || bar.interiorColumns !in 40..bar.barRight-bar.barLeft) return null
        val burst = departureBurstReferences(rows, bar, start, at + 2*S)
        val blackout = if (darkCropIds.size < 2) null else ReplayEndingAnnouncements.afterFinalCry(rows, cry)
            .zipWithNext().firstOrNull { (a, b) ->
                a.articleId in darkCropIds && b.articleId in darkCropIds &&
                    b.monotonicTimeNanos!! - a.monotonicTimeNanos!! in 100_000_000L..1_500_000_000L
            }
        if (result == null && burst.isEmpty() && blackout == null) return null
        val resolvedInterior = bar.liveColumns == 0 && bar.unmaskedUncertainColumns == 0 &&
            sliver.sourceId == "COMPLETE_${recipient}_HP_BAR_FRAME_WITNESS" &&
            when (bar.status) {
                "VISIBLE_EMPTY" -> bar.damageColumns == 0
                "DAMAGE_COLOR", "SUSTAINED_DAMAGE_COLOR" -> bar.damageColumns in 1..bar.interiorColumns
                else -> false
            }
        if (resolvedInterior) {
            val laterReadings = rows.filter { row ->
                (row.payload as? ArchivedActiveHpBarMeasured)?.side == recipient &&
                    row.monotonicTimeNanos!! in (sliver.monotonicTimeNanos!! + 1)..at
            }
            if (laterReadings.isNotEmpty()) {
                val hp = ReplayHpEvidence(rows)
                if (laterReadings.any { hp.confirmsLiveHp(it) }) return null
            }
        }
        val hpRefs = if (resolvedInterior) emptyList() else
            comparableHits(rows, bar, recipient, ownerSince, targetSince, start,
                candidates.filter { it.moveName == attack.moveName && strong(it) }) ?: return null
        val refs = attack.fragments.map { it.articleId }.distinct()
        return ReplayFinishingFastAssociation(attacker,owner.speciesName,recipient,target.speciesName,targetSince,
            move.name,at,start,at,refs, (cryMatch.fragments.map { it.articleId } + hpRefs + sliver.articleId).distinct(),
            (listOfNotNull(badge.articleId, cry.articleId, result?.articleId, blackout?.first?.articleId, blackout?.second?.articleId) + burst).distinct(), ownerSince, basis = if (recipient == "OPPONENT") BASIS else PLAYER_FAINT,
            departedBy = ReplayTimeBound(at, cry.articleId))
    }

    /** A match-level audio winner can mask a second sound at a different offset.
     * Use this only after an independent terminal faint and a matching HP change. */
    fun mixedAtConfirmedDeparture(
        rows: List<ArchivedRealityArticle>, knockout: ReplayKnockout, owner: ReplayCombatant,
        ownerSince: Long, moveName: String, duration: Long, appearanceAt: (String, Long) -> Long,
    ): ReplayFinishingFastAssociation? {
        if (knockout.basis != com.example.overdex.battle.inference.DepartureCryFaint.LAST ||
            knockout.replacementAtNanos != null || owner.isFainted) return null
        val cry = (knockout.timing as? ReplayFaintTiming.Cry)?.onset ?: return null
        val recipient = knockout.side
        val attacker = if (recipient == "OPPONENT") "PLAYER" else "OPPONENT"
        val at = cry.atNanos
        val from = maxOf(ownerSince, knockout.appearanceAtNanos, at - 2 * duration)
        fun samePair(time: Long) = appearanceAt(attacker, time) == ownerSince &&
            appearanceAt(recipient, time) == knockout.appearanceAtNanos
        val exchange = rows.filter { it.monotonicTimeNanos!! in (from - S)..at }
        val byId = exchange.associateBy { it.articleId }
        val matches = exchange.mapNotNull { soundRow ->
            val sound = soundRow.payload as? ArchivedFastMoveSoundMeasured ?: return@mapNotNull null
            if (!sound.audible || soundRow.sourceId != "FAST_MOVE_SOUND_WITNESS") return@mapNotNull null
            val user = sound.referenceSearchScopes.singleOrNull { it.side == attacker } ?: return@mapNotNull null
            val other = sound.referenceSearchScopes.singleOrNull { it.side == recipient } ?: return@mapNotNull null
            if (!user.speciesName.equals(owner.speciesName, true) ||
                !other.speciesName.equals(knockout.speciesName, true) ||
                user.moveNames.singleOrNull() != moveName || moveName in other.moveNames) return@mapNotNull null
            val candidate = sound.referenceCandidates.singleOrNull {
                it.moveName == moveName && it.similarity in .90f..1f && it.changingSimilarity in .75f..1f &&
                    it.referenceOffsetNanos >= 0 && it.matchedDurationNanos in 300_000_000L..1_200_000_000L
            } ?: return@mapNotNull null
            // Reject a runner-up fit measured on the same part of the clip as
            // the stronger opposing sound.
            val competitor = sound.referenceCandidates.maxByOrNull { it.similarity } ?: return@mapNotNull null
            if (competitor.moveName !in other.moveNames || competitor.similarity !in .90f..1f ||
                competitor.matchedDurationNanos !in 300_000_000L..1_200_000_000L) return@mapNotNull null
            val clip = soundRow.evidenceReferences.orEmpty().mapNotNull(byId::get).singleOrNull {
                val audio = it.payload as? ArchivedAudioCaptured
                audio?.captureSource == "PLAYBACK_POKEMON_GO" && it.monotonicTimeNanos == soundRow.monotonicTimeNanos &&
                    candidate.matchingOffsetNanos in 0..(audio.durationNanos - candidate.matchedDurationNanos) &&
                    competitor.matchingOffsetNanos in 0..(audio.durationNanos - competitor.matchedDurationNanos)
            } ?: return@mapNotNull null
            if (candidate.matchingOffsetNanos < competitor.matchingOffsetNanos + competitor.matchedDurationNanos &&
                competitor.matchingOffsetNanos < candidate.matchingOffsetNanos + candidate.matchedDurationNanos) return@mapNotNull null
            val segment = clip.monotonicTimeNanos!! + candidate.matchingOffsetNanos
            val onset = segment - candidate.referenceOffsetNanos
            if (onset !in from..at || segment + candidate.matchedDurationNanos > at || !samePair(onset)) return@mapNotNull null
            Triple(onset, soundRow.articleId, clip.articleId)
        }
        val first = matches.minOfOrNull { it.first } ?: return null
        val audioSources = matches.map { byId.getValue(it.third) }
        if (audioSources.map { (it.payload as ArchivedAudioCaptured).sha256 }.distinct().size < 2 ||
            audioSources.map { it.monotonicTimeNanos }.distinct().size < 2 ||
            matches.any { it.first - first > 80_000_000L }) return null
        val start = matches.maxOf { it.first }
        if (exchange.any { it.monotonicTimeNanos!! >= start - 250_000_000L &&
                (it.payload == ArchivedGetReadyWitnessed || it.payload == ArchivedAttackIncoming ||
                    it.payload == ArchivedChargeMoveUsedAnnounced || it.payload is ArchivedChargedMoveEnergySpent) }) return null
        val changed = exchange.lastOrNull { row ->
            val bar = row.payload as? ArchivedCompleteHpBarObserved ?: return@lastOrNull false
            bar.side == recipient && row.sourceId == "COMPLETE_${recipient}_HP_BAR_FRAME_WITNESS" &&
                row.confidence?.let { it in .90f..1f } == true && samePair(row.monotonicTimeNanos!!) &&
                abs(row.monotonicTimeNanos - start) <= 250_000_000L &&
                ReplaySliverExchange.inBattlePosition(bar) &&
                bar.liveColumns == 0 && bar.damageColumns in 1..bar.interiorColumns &&
                bar.status in setOf("DAMAGE_COLOR", "SUSTAINED_DAMAGE_COLOR", "UNRESOLVED_COLOR")
        } ?: return null
        val hp = ReplayHpEvidence(rows)
        if (exchange.any { row -> row.monotonicTimeNanos!! > changed.monotonicTimeNanos!! && samePair(row.monotonicTimeNanos) &&
                (row.payload as? ArchivedActiveHpBarMeasured)?.side == recipient && hp.confirmsLiveHp(row) }) return null
        return ReplayFinishingFastAssociation(attacker, owner.speciesName, recipient, knockout.speciesName,
            knockout.appearanceAtNanos, moveName, minOf(at, start + duration), start, at,
            matches.flatMap { listOf(it.second, it.third) }.distinct(), listOf(changed.articleId),
            knockout.sourceArticleIds, ownerSince, basis = MIXED_NEAR_DEPARTURE, departedBy = cry)
    }

    private fun comparableHits(
        rows: List<ArchivedRealityArticle>, bar: ArchivedCompleteHpBarObserved, recipient: String,
        ownerSince: Long, targetSince: Long, start: Long, candidates: List<ReplayMixedAudioCandidate>,
    ): List<String>? {
        if (bar.status != "LIVE_COLOR" || bar.liveColumns !in 1..bar.interiorColumns ||
            bar.damageColumns !in 1..(bar.interiorColumns - bar.liveColumns)) return null
        // A complete outline may still contain unknown background columns. Use an
        // independently measured, settled red fraction as the pre-hit upper estimate.
        val hpEvidence = ReplayHpEvidence(rows)
        val previousHp = rows.filter { r ->
            val p = r.payload as? ArchivedActiveHpBarMeasured
            p?.side == recipient && p.fillColor == "RED" && r.confidence?.let { it in .8f..1f } == true &&
                r.monotonicTimeNanos!! in maxOf(targetSince, start - S) until start && hpEvidence.confirmsLiveHp(r)
        }.takeLast(2)
        if (previousHp.size < 2 || previousHp[0].monotonicTimeNanos == previousHp[1].monotonicTimeNanos) return null
        val levels = previousHp.map { (it.payload as ArchivedActiveHpBarMeasured).filledFraction }
        if (levels.any { it <= 0f || it > 1f } || abs(levels[0] - levels[1]) > 1f/bar.interiorColumns) return null
        val fraction = maxOf(levels.max(), bar.liveColumns.toFloat()/bar.interiorColumns)
        // Require two earlier, distinct, acoustically matched attacks in this same matchup which
        // each removed more than the remaining sliver. Never cross a switch or a new charge phase.
        val window = maxOf(ownerSince,targetSince,start-5*S)
        val compared = candidates.filter { it.estimatedOnsetNanos in window until start }
        val matched = compared.mapNotNull { use ->
            rows.singleOrNull { r -> val tick = r.payload as? ArchivedActiveHpBarDamageTickMeasured
                tick?.damagedSide == recipient && tick.afterFraction > 0 && tick.lostFraction > fraction &&
                    hpEvidence.acceptsSingleHit(r) &&
                    abs(r.monotonicTimeNanos!! - use.estimatedOnsetNanos) <= 350_000_000L }?.let { use to it }
        }.distinctBy { it.second.articleId }
        if (matched.size < 2) return null
        return previousHp.map { it.articleId } + matched.flatMap { (use, tick) -> use.fragments.map { it.articleId } + tick.articleId }
    }

    // An optional visual corroboration of departure. Phase assignment alone cannot locate
    // the subject. Anchor the particle sequence below the last opponent bar in its own raster.
    private fun departureBurstReferences(
        rows: List<ArchivedRealityArticle>, bar: ArchivedCompleteHpBarObserved, from: Long, through: Long,
    ): List<String> {
        val phases = rows.mapNotNull { row ->
            val batch = row.payload as? ArchivedTerminalParticleObservations ?: return@mapNotNull null
            if (row.sourceId != "TERMINAL_PARTICLE_WITNESS" || batch.requestedRecipient != bar.side) return@mapNotNull null
            batch.phaseAtNanos to batch.samples.filter {
                it.sourceAtNanos in from..through && it.sourceAtNanos in batch.phaseAtNanos until batch.sourceDeadlineNanos
            }.map { s ->
                if (s.points.size > 128 || s.flashes.size > 16) return@map TerminalEffectFrame(row.articleId, s.copy(status = "UNREADABLE"))
                fun nearRecipient(x: Double, y: Double) = x >= bar.barLeft && x <= bar.barRight &&
                    y >= bar.barTop && y <= bar.barBottom + (bar.barRight - bar.barLeft) &&
                    s.sourceWidth == bar.sourceWidth && s.sourceHeight == bar.sourceHeight
                TerminalEffectFrame(row.articleId, s.copy(points = s.points.filter { nearRecipient(it.x, it.y) },
                    flashes = s.flashes.filter { nearRecipient(it.x, it.y) }))
            }
        }.groupBy({ it.first }, { it.second })
        return phases.values.flatMap { TerminalCollapseSequences.find(it.flatten()) }
            .singleOrNull()?.evidenceReferences.orEmpty()
    }
}
