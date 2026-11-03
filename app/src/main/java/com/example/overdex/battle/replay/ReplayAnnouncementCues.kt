package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.observation.announcementContainsPhrase
import com.example.overdex.battle.observation.ANNOUNCEMENT_PHASE_TEXT_SOURCES

/** Older recordings sometimes kept the OCR but failed to publish later typed warnings. */
internal fun isReplayAttackIncomingCue(article: ArchivedRealityArticle): Boolean =
    article.payload is ArchivedAttackIncoming || replayAnnouncementContains(article, "ATTACKINCOMING") ||
        isReplayShieldPromptCue(article)

/** The explicit shield prompt also proves opponent preparation when its earlier warning was missed. */
internal fun isReplayShieldPromptCue(article: ArchivedRealityArticle): Boolean {
    if (article.sourceId !in ANNOUNCEMENT_PHASE_TEXT_SOURCES && article.sourceId != "MATCH_OUTCOME_TEXT_WITNESS") return false
    val text = (article.payload as? ArchivedRawText)?.value ?: return false
    return announcementContainsPhrase(text, "USEAPROTECTSHIELD") ||
        announcementContainsPhrase(text, "NOPROTECTSHIELDSREMAINING")
}

internal fun isReplayGetReadyCue(article: ArchivedRealityArticle): Boolean =
    article.payload is ArchivedGetReadyWitnessed || replayAnnouncementContains(article, "GETREADY")

private fun replayAnnouncementContains(article: ArchivedRealityArticle, phrase: String): Boolean =
    article.sourceId in ANNOUNCEMENT_PHASE_TEXT_SOURCES &&
        (article.payload as? ArchivedRawText)?.value?.let { announcementContainsPhrase(it, phrase) } == true
