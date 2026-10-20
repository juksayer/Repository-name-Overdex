package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.MatchArchive
import com.example.overdex.battle.archive.MatchArchiveSerializer
import com.example.overdex.battle.archive.ArchivedRealityArticle
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.security.DigestOutputStream

/** Run on I/O. Source snapshots live outside the automatic newest-50 archive cleanup. */
class ReplayExcerptStore(private val root: File) {
    private companion object {
        const val MAX_SOURCE_BYTES = 128L * 1024 * 1024
    }
    private val excerptsDirectory = File(root, "replay_excerpts")
    private val sourcesDirectory = File(root, "replay_sources")
    private val compilationsDirectory = File(root, "replay_compilations")
    private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }

    fun save(
        archive: MatchArchive,
        start: ArchivedRealityArticle,
        end: ArchivedRealityArticle,
        title: String? = null
    ): ReplayExcerpt {
        val startTime = requireNotNull(start.monotonicTimeNanos) { "Excerpt start needs monotonic time." }
        val endTime = requireNotNull(end.monotonicTimeNanos) { "Excerpt end needs monotonic time." }
        return saveRange(archive, minOf(startTime, endTime), maxOf(startTime, endTime), title)
    }

    @Synchronized
    fun saveRange(
        archive: MatchArchive,
        startTime: Long,
        endTime: Long,
        title: String? = null,
        notes: String = "",
        recoveredIdentities: List<ReplayIdentityObservation> = emptyList(),
    ): ReplayExcerpt {
        ReplayWindow(startTime, endTime)
        val model = MatchReplayModel(archive)
        require(startTime >= model.startNanos && endTime <= model.endNanos) { "Clip lies outside this replay." }
        // Preserve every article, including evidence preceding the clip and later
        // identity observations. Slicing here loses starting HP/energy and identities.
        val sourceHash = retainSource(archive)
        val timed = archive.articles.filter { it.monotonicTimeNanos != null }.sortedBy { it.monotonicTimeNanos }
        val excerpt = ReplayExcerpt(
            excerptId = UUID.randomUUID().toString(),
            archiveMatchId = archive.matchId,
            archiveSha256 = sourceHash,
            startMonotonicTimeNanos = startTime,
            endMonotonicTimeNanos = endTime,
            startArticleId = timed.lastOrNull { it.monotonicTimeNanos!! <= startTime }?.articleId,
            endArticleId = timed.lastOrNull { it.monotonicTimeNanos!! <= endTime }?.articleId,
            title = title?.trim()?.take(80)?.takeIf { it.isNotEmpty() } ?: "Clip ${list().size + 1}",
            createdAtMillis = System.currentTimeMillis(),
            notes = notes.trim().take(2000),
            recoveredIdentities = recoveredIdentities,
        )
        atomicText(excerptFile(excerpt.excerptId), json.encodeToString(excerpt))
        return excerpt
    }

    fun list(): List<ReplayExcerpt> = excerptsDirectory.listFiles()
        .orEmpty()
        .filter { it.isFile && it.name.endsWith(".odxexcerpt.json") }
        .mapNotNull { file -> runCatching { json.decodeFromString<ReplayExcerpt>(file.readText()) }.getOrNull() }
        .sortedByDescending { it.createdAtMillis }

    fun updateDetails(clip: ReplayExcerpt, title: String, notes: String): ReplayExcerpt {
        require(title.isNotBlank()) { "Give the clip a name." }
        require(excerptFile(clip.excerptId).isFile) { "Clip no longer exists." }
        return clip.copy(title = title.trim().take(80), notes = notes.trim().take(2000)).also {
            atomicText(excerptFile(it.excerptId), json.encodeToString(it))
        }
    }

    fun compilations(): List<ReplayCompilation> = compilationsDirectory.listFiles().orEmpty()
        .filter { it.isFile && it.name.endsWith(".odxcompilation.json") }
        .mapNotNull { runCatching { json.decodeFromString<ReplayCompilation>(it.readText()) }.getOrNull() }
        .sortedByDescending { it.updatedAtMillis }

    fun loadDraft(): ReplayCompilation? = File(root, "replay_compilation_draft.json")
        .takeIf { it.isFile }?.let { json.decodeFromString<ReplayCompilation>(it.readText()) }

    fun saveDraft(value: ReplayCompilation) =
        atomicText(File(root, "replay_compilation_draft.json"), json.encodeToString(value))

    fun clearDraft() {
        val file = File(root, "replay_compilation_draft.json")
        check(!file.exists() || file.delete()) { "Could not clear the saved draft." }
    }

    fun saveCompilation(value: ReplayCompilation): ReplayCompilation {
        require(value.title.isNotBlank()) { "Give the compilation a name." }
        require(value.entries.isNotEmpty()) { "Add at least one clip." }
        require(value.entries.map { it.id }.distinct().size == value.entries.size) { "Duplicate entry IDs." }
        val clips = list().associateBy { it.excerptId }
        val verifiedSources = mutableMapOf<String, String>()
        value.entries.forEach { entry ->
            val clip = requireNotNull(clips[entry.excerptId]) { "A clip is missing from the library." }
            val matchId = verifiedSources.getOrPut(clip.archiveSha256) {
                loadSource(clip).matchId // Also protects legacy references before committing.
            }
            require(matchId == clip.archiveMatchId) { "Clip source belongs to a different match." }
        }
        return value.copy(title = value.title.trim().take(80), updatedAtMillis = System.currentTimeMillis()).also {
            atomicText(File(compilationsDirectory, "${safeId(it.id)}.odxcompilation.json"), json.encodeToString(it))
        }
    }

    fun loadSource(clip: ReplayExcerpt): MatchArchive {
        val source = sourceFile(clip.archiveSha256)
        if (!source.isFile) {
            // Older versions saved only a reference. Recover only an exact source;
            // a different export with the same Match ID is not silently substituted.
            val recovered = File(root, "match_archives").listFiles().orEmpty()
                .filter { it.isFile && it.name.endsWith(".odxmatch.zip") }
                .firstNotNullOfOrNull { file ->
                    runCatching {
                        val archive = file.inputStream().use {
                            com.example.overdex.battle.archive.MatchArchivePackageReader.read(it)
                        }
                        if (archive.matchId != clip.archiveMatchId) null else {
                            val original = MatchArchiveSerializer.serialize(archive).toByteArray(Charsets.UTF_8)
                            if (sha256(original) == clip.archiveSha256) original else null
                        }
                    }.getOrNull()
                } ?: error("Source recording is unavailable for ${clip.title ?: "this older clip"}.")
            check(recovered.size <= MAX_SOURCE_BYTES) { "Replay source exceeds the read limit." }
            atomicWrite(source) { output ->
                GZIPOutputStream(object : java.io.FilterOutputStream(output) {
                    override fun close() { flush() }
                }).use { it.write(recovered) }
            }
        }
        val bytes = GZIPInputStream(source.inputStream()).use { stream ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                check(output.size().toLong() + count <= MAX_SOURCE_BYTES) { "Replay source exceeds the read limit." }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        check(sha256(bytes) == clip.archiveSha256) { "Saved clip source failed its integrity check." }
        return MatchArchiveSerializer.deserialize(bytes.toString(Charsets.UTF_8)).also {
            check(it.matchId == clip.archiveMatchId) { "Saved clip belongs to a different match." }
        }
    }

    /** Legacy references stay protected until their own source snapshot exists. */
    fun unprotectedSourceMatchIds(): Set<String> = list()
        .filter { !runCatching { sourceFile(it.archiveSha256).isFile }.getOrDefault(false) }
        .map { it.archiveMatchId }.toSet()

    private fun retainSource(archive: MatchArchive): String {
        check(sourcesDirectory.isDirectory || sourcesDirectory.mkdirs()) { "Could not create clip storage." }
        val temporary = File.createTempFile("source-", ".tmp", sourcesDirectory)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileOutputStream(temporary).use { file ->
                GZIPOutputStream(file).use { gzip ->
                    // Do not publish a clip that the bounded reader cannot reopen.
                    val bounded = object : java.io.FilterOutputStream(DigestOutputStream(gzip, digest)) {
                        var written = 0L
                        override fun write(value: Int) {
                            check(++written <= MAX_SOURCE_BYTES) { "This replay is too large to save as a clip." }
                            out.write(value)
                        }
                        override fun write(bytes: ByteArray, offset: Int, length: Int) {
                            written += length
                            check(written <= MAX_SOURCE_BYTES) { "This replay is too large to save as a clip." }
                            out.write(bytes, offset, length)
                        }
                    }
                    MatchArchiveSerializer.serializeTo(archive, bounded)
                    gzip.finish()
                    file.fd.sync()
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            val target = sourceFile(hash)
            // Atomic replacement also repairs an interrupted/corrupt previous copy.
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            return hash
        } finally { temporary.delete() }
    }

    private fun sourceFile(hash: String): File {
        require(hash.matches(Regex("[a-f0-9]{64}"))) { "Invalid clip source identity." }
        return File(sourcesDirectory, "$hash.json.gz")
    }

    private fun safeId(id: String): String {
        require(id.matches(Regex("[a-zA-Z0-9_-]{1,80}"))) { "Invalid library identity." }
        return id
    }

    private fun excerptFile(id: String) = File(excerptsDirectory, "${safeId(id)}.odxexcerpt.json")
    private fun atomicText(file: File, value: String) = atomicWrite(file) { it.write(value.toByteArray(Charsets.UTF_8)) }
    private fun atomicWrite(file: File, write: (FileOutputStream) -> Unit) {
        check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs()) { "Could not create replay library." }
        val temporary = File.createTempFile("saving-", ".tmp", file.parentFile)
        try {
            FileOutputStream(temporary).use { output ->
                // Callers may finish wrappers but must leave this stream open for sync.
                write(output)
                output.fd.sync()
            }
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { temporary.delete() }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
