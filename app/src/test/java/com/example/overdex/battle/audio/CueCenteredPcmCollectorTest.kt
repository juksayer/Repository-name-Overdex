package com.example.overdex.battle.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CueCenteredPcmCollectorTest {
    @Test
    fun `emits only a cue-centered pre and post roll`() {
        val collector = CueCenteredPcmCollector(preRollBytes = 4, postRollBytes = 6)
        collector.ingest(byteArrayOf(1, 2, 3, 4))
        collector.cue(AudioCaptureCue("article-7", BattleCryCueKind.COUNTDOWN_2))

        assertEquals(emptyList<CueCenteredPcmCapture>(), collector.ingest(byteArrayOf(5, 6)))
        val captures = collector.ingest(byteArrayOf(7, 8, 9, 10))

        assertEquals(1, captures.size)
        assertEquals("article-7", captures.single().cue.articleId)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), captures.single().pcm16le)
    }
}
