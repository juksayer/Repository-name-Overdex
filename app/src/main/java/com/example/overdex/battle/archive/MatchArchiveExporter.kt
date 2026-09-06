package com.example.overdex.battle.archive

import com.example.overdex.battle.observation.MatchId
import com.example.overdex.battle.reality.RealityTimeline
import java.io.OutputStream

/**
 * Exports the currently recorded articles belonging to one Match.
 *
 * Preserves append order and does not imply that the Match is complete.
 * Storage and stream lifecycle follow the package writer's contract.
 * Mapping and writing failures propagate to the caller.
 */
object MatchArchiveExporter {

    fun export(
        realityTimeline: RealityTimeline,
        matchId: MatchId,
        output: OutputStream
    ): MatchArchiveManifest {
        val snapshot = realityTimeline.getArticles()
        val matchArticles = snapshot.filter { it.matchId == matchId }

        val archive = RealityArticleArchiveMapper.createArchive(
            matchId = matchId,
            articles = matchArticles
        )

        return MatchArchivePackageWriter.write(archive, output)
    }
}