package com.example.overdex.battle.archive

import kotlinx.serialization.Serializable

/**
 * An archive DTO that preserves every current RealityArticle field in serializable form.
 */
@Serializable
data class ArchivedRealityArticle(
    val articleId: String,
    val matchId: String,
    val perceivedAt: Long,
    val recordedAt: Long,
    val sourceId: String,
    val payload: ArchivedTestimonyPayload,
    val predecessorIds: List<String>,
    val confidence: Float?,
    val sequenceNumber: Long?,
    val evidenceReferences: List<String>?
)
