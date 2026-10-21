package com.example.overdex.battle.observation

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.example.overdex.data.observation.OcrField
import com.example.overdex.data.observation.OcrHeightProfile
import com.example.overdex.data.observation.OcrSourceContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AnnouncementRecordedCropDeviceTest {
    @Test fun latestRecordedRockTombPixelsStillResolveInTheTransientLane() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = BitmapFactory.decodeFile(File(context.cacheDir, "recorded-rock-tomb.png").path)
        try {
            val readings = AnnouncementRecognizer.recognizeEvidence(bitmap, OcrSourceContext(672),
                OcrHeightProfile(OcrField.ANNOUNCEMENT))
            assertTrue(readings.any { it.acceptedText.contains("Camerupt", true) && it.acceptedText.contains("Rock Tomb", true) })
        } finally { bitmap.recycle() }
    }
}
