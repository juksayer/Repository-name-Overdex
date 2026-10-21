package com.example.overdex.data

import com.example.overdex.battle.artifact.CropArtifactReference
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BattleCalibrationReferenceStoreTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun `keeps an exact independent copy of the most recent full frame`() {
        val root = folder.newFolder()
        val store = BattleCalibrationReferenceStore(root)
        val first = frame(root, 'a', byteArrayOf(1, 2, 3))
        assertTrue(store.remember(first))
        File(root, first.relativePath).delete()
        assertArrayEquals(byteArrayOf(1, 2, 3), store.latest()!!.readBytes())
        val second = frame(root, 'b', byteArrayOf(4, 5))
        assertTrue(store.remember(second))
        assertArrayEquals(byteArrayOf(4, 5), store.latest()!!.readBytes())
        assertEquals(1, store.latest()!!.parentFile!!.listFiles()!!.size)
    }

    @Test
    fun `crop and incomplete references cannot replace the last good full frame`() {
        val root = folder.newFolder()
        val store = BattleCalibrationReferenceStore(root)
        val good = frame(root, 'a', byteArrayOf(1, 2, 3))
        assertTrue(store.remember(good))
        assertFalse(store.remember(good.copy(relativePath = good.relativePath.replace("frames", "crops"))))
        assertFalse(store.remember(good.copy(byteCount = 4)))
        assertArrayEquals(byteArrayOf(1, 2, 3), store.latest()!!.readBytes())
    }

    private fun frame(root: File, hash: Char, bytes: ByteArray): CropArtifactReference {
        val sha = hash.toString().repeat(64)
        val path = "artifacts/frames/sha256/$sha.png"
        File(root, path).apply { parentFile!!.mkdirs(); writeBytes(bytes) }
        return CropArtifactReference(path, sha, bytes.size.toLong())
    }
}
