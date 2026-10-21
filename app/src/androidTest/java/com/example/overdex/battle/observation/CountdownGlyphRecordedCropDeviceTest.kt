package com.example.overdex.battle.observation

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

/** Original, unmodified crops from the MOONDROP recording of 2026-10-08 23:02. */
class CountdownGlyphRecordedCropDeviceTest {
    @Test
    fun recognizesRecordedCountdownWithoutWaitingForOcr() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        CountdownGlyphMatcher.initialize(instrumentation.targetContext)
        listOf("3" to "3.png", "1" to "1.png", "GO" to "go.png").forEach { (glyph, file) ->
            val bitmap = instrumentation.context.assets.open("countdown/$file").use(BitmapFactory::decodeStream)
                ?: error("Cannot decode $file")
            try {
                val started = System.nanoTime()
                val result = CountdownGlyphMatcher.match(bitmap)
                val elapsedMs = (System.nanoTime() - started) / 1_000_000L
                Log.i("CountdownRecordedTest", "glyph=$glyph result=${result.candidate} elapsedMs=$elapsedMs")
                assertEquals(glyph, result.candidate)
            } finally {
                bitmap.recycle()
            }
        }
    }
}
