package com.example.overdex.battle.observation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.model.AnchorRegion
import com.example.overdex.ui.components.BattleHudBadgeShape
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RetainedBadgeImageDeviceTest {
    @Test fun curvedOpeningKeepsNativePixelsButNeverCopiesHudOrOutsideCorners() {
        // Deliberately oversize the assigned crop below the joint. Scenery and
        // our own opaque artwork must not survive in the retained image.
        val source = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
        val badge = AnchorRegion(100f / 600, 50f / 600, 415f / 600, 100f / 600)
        val hud = AnchorRegion(100f / 600, 120f / 600, 415f / 600, 300f / 600)
        val canvas = Canvas(source)
        canvas.drawColor(Color.GREEN)
        canvas.drawRoundRect(RectF(100f, 50f, 515f, 150f), 27f, 27f, Paint().apply { color = Color.WHITE })
        val path = (BattleHudBadgeShape.createOutline(Size(415f, 300f), LayoutDirection.Ltr,
            Density(1f)) as Outline.Generic).path.asAndroidPath()
        canvas.save(); canvas.translate(100f, 120f)
        canvas.drawPath(path, Paint().apply { color = Color.MAGENTA }); canvas.restore()
        val bounds = requireNotNull(BattleCropResolver.resolveRect(badge, 600, 600))
        val retained = requireNotNull(retainedBadgeImage(source, badge, hud))
        try {
            assertEquals("Keep the badge below the window's rectangular top", Color.WHITE,
                retained.getPixel(300 - bounds.left, 140 - bounds.top))
            assertEquals("HUD body below the curved joint is transparent", 0,
                Color.alpha(retained.getPixel(300 - bounds.left, 149 - bounds.top)))
            assertEquals("Outside the rounded native corner is transparent", 0, Color.alpha(retained.getPixel(0, 0)))
            val pixels = IntArray(retained.width * retained.height)
            retained.getPixels(pixels, 0, retained.width, 0, 0, retained.width, retained.height)
            assertFalse("Never retain HUD ink", pixels.any { it == Color.MAGENTA })
            assertFalse("Never retain outside-corner scenery", pixels.any { it == Color.GREEN })
        } finally { retained.recycle(); source.recycle() }
    }

    @Test fun recordedBadgeRetainsCompleteBallRimsInsideTheHudOpening() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val source = Bitmap.createBitmap(672, 1493, Bitmap.Config.ARGB_8888)
        val band = instrumentation.context.assets.open("badge-retention-20261024/two.png")
            .use(BitmapFactory::decodeStream)!!
        Canvas(source).drawBitmap(band, 0f, 80f, null)
        val badge = AnchorRegion(399f / 672, 86f / 1493, 258f / 672, 82f / 1493)
        val hud = AnchorRegion(400.08887f / 672, 144.323334f / 1493, 258.22223f / 672, .3f)
        val bounds = requireNotNull(BattleCropResolver.resolveRect(badge, source.width, source.height))
        val retained = requireNotNull(retainedBadgeImage(source, badge, hud))
        try {
            for (x in listOf(563, 599, 636)) {
                assertEquals("Keep the complete native ball at $x", source.getPixel(x, 150),
                    retained.getPixel(x - bounds.left, 150 - bounds.top))
            }
            assertEquals("No sprite/card/background strip below the joint", 0,
                Color.alpha(retained.getPixel(540 - bounds.left, 165 - bounds.top)))
            assertEquals("Rounded display coordinates cannot leave a thin HUD edge", 0,
                Color.alpha(retained.getPixel(400 - bounds.left, 160 - bounds.top)))
            File(instrumentation.targetContext.cacheDir, "retained-curved-badge.png").outputStream().use {
                retained.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally { retained.recycle(); band.recycle(); source.recycle() }
    }
}
