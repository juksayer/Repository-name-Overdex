package com.example.overdex.battle.observation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamSelectMenuDepartureTest {
    @Test fun `unrelated scenery without a witnessed party anchor cannot dismiss setup`() {
        val monitor = TeamSelectMenuDepartureMonitor()
        for (ms in listOf(100L, 200L, 300L)) assertFalse(frame(monitor, ms, false))
    }

    @Test fun `party disappearance dismisses within two fresh samples without countdown OCR`() {
        val monitor = TeamSelectMenuDepartureMonitor()
        assertFalse(frame(monitor, 100L, true))
        assertFalse(frame(monitor, 200L, false))
        assertTrue(frame(monitor, 300L, false))
        assertFalse(monitor.shouldSample(scope, 400_000_000L))
    }

    @Test fun `one transient or unavailable frame is not a sustained departure`() {
        val monitor = TeamSelectMenuDepartureMonitor()
        assertFalse(frame(monitor, 100L, true))
        assertFalse(frame(monitor, 200L, false))
        assertFalse(frame(monitor, 300L, true))
        assertFalse(frame(monitor, 400L, false))
        assertFalse(frame(monitor, 500L, null))
        assertFalse(frame(monitor, 600L, false))
        assertTrue(frame(monitor, 700L, false))
    }

    @Test fun `restarted attempt discards the previous screen baseline`() {
        val monitor = TeamSelectMenuDepartureMonitor()
        assertFalse(frame(monitor, 100L, true))
        assertFalse(frame(monitor, 200L, false))
        val next = TeamSelectMenuScope("match", 250_000_000L)
        assertTrue(monitor.shouldSample(next, 300_000_000L))
        assertFalse(monitor.observe(300_000_000L, false))
        assertTrue(monitor.shouldSample(next, 400_000_000L))
        assertFalse(monitor.observe(400_000_000L, false))
    }

    @Test fun `out of order frames and preattempt frames cannot advance dismissal`() {
        val monitor = TeamSelectMenuDepartureMonitor()
        val next = TeamSelectMenuScope("match", 100_000_000L)
        assertFalse(monitor.shouldSample(next, 99_000_000L))
        assertTrue(monitor.shouldSample(next, 200_000_000L))
        assertFalse(monitor.observe(200_000_000L, true))
        assertFalse(monitor.shouldSample(next, 190_000_000L))
        assertFalse(monitor.shouldSample(next, 220_000_000L))
    }

    @Test fun `party shapes require pale card backgrounds and a green control`() {
        val white = color(248, 252, 240)
        val green = color(65, 196, 156)
        val dark = color(40, 55, 48)
        assertTrue(TeamSelectMenuSurfaceProbe.cardBackgroundPresent(List(60) { white } + List(40) { dark }))
        assertFalse(TeamSelectMenuSurfaceProbe.cardBackgroundPresent(List(100) { green }))
        assertTrue(TeamSelectMenuSurfaceProbe.partyButtonPresent(List(80) { green } + List(20) { white }))
        assertFalse(TeamSelectMenuSurfaceProbe.partyButtonPresent(List(100) { white }))
    }

    @Test fun `our overlay or an invalid crop makes departure input unavailable`() {
        val calibration = TeamSelectCalibration.measured1080x2400
        assertTrue(TeamSelectMenuSurfaceProbe.unoccluded(calibration, null))
        assertTrue(TeamSelectMenuSurfaceProbe.unoccluded(calibration,
            com.example.overdex.model.AnchorRegion(.07f, .43f, .86f, .22f)))
        assertFalse(TeamSelectMenuSurfaceProbe.unoccluded(calibration, calibration.rosterSlot1))
        assertFalse(TeamSelectMenuSurfaceProbe.unoccluded(calibration.copy(
            useThisParty = com.example.overdex.model.AnchorRegion(Float.NaN, 0f, .2f, .1f)), null))
    }

    private val scope = TeamSelectMenuScope("match", 0L)
    private fun frame(monitor: TeamSelectMenuDepartureMonitor, ms: Long, visible: Boolean?): Boolean {
        val at = ms * 1_000_000L
        assertTrue(monitor.shouldSample(scope, at))
        return monitor.observe(at, visible)
    }
    private fun color(r: Int, g: Int, b: Int) = (0xff shl 24) or (r shl 16) or (g shl 8) or b
}
