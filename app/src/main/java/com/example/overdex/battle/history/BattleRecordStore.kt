package com.example.overdex.battle.history

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import com.example.overdex.battle.archive.ArchiveDirectoryManager
import com.example.overdex.battle.archive.MatchArchive
import com.example.overdex.battle.archive.MatchArchivePackageReader
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.coroutines.coroutineContext

@Serializable
data class IndexedBattleRecord(val record: BattleRecord, val archiveUri: String,
    val archiveBytes: Long, val archiveModifiedAt: Long, val indexVersion: Int = 1)

data class BattleRecordLibrary(val records: List<IndexedBattleRecord>, val failures: List<String>)

/** Small, rebuildable JSON indexes. Reading history never decodes or retains PNGs. */
class BattleRecordStore(private val context: Context) {
    private val directory = File(context.filesDir, "battle_records")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun save(archive: MatchArchive, entry: ArchiveDirectoryManager.ArchiveEntry): IndexedBattleRecord = synchronized(writeLock) {
        val indexed = IndexedBattleRecord(BattleRecordProjector.project(archive), entry.uri.toString(), entry.byteCount, entry.lastModified)
        directory.mkdirs()
        val target = AtomicFile(indexFile(archive.matchId))
        val out = target.startWrite()
        try { out.write(json.encodeToString(indexed).toByteArray()); target.finishWrite(out) }
        catch (error: Exception) { target.failWrite(out); throw error }
        indexed
    }

    /** Retiring a smaller export must not replace a richer lifetime record. */
    fun preserveBeforeArchiveRemoval(archive: MatchArchive, entry: ArchiveDirectoryManager.ArchiveEntry) = synchronized(writeLock) {
        val previous = runCatching {
            json.decodeFromString<IndexedBattleRecord>(AtomicFile(indexFile(archive.matchId)).readFully().toString(Charsets.UTF_8))
        }.getOrNull()
        if (previous == null || previous.indexVersion != 1 || previous.record.articleCount < archive.articles.size) {
            save(archive, entry)
        }
    }

    suspend fun refresh(onProgress: (Int, Int) -> Unit = { _, _ -> }): BattleRecordLibrary = withContext(Dispatchers.IO) {
        val cached = directory.listFiles().orEmpty().filter { it.extension == "json" }.mapNotNull {
            runCatching { json.decodeFromString<IndexedBattleRecord>(AtomicFile(it).readFully().toString(Charsets.UTF_8)) }.getOrNull()
        }
        val byUri = cached.associateBy { it.archiveUri }
        val records = cached.associateBy { it.record.matchId }.toMutableMap()
        val failures = mutableListOf<String>()
        val entries = ArchiveDirectoryManager(context).listArchives().filterNot { it.name.startsWith("active-") }
        entries.forEachIndexed { index, entry ->
            coroutineContext.ensureActive()
            onProgress(index, entries.size)
            val previous = byUri[entry.uri.toString()]
            if (previous == null || previous.archiveBytes != entry.byteCount || previous.archiveModifiedAt != entry.lastModified || previous.indexVersion != 1) {
                try {
                    val archive = readArchive(entry.uri)
                    // Full/compact copies are the same match. Prefer the more complete record,
                    // not another set of lifetime increments.
                    val old = records[archive.matchId]
                    if (old == null || archive.articles.size >= old.record.articleCount) records[archive.matchId] = save(archive, entry)
                } catch (error: Exception) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    failures += "${entry.name}: ${error.message}"
                }
            }
        }
        onProgress(entries.size, entries.size)
        BattleRecordLibrary(records.values.sortedByDescending { it.record.recordedAt }, failures)
    }

    fun readArchive(uri: Uri): MatchArchive = context.contentResolver.openInputStream(uri)?.use {
        MatchArchivePackageReader.read(it, artifactPolicy = MatchArchivePackageReader.ArtifactPolicy.REFERENCES_ONLY)
    } ?: error("The recorded match is no longer available.")

    private fun indexFile(id: String): File {
        val name = MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(directory, "$name.json")
    }
    private companion object { val writeLock = Any() }
}
