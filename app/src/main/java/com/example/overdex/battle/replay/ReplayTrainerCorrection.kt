package com.example.overdex.battle.replay

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

/** A trainer's statement, separate from the source archive and automatic observations. */
@Serializable
data class ReplayTrainerCorrection(
    val id: String,
    val matchId: String,
    /** Offset from the match start, not the device's nonportable monotonic clock. */
    val matchOffsetNanos: Long,
    val kind: ReplayCorrectionKind,
    val side: ReplayCorrectionSide,
    val speciesName: String,
    val moveName: String? = null,
    val note: String = "",
    val authorName: String,
    val createdAtMillis: Long,
    /** An explicit trainer assertion, never inferred solely from proximity. */
    val finishingMoveId: String? = null,
    /** Source identity is retained when another trainer's file is imported. */
    val originMatchId: String? = null,
) {
    fun validate() {
        require(runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false))
        require(matchId.isNotBlank() && matchId.length <= 200)
        require(matchOffsetNanos in 0..3_600_000_000_000L) { "Entry time must be within one hour of match start." }
        require(speciesName.isNotBlank() && speciesName.length <= 100)
        require(authorName.isNotBlank() && authorName.length <= 100)
        require(note.length <= 2000)
        require(moveName == null || (moveName.isNotBlank() && moveName.length <= 100))
        require(kind == ReplayCorrectionKind.FAINT || moveName != null)
        require(kind != ReplayCorrectionKind.FAINT || moveName == null)
        require(createdAtMillis >= 0)
        require(originMatchId == null || (originMatchId.isNotBlank() && originMatchId.length <= 200))
        require(finishingMoveId == null || (kind == ReplayCorrectionKind.FAINT &&
            runCatching { UUID.fromString(finishingMoveId).toString() == finishingMoveId }.getOrDefault(false) &&
            finishingMoveId != id))
    }
}

@Serializable enum class ReplayCorrectionKind { FAST_MOVE, CHARGED_MOVE, FAINT }
@Serializable enum class ReplayCorrectionSide { PLAYER, OPPONENT }

/** Each statement is independently retained; a bad file cannot hide other corrections. */
class ReplayTrainerCorrectionStore(private val root: File) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }
    private val directory get() = File(root, "replay_trainer_corrections")

    @Synchronized fun add(correction: ReplayTrainerCorrection) {
        correction.validate()
        check(directory.isDirectory || directory.mkdirs()) { "Could not create correction storage." }
        val target = file(correction.matchId, correction.id)
        check(!target.exists()) { "Correction already exists." }
        val temporary = File.createTempFile("correction-", ".tmp", directory)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(json.encodeToString(correction).toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } finally { temporary.delete() }
    }

    fun list(matchId: String): List<ReplayTrainerCorrection> {
        val prefix = hash(matchId) + "-"
        return directory.listFiles().orEmpty().asSequence()
            .filter { it.isFile && it.name.startsWith(prefix) && it.name.endsWith(".json") && it.length() <= 16_384 }
            .mapNotNull { file -> runCatching {
                json.decodeFromString<ReplayTrainerCorrection>(file.readText()).also {
                    it.validate()
                    require(it.matchId == matchId && file == this.file(matchId, it.id))
                }
            }.getOrNull() }
            .sortedWith(compareBy<ReplayTrainerCorrection> { it.matchOffsetNanos }.thenBy { it.id })
            .toList()
    }

    /** The caller explicitly confirms the target match and whether perspectives are reversed. */
    @Synchronized fun importFile(bytes: ByteArray, targetMatchId: String, reverseSides: Boolean): Int {
        require(targetMatchId.isNotBlank() && targetMatchId.length <= 200)
        val source = ReplayCorrectionTransfer.decode(bytes)
        val mapped = source.entries.map { entry -> if (source.sourceMatchId == targetMatchId && !reverseSides) entry else entry.copy(matchId = targetMatchId,
            side = if (!reverseSides) entry.side else if (entry.side == ReplayCorrectionSide.PLAYER)
                ReplayCorrectionSide.OPPONENT else ReplayCorrectionSide.PLAYER,
            originMatchId = entry.originMatchId ?: source.sourceMatchId) }
        val existing = list(targetMatchId).associateBy { it.id }
        mapped.forEach { entry -> require(existing[entry.id] == null || existing[entry.id] == entry) {
            "An entry with the same ID already has different content."
        } }
        val importDirectory = File(root, "replay_correction_imports")
        check(importDirectory.isDirectory || importDirectory.mkdirs())
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val original = File(importDirectory, "$digest.json")
        if (!original.exists()) {
            val temp = File.createTempFile("import-", ".tmp", importDirectory)
            try {
                FileOutputStream(temp).use { it.write(bytes); it.fd.sync() }
                Files.move(temp.toPath(), original.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } finally { temp.delete() }
        }
        mapped.filter { it.id !in existing }.forEach(::add)
        return mapped.count { it.id !in existing }
    }

    private fun file(matchId: String, id: String) = File(directory, "${hash(matchId)}-$id.json")
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
