package com.example.overdex.battle.archive

/** Selects a self-contained Timeline slice for a requested archive profile. */
object MatchArchiveArticleSelector {
    fun select(
        articles: List<ArchivedRealityArticle>,
        mode: MatchArchiveExportMode
    ): List<ArchivedRealityArticle> {
        if (mode == MatchArchiveExportMode.FULL_FORENSIC) return articles

        val byId = articles.associateBy { it.articleId }
        val retainedIds = linkedSetOf<String>()
        val pendingIds = ArrayDeque<String>()

        // The timeline remains intact. Raw artifact records join it only when a
        // retained observation explicitly cites them as evidence.
        articles.filterNot(::isRawArtifact).forEach { article ->
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

    private fun ArchivedRealityArticle.referencedArticleIds(): Sequence<String> = sequence {
        yieldAll(predecessorIds)
        evidenceReferences?.let { yieldAll(it) }
    }
}
