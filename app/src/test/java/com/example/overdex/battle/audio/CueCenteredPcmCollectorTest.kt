package com.example.overdex.battle.audio

import org.junit.Assert.*
import org.junit.Test

class CueCenteredPcmCollectorTest {
    private fun collector() = CueCenteredPcmCollector(
        preRollBytes = 1_000, postRollBytes = 1_400, bytesPerSecond = 2_000,
    ) // 1 kHz PCM-16, the same 500/700 ms window as live capture.

    @Test fun `emits only a requested pre and post roll with its source timestamps`() {
        val collector = collector()
        assertTrue(feed(collector, 0, 1_000).isEmpty())
        assertNull(collector.cue(cue("impact", 1_000)))
        assertTrue(feed(collector, 1_000, 1_680).isEmpty())
        val capture = feed(collector, 1_680, 1_700).single() as CueCenteredPcmCapture
        assertEquals("impact", capture.cue.articleId)
        assertArrayEquals(pcm(500, 1_700), capture.pcm16le)
        assertEquals(500_000_000L, capture.startedAtMonotonicNanos)
        assertEquals(100_500L, capture.startedAtWallTimeMillis)
    }

    @Test fun `late visual testimony retrieves the original sound instead of delivery time`() {
        val onTime = collector()
        feed(onTime, 0, 1_000)
        onTime.cue(cue("impact", 1_000))
        val expected = feed(onTime, 1_000, 1_700).single() as CueCenteredPcmCapture

        val delayed = collector()
        feed(delayed, 0, 2_640) // New match impact testimony was as much as 1.633 s late.
        delayed.cue(cue("impact", 1_000))
        val actual = feed(delayed, 2_640, 2_660).single() as CueCenteredPcmCapture
        assertArrayEquals(expected.pcm16le, actual.pcm16le)
        assertEquals(expected.startedAtMonotonicNanos, actual.startedAtMonotonicNanos)
        assertEquals(expected.startedAtWallTimeMillis, actual.startedAtWallTimeMillis)
    }

    @Test fun `overlapping opposing cues keep separate references and sample aligned windows`() {
        val collector = collector()
        feed(collector, 0, 1_400)
        collector.cue(cue("player-impact", 1_003))
        collector.cue(cue("opponent-impact", 1_009))
        val captures = feed(collector, 1_400, 1_720).filterIsInstance<CueCenteredPcmCapture>()
        assertEquals(2, captures.size)
        assertArrayEquals(pcm(503, 1_703), captures[0].pcm16le)
        assertArrayEquals(pcm(509, 1_709), captures[1].pcm16le)
        assertEquals("player-impact", captures[0].cue.articleId)
        assertEquals("opponent-impact", captures[1].cue.articleId)
    }

    @Test fun `wrapped history retains the correct old clip and rejects expired requests`() {
        val collector = collector()
        feed(collector, 0, 9_000)
        collector.cue(cue("retained", 8_000))
        collector.cue(cue("expired", 1_000))
        val results = feed(collector, 9_000, 9_020)
        val capture = results.filterIsInstance<CueCenteredPcmCapture>().single()
        assertArrayEquals(pcm(7_500, 8_700), capture.pcm16le)
        assertEquals(7_500_000_000L, capture.startedAtMonotonicNanos)
        assertEquals(CueCenteredPcmUnavailable(cue("expired", 1_000), "REQUEST_OUTSIDE_RETAINED_AUDIO"),
            results.filterIsInstance<CueCenteredPcmUnavailable>().single())
    }

    @Test fun `missing pre roll never becomes a shorter or newer audio artifact`() {
        val collector = collector()
        feed(collector, 0, 200)
        collector.cue(cue("too-early", 200))
        assertEquals(listOf(CueCenteredPcmUnavailable(cue("too-early", 200), "REQUEST_OUTSIDE_RETAINED_AUDIO")),
            feed(collector, 200, 1_000))
    }

    @Test fun `input gap cannot stitch unrelated sound around a pending event`() {
        val collector = collector()
        feed(collector, 0, 1_000)
        collector.cue(cue("gap", 1_000))
        assertEquals(listOf(CueCenteredPcmUnavailable(cue("gap", 1_000), "INPUT_GAP")),
            feed(collector, 5_000, 5_020))
        collector.cue(cue("old", 1_000))
        assertEquals(listOf(CueCenteredPcmUnavailable(cue("old", 1_000), "REQUEST_OUTSIDE_RETAINED_AUDIO")),
            feed(collector, 5_020, 5_040))
    }

    @Test fun `reset removes prior session sound and pending cues`() {
        val collector = collector()
        feed(collector, 0, 1_000)
        collector.cue(cue("old", 1_000))
        collector.reset()
        assertTrue(feed(collector, 1_000, 2_000).isEmpty())
        collector.cue(cue("new", 1_200))
        assertEquals(listOf(CueCenteredPcmUnavailable(cue("new", 1_200), "REQUEST_OUTSIDE_RETAINED_AUDIO")),
            feed(collector, 2_000, 2_020))
    }

    @Test fun `missing source time and excessive pending requests are explicitly unavailable`() {
        val collector = collector()
        assertEquals("MISSING_SOURCE_TIME", collector.cue(AudioCaptureCue("untimed", BattleCryCueKind.FAST_MOVE_IMPACT))?.reason)
        repeat(32) { assertNull(collector.cue(cue("pending-$it", 1_000))) }
        assertEquals("REQUEST_QUEUE_FULL", collector.cue(cue("overflow", 1_000))?.reason)
    }

    @Test fun `48 kHz windows keep whole samples and the original start after sub millisecond cues`() {
        val collector = CueCenteredPcmCollector(48_000, 67_200)
        val samples = ByteArray(3 * 96_000) { (it / 2).toByte() }
        for (end in 1_920..samples.size step 1_920) {
            collector.ingest(samples.copyOfRange(end - 1_920, end),
                end * 1_000_000_000L / 96_000, 100_000L + end * 1_000L / 96_000)
        }
        val at = 1_000_015_000L
        collector.cue(AudioCaptureCue("fractional", BattleCryCueKind.FAST_MOVE_IMPACT, at))
        val capture = collector.ingest(ByteArray(1_920), 3_020_000_000L, 103_020L).single() as CueCenteredPcmCapture
        assertArrayEquals(samples.copyOfRange(48_000, 163_200), capture.pcm16le)
        assertEquals(500_000_000L, capture.startedAtMonotonicNanos)
        assertEquals(100_500L, capture.startedAtWallTimeMillis)
    }

    @Test fun `large input chunk retains only the bounded tail with correct timestamps`() {
        val collector = collector()
        collector.ingest(pcm(0, 10_000), 10_000_000_000L, 110_000L)
        collector.cue(cue("tail", 9_000))
        val capture = feed(collector, 10_000, 10_020).single() as CueCenteredPcmCapture
        assertArrayEquals(pcm(8_500, 9_700), capture.pcm16le)
        assertEquals(8_500_000_000L, capture.startedAtMonotonicNanos)
    }

    private fun cue(id: String, atMillis: Long) =
        AudioCaptureCue(id, BattleCryCueKind.FAST_MOVE_IMPACT, atMillis * 1_000_000L)

    private fun feed(collector: CueCenteredPcmCollector, startMillis: Int, endMillis: Int): List<CueCenteredPcmResult> =
        (startMillis until endMillis step 20).flatMap { start ->
            val end = minOf(start + 20, endMillis)
            collector.ingest(pcm(start, end), end * 1_000_000L, 100_000L + end)
        }

    private fun pcm(from: Int, until: Int) = ByteArray((until - from) * 2).also { bytes ->
        for (sample in from until until) {
            val index = (sample - from) * 2
            bytes[index] = sample.toByte()
            bytes[index + 1] = (sample shr 8).toByte()
        }
    }
}
