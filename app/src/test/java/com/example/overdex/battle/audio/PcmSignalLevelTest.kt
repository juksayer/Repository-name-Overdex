package com.example.overdex.battle.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class PcmSignalLevelTest {
    @Test fun `silence is distinct from both signed endpoints`() {
        assertEquals(0f, PcmSignalLevel.peak(ByteArray(128)), 0f)
        assertEquals(1f, PcmSignalLevel.peak(byteArrayOf(0, -128)), 0f)
        assertEquals(32767f / 32768f, PcmSignalLevel.peak(byteArrayOf(-1, 127)), 0f)
    }
    @Test fun `only returned whole samples are measured`() {
        assertEquals(0f, PcmSignalLevel.peak(byteArrayOf(0, 0, -1, 127), 2), 0f)
        assertEquals(0f, PcmSignalLevel.peak(byteArrayOf(-1), 1), 0f)
    }
}
