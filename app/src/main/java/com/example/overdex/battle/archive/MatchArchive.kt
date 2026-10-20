package com.example.overdex.battle.archive

import kotlinx.serialization.Serializable

/**
 * An export representation of one Match's canonical Timeline slice.
 */
@Serializable
data class MatchArchive(
    val schemaVersion: Int = 1,
    val matchId: String,
    val articles: List<ArchivedRealityArticle>,
    /** Capture references remain testimony even when their image/audio bytes are omitted. */
    val mediaIncluded: Boolean = true
)
