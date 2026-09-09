package com.example.overdex.battle.archive

import com.example.overdex.battle.observation.MatchId
import com.example.overdex.battle.reality.RealityTimeline
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    /**
     * Generates a unique match archive filename using the device's local date and time
     * in the format `yyyy-MM-dd_HH-mm-ss.odxmatch.zip`, adding a numeric suffix before
     * `.odxmatch.zip` if the filename already exists.
     */
    fun generateExportFilename(dir: File, date: Date = Date()): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT)
        val baseTimestamp = dateFormat.format(date)
        var candidate = "$baseTimestamp.odxmatch.zip"
        var counter = 1
        while (File(dir, candidate).exists()) {
            candidate = "${baseTimestamp}_$counter.odxmatch.zip"
            counter++
        }
        return candidate
    }
}
