package com.example.overdex.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.example.overdex.ui.screens.observatory.ReplaySpriteVisibleBounds
import com.example.overdex.ui.screens.observatory.replaySpriteVisibleBounds
import com.example.overdex.ui.screens.observatory.replaySpriteVisibleTopFraction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Android pixels exercise real alpha and Fit letterboxing without relying on a sprite palette. */
class ReplaySpriteAnchorDeviceTest {
    @Test fun paddedSquareUsesAlphaIncludingFaintEdgesAndOpaqueBlack() {
        val source = art(20, 20, Rect(4, 2, 14, 16))
        try {
            source.setPixel(3, 1, Color.argb(1, 255, 0, 255))
            source.setPixel(14, 17, Color.argb(1, 255, 0, 255))
            val bounds = replaySpriteVisibleBounds(source)
            assertBounds(ReplaySpriteVisibleBounds(.15f, .05f, .75f, .9f), bounds)
            assertEquals(bounds.top, replaySpriteVisibleTopFraction(source), TOLERANCE)
            assertEquals("Geometry reading leaves source pixels intact", Color.BLACK, source.getPixel(4, 2))
            assertFalse(source.isRecycled)
        } finally {
            source.recycle()
        }
    }

    @Test fun tallArtIncludesHorizontalFitLetterboxing() {
        val source = art(10, 20, Rect(2, 4, 8, 16))
        try {
            assertBounds(ReplaySpriteVisibleBounds(.35f, .2f, .65f, .8f), replaySpriteVisibleBounds(source))
        } finally {
            source.recycle()
        }
    }

    @Test fun wideArtIncludesVerticalFitLetterboxing() {
        val source = art(20, 10, Rect(4, 2, 16, 8))
        try {
            assertBounds(ReplaySpriteVisibleBounds(.2f, .35f, .8f, .65f), replaySpriteVisibleBounds(source))
        } finally {
            source.recycle()
        }
    }

    @Test fun transparentAndRecycledArtUseTheWholeNodeFallback() {
        val source = Bitmap.createBitmap(10, 20, Bitmap.Config.ARGB_8888)
        val fullNode = ReplaySpriteVisibleBounds(0f, 0f, 1f, 1f)
        try {
            source.eraseColor(Color.TRANSPARENT)
            assertBounds(fullNode, replaySpriteVisibleBounds(source))
            assertEquals(0f, replaySpriteVisibleTopFraction(source), 0f)
        } finally {
            source.recycle()
        }
        assertBounds(fullNode, replaySpriteVisibleBounds(source))
        assertEquals(0f, replaySpriteVisibleTopFraction(source), 0f)
    }

    @Test fun visibleHeadAndFacingEdgeMapToActualPixelsThroughFocusAndMirroring() {
        val source = art(10, 20, Rect(2, 4, 8, 16))
        try {
            val bounds = replaySpriteVisibleBounds(source)
            val resting = render(source, bounds, focusScale = 1f)
            val enlarged = render(source, bounds, focusScale = 1.5f)
            val mirrored = render(source, bounds, focusScale = -1.5f)
            try {
                val restingPixels = opaquePixelBounds(resting)
                val enlargedPixels = opaquePixelBounds(enlarged)
                val mirroredPixels = opaquePixelBounds(mirrored)
                assertEquals("The visible head stays at the same pixel while the body enlarges",
                    restingPixels.top, enlargedPixels.top)
                assertEquals(restingPixels.top, mirroredPixels.top)
                val expectedRight = NODE_LEFT + NODE_SIZE / 2f +
                    (bounds.right - .5f) * NODE_SIZE * 1.5f
                val expectedMirroredFront = NODE_LEFT + NODE_SIZE / 2f -
                    (bounds.right - .5f) * NODE_SIZE * 1.5f
                assertEquals("The player's facing edge follows its visible art, not its square node",
                    expectedRight, enlargedPixels.right.toFloat(), 1f)
                assertEquals("A horizontal flip maps that front edge to the opponent's left edge",
                    expectedMirroredFront, mirroredPixels.left.toFloat(), 1f)
            } finally {
                resting.recycle()
                enlarged.recycle()
                mirrored.recycle()
            }
        } finally {
            source.recycle()
        }
    }

    private fun art(width: Int, height: Int, visible: Rect): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            for (y in visible.top until visible.bottom) {
                for (x in visible.left until visible.right) setPixel(x, y, Color.BLACK)
            }
        }

    private fun render(source: Bitmap, bounds: ReplaySpriteVisibleBounds, focusScale: Float): Bitmap =
        Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
            Canvas(this).apply {
                translate(NODE_LEFT, NODE_TOP)
                scale(focusScale, kotlin.math.abs(focusScale), NODE_SIZE / 2f, NODE_SIZE * bounds.top)
                val fittedWidth = source.width * NODE_SIZE / maxOf(source.width, source.height)
                val fittedHeight = source.height * NODE_SIZE / maxOf(source.width, source.height)
                val left = (NODE_SIZE - fittedWidth) / 2f
                val top = (NODE_SIZE - fittedHeight) / 2f
                // This helper's contract is the original nonzero-alpha pixel edges. Modern
                // Paint() enables bilinear filtering by default; that spreads partial alpha
                // half a source pixel beyond those edges, and the fringe grows when scaled.
                // Keep this geometry fixture nearest-neighbor so its exact-pixel assertions
                // measure the source bounds rather than a renderer's interpolation footprint.
                val paint = Paint().apply {
                    isFilterBitmap = false
                    isAntiAlias = false
                }
                drawBitmap(source, null, RectF(left, top, left + fittedWidth, top + fittedHeight), paint)
            }
        }

    private fun opaquePixelBounds(bitmap: Bitmap): Rect {
        var left = bitmap.width
        var top = bitmap.height
        var right = 0
        var bottom = 0
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                if (Color.alpha(bitmap.getPixel(x, y)) == 0) continue
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x + 1)
                bottom = maxOf(bottom, y + 1)
            }
        }
        return Rect(left, top, right, bottom)
    }

    private fun assertBounds(expected: ReplaySpriteVisibleBounds, actual: ReplaySpriteVisibleBounds) {
        assertEquals("left", expected.left, actual.left, TOLERANCE)
        assertEquals("top", expected.top, actual.top, TOLERANCE)
        assertEquals("right", expected.right, actual.right, TOLERANCE)
        assertEquals("bottom", expected.bottom, actual.bottom, TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = .00001f
        const val NODE_SIZE = 200f
        const val NODE_LEFT = 100f
        const val NODE_TOP = 80f
    }
}
