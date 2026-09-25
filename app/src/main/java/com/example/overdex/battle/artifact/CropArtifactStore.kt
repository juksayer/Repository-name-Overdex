package com.example.overdex.battle.artifact

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

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
}

/**
 * Content-addressed crop store rooted at an app or archive repository root.
 * References are always portable relative paths beneath that root.
 */
class FileCropArtifactStore(private val repositoryRoot: File) : CropArtifactStore {
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
        if (reference.relativePath != "artifacts/crops/sha256/${reference.sha256}.png") return null
        return try {
            val file = File(repositoryRoot, reference.relativePath)
            if (!isWithinRoot(file) || !file.isFile || file.length() != reference.byteCount || sha256(file.readBytes()) != reference.sha256) return null
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (_: Exception) {
            null
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

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
