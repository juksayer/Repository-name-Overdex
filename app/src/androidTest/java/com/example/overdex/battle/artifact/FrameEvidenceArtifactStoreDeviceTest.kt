package com.example.overdex.battle.artifact

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.overdex.battle.observation.BattleCropBounds
import com.example.overdex.battle.observation.BattleCropProvenance
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FrameEvidenceArtifactStoreDeviceTest {
    @Test
    fun cropsFromOnePublishedFrameShareOneCoordinatePreservingArtifact() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = File(context.cacheDir, "frame-evidence-${System.nanoTime()}")
        val store = FileCropArtifactStore(root)
        val left = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val right = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val leftProvenance = BattleCropProvenance("left", 4, 2, BattleCropBounds(0, 0, 2, 2))
        val rightProvenance = BattleCropProvenance("right", 4, 2, BattleCropBounds(2, 0, 4, 2))
        try {
            val leftReference = async { store.preserveFrameCrop(42L, left, leftProvenance) }
            val rightReference = async { store.preserveFrameCrop(42L, right, rightProvenance) }
            val first = leftReference.await()!!
            val second = rightReference.await()!!

            assertEquals(first, second)
            assertTrue(first.relativePath.startsWith("artifacts/frames/sha256/"))
            val loadedLeft = store.loadVerifiedPng(first, leftProvenance)!!
            val loadedRight = store.loadVerifiedPng(first, rightProvenance)!!
            try {
                assertEquals(Color.RED, loadedLeft.getPixel(0, 0))
                assertEquals(Color.BLUE, loadedRight.getPixel(0, 0))
            } finally {
                loadedLeft.recycle()
                loadedRight.recycle()
            }
        } finally {
            left.recycle()
            right.recycle()
            root.deleteRecursively()
        }
    }

    @Test
    fun sequentialWitnessesReferenceTheSameCompletePublishedFrame() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = File(context.cacheDir, "complete-frame-evidence-${System.nanoTime()}")
        val store = FileCropArtifactStore(root)
        val source = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until height) for (x in 0 until width) {
                setPixel(x, y, if (x < 2) Color.RED else Color.BLUE)
            }
        }
        val left = Bitmap.createBitmap(source, 0, 0, 2, 2)
        val right = Bitmap.createBitmap(source, 2, 0, 2, 2)
        val leftProvenance = BattleCropProvenance("left", 4, 2, BattleCropBounds(0, 0, 2, 2))
        val rightProvenance = BattleCropProvenance("right", 4, 2, BattleCropBounds(2, 0, 4, 2))
        try {
            val first = store.preserveFrameCrop(43L, left, leftProvenance, source)!!
            val second = store.preserveFrameCrop(43L, right, rightProvenance, source)!!

            assertEquals(first, second)
            val loadedRight = store.loadVerifiedPng(second, rightProvenance)!!
            try {
                assertEquals(Color.BLUE, loadedRight.getPixel(0, 0))
            } finally {
                loadedRight.recycle()
            }
        } finally {
            source.recycle()
            left.recycle()
            right.recycle()
            root.deleteRecursively()
        }
    }
}
