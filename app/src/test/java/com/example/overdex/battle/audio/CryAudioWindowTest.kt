package com.example.overdex.battle.audio

import org.junit.Assert.*
import org.junit.Test

class CryAudioWindowTest {
    private fun chunk(seq:Long,end:Long,rate:Int=8000,channels:Int=1,frames:Int=8000,source:String="game") =
        LivePcmChunk(Pcm16Audio(rate,channels,ShortArray(frames*channels) { (it%30000).toShort() }),seq,end,end/1_000_000,source)

    @Test fun `keeps four seconds and preserves source clock and original stereo samples`() {
        val window=CryAudioWindow()
        for(i in 1L..3L)assertNull(window.ingest(chunk(i,i*1_000_000_000,channels=2),1))
        val snapshot=window.ingest(chunk(4,4_000_000_000,channels=2),1)!!
        assertEquals(0L,snapshot.startNanos)
        assertEquals(4_000_000_000L,snapshot.durationNanos)
        assertEquals(64000,snapshot.audio.samples.size)
        assertEquals(2,snapshot.audio.channelCount)
        assertArrayEquals(chunk(4,4_000_000_000,channels=2).audio.samples,snapshot.audio.samples.copyOfRange(48000,64000))
    }
    @Test fun `missing chunks and new observation attempts require fresh history`() {
        val window=CryAudioWindow()
        for(i in 1L..4L)window.ingest(chunk(i,i*1_000_000_000),1)
        assertNull(window.ingest(chunk(6,6_000_000_000),1))
        assertNull(window.ingest(chunk(7,7_000_000_000),2))
        assertNull(window.ingest(chunk(8,8_000_000_000),2))
        assertNull(window.ingest(chunk(9,9_000_000_000),2))
        assertEquals(6_000_000_000L,window.ingest(chunk(10,10_000_000_000),2)!!.startNanos)
    }
    @Test fun `awkward chunk sizes do not accumulate resampling rounding errors`() {
        val window=CryAudioWindow();var snapshot:CryAudioWindowSnapshot?=null
        for(i in 1L..400L) {
            val end=i*512*1_000_000_000/44100
            window.ingest(chunk(i,end,rate=44100,frames=512),1)?.let { snapshot=it }
        }
        val actual=snapshot!!
        assertEquals(176400,actual.audio.samples.size)
        assertEquals(4_000_000_000L,actual.durationNanos)
        assertEquals(actual.endNanos-4_000_000_000L,actual.startNanos)
    }
    @Test fun `source and format changes cannot stitch captures together`() {
        val window=CryAudioWindow()
        for(i in 1L..4L)window.ingest(chunk(i,i*1_000_000_000),1)
        assertNull(window.ingest(chunk(5,5_000_000_000,source="other"),1))
        assertNull(window.ingest(chunk(6,6_000_000_000,rate=48000,frames=48000,source="other"),1))
    }
}
