package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.*
import com.example.overdex.battle.observation.announcementContainsPhrase

/** Older recordings sometimes kept the OCR but failed to publish later typed warnings. */
internal fun isReplayAttackIncomingCue(article: ArchivedRealityArticle): Boolean =
    article.payload is ArchivedAttackIncoming || replayAnnouncementContains(article, "ATTACKINCOMING")

internal fun isReplayGetReadyCue(article: ArchivedRealityArticle): Boolean =
    article.payload is ArchivedGetReadyWitnessed || replayAnnouncementContains(article, "GETREADY")

private fun replayAnnouncementContains(article: ArchivedRealityArticle, phrase: String): Boolean =
    article.sourceId == "ANNOUNCEMENT_WITNESS" &&
        (article.payload as? ArchivedRawText)?.value?.let { announcementContainsPhrase(it, phrase) } == true
