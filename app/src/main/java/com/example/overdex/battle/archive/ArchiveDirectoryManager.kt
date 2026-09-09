package com.example.overdex.battle.archive

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.overdex.battle.observation.MatchId
import com.example.overdex.battle.reality.RealityTimeline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ArchiveDirectoryManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("overdex_archive_directory_prefs", Context.MODE_PRIVATE)
    private val uriKey = "archive_folder_uri"

    fun getFolderUri(): Uri? {
        val uriString = prefs.getString(uriKey, null) ?: return null
        return Uri.parse(uriString)
    }

    fun saveFolderUri(uri: Uri) {
        prefs.edit().putString(uriKey, uri.toString()).apply()
    }

    fun clearFolderUri() {
        prefs.edit().remove(uriKey).apply()
    }

    fun getFolderDisplayName(): String {
        val uri = getFolderUri() ?: return "No folder configured"
        return try {
            val docFile = DocumentFile.fromTreeUri(context, uri)
            docFile?.name ?: uri.lastPathSegment ?: uri.toString()
        } catch (e: Exception) {
            uri.toString()
        }
    }

    fun isFolderAvailable(): Boolean {
        val uri = getFolderUri() ?: return false
        return try {
            val docFile = DocumentFile.fromTreeUri(context, uri)
            val available = docFile != null && docFile.exists() && docFile.canRead() && docFile.canWrite()
            if (!available) {
                clearFolderUri()
            }
            available
        } catch (e: Exception) {
            clearFolderUri()
            false
        }
    }

    data class ArchiveEntry(
        val name: String,
        val uri: Uri,
        val lastModified: Long
    )

    fun listArchives(): List<ArchiveEntry> {
        val uri = getFolderUri() ?: return emptyList()
        val docFile = DocumentFile.fromTreeUri(context, uri) ?: return emptyList()
        if (!docFile.exists() || !docFile.canRead()) {
            clearFolderUri()
            return emptyList()
        }

        return docFile.listFiles()
            .filter { it.isFile && (it.name?.lowercase()?.endsWith(".odxmatch.zip") == true) }
            .map { file ->
                ArchiveEntry(
                    name = file.name ?: "unknown.odxmatch.zip",
                    uri = file.uri,
                    lastModified = file.lastModified()
                )
            }
            .sortedByDescending { it.name } // Sort newest first by timestamp filename
    }

    fun exportMatch(matchId: MatchId, realityTimeline: RealityTimeline): Uri? {
        val uri = getFolderUri() ?: return null
        val parentDoc = DocumentFile.fromTreeUri(context, uri) ?: return null
        if (!parentDoc.exists() || !parentDoc.canWrite()) {
            clearFolderUri()
            return null
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT)
        val baseTimestamp = dateFormat.format(Date())
        var fileName = "$baseTimestamp.odxmatch.zip"
        var counter = 1

        // Check collisions inside the selected SAF folder using DocumentFile without relying on File.exists()
        while (parentDoc.findFile(fileName) != null) {
            fileName = "${baseTimestamp}_$counter.odxmatch.zip"
            counter++
        }

        val newFile = parentDoc.createFile("application/zip", fileName) ?: return null
        val outputStream = context.contentResolver.openOutputStream(newFile.uri, "w") ?: return null

        outputStream.use {
            MatchArchiveExporter.export(
                realityTimeline = realityTimeline,
                matchId = matchId,
                output = it
            )
        }

        return newFile.uri
    }
}
