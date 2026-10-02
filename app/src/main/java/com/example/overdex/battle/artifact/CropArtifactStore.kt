package com.example.overdex.battle.artifact

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import com.example.overdex.battle.observation.BattleCropBounds
import com.example.overdex.battle.observation.BattleCropProvenance
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class CropArtifactReference(
    val relativePath: String,
    val sha256: String,
    val byteCount: Long,
    val mediaType: String = "image/png"
)

/**
 * Durable storage for crop bytes. A null result means the crop was not
 * preserved and must not be admitted as testimony.
 */
interface CropArtifactStore {
    fun preservePng(bitmap: Bitmap): CropArtifactReference?

    /**
     * Coalesces every crop requested from one published frame into one immutable
     * coordinate-preserving PNG. Each testimony keeps its own crop provenance
     * while sharing the frame artifact.
     */
    suspend fun preserveFrameCrop(
        frameMonotonicTimeNanos: Long,
        bitmap: Bitmap,
        provenance: BattleCropProvenance,
        sourceFrame: Bitmap? = null
    ): CropArtifactReference? = preservePng(sourceFrame ?: bitmap)
}

/**
 * Content-addressed crop store rooted at an app or archive repository root.
 * References are always portable relative paths beneath that root.
 */
class FileCropArtifactStore(private val repositoryRoot: File) : CropArtifactStore {
    private val frameScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val frameLock = Any()
    private val pendingFrames = linkedMapOf<Long, PendingFrame>()
    private val pendingSourceFrameWrites = mutableMapOf<Long, CompletableDeferred<CropArtifactReference?>>()
    private val finalizedFrames = object : LinkedHashMap<Long, FinalizedFrame>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, FinalizedFrame>?): Boolean = size > 128
    }

    override fun preservePng(bitmap: Bitmap): CropArtifactReference? {
        val bytes = ByteArrayOutputStream().use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) return null
            output.toByteArray()
        }
        return preserveEncodedPng(bytes)
    }

    fun preserveEncodedPng(bytes: ByteArray): CropArtifactReference? {
        val hash = sha256(bytes)
        val relativePath = "artifacts/crops/sha256/$hash.png"
        return preserveEncoded(bytes, relativePath, hash)
    }

    fun preserveEncodedFramePng(bytes: ByteArray): CropArtifactReference? {
        val hash = sha256(bytes)
        val relativePath = "artifacts/frames/sha256/$hash.png"
        return preserveEncoded(bytes, relativePath, hash)
    }

    override suspend fun preserveFrameCrop(
        frameMonotonicTimeNanos: Long,
        bitmap: Bitmap,
        provenance: BattleCropProvenance,
        sourceFrame: Bitmap?
    ): CropArtifactReference? {
        if (sourceFrame != null) {
            return preserveSourceFrame(frameMonotonicTimeNanos, sourceFrame, provenance)
        }
        val deferred = synchronized(frameLock) {
            finalizedFrames[frameMonotonicTimeNanos]?.let { finalized ->
                return if (finalized.coveredBounds.any { it.contains(provenance.bounds) }) finalized.reference else null
            }
            val pending = pendingFrames.getOrPut(frameMonotonicTimeNanos) {
                PendingFrame(
                    sourceWidth = provenance.sourceWidth,
                    sourceHeight = provenance.sourceHeight,
                    result = CompletableDeferred()
                ).also { created ->
                    frameScope.launch {
                        delay(FRAME_COALESCE_MILLIS)
                        finalizeFrame(frameMonotonicTimeNanos, created)
                    }
                }
            }
            if (pending.sourceWidth != provenance.sourceWidth || pending.sourceHeight != provenance.sourceHeight) {
                return null
            }
            if (!pending.accepting) return null
            pending.crops += PendingCrop(bitmap, provenance)
            pending.result
        }
        return deferred.await()
    }

    /**
     * Writes the published source frame once. Every witness for that frame can
     * then cite the same immutable PNG plus its own crop coordinates, even when
     * the witness reaches the frame after another crop worker.
     */
    private suspend fun preserveSourceFrame(
        frameMonotonicTimeNanos: Long,
        sourceFrame: Bitmap,
        provenance: BattleCropProvenance
    ): CropArtifactReference? {
        if (sourceFrame.width != provenance.sourceWidth || sourceFrame.height != provenance.sourceHeight) return null
        val fullBounds = BattleCropBounds(0, 0, sourceFrame.width, sourceFrame.height)
        var ownsWrite = false
        val deferred = synchronized(frameLock) {
            finalizedFrames[frameMonotonicTimeNanos]
                ?.takeIf { finalized -> finalized.coveredBounds.any { it.contains(fullBounds) } }
                ?.let { return it.reference }
            pendingSourceFrameWrites[frameMonotonicTimeNanos]
                ?: CompletableDeferred<CropArtifactReference?>().also {
                    pendingSourceFrameWrites[frameMonotonicTimeNanos] = it
                    ownsWrite = true
                }
        }
        if (ownsWrite) {
            val reference = try {
                val bytes = ByteArrayOutputStream().use { output ->
                    if (!sourceFrame.compress(Bitmap.CompressFormat.PNG, 100, output)) return@use null
                    output.toByteArray()
                }
                bytes?.let(::preserveEncodedFramePng)
            } catch (_: Exception) {
                null
            }
            synchronized(frameLock) {
                pendingSourceFrameWrites.remove(frameMonotonicTimeNanos)
                if (reference != null) {
                    finalizedFrames[frameMonotonicTimeNanos] = FinalizedFrame(
                        reference = reference,
                        coveredBounds = listOf(fullBounds)
                    )
                }
            }
            deferred.complete(reference)
        }
        return deferred.await()
    }

    private fun finalizeFrame(frameMonotonicTimeNanos: Long, pending: PendingFrame) {
        val crops = synchronized(frameLock) {
            if (pendingFrames[frameMonotonicTimeNanos] !== pending) return
            pending.accepting = false
            pending.crops.toList()
        }
        val reference = try {
            val atlas = Bitmap.createBitmap(pending.sourceWidth, pending.sourceHeight, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(atlas)
                crops.forEach { crop ->
                    canvas.drawBitmap(
                        crop.bitmap,
                        crop.provenance.bounds.left.toFloat(),
                        crop.provenance.bounds.top.toFloat(),
                        null
                    )
                }
                val bytes = ByteArrayOutputStream().use { output ->
                    if (!atlas.compress(Bitmap.CompressFormat.PNG, 100, output)) return@use null
                    output.toByteArray()
                }
                bytes?.let(::preserveEncodedFramePng)
            } finally {
                atlas.recycle()
            }
        } catch (_: Exception) {
            null
        }
        synchronized(frameLock) {
            pendingFrames.remove(frameMonotonicTimeNanos)
            if (reference != null) {
                finalizedFrames[frameMonotonicTimeNanos] = FinalizedFrame(
                    reference = reference,
                    coveredBounds = crops.map { it.provenance.bounds }
                )
            }
        }
        pending.result.complete(reference)
    }

    private fun preserveEncoded(bytes: ByteArray, relativePath: String, hash: String): CropArtifactReference? {
        val target = File(repositoryRoot, relativePath)
        if (!isWithinRoot(target)) return null

        try {
            target.parentFile?.mkdirs() ?: return null
            if (target.exists()) {
                return referenceIfVerified(target, relativePath, hash, bytes.size.toLong())
            }

            val temporary = File(target.parentFile, ".${hash}.${System.nanoTime()}.tmp")
            try {
                FileOutputStream(temporary).use { output ->
                    output.write(bytes)
                    output.fd.sync()
                }
                if (!temporary.renameTo(target) && !target.exists()) return null
            } finally {
                if (temporary.exists()) temporary.delete()
            }
            return referenceIfVerified(target, relativePath, hash, bytes.size.toLong())
        } catch (_: Exception) {
            return null
        }
    }

    fun loadVerifiedPng(reference: CropArtifactReference): Bitmap? {
        if (!isSupportedVisualReference(reference)) return null
        return try {
            val file = File(repositoryRoot, reference.relativePath)
            if (!isWithinRoot(file) || !file.isFile || file.length() != reference.byteCount || sha256(file.readBytes()) != reference.sha256) return null
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (_: Exception) {
            null
        }
    }

    fun loadVerifiedPng(
        reference: CropArtifactReference,
        provenance: BattleCropProvenance
    ): Bitmap? {
        val stored = loadVerifiedPng(reference) ?: return null
        if (reference.relativePath.startsWith("artifacts/crops/")) return stored
        return try {
            if (stored.width != provenance.sourceWidth || stored.height != provenance.sourceHeight) return null
            val bounds = provenance.bounds
            if (bounds.left < 0 || bounds.top < 0 || bounds.right > stored.width || bounds.bottom > stored.height ||
                bounds.right - bounds.left <= 0 || bounds.bottom - bounds.top <= 0
            ) return null
            Bitmap.createBitmap(stored, bounds.left, bounds.top, bounds.right - bounds.left, bounds.bottom - bounds.top)
        } finally {
            stored.recycle()
        }
    }

    private fun referenceIfVerified(
        file: File,
        relativePath: String,
        expectedHash: String,
        expectedByteCount: Long
    ): CropArtifactReference? {
        if (!file.isFile || file.length() != expectedByteCount) return null
        if (sha256(file.readBytes()) != expectedHash) return null
        return CropArtifactReference(relativePath, expectedHash, expectedByteCount)
    }

    private fun isWithinRoot(file: File): Boolean =
        file.canonicalFile.path.startsWith(repositoryRoot.canonicalFile.path + File.separator)

    private fun isSupportedVisualReference(reference: CropArtifactReference): Boolean =
        reference.relativePath == "artifacts/crops/sha256/${reference.sha256}.png" ||
            reference.relativePath == "artifacts/frames/sha256/${reference.sha256}.png"

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private data class PendingFrame(
        val sourceWidth: Int,
        val sourceHeight: Int,
        val result: CompletableDeferred<CropArtifactReference?>,
        val crops: MutableList<PendingCrop> = mutableListOf(),
        var accepting: Boolean = true
    )

    private data class PendingCrop(val bitmap: Bitmap, val provenance: BattleCropProvenance)
    private data class FinalizedFrame(
        val reference: CropArtifactReference,
        val coveredBounds: List<BattleCropBounds>
    )

    private fun BattleCropBounds.contains(other: BattleCropBounds): Boolean =
        left <= other.left && top <= other.top && right >= other.right && bottom >= other.bottom

    private companion object {
        const val FRAME_COALESCE_MILLIS = 35L
    }
}
