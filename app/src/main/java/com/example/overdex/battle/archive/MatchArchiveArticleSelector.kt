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
        // explicitly named QTE calibration samples and a bounded real-battle LCD
        // preview are retained for inspection independently of successful recognition.
        articles.filter { !isRawArtifact(it) || isQteCalibrationSample(it) || it.articleId in previewIds }.forEach { article ->
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

    private fun isQteCalibrationSample(article: ArchivedRealityArticle): Boolean =
        article.sourceId == "QTE_TYPE_ICONS_CAPTURE" &&
            (article.payload as? ArchivedCropCaptured)?.cropName == "QteTypeIconsCrop"

    /** At most seven extra raw samples: actual pixels, independent of each field's recognition success. */
    private fun calibrationPreviewIds(articles: List<ArchivedRealityArticle>): Set<String> =
        articles.groupBy { it.matchId }.flatMap { (matchId, rows) ->
            BattleCalibrationPreviewSelection.fromArchivedArticles(matchId, rows)?.selectedArticleIds.orEmpty()
        }.toSet()

    private fun ArchivedRealityArticle.referencedArticleIds(): Sequence<String> = sequence {
        yieldAll(predecessorIds)
        evidenceReferences?.let { yieldAll(it) }
    }
}
