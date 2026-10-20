package com.example.overdex.battle.archive

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.overdex.battle.observation.MatchId
import com.example.overdex.battle.reality.RealityTimeline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.FileOutputStream
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class ArchiveDirectoryManager(private val context: Context) {
    private val internalArchiveDirectory = File(context.filesDir, "match_archives")
    private val checkpointWriters = mutableMapOf<MatchId, MatchCheckpointJournal>()
    private val archiveRetention = MatchArchiveRetention(internalArchiveDirectory)

    init {
        releaseRetiredArchiveFolderPermission()
    }

    data class ArchiveEntry(
        val name: String,
        val uri: Uri,
        val lastModified: Long,
        val byteCount: Long
    )

    data class InterruptedMatchRecovery(
        val recoveredNames: List<String>,
        val failures: Map<String, String>
    )

    /** Snapshot at process startup, before any new session can create a checkpoint. */
    fun interruptedCheckpointFiles(): List<File> = internalArchiveDirectory.listFiles().orEmpty()
        .filter { it.isFile && it.name.startsWith("active-") &&
            (it.name.endsWith(".odxmatch.zip") || it.name.endsWith(".odxjournal")) }
        .sortedBy { it.name }

    /** Run on I/O. Invalid files and any incomplete journal tail remain available for diagnosis. */
    fun recoverInterruptedMatchCheckpoints(
        checkpoints: List<File> = interruptedCheckpointFiles()
    ): InterruptedMatchRecovery {
        val recovered = mutableListOf<String>()
        val failures = linkedMapOf<String, String>()

        checkpoints.forEach { checkpoint ->
            runCatching {
                if (checkpoint.name.endsWith(".odxjournal")) {
                    return@runCatching recoverJournal(checkpoint)
                }
                val archive = checkpoint.inputStream().use { input ->
                    MatchArchivePackageReader.read(
                        input = input,
                        artifactPolicy = MatchArchivePackageReader.ArtifactPolicy.REFERENCES_ONLY
                    )
                }
                val matchStartedAt = archive.articles.minOfOrNull { it.perceivedAt }
                    ?: checkpoint.lastModified()
                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT)
                    .format(Date(matchStartedAt))
                val destination = nextRecoveredDestination(timestamp)
                moveCheckpoint(checkpoint, destination)
                destination
            }.onSuccess { destination ->
                recovered += destination.name
            }.onFailure { error ->
                failures[checkpoint.name] = error.message ?: error::class.java.simpleName
            }
        }
        return InterruptedMatchRecovery(recovered, failures)
    }

    private fun recoverJournal(checkpoint: File): File {
        val recovery = MatchCheckpointJournal.read(checkpoint)
        val archive = recovery.archive
        require(archive.matchId.matches(Regex("[A-Za-z0-9_-]+")))
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT)
            .format(Date(archive.articles.minOf { it.perceivedAt }))
        val destination = File(internalArchiveDirectory, "${timestamp}_${archive.matchId}_recovered.odxmatch.zip")
        val compact = archive.copy(articles = MatchArchiveArticleSelector.select(
            archive.articles, MatchArchiveExportMode.COMPACT_CITED_EVIDENCE))
        // A crash after publication but before retiring the journal must not duplicate the match.
        if (destination.exists()) {
            val existing = destination.inputStream().use { MatchArchivePackageReader.read(it,
                artifactPolicy = MatchArchivePackageReader.ArtifactPolicy.REFERENCES_ONLY) }
            require(existing == compact) { "Recovered archive differs from checkpoint" }
        } else {
            val staged = File(internalArchiveDirectory, ".${destination.name}.tmp")
            try {
                FileOutputStream(staged).use { MatchArchivePackageWriter.write(compact, it, context.filesDir) }
                java.io.RandomAccessFile(staged, "rw").use { it.fd.sync() }
                publishStagedMatchArchive(staged, destination)
            } finally { staged.delete() }
        }
        // Retain a torn tail, rather than treating the unrecovered bytes as valid testimony.
        if (recovery.incompleteTail) moveCheckpoint(checkpoint, File(checkpoint.path + ".recovered"))
        else check(checkpoint.delete()) { "Could not retire recovered checkpoint" }
        return destination
    }

    private fun nextRecoveredDestination(timestamp: String): File {
        var candidate = File(internalArchiveDirectory, "${timestamp}_recovered.odxmatch.zip")
        var counter = 1
        while (candidate.exists()) {
            candidate = File(internalArchiveDirectory, "${timestamp}_recovered_$counter.odxmatch.zip")
            counter++
        }
        return candidate
    }

    private fun moveCheckpoint(source: File, destination: File) {
        destination.parentFile?.mkdirs()
        try {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), destination.toPath())
        }
    }

    fun listArchives(): List<ArchiveEntry> {
        return internalArchiveDirectory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.length() > 0L && it.name.lowercase().endsWith(".odxmatch.zip") }
            .map { file ->
                ArchiveEntry(
                    name = file.name,
                    uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.archive-files",
                        file
                    ),
                    lastModified = file.lastModified(),
                    byteCount = file.length()
                )
            }
            .sortedByDescending { it.name }
    }

    /** Run on I/O after a successful save/recovery. Portable Downloads exports remain untouched. */
    fun trimCompletedArchives(protectedMatchIds: Set<String> = emptySet()): MatchArchiveRetention.Result =
        synchronized(retentionLock) {
            val records = com.example.overdex.battle.history.BattleRecordStore(context)
            val legacyClipSources = com.example.overdex.battle.replay.ReplayExcerptStore(context.filesDir)
                .unprotectedSourceMatchIds()
            archiveRetention.trim(protectedMatchIds = protectedMatchIds + legacyClipSources) { file ->
                val archive = file.inputStream().use { MatchArchivePackageReader.read(it) }
                val entry = ArchiveEntry(
                    file.name,
                    FileProvider.getUriForFile(context, "${context.packageName}.archive-files", file),
                    file.lastModified(), file.length()
                )
                records.preserveBeforeArchiveRemoval(archive, entry)
            }.also { result ->
                if (result.deletedNames.isNotEmpty()) android.util.Log.i(
                    "MATCH_ARCHIVE", "Kept newest 50 matches; retired ${result.deletedNames.size} archives (${result.bytesRemoved} bytes)"
                )
                result.failures.forEach { (name, reason) ->
                    android.util.Log.w("MATCH_ARCHIVE", "Retention kept $name: $reason")
                }
            }
        }

    fun exportMatch(
        matchId: MatchId,
        realityTimeline: RealityTimeline,
        mode: MatchArchiveExportMode = MatchArchiveExportMode.COMPACT_CITED_EVIDENCE,
        requestedFileName: String? = null,
        onArtifactVerified: ((completed: Int, total: Int) -> Unit)? = null
    ): Uri? {
        val fileName = if (requestedFileName == null) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT)
            val baseTimestamp = dateFormat.format(Date())
            var candidate = "$baseTimestamp.odxmatch.zip"
            var counter = 1
            while (File(internalArchiveDirectory, candidate).exists()) {
                candidate = "${baseTimestamp}_$counter.odxmatch.zip"
                counter++
            }
            candidate
        } else {
            require(requestedFileName.endsWith(".odxmatch.zip")) {
                "Requested archive name must end with .odxmatch.zip"
            }
            // A checkpoint is replaced only after its new package has been built
            // successfully below. This keeps an earlier checkpoint available if
            // serialization or artifact verification fails.
            requestedFileName
        }

        // Build in cache first. A failed package must never replace the last
        // valid internal checkpoint or appear as an empty archive.
        val temporary = File.createTempFile("overdex-match-", ".odxmatch.zip", context.cacheDir)
        try {
            FileOutputStream(temporary).use {
                MatchArchiveExporter.export(
                    realityTimeline,
                    matchId,
                    it,
                    context.filesDir,
                    mode,
                    onArtifactVerified
                )
            }
            require(temporary.length() > 0L) { "Archive package was empty." }
            internalArchiveDirectory.mkdirs()
            val destination = File(internalArchiveDirectory, fileName)
            val staged = File(internalArchiveDirectory, ".$fileName.tmp")
            temporary.copyTo(staged, overwrite = true)
            publishStagedMatchArchive(staged, destination)
            return FileProvider.getUriForFile(
                context,
                "${context.packageName}.archive-files",
                destination
            )
        } finally {
            temporary.delete()
        }
    }

    /**
     * Publishes a portable copy to Downloads/Odxmatches without requesting a
     * folder-wide storage grant. Live checkpoints and automatic completed-match
     * saves remain in app-owned internal storage.
     */
    fun exportMatchToDownloads(
        matchId: MatchId,
        realityTimeline: RealityTimeline,
        mode: MatchArchiveExportMode,
        onArtifactVerified: ((completed: Int, total: Int) -> Unit)? = null
    ): Uri? = exportArchiveToDownloads(
        MatchArchiveSource(matchId, realityTimeline).snapshot(),
        mode, onArtifactVerified
    )

    /** Exports an already loaded snapshot, including after process recreation. */
    fun exportArchiveToDownloads(
        archive: MatchArchive,
        mode: MatchArchiveExportMode,
        onArtifactVerified: ((completed: Int, total: Int) -> Unit)? = null
    ): Uri? {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", Locale.ROOT)
        val archiveKind = when (mode) {
            MatchArchiveExportMode.TIMELINE_ONLY -> "replay-only"
            MatchArchiveExportMode.FULL_FORENSIC -> "full"
            MatchArchiveExportMode.COMPACT_CITED_EVIDENCE -> "compact"
        }
        val fileName = "${dateFormat.format(Date())}-$archiveKind.odxmatch.zip"

        val temporary = File.createTempFile("overdex-export-", ".odxmatch.zip", context.cacheDir)
        var publishedUri: Uri? = null
        try {
            FileOutputStream(temporary).use {
                MatchArchivePackageWriter.write(
                    archive.forExport(mode),
                    it,
                    context.filesDir,
                    onArtifactVerified
                )
            }
            require(temporary.length() > 0L) { "Archive package was empty." }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Odxmatches")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            publishedUri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values
            ) ?: throw java.io.IOException("Unable to create the archive in Downloads/Odxmatches.")
            try {
                context.contentResolver.openOutputStream(publishedUri, "w")
                    ?.use { output -> temporary.inputStream().use { input -> input.copyTo(output) } }
                    ?: throw java.io.IOException("Unable to write the archive in Downloads/Odxmatches.")
                context.contentResolver.update(
                    publishedUri,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null
                )
                return publishedUri
            } catch (error: Exception) {
                context.contentResolver.delete(publishedUri, null, null)
                throw error
            }
        } finally {
            temporary.delete()
        }
    }

    /** Append and fsync only new testimony; already durable artifacts stay in their store. */
    @Synchronized
    fun exportMatchCheckpoint(
        matchId: MatchId,
        realityTimeline: RealityTimeline
    ): Int {
        require(matchId.value.matches(Regex("[A-Za-z0-9_-]+")))
        return checkpointWriters.getOrPut(matchId) {
            MatchCheckpointJournal(File(internalArchiveDirectory, "active-${matchId.value}.odxjournal"), matchId)
        }.append(realityTimeline)
    }

    @Synchronized
    fun deleteMatchCheckpoint(matchId: MatchId): Boolean {
        checkpointWriters.remove(matchId)
        return listOf(".odxmatch.zip", ".odxjournal").map { suffix ->
            val file = File(internalArchiveDirectory, "active-${matchId.value}$suffix")
            !file.exists() || file.delete()
        }.all { it }
    }

    /** Releases the old Archive Directory tree grant once after this storage migration. */
    private fun releaseRetiredArchiveFolderPermission() {
        val prefs = context.getSharedPreferences("overdex_archive_directory_prefs", Context.MODE_PRIVATE)
        val rawUri = prefs.getString("archive_folder_uri", null)
        if (rawUri != null) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(rawUri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
        }
        prefs.edit().clear().apply()
    }

    private companion object { val retentionLock = Any() }
}
