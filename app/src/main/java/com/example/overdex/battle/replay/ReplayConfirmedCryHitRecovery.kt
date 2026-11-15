package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import kotlin.math.abs

/** An inferred use linked to an independently confirmed loss. atNanos is the cry cue,
 * used as a presentation deadline; it is not a measured impact or damage tick. */
internal data class ReplayConfirmedCryHit(val moveName: String, val atNanos: Long, val articleIds: Set<String>,
    val sourceTimesNanos: Set<Long>)

internal object ReplayConfirmedCryHitRecovery {
    private const val S = 1_000_000_000L
    fun recover(articles: List<ArchivedRealityArticle>, audio: List<ReplayMixedAudioCandidate>,
                knockouts: List<ReplayKnockout>, playerMoveAt: (Long) -> Pair<String, Long>?,
                opponentMayUse: (String, Long) -> Boolean?, actorAt: (String, Long) -> ReplayCombatant?,
                appearanceAt: (String, Long) -> Long, sameExchange: (Long, Long) -> Boolean): List<ReplayConfirmedCryHit> {
        fun key(s: String) = s.uppercase().filter(Char::isLetterOrDigit)
        val ordered = articles.filter { it.monotonicTimeNanos != null }.sortedBy { it.monotonicTimeNanos }
        return audio.mapNotNull candidate@{ sound ->
            val start = sound.estimatedOnsetNanos
            val (move, duration) = playerMoveAt(start) ?: return@candidate null
            if (key(move) != key(sound.moveName) || duration !in (S/2)..(5*S) ||
                sound.fragments.size < 2 || opponentMayUse(move, start) != false) return@candidate null
            val target = actorAt("OPPONENT", start) ?: return@candidate null
            val player = actorAt("PLAYER", start) ?: return@candidate null
            if (target.speciesId == null || player.speciesId == null || target.speciesId == player.speciesId) return@candidate null
            val motion = ordered.lastOrNull { a ->
                val p = a.payload as? ArchivedActiveHpBarMotionCadenceMeasured ?: return@lastOrNull false
                abs(a.monotonicTimeNanos!! - start) <= S && p.movingSide == "PLAYER" && p.sampleCount >= 3 &&
                    p.verticalExcursionPixels.isFinite() && p.verticalExcursionPixels > 0 &&
                    abs(p.intervalNanos - duration) <= S/2 && sameExchange(a.monotonicTimeNanos!!, start)
            } ?: return@candidate null
            val cry = ordered.singleOrNull { a ->
                val p = a.payload as? ArchivedBattleCryLocalized ?: return@singleOrNull false
                p.speciesId == target.speciesId && a.monotonicTimeNanos!! in start..(start + 3*S) &&
                    sameExchange(start, a.monotonicTimeNanos!!)
            } ?: return@candidate null
            val cryAt = cry.monotonicTimeNanos!!
            val repeat = ordered.singleOrNull { a ->
                val p = a.payload as? ArchivedRepeatCryDepartureCandidate ?: return@singleOrNull false
                a.monotonicTimeNanos == cryAt && p.side == "OPPONENT" && p.speciesId == target.speciesId &&
                    cry.articleId in p.cryOccurrenceIds
            } ?: return@candidate null
            val ko = knockouts.singleOrNull { it.side == "OPPONENT" && key(it.speciesName) == key(target.speciesName) &&
                it.appearanceAtNanos == appearanceAt("OPPONENT", start) && it.sourceArticleIds.isNotEmpty() &&
                it.verifiedPokeBallsAfter in 1..2 && it.recordedAtNanos >= cryAt &&
                (it.replacementAtNanos == null || cryAt < it.replacementAtNanos) } ?: return@candidate null
            if (audio.any { it !== sound && key(it.moveName) == key(move) &&
                    it.estimatedOnsetNanos in (start - 3*S)..cryAt &&
                    abs(it.estimatedOnsetNanos - start) >= 300_000_000L }) return@candidate null
            if (ordered.any { a -> a.monotonicTimeNanos!! in (start + 1)..cryAt && when (val p = a.payload) {
                    is ArchivedFastMoveUseObserved -> p.attackingSide == "PLAYER"
                    is ArchivedChargedMoveEnergySpent -> p.side == "PLAYER"
                    is ArchivedChargeMoveUsedAnnounced -> true
                    else -> false
                } }) return@candidate null
            val audioIds = sound.fragments.map { it.articleId }.toSet()
            ReplayConfirmedCryHit(move, cryAt, audioIds + setOf(motion.articleId, cry.articleId, repeat.articleId) + ko.sourceArticleIds,
                setOf(start, motion.monotonicTimeNanos!!, cryAt) + sound.fragments.map { it.captureAtNanos })
        }
    }
}
