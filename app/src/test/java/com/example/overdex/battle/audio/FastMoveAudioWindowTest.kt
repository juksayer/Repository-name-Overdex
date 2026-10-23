package com.example.overdex.battle.audio

import org.junit.Assert.*
import org.junit.Test

class FastMoveAudioWindowTest {
    private fun chunk(seq: Long, source: String = "PLAYBACK_POKEMON_GO", channels: Int = 2) = LivePcmChunk(
        Pcm16Audio(48_000, channels, ShortArray(960 * channels) { 400 }), seq,
        (seq + 1) * 20_000_000L, (seq + 1) * 20, source)

    @Test fun `stereo capture yields bounded overlapping mono windows without visual input`() {
        val window = FastMoveAudioWindow()
        val snapshots = (0L until 500).mapNotNull { window.ingest(chunk(it), generation = 3) }
        assertEquals(45, snapshots.size)
        assertTrue(snapshots.all { it.samples.size == 9_600 && it.generation == 3L })
        assertEquals(1_200_000_000, snapshots.first().durationNanos)
        assertEquals(0, snapshots.first().startNanos)
        assertTrue(snapshots.zipWithNext().all { (a, b) -> b.endNanos - a.endNanos == 200_000_000L })
    }

    @Test fun `a gap source change or format change cannot join two sounds into one window`() {
        for (kind in 0..3) {
            val window = FastMoveAudioWindow()
            (0L until 60).forEach { window.ingest(chunk(it)) }
            assertTrue(window.ready)
            val next = when (kind) {
                0 -> chunk(61)
                1 -> chunk(60, source = "MICROPHONE")
                2 -> chunk(60, channels = 1)
                else -> chunk(60).copy(endMonotonicNanos = 9_000_000_000)
            }
            assertNull(window.ingest(next))
            assertFalse(window.ready)
        }
    }
}
