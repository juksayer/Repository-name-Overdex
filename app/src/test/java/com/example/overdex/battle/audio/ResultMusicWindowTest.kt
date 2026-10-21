package com.example.overdex.battle.audio

import org.junit.Assert.*
import org.junit.Test

class ResultMusicWindowTest {
    private fun chunk(seq: Long, source: String = "PLAYBACK_POKEMON_GO", rate: Int = 48000) = LivePcmChunk(
        Pcm16Audio(rate, 1, ShortArray(rate / 50)), seq, (seq + 1) * 20_000_000L, (seq + 1) * 20, source)

    @Test fun `continuous PCM stays bounded at four seconds with one scan per second`() {
        val window = ResultMusicWindow()
        val snapshots = (0L until 1000).mapNotNull { window.ingest(chunk(it)) }
        assertEquals(18, snapshots.size)
        assertEquals(24_000, snapshots.first().audio.samples.size)
        assertEquals(32_000, snapshots.last().audio.samples.size)
        assertTrue(snapshots.zipWithNext().all { (a,b) -> b.endNanos - a.endNanos == 1_000_000_000L })
    }

    @Test fun `missing chunk new source format and time discontinuity all require new audio`() {
        for (change in 0..3) {
            val window = ResultMusicWindow()
            (0L until 150).forEach { window.ingest(chunk(it)) }
            val changed = when (change) {
                0 -> chunk(151)
                1 -> chunk(150, source = "MICROPHONE")
                2 -> chunk(150, rate = 16000)
                else -> chunk(150).copy(endMonotonicNanos = 7_000_000_000L)
            }
            assertNull(window.ingest(changed))
        }
    }

    @Test fun `confidence alone cannot join different captures repeated offsets or missed scans`() {
        val audio = Pcm16Audio(8000, 1, ShortArray(32000))
        val first = ResultMusicWindowSnapshot(audio, 4_000_000_000, 4000, "PLAYBACK_POKEMON_GO", 1)
        val next = first.copy(endNanos = 5_000_000_000)
        val match = ResultMusicMatch(ResultMusicTheme.WIN, "sha", .98f, .62f, 1_000_000_000)
        fun check(snapshot: ResultMusicWindowSnapshot, found: ResultMusicMatch?) {
            val confirmation = ResultMusicConfirmation()
            assertFalse(confirmation.accept(first, match))
            assertFalse(confirmation.accept(snapshot, found))
        }
        check(next, match)
        check(next, match.copy(theme = ResultMusicTheme.LOSS))
        check(next, null)
        check(next.copy(continuity = 2), match.copy(referenceOffsetNanos = 2_000_000_000))
        check(next.copy(captureSource = "MICROPHONE"), match.copy(referenceOffsetNanos = 2_000_000_000))
        check(next.copy(endNanos = 6_000_000_000), match.copy(referenceOffsetNanos = 3_000_000_000))
    }
}
