package com.example.overdex.battle.artifact

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

data class AudioArtifactReference(
    val relativePath: String,
    val sha256: String,
    val byteCount: Long,
    val mediaType: String = "audio/wav"
)

/**
 * Durable storage for a captured WAV snippet. Testimony may cite an audio artifact only after
 * this store has written and verified its bytes.
 */
interface AudioArtifactStore {
    fun preserveWav(bytes: ByteArray): AudioArtifactReference?
}

/** Content-addressed, portable audio artifact repository. */
class FileAudioArtifactStore(private val repositoryRoot: File) : AudioArtifactStore {
    override fun preserveWav(bytes: ByteArray): AudioArtifactReference? {
        val hash = sha256(bytes)
        val relativePath = "artifacts/audio/sha256/$hash.wav"
        val target = File(repositoryRoot, relativePath)
        if (!isWithinRoot(target)) return null

        return try {
            target.parentFile?.mkdirs() ?: return null
            if (!target.exists()) {
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
            }
            if (!target.isFile || target.length() != bytes.size.toLong() || sha256(target.readBytes()) != hash) {
                return null
            }
            AudioArtifactReference(relativePath, hash, bytes.size.toLong())
        } catch (_: Exception) {
            null
        }
    }

    private fun isWithinRoot(file: File): Boolean =
        file.canonicalFile.path.startsWith(repositoryRoot.canonicalFile.path + File.separator)

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
