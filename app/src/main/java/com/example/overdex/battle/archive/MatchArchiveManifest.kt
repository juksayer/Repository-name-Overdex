package com.example.overdex.battle.archive

import kotlinx.serialization.Serializable

/**
 * Manifest metadata for a `.odxmatch` ZIP package container.
 */
@Serializable
data class MatchArchiveManifest(
    val archiveFormatVersion: Int = 2,
    val archiveType: String = "overdex-match-archive",
    val matchId: String,
    val timelineEntry: String = "timeline.json",
    val articleCount: Int,
    val artifacts: List<ArchivedArtifactEntry> = emptyList(),
    /** Format 3 can explicitly omit media without erasing the original capture references. */
    val mediaIncluded: Boolean = true
)

@Serializable
data class ArchivedArtifactEntry(
    val relativePath: String,
    val sha256: String,
    val byteCount: Long,
    val mediaType: String
)
