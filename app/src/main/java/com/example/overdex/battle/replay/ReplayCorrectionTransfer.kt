package com.example.overdex.battle.replay

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Base64

/** Small portable file, carried as a bounded payload in an authenticated Comms message. */
@Serializable
data class ReplayCorrectionFile(val version: Int = 1, val sourceMatchId: String, val entries: List<ReplayTrainerCorrection>)

object ReplayCorrectionTransfer {
    private const val PREFIX = "OVERDEX-CORRECTION-FILE-V1:"
    private const val MAX_FILE_BYTES = 2900
    private const val MAX_MESSAGE_CHARS = 4000
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }

    fun readBounded(input: java.io.InputStream): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= MAX_FILE_BYTES) { "Correction file is too large." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    fun encode(file: ReplayCorrectionFile): ByteArray {
        validate(file)
        return json.encodeToString(file).toByteArray(Charsets.UTF_8).also { require(it.size <= MAX_FILE_BYTES) { "Too many entries for one correction file." } }
    }
    fun decode(bytes: ByteArray): ReplayCorrectionFile {
        require(bytes.size in 1..MAX_FILE_BYTES) { "Correction file is too large or empty." }
        return json.decodeFromString<ReplayCorrectionFile>(bytes.toString(Charsets.UTF_8)).also(::validate)
    }
    fun toMessage(bytes: ByteArray): String {
        decode(bytes)
        return (PREFIX + Base64.getEncoder().encodeToString(bytes)).also { require(it.length <= MAX_MESSAGE_CHARS) }
    }
    fun fromMessage(text: String): ByteArray? {
        if (!text.startsWith(PREFIX)) return null
        require(text.length <= MAX_MESSAGE_CHARS) { "Correction message is too large." }
        return Base64.getDecoder().decode(text.removePrefix(PREFIX)).also { decode(it) }
    }
    private fun validate(file: ReplayCorrectionFile) {
        require(file.version == 1 && file.sourceMatchId.isNotBlank() && file.sourceMatchId.length <= 200)
        require(file.entries.isNotEmpty() && file.entries.size <= 30)
        require(file.entries.map { it.id }.distinct().size == file.entries.size)
        file.entries.forEach { it.validate(); require(it.matchId == file.sourceMatchId) }
        file.entries.filter { it.finishingMoveId != null }.forEach { faint ->
            val move = requireNotNull(file.entries.singleOrNull { it.id == faint.finishingMoveId }) { "A linked move is missing." }
            require(move.kind != ReplayCorrectionKind.FAINT && move.side != faint.side && move.matchOffsetNanos <= faint.matchOffsetNanos)
        }
    }
}
