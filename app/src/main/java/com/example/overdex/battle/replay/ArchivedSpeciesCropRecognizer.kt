package com.example.overdex.battle.replay

import com.example.overdex.battle.observation.ownedOcrPixels

import android.graphics.BitmapFactory
import android.util.Log
import com.example.overdex.battle.archive.ArchivedSpeciesCheckMeasured
import com.example.overdex.battle.observation.SpeciesCheckCoordinator
import com.example.overdex.battle.archive.ArchivedCropCaptured
import com.example.overdex.battle.archive.ArchivedPlayerTeamConfiguration
import com.example.overdex.battle.archive.badgeBattleBoundary
import com.example.overdex.battle.archive.hasPreBattleScreen
import com.example.overdex.battle.archive.withQualifiedBattleIdentities
import com.example.overdex.battle.archive.MatchArchive
import com.example.overdex.battle.observation.BattleCropContracts
import com.example.overdex.battle.observation.SpeciesTextResolver
import com.example.overdex.data.observation.SpeciesNameRecognizer
import com.example.overdex.data.observation.OcrPriority
import com.example.overdex.data.observation.OcrField
import com.example.overdex.data.observation.OcrHeightProfile
import com.example.overdex.data.observation.OcrSourceContext
import kotlinx.serialization.Serializable
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
    @Serializable private data class Reading(val name: String, val basis: String)

    suspend fun recognize(
        archive: MatchArchive,
        openArchive: () -> InputStream?,
        knownSpeciesNames: Set<String>,
        resolveSpeciesId: suspend (String) -> Int?,
        onProgress: (ReplayCropProgress) -> Unit = {}
    ): List<ReplayIdentityObservation> = recognize(archive, openArchive, knownSpeciesNames, resolveSpeciesId, onProgress, null)

    internal suspend fun recognize(
        archive: MatchArchive,
        openArchive: () -> InputStream?,
        knownSpeciesNames: Set<String>,
        resolveSpeciesId: suspend (String) -> Int?,
        onProgress: (ReplayCropProgress) -> Unit,
        cache: ReplayReviewCache?,
        onIncomplete: () -> Unit = {},
    ): List<ReplayIdentityObservation> = withContext(Dispatchers.IO) {
        if (!archive.mediaIncluded || knownSpeciesNames.isEmpty()) return@withContext emptyList()
        val targets = listOf(
            sampleTargets(archive, "PLAYER", BattleCropContracts.playerActiveSpeciesText.cropName),
            sampleTargets(archive, "OPPONENT", BattleCropContracts.opponentActiveSpeciesText.cropName)
        ).flatten()
        if (targets.isEmpty()) return@withContext emptyList()

        val rows = archive.articles.filter { it.matchId == archive.matchId }
        val hasStart = rows.badgeBattleBoundary() != null
        val configuration by lazy { ArchivedPlayerTeamConfiguration(archive.withQualifiedBattleIdentities().articles) }
        val key = cache?.key(listOf("battle-species-crops-v2") + knownSpeciesNames.sorted())
        val cached = if (cache != null && key != null) cache.read<List<Reading?>>(key) else null
        val names = cached?.takeIf { it.size == targets.size } ?: run readTargets@{
            val artifactTargets = targets.associateBy { it.payload.artifactPath }
            onProgress(ReplayCropProgress("READING CROPS", 0, artifactTargets.size))
            val input = openArchive() ?: run {
                onIncomplete()
                return@readTargets emptyList<Reading?>()
            }
            val encodedArtifacts = readSelectedArtifacts(input, artifactTargets) { completed ->
                onProgress(ReplayCropProgress("READING CROPS", completed, artifactTargets.size))
            }
            Log.d("REPLAY_SPECIES", "selected=${targets.size} artifacts=${encodedArtifacts.size}")
            var complete = true
            val heightProfiles = mapOf("PLAYER" to OcrHeightProfile(OcrField.SPECIES),
                "OPPONENT" to OcrHeightProfile(OcrField.SPECIES))
            var completed = 0
            onProgress(ReplayCropProgress("VERIFYING CROPS", 0, targets.size))
            val names = targets.map { target ->
                val path = target.payload.artifactPath
                val bytes = encodedArtifacts[path]
                val name = if (bytes == null || !target.matches(bytes)) {
                    complete = false
                    null
                } else {
                    val bitmap = decodeTargetCrop(bytes, target.payload)?.ownedOcrPixels(target.payload.statusBarRows, target.payload.overlayBounds)
                    if (bitmap == null) {
                        complete = false
                        null
                    } else try {
                        // New recordings retain the HUD footprint from capture time.
                        // Never use today's HUD position to classify an archived word.
                        SpeciesNameRecognizer.withReadingTurn(OcrPriority.NORMAL) {
                            val candidates = recognizeCandidates(bitmap,
                                OcrSourceContext(target.payload.sourceWidth, target.payload.overlayBounds),
                                heightProfiles.getValue(target.side))
                            if (hadReadFailure) {
                                complete = false
                                onIncomplete()
                            }
                            candidates
                        }
                            .firstNotNullOfOrNull { candidate ->
                                val reading = SpeciesTextResolver.match(candidate.text, knownSpeciesNames).resolution
                                reading?.takeIf { it.basis.startsWith("EXACT_CATALOGUE_TEXT") ||
                                    (hasStart && target.payload.nativeTeamBadgePresent == true && (target.side != "PLAYER" ||
                                        !configuration.conflictsWithFullRoster(it.speciesName, target.atNanos))) }
                                    ?.let { Reading(it.speciesName, it.basis) }
                            }
                    } finally {
                        bitmap.recycle()
                    }
                }
                onProgress(ReplayCropProgress("VERIFYING CROPS", ++completed, targets.size))
                name
            }

            if (complete && cache != null && key != null) cache.write(key, names)
            names
        }
        val recognizedByTarget = targets.zip(names).toMap()
        onProgress(ReplayCropProgress("RESOLVING SPECIES"))
        // A matching badge after a fresh identity check is independent proof
        // of who is still on screen, even when its text did not change.
        val checks = rows.mapNotNull { row -> (row.payload as? ArchivedSpeciesCheckMeasured)?.takeIf {
            it.status == "OPENED" && it.reason in SpeciesCheckCoordinator.VERIFICATION_REASONS
        } }.groupBy({ it.side }, { it.triggerMonotonicNanos })
        val lastBySide = mutableMapOf<String, ReplayIdentityObservation>()
        buildList {
            targets.sortedBy(Target::atNanos).forEach { target ->
                val reading = recognizedByTarget[target] ?: return@forEach
                val speciesName = reading.name
                val prior = lastBySide[target.side]
                if (prior?.speciesName == speciesName &&
                    checks[target.side].orEmpty().none { it > prior.atNanos && it <= target.atNanos }) return@forEach
                Log.d("REPLAY_SPECIES", "side=${target.side} species=$speciesName at=${target.atNanos}")
                val identity = ReplayIdentityObservation(
                    side = target.side,
                    speciesName = speciesName,
                    speciesId = resolveSpeciesId(speciesName),
                    atNanos = target.atNanos,
                    basis = "RECONSTRUCTED FROM ARCHIVED SPECIES CROP",
                    ocrMatchBasis = reading.basis,
                    articleId = target.articleId,
                )
                add(identity)
                lastBySide[target.side] = identity
            }
        }
    }

    private fun sampleTargets(archive: MatchArchive, side: String, cropName: String): List<Target> {
        val rows = archive.articles.filter { it.matchId == archive.matchId }
        val start = rows.badgeBattleBoundary()?.monotonicTimeNanos
        if (start == null && rows.hasPreBattleScreen()) return emptyList()
        val available = archive.articles.mapNotNull { article ->
            val payload = article.payload as? ArchivedCropCaptured ?: return@mapNotNull null
            if (article.matchId != archive.matchId || payload.cropName != cropName) return@mapNotNull null
            if (start != null && (article.monotonicTimeNanos ?: return@mapNotNull null) < start) return@mapNotNull null
            Target(article.articleId, side, article.monotonicTimeNanos ?: return@mapNotNull null, payload)
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
        val articleId: String,
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
