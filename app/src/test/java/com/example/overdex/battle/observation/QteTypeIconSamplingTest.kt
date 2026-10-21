package com.example.overdex.battle.observation

import com.example.overdex.model.AnchorRegion
import org.junit.Assert.*
import org.junit.Test

class QteTypeIconSamplingTest {
    private var now = 100_000_000_000L
    private var saved = AnchorRegion(.4f, .45f, .2f, .1f)
    private val gate = QteTypeIconSampling(savedRegion = { saved }, now = { now })

    @Test fun `late ready cue gets only the remaining window and repeats do not extend it`() {
        gate.beginSession()
        gate.ready(now - 3_000_000_000L)
        assertTrue(gate.isEnabled())
        now += 6_000_000_000L
        gate.ready(now)
        now += 1_000_000_000L
        assertFalse(gate.isEnabled())
    }

    @Test fun `expired future or old out of order cues cannot open a capture window`() {
        gate.beginSession()
        gate.ready(now - QteTypeIconSampling.WINDOW_NANOS)
        gate.ready(now + 1)
        assertFalse(gate.isEnabled())
        gate.ready(now)
        gate.closeWindow(now + 1)
        now += 2
        gate.ready(now - 2_000_000_000L)
        assertFalse(gate.isEnabled())
    }

    @Test fun `manual sampling uses draft geometry and rejects cached frames from before the tap`() {
        val draft = AnchorRegion(.3f, .4f, .2f, .2f)
        assertFalse(gate.sample(draft))
        gate.beginSession()
        assertTrue(gate.sample(draft))
        assertEquals(draft, gate.region)
        assertEquals(AnchorRegion(.4f, .45f, .2f, .1f), saved)
        assertFalse(gate.acceptsFrame(now - 1))
        assertTrue(gate.acceptsFrame(now))
        assertFalse(gate.acceptsFrame(now + QteTypeIconSampling.WINDOW_NANOS))
        assertFalse(gate.sample(saved))
    }

    @Test fun `delayed ready cannot reopen a qte whose closing announcement arrived first`() {
        gate.beginSession()
        gate.closeWindow(now - 1_000_000_000L)
        gate.ready(now - 3_000_000_000L)
        assertFalse(gate.isEnabled())
        now += 12_000_000_000L
        gate.ready(now)
        assertTrue(gate.isEnabled())
    }

    @Test fun `sample limit stops capture even if many frames arrive at once`() {
        gate.beginSession()
        gate.sample(saved)
        repeat(QteTypeIconSampling.MAX_SAMPLES) { gate.captured(now) }
        assertEquals(80, gate.status.value.saved)
        assertFalse(gate.isEnabled())
    }

    @Test fun `new qte reads the latest saved box instead of the match start snapshot`() {
        gate.beginSession()
        gate.ready(now)
        val first = gate.region
        now += 12_000_000_000L
        saved = saved.copy(x = .2f)
        gate.ready(now)
        assertNotEquals(first, gate.region)
        assertEquals(saved, gate.region)
    }

    @Test fun `charge announcement closes current sampling but an older announcement does not`() {
        gate.beginSession()
        gate.sample(saved)
        gate.closeWindow(now - 1)
        assertTrue(gate.isEnabled())
        gate.closeWindow(now)
        assertFalse(gate.isEnabled())
    }

    @Test fun `match end and stop reject more samples and next match starts empty`() {
        gate.beginSession()
        gate.sample(saved)
        gate.captured(now)
        gate.endSession()
        assertFalse(gate.isEnabled())
        assertFalse(gate.sample(saved))
        gate.ready(now)
        assertFalse(gate.isEnabled())
        gate.beginSession()
        assertFalse(gate.isEnabled())
        assertEquals(0, gate.status.value.saved)
        assertNull(gate.region)
        assertTrue(gate.sample(saved))
        gate.stop()
        assertFalse(gate.sample(saved))
    }
}
