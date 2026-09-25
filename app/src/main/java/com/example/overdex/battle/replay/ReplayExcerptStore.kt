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

/** Local storage for references into Match archives; archive evidence remains untouched. */
class ReplayExcerptStore(private val root: File) {
    private val excerptsDirectory = File(root, "replay_excerpts")
    private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }

    fun save(
        archive: MatchArchive,
        start: ArchivedRealityArticle,
        end: ArchivedRealityArticle,
        title: String? = null
    ): ReplayExcerpt {
        val startTime = requireNotNull(start.monotonicTimeNanos) { "Excerpt start needs monotonic time." }
        val endTime = requireNotNull(end.monotonicTimeNanos) { "Excerpt end needs monotonic time." }
        val orderedStart = minOf(startTime, endTime)
        val orderedEnd = maxOf(startTime, endTime)
        val excerpt = ReplayExcerpt(
            excerptId = UUID.randomUUID().toString(),
            archiveMatchId = archive.matchId,
            archiveSha256 = sha256(MatchArchiveSerializer.serialize(archive).toByteArray(Charsets.UTF_8)),
            startMonotonicTimeNanos = orderedStart,
            endMonotonicTimeNanos = orderedEnd,
            startArticleId = if (startTime <= endTime) start.articleId else end.articleId,
            endArticleId = if (startTime <= endTime) end.articleId else start.articleId,
            title = title?.trim()?.takeIf { it.isNotEmpty() }
        )
        check(excerptsDirectory.exists() || excerptsDirectory.mkdirs()) { "Could not create replay excerpt directory." }
        File(excerptsDirectory, "${excerpt.excerptId}.odxexcerpt.json").writeText(json.encodeToString(excerpt))
        return excerpt
    }

    fun list(): List<ReplayExcerpt> = excerptsDirectory.listFiles()
        .orEmpty()
        .filter { it.isFile && it.name.endsWith(".odxexcerpt.json") }
        .mapNotNull { file -> runCatching { json.decodeFromString<ReplayExcerpt>(file.readText()) }.getOrNull() }
        .sortedByDescending { it.excerptId }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
