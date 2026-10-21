package com.example.overdex.battle.audio

import org.junit.Assert.*
import org.junit.Test

class TimedStatSoundProbeBufferTest {
    @Test fun `late request returns audio around original announcement rather than delivery time`() {
        val buffer = TimedStatSoundProbeBuffer()
        feed(buffer, 0, 20)
        val read = buffer.read(10_000_000_000L) as TimedStatSoundProbeBuffer.Read.Ready
        assertEquals(13 * 8_000, read.audio.samples.size)
        assertEquals(4, read.audio.samples.first().toInt())
        assertEquals(16, read.audio.samples.last().toInt())
        assertEquals(17_000_000_000L, read.endNanos)
        assertEquals(117_000L, read.endWallMillis)
    }

    @Test fun `window waits for later sound and handles wrapped history`() {
        val buffer = TimedStatSoundProbeBuffer()
        feed(buffer, 0, 25)
        assertEquals(TimedStatSoundProbeBuffer.Read.Pending, buffer.read(20_000_000_000L))
        feed(buffer, 25, 28)
        val read = buffer.read(20_000_000_000L) as TimedStatSoundProbeBuffer.Read.Ready
        assertEquals(14, read.audio.samples.first().toInt())
        assertEquals(26, read.audio.samples.last().toInt())
        assertEquals(27_000_000_000L, read.endNanos)
    }

    @Test fun `expired pre-roll and reset never fabricate missing audio`() {
        val buffer = TimedStatSoundProbeBuffer()
        feed(buffer, 0, 30)
        assertEquals(TimedStatSoundProbeBuffer.Read.Unavailable, buffer.read(15_000_000_000L))
        buffer.reset()
        assertEquals(TimedStatSoundProbeBuffer.Read.Pending, buffer.read(20_000_000_000L))
        feed(buffer, 30, 31)
        assertEquals(TimedStatSoundProbeBuffer.Read.Unavailable, buffer.read(30_000_000_000L))
    }

    private fun feed(buffer: TimedStatSoundProbeBuffer, start: Int, end: Int) {
        for (second in start until end) buffer.ingest(LivePcmChunk(
            Pcm16Audio(8_000, 1, ShortArray(8_000) { second.toShort() }), second.toLong(),
            (second + 1) * 1_000_000_000L, 100_000 + (second + 1) * 1_000L, "TEST"))
    }
}
