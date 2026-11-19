package com.example.overdex.battle.replay

import com.example.overdex.battle.archive.MatchArchive
import com.example.overdex.battle.archive.MatchArchiveSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Disposable readings only. A changed recording, timeline, app install or candidate set gets a new key. */
internal class ReplayReviewCache private constructor(private val directory: File, private val recordingKey: String) {
    fun key(parameters: List<String>): String =
        ReplayRetainedAudioRecovery.sha(Json.encodeToString(listOf(recordingKey) + parameters).encodeToByteArray())

    suspend inline fun <reified T> read(key: String): T? = withContext(Dispatchers.IO) {
        val file = File(directory, "$key.json")
        try {
            if (file.length() !in 1..MAX_ENTRY_BYTES) return@withContext null
            val contents = file.readText()
            val body = contents.substringAfter('\n', "")
            if (contents.substringBefore('\n') != ReplayRetainedAudioRecovery.sha(body.encodeToByteArray())) return@withContext null
            Json.decodeFromString<T>(body).also { file.setLastModified(System.currentTimeMillis()) }
        } catch (_: IOException) { null }
          catch (_: SerializationException) { null }
    }

    suspend inline fun <reified T> write(key: String, value: T) = withContext(Dispatchers.IO) {
        val body = try { Json.encodeToString(value).encodeToByteArray() }
        catch (_: SerializationException) { return@withContext }
        if (body.size + 65 > MAX_ENTRY_BYTES) return@withContext
        ensureActive()
        try {
            directory.mkdirs()
            val temporary = File.createTempFile("writing-", ".tmp", directory)
            try {
                temporary.outputStream().buffered().use { out ->
                    out.write(ReplayRetainedAudioRecovery.sha(body).encodeToByteArray())
                    out.write(10)
                    out.write(body)
                }
                ensureActive()
                Files.move(temporary.toPath(), File(directory, "$key.json").toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally {
                temporary.delete()
            }
            directory.listFiles { f -> f.extension == "json" }?.sortedByDescending { it.lastModified() }
                ?.drop(MAX_ENTRIES)?.forEach { it.delete() }
        } catch (_: IOException) {
            // Storage pressure must not prevent a successful review.
        }
    }

    companion object {
        private const val MAX_ENTRY_BYTES = 512L * 1024
        private const val MAX_ENTRIES = 128

        suspend fun open(directory: File, installedAt: Long, archive: MatchArchive,
                         openArchive: () -> InputStream?): ReplayReviewCache? = withContext(Dispatchers.IO) {
            val digest = MessageDigest.getInstance("SHA-256")
            val sink = object : OutputStream() {
                override fun write(b: Int) = digest.update(b.toByte())
                override fun write(b: ByteArray, off: Int, len: Int) = digest.update(b, off, len)
            }
            try {
                openArchive()?.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        sink.write(buffer, 0, n)
                    }
                } ?: return@withContext null
                val mediaDigest = digest.digest()
                MatchArchiveSerializer.serializeTo(archive, sink)
                val key = ReplayRetainedAudioRecovery.sha(mediaDigest + digest.digest() + installedAt.toString().encodeToByteArray())
                ReplayReviewCache(directory, key)
            } catch (_: IOException) { null }
              catch (_: SecurityException) { null }
        }
    }
}
