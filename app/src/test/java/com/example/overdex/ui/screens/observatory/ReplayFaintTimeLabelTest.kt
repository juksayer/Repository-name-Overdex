package com.example.overdex.ui.screens.observatory

import com.example.overdex.battle.replay.ReplayFaintTiming
import com.example.overdex.battle.replay.ReplayTimeBound
import org.junit.Assert.assertEquals
import org.junit.Test

class ReplayFaintTimeLabelTest {
    private val second = 1_000_000_000L
    private val go = 70 * second
    private fun frame(offset: Long) = ReplayTimeBound(go + offset, "source")

    @Test fun `both bounds round outward across seconds and minute boundaries`() {
        fun range(a: Long, b: Long) = formatReplayFaintTime(ReplayFaintTiming.Window(frame(a), frame(b)), go, 0)
        assertEquals("KO window 0:35–0:37 from GO", range(35 * second + 800_000_000L, 36 * second + 1))
        assertEquals("KO window 0:35–0:36 from GO", range(35 * second + 1, 35 * second + 2))
        assertEquals("KO window 0:59–1:00 from GO", range(60 * second - 1, 60 * second))
    }

    @Test fun `one-sided, observed, trainer and unknown times have distinct labels`() {
        val by = frame(36 * second + 400_000_000L)
        assertEquals("KO by 0:37 from GO", formatReplayFaintTime(ReplayFaintTiming.Window(null, by), go, 0))
        assertEquals("0 HP at 00:36.4 from GO", formatReplayFaintTime(ReplayFaintTiming.ZeroHp(by), go, 0))
        assertEquals("Trainer mark 00:36.4 from GO", formatReplayFaintTime(ReplayFaintTiming.TrainerMark(by.atNanos), go, 0))
        assertEquals("KO time unknown", formatReplayFaintTime(ReplayFaintTiming.Unknown, go, 0))
        assertEquals("KO by 1:47 from Replay start", formatReplayFaintTime(ReplayFaintTiming.Window(null, by), null, 0))
    }
}
