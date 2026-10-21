package com.example.overdex.battle.inference

import com.example.overdex.battle.custody.MatchEnded
import com.example.overdex.battle.custody.MatchResultMusicCompared
import com.example.overdex.battle.custody.MatchResultMusicMeasured
import com.example.overdex.battle.reality.RealityArticle
import kotlin.math.abs

data class ResultMusicComparison(
    val payload: MatchResultMusicCompared, val music: RealityArticle, val result: RealityArticle,
)

/** Either witness may arrive first. Compare source clocks, never publication order.
 * This does not emit or amend MatchEnded, even when the independent witnesses disagree.
 */
class ResultMusicCorroboration {
    private var result: RealityArticle? = null
    private val music = linkedMapOf<String, RealityArticle>()
    private val compared = mutableSetOf<String>()

    fun accept(article: RealityArticle): List<ResultMusicComparison> {
        when (article.payload) {
            is MatchEnded -> if (result == null) result = article
            is MatchResultMusicMeasured -> {
                music[article.id.value] = article
                while (music.size > 4) music.remove(music.keys.first())
            }
            else -> return emptyList()
        }
        val end = result ?: return emptyList()
        val endAt = end.monotonicTimeNanos ?: return emptyList()
        val ended = end.payload as MatchEnded
        return music.values.mapNotNull { sound ->
            if (sound.matchId != end.matchId) return@mapNotNull null
            val at = sound.monotonicTimeNanos ?: return@mapNotNull null
            if (abs(at - endAt) > MAX_GAP_NANOS || sound.id.value in compared) return@mapNotNull null
            val measured = sound.payload as MatchResultMusicMeasured
            if (measured.theme !in setOf("WIN", "LOSS")) return@mapNotNull null
            compared += sound.id.value
            ResultMusicComparison(MatchResultMusicCompared(measured.theme, ended.result.name,
                measured.theme == ended.result.name, sound.id.value, end.id.value), sound, end)
        }
    }

    companion object { const val MAX_GAP_NANOS = 30_000_000_000L }
}
