package com.example.overdex.battle.audio

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AudioPreparationDeviceTest {
    @Test fun cryIndexIsSharedAcrossMatchesAndCapturedAudioDecodesWithoutChangingSamples() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val start = System.nanoTime()
        val first = PreparedCryReferenceIndex.get(instrumentation.targetContext)
        val preparedAt = System.nanoTime()
        val second = PreparedCryReferenceIndex.get(instrumentation.targetContext)
        val reusedAt = System.nanoTime()
        assertSame(first, second)
        val expected = CryReferenceCatalog.load(instrumentation.targetContext).references.map { it.sha256 }.toSet()
        assertTrue(expected.isNotEmpty())
        assertEquals(expected, first.map { it.reference.sha256 }.toSet())
        val pcm = ByteArray(115200) { (it * 31).toByte() }
        val wav = Pcm16WavEncoder.encode(pcm, 48000, 1)
        val decodeStart = System.nanoTime()
        repeat(20) {
            val decoded = requireNotNull(WavPcm16.decode(wav))
            assertEquals(57600, decoded.samples.size)
            assertEquals(((pcm[0].toInt() and 255) or (pcm[1].toInt() shl 8)).toShort(), decoded.samples[0])
            assertEquals(((pcm[115198].toInt() and 255) or (pcm[115199].toInt() shl 8)).toShort(), decoded.samples.last())
        }
        instrumentation.sendStatus(0, Bundle().apply {
            putString("audio_preparation", "references=${first.size} firstMs=${(preparedAt-start)/1_000_000} " +
                "reuseUs=${(reusedAt-preparedAt)/1_000} decode20Ms=${(System.nanoTime()-decodeStart)/1_000_000}")
        })
    }
}
