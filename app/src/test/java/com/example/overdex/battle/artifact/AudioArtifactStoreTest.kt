package com.example.overdex.battle.artifact

import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioArtifactStoreTest {
    @Test
    fun `writes a content-addressed portable wav reference only after bytes verify`() {
        val root = Files.createTempDirectory("overdex-audio-").toFile()
        try {
            val bytes = byteArrayOf(82, 73, 70, 70, 1, 2, 3, 4) // Minimal RIFF-like test bytes.
            val store = FileAudioArtifactStore(root)

            val first = store.preserveWav(bytes)!!
            val second = store.preserveWav(bytes)!!

            assertEquals(first, second)
            assertEquals("audio/wav", first.mediaType)
            assertTrue(first.relativePath.matches(Regex("artifacts/audio/sha256/[a-f0-9]{64}\\.wav")))
            assertArrayEquals(bytes, java.io.File(root, first.relativePath).readBytes())
        } finally {
            root.deleteRecursively()
        }
    }
}
