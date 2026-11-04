package com.example.overdex.battle.observation

import com.example.overdex.battle.audio.FastMoveListeningGate
import com.example.overdex.battle.custody.CountdownGlyphWitnessed
import com.example.overdex.battle.custody.VsScreenWitnessed
import com.example.overdex.battle.reality.RealityArticle

/**
 * Source-time admission for attention that needs battle underway, including a
 * session that missed GO. Reuses established independent combat/phase proof,
 * without the VS/countdown cues that open prebattle audio attention. This does
 * not establish Match Start or ordinary Fast Move play.
 */
internal class BattleInProgressAdmission {
    private val progress = FastMoveListeningGate()

    fun observe(article: RealityArticle) {
        val payload = article.payload
        if (payload !is CountdownGlyphWitnessed && payload != VsScreenWitnessed) {
            progress.observe(payload, article.monotonicTimeNanos, article.id.value)
        }
    }

    fun allows(at: Long): Boolean = progress.allows(at)

    fun triggerIds(): List<String> = progress.triggerIds()
}
