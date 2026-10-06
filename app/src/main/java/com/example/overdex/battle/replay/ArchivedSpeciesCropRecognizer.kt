package com.example.overdex.battle.replay

import android.graphics.BitmapFactory
import android.util.Log
import com.example.overdex.battle.archive.ArchivedCropCaptured
import com.example.overdex.battle.archive.MatchArchive
import com.example.overdex.battle.observation.BattleCropContracts
import com.example.overdex.battle.observation.SpeciesTextResolver
import com.example.overdex.data.observation.SpeciesNameRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

data class ReplayCropProgress(val stage: String, val completed: Int = 0, val total: Int = 0)

/**
 * Re-reads only the durable species-name crops needed by Match Replay.
 *
 * Opening an archive remains lightweight: no PNG is decoded with the Timeline.
 * Entering replay explicitly requests a bounded chronological sample from each
 * species crop. The archive remains immutable; these are replay projections.
 */
object ArchivedSpeciesCropRecognizer {
    suspend fun recognize(
        archive: MatchArchive,
        openArchive: () -> InputStream?,
        knownSpeciesNames: Set<String>,
        resolveSpeciesId: suspend (String) -> Int?,
        onProgress: (ReplayCropProgress) -> Unit = {}
    ): List<ReplayIdentityObservation> = withContext(Dispatchers.IO) {
        if (knownSpeciesNames.isEmpty()) return@withContext emptyList()
        val targets = listOf(
            sampleTargets(archive, "PLAYER", BattleCropContracts.playerActiveSpeciesText.cropName),
            sampleTargets(archive, "OPPONENT", BattleCropContracts.opponentActiveSpeciesText.cropName)
        ).flatten()
        if (targets.isEmpty()) return@withContext emptyList()

        val artifactTargets = targets.associateBy { it.payload.artifactPath }
        onProgress(ReplayCropProgress("READING CROPS", 0, artifactTargets.size))
        val encodedArtifacts = readSelectedArtifacts(openArchive() ?: return@withContext emptyList(), artifactTargets) { completed ->
            onProgress(ReplayCropProgress("READING CROPS", completed, artifactTargets.size))
        }
        Log.d("REPLAY_SPECIES", "selected=${targets.size} artifacts=${encodedArtifacts.size}")
        val recognizedByTarget = linkedMapOf<Target, String?>()
        var completed = 0
        onProgress(ReplayCropProgress("VERIFYING CROPS", 0, targets.size))
        targets.forEach { target ->
            val path = target.payload.artifactPath
            val bytes = encodedArtifacts[path]
            recognizedByTarget[target] = if (bytes == null || !target.matches(bytes)) {
                null
            } else {
                val bitmap = decodeTargetCrop(bytes, target.payload)
                if (bitmap == null) null else try {
                    SpeciesNameRecognizer.recognizeCandidates(bitmap)
                        .firstNotNullOfOrNull { SpeciesTextResolver.resolve(it.text, knownSpeciesNames) }
                } finally {
                    bitmap.recycle()
                }
            }
            onProgress(ReplayCropProgress("VERIFYING CROPS", ++completed, targets.size))
        }

        onProgress(ReplayCropProgress("RESOLVING SPECIES"))
        val lastBySide = mutableMapOf<String, String>()
        buildList {
            targets.sortedBy(Target::atNanos).forEach { target ->
                val speciesName = recognizedByTarget[target] ?: return@forEach
                if (lastBySide[target.side] == speciesName) return@forEach
                lastBySide[target.side] = speciesName
                Log.d("REPLAY_SPECIES", "side=${target.side} species=$speciesName at=${target.atNanos}")
                add(
                    ReplayIdentityObservation(
                        side = target.side,
                        speciesName = speciesName,
                        speciesId = resolveSpeciesId(speciesName),
                        atNanos = target.atNanos,
                        basis = "RECONSTRUCTED FROM ARCHIVED SPECIES CROP"
                    )
                )
            }
        }
    }

    private fun sampleTargets(archive: MatchArchive, side: String, cropName: String): List<Target> {
        val available = archive.articles.mapNotNull { article ->
            val payload = article.payload as? ArchivedCropCaptured ?: return@mapNotNull null
            if (payload.cropName != cropName) return@mapNotNull null
            Target(side, article.monotonicTimeNanos ?: return@mapNotNull null, payload)
        }.sortedBy(Target::atNanos)
        if (available.isEmpty()) return emptyList()

        val sampled = mutableListOf<Target>()
        var lastAt = Long.MIN_VALUE
        available.forEach { target ->
            if (sampled.isEmpty() || target.atNanos - lastAt >= SAMPLE_INTERVAL_NANOS) {
                sampled += target
                lastAt = target.atNanos
            }
        }
        if (sampled.last().atNanos != available.last().atNanos) sampled += available.last()
        return sampled.take(MAX_SAMPLES_PER_SIDE)
    }

    private fun decodeTargetCrop(bytes: ByteArray, payload: ArchivedCropCaptured): android.graphics.Bitmap? {
        val stored = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        if (payload.artifactPath.startsWith("artifacts/crops/")) return stored
        return try {
            if (stored.width != payload.sourceWidth || stored.height != payload.sourceHeight) return null
            val width = payload.cropRight - payload.cropLeft
            val height = payload.cropBottom - payload.cropTop
            if (payload.cropLeft < 0 || payload.cropTop < 0 || width <= 0 || height <= 0 ||
                payload.cropRight > stored.width || payload.cropBottom > stored.height
            ) return null
            android.graphics.Bitmap.createBitmap(stored, payload.cropLeft, payload.cropTop, width, height)
        } finally {
            stored.recycle()
        }
    }

    private fun readSelectedArtifacts(
        input: InputStream,
        targets: Map<String, Target>,
        onProgress: (Int) -> Unit
    ): Map<String, ByteArray> {
        val remaining = targets.keys.toMutableSet()
        val found = linkedMapOf<String, ByteArray>()
        input.use { raw ->
            ZipInputStream(raw).use { zip ->
                var entry = zip.nextEntry
                while (entry != null && remaining.isNotEmpty()) {
                    if (!entry.isDirectory && entry.name in remaining) {
                        val expected = targets.getValue(entry.name).payload.byteCount
                        if (expected in 1..MAX_ARTIFACT_BYTES) {
                            found[entry.name] = readBounded(zip, expected.toInt())
                        }
                        remaining -= entry.name
                        onProgress(targets.size - remaining.size)
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return found
    }

    private fun readBounded(input: InputStream, expectedBytes: Int): ByteArray {
        val output = ByteArrayOutputStream(expectedBytes)
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= MAX_ARTIFACT_BYTES) { "Archived crop exceeds replay limit." }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private data class Target(
        val side: String,
        val atNanos: Long,
        val payload: ArchivedCropCaptured
    ) {
        fun matches(bytes: ByteArray): Boolean =
            bytes.size.toLong() == payload.byteCount && sha256(bytes) == payload.sha256
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private const val SAMPLE_INTERVAL_NANOS = 2_000_000_000L
    private const val MAX_SAMPLES_PER_SIDE = 80
    private const val MAX_ARTIFACT_BYTES = 4 * 1024 * 1024
}
