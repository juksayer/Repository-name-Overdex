package com.example.overdex.battle.collector

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.overdex.battle.custody.*
import com.example.overdex.model.observation.ObservationInput
import com.example.overdex.model.observation.SessionSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

@RunWith(AndroidJUnit4::class)
class AttackIncomingCollectorAndroidTest {

    private val testSourceId = SourceId("ANDROID_TEST_COLLECTOR")

    @Test
    fun collector_identifies_attack_incoming_from_real_bitmap() = runBlocking {
        val custody = FakeCustody()
        val input = FakeInput()
        val collector = AttackIncomingCollector(input, custody, testSourceId)

        // 1. Create a bitmap with the text "ATTACK INCOMING!" in the target region
        // Geometry: 1080x2460, Announcement Region: (0, 710, 1080, 160)
        val fullBitmap = Bitmap.createBitmap(1080, 2460, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(fullBitmap)
        val paint = Paint().apply {
            color = Color.WHITE
            textSize = 100f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        
        // Draw the target text centered in the announcement region
        val centerX = 1080f / 2
        val centerY = 710f + (160f / 2) + 20f // Baseline adjustment
        canvas.drawText("ATTACK INCOMING!", centerX, centerY, paint)

        // 2. Start collector and supply the bitmap
        collector.start()
        delay(200) // Ensure launch/supply has registered callback
        input.triggerFrame(fullBitmap)

        // 3. Wait for processing (ML Kit is async internally)
        // We poll custody for the testimony record
        var testimony: TestimonyRecord? = null
        repeat(20) { 
            testimony = custody.records.find { it is TestimonyRecord } as? TestimonyRecord
            if (testimony != null) return@repeat
            delay(100)
        }

        // 4. Verify results
        assertTrue("Testimony should be produced from real text recognition", testimony != null)
        assertEquals(RawTestimony("ATTACK_INCOMING"), testimony?.payload)
        assertEquals(1.0f, testimony?.confidence ?: 0f, 0.01f)
        
        collector.stop()
    }

    @Test
    fun collector_remains_silent_when_text_is_not_present() = runBlocking {
        val custody = FakeCustody()
        val input = FakeInput()
        val collector = AttackIncomingCollector(input, custody, testSourceId)

        // Create an empty (black) bitmap
        val emptyBitmap = Bitmap.createBitmap(1080, 2460, Bitmap.Config.ARGB_8888)

        collector.start()
        input.triggerFrame(emptyBitmap)

        // Wait a bit to ensure no testimony is produced
        delay(1000)

        val testimonyCount = custody.records.count { it is TestimonyRecord }
        assertEquals("Should produce no testimony for empty bitmap", 0, testimonyCount)
        
        collector.stop()
    }

    // --- Fakes ---

    private class FakeInput : ObservationInput {
        override val source = SessionSource.LIVE_CAPTURE
        private var callback: (suspend (Bitmap) -> Unit)? = null

        override suspend fun supply(onVisualData: suspend (Bitmap) -> Unit) {
            callback = onVisualData
        }

        suspend fun triggerFrame(bitmap: Bitmap) {
            android.util.Log.d("FakeInput", "triggerFrame: callback is ${if (callback == null) "NULL" else "NOT NULL"}")
            callback?.invoke(bitmap)
        }
    }

    private class FakeCustody : TestimonyCustody {
        val records = CopyOnWriteArrayList<CustodyRecord>()
        private val sequence = AtomicLong(0)

        override fun submitAvailability(sourceId: SourceId, available: Boolean, timestamp: Long): SourceAvailabilityRecord {
            val r = SourceAvailabilityRecord(sequence.getAndIncrement(), timestamp, sourceId, available)
            records.add(r)
            return r
        }

        override fun submitInputAvailability(sourceId: SourceId, available: Boolean, timestamp: Long): SourceInputRecord {
            val r = SourceInputRecord(sequence.getAndIncrement(), timestamp, sourceId, available)
            records.add(r)
            return r
        }

        override fun submitTestimony(sourceId: SourceId, payload: TestimonyPayload, timestamp: Long, confidence: Float, evidenceReferences: List<String>): TestimonyRecord {
            val r = TestimonyRecord(sequence.getAndIncrement(), timestamp, sourceId, payload, confidence, evidenceReferences)
            records.add(r)
            return r
        }

        override fun getRecords(): List<CustodyRecord> = records.toList()
    }
}
