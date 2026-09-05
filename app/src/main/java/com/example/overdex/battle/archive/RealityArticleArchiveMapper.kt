package com.example.overdex.battle.archive

import com.example.overdex.battle.custody.AttackIncoming
import com.example.overdex.battle.custody.PokemonIdentified
import com.example.overdex.battle.custody.RawTestimony
import com.example.overdex.battle.observation.MatchId
import com.example.overdex.battle.reality.RealityArticle

/**
 * Mapper for converting a RealityArticle into an ArchivedRealityArticle and assembling a MatchArchive.
 */
object RealityArticleArchiveMapper {

    fun map(article: RealityArticle): ArchivedRealityArticle {
        val mId = article.matchId ?: throw IllegalArgumentException("RealityArticle ${article.id.value} has a null matchId and cannot be archived.")

        val archivedPayload: ArchivedTestimonyPayload = when (val p = article.payload) {
            is RawTestimony -> {
                when (val data = p.data) {
                    is String -> ArchivedRawText(data)
                    is Int -> ArchivedRawInt(data)
                    else -> throw IllegalArgumentException("Unsupported RawTestimony data type: ${data::class.java.name}")
                }
            }
            is AttackIncoming -> ArchivedAttackIncoming
            is PokemonIdentified -> ArchivedPokemonIdentified(p.species)
            else -> throw IllegalArgumentException("Unsupported TestimonyPayload type: ${p::class.java.name}")
        }

        return ArchivedRealityArticle(
            articleId = article.id.value,
            matchId = mId.value,
            perceivedAt = article.perceivedAt,
            recordedAt = article.recordedAt,
            sourceId = article.sourceId.id,
            payload = archivedPayload,
            predecessorIds = article.predecessorIds.map { it.value },
            confidence = article.confidence,
            sequenceNumber = article.sequenceNumber,
            evidenceReferences = article.evidenceReferences
        )
    }

    fun createArchive(matchId: MatchId, articles: List<RealityArticle>): MatchArchive {
        articles.forEach { article ->
            if (article.matchId != matchId) {
                throw IllegalArgumentException("RealityArticle ${article.id.value} matchId (${article.matchId}) does not match requested matchId ($matchId)")
            }
        }

        val archivedArticles = articles.map { map(it) }
        return MatchArchive(
            schemaVersion = 1,
            matchId = matchId.value,
            articles = archivedArticles
        )
    }
}
