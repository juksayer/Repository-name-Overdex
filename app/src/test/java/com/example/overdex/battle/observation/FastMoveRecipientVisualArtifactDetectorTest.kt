package com.example.overdex.battle.observation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FastMoveRecipientVisualArtifactDetectorTest {
    @Test fun `transient color change produces one spatial color measurement`() {
        val detector = FastMoveRecipientVisualArtifactDetector()
        val neutral = IntArray(24 * 24) { rgb(80, 80, 80) }
        val artifact = neutral.copyOf().also { pixels ->
            for (y in 8 until 16) for (x in 12 until 20) pixels[y * 24 + x] = rgb(235, 55, 35)
        }

        assertNull(detector.accept(FastMoveRecipientVisualSample(neutral), 1_000_000_000L))
        assertNull(detector.accept(FastMoveRecipientVisualSample(artifact), 1_200_000_000L))
        val measured = detector.accept(FastMoveRecipientVisualSample(neutral), 1_400_000_000L)

        assertNotNull(measured)
        requireNotNull(measured)
        assertEquals(64, measured.changedSampleCount)
        assertTrue(measured.meanRed > measured.meanGreen)
        assertTrue(measured.meanRed > measured.meanBlue)
        assertTrue(measured.centroidX > 0.5f)
        assertTrue(measured.changedPixelFraction > 0.08f)
        assertEquals(1_200_000_000L, measured.observedAtMonotonicTimeNanos)
    }

    @Test fun `stable artifact frame does not create duplicate testimony`() {
        val detector = FastMoveRecipientVisualArtifactDetector()
        val neutral = IntArray(24 * 24) { rgb(70, 70, 70) }
        val artifact = neutral.copyOf().also { pixels ->
            for (index in 0 until 80) pixels[index] = rgb(30, 170, 240)
        }
        detector.accept(FastMoveRecipientVisualSample(neutral), 1_000_000_000L)
        assertNull(detector.accept(FastMoveRecipientVisualSample(artifact), 1_200_000_000L))
        assertNull(detector.accept(FastMoveRecipientVisualSample(artifact), 1_300_000_000L))
        assertNotNull(detector.accept(FastMoveRecipientVisualSample(neutral), 1_400_000_000L))
        assertNull(detector.accept(FastMoveRecipientVisualSample(neutral), 1_500_000_000L))
    }

    private fun rgb(red: Int, green: Int, blue: Int): Int =
        (0xff shl 24) or (red shl 16) or (green shl 8) or blue

    @Test fun `a camera gap cannot finish an old transient`() {
        val detector = FastMoveRecipientVisualArtifactDetector()
        val neutral = FastMoveRecipientVisualSample(IntArray(576) { rgb(80, 80, 80) })
        val peak = FastMoveRecipientVisualSample(IntArray(576) { rgb(220, 20, 20) })
        detector.accept(neutral, 1_000_000_000L)
        detector.accept(peak, 1_200_000_000L)
        assertNull(detector.accept(neutral, 2_000_000_000L))
        assertNull(detector.accept(neutral, 2_100_000_000L))
    }

    @Test fun `a relocated sampling region cannot finish an old transient`() {
        val detector = FastMoveRecipientVisualArtifactDetector()
        val bounds = BattleCropBounds(20, 200, 400, 700)
        val neutral = FastMoveRecipientVisualSample(IntArray(576) { rgb(80, 80, 80) }, bounds)
        val peak = FastMoveRecipientVisualSample(IntArray(576) { rgb(220, 20, 20) }, bounds)
        detector.accept(neutral, 1_000_000_000L)
        detector.accept(peak, 1_200_000_000L)
        assertNull(detector.accept(neutral.copy(bounds = BattleCropBounds(200, 400, 580, 900)), 1_300_000_000L))
    }

    @Test fun `duplicate and reordered frames do not release a current peak`() {
        val detector = FastMoveRecipientVisualArtifactDetector()
        val neutral = FastMoveRecipientVisualSample(IntArray(576) { rgb(80, 80, 80) })
        val peak = FastMoveRecipientVisualSample(IntArray(576) { rgb(220, 20, 20) })
        detector.accept(neutral, 1_000_000_000L)
        detector.accept(peak, 1_200_000_000L)
        assertNull(detector.accept(neutral, 1_100_000_000L))
        assertNull(detector.accept(neutral, 1_200_000_000L))
        assertEquals(1_200_000_000L, detector.accept(neutral, 1_300_000_000L)!!.observedAtMonotonicTimeNanos)
    }

    @Test fun `a long scene change cannot become an impact when it finally recedes`() {
        val detector = FastMoveRecipientVisualArtifactDetector()
        val neutral = FastMoveRecipientVisualSample(IntArray(576) { rgb(80, 80, 80) })
        val changed = FastMoveRecipientVisualSample(IntArray(576) { rgb(220, 20, 20) })
        detector.accept(neutral, 1_000_000_000L)
        for (ms in 1200L..2400L step 400L) assertNull(detector.accept(changed, ms * 1_000_000L))
        assertNull(detector.accept(neutral, 2_700_000_000L))
    }
}
