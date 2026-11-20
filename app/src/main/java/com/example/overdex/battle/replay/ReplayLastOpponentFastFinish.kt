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
    val playerMoves: Map<String, Move>,
    val opponentMoves: Set<String>,
    val cryId: String,
)

internal object ReplayLastOpponentFastFinish {
    const val BASIS = "LAST_OPPONENT_CRY_AND_RECEIVED_FAST_MOVE_PATTERN"
    private const val S = 1_000_000_000L

    suspend fun read(archive: MatchArchive, catalog: GameMasterLoader, open: () -> InputStream?,
                     asset: (String) -> ByteArray): ReplayLastOpponentFastEvidence? = read(archive, catalog, open, asset, null)

    suspend fun read(
        archive: MatchArchive, catalog: GameMasterLoader,
        open: () -> InputStream?, asset: (String) -> ByteArray, cache: ReplayReviewCache?,
    ): ReplayLastOpponentFastEvidence? = withContext(Dispatchers.Default) {
        val rows = archive.articles.filter { it.matchId == archive.matchId && it.monotonicTimeNanos != null }
            .sortedBy { it.monotonicTimeNanos }
        val end = rows.firstOrNull { it.payload is ArchivedMatchEnded } ?: return@withContext null
        if ((end.payload as ArchivedMatchEnded).result != "WIN") return@withContext null
        val cry = rows.lastOrNull { it.payload is ArchivedBattleCryLocalized &&
            end.monotonicTimeNanos!! - it.monotonicTimeNanos!! in 0..20*S } ?: return@withContext null
        val at = cry.monotonicTimeNanos!!
        val sound = cry.payload as ArchivedBattleCryLocalized
        fun active(side: String) = rows.lastOrNull { it.monotonicTimeNanos!! <= at &&
            (it.payload as? ArchivedActivePokemonSpeciesWitnessed)?.side == side }?.payload as? ArchivedActivePokemonSpeciesWitnessed
        val player = active("PLAYER")?.speciesId ?: return@withContext null
        val opponent = active("OPPONENT")?.speciesId ?: return@withContext null
        if (player == opponent || opponent != sound.speciesId || sound.correlation < .95f) return@withContext null
        val badge = rows.lastOrNull { it.monotonicTimeNanos!! <= at &&
            (it.payload as? ArchivedOpponentBattleResourceCountMeasured)?.resource == "POKE_BALLS" }
        if ((badge?.payload as? ArchivedOpponentBattleResourceCountMeasured)?.visibleCount != 1) return@withContext null
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
        ReplayLastOpponentFastEvidence(audio, playerMoves, opponentMoves.keys, cry.articleId)
    }

    fun resolve(
        evidence: ReplayLastOpponentFastEvidence, rows: List<ArchivedRealityArticle>,
        owner: ReplayCombatant, target: ReplayCombatant, ownerSince: Long, targetSince: Long,
    ): ReplayFinishingFastAssociation? {
        val cry = rows.singleOrNull { it.articleId == evidence.cryId } ?: return null
        val at = cry.monotonicTimeNanos!!
        val sound = cry.payload as? ArchivedBattleCryLocalized ?: return null
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
        val candidates = audio.mixedCandidates.filter { it.moveName in evidence.playerMoves }
        val possible = candidates.filter { candidate ->
            val turns = evidence.playerMoves[candidate.moveName]?.turns ?: return@filter false
            at - candidate.estimatedOnsetNanos in 0..(turns * S / 2)
        }
        val attack = possible.singleOrNull() ?: return null
        val start = attack.estimatedOnsetNanos
        val move = evidence.playerMoves.getValue(attack.moveName)
        if (!strong(attack) || attack.moveName in evidence.opponentMoves || start < maxOf(ownerSince,targetSince) ||
            at - start > (move.turns ?: return null)*S/2 ||
            audio.fastEffects.any { it.estimatedOnsetNanos in start..at && it.moveName in evidence.playerMoves &&
                (it.moveName != attack.moveName || abs(it.estimatedOnsetNanos-start) > 80_000_000L) }) return null
        val result = rows.firstOrNull { it.payload is ArchivedMatchEnded } ?: return null
        if ((result.payload as ArchivedMatchEnded).result != "WIN" || result.monotonicTimeNanos!! - at !in 0..20*S) return null
        val end = result.monotonicTimeNanos
        val badge = rows.lastOrNull { it.monotonicTimeNanos!! <= start &&
            (it.payload as? ArchivedOpponentBattleResourceCountMeasured)?.resource == "POKE_BALLS" } ?: return null
        val count = badge.payload as ArchivedOpponentBattleResourceCountMeasured
        if (count.maximumCount != 3 || count.visibleCount != 1 || start-badge.monotonicTimeNanos!! > 30*S) return null
        if (rows.any { r -> r.monotonicTimeNanos!! in start-S..end && when (val p = r.payload) {
            is ArchivedAttackIncoming, is ArchivedGetReadyWitnessed, is ArchivedChargeMoveUsedAnnounced,
            is ArchivedChargedMoveEnergySpent, is ArchivedChargeMoveQteVibrationPatternInferred,
            is ArchivedObservationRestartedByUser, is ArchivedObservationSessionStoppedByUser,
            is ArchivedScreenIgnoredByUser -> true
            is ArchivedFastMoveUseObserved -> r.monotonicTimeNanos >= start && (p.attackingSide != "OPPONENT" || r.monotonicTimeNanos >= at)
            is ArchivedActiveHpBarDamageTickMeasured -> r.monotonicTimeNanos >= start && (p.damagedSide != "PLAYER" || r.monotonicTimeNanos >= at)
            is ArchivedBattleCryLocalized -> r.articleId != cry.articleId
            is ArchivedOpponentBattleResourceCountMeasured -> p.resource == "POKE_BALLS" && p.visibleCount > 1
            is ArchivedActivePokemonSpeciesWitnessed -> p.speciesId != (if (p.side == "PLAYER") owner.speciesId else target.speciesId)
            else -> false
        } }) return null
        val sliver = rows.lastOrNull { r -> val p = r.payload as? ArchivedCompleteHpBarObserved
            p?.side == "OPPONENT" && r.monotonicTimeNanos!! in start..at } ?: return null
        val bar = sliver.payload as ArchivedCompleteHpBarObserved
        if (sliver.confidence?.let { it in .8f..1f } != true || bar.status != "LIVE_COLOR" ||
            bar.liveColumns !in 1..bar.interiorColumns || bar.damageColumns !in 1..(bar.interiorColumns - bar.liveColumns) ||
            bar.appearanceStartedAtNanos?.let { it !in targetSince..sliver.monotonicTimeNanos!! } == true ||
            bar.barLeft < 0 || bar.barTop < 0 ||
            bar.barRight >= bar.sourceWidth || bar.barBottom >= bar.sourceHeight || bar.barBottom <= bar.barTop ||
            bar.barRight-bar.barLeft < 6*(bar.barBottom-bar.barTop) || bar.interiorColumns !in 40..bar.barRight-bar.barLeft) return null
        // A complete outline may still contain unknown background columns. Use an
        // independently measured, settled red fraction as the pre-hit upper estimate.
        val hpEvidence = ReplayHpEvidence(rows)
        val previousHp = rows.filter { r ->
            val p = r.payload as? ArchivedActiveHpBarMeasured
            p?.side == "OPPONENT" && p.fillColor == "RED" && r.confidence?.let { it in .8f..1f } == true &&
                r.monotonicTimeNanos!! in maxOf(targetSince, start - S) until start && hpEvidence.confirmsLiveHp(r)
        }.takeLast(2)
        if (previousHp.size < 2 || previousHp[0].monotonicTimeNanos == previousHp[1].monotonicTimeNanos) return null
        val levels = previousHp.map { (it.payload as ArchivedActiveHpBarMeasured).filledFraction }
        if (levels.any { it <= 0f || it > 1f } || abs(levels[0] - levels[1]) > 1f/bar.interiorColumns) return null
        val fraction = maxOf(levels.max(), bar.liveColumns.toFloat()/bar.interiorColumns)
        // Require two earlier, distinct, acoustically matched attacks in this same matchup which
        // each removed more than the remaining sliver. Never cross a switch or a new charge phase.
        val window = maxOf(ownerSince,targetSince,start-5*S)
        val compared = candidates.filter { it.moveName == attack.moveName && it.estimatedOnsetNanos in window until start && strong(it) }
        val matched = compared.mapNotNull { use ->
            rows.singleOrNull { r -> val tick = r.payload as? ArchivedActiveHpBarDamageTickMeasured
                tick?.damagedSide == "OPPONENT" && tick.afterFraction > 0 && tick.lostFraction > fraction &&
                    hpEvidence.acceptsSingleHit(r) &&
                    abs(r.monotonicTimeNanos!! - use.estimatedOnsetNanos) <= 350_000_000L }?.let { use to it }
        }.distinctBy { it.second.articleId }
        if (matched.size < 2) return null
        val refs = attack.fragments.map { it.articleId }.distinct()
        return ReplayFinishingFastAssociation("PLAYER",owner.speciesName,"OPPONENT",target.speciesName,targetSince,
            move.name,at,start,at,refs, (cryMatch.fragments.map { it.articleId } + matched.map { it.second.articleId } +
            matched.flatMap { (use, _) -> use.fragments.map { it.articleId } } + previousHp.map { it.articleId } + sliver.articleId).distinct(),
            (listOf(badge.articleId, cry.articleId, result.articleId) +
                departureBurstReferences(rows, bar, start, minOf(at + 2*S, end))).distinct(), ownerSince, basis = BASIS,
            departedBy = ReplayTimeBound(at, cry.articleId))
    }

    // An optional visual corroboration of departure. Phase assignment alone cannot locate
    // the subject. Anchor the particle sequence below the last opponent bar in its own raster.
    private fun departureBurstReferences(
        rows: List<ArchivedRealityArticle>, bar: ArchivedCompleteHpBarObserved, from: Long, through: Long,
    ): List<String> {
        val phases = rows.mapNotNull { row ->
            val batch = row.payload as? ArchivedTerminalParticleObservations ?: return@mapNotNull null
            if (row.sourceId != "TERMINAL_PARTICLE_WITNESS" || batch.requestedRecipient != "OPPONENT") return@mapNotNull null
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
