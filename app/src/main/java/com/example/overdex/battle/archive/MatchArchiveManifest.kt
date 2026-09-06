package com.example.overdex.battle.archive

import kotlinx.serialization.Serializable

/**
 * Manifest metadata for a `.odxmatch` ZIP package container.
 */
@Serializable
data class MatchArchiveManifest(
    val archiveFormatVersion: Int = 1,
    val archiveType: String = "overdex-match-archive",
    val matchId: String,
    val timelineEntry: String = "timeline.json",
    val articleCount: Int
)