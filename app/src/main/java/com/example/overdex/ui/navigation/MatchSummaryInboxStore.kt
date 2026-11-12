package com.example.overdex.ui.navigation

import android.content.Context
import com.example.overdex.battle.archive.MatchArchive
import com.example.overdex.battle.archive.MatchArchiveArticleSelector
import com.example.overdex.battle.archive.MatchArchiveExportMode
import com.example.overdex.battle.archive.MatchArchiveSerializer
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** App-private review recovery. Call disk methods on I/O; no image/audio bytes are read or copied. */
internal class MatchSummaryInboxStore internal constructor(private val directory: File) {
    constructor(context: Context) : this(File(context.filesDir, "match_summary_inbox"))

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val stateFile get() = File(directory, "state.json")

    fun readState(): MatchSummaryInboxState = synchronized(fileLock) {
        if (!stateFile.exists()) MatchSummaryInboxState()
        else json.decodeFromString<MatchSummaryInboxState>(stateFile.readText())
    }

    /** A delayed queued save cannot undo a newer acknowledgment. Snapshot/archive writes are independent. */
    fun saveState(state: MatchSummaryInboxState): Boolean = synchronized(fileLock) {
        val previous = readState()
        if (stateFile.exists() && state.revision <= previous.revision) return false
        writeAtomically(stateFile, json.encodeToString(state))
        true
    }

    /** Refresh an existing match in place; a later refresh never changes pending/delivered membership. */
    fun upsert(archive: MatchArchive): Boolean = synchronized(fileLock) {
        require(archive.matchId.isNotBlank())
        val referenceOnly = archive.copy(
            articles = MatchArchiveArticleSelector.select(archive.articles, MatchArchiveExportMode.COMPACT_CITED_EVIDENCE),
            mediaIncluded = false,
        )
        val previous = readArchive(archive.matchId)
        if (previous != null && previous.articles.size > referenceOnly.articles.size) return false
        if (previous == referenceOnly) return false
        writeAtomically(archiveFile(archive.matchId), MatchArchiveSerializer.serialize(referenceOnly))
        true
    }

    fun readArchive(matchId: String): MatchArchive? = synchronized(fileLock) {
        val file = archiveFile(matchId)
        if (!file.exists()) null else MatchArchiveSerializer.deserialize(file.readText()).also {
            require(it.matchId == matchId) { "Review snapshot belongs to a different match." }
        }
    }

    private fun archiveFile(matchId: String): File {
        val name = MessageDigest.getInstance("SHA-256").digest(matchId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(File(directory, "matches"), "$name.json")
    }

    private fun writeAtomically(destination: File, text: String) {
        destination.parentFile?.mkdirs()
        val staged = File(destination.parentFile, ".${destination.name}.tmp")
        try {
            FileOutputStream(staged).use { output ->
                output.write(text.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            try {
                Files.move(staged.toPath(), destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(staged.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally { staged.delete() }
    }

    private companion object { val fileLock = Any() }
}
