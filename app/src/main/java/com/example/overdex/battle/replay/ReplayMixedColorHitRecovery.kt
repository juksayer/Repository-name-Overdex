package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import kotlin.math.abs

/** Preserves source times and identifies a hit, never the damage amount or a faint. */
internal data class ReplayMixedColorHit(val moveName: String, val atNanos: Long, val articleIds: Set<String>,
    val sourceTimesNanos: Set<Long>)

internal object ReplayMixedColorHitRecovery {
    private const val S = 1_000_000_000L
    fun recover(articles: List<ArchivedRealityArticle>, audio: List<ReplayMixedAudioCandidate>,
                playerMoveAt: (Long) -> String?, opponentMayUse: (String, Long) -> Boolean?,
                sameExchange: (Long, Long) -> Boolean): List<ReplayMixedColorHit> {
        fun key(name: String) = name.uppercase().filter(Char::isLetterOrDigit)
        val ordered = articles.filter { it.monotonicTimeNanos != null }.sortedBy { it.monotonicTimeNanos }
        val profiles = ordered.filter { (it.payload as? ArchivedCompleteHpBarObserved)?.side == "OPPONENT" }
        fun valid(p: ArchivedCompleteHpBarObserved) = p.sourceWidth > 0 && p.sourceHeight > 0 &&
            p.barLeft >= 0 && p.barTop >= 0 && p.barRight <= p.sourceWidth && p.barBottom < p.sourceHeight &&
            p.barRight > p.barLeft && p.barBottom > p.barTop && p.interiorColumns > 0 &&
            p.liveColumns in 0..p.interiorColumns && p.damageColumns in 0..p.interiorColumns &&
            p.liveColumns + p.damageColumns <= p.interiorColumns && p.railBrightness.isFinite()
        val used = mutableSetOf<String>()
        return audio.sortedBy { it.estimatedOnsetNanos }.mapNotNull candidate@{ sound ->
            val start = sound.estimatedOnsetNanos
            if (sound.fragments.size < 2 || sound.fragments.map { it.articleId }.any { it in used } ||
                playerMoveAt(start)?.let { key(it) == key(sound.moveName) } != true ||
                opponentMayUse(sound.moveName, start) != false) return@candidate null
            val visual = ordered.firstOrNull { a ->
                val p = a.payload as? ArchivedFastMoveRecipientVisualArtifactMeasured ?: return@firstOrNull false
                val at = a.monotonicTimeNanos!!
                at in start..(start + 2*S) && p.damagedSide == "OPPONENT" && p.changedSampleCount > 0 &&
                    p.changedPixelFraction.isFinite() && p.changedPixelFraction in .08f..1f &&
                    p.meanColorDistance.isFinite() && p.meanColorDistance in .16f..1f && sameExchange(start, at)
            } ?: return@candidate null
            val visualAt = visual.monotonicTimeNanos!!
            val transition = profiles.firstOrNull { a ->
                val p = a.payload as ArchivedCompleteHpBarObserved
                val at = a.monotonicTimeNanos!!
                at in visualAt..(start + 3*S) && p.status in setOf("DAMAGE_COLOR", "SUSTAINED_DAMAGE_COLOR", "UNRESOLVED_COLOR") &&
                    p.damageColumns >= 2 && valid(p) && sameExchange(start, at)
            } ?: return@candidate null
            val after = transition.payload as ArchivedCompleteHpBarObserved
            val at = transition.monotonicTimeNanos!!
            val before = profiles.lastOrNull { a ->
                val p = a.payload as ArchivedCompleteHpBarObserved
                val t = a.monotonicTimeNanos!!
                t in visualAt until at && at - t <= S && p.status == "LIVE_COLOR" && p.liveColumns > 0 &&
                    p.damageColumns == 0 && valid(p) && p.sourceWidth == after.sourceWidth && p.sourceHeight == after.sourceHeight &&
                    abs(p.barLeft - after.barLeft) <= 2 && abs(p.barRight - after.barRight) <= 2 &&
                    abs(p.barTop - after.barTop) <= (p.barBottom - p.barTop) && sameExchange(start, t)
            } ?: return@candidate null
            if (audio.any { it !== sound && key(it.moveName) == key(sound.moveName) &&
                    it.estimatedOnsetNanos in (start - 3*S)..at &&
                    abs(it.estimatedOnsetNanos - start) >= 300_000_000L }) return@candidate null
            if (ordered.any { a -> a.monotonicTimeNanos!! in (start + 1)..at && when (val p = a.payload) {
                    is ArchivedFastMoveUseObserved -> p.attackingSide == "PLAYER"
                    is ArchivedChargedMoveEnergySpent -> p.side == "PLAYER"
                    is ArchivedChargeMoveUsedAnnounced -> true
                    else -> false
                } }) return@candidate null
            val audioIds = sound.fragments.map { it.articleId }.toSet()
            used += audioIds
            ReplayMixedColorHit(sound.moveName, at, audioIds + setOf(visual.articleId, before.articleId, transition.articleId),
                setOf(start, visualAt, before.monotonicTimeNanos!!, at) + sound.fragments.map { it.captureAtNanos })
        }
    }
}
