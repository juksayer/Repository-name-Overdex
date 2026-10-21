package com.example.overdex.battle.archive

import com.example.overdex.battle.observation.MatchId
import com.example.overdex.battle.reality.RealityTimeline
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

/** Append-only crash recovery. Artifacts are already durable in the content-addressed store. */
internal class MatchCheckpointJournal(private val file: File, private val matchId: MatchId) {
    private var nextIndex = 0
    private var articleCount = 0
    private var committedLength = 0L
    private var initialized = false

    @Synchronized fun append(timeline: RealityTimeline): Int {
        if (!initialized) {
            if (file.exists()) {
                val recovered = read(file)
                require(recovered.archive.matchId == matchId.value)
                nextIndex = recovered.timelineEndIndex
                articleCount = recovered.archive.articles.size
                committedLength = recovered.validLength
            }
            initialized = true
        }
        val sourceTail = timeline.getArticlesFrom(nextIndex)
        val endIndex = nextIndex + sourceTail.size
        val tail = sourceTail.filter { it.matchId == matchId }
        if (tail.isEmpty()) { nextIndex = endIndex; return 0 }
        val bytes = json.encodeToString(Batch(matchId = matchId.value, firstIndex = articleCount,
            timelineEndIndex = endIndex,
            articles = tail.map(RealityArticleArchiveMapper::map))).toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BATCH_BYTES) { "Checkpoint batch exceeds safety limit" }
        file.parentFile?.mkdirs()
        RandomAccessFile(file, "rw").use { output ->
            // A failed prior append may have left a partial tail. Its prefix remains intact.
            output.setLength(committedLength)
            output.seek(committedLength)
            output.writeInt(MAGIC)
            output.writeInt(bytes.size)
            output.write(bytes)
            output.write(digest(bytes))
            output.fd.sync()
            committedLength = output.filePointer
            nextIndex = endIndex
            articleCount += tail.size
        }
        return tail.size
    }

    @Serializable private data class Batch(
        val schemaVersion: Int = 1, val matchId: String, val firstIndex: Int, val timelineEndIndex: Int,
        val articles: List<ArchivedRealityArticle>,
    )

    data class Recovery(val archive: MatchArchive, val validLength: Long, val incompleteTail: Boolean,
        val timelineEndIndex: Int)

    companion object {
        private const val MAGIC = 0x4f44584a // ODXJ
        private const val MAX_BATCH_BYTES = 32 * 1024 * 1024
        private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true; classDiscriminator = "kind" }
        private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)

        fun read(file: File): Recovery {
            val articles = mutableListOf<ArchivedRealityArticle>()
            var matchId: String? = null
            var validLength = 0L
            var timelineEndIndex = 0
            RandomAccessFile(file, "r").use { input ->
                while (input.filePointer < input.length()) {
                    // Only an incomplete final write is recoverable. Corrupt complete records fail closed.
                    if (input.length() - input.filePointer < 8) break
                    require(input.readInt() == MAGIC) { "Invalid checkpoint record" }
                    val size = input.readInt()
                    require(size in 1..MAX_BATCH_BYTES) { "Invalid checkpoint record length" }
                    if (input.length() - input.filePointer < size.toLong() + 32) break
                    val bytes = ByteArray(size).also(input::readFully)
                    val expected = ByteArray(32).also(input::readFully)
                    require(digest(bytes).contentEquals(expected)) { "Checkpoint checksum mismatch" }
                    val batch = json.decodeFromString<Batch>(bytes.toString(Charsets.UTF_8))
                    require(batch.schemaVersion == 1 && batch.firstIndex == articles.size)
                    require(batch.articles.isNotEmpty() && batch.articles.all { it.matchId == batch.matchId })
                    require(batch.timelineEndIndex >= timelineEndIndex + batch.articles.size)
                    require(matchId == null || matchId == batch.matchId) { "Checkpoint mixes Matches" }
                    matchId = batch.matchId
                    articles += batch.articles
                    timelineEndIndex = batch.timelineEndIndex
                    validLength = input.filePointer
                }
                require(matchId != null) { "No complete checkpoint record" }
                return Recovery(MatchArchive(matchId = requireNotNull(matchId), articles = articles),
                    validLength, validLength != input.length(), timelineEndIndex)
            }
        }
    }
}
