package com.example.overdex.battle.archive

import com.example.overdex.battle.observation.BattleCalibrationPreviewSelection

/** Selects a self-contained Timeline slice for a requested archive profile. */
object MatchArchiveArticleSelector {
    fun select(
        articles: List<ArchivedRealityArticle>,
        mode: MatchArchiveExportMode
    ): List<ArchivedRealityArticle> {
        if (mode != MatchArchiveExportMode.COMPACT_CITED_EVIDENCE) return articles

        val byId = articles.associateBy { it.articleId }
        val retainedIds = linkedSetOf<String>()
        val pendingIds = ArrayDeque<String>()
        val previewIds = calibrationPreviewIds(articles)

        // The timeline remains intact. Raw artifact records join it only when a
        // retained observation explicitly cites them as evidence. The bounded,
        // explicitly named QTE/hand/charged-HP calibration samples and a bounded real-battle
        // LCD preview are retained independently of successful recognition. Hand
        // samples are raw manual experiments, not recall/send-out testimony.
        articles.filter { !isRawArtifact(it) || isCalibrationSample(it) || it.articleId in previewIds }.forEach { article ->
            retainedIds += article.articleId
            article.referencedArticleIds().forEach(pendingIds::addLast)
        }

        while (pendingIds.isNotEmpty()) {
            val article = byId[pendingIds.removeFirst()] ?: continue
            if (!retainedIds.add(article.articleId)) continue
            article.referencedArticleIds().forEach(pendingIds::addLast)
        }

        return articles.filter { it.articleId in retainedIds }
    }

    private fun isRawArtifact(article: ArchivedRealityArticle): Boolean =
        article.payload is ArchivedCropCaptured || article.payload is ArchivedAudioCaptured

    private fun isCalibrationSample(article: ArchivedRealityArticle): Boolean {
        val crop = article.payload as? ArchivedCropCaptured ?: return false
        return when (article.sourceId) {
            "QTE_TYPE_ICONS_CAPTURE" -> crop.cropName == "QteTypeIconsCrop"
            "OPPONENT_TRAINER_HAND_CAPTURE" -> crop.cropName == "OpponentTrainerHandCrop"
            "CHARGED_IMPACT_HP_CAPTURE" -> crop.cropName == "ChargedImpactHpCrop"
            else -> false
        }
    }

    /** At most eight extra raw previews; phase-bounded calibration samples remain independent. */
    private fun calibrationPreviewIds(articles: List<ArchivedRealityArticle>): Set<String> =
        articles.groupBy { it.matchId }.flatMap { (matchId, rows) ->
            BattleCalibrationPreviewSelection.fromArchivedArticles(matchId, rows)?.selectedArticleIds.orEmpty()
        }.toSet()

    private fun ArchivedRealityArticle.referencedArticleIds(): Sequence<String> = sequence {
        yieldAll(predecessorIds)
        evidenceReferences?.let { yieldAll(it) }
    }
}
