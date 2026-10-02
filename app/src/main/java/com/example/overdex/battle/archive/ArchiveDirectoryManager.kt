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

class ArchiveDirectoryManager(private val context: Context) {
    private val internalArchiveDirectory = File(context.filesDir, "match_archives")

    init {
        releaseRetiredArchiveFolderPermission()
    }

    data class ArchiveEntry(
        val name: String,
        val uri: Uri,
        val lastModified: Long,
        val byteCount: Long
    )

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
            if (destination.exists() && !destination.delete()) {
                staged.delete()
                throw java.io.IOException("Unable to replace internal match checkpoint.")
            }
            if (!staged.renameTo(destination)) {
                staged.delete()
                throw java.io.IOException("Unable to publish internal match checkpoint.")
            }
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
    ): Uri? {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS", Locale.ROOT)
        val archiveKind = if (mode == MatchArchiveExportMode.FULL_FORENSIC) "full" else "compact"
        val fileName = "${dateFormat.format(Date())}-$archiveKind.odxmatch.zip"

        val temporary = File.createTempFile("overdex-export-", ".odxmatch.zip", context.cacheDir)
        var publishedUri: Uri? = null
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

    /**
     * Writes the latest accepted portion of an active match under a stable name.
     * Checkpoints are compact and replaceable; the completed result export keeps
     * the normal timestamped filename.
     */
    fun exportMatchCheckpoint(
        matchId: MatchId,
        realityTimeline: RealityTimeline,
        onArtifactVerified: ((completed: Int, total: Int) -> Unit)? = null
    ): Uri? = exportMatch(
        matchId = matchId,
        realityTimeline = realityTimeline,
        mode = MatchArchiveExportMode.COMPACT_CITED_EVIDENCE,
        onArtifactVerified = onArtifactVerified,
        requestedFileName = "active-${matchId.value}.odxmatch.zip"
    )

    fun deleteMatchCheckpoint(matchId: MatchId): Boolean {
        val name = "active-${matchId.value}.odxmatch.zip"
        val internalFile = File(internalArchiveDirectory, name)
        return !internalFile.exists() || internalFile.delete()
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
}
