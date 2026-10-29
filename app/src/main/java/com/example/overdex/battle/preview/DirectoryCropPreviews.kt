package com.example.overdex.battle.preview

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.overdex.battle.archive.*
import com.example.overdex.battle.artifact.CropArtifactReference
import com.example.overdex.battle.custody.CropCaptured
import com.example.overdex.battle.observation.BattleCropBounds
import com.example.overdex.battle.observation.BattleCropProvenance
import com.example.overdex.battle.reality.RealityArticle
import com.example.overdex.battle.reality.RealityTimeline
import java.io.File
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

enum class DirectoryCropSlot {
    PLAYER_SPECIES, OPPONENT_SPECIES, PLAYER_HP, OPPONENT_HP,
    PLAYER_BALLS, OPPONENT_BALLS, ANNOUNCEMENT
}

data class DirectoryCropImage(val bitmap: Bitmap?, val capturedAtMillis: Long, val articleId: String)

data class DirectoryCropPreviewState(
    val matchId: String? = null,
    val crops: Map<DirectoryCropSlot, DirectoryCropImage> = emptyMap(),
    val isLoading: Boolean = false,
) {
    val latestCaptureMillis: Long? get() = crops.values.maxOfOrNull { it.capturedAtMillis }
}

/** Pixel provenance is retained independently of any UI bitmap or current calibration. */
internal data class DirectoryCropReference(
    val articleId: String,
    val capturedAtMillis: Long,
    val capturedAtNanos: Long?,
    val artifact: CropArtifactReference,
    val provenance: BattleCropProvenance,
) {
    fun samePixels(other: DirectoryCropReference): Boolean =
        artifact == other.artifact && provenance == other.provenance
}

/** Chooses by capture time, never by asynchronous publication/delivery order. */
internal class DirectoryCropSelection(val matchId: String) {
    private val latest = linkedMapOf<DirectoryCropSlot, DirectoryCropReference>()
    val crops: Map<DirectoryCropSlot, DirectoryCropReference> get() = latest.toMap()

    fun accept(article: RealityArticle) {
        if (article.matchId?.value != matchId) return
        val crop = article.payload as? CropCaptured ?: return
        accept(crop.cropProvenance.cropName, DirectoryCropReference(article.id.value,
            article.perceivedAt, article.monotonicTimeNanos, crop.artifact, crop.cropProvenance))
    }

    fun accept(article: ArchivedRealityArticle) {
        if (article.matchId != matchId) return
        val crop = article.payload as? ArchivedCropCaptured ?: return
        accept(crop.cropName, DirectoryCropReference(article.articleId, article.perceivedAt,
            article.monotonicTimeNanos,
            CropArtifactReference(crop.artifactPath, crop.sha256, crop.byteCount, crop.mediaType),
            BattleCropProvenance(crop.cropName, crop.sourceWidth, crop.sourceHeight,
                BattleCropBounds(crop.cropLeft, crop.cropTop, crop.cropRight, crop.cropBottom))))
    }

    private fun accept(name: String, reference: DirectoryCropReference) {
        val slot = slots[name] ?: return
        val previous = latest[slot]
        val newer = previous == null || if (reference.capturedAtNanos != null && previous.capturedAtNanos != null) {
            reference.capturedAtNanos > previous.capturedAtNanos
        } else reference.capturedAtMillis > previous.capturedAtMillis
        if (newer) latest[slot] = reference
    }

    companion object {
        private val slots = mapOf(
            "PlayerActiveSpeciesTextCrop" to DirectoryCropSlot.PLAYER_SPECIES,
            "OpponentActiveSpeciesTextCrop" to DirectoryCropSlot.OPPONENT_SPECIES,
            "PlayerHpEvidenceCrop" to DirectoryCropSlot.PLAYER_HP,
            "OpponentHpEvidenceCrop" to DirectoryCropSlot.OPPONENT_HP,
            "PlayerPokeBallsCrop" to DirectoryCropSlot.PLAYER_BALLS,
            "OpponentPokeBallsCrop" to DirectoryCropSlot.OPPONENT_BALLS,
            "AnnouncementCrop" to DirectoryCropSlot.ANNOUNCEMENT,
        )

        fun fromBattleArchive(archive: MatchArchive): DirectoryCropSelection? {
            // A capture session, Team Select scan, or menu OCR is not a battle.
            val battle = archive.articles.any { it.matchId == archive.matchId && when (it.payload) {
                ArchivedMatchStarted, is ArchivedSupportingMatchStart, is ArchivedMatchEnded,
                is ArchivedFastMoveUseObserved, is ArchivedActiveHpBarDamageTickMeasured -> true
                else -> false
            } }
            if (!battle) return null
            return DirectoryCropSelection(archive.matchId).also { selected ->
                archive.articles.forEach(selected::accept)
            }.takeIf { it.crops.isNotEmpty() }
        }
    }
}

/**
 * Presentation-only access to existing evidence. Collection is owned by the
 * Directory LCD; no collection means no polling, decoding, capture or OCR work.
 * Published bitmaps are immutable in practice and are never manually recycled:
 * Compose can still own an older frame after the newest StateFlow value changes.
 */
class DirectoryCropPreviewFeed internal constructor(
    private val repositoryRoot: File,
    private val sources: () -> List<MatchArchiveSource>,
    private val decodeLocal: ((DirectoryCropReference) -> Bitmap?)? = null,
) {
    private data class LiveSelection(val timeline: RealityTimeline, val selection: DirectoryCropSelection,
        var consumed: Int = 0)
    private data class Selected(val matchId: String, val crops: Map<DirectoryCropSlot, DirectoryCropReference>,
        val archiveFile: File? = null)
    private val live = mutableMapOf<String, LiveSelection>()
    private var bootstrapped = false
    private var archiveFingerprint: List<Triple<String, Long, Long>>? = null
    private var selected: Selected? = null
    private var decodedReferences = emptyMap<DirectoryCropSlot, DirectoryCropReference>()
    private var state = DirectoryCropPreviewState()

    fun states(): Flow<DirectoryCropPreviewState> = flow {
        emit(state.copy(isLoading = state.matchId == null && !bootstrapped))
        while (true) {
            currentCoroutineContext().ensureActive()
            var candidate = currentLiveSelection()
            if (candidate == null) {
                val files = archiveFiles()
                val fingerprint = files.map { Triple(it.name, it.length(), it.lastModified()) }
                if (fingerprint != archiveFingerprint) {
                    val archived = latestArchivedSelection(files)
                    currentCoroutineContext().ensureActive()
                    // An asynchronous recovered archive can appear after the
                    // Directory first opens. Revisit only a changed file list.
                    archiveFingerprint = fingerprint
                    // A slow cold read cannot replace a newly arrived live match.
                    candidate = currentLiveSelection() ?: archived
                }
                currentCoroutineContext().ensureActive()
                bootstrapped = true
            }
            if (candidate != null) selected = candidate
            selected?.let { next ->
                val rendered = renderImages(next)
                val warm = if (next.archiveFile != null) currentLiveSelection() else null
                if (warm != null && warm.matchId != next.matchId) {
                    selected = warm
                } else {
                    decodedReferences = next.crops
                    state = rendered
                }
            }
            state = state.copy(isLoading = false)
            emit(state)
            delay(1_000L)
        }
    }.flowOn(Dispatchers.IO).conflate()

    private fun currentLiveSelection(): Selected? {
        val currentSources = sources().distinctBy { it.matchId.value }
        live.keys.retainAll(currentSources.map { it.matchId.value }.toSet())
        for (source in currentSources) {
            val id = source.matchId.value
            val entry = live[id]?.takeIf { it.timeline === source.realityTimeline }
                ?: LiveSelection(source.realityTimeline, DirectoryCropSelection(id)).also { live[id] = it }
            val tail = entry.timeline.getArticlesFrom(entry.consumed)
            tail.forEach(entry.selection::accept)
            entry.consumed += tail.size
            if (entry.selection.crops.isNotEmpty()) return Selected(id, entry.selection.crops)
        }
        return null
    }

    private fun archiveFiles(): List<File> = File(repositoryRoot, "match_archives").listFiles().orEmpty()
        .filter { it.isFile && !it.name.startsWith("active-") && it.name.endsWith(".odxmatch.zip") }
        .sortedByDescending { it.name }

    private suspend fun latestArchivedSelection(files: List<File>): Selected? {
        for (file in files) {
            currentCoroutineContext().ensureActive()
            val archive = try {
                file.inputStream().use { MatchArchivePackageReader.read(it,
                    artifactPolicy = MatchArchivePackageReader.ArtifactPolicy.REFERENCES_ONLY) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { continue }
            val selection = DirectoryCropSelection.fromBattleArchive(archive) ?: continue
            return Selected(selection.matchId, selection.crops, file)
        }
        return null
    }

    private suspend fun renderImages(next: Selected): DirectoryCropPreviewState {
        val sameMatch = state.matchId == next.matchId
        val images = linkedMapOf<DirectoryCropSlot, DirectoryCropImage>()
        val missing = linkedMapOf<DirectoryCropSlot, DirectoryCropReference>()
        for ((slot, reference) in next.crops) {
            currentCoroutineContext().ensureActive()
            val old = decodedReferences[slot]
            val bitmap = if (sameMatch && old != null && reference.samePixels(old)) {
                state.crops[slot]?.bitmap
            } else {
                val loaded = if (decodeLocal != null) decodeLocal.invoke(reference)
                    else DirectoryCropArchiveBytes.readLocal(repositoryRoot, reference.artifact)
                        ?.let { decodeArchivedCrop(it, reference) }
                loaded?.takeIf { validCropDimensions(it.width, it.height, reference) }
                    .also { if (loaded != null && it == null) loaded.recycle() }
            }
            images[slot] = DirectoryCropImage(bitmap, reference.capturedAtMillis, reference.articleId)
            // A null already attempted image is not repeatedly read every poll.
            if (bitmap == null && (!sameMatch || old == null || !reference.samePixels(old))) missing[slot] = reference
        }
        if (missing.isNotEmpty() && next.archiveFile != null) {
            val bytes = DirectoryCropArchiveBytes.read(next.archiveFile, missing.values.map { it.artifact })
            for ((slot, reference) in missing) {
                currentCoroutineContext().ensureActive()
                val bitmap = bytes[reference.artifact.relativePath]?.let { decodeArchivedCrop(it, reference) }
                images[slot] = DirectoryCropImage(bitmap, reference.capturedAtMillis, reference.articleId)
            }
        }
        currentCoroutineContext().ensureActive()
        return DirectoryCropPreviewState(next.matchId, images)
    }

    private fun decodeArchivedCrop(bytes: ByteArray, reference: DirectoryCropReference): Bitmap? {
        val dimensions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, dimensions)
        if (dimensions.outWidth <= 0 || dimensions.outHeight <= 0 ||
            dimensions.outWidth.toLong() * dimensions.outHeight > 16_777_216L) return null
        val stored = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        if (reference.artifact.relativePath.startsWith("artifacts/crops/")) {
            if (validCropDimensions(stored.width, stored.height, reference)) return stored
            stored.recycle()
            return null
        }
        val p = reference.provenance
        val b = p.bounds
        if (stored.width != p.sourceWidth || stored.height != p.sourceHeight || b.left < 0 || b.top < 0 ||
            b.right > stored.width || b.bottom > stored.height || b.right <= b.left || b.bottom <= b.top) {
            stored.recycle()
            return null
        }
        return try {
            val crop = Bitmap.createBitmap(stored, b.left, b.top, b.right - b.left, b.bottom - b.top)
            if (crop !== stored) stored.recycle()
            crop
        } catch (_: IllegalArgumentException) { stored.recycle(); null }
    }

    private fun validCropDimensions(width: Int, height: Int, reference: DirectoryCropReference): Boolean =
        width == reference.provenance.bounds.right - reference.provenance.bounds.left &&
            height == reference.provenance.bounds.bottom - reference.provenance.bounds.top

}

/** Random-access, bounded verification of only requested entries; never imports or rewrites media. */
internal object DirectoryCropArchiveBytes {
    private const val MAX_ARTIFACT_BYTES = 16L * 1024 * 1024
    private fun safeReference(reference: CropArtifactReference): Boolean =
        reference.mediaType == "image/png" && reference.byteCount in 1..MAX_ARTIFACT_BYTES &&
            reference.sha256.matches(Regex("[a-f0-9]{64}")) &&
            reference.relativePath in setOf("artifacts/crops/sha256/${reference.sha256}.png",
                "artifacts/frames/sha256/${reference.sha256}.png")

    private fun verified(bytes: ByteArray, reference: CropArtifactReference): Boolean =
        bytes.size.toLong() == reference.byteCount &&
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } == reference.sha256

    fun readLocal(root: File, reference: CropArtifactReference): ByteArray? {
        if (!safeReference(reference)) return null
        return try {
            val file = File(root, reference.relativePath)
            if (!file.canonicalFile.toPath().startsWith(root.canonicalFile.toPath()) || !file.isFile ||
                file.length() != reference.byteCount) null
            else file.readBytes().takeIf { verified(it, reference) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
    }

    fun read(file: File, references: Collection<CropArtifactReference>): Map<String, ByteArray> {
        val found = mutableMapOf<String, ByteArray>()
        try {
            ZipFile(file).use { zip ->
                references.distinctBy { it.relativePath }.forEach { reference ->
                    val path = reference.relativePath
                    if (!safeReference(reference)) return@forEach
                    val entry = zip.getEntry(path) ?: return@forEach
                    if (entry.isDirectory || (entry.size >= 0 && entry.size != reference.byteCount)) return@forEach
                    val bytes = zip.getInputStream(entry).use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var remaining = reference.byteCount.toInt() + 1
                        while (remaining > 0) {
                            val read = input.read(buffer, 0, minOf(buffer.size, remaining))
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            remaining -= read
                        }
                        output.toByteArray()
                    }
                    if (verified(bytes, reference)) {
                        found[path] = bytes
                    }
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Missing/retired/corrupt media is an unavailable preview. */ }
        return found
    }
}
