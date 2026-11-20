package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.model.Move
import java.io.File
import java.io.InputStream
import kotlin.math.abs

@kotlinx.serialization.Serializable
data class ReplayImpactEvidence(val frame: ReplayImpactFrame, val sounds: ReplayAudioRecovery)

internal object ReplayImpactFinish {
    const val BASIS = "PIXEL_MATCHED_ANNOUNCEMENT_AND_CORROBORATED_DEPARTURE"
    private const val S = 1_000_000_000L

    suspend fun read(archive: MatchArchive, directory: File, moves: Map<String, List<Move>>,
                     audio: ReplayAudioRecovery, openArchive: () -> InputStream?,
                     readAsset: (String) -> ByteArray, cache: ReplayReviewCache? = null): ReplayImpactEvidence? {
        val frame = ArchivedImpactFrameRecognizer.recognize(archive, directory, moves.values.flatten(), openArchive)
            ?: return ReplaySoundMatchedBanner.read(archive, directory, moves, audio, openArchive, readAsset, cache)
        val opponent = archive.articles.filter { it.matchId == archive.matchId && it.monotonicTimeNanos?.let { t -> t <= frame.at } == true &&
            (it.payload as? ArchivedActivePokemonSpeciesWitnessed)?.side == "OPPONENT" }
            .maxByOrNull { it.monotonicTimeNanos!! }?.payload as? ArchivedActivePokemonSpeciesWitnessed
        // If the all-battle pool exceeded its bound, compare the ending against both active
        // species. The announcing player has a known equipped pair. The opposing loadout is open.
        val sounds = if (opponent?.speciesId != null && (audio.unavailableReferences.isNotEmpty() || audio.referenceCandidateNames.isEmpty())) {
            val equipped = frame.actor.chargedMoveNames + frame.actor.fastMoveName
            val playerMoves = moves[frame.actor.speciesName].orEmpty().filter { it.name in equipped }
            val opponentMoves = moves[opponent.speciesName].orEmpty()
            val candidates = playerMoves + opponentMoves
            if (playerMoves.map { it.name }.toSet() != equipped.toSet() || opponentMoves.isEmpty()) audio else ArchivedEndingAudioRecognizer.recognize(archive, candidates,
                setOf(frame.actor.speciesId, opponent.speciesId), openArchive,
                (frame.at - 4*S)..(frame.clearedAt ?: (frame.landing + 5*S)), null, readAsset, cache)
        } else audio
        return ReplayImpactEvidence(frame, sounds)
    }

    fun associate(e: ReplayImpactEvidence, articles: List<ArchivedRealityArticle>, actor: ReplayCombatant?,
                  target: ReplayCombatant?, ownerAt: Long, targetAt: Long, nextTarget: Long?, nextOwner: Long?): ReplayFinishingChargeAssociation? {
        val f = e.frame
        val result = articles.firstOrNull { it.payload is ArchivedMatchEnded } ?: return null
        val end = result.monotonicTimeNanos!!
        val counts = articles.filter { article ->
            val count = article.payload as? ArchivedOpponentBattleResourceCountMeasured
            count?.resource == "POKE_BALLS" && count.maximumCount == 3
        }
        val count = counts.lastOrNull { it.monotonicTimeNanos!! <= f.at } ?: return null
        if ((count.payload as ArchivedOpponentBattleResourceCountMeasured).visibleCount != 1 ||
            counts.any { it.monotonicTimeNanos!! in count.monotonicTimeNanos!!..end &&
                (it.payload as ArchivedOpponentBattleResourceCountMeasured).visibleCount > 1 }) return null
        val emptyBadge = counts.firstOrNull {
            it.monotonicTimeNanos!! in f.landing..minOf(end, f.landing + 5*S) &&
                it.sourceId == count.sourceId && !it.evidenceReferences.isNullOrEmpty() &&
                (it.payload as ArchivedOpponentBattleResourceCountMeasured).visibleCount == 0
        }
        if (emptyBadge != null && counts.any { it.monotonicTimeNanos!! in emptyBadge.monotonicTimeNanos!!..end &&
                (it.payload as ArchivedOpponentBattleResourceCountMeasured).visibleCount != 0 }) return null
        val burstAt = f.clearedAt?.takeIf { f.chargeSound == null && f.departureIds.size == 3 }
        val repeatCry = f.chargeSound?.let {
            val entrance = articles.singleOrNull { row -> row.articleId == f.departureIds.firstOrNull() } ?: return null
            val known = entrance.payload as? ArchivedBattleCryLocalized ?: return null
            val departure = e.sounds.partialCryCandidates.singleOrNull { candidate ->
                candidate.speciesId == known.speciesId && candidate.speciesId == target?.speciesId
            }?.fragments?.singleOrNull { part -> part.articleId == f.departureIds.lastOrNull() &&
                part.referenceSha256 == known.referenceSha256 && part.durationNanos >= S && part.correlation >= .90f }
                ?: return null
            if (entrance.monotonicTimeNanos!! !in (targetAt - 2*S) until f.at ||
                departure.estimatedOnsetNanos <= f.landing || departure.captureAtNanos != f.clearedAt ||
                articles.none { row -> row.articleId == departure.articleId && row.monotonicTimeNanos == departure.captureAtNanos &&
                    (row.payload as? ArchivedAudioCaptured)?.sha256 == departure.audioSha256 }) return null
            departure
        }
        val clear = burstAt ?: emptyBadge?.monotonicTimeNanos ?: repeatCry?.captureAtNanos ?: return null
        if (articles.filter { it.payload is ArchivedMatchEnded }.any { (it.payload as ArchivedMatchEnded).result != "WIN" } ||
            clear !in f.landing..end || end - clear > 20*S ||
            actor?.speciesName != f.actor.speciesName || target?.speciesId == null || target.speciesName == actor.speciesName ||
            actor.isFainted || target.isFainted || nextTarget?.let { it <= end } == true || nextOwner?.let { it <= clear } == true) return null
        val sounds = e.sounds
        val terminalCries = sounds.cries.filter { it.estimatedOnsetNanos in f.landing..clear }
        val cry = terminalCries.singleOrNull()?.takeIf { it.speciesId == target.speciesId }
        if (sounds.cries.any { it !== cry && it.estimatedOnsetNanos in f.at..end } ||
            sounds.fastEffects.any { it.estimatedOnsetNanos in (f.at - 2*S)..clear } ||
            sounds.charges.any { it.moveName != f.move && it.estimatedOnsetNanos in (f.at - 2*S)..clear }) return null
        val partial = cry?.let { primary -> sounds.partialCryCandidates.singleOrNull { candidate ->
            candidate.speciesId == primary.speciesId && abs(candidate.estimatedOnsetNanos - primary.estimatedOnsetNanos) < 80_000_000L &&
                candidate.fragments.any { fragment -> primary.fragments.none { it.audioSha256 == fragment.audioSha256 } }
        } }
        // One unavailable cue is silence, not a contrary observation. Require two
        // independent kinds of departure evidence; a second clip still counts as audio.
        val qualifiedCry = cry != null && sounds.unavailableReferences.isEmpty() && sounds.unavailableArtifacts.isEmpty()
        if (repeatCry == null && listOf(qualifiedCry, burstAt != null, emptyBadge != null).count { it } < 2) return null
        if (articles.any { a -> a.monotonicTimeNanos!! in (f.at - 2_500_000_000L)..clear &&
                (a.payload as? ArchivedFastMoveUseObserved)?.damagedSide == "OPPONENT" }) return null
        if (articles.any { a -> a.monotonicTimeNanos!! in f.at..end && when (val p = a.payload) {
                is ArchivedGetReadyWitnessed, is ArchivedAttackIncoming, is ArchivedScreenIgnoredByUser,
                is ArchivedObservationSessionStoppedByUser, is ArchivedObservationRestartedByUser -> true
                is ArchivedActiveHpBarDamageTickMeasured -> p.damagedSide == "OPPONENT" && a.monotonicTimeNanos > f.landing
                is ArchivedRawText -> a.sourceId == "ANNOUNCEMENT_WITNESS" &&
                    p.value.contains(Regex("""\b(blocked|go,)""", RegexOption.IGNORE_CASE))
                else -> false
            } }) return null
        return ReplayFinishingChargeAssociation("PLAYER", actor.speciesName, ownerAt, "OPPONENT", target.speciesName, targetAt,
            f.move, f.at, clear, f.landing, null, (listOf(f.announcementCropId, f.frameSha256) + f.chargeSound?.fragments.orEmpty().map { it.articleId }).distinct(),
            f.departureIds + (cry?.fragments.orEmpty() + partial?.fragments.orEmpty()).map { it.articleId } +
                listOfNotNull(count.articleId, emptyBadge?.articleId, result.articleId),
            emptyList(), if (repeatCry == null) BASIS else ReplaySoundMatchedBanner.BASIS, impactSupportArticleIds = listOf(f.profileId),
            departedBy = ReplayTimeBound(clear, if (burstAt != null) f.departureIds.last() else repeatCry?.articleId ?: requireNotNull(emptyBadge).articleId))
    }
}
