package com.example.overdex.battle.artifact

import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CropArtifactStoreTest {
    @Test
    fun `writes content-addressed relative artifact and verifies deduplicated bytes`() {
        val root = Files.createTempDirectory("overdex-crops-").toFile()
        try {
            val bytes = byteArrayOf(1, 2, 3, 4, 5)
            val store = FileCropArtifactStore(root)

            val first = store.preserveEncodedPng(bytes)!!
            val second = store.preserveEncodedPng(bytes)!!

            assertEquals(first, second)
            assertTrue(first.relativePath.matches(Regex("artifacts/crops/sha256/[a-f0-9]{64}\\.png")))
            assertEquals(first.sha256, first.relativePath.removePrefix("artifacts/crops/sha256/").removeSuffix(".png"))
            assertArrayEquals(bytes, java.io.File(root, first.relativePath).readBytes())
        } finally {
            root.deleteRecursively()
        }
    }
}
